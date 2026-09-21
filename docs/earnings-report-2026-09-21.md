# 日报 2026-09-21 — V1.30：审计检索/CSV导出/保留策略TTL（审计全生命周期闭环）

## 一、今天做了什么

1. **V1.30 核心：审计可运营化三件套（mcp-governance）**
   - **多条件检索 `search(Query)`**：tool/caller/decision/tier/from/to/limit 全字段可空过滤，JDBC 动态 WHERE 全参数绑定（方言无关 H2/MySQL/PG/SQL Server），内存实现同语义；`GET /api/admin/governance/audit` 升级支持全部过滤参数（旧调用完全兼容）；
   - **CSV 导出 `/audit/export`**：RFC 4180 + UTF-8 BOM（Excel 中文不乱码）+ 逗号/引号/换行转义（防 CSV 注入）——SIEM 导入/审计师 Excel 复核/监管报送可直接用；
   - **保留策略 TTL `/audit/prune`**：`deleteBefore(Instant)` 物理清理早于保留期的历史事件，`POST ?retentionDays=90` 返回删除条数，可配 cron 定期触发（GDPR 数据最小化）；
2. **测试**：governance 模块 49 个全绿（新增 6 个 H2 集成测试：检索过滤/时间范围/空查询=recent/limit clamp/清理只删旧/空值 noop）；**全仓 21 模块构建通过**；
3. **文档**：V1.30 release notes 发布；governance-guide 新增第 10 章（检索/导出/保留策略 + curl 示例）；
4. **市场雷达 09-21**：新增信号——中国移动（中移互联网）AI 智能体研发岗（校招，MCP Tool Server + AI 能力网关 + Skill 图谱，开源贡献优先）、Singtel MCP governance 岗、Descope（$88M 融资）MCP 安全工程师、micro1 MCP Expert $60-120/hr、武汉敏恒 Agent 岗、沃尔玛中国窗口延续至 10-27。

## 二、为什么做这些

- **立项目依据**（昨日 release notes「下一步」候选 + 市场雷达行动建议）：V1.29 打通「审计落库」，V1.30 补齐「怎么查、怎么交出去、留多久」——对应企业安全团队的完整取证问题链；
- **技术选型复用已验证模式**：V1.22/V1.28/V1.29 的「方言无关 JDBC」套路第四次复用（search 动态 WHERE + deleteBefore），成本低、风险小、风格统一；
- **市场依据**：**Singtel 把 "architect the MCP governance" 直接写进 JD**，Descope 主打「MCP 安全认证授权」，欧盟数据最小化是采购硬要求——审计检索/导出/清理正是「企业采购验收项」，把项目从 $100K 档「认真版」往 $150K+「企业多租户」档推的关键差异点。

## 三、明天做什么（候选）

1. **OneSeven 投递包执行**（GitHub 链接 + V1.8→V1.30 版本叙事 + 三句话话术）；沃尔玛中国（猎聘，10-27 截止）同步投递，主打「开源 + Spring AI 生态贡献」加分项；**中国移动校招**（10-08 截止）投递包（MCP Server + 网关 + Registry 叙事）；
2. **Kafka AuditSink**（同 SPI 换实现，量级升级路径，可选延后）；
3. **OpenTelemetry trace 关联**：审计事件与调用链 traceId 打通，事故复盘秒级定位；
4. **审计看板**：管理面板按天/工具/调用方聚合趋势图。