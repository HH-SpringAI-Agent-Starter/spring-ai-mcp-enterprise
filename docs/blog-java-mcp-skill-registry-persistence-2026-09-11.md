# Java 企业级 MCP Server 的 Skill Registry 如何做到"重启不丢版本"？V1.22 JDBC 持久化实战

> 关键词：MCP Skill Registry、MCP 版本管理持久化、MCP 灰度发布、MCP 故障回滚、Java MCP 框架、Spring Boot MCP Server、企业级 AI 网关
> 项目：Spring AI MCP Enterprise（GitHub: HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise）

## 一、一个被忽视的企业级细节

过去一年，MCP（Model Context Protocol）从 3,000+ 个 Server 涨到 18,000+，招聘市场里"MCP Engineer / MCP 平台工程师"成了最快增长的新岗位。翻一翻企业 JD，高频出现的不是"写个 tool"，而是：

- **Skill / Spec Registry 服务**（沃尔玛中国 AI 平台岗）
- **版本管理、灰度发布、故障回滚**（上海 MCP 平台工程岗）
- **鉴权、限流、熔断、灰度**（几乎所有 AI 网关岗）

我们开源的 **Spring AI MCP Enterprise** 在 V1.21 就落地了 Skill Registry：注册即激活、语义化版本、显式激活、一键回滚、权重灰度。12 个测试全绿，文档齐全。

但它有一个"demo 级"的短板：**状态全在进程内存里**。

这意味着——K8s 滚动发布一次，当前 ACTIVE 版本、灰度权重、版本历史**全部清零**。"故障回滚"这个卖点，在重启后就成了摆设。

V1.22 就是来补这一刀的：**Skill Registry 持久化（JDBC），opt-in 开启，默认零依赖不变。**

## 二、设计：一个 SPI，两种实现

把"治理状态"和"存储介质"解耦：

```java
public interface SkillRegistryStore {
    default boolean isPersistent() { return false; }
    List<SkillSpec> loadVersions();
    Map<String,String> loadActiveVersions();
    Map<String,Map<String,Integer>> loadGrayWeights();
    void upsert(SkillSpec spec);
    void setActive(String name, String version);
    void setGrayWeight(String name, String version, int weight);
    void clearGray(String name);
    void removeSkill(String name);
}
```

- `InMemorySkillRegistryStore`：默认实现，全部 no-op，`isPersistent()=false` → **V1.21 行为零变化**，不需要数据库也能跑。
- `JdbcSkillRegistryStore`：单表、方言无关，`store=jdbc` 时才装配。

`SkillRegistry` 增加 `SkillRegistry(props, store)` 构造器，旧构造器委托到内存实现——**源码级向后兼容，V1.21 的 12 个测试一行没改仍然全绿。**

## 三、实现：为什么是"一张表 + delete/insert"

```sql
CREATE TABLE IF NOT EXISTS mcp_skill_registry (
  skill_name VARCHAR(128) NOT NULL,
  version VARCHAR(64) NOT NULL,
  description VARCHAR(1024),
  category VARCHAR(128), owner VARCHAR(128), status VARCHAR(32),
  input_schema VARCHAR(4000),          -- SkillSpec 的 JSON schema
  active INT NOT NULL DEFAULT 0,       -- 1 = 当前服务版本
  gray_weight INT,                     -- NULL = 未灰度
  created_at BIGINT, updated_at BIGINT,
  PRIMARY KEY (skill_name, version)
);
```

三个刻意的取舍，让同一份代码在 H2 / MySQL / PostgreSQL / SQL Server 上都能跑：

1. **不用 `MERGE`**。`MERGE`/`UPSERT`/`ON DUPLICATE KEY` 是各数据库语法分歧最大的地方（`MERGE` / `INSERT ... ON DUPLICATE KEY UPDATE` / `INSERT ... ON CONFLICT` 三种写法）。这里改成 `upsert = DELETE + INSERT`，表规模只有几十到几千行，多一条语句代价可忽略，换来的是**零方言适配**。
2. **`active` 用 `INT` 而不是 `BOOLEAN`**。`BOOLEAN` 在 MySQL 是 `TINYINT`、在 Oracle 根本不存在，`INT` 最稳。
3. **`input_schema` 默认 `VARCHAR(4000)`**。全库可用；生产里 schema 更大的话，按指南改 `TEXT`/`CLOB` 并交给迁移工具托管。

还有一个容易被忽略的正确性细节：**`upsert` 会保留已有行的 `active` 标记**。

```java
Integer existingActive = queryInt("SELECT active FROM ... WHERE skill_name=? AND version=?", ...);
jdbc.update("DELETE FROM ...");
jdbc.update("INSERT INTO ... (..., active, ...) VALUES (..., ?, ...)", existingActive == null ? 0 : existingActive, ...);
```

否则"更新一个正在服务的版本的描述"会把它误置为"非激活"，导致该 Skill 一夜之间无人服务。

## 四、可用性优先于持久性

每次治理操作，**内存先改成功，再 best-effort 落库**：

```java
private void persist(String action, Runnable mutation) {
    if (store == null || !store.isPersistent()) return;
    try { mutation.run(); }
    catch (RuntimeException e) {
        log.warn("⚠️ [V1.22] Skill Registry persistence failed ({}): {}", action, e.getMessage());
    }
}
```

数据库抖一下，不该让"注册/回滚/灰度"这些运维操作失败。如果你要强一致，自定义一个 `SkillRegistryStore` Bean 即可覆盖默认装配（`@ConditionalOnMissingBean` 会尊重你的定义）。

## 五、启动可恢复：`reload()`

应用启动时（自动配置里检测到持久化存储就调用）从库里重建内存态：加载全部版本、ACTIVE 版本、灰度权重。非持久化存储上是 no-op；store 故障则打 WARN 后以空表启动。

## 六、验证：用 H2 模拟"重启"

9 个新测试全部模拟**"重启"**这个动作——每次 `new SkillRegistry(props, store)` 都从同一个 store 重新 `reload()`：

```java
@Test
void grayWeightsSurviveRestart() {
    SkillRegistry first = restarted();
    first.register(spec("calc_tool", "1.0.0", "stable"));
    first.register(spec("calc_tool", "1.1.0", "canary"));
    first.gray("calc_tool", "1.1.0", 100);

    SkillRegistry second = restarted();      // ← 相当于进程重启
    for (int i = 0; i < 50; i++) {
        assertEquals("1.1.0", second.route("calc_tool").getVersion());
    }
}
```

覆盖：版本+ACTIVE 恢复、显式激活恢复、回滚恢复、灰度权重恢复、激活清空灰度、`input_schema` JSON 往返、删除清空、建表幂等。`mcp-registry` 模块 **21 个测试全绿**，`mcp-server -am` 全链路 `BUILD SUCCESS`。

## 七、三分钟上手

```yaml
mcp:
  enterprise:
    registry:
      skill:
        store: jdbc                # 从 memory 改成 jdbc
        init-schema: true          # 自动建表（用 Flyway 时置 false）
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/mcp?characterEncoding=utf8
```

然后注册、回滚、**重启进程**、再查询——版本历史与 ACTIVE 版本还在。

## 八、这对求职/接单意味着什么

2026 年 9 月的市场信号很明确：

- **花旗（新加坡）** 招 Spring Boot/Java 工程师，JD 明确写"Required technical knowledge in Agentic AI, MCP server and their tools"——**Java 栈的 MCP 岗位**。
- **Sumo Logic** Staff Engineer（MCP & Agent Infrastructure）$207K–$243K，要求 Java/Scala/Go/Python + MCP server 基础设施 + 多租户 + 限流。
- **沃尔玛中国** 高级 AI 平台工程师 ¥30–55K/月，要求 AI/MCP 网关 + **Skill/Spec Registry** + 鉴权限流熔断灰度。
- Upwork 上 "Skills Platform Architecture (MCP)"、"MCP Expert"（明确要求 Java，$60–120/小时）持续挂单。

而 **Java + Spring Boot + MCP + 治理层（版本/灰度/回滚/持久化）** 的组合，在开源里几乎是空白——Python 生态已经很卷，Java 企业级 MCP 框架寥寥。这正是这个项目的差异化卖点。

> 项目地址：GitHub `HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise`
> 模块：`mcp-core` / `mcp-auth` / `mcp-tenant` / `mcp-monitor` / `mcp-registry` / `mcp-a2a` / `mcp-alibaba` / `mcp-tools`
> 特性：RBAC + OAuth2 + 多租户三档隔离 + 限流 + 审计 + A2A 双协议网关 + Skill Registry（持久化）
