# 日报 2026-09-19 —— V1.28：Governance ApprovalStore JDBC 化

## 一、今天做了什么

1. **V1.28 核心：审批队列落库（`JdbcApprovalStore`）**
   - 解决 V1.26 遗留的企业级缺口：多实例部署下审批状态不可见 → HITL 闭环断裂；
   - 单表 `mcp_approval_requests`，方言无关（H2/MySQL/PG/SQL Server），
     读穿式无一致性窗口，`CONSUMED` 防重放升级为集群承诺；
   - `approval.store=memory|jdbc` 可配，无数据源自动回退内存（可用性优先）；
2. **测试**：governance 模块 35 个测试全绿（新增 7 个 H2 集成测试）；
3. **文档**：governance-guide 新增「审批状态持久化」章节（含表结构/启用方式/一致性语义）；
   README 治理模块更新；V1.28 release notes 发布；
4. **市场雷达 09-19**：web_search 检索窗口 09-16~09-19 ——
   捕获 NTT DATA（Java+MCP+OAuth2，近期最匹配）、CloudIngest 保险批量合同、
   Upwork MCP Expert（$60-120/h×50 人）等新信号，价格带与卖点提炼入库。

## 二、为什么做这些

- **V1.28 的立项依据**（昨天 release notes 的「下一步」第 1 条 + 09-18 市场雷达行动建议）：
  企业级 $15K-$40K 档演示能力需要「多实例审批共享」——审计合规 + 高可用叙事缺一不可；
- **技术选型复用 V1.22 已验证模式**：JdbcSkillRegistryStore 的方言无关 JDBC 方案
  已在 registry 落过地，本次同构复制到 governance，代价低、风险小、风格统一；
- **市场侧**：NTT DATA 的 Recruiter 红旗（"weak identity and authorization"、
  "no production API ownership"）反向验证——本项目 28 个版本把红旗全部变成护城河。

## 三、明天做什么（候选）

1. **Governance AuditSink JDBC 化**（审计事件落库，V1.29 主题，同一 SPI 套路）；
2. **NTT DATA 投递包**：GitHub 链接 + 版本叙事（V1.8 OAuth2 → V1.24 观测 → V1.26 治理 → V1.28 JDBC）
   + 三句话话术；OneSeven / 沃尔玛中国继续跟进；
3. **Upwork 服务产品上架**（Enterprise Java MCP Server + Governance，
   对标 $2,500 档：安全加固 + Docker + 审批流 + 观测）；
4. 掘金/CSDN 稿件（V1.28「审批状态持久化」主题，扩大 SEO 覆盖）。

## 四、数字面

| 指标 | 值 |
|---|---|
| 版本 | V1.28（累计 28 个 release） |
| governance 测试 | 35 全绿（新增 7） |
| 变更文件 | 11 个 |
| 新增市场信号 | NTT DATA / CloudIngest / Upwork MCP Expert 等 |

*相关：[V1.28 发布说明](V1.28-release-notes.md) · [市场雷达 09-19](market-research-2026-09-19.md) · [治理指南](governance-guide.md)*