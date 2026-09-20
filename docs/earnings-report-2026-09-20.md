# 日报 2026-09-20 — V1.29：Governance AuditSink JDBC 化（审计事件落库）

## 一、今天做了什么

1. **V1.29 核心：审计事件落库（`JdbcGovernanceAuditSink`）**
   - 解决 V1.26 遗留的企业级缺口：审计只活在单机内存 + 日志 → 重启即丢、多实例无全局视图；
   - 单表 `mcp_governance_audit`，方言无关（H2/MySQL/PG/SQL Server），
     **只写不删**（防篡改）、**fail-soft**（审计不挂业务）、**近实时双写**（落库+SLF4J 并存）；
   - `audit.store=memory|jdbc` 可配，无数据源自回退内存（可用性优先）；
   - 细节打磨：`seq` 列解决同毫秒排序确定性；值级截断保证落库 JSON 合法可反序列化。
2. **测试**：governance 模块 43 个全绿（新增 8 个 H2 集成测试）；全仓 21 模块 `mvn install` 通过。
3. **文档**：governance-guide 新增第 8 章「审计事件持久化」（含 SQL 查询示例）；
   V1.29 release notes 发布；新增掘金/CSDN 稿件（审计落库主题）。
4. **市场雷达 09-20**：捕捉 OneSeven（Java+Spring+WebFlux MCP 岗，$4-5K/月，全匹配）、
   沃尔玛中国（AI/MCP 网关岗 ¥30-55K，Spring AI 生态开源贡献优先）、
   Lifted（Upwork 旗下，Java/Go MCP 岗）、箱箱共用（上海，20-30K·13薪）等新信号；
   价格带交叉验证（$8K MVP 到 $150K+ 企业多租户），赛道趋势（业务应用类 MCP 第二波、审计采购显性化）。

## 二、为什么做这些

- **V1.29 立项依据**（昨日 release notes「下一步」第 1 条 + 市场雷达行动建议）：
  V1.28 把审批状态落库后，「审计事件不落库」成了合规叙事里最明显的短板——
  企业安全团队的取证三连问（谁批的/调了什么/重启后还在吗）必须闭环；
- **技术选型复用已验证模式**：V1.22 JdbcSkillRegistryStore + V1.28 JdbcApprovalStore 的
  「方言无关 JDBC」套路第三次复制，成本低、风险小、风格统一；
- **市场依据**：LaunchDay Advisors 本周文章把「audit log as a TODO」称为最贵的偷懒
  （薄配团队半年后返工 30-50%）；「审计落库」正是把项目从 $100K 档「认真版」
  往企业采购清单上推的关键差异点，也是 OneSeven 这类 Java+MCP 岗位面试的硬通货。

## 三、明天做什么（候选）

1. **Kafka AuditSink**（同 SPI 换实现，量级升级路径，可选延后）；
2. **OneSeven 投递包**：GitHub 链接 + 版本叙事（V1.8 OAuth2 → V1.24 观测 → V1.26 治理 → V1.28/29 JDBC 落库）
   + 三句话话术；沃尔玛中国（猎聘）同步投递，主打「开源 Spring AI 生态贡献」加分项；
3. **OpenTelemetry trace 关联**：审计事件与 traceId 打通；
4. **NTT DATA（09-18 捕捉）跟进**：弱身份/无生产所有权红旗的正面回应材料已齐（OAuth2+治理+落库），
   整理成可发送的英文一页摘要。