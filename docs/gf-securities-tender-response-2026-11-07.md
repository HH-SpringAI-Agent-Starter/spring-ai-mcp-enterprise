# 广发证券 MCP 双征集 · 应答素材包（V1.65 · 2026-11-07）

> **窗口：《智能金融资讯数据服务平台》供应商征集 2026-11-13 17:30 截止（剩 6 天）**
> 官方公告：[13321.jhtml 智能金融资讯数据服务平台](http://gfjc.gf.com.cn/gyszjgg/13321.jhtml) ｜ [13322.jhtml 2026 企业微信持续建设项目](http://gfjc.gf.com.cn/gyszjgg/13322.jhtml)
> 用途：把本仓库的模块 / 代码 / 文档映射成**征集材料里可写的应答条目与可演示案例**。不是标书代写，是「素材 + 话术 + 自检」。

---

## 0. 先说结论（三条，别自我感动）

| # | 结论 | 依据 |
| --- | --- | --- |
| 1 | **本次目标是「进供应商库」，不是中标。** 两份公告都白纸黑字写：「本次公告为招标启动前供应商预征集」「**不等于招标公告及资格预审公告**，按时提交合格资料后，并不必然获得正式采购邀请」。 | 13321 / 13322 第九条声明 |
| 2 | **技术条款几乎逐条命中本框架，但商务资质条款大概率是硬伤**：成立 3 年+、注册资金 ≥500 万、原厂或授权代理、近 3 年 ≥1 个（13322 要 ≥2 个）**金融行业**成功案例及证明材料、纳税信用评级 + 近两月完税凭证、13322 还要**广州本地服务团队**。个人 / 小团队直接以自己为主体报名，材料关过不去。 | 13321 第三条 / 13322 第三、六条 |
| 3 | **最优路径是联合体 / 授权代理**：找一家满足商务资质、有广州本地团队、有金融案例的集成商做主体，本框架作为「**MCP Server 工程能力与治理增强层**」的技术供方 + 原厂授权组件，走 13322 允许的「第三方产品/组件须取得原厂有效授权」条款。 | 13322 第三条第 3 项 |

> **⚠️ 不要做的事**：不要在材料里虚构公司年限、注册资本、金融案例或完税凭证。公告明确「提供虚假材料将被列为征集方供应商黑名单」，这是不可逆的。

---

## 1. 13322 号《企业微信持续建设项目》— 专业资质逐条应答

> 这是两份公告里**唯一把 MCP 工程能力写成资质门槛**的一份，也是本框架最有说服力的对照表。

| 公告原文要求 | 本框架对应能力 | 可演示证据（仓库内真实文件） | 建议应答话术 |
| --- | --- | --- | --- |
| **工具定义与描述** | `ToolDefinition` + `ToolRegistry` 统一元数据（名称/描述/参数 schema/风险等级） | `mcp-core/src/main/java/com/mcp/enterprise/core/ToolDefinition.java`、`ToolRegistry.java`；`docs/api-docs.md` | 「工具以声明式元数据注册，描述与参数 schema 一并进入目录，Agent 侧可发现、可版本化，不散落在各业务 Controller」 |
| **参数校验** | `McpGovernanceGuard` + `McpToolRiskClassifier` + `CachedBodyHttpServletRequest`（可重复读 body 做校验）；参考实现 `BoundedRequestWrapper`（4 MiB 上限） | `mcp-governance/.../McpGovernanceGuard.java`、`McpToolRiskClassifier.java`；`examples/governance-hardening/BoundedRequestWrapper.java` | 「入参在网关层统一校验并 fail-closed；对 `CVE-2026-108395` 类无界请求体做声明值 + 流式双限，超限 413」 |
| **鉴权与权限控制** | `McpApiKeyAuthFilter` / `McpJwtTokenProvider` / `McpOAuth2TokenEndpoint`（`mcp-auth`）；`ToolScopePolicy` + `ScopeMatcher`（`mcp-core`）；`McpGovernanceFilter` 逐工具策略 | `mcp-auth/src/main/java/com/mcp/enterprise/auth/*`；`mcp-core/.../ToolScopePolicy.java` | 「API Key / JWT / OAuth2 三通道 + 工具级 scope ACL；针对 `CVE-2026-111771` 类令牌受众绕过，强制 aud / issuer / resource 三者与所访问 server 强绑定，不匹配 401」 |
| **调用限流** | `GatewayRateLimitManager` + `RedisRateLimiter`（令牌桶，支持租户/工具维度） | `mcp-core/.../GatewayRateLimitManager.java`、`RedisRateLimiter.java` | 「网关集中限流，按租户 + 工具双维度配额，避免单个 Agent 打爆券商核心系统；支持异步任务隔离」 |
| **日志与可观测性** | `McpAuditLogger` + `McpMetricsCollector` + `McpToolInvocationMonitor` + `McpAlertService`（`mcp-monitor`）；Prometheus 规则 `config/prometheus/alerts.yml`；🆕 `examples/monitor-alert-rules/` | `mcp-monitor/src/main/java/com/mcp/enterprise/monitor/*`；`config/prometheus/alerts.yml`；`examples/monitor-alert-rules/` | 「逐调用记录 args / result / identity / approval decision，落库 + SIEM 流；16 类攻击面 → 告警规则已预置，可现场演示」 |
| **日志不可篡改**（关键指标） | `JdbcGovernanceAuditSink` + `HttpGovernanceAuditSink`（`mcp-governance`）+ `McpAuditLogger` 持久化；追加式审计流，无更新/删除接口 | `mcp-governance/.../JdbcGovernanceAuditSink.java`、`McpGovernanceAuditSink.java` | 「审计事件 append-only 落库，治理与审批决策同源留存；可按客户经理、工具、时间窗回溯，满足『服务行为自动留痕、日志不可篡改』」 |
| **数据权限按客户经理归属严格隔离**（关键指标） | `mcp-tenant`：`McpTenantFilter` / `TenantContext` / `TenantAwareJdbcTemplate` / `TenantSchemaDataSource` / `TenantModeGuard`；三种隔离模式 + 运行时生命周期 | `mcp-tenant/src/main/java/com/mcp/enterprise/tenant/*`；`docs/blog-java-mcp-multitenant-three-tier-2026-08-28.md` | 「租户上下文在请求入口解析并透传到 JDBC 层，客户经理归属作为隔离键；工具调用与 SQL 走同一套归属判定，避免『主 API 有对象级授权、MCP 工具跳过』（`CVE-2026-121458` 类）」 |
| **技能(Skill)定义/注册/编排/复用/版本管理与效果评测** | `mcp-registry`：`SkillRegistry` / `SkillSpec` / `SkillStatus`（状态机）/ `SkillDiscoveryController` / `SkillAdminController` / `JdbcSkillRegistryStore` | `mcp-registry/src/main/java/com/mcp/enterprise/registry/*` | 「Skill 作为一等公民注册，带状态（含默认 disabled 待审核启用）、版本与持久化存储；发现接口供 Agent 侧懒加载，与 Uber 800+ server 生产架构同构」 |
| **写操作 HITL / 权限可控** | `McpApprovalService` + `ApprovalRequest` / `ApprovalStore` / `JdbcApprovalStore` + `GovernanceDecision` + `RiskTier` | `mcp-governance/.../McpApprovalService.java`、`RiskTier.java` | 「按风险分级，高风险写操作返回 202 待审批，审批绑定 session + 工具版本 + 入参；对标 DigitalOcean Action Gateway 的 allow/ask/deny 与 bound approvals」 |
| **须使用我司 Dify / hiagent 平台开发 Agent** | 本框架**不替换**低代码平台：`mcp-gateway` 的 `FederatedToolExecutor` / `McpFederationManager` / `StreamableHttpUpstreamClient` 以联邦方式接入外部 MCP 端点；`mcp-spring-boot-starter` 可作为旁路治理层 | `mcp-gateway/src/main/java/com/mcp/enterprise/gateway/*`；`examples/client-java/McpEnterpriseStreamableClient.java` | 「**Agent 编排完全在贵司指定平台完成；本框架只承接 MCP 工具接入层与治理增强层**。Gartner 首个 AI Agent Management MQ 已指出 Dify 等平台『最强控制只覆盖内建 agent、第三方运行时治理有限』，本方案正是补齐该缺口」 |
| **与券商核心业务系统（客户/账户/交易/留痕）对接** | Spring Boot 原生 Java 栈、JDBC/MyBatis 生态、私有化部署；`Dockerfile` + `docker-compose.yml` + `k8s/` | `Dockerfile`、`docker-compose.yml`、`k8s/`、`docs/architecture.md` | 「存量 Java 系统无需改造成 Python/TS 栈，工具以 Spring Bean 实现 `McpToolExecutor` 自动注册；私有化 + 等保 + 信创兼容」 |
| **广州本地服务团队 / 持续迭代** | ⛔ **本框架不具备** → 必须由联合体主体提供 | — | 走联合体，见第 3 节 |

> **🎯 一句话总括（可放材料首页）**：公告要求的「工具定义与描述、参数校验、鉴权与权限控制、调用限流、日志与可观测性」五件套，在本框架中分别由 `mcp-core` / `mcp-governance` / `mcp-auth` / `mcp-core` / `mcp-monitor` 五个模块原生提供，且已通过 `examples/governance-hardening/` 与 `examples/monitor-alert-rules/` 给出可运行参考实现；`docs/enterprise-rfp-checklist.md` 的 16 类攻击面→治理模块对照表可直接作为验收清单。

---

## 2. 13321 号《智能金融资讯数据服务平台》— 技术范围逐条应答

| 公告原文 | 本框架 / 生态对应 | 应答要点 |
| --- | --- | --- |
| **MCP 工具协议** | `McpStatelessEndpoint` / `McpSseEndpoint` / `McpBearerAuthFilter`；`mcp-spring-boot-starter` 自动装配 | 支持 stateless（对齐 2026-07-28 新规范）与 SSE 双传输；官方 Spring AI 2.0 SDK 直连，Alibaba 仅可选兼容层（见 `docs/spring-ai-alibaba-compatibility-note-2026-11-01.md`） |
| **Skill 技能包** | `mcp-registry`（`SkillRegistry` / `SkillSpec` / `SkillStatus`） | 技能定义-注册-编排-复用-版本管理-评测全链路 |
| **子 Agent 编排** | ⚠️ **不由本框架承担**（应由 Dify / hiagent 等编排平台负责） | 诚实划界：治理增强层不做编排，避免与甲方平台选型冲突；本框架保证被编排的每个工具都受同一套策略约束 |
| **长期 / 会话记忆** | ⚠️ 非本框架核心（由 Agent 平台 / 向量库承担） | 同上；本框架提供会话上下文透传 `McpTraceContext` 与按用户隔离的会话指纹，防 `CVE-2026-121419` 类会话劫持 |
| **混合检索 / 多源知识融合** | ⚠️ 检索引擎非本框架职责；但工具接入层可对 Wind / 通联 / 同花顺及内部知识库做统一注册与**按订阅/entitlements 授权** | 对标 11-05 ISI Markets「Governed access」：数据访问严格按客户已订阅内容、数据 entitlements、AI 使用权授权，检索结果附来源引用 → 与券商「已采购多源数据 + 内部知识沉淀」场景同构 |
| **券商级 / 数据安全、保密及合规** | `mcp-auth` + `mcp-tenant` + `mcp-governance` + `mcp-audit`；`docs/mcp-governance-dengbao-compliance.md`、`docs/mcp-governance-regulatory-compliance-2026.md` | 私有化部署、逐工具授权、append-only 留痕、数据主体隔离；可提供 GDPR ROPA 七字段元数据导出思路（对标 Peliqan 2026 采购参考） |
| **沉淀可外部产品化的能力组件** | 模块化设计（21 个 Maven 模块，可按需组合）+ Apache-2.0 开源 + REST API 完备 | 「能力组件标准化封装」正是本项目形态：可独立发布 `mcp-governance` / `mcp-registry` 作为产品化组件，而非一次性定制代码 |

> ⚠️ **三条诚实边界必须写进材料**：子 Agent 编排 / 混合检索 / 长期记忆不是本框架的职责。把它们说成自己能做，一进场演示就会穿帮。**正确打法是「Agent 平台做编排，我们做被编排工具的治理层」** —— 这恰好也是 Gartner MQ 指出的市场空白。

---

## 3. 资质自检与联合体方案（6 天内最该做的事）

### 3.1 自检表
| 门槛 | 要求 | 个人 / 小团队 | 处理 |
| --- | --- | --- | --- |
| 成立年限 / 注册资金 | 3 年+ / ≥500 万 | ⛔ 通常不满足 | 由联合体主体满足 |
| 原厂或授权代理 | 必须 | ✅ 本框架原厂可出具组件授权 | 出具**原厂组件授权书**（13322 第三条第 3 项明确允许第三方组件，只要取得原厂授权并说明授权要求） |
| 金融行业成功案例 | 13321 ≥1 个 / 13322 ≥2 个（近 2–3 年，含证明材料 + 可演示案例） | ⛔ 通常不满足 | 由联合体主体提供；本框架提供**可演示的技术案例**（开源仓库 + 参考实现 + 16 类攻击面验收清单）作技术附件 |
| 纳税信用评级 / 无欠税证明 | 必备 | ⛔ | 由主体提供 |
| 近两月完税凭证 | 必备 | ⛔ | 由主体提供 |
| 广州本地服务团队 | 13322 明确要求 | ⛔ | 选**广州本地集成商**作主体 |
| 无关联关系声明 | 与其他报名服务商无关联 | ✅ | 主体出具 |

### 3.2 建议的联合体分工
| 角色 | 承担 |
| --- | --- |
| **主体（广州本地集成商 / 有券商案例）** | 报名、商务资质、案例与完税材料、广州本地运维、与 Dify/hiagent 平台对接、Agent 编排与检索 |
| **本框架（技术供方 / 组件原厂）** | MCP Server 工程能力五件套、治理增强层、审计与多租户隔离、私有化部署、原厂组件授权书、可演示参考实现与验收清单 |
| **数据侧（可选）** | Wind / 通联 / 同花顺数据源授权与混合检索（甲方已采购，通常由甲方或集成商承担） |

### 3.3 6 天行动清单
| 时间 | 动作 |
| --- | --- |
| 11-08（D+1） | 确定是否走联合体；若走，**立刻**联系 1–2 家广州本地有证券/银行案例的集成商，用第 1 节对照表做一页纸技术简介 |
| 11-09（D+2） | 准备原厂组件授权书模板、模块清单、License（Apache-2.0）说明；整理 `docs/enterprise-rfp-checklist.md` 16 类对照表作技术附件 |
| 11-10（D+3） | 录一段 5–10 分钟**可演示案例**录屏：工具注册默认 disabled → 审批启用 → 越权调用被拒 → 审计可检索；对应 13322「可演示案例」要求 |
| 11-11（D+4） | 主体备齐工商/案例/纳税材料；本框架准备好 `examples/governance-hardening/` + `examples/monitor-alert-rules/` 的可运行说明 |
| 11-12（D+5） | 内审材料真实性；邮件名按格式「广发证券智能金融资讯数据服务平台项目+供应商名称」 |
| **11-13 17:30 前** | 发至 `gfjc@gf.com.cn`，**并发后致电 020-66335630（李华龙）确认电子邮件发送成功**（公告明确要求） |

### 3.4 提交材料格式（公告原文）
- 附件**可编辑电子版本** + **加盖公章扫描件**；扫描件独立存为 JPG / PNG / GIF / PDF。
- 邮件主题：`广发证券XXX项目+供应商名称`。
- 声明：材料**仅供征集供应商信息和建立供应商库之用，非采购邀请**；虚假材料 → 供应商黑名单。

---

## 4. 竞标时的差异化话术（三句，别多说）

1. **「我们不替换贵司的 Dify / hiagent，我们补它自己承认缺的那一层。」** —— Gartner 首个 AI Agent Management MQ 对 Dify 的原文批评是「最强控制只针对内建 agent，第三方运行时治理有限」；AvePoint 被批「缺实时运行时控制」，Gravitee 被批「只有流量过它的网关才生效」。
2. **「11-05 和 11-06 两天，官方 SDK 连续在『令牌受众 / 资源绑定』同一个点出洞，而且升级后新校验默认关闭。」** —— `CVE-2026-123271/123272`（8.0）+ `CVE-2026-111771`；所以「升级 SDK ≠ 安全边界」，边界必须在 aud/issuer/resource 强绑定、逐工具授权、出站白名单与防篡改审计这一层。
3. **「五件套不是 PPT，是 21 个 Maven 模块里的类，今天就能演示。」** —— 现场演示 16 类攻击面验收清单里的任意 3 条（一个字符 bearer 必须 401 / >4 MiB 必须 413 / 含 `command` 的注册请求必须拒绝并告警）。

---

## 5. 风险与复盘口径

| 风险 | 说明 | 缓解 |
| --- | --- | --- |
| 预征集 ≠ 采购邀请 | 提交合格材料也不必然获正式投标邀请 | 目标定义为**进供应商库 + 建立技术认知**，不计入中标概率 |
| 资质硬伤 | 年限 / 注册资本 / 金融案例 / 广州团队 | 联合体；绝不虚构材料 |
| 技术边界被高估 | 编排 / 检索 / 记忆不是本框架职责 | 材料里主动划界，反而显专业 |
| 公告可能再延期 | 13321 已是「延长至 13 号」 | 按 11-13 硬截止准备，延期当缓冲，不赌延期 |
| 竞品 | 大厂 / 集成商自带 MCP 方案 | 打「唯一 Java/Spring 开源企业级治理栈 + 与指定低代码平台共存」，不硬碰编排平台 |

> 归档：本文为 V1.65 情报侧交付物，情报来源见 [`docs/earnings-report-2026-11-07.md`](earnings-report-2026-11-07.md) 第 4 节。
