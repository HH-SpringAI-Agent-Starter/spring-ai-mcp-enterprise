# 银行智能体平台「底座期」采购潮 · 逐条应答与行动包

> **V1.67 · 2026-11-09（周一）** ｜ 适用：兴业银行 / 北京农商 / 上海农商智能体平台征集，广发证券三份征集（含 11-12 17:30 最紧窗口），中原银行「安全围栏 + AI 网关」组合采购。
> 情报原文见 [`earnings-report-2026-11-09.md`](earnings-report-2026-11-09.md)，报价锚见 [`mcp-governance-pricing-anchor-2026-11-09.md`](mcp-governance-pricing-anchor-2026-11-09.md)。
> ⚠️ **全部为预征集 / POC 征集，非采购邀请、非资格预审**；目标＝进供应商库。资质不足组联合体，**禁止虚构材料**。

---

## 一、为什么今天必须动手（窗口表）

| 项目 | 截止 / 开标 | 剩余 | 本期动作 |
| --- | --- | --- | --- |
| **广发证券《2026 态势感知及安全分析平台扩容项目》** | **11-12 17:30** | **3 天（最紧）** | 原厂或授权代理 + ≥2 金融案例 + ≥2 安全项目案例；主打「MCP 安全工具管理 + 安全技能库 + 统一日志底座」 |
| 广发证券 13321《智能金融资讯数据服务平台》 | 11-13 17:30 | 4 天 | 沿用 [`gf-securities-tender-response-2026-11-07.md`](gf-securities-tender-response-2026-11-07.md) 12 条应答 + 本期云网关问答 |
| 广发证券 13322《2026 企业微信持续建设项目》 | 11-13 17:30 | 4 天 | 同上；注意**指定 Dify / hiagent 开发 Agent** → 用「平台做编排 + 外挂 Java 治理层」 |
| 厦大 / 军队 190 万 / 中山 399 万 | 11-11 / 11-16 / 11-17 | 2 / 7 / 8 天 | 备「治理 + 网关 + 等保」素材 |
| 山东重工集团级平台二次标 / 呼市 900 万 / 中央民大 3712 万 | 11-20 / 11-23 / 11-25 | 11 / 14 / 16 天 | 「统一安全管控」联合体素材 |
| 上海黄浦 306 万 / 上海消防 713.75 万 | 11-30 开标 | 21 天 | ⚠️ 未点名 MCP，仅作相邻机会跟踪 |
| 兴业 / 北京农商 / 上海农商 智能体平台 | 征集 / POC 阶段（无统一截止） | 持续 | 本期新增逐条应答表（第二节） |

---

## 二、银行「买底座不买场景」逐条应答（本期核心）

采购标的已从「买一个场景」变成「买一套开发与运营底座」，且把 Skill 数量、MCP 市场、评测观测运营全生命周期写进文件 → **有可验收的工程口径**。逐条对位：

| # | 买方要求（公告/报道逐字） | 本框架模块 | 仓库真实文件 / 证据 | 应答话术 |
| --- | --- | --- | --- | --- |
| 1 | 北京农商「覆盖智能体**开发、纳管、调用、评测、观测、运营全生命周期**」 | `mcp-registry` + `mcp-monitor` + `mcp-governance` | `mcp-registry/`（工具注册与生命周期）、[`examples/monitor-alert-rules/`](../examples/monitor-alert-rules/README.md)（Prometheus 4 组 15 条 + 应用内 6 条） | 「全生命周期不是六个产品拼起来，是同一份工具元数据贯穿注册、调用、评测、告警、留痕；我们提供统一注册表 + 统一埋点契约」 |
| 2 | 北京农商「Skill 和工具广场…内置通用 Skill 与工具**不少于 50 个**，支持自然语言自动生成 Skill、本地 Skill 导入」 | `mcp-registry` + `mcp-tools` | `mcp-registry/`（工具注册/版本）、`mcp-tools/`（工具定义与描述）、`mcp-integrations/` | 「工具广场的核心是**注册 + 版本 + 审核状态**，不是数量；我们提供默认 disabled 待审核启用（对齐 Uber 800+ server 生产实践），50 个 Skill 只是填充物」 |
| 3 | 上海农商「支持 **Skill、MCP 插件市场**功能，内置单据 OCR、智能数据查询等金融行业插件」 | `mcp-registry` + `mcp-gateway` + `mcp-integrations` | `mcp-gateway/`（联邦上游）、`mcp-integrations/`（出站集成）、`examples/spring-ai-alibaba-mcp-server/` | 「插件市场必须带**安装前只读审计 + 工具描述快照比对**，否则每装一个插件就是一次供应链投毒机会」 |
| 4 | 上海农商「解决企业缺乏**智能体测评、发布、权限管理**问题」 | `mcp-governance` + `mcp-auth` + `mcp-tenant` | `mcp-governance/`（RBAC/Scope-ACL/HITL/参数校验）、`mcp-auth/`（Bearer + jti + 吊销） | 「发布即授权：工具未通过审核不进入列表；权限按客户经理归属隔离（对齐广发 13322 关键指标）」 |
| 5 | 兴业「**灵活插件集成与调用** + 主流大模型支持 + 流程自主规划 + 可视化编排」 | `mcp-integrations` + `mcp-spring-boot-starter` + `mcp-gateway` | [`docs/spring-ai-alibaba-compatibility-note-2026-11-01.md`](spring-ai-alibaba-compatibility-note-2026-11-01.md) | 「**核心治理层基于官方 Spring AI SDK，Alibaba 仅可选兼容层、不进主 reactor**」→ 避免被上游停更绑架（V1.59 固化口径） |
| 6 | 中原「**大模型安全围栏 + 企业级 AI 网关**」组合采购 | `mcp-governance` + `mcp-gateway` + `mcp-core` | [`examples/governance-hardening/`](../examples/governance-hardening/README.md)（4MiB 上限 / fail-closed / 同源重定向 / 会话按用户 / 工具默认禁用 / 按 caller 授权 / HITL / 审计） | 「围栏与网关不是两个产品：同一套策略在注册期与调用期各执行一次」 |
| 7 | 广发扩容「**MCP 安全工具管理 + 安全技能库管理**」+ 6 个安全智能体 + **AI 安全数据底座**（统一日志模型 / 实体识别与关联） | `mcp-governance` + `mcp-audit` + `mcp-monitor` | `mcp-audit/`（append-only）、`mcp_security_*` 10 指标、`examples/audit-ropa-export/` | 「统一日志模型要包含 who / tool / args / decision / reason 五元组；告警规则见 15 条 Prometheus 规则，可直接导入」 |
| 8 | 11-09 判例「员工用哪个工具、处理什么文件」需可追溯 | `mcp-governance` + `mcp-audit` | 同上 + [`enterprise-rfp-checklist.md`](enterprise-rfp-checklist.md)（18→20 类攻击面逐条验收） | 「制度最后一公里是工具级准入 + 生成内容纳入留痕复核，不是再发一份员工手册」 |
| 9 | 英国五大行封禁外部 Agent →「外部 Agent 只受控只读接入 + 强认证不可绕过」 | `mcp-auth` + `mcp-governance` + `mcp-tenant` | `mcp-auth/`、工具级 Scope-ACL、HITL | 「读工具默认放行、写工具默认 HITL、交易类工具默认禁用；强认证在服务端校验，不信任模型自称身份」 |
| 10 | 「日志不可篡改 / 数据权限按归属严格隔离」（广发 13322 原话） | `mcp-audit` + `mcp-tenant` | `JdbcGovernanceAuditSink` / `McpTenantFilter` / `TenantAwareJdbcTemplate` | 「append-only 审计 + 租户感知 JDBC，跨租户查询返回空集，可作现场演示用例」 |

**可演示案例（11-12 / 11-13 报名材料附件）**：
- [`examples/connect-path-guard/`](../examples/connect-path-guard/README.md)：`$(...)` 握手期 RCE 载荷被拒 + 正常 https URL 对照组通过（6 攻击 + 3 对照断言）；本期新增 `McpResolvedIpGuard`（解析后真实 IP 校验，兑现 V1.66 待办⑤）。
- `examples/tool-input-grammar/`（本期新增）：官方 `mcp-server-sqlite` 的 `VACUUM INTO` 前缀检查绕过被 per-tool grammar 拦下（8 攻击 + 对照组）。
- `examples/audit-ropa-export/`：GDPR ROPA 七字段 JSON/CSV 导出 + 完整性自检。
- 三个示例均为**纯 JDK 零依赖、`javac` 编译 + `main()` 自检全绿**，不进 reactor、零构建风险。

---

## 三、买方必问的 5 个问题（提前备好答案）

| 买方问题 | 建议回答（诚实版） |
| --- | --- |
| 「Azure / AWS / 腾讯云 / 现在 Google Cloud 都有 MCP 网关了，为什么还要你？」 | 「云网关管**流量与成本**，我们管**责任认定**：跨运行时签字、私有化合规工件、存量 Java 权限复用、连接路径准入。Strac 8 家横评的原话是『通过数据保护审查的候选名单短得多』——我们做的是让名单变短的那部分。」完整对照见 [`enterprise-cloud-gateway-comparison-2026-11-08.md`](enterprise-cloud-gateway-comparison-2026-11-08.md) |
| 「官方 SDK 升级到最新版不就安全了吗？」 | 「11-08 官方 `modelcontextprotocol/servers` 参考实现一次性被披露 7 个 CVE；Anthropic 对 STDIO 架构缺陷明确 **Won't Be Patched**，唯一缓解是 STDIO command allowlist 且不是 SDK 默认。规范里**授权是 OPTIONAL 的**——协议不保证调用经过认证。」 |
| 「你们有金融行业成功案例吗？」 | ⚠️ **诚实回答：本项目目前没有商业合同或中标记录**，不能虚构。可改为「提供可运行的治理参考实现 + 逐条合规对照 + 与公开中标项目（ZC26G280436 683 万）的能力对标」，并**主动提出与原厂/集成商组联合体**满足案例门槛。 |
| 「指定 Dify / hiagent 开发 Agent，你们怎么接？」 | 「Gartner 首个 AI Agent MQ 已指出各家最深治理只覆盖自有 runtime、第三方运行时管不住。我们**不抢编排**，在工具接入层外挂统一策略：买方指定平台上发起的跨运行时工具调用仍能被本层拒绝并留痕。」 |
| 「报价为什么比别家高一个数量级？」 | 「别家报的是『把系统接上』（$8K–15K），我们报的是『通过数据保护审查并留下可审计证据』（生产级 $80K–220K）。价差对应的是 audit/SSO/RBAC/versioning/多租户/可观测，见 [`mcp-governance-pricing-anchor-2026-11-09.md`](mcp-governance-pricing-anchor-2026-11-09.md)。」 |

---

## 四、资质自检（不满足就组联合体，不要硬投）

- [ ] 独立法人 / 成立 ≥3 年 / 注册资金 ≥500 万
- [ ] **原厂或授权代理商**（广发扩容明确要求；如用第三方组件需原厂有效授权说明）
- [ ] 近 2–3 年 ≥1–2 个**金融行业**成功案例及证明材料（广发 13321 要 1 个、13322 要 2 个、扩容要 2 个金融 + 2 个安全）
- [ ] 纳税人信用评级 / 无欠税证明 + **近两个月完税凭证**
- [ ] **广州本地服务团队**（13322 明确要求，能提供上线后运维与 MCP 工具持续扩展）
- [ ] 与其它报名服务商无关联关系
- [ ] 近三年无重大违法记录 / 无违背社会责任不良信息

> 本项目为**个人开源项目**，上述资质大概率不满足 → **推荐路径：作为技术分包 / 联合体成员，向具备资质的原厂或广州本地集成商提供治理层组件与应答素材**，而非独立报名。这句话必须写进内部结论，避免浪费窗口。

---

## 五、6 天行动清单（11-09 → 11-13）

| 日 | 动作 | 产出 |
| --- | --- | --- |
| **11-09（今晚）** | 归档本期情报、报价锚、本应答包；把本期两个新示例编译自检结果截图留档 | 本文档 + 3 份 docs + 2 个 examples |
| **11-10** | 把 [`enterprise-cloud-gateway-comparison-2026-11-08.md`](enterprise-cloud-gateway-comparison-2026-11-08.md) 的 11 维对照 + 5 条买方问答压成 **1 页 PDF**；把本应答包第 2 节表格抽成附件 | 报名材料附件 A/B |
| **11-11** | 核授权 / 案例 / 完税 / 广州本地团队四项硬门槛；列出 3 家可组联合体的原厂或集成商 | 联合体候选清单 |
| **11-12** | **态势感知扩容 17:30 截止**：主攻「MCP 安全工具管理 + 统一日志底座」，附 15 条告警规则与 ROPA 导出示例 | 提交或明确放弃并复盘 |
| **11-13** | **13321 + 13322 17:30 截止**：沿用 V1.65 应答包 + 本期「治理层=定价权」与七 CVE 可演示案例 | 提交或明确放弃并复盘 |
| **11-14 起** | 山东重工 11-20 / 呼市 11-23 / 中央民大 11-25 备「统一安全管控」素材；跟踪兴业/北京农商/上海农商正式招标文件发布 | 下期情报 |

---

## 六、诚实边界（延续 V1.65~V1.67 口径）

- 广发三份、北京农商、上海农商均为**供应商预征集 / POC 征集**，公告明写「非采购邀请」「提交合格资料不必然获得正式邀请」。
- 黄浦 306 万 / 上海消防 713.75 万 / 知识工场 89 万**未点名 MCP**，不计入可投清单。
- 「资质门槛硬、建议联合体、禁虚构材料」三条**不可因窗口紧张而放宽**。
- 本框架**无商业合同 / 无中标记录 / 无金融行业案例**；`examples/` 全部为参考实现，`mcp-monitor` 未导出 `exportPrometheus()` 到 actuator（延续 V1.66 待办）。
- 报价锚来自卖方销售页，非成交价；本期所有情报校验日期 2026-11-09。

---

_整理：`spring-ai-mcp-enterprise` V1.67 每日开发任务（挣钱部分）。_
