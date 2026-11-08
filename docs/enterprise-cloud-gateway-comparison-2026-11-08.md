# 当云厂商把 MCP 治理做成标准层：本框架的定位复核与买方应答

> 2026-11-08 · V1.66 · **售前 / 投标应答素材**（可直接摘用）
> 触发：11-03~11-08 一周内，微软、AWS、腾讯云、Celigo 先后把「MCP 治理」发布为平台标准能力。
> 定位：**这是一次诚实的定位复核，不是新一轮自夸。** 结论是「品类被验证、差异化必须换锚点」。

---

## 0. 一句话结论

**「有没有 MCP 治理」已经不再是卖点** —— 三大云 + iPaaS 都把它做成了控制台里的默认能力。
本框架接下来能卖的，是**云厂商结构性不做的四件事**：

1. **买方指定平台之外的跨运行时治理**（Gartner MQ 已实证：各家最深的控制只覆盖自有 runtime）；
2. **存量 Java / Spring 资产的 MCP 化接入层**（云网关把 REST 转 MCP 工具，但不替你重构十五年陈的 J2EE 权限模型）；
3. **私有化 + 等保 / 信创 / 密评 + 日志不可篡改**（数据不出境、审计可签字，是招标必选项）；
4. **可交付源码与可复现验收**（不是付费墙里的 Enterprise tier，是能进标书、能过代码审计、能被甲方二开的东西）。

> **金句（接续 V1.64/V1.65）**：协议负责连通、平台负责编排、云厂商负责托管，
> **而「谁对这次调用签字负责」这件事，只能由一层不绑定任何 Agent 平台、也不绑定任何云的治理层承担。**

---

## 1. 本周事实：治理成了平台标准能力

| 厂商 / 产品 | 时间 | 公开的治理能力 | 交付形态 |
| --- | --- | --- | --- |
| **Microsoft Azure API Management「AI Gateway」公开预览** | 11 月（Adaptiv 合作披露） | 集中管理 models / **MCP servers** / policies / observability；token 限流、配额、Azure AI Content Safety、备用模型回退；REST 经 OpenAPI **转成 MCP 工具**；connector-backed tools；token 指标按 consumer 流入 App Insights、prompt/completion 日志进 Azure Monitor | 托管 PaaS（Azure） |
| **Amazon Bedrock AgentCore Gateway 能力扩展** | 11 月 | 全 MCP primitives（tools/prompts/resources）；**dynamic listing** 在请求时按调用者身份实时决定工具可见性；Streamable HTTP streaming；session 按用户绑定防劫持；**elicitation HITL 三模式**（form / URL / URL-exception）；OAuth 2.0 **RFC 8693 on-behalf-of** token exchange；PrivateLink | 托管 PaaS（AWS） |
| **腾讯云 云原生智能网关 v3.9.8** | **产品动态 11-06 18:27 更新** | MCP 网关支持 **OAuth 2.0 授权码**访问后端存量 HTTP，**无需改造既有鉴权体系**；按消费者/密钥配置模型可用范围；组织节点逐级配额与成本计量；意图路由自定义 | 托管 + 私有化（国内） |
| **Celigo MCP Management 11 月版** | 11 月 | Server / **Gateway** / **Registry** 三件套；**identity-aware caller context**（从已认证请求解析身份，**不接受模型自称**，无关联人员时返回空而非无限制＝fail-closed）；registry API v0.1；end-user roles；step-level 执行日志；**安装的 MCP server 默认 disabled 待审核** | 商业 iPaaS SaaS |
| **企业标准化共识**（sim.ai 横评，11 月核） | 11 月 | 八条硬要求：审批清单与 owner / 数据分类 / workspace 级凭据而非 prompt 内嵌 / 增改批调权限分离 / 域名网络控制 / 执行记录 / schema 版本与变更控制 / 超时重试限流 | 选型方法论 |

**这不是坏消息。** 微软合作方的原话最值钱：

> 「传统 API 网关理解的是一次 HTTP 事务，**AI 网关理解的是一次 AI 交互**；传统网关不懂 token、prompt、completion、模型、上下文窗口与 AI 安全风险。」

一年前我们说「治理是 MCP 企业化的必经之路」还只是开源项目的主张；这一周它由 Azure / AWS / 腾讯云 / Celigo 的产品路线图共同背书。**品类、预算、采购条目都不用再教育了。**

---

## 2. 能力对照：哪些被覆盖、哪些天生不会被覆盖

| 治理维度 | Azure AI Gateway | AgentCore Gateway | 腾讯云 3.9.8 | Celigo | **本框架（V1.66）** |
| --- | --- | --- | --- | --- | --- |
| 工具注册 / 目录 / 版本 | ✅ | ✅ Registry 式聚合 | 部分 | ✅ Registry v0.1 | ✅ `mcp-registry` + `SkillSpec` |
| 按 caller 身份动态列工具 | 部分 | ✅ **dynamic listing** | 部分 | ✅ caller context | ✅ `CallerPolicy.hasScope` + 默认 disabled |
| 鉴权 / Scope / 令牌受众 | ✅ OAuth | ✅ RFC 8693 | ✅ OAuth2 授权码 | ✅ Identity | ✅ `mcp-auth` + `McpGatewayGuardFilter`（aud/iss/resource 强绑定） |
| 限流 / 配额 / 成本 | ✅ 强 | ✅ | ✅ **组织逐级** | 部分 | ✅ `mcp-core` RateLimit（无成本计量） |
| HITL 人工确认 | 部分 | ✅ **elicitation 三模式** | — | 部分 | ✅ 审批单 + 待审批 gauge |
| 可观测 / 审计留痕 | ✅ App Insights | ✅ | ✅ 成本明细 | ✅ step-level | ✅ `mcp-monitor` + **append-only 防篡改审计** + `mcp_security_*` 安全指标 |
| 多租户 / 数据隔离 | 平台级 | 平台级 | ✅ 组织维度 | ✅ workspace | ✅ `mcp-tenant` **按客户经理归属行级隔离** |
| **跨云 / 跨运行时 / 跨 Agent 平台** | ⛔ 绑 Azure | ⛔ 绑 AWS | ⛔ 绑腾讯云 | ⛔ 绑 Celigo | ✅ **协议级、平台无关**，与买方指定的 Dify / hiagent 并存 |
| **私有化 + 等保 / 信创 / 密评** | ⛔ | ⛔ | 部分 | ⛔ | ✅ Apache-2.0 源码可交付、可离线部署 |
| **存量 Java / J2EE 业务权限复用** | ⛔ 只转 OpenAPI | ⛔ | ⛔ 免改造但只透传 | ⛔ | ✅ 工具级 ACL **复用主 API 同一套对象授权** |
| **合规工件（ROPA / 16→18 类攻击面验收）** | ⛔ | ⛔ | ⛔ | ⛔ | ✅ `docs/enterprise-rfp-checklist.md` + ROPA 导出示例 |
| **连接路径 / 客户端准入防护** | ⛔ | ⛔ | ⛔ | ⛔ | ✅ `examples/connect-path-guard/`（V1.66） |

> 表中「⛔」不是产品缺陷，而是**交付形态决定的边界**：托管服务必须把自己作为唯一控制平面，不可能替甲方在另一个云、另一套 Agent 平台、另一套等保域里签字。

---

## 3. 买方会问的五个问题（应答话术，可直接用）

**Q1：Azure / AWS 已经有 AI Gateway 了，为什么还要单独一套？**
> 如果贵司全部 MCP 流量都在同一朵云、同一个 Agent 平台内，用云厂商网关是合理的，我们不建议重复建设。
> 但 13322 号公告写明**须使用采购方指定的 Dify / hiagent 平台开发 Agent**，且要与券商核心系统对接、日志不可篡改、按客户经理归属隔离 —— 这个场景下控制点必须落在**平台之外、协议之上**。Gartner 首个 AI Agent Management MQ 已逐家指出：各家最深的治理只覆盖自有 runtime，第三方运行时管不住。我们补的正是这一段，且不与云网关抢流量入口。

**Q2：你们的限流/配额没有云厂商的组织级成本计量，是不是弱？**
> 是，这一维我们明确不做，建议由云网关承担（分层，不重叠）。我们的边界是**鉴权决策、工具准入、写操作审批与不可篡改留痕** —— 也就是「谁对这次调用签字」。成本计量属于资源运营，不属于责任认定。

**Q3：怎么证明不是 PPT 治理？**
> 三条可复现证据：① `examples/governance-hardening/` 与 `examples/monitor-alert-rules/` 的 18 类攻击面 → 规则 → 逐条验收方式；② V1.66 起 `mcp_security_*` 埋点已在过滤器每个拒绝分支落地并可导出 Prometheus；③ `examples/audit-ropa-export/` 输出 GDPR Art.30 七字段 JSON/CSV，导出前强制完整性自检。都可以现场跑。

**Q4：官方 SDK 升级了不就安全了吗？**
> 11-06 的 `CVE-2026-111771` 恰恰相反：升级到 2.3.0 后新增的 `expectedResource` **默认关闭**，不显式启用等于没修；11-05 的 `CVE-2026-108395` 两条 SDK CVE 未进 GitHub Advisory DB，pip-audit / osv-scanner / Dependabot 全部漏报；11-06 的 opencode 同类 RCE 更是「无 CVE 的 refactor 修复」。**升级是必要条件，不是充分条件**，需要一层不随 SDK 版本变化的独立校验。

**Q5：连接阶段也要管？我们工具审批很严了。**
> `CVE-2026-119025` 里代码在**工具列表出现之前**就执行了：零工具调用、零审批提示、零模型参与，注入在 OAuth discovery 的 `authorization_endpoint` 到达 PowerShell 的路径上。工具级 guardrail 架构上看不见这一层。所以 V1.66 单独交付了 `examples/connect-path-guard/`（字符级校验 + argv 启动 + 客户端版本下限 + 远程默认 opt-in）。

---

## 4. 定位调整后的四条销售动作

| # | 动作 | 为什么现在做 |
| --- | --- | --- |
| 1 | 把本页「能力对照表 + Q1/Q3 应答」抽成一页 PDF，附在 **广发 11-13 17:30** 报名材料后 | 剩 5 天；买方大概率已看过云厂商方案，先回答「不重复建设」比先讲功能有效 |
| 2 | 售前话术从「我们有 MCP 治理」改为「**我们负责跨运行时签字 + 私有化合规工件**」 | 治理能力已被标准化，旧话术在选型会上会被直接挑战 |
| 3 | 山东重工 11-20「统一安全管控」、军队 190 万 11-16 / 中山 399 万 11-17 备标时，主打**私有化 + 等保 + 密评 + 日志不可篡改** | 托管云结构上无法满足，是真实护城河 |
| 4 | 把腾讯云 3.9.8「OAuth2 授权码访问存量 HTTP 免改造」列为**可组合项**而非竞品 | 国内甲方常已买腾讯云网关，做「云网关管流量 + 本框架管责任」的分层方案更容易进联合体 |

---

## 5. 诚实边界（务必保留，不要删）

- 本框架 **21 模块主构建未改动**，V1.66 新增内容全部在 `docs/` 与 `examples/`（不进 Maven reactor）；`examples/` 是**参考实现**，不是已集成进 `mcp-server` 的生产 Bean。
- `mcp_security_*` 指标目前由 `examples/governance-hardening/McpSecurityMetrics.java` 导出，**`mcp-monitor` 仍未内置**；生产集成需把该实例挂到 actuator 端点。
- Azure AI Gateway 为**公开预览**、AgentCore 扩展为 11 月报道，功能与 GA 状态以厂商官方文档为准，投标引用时须注明「截至 2026-11-08 公开信息」。
- 腾讯云 3.9.8 产品动态页更新时间为 **2026-11-06 18:27**，属产品文档而非新闻稿，能力描述以其文档为准。
- 本页不含任何未公开中标信息、不虚构案例与资质；预征集 ≠ 采购邀请。

---

## 参考

- Microsoft AI Gateway 公开预览（Adaptiv 合作披露）：https://adaptiv.nz/?p=996227
- Amazon Bedrock AgentCore Gateway 能力扩展：https://dev.to/om_shree_0709/amazon-bedrock-agentcore-gateway-just-became-the-enterprise-mcp-infrastructure-layer-heres-what-m4o
- 腾讯云云原生智能网关产品动态 v3.9.8（2026-11-06）：https://cloud.tencent.cn/document/product/1826/134899
- Celigo November 2026 Release / MCP Management：https://www.celigo.com/builders-hub/celigo-november-2026-release
- 企业 MCP 标准化平台横评：https://www.sim.ai/library/enterprise-mcp-server-standardization-platforms
- 完整情报：[`earnings-report-2026-11-08.md`](earnings-report-2026-11-08.md) ｜ 逐条应答：[`gf-securities-tender-response-2026-11-07.md`](gf-securities-tender-response-2026-11-07.md)
