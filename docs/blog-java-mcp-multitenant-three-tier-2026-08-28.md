# Java 构建企业级 MCP Server：多租户三档隔离实战（Row / Schema / Instance）

> 候选发布平台：掘金 / CSDN / InfoQ | 作者：spring-ai-mcp-enterprise 项目组 | 2026-08-28
> 配套开源项目：https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise

## 引子：为什么多租户是 MCP 企业化的第一道门槛

MCP（Model Context Protocol）正在从「AI 开发者的玩具」变成「企业 AI 基础设施」。OpenAI、Anthropic、Google 全部原生支持，MCP Server 目录已超 11,000 条。但企业落地的第一问永远是：**多个业务方共用一套 MCP Server，怎么隔离？**

- 跨国集团：不同 BU/子公司不能互看数据；
- SaaS 产品：把 MCP 能力开放给客户（按 iMagic/SolGuruz 外包价目，这一档是 **$40K-80K**）；
- 金融监管：客户数据必须物理驻留隔离（合规成本 +25-40%）。

多租户隔离有清晰的「三档」演进路径，我们的开源框架 `spring-ai-mcp-enterprise` 已经落地前两档，第三档完成设计。这篇文章用真实代码讲清楚三个模式怎么选、怎么实现、边界在哪。

## 第一档：Row-level 行级隔离（低成本、快落地）

**思想**：所有租户共享同一张表，每行带 `tenant_id` 列，查询强制带租户条件。

### 实现要点（mcp-tenant 模块，V1.11）

1. **TenantContext**：从 HTTP 头 `X-Tenant-Id` 解析当前租户（Filter 绑定，业务代码零感知）；
2. **TenantAwareJdbcTemplate**：继承 `JdbcTemplate`，重写所有执行入口，**强制**在 SQL 前注入租户条件——你没写租户条件？**fail-closed 直接抛异常**，绝不静默跨租户；
3. 默认 `mode: row`，开箱即用，业务 SQL 改动最小。

### 适用场景
- 租户数多、单租户数据量小；
- 快速上线，成本敏感；
- 团队纪律好，能保证 SQL 规范。

### 边界
- 共享表 = 无物理边界，一次「忘写租户列」的失误就可能泄漏；
- 无法做每租户独立索引/分区策略；
- 强合规场景（金融/政务/医疗）过不了审计。

## 第二档：Schema-level Schema 隔离（物理边界的起点）

**思想**：每个租户一个数据库 Schema（`tenant_acme`、`tenant_globex`...），连接层自动切换。

### 实现要点（mcp-tenant 模块，V1.12）

1. **TenantSchemaManager**：租户 ID → Schema 名映射，白名单校验（仅 `[A-Za-z0-9_-]`、≤64 字符、小写化、`-`→`_`），**把 SQL 标识符注入面封死**；
2. **方言自动检测**（postgresql/mysql/h2/generic）：`SET search_path` / `SET SCHEMA` / `USE` 自动选择；
3. **TenantSchemaDataSource**：包装 DataSource，`getConnection()` 返回动态代理 Connection，在创建 Statement 前自动切 Schema，连接级缓存（重复调用零开销）；
4. **Provision 自动开通**：首次访问自动 `CREATE SCHEMA IF NOT EXISTS` + 模板 DDL 落在**该租户 Schema 内**（幂等）；
5. 无租户绑定 → fail-closed。

```yaml
mcp:
  tenant:
    mode: schema
    schema:
      prefix: tenant_
      dialect: postgresql   # 生产建议显式指定
      provision-on-first-use: true
```

```bash
curl -H "Authorization: Bearer $TOKEN" -H "X-Tenant-Id: acme-corp" \
  http://localhost:8080/mcp/stateless
```

### 适用场景
- 中大型租户、每租户数据量需要独立表空间；
- 合规要求「数据物理隔离」；
- 想给不同租户不同建表模板。

### 边界
- 同库同实例：一个租户的慢查询/满库仍可能拖累邻居；
- 无法按租户做独立连接池配额、独立故障域。

## 第三档：Instance-level 实例隔离（设计已完成，开发中）

**思想**：每个租户独立 DataSource + 独立连接池 + 独立数据库/实例——物理级故障域。

### 设计要点（V1.13 预研，docs/multi-tenant-instance-research.md）

1. **TenantInstanceRegistry**：租户 → DataSource 注册表，支持运行时开通/停用（无重启）；
2. **动态路由 DataSource**：基于 `TenantContext` 路由，未注册租户 fail-closed；
3. 连接池参数按租户配额（max-pool-size / min-idle 独立配置）；
4. 与 V1.14 租户生命周期管理 REST API 衔接（`/api/admin/tenants` CRUD + 配额）。

### 适用场景
- 金融/政务/医疗：客户数据必须独立数据库驻留；
- 大客户需要独立 SLA、独立容量；
- SaaS 多租户开放 MCP 能力（外包价目最高档）。

## 三档怎么选（决策表）

| 判据 | Row | Schema | Instance |
|------|-----|--------|----------|
| 租户数 | 多 | 中 | 少而大 |
| 单租户数据量 | 小 | 中 | 大 |
| 合规强度 | 低 | 中高 | 最高 |
| 成本 | 低 | 中 | 高 |
| 隔离强度 | 逻辑 | 物理（库内） | 物理（实例级） |
| 落地成本 | 1 天 | 1-2 天 | 需建库/账号 |

**我们的工程原则**：三种模式同一套 `X-Tenant-Id` 约定和 `TenantContext`，切换模式业务代码零改动——先在 Row 上跑通业务，合规升级时切 Schema，大客户来了再上 Instance。

## 开源地址与下一步

项目：https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise
当前能力：RBAC + OAuth2（含刷新令牌轮换/重用检测）+ 审计日志 + 限流 + 工具注册中心 + REST/Stateless HTTP 传输 + **Row/Schema 双模式多租户** + Spring AI Alibaba 集成 + 监控（Prometheus/Grafana）。

近期路线：Instance 级多租户（V1.13）→ 租户生命周期管理 REST API（V1.14）→ 三档隔离完整故事线的企业白皮书。

---

*如果这篇文章对你有帮助，欢迎 Star 项目。企业用户要接多租户、OAuth、审计合规的，可以直接开 Issue 讨论。*