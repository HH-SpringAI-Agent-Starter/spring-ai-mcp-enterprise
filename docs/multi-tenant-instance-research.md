# V1.13 预研：实例级多租户隔离（Instance-Level Multi-Tenancy）

> 状态：预研设计 | 版本：1.1.0 → 1.1.0（新增模块能力） | 日期：2026-08-28
> 前置：V1.11 Row-level（08-26）✅ → V1.12 Schema-level（08-27）✅ → **V1.13 Instance-level（本文）**

## 一、为什么做实例级隔离

| 维度 | Row（V1.11） | Schema（V1.12） | Instance（V1.13） |
|------|-------------|-----------------|-------------------|
| 隔离边界 | 业务 SQL 行级（tenant_id 列） | 数据库 Schema | **独立 DataSource + 连接池 + 独立数据库/实例** |
| 故障域 | 无（共享表） | Schema 级（同库） | **物理级（库/实例）** |
| 资源配额 | 不可控 | 库内可调 | **每租户独立连接池/容量/限流** |
| 合规强度 | 中 | 高（金融/政务/医疗） | **最高（数据驻留/监管隔离）** |
| 成本 | 低 | 中 | 高（每租户一份资源） |

**市场信号**：Sumo Logic Staff SWE（$207-243K）JD 的 "multi-tenant execution at enterprise scale" + "fault tolerance / isolation / quotas"；iMagic 外包报价单将「Enterprise multi-tenant MCP server」单独定价 $40K-80K（6-10 周）——实例级隔离是支撑该档报价的核心卖点。金融/政务客户（数据驻留合规）只接受实例级。

## 二、目标能力

1. 每租户独立 `DataSource`（独立 URL/账号/连接池），动态创建与销毁；
2. 租户 → 数据源注册表（`TenantInstanceRegistry`），支持运行时开通/停用（对接未来的生命周期管理 API）；
3. 连接池复用 Spring Boot 的 `HikariDataSource` 构建器，池参数（max-active/超时/探活）按租户配额配置；
4. 默认 fail-closed：未注册租户的任何访问直接拒绝；
5. 与 V1.11/V1.12 共用 `X-Tenant-Id` 头 + `TenantContext`，业务代码零改动切换模式。

## 三、设计草案

### 3.1 模块结构（仍放 mcp-tenant，新增类）

```
com.mcp.enterprise.tenant.instance
├── TenantInstanceRegistry        // 租户→DataSource 注册表（并发安全，支持动态增删）
├── TenantInstanceDataSource      // 动态路由 DataSource（无租户 fail-closed）
├── TenantInstanceProperties      // 每租户连接池/账号配置（可来自 yml 或管理 API）
├── TenantInstanceProvisioner     // 按模板创建租户库/账号（可选：JDBC 执行 DDL）
└── McpTenantInstanceAutoConfiguration
```

### 3.2 核心接口草案

```java
public interface TenantInstanceRegistry {
    DataSource get(String tenantId);                 // 未注册 → TenantNotResolvedException
    void register(String tenantId, DataSource ds);   // 运行时开通
    void unregister(String tenantId);                // 停用：关闭连接池
    Set<String> tenants();
}

// 动态路由：AbstractRoutingDataSource 的 MCP 版
public class TenantInstanceDataSource extends AbstractRoutingDataSource {
    @Override protected Object determineCurrentLookupKey() {
        return TenantContext.getTenantId().orElseThrow(TenantNotResolvedException::new);
    }
    // 注册表校验：白名单 + 已注册，否则 fail-closed
}
```

### 3.3 配置草案

```yaml
mcp:
  tenant:
    mode: instance
    instance:
      enabled: true
      tenants:                        # 静态配置（管理 API 可覆盖）
        acme-corp:
          url: jdbc:mysql://db-acme:3306/mcp
          username: acme_app
          password: ${ACME_DB_PASSWORD}
          pool:
            maximum-pool-size: 10
            minimum-idle: 2
        globex:
          url: jdbc:mysql://db-globex:3306/mcp
          username: globex_app
          password: ${GLOBEX_DB_PASSWORD}
          pool:
            maximum-pool-size: 5
    schema:                           # 兼容：schema 模式配置继续可用
      prefix: tenant_
```

### 3.4 三档模式互斥规则

- `mode: row` → 注册 `TenantAwareJdbcTemplate`（V1.11，默认，零变化）
- `mode: schema` → 注册 `TenantSchemaManager` + `TenantSchemaDataSource`（V1.12）
- `mode: instance` → 注册 `TenantInstanceRegistry` + `TenantInstanceDataSource`（V1.13）
- 同一时刻仅一种模式装配，配置校验在 `McpTenantAutoConfiguration` 中 fail-fast。

### 3.5 测试计划（H2 双实例验证隔离）

1. 两个 H2 内存库（不同 URL）模拟两个租户实例，各自建表写数据；
2. acme 写入 → globex URL 查询不可见（物理隔离）；
3. 未注册租户访问 → fail-closed 异常；
4. 运行时 register/unregister 生命周期；
5. 连接池参数按租户生效（maximum-pool-size 断言）；
6. 三模式互斥装配测试（row/schema/instance 各启一次，断言 Bean 集合正确）。

## 四、与 V1.14 的衔接

- V1.14 租户生命周期管理 REST API（`/api/admin/tenants` CRUD + 配额）将直接消费 `TenantInstanceRegistry`；
- 实例级 + 管理 API = 外包报价单上的「企业多租户 $40-80K」档的可演示交付物。

## 五、风险与决策点

| 风险 | 对策 |
|------|------|
| 每租户一个连接池 → 资源开销 | 池参数按租户配额配置，闲置租户可缩容至 minimum-idle=0（Hikari 支持） |
| 动态注册的数据源泄露 | 注册表强校验 + unregister 强制 close + 审计日志记录租户生命周期事件 |
| 与 Spring 事务管理集成 | 事务需绑定到路由后的具体 DataSource；`@Transactional` 建议用 per-tenant TransactionManager 或编程式事务（先文档约束，后续再抽象） |
| 密码安全 | 支持 `${ENV}` 占位符 + 可选 jasypt 加密（复用 mcp-core 的配置解密能力） |

## 六、验收标准

- [ ] 三档模式（row/schema/instance）配置互斥且各模式测试全绿
- [ ] 实例级物理隔离演示（两租户独立库，互不可见）
- [ ] 运行时开通/停用租户（无重启）
- [ ] 与既有 `X-Tenant-Id` 链路零改动兼容
- [ ] 发布说明 + 博客稿（三档隔离完整故事线）