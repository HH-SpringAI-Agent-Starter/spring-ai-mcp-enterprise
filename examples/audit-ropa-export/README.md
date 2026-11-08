# GDPR ROPA 七字段导出示例（Reference Only）

> V1.66 新增 · 2026-11-08 · **兑现 V1.65 待办 ⑤** · 不进 Maven reactor、零外部依赖
> `javac RopaExportService.java && java com.example.mcp.audit.ropa.RopaExportService` 直接产出 JSON + CSV。

---

## 为什么这个目录值钱

V1.65 把「GDPR ROPA 是采购文档」写进了 [`docs/enterprise-rfp-checklist.md`](../../docs/enterprise-rfp-checklist.md)：

> Peliqan《GDPR-Compliant MCP Servers: The 2026 Procurement Reference》指出 **ROPA（处理活动记录）是 DPA 问询、网络保险承保、SOC 2 审计都会索要的唯一工件**，要求 MCP server 自动产出七类元数据；自动化每年为 DPO 省 **40–80 小时**。

**采购应答里写「符合等保」不如写「这七类元数据由哪个类产生、怎么导出」。** 本目录把这句话变成可运行的交付物：一份能直接交给法务/审计的 JSON，一份能给采购用 Excel 打开的 CSV，以及导出前的强制完整性自检。

---

## 七字段 ↔ 数据来源 ↔ 招标条款

| # | ROPA 字段 | 本框架数据来源 | 广发 13322 号公告关键指标 |
| --- | --- | --- | --- |
| 1 | `purposeTag` per-tool purpose | `ToolDefinition` + `SkillSpec` 元数据 | 「工具定义与描述」「技能可复用、可编排」 |
| 2 | `dataCategories` per-connector 分级 | 连接器注册时的数据分类标签 | 「参数校验」「权限可控」 |
| 3 | `dataSubjects` 数据主体类别 | `McpTenantFilter` / `TenantContext` | 「数据权限**按客户经理归属严格隔离**」 |
| 4 | `recipients` 接收方 | `mcp-gateway` 联邦上游 + `mcp-integrations` 出站记录 | 「与券商核心业务系统（客户/账户/交易/留痕）对接」 |
| 5 | `thirdCountryTransfer` / `sccReference` | 出站白名单 + 部署形态（私有化＝不出境） | 私有化部署 / 信创要求 |
| 6 | `retentionDays` 保留期 | 审计库分区策略 | 「服务行为自动留痕」 |
| 7 | `securityControls` 安全措施 | `JdbcGovernanceAuditSink` / `McpAuditLogger` append-only | 「**日志不可篡改**」 |

---

## 用法

```java
RopaExportService svc = new RopaExportService(entriesFromRegistryAndAuditDb());
svc.assertExportable();        // 七字段缺一即抛异常，不交半成品
String json = svc.exportJson(); // -> DPA / 保险 / SOC 2
String csv  = svc.exportCsv();  // -> 采购 / 法务 Excel
```

生产集成三步：

1. `collect()` 换成从注册中心 + 审计库查询，别用硬编码样例；
2. 把 `exportJson()` 挂到管理端点，**导出动作本身也要落审计**（谁在什么时候导出了全量 ROPA）；
3. 保留 `lastReviewed`，配定期复核（样例中三条均为 2026-11-08）。

---

## 自检（含对照组）

`main()` 构造三条样例（客户资产查询 / 公开资讯检索 / 技能包导出），断言：

- ✅ JSON 含 `schema: mcp-ropa/v1` 与 `processingActivities`；
- ✅ CSV 含审计接收方 `append-only 审计库`；
- ⛔ **对照组**：七字段全空的记录必须被 `IllegalStateException` 拦下，不允许静默导出一份 DPA 会退回的半成品。

V1.66 已实测 `self-test OK (3 activities + 1 negative control)`。

> ⚠️ 诚实边界：本示例是**导出格式与完整性校验**的参考实现，不是法律意见；第三国转移 / SCC 判定、保留期合法性需由 DPO 按实际部署确认。样例中「私有化＝数据不出境」仅在确实无跨境出站时成立。

---

## 参考

- Peliqan GDPR 采购参考：https://peliqan.io/blog/gdpr-compliant-mcp-servers/
- 采购对照表：[`docs/enterprise-rfp-checklist.md`](../../docs/enterprise-rfp-checklist.md)
- 广发应答素材包：[`docs/gf-securities-tender-response-2026-11-07.md`](../../docs/gf-securities-tender-response-2026-11-07.md)
