# Java 企业级 MCP Server 的多租户三档隔离：从行级到实例级的完整工程实践

> 投稿：掘金 / CSDN | 日期：2026-08-29 | 配套开源项目：`HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise`（Spring Boot 3.4 / Java 17）
> 系列前篇：《用 Java + Spring Boot 搭建企业级 MCP Server》（V1.0）、《MCP 企业级安全实践：RBAC/限流/审计/OAuth2》（V1.2-V1.9）

## 为什么多租户是 MCP Server 商业化的分水岭

2026 年的 MCP 市场已经走过「能连上」的阶段，进入「敢不敢把生产数据交给 Agent」的阶段。招聘市场的信号非常直接：

- Anthropic Enterprise 团队开设 MCP Engineer 岗（$300K-320K），职责就是帮金融/医疗客户把 MCP Server 做到「security, scalability, and enterprise compliance」；
- NTT DATA 的 MCP & Enterprise Integration Engineer JD 里写着招聘红线：「*Only consumed MCP tools; no server implementation*」直接刷掉；
- Upwork 与外包行业里，「Enterprise multi-tenant MCP server」被单独定价 $40K-80K（6-10 周）。

多租户能力，就是「能连上」和「企业敢用」之间的那道墙。而多租户本身也有三档深度：**行级 → Schema 级 → 实例级**。本文用一个真实开源框架（spring-ai-mcp-enterprise）拆解三档的实现与取舍，最后给出完整的实例级实现代码。

## 三档隔离：一张表看懂差异

| 维度 | 行级 Row | Schema 级 | **实例级 Instance** |
|---|---|---|---|
| 隔离边界 | SQL 行（tenant_id 列） | 数据库 Schema | **独立 DataSource + 连接池 + 独立库/实例** |
| 故障域 | 无（共享表） | Schema 级（同库） | **物理级** |
| 资源配额 | 不可控 | 库内可调 | **每租户独立池/容量/限流** |
| 合规强度 | 中 | 高 | **最高（数据驻留/监管隔离）** |
| 成本 | 低 | 中 | 高（每租户一份资源） |

**选择的经验法则**：SaaS 起步用行级（零成本、改动最小）；需要平台级强隔离时上 Schema 级；金融/政务/医疗客户（数据驻留、监管审计要求）只接受实例级。三档共用同一个 `X-Tenant-Id` 头与 TenantContext，业务代码零改动即可切换。

## 第一档：行级隔离（V1.11）

核心思想：所有表带 `tenant_id` 列，JDBC 操作由 `TenantAwareJdbcTemplate` 包裹，**没有租户上下文时直接拒绝执行（fail-closed）**——这是防跨租户越权的底线。

```java
public class TenantAwareJdbcTemplate extends JdbcTemplate {
    // 每次 execute/query 前强制校验租户，防止"忘记带租户"导致的串租户
    @Override
    public <T> T execute(StatementCallback<T> action) {
        TenantContext.currentTenantOrThrow();   // 无租户 → TenantNotResolvedException
        return super.execute(action);
    }
}
```

租户上下文用 `InheritableThreadLocal` 实现，异步任务自动继承；同时镜像到 SLF4J MDC，让每条审计日志天然带租户标签：

```java
public final class TenantContext {
    private static final ThreadLocal<String> HOLDER = new InheritableThreadLocal<>();
    public static String currentTenantOrThrow() {
        return get().orElseThrow(TenantNotResolvedException::new);
    }
}
```

## 第二档：Schema 级隔离（V1.12）

核心思想：同一物理库内，每个租户一个 Schema（如 `tenant_acme`）。用动态代理包住 Connection，在 `createStatement/prepareStatement/prepareCall` 之前自动执行 `SET SCHEMA tenant_xxx`（PostgreSQL 是 `SET search_path TO`，MySQL 是 `USE`）。

```java
public class TenantSchemaDataSource extends DelegatingDataSource {
    private Connection proxy(Connection target) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (SWITCH_HOOKS.contains(method.getName())) {   // createStatement/prepareStatement/prepareCall
                ensureTenantSchema(target, switchedSchema);  // 解析租户 → SET SCHEMA → 缓存已切换标记
            }
            return method.invoke(target, args);
        };
        return (Connection) Proxy.newProxyInstance(...);
    }
}
```

两个工程细节值得注意：
1. **连接级缓存**：同一物理连接已切到同一 Schema 时跳过 SET 语句——后续语句零开销；
2. **自动建 Schema**：`provision-on-first-use=true` 时首次访问自动 `CREATE SCHEMA IF NOT EXISTS` 并执行模板 DDL，保证每个租户的表结构一致。租户 ID 到 Schema 名的映射做了严格白名单校验（`^[A-Za-z0-9_-]{1,64}$`），防注入。

## 第三档：实例级隔离（V1.13）——本文重点

核心思想：**每个租户一个独立的 DataSource + HikariCP 连接池**，可指向独立数据库实例。这是物理故障域隔离与数据驻留合规的唯一解。本次实现共 5 个核心类：

### 1) 注册表：租户 → 数据源

```java
public interface TenantInstanceRegistry extends AutoCloseable {
    DataSource get(String tenantId);       // 未注册 → TenantNotResolvedException（fail-closed）
    void register(String tenantId, DataSource ds);   // 运行时开通，替换旧池自动关闭
    DataSource unregister(String tenantId);          // 停用：强制关闭连接池
    Set<String> tenants();
    void close();                          // 上下文关闭时释放全部池
}
```

实现用 `ConcurrentHashMap`，重点保证两条生命周期安全：
- unregister 必须关池（否则连接池泄漏）；
- register 覆盖旧租户时先关旧池再装新池。

### 2) 路由数据源：fail-closed 是默认值

```java
public class TenantInstanceDataSource implements DataSource {
    private final TenantInstanceRegistry registry;
    @Override
    public Connection getConnection() throws SQLException {
        String tenantId = TenantContext.getOrNull();
        if (tenantId == null || tenantId.isBlank()) {
            throw new TenantNotResolvedException("No tenant resolved ...");  // 无租户 = 拒绝
        }
        return registry.get(tenantId).getConnection();   // 未注册 = 拒绝
    }
}
```

因为路由发生在每次 `getConnection()`，**运行时 register/unregister 即时生效，无需重启**——这是未来生命周期管理 API（V1.14）的地基。

### 3) 每租户池的构建：配额 + 密钥占位

```java
public DataSource provision(String tenantId, McpTenantProperties.TenantDatasource spec) {
    HikariDataSource pool = new HikariDataSource();
    pool.setPoolName("mcp-tenant-" + tenantId);
    pool.setJdbcUrl(spec.getUrl());
    pool.setUsername(spec.getUsername());
    pool.setPassword(resolveSecret(spec.getPassword()));   // ${ENV_VAR} → 系统属性/环境变量
    pool.setMaximumPoolSize(spec.getPool().getMaximumPoolSize());  // 每租户配额
    // ... minimumIdle / connectionTimeout / maxLifetime / idleTimeout
    return pool;
}
```

`${ACME_DB_PASSWORD}` 这类占位符在 provision 时解析，密钥永远不会进 git；每租户的 `maximum-pool-size` 就是资源配额的可视化控制点。

### 4) 三模式互斥守卫：宁可启动失败，不可静默裸奔

```java
public class TenantModeGuard {
    public TenantModeGuard(McpTenantProperties properties) {
        if (properties.getMode() == Mode.INSTANCE && !properties.getInstance().isEnabled()) {
            throw new IllegalStateException("mode=instance 但 instance.enabled=false → 直接启动失败");
        }
    }
}
```

三种模式的 Bean 各自挂在 `@ConditionalOnProperty(mode=...)` 条件下，天然互斥；守卫补上条件注解表达不了的语义矛盾，把「配置错了静默无隔离」变成「启动即报错」。

## 测试：双 H2 实例验证物理隔离

实例级隔离的正确性必须用「两个真的数据库」验证，而不能用 mock：

```java
@Test
void tenantsArePhysicallyIsolatedAcrossDatabases() {
    TenantContext.withTenant("acme", () -> {       // 路由到 acme 专属 H2 库
        jdbc.update("INSERT INTO app_data(payload) VALUES ('acme-secret')");
        ...
    });
    TenantContext.withTenant("globex", () -> {     // 路由到 globex 专属 H2 库
        // globex 库里同表结构、0 行 acme 数据 —— 物理隔离成立
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM app_data", Integer.class));
    });
}
```

加上无租户 fail-closed、未注册租户拒绝、运行时开通/停用、连接池配额断言，共 17 个新测试覆盖了全部关键路径。

## 什么时候选哪一档

- **验证期 / 低成本 SaaS**：行级。写对 `tenant_id` 过滤 + fail-closed，一天上线；
- **平台型中台 / 已有集中式 DBA**：Schema 级。隔离强度够，运维成本可控；
- **金融、政务、医疗 / 甲方明确要求数据驻留**：实例级。贵，但这是唯一能过合规审计的答案——也是外包市场上能单独报价 $40K-80K 的能力。

三档共用 `X-Tenant-Id` 头、TenantContext 和业务代码的事实，意味着**你可以先用行级快速交付，等客户预算到位再平滑升级到实例级**——这个「升级不重构」的卖点，正是企业采购决策里最值钱的那句话。

---

*项目地址：github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise（Apache 2.0，欢迎 Star/PR）*
*下期预告：租户生命周期管理 REST API（V1.14）——让「企业多租户」从演示变成可运营的交付物。*