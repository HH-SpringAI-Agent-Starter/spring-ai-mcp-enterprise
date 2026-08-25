# 多租户隔离技术预研 — Spring AI MCP Enterprise

> 版本：V1.11 功能候选 | 日期：2026-08-25
> 目标：为「SaaS 型 MCP 平台」（报价 $40-80K 档）做多租户隔离技术选型，评估在现有 V1.9（OAuth2/EMA）基础上落地多租户的路径。

## 一、为什么现在需要多租户

- 市场信号：MintMCP（MCP Gateway）获 Cowboy Ventures + Coatue 投资，MCP 网关/治理赛道资本验证；企业采购从「单连接器」走向「平台」；
- 商业信号：报价单中「企业 MCP 平台 $25-60K / Agentic 平台 $50-90K」档的核心差异点 = 多租户隔离 + 治理；
- 技术信号：AAIF 认证目录 Q4 2026 落地，仅 12.9% 服务器达「高信任分」，多租户隔离是认证加分项。

## 二、三种隔离模型对比

| 模型 | 隔离级别 | 成本 | 适用规模 | 说明 |
| --- | --- | --- | --- | --- |
| **共享 Schema（Row-level）** | 数据行级 | 低 | 中小型（<50 租户） | 所有租户共用表，每行带 `tenant_id`；最易落地 |
| **独立 Schema（Database-per-tenant）** | 逻辑级 | 中 | 中型（50-500 租户） | 每租户独立 schema，表结构一致；迁移成本中等 |
| **独立实例（Dedicated）** | 物理级 | 高 | 大型/合规敏感 | 每租户独立 JVM/容器/数据库；隔离最强，运维成本最高 |

## 三、与现有 V1.9 OAuth2 模型的关系

现有架构：`OAuth2 Client Credentials → client_id + scope + roles → 资源服务器校验`。

多租户落地 = 在 OAuth2 之上叠加 **tenant 维度**：

```
client_id ──→ (client_tenant_mapping) ──→ tenant_id
scope ──────→ 工具权限（已有）
tenant_id ──→ 数据行过滤 / schema 路由（新增）
```

关键设计点：
1. **Tenant 解析**：从 JWT claim（`tenant_id`）或 client 注册表映射获得，绝不信任客户端传入的 header；
2. **数据隔离**：`database_query` 工具自动注入 `WHERE tenant_id = ?`（共享 Schema 模型）或动态切换 DataSource（Schema/实例模型）；
3. **管理面隔离**：API Key 管理、审计日志、限流配额均按 tenant 维度独立；
4. **审计增强**：审计日志新增 `tenant_id` 字段，支持按租户导出（合规要求）。

## 四、落地路径建议（分三步）

### Step 1（V1.11）：Row-level 隔离
- `mcp-tenant` 模块：`TenantContext`（ThreadLocal + Reactor Context 传播）、`TenantResolver`（JWT claim / header 映射）、`TenantAwareJdbcTemplate`（自动注入 tenant 条件）；
- `database_query` 工具改造：默认强制 `tenant_id` 过滤，`MCP_TENANT_ISOLATION=STRICT` 时禁止无租户查询；
- 审计日志加 `tenant_id` 字段。

### Step 2（V1.12）：Schema 级隔离
- 动态 DataSource 路由（`AbstractRoutingDataSource` + tenant 映射）；
- 租户注册/迁移 API（`POST /api/v1/tenants`、`POST /api/v1/tenants/{id}/migrate`）。

### Step 3（V1.13+）：实例级隔离 + 计费
- K8s Operator 按租户拉起独立 Deployment（参考 k8s/ 目录）；
- 用量计费（工具调用次数/Token）→ 对接报价单按量计费模式。

## 五、参考实现（社区）

- Spring 官方多租户模式（Hibernate `tenant_id` 过滤器 / Spring Data 多租户扩展）；
- `AbstractRoutingDataSource` 动态数据源方案（成熟、文档多）；
- 云厂商托管方案：AWS SaaS Builder Toolkit、阿里云 SaaS 加速器（国内交付可参考）。

## 六、对报价单的意义

| 报价档 | 所需能力 | 现状 | 差距 |
| --- | --- | --- | --- |
| 单连接器 $8-15K | 单租户 | ✅ V1.0-V1.9 全部具备 | 无 |
| 企业平台 $25-60K | + 多租户(行级) + 管理面 | ✅ OAuth2/RBAC/审计 | Row-level 隔离（V1.11） |
| Agentic 平台 $50-90K | + Schema/实例级 + 计费 | ⚠️ 部分 | Step 2-3（V1.12+） |

**结论：多租户 Row-level 隔离是下一个高 ROI 功能，直接解锁报价单中档价位。**

## 相关文档

- [企业采购对照表](enterprise-rfp-checklist.md)（RFP 差距清单：多租户列 V1.11+）
- [兼职报价单](mcp-freelance-offer.md)
- [OAuth2 企业授权指南](oauth2-guide.md)
- [架构说明](architecture.md)
