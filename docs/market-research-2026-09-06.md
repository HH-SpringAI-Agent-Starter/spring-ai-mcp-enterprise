# 市场雷达 2026-09-06 — MCP/A2A 企业需求 · 招聘 · 招标价目

> 扫描窗口：2026-09-04 ~ 09-06（近 3 天），聚焦 Java + Spring + MCP/A2A/OAuth2/scope/Skill Registry
> 对应动作：V1.21（Skill Registry 技能注册表 + 版本治理 + 灰度 + 回滚 + proposal 模板库）已按本雷达信号落地

---

## 一、近 3 天高价值招聘 / 外包信号（新发现）

| 公司 / 岗位 | 地点 / 形式 | 薪资 | 关键技术要求（与本项目卖点对应）|
| --- | --- | --- | --- |
| **禾蛙/迅致直营 — MCP平台开发工程师** | 上海（985/211 本科）| **¥80–120万/年 ×12薪 + 股票激励** | **基于开源 MCP 生态搭建企业级平台：自研 MCP Server/Client + 企微×Claude Enterprise 打通（身份鉴权/消息路由/权限管控/沙箱隔离）+ Skill 标准化运行环境（注册、版本管理、灰度发布、故障回滚）+ 开发者文档与工具链** ｜ 2 天前刷新，佣金 166K —— **本项目 V1.21 mcp-registry 逐条命中"Skill 注册/版本/灰度/回滚"** |
| **Anthropic — MCP Engineer（Enterprise）** | SF / NYC | **$300K–320K/yr** | 审查合作伙伴 MCP Server 架构（安全/可扩展性/合规）、OAuth 与企业身份实施咨询、部署模式咨询——"能审查 MCP 安全"成官方付费能力 ｜ 新出现 |
| **Anthropic — Staff+ SWE Platform Connectivity (AI)** | London / SF | **$405K–485K/yr** | **MCP proxy + OAuth/token 管理 + 企业权限控制**、SLO/oncall、参与 MCP 规范与官方 SDK ｜ 新出现 |
| **Cognizant — Agentic AI MCP Integration Specialist** | Boise 远程（美）| **$98K–115K/yr** | MCP Server/connector 设计 + ServiceNow/Jira/SharePoint/Databricks/Snowflake 集成 + **OAuth2/OIDC/Entra ID/RBAC** + 可观测性；**申请截止 2026-09-09（还有 3 天）** ｜ 截止临近 |
| **NTT DATA — MCP and Enterprise Integration Engineer** | Hyderabad/Noida | 未公开（企业集成大厂）| Java/Python/TypeScript + **OAuth2/OIDC/mTLS/服务身份/最小权限 + 工具级可观测性 + 合同测试**；Recruiter 红旗：只会消费 MCP 工具不会实现 Server、identity/authorization 知识弱、无生产 API 所有权 ｜ 持续在招 |
| **CareerFlow — AI SWE (MCP Development / AI R&D)** | 远程 NA/LATAM | **$104K–145K/yr 或 $50–70/hr** | 维护开源 **MCP Toolbox for Databases** + 测试套件 + 技术博客转示例代码；6 个月合同，40h/周，PST 重叠 6h ｜ 持续在招 |
| **智联招聘 — AI+MCP项目开发工程师** | 大庆（兼职/临时）| **5000–10000 元/次** | Java/C#，3 年以上 AI 驱动 MCP 项目经验，MCP 记忆/控制器/规划器模块 + RAG/Agent 编排 + Docker/K8s；有开源 MCP 项目贡献优先 ｜ 新出现（零散计价市场信号）|
| **OneSeven Tech — Sr Backend Engineer MCP Infrastructure** | LATAM 远程 | **$4K–5K/月**（Deel 支付）| **Java + Spring Boot/WebFlux + MCP server 组件 + SQL Server** + 与 on-prem 系统桥接；6 个月合同可续 ｜ Java 栈匹配 |
| **Exerizon — Mid-level Java Engineer (AI Agents, MCP)** | 波兰远程（B2B）| 未公开 | **Java 17+ Spring Boot 为全球保险公司建 MCP Server**（JSON-RPC 2.0 + SSE 传输层 + 数据契约）+ Kafka；需波兰语 ｜ Java 栈匹配 |

## 二、持续在招（延续信号，仍有效）

| 公司 / 岗位 | 薪资 | 关键点 |
| --- | --- | --- |
| **Sumo Logic** — Staff SWE Core AI Platform (MCP) | $207K–243K + Equity | MCP 托管框架 + 联邦 + 多租户隔离 + OAuth/token 交换 |
| **沃尔玛中国** — 高级AI平台/应用开发工程师 | **¥30K–55K/月** | **AI/MCP 网关 + Skill/Spec Registry + 鉴权/限流/熔断/灰度** + Nacos/OPA/Higress（截止 10/27）|
| **Greelow** — MCP Developer（拉美）| $6K–9K/月 | OAuth 2.1 + per-user scoping + rate limits + audit logs |
| **WF Next（SethAI 筛选）** — 印度 MCP 开发者池 | $7K–12K/月（senior 全包）| token scoping / per-tenant quotas / audit trails 为首轮筛选 |
| **MintMCP** — SWE（企业 MCP 治理平台）| 竞争性 + 股权 | 开源 MCP server 贡献优先 |
| **Anthropic** — SWE MCP（London）| £255K–450K | MCP 规范 + SDK + 企业集成 |

## 三、行业动态（决定产品方向）

1. **Anthropic 连开 3 个 MCP 岗位，"审查 MCP 安全"成为官方付费能力**：MCP Engineer 职责就是审查合作伙伴的 MCP Server 架构并指导 OAuth 落地——这意味着"能通过安全审查的 MCP Server"已经是 Anthropic 商业护城河的一部分。本项目 `docs/security-review-checklist.md`（6 大类 15+ 审查项 → 实现位置 → 演示方式）正好是这份岗位的"面试答案 + 交付物模板"。
2. **Skill Registry 从"加分项"变成"标配项"**：禾蛙 ¥80-120万 JD 明确要求 Skill 注册/版本管理/灰度发布/故障回滚；沃尔玛 JD 要求 Skill、Spec Registry。两天内两个高价 JD 都点名同一能力——**今天 V1.21 的 mcp-registry 模块（/api/admin/skills 注册+版本+激活+回滚+灰度 /api/mcp/skills 发现）正好命中，可直接写进简历与 proposal 做代码证据。**
3. **国内 MCP 价格锚点再次上移**：上海 MCP 平台工程师 ¥80-120万/年 > 沃尔玛 ¥30-55K/月 > 智联按次 5000-10000 元。MCP 平台架构岗是当下国内最高溢价 AI 工程岗位之一，且要求"会写代码 + 读源码 + 产品思维"，开源贡献是硬通货。
4. **Java 栈需求依然坚挺且集中在传统企业**：Exerizon（保险）、NTT DATA（企业集成）、Cognizant（ServiceNow/Jira 等企业工具）、OneSeven（SQL Server）——Java+Spring 是金融/保险/零售数据打通的首选栈，与 Python 派系（Anthropic/MintMCP/TalentAlly AI 原生）形成明确分工。

## 四、用户（Java + Spring + AI）在该赛道的卖点

- **V1.21 新增"Skill Registry"武器**：注册+语义化版本管理+显式激活+故障回滚+灰度路由，12 测试全绿——直接对标禾蛙 ¥80-120万 JD 与沃尔玛 Skill/Spec Registry 要求；且比"只做 MCP Server"的候选人多出一层**平台治理**叙事。
- **全链路可演示**：V0.1 → V1.21，21 个版本覆盖 RBAC → 限流 → 审计 → OAuth2 全生命周期 → 多租户三档隔离 → MCp+A2A 双协议 → SSE → Bearer 强制鉴权 → Signed Agent Card → Scope ACL → Skill Registry → 变现通道；逐条命中本周 JD 能力清单。
- **安全审查叙事完整**：security-review-checklist 可复制为 Anthropic MCP Engineer 岗位的"审查方法论"，也可作为 NTT DATA red flags 的反向证明。
- **双协议（MCP+A2A）+ 平台治理（Skill Registry）**：市场多数候选人只会"建一个 MCP Server"，本项目是"企业级 MCP 平台"，正好对上禾蛙/沃尔玛的"平台型/中间件"要求（JD 原话："给别人用的系统"开发经验、向后兼容与 API 品味）。

## 五、待办（V1.22 候选）

- [ ] **mcp.so 注册表提交**：把 Skill Registry + A2A Signed Card + Scope ACL 特性集打标上线
- [ ] **Skill Registry 持久化**（JDBC/MySQL 版，对齐沃尔玛 Nacos/注册中心叙事）
- [ ] **企微 × Claude Enterprise 打通方案**（禾蛙 JD 实战：身份鉴权/消息路由/沙箱隔离设计稿）
- [ ] **Upwork OAuth 2.1 实操接入验证**（需用户账号）
- [ ] **内容矩阵**：V1.21 公众号/知乎/掘金发布 + mcp 中文教程系列