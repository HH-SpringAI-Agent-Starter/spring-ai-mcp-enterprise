# 财富机会雷达 · MCP 赛道（2026-11-15，窗口 11-13~11-15）

> **Task 6 全量输出**：哪些企业在招 MCP 人才 / 哪些项目在招标 / 什么价 / Java+Spring+AI 组合的卖点。
> 采集方式：实时 web 检索，所有金额、日期、JD 原话均标注来源；**未点名 MCP 的一律不计入可投清单**。

---

## 1. 执行摘要 · 今日最强 5 个信号

| # | 信号 | 为什么值钱 |
| --- | --- | --- |
| 1 | ⭐⭐ **MCP / Agent Skills 已归 Linux Foundation · AAIF 厂商中立治理**；规格变更不再有单厂商补丁通知，采购模板须要求披露规格版本依赖、既有安全审批基准须重评 | 把「**规格版本 = 受监控依赖 + 变更后全量复验**」写进 RFP，是本框架最干净的新增验收项（V1.73 核心） |
| 2 | ⭐⭐ **IBM ContextForge MCP Gateway 11-13 一天三个 CVE**（`110178` 跨租户导入覆写凭据 / `110194` DCR metadata SSRF / 🆕 `110199` 管理后台 mXSS），统一修 v1.1.0，其中 `110178` 官方 **Workarounds: None** | 「管理面 UI / 配置导入 / 连接建立」三层同时出洞 → **配置面不是安全边界**的最强实证 |
| 3 | ⭐ **n8n `CVE-2026-126386`**（4.7）：未认证 `/mcp-oauth/register` 接受任意 `redirect_uri`，用可信 n8n 域名做钓鱼；与 IBM `110194` **同源**（外部 URI 未校验即信任） | 连接建立期准入可直接现场演示 |
| 4 | ⭐ **DE-CIX MCP server 门测试 A/B**：完整句子「procure and deploy without asking first」拿 **0.803 auto-act** 放行，「operator confirmation」也只到 0.8488；**scope 形式**同一动作 0.50 advisory hold → 原文结论「**enforce the allowlist mechanically on the tool call itself; use a confidence score over prose as a second opinion, never as the lock**」 | V1.72「确定性执行门」论断拿到**独立第三方实证**，本期工程增量直接对位 |
| 5 | ⭐ **Swift Media 11-13 采购周报**：「MCP gateways become a procurement requirement」+ 模型停用通知 30–90 天进 MSA + Agent FinOps 按业务单元分摊 + 保险商问自主动作 + 联邦 agent 身份 | 买方把治理写进**合同与 RFP**，不再只是技术偏好 |

**一句话**：协议治理权上移了，**授权责任没有上移**。规格管「怎么连」，云网关管「从哪连」，旁路监视管「看见了什么」，**没有一层替你决定「这次能不能动」** —— 这一层只能由集成方在自己的执行路径里落地并现场验收。

---

## 2. 招标 / 采购（国内）

### 2.1 广发证券四份征集 · 官方原文核实（本期兑现 V1.72 待办①②）

| 编号 | 项目 | 截止（官方原文） | MCP 相关要求 | 状态 |
| --- | --- | --- | --- | --- |
| **13321** | 智能金融资讯数据服务平台（**延长至 13 号**） | **2026-11-13 17:30** | 逐字「引入 **MCP 工具协议**、Skill 技能包、子 Agent 编排、长期/会话记忆和混合检索」，券商级、能力可外部产品化 | ✅ 已截止 |
| **13322** | 2026 年企业微信持续建设项目 | **2026-11-15 17:30**（**今日截止**） | 逐字「技能可复用、可编排，**MCP 工具调用稳定且权限可控**；服务行为自动留痕、**日志不可篡改**；数据权限按客户经理归属严格隔离」；须用采购方 **Dify / hiagent** 平台；**广州本地服务团队** | ⏰ 今日 17:30 已过 |
| **13327** | AI 智能运维（历史记录：目标第 7 条「推进可观测、自动化、日志等关联平台的 **MCP 封装接入**」） | **官方正文确无报名截止** | 7 项资质硬门槛 | ⚠️ **必须向采购平台核实，不得凭编号推断** |
| 另见 | **2026 年态势感知及安全分析平台扩容项目**（广发主站公告） | **2026-11-12 17:30** | 运营管理含「**MCP 安全工具管理**、安全技能库管理」+ AI 安全数据底座 | ⚠️ 与 13327 可能为不同公告，本期不做合并认定 |

> **口径修正（重要）**：本期回读到一份「态势感知及安全分析平台扩容」公告（截止 11-12、含 MCP 安全工具管理），与历史记录的 13327《AI 智能运维》**是否同一项目未确认**；13327 报名截止仍**须向采购平台核实**。
> **诚实口径**：四份**均为供应商预征集 ≠ 采购邀请 / 资格预审 / 招标公告**；公告明确「按时提交合格报名资料后，并不必然获得正式投标邀请」。本项目 7 项资质（独立法人 / 成立 3 年+ / 注册 500 万+ / 原厂或授权代理 / 金融行业成功案例 / 近两月完税 / 广州本地团队）**全不满足** → 只能作**联合体技术分包**，**禁虚构材料**。11-04 元规简报提到 13322「目标 11 月底前首批功能上线」，属期望目标非已投产。

### 2.2 可投窗口倒计时（延续跟踪）

| 项目 | 预算 / 截止 | 备注 |
| --- | --- | --- |
| 军队大模型安全防护 | 190 万 · **11-16** | 需治理 + 网关 + 等保素材 |
| 中山大学 | 399 万 · **11-17** | 备标 |
| **山东重工集团** 人工智能算力及平台建设（二次） | **11-20** | 逐字「统一入口、统一服务、统一管理、**统一安全管控**」+ 智能体应用平台 + 大模型安全护栏，接受联合体 → 最贴合 `mcp-registry`+`mcp-gateway`+`mcp-governance` |
| 呼和浩特 | 900 万 · 11-23 | — |
| 工信部电子五所 | 173.53 万 · 12-03 | **不接受联合体** |

### 2.3 ⚠️ 本期不计入可投清单

- **河北省数据和政务服务局**「垂直大模型、智能体赋能产业发展研究」中标公告（11-13，中国政府采购网）：中标方中关村科学城城市大脑股份有限公司，**48.9 万元**，服务期至 2026-12-10 → 正文**未点名 MCP**，仅作智能体平台预算锚。
- 历史已标注的建行浙江 900 万数币营销、11-11 大模型大单中标候选（南天 218 万 / 讯飞 268 万 / 百度 300 万）同样**未点名 MCP**。

---

## 3. 招聘 · 哪些企业在招 MCP 人才

### 3.1 本期新抓（11-13 更新）

| 企业 / 岗位 | 薪资 | JD 原话 | 点名 MCP |
| --- | --- | --- | --- |
| **小米 · 高级 AI 系统开发工程师（大模型与 RAG 方向）** | **25–30k** | 「设计并实现 **Agent 工作流编排框架，支持工具调用（MCP 协议）**、任务规划与自动化执行」；「基于 **Java/Go/Python** 构建高并发、可扩展的 AI 微服务」；「扎实的 **Java** 编程基础，熟悉 Spring / SpringMVC / **SpringBoot** / Spring Cloud」 | ✅ **本期最精准**（Java+Spring+MCP+Agent 编排全中） |
| **深圳京发科技控股 · JAVA 开发** | **7–20K/月** | 「接入并编排多模态 AI 服务…完成 Prompt 调优及 **MCP 等扩展协议的落地**」；「负责 AI 后端服务架构设计与核心代码编写」 | ✅ |
| 小米 · Java 技术专家 / 技术经理（北京海淀） | **30–50k** | 云原生 + Dubbo/Nacos/网关 + 「AI 应用实践经验」 | ❌ 未点名 MCP（邻近赛道） |
| 猎聘 · Java 开发工程师（AI 全栈 / AI Coding，杭州西湖，保险） | **15–30k·15薪**（11-05） | SpringBoot/SpringCloud + RAG/Agent/大模型 API + Cursor/Claude Code | ❌ 未点名 MCP |
| 南宁 · AI 智能体开发工程师（兼职） | 15–30k（90 天前） | Java 后端 + RAG + Agent | ❌ |

### 3.2 存量精准岗（用于作品集投递，非本期新增）

| 企业 | 薪资 | 关键要求 |
| --- | --- | --- |
| Jobgether（US Remote，11-12） | **$139.7K–176.6K/yr + 8% bonus** | 逐字「MCP servers and tool interfaces, providing AI agents with **secure and governed access**」+ OT/ICS |
| 上海凯世通半导体（万业企业 600641 子公司） | **10–18k·14薪** | 「封装离子注入机 **SECS/GEM、MES/FDC 为标准 MCP 工具，使 Agent 能安全调用**」 |
| 海康威视（西安） | — | 逐字 **Spring AI Alibaba + HITL + MCP Server/Skills + 权限控制/安全护栏** |
| 法本信息（杭州） | — | 「必须深刻理解并熟悉 MCP 协议，**基于 Java 扩展和开发 MCP Server**」 |
| PNC 银行（11-10，美国五地） | — | 逐字「Implement and support **MCP based solutions**」 |
| 美团 / 上交所技术公司 / 智慧树 / 中软环球 | — | 均点名 MCP + Agent / Skill |
| 阿里云智能（北京望京） | **60–90K·16薪** | RAG / Agent 框架 / **MCP 协议** / Agent 评测 |
| 建发（厦门）/ 上海整车厂 | **50–80k / 40–70k·15薪** | 存量 Java 资产 MCP 化 |

> **结论**：本期两条最精准的 JD（小米、深圳京发）都在**互联网大厂 AI 平台 / 中小型 AI 后端**，且共同点是「**Java 服务端 + Agent 编排 + MCP 协议落地**」——不是算法岗，正是本仓库 `mcp-*` 21 模块 + `examples/` 参考实现可直接当作品集的位置。⚠️ 国内买方仍把 MCP 当**实现细节而非岗位名**（「MCP engineer 是技能不是岗位」），所以卖点要落在**治理工件的可验收性**，不是「我会用 SDK」。

---

## 4. 价格 · 什么价

| 口径 | 数字 | 来源 / 备注 |
| --- | --- | --- |
| ⭐ **SnipeWork Upwork 实时市场指数（11-09~11-15 周）** | 扫描 **16,910 jobs** · 平均固定预算 **$919** · 平均时薪 **$24/hr** | 全平台 live job posts，**不是 MCP 专项**，作总盘锚 |
| **Upwork 官方 2026 时薪指南** | 入门 **$10–25** / 中级 **$25–75** / 高级开发·AI·战略咨询 **$75–150+** | 分类细项：AI Developers $30–50 / AI Engineers $35–60 / ML Engineers $50–200 / Deep Learning $30–120 |
| **botgathering 11-02 实测（`mcp` 类目公开页）** | part-timer fit **4/10**、30+h/week **3/10**；对照 n8n 固定价样本 **$10 / $130 / $275 / $800 / $2,000 / $4,000** | ⚠️ 作者自报、单次扫描、`UNTESTED draft`、无账号无投标，**不是基准** |
| **费率（11-12 三源复核）** | Upwork 卖方 **0–15%**（典型 10%）/ Fiverr **20%** / Freelancer **10% 或 $5** | 旧 10/20/5% 口径弃用 |
| **Upwork 官方 MCP Server**（接单基础设施） | OAuth 2.1、**draft-confirm**、所有资金动作在 upwork.com 完成 | 46 工具，可直接在 Claude/Cursor/ChatGPT 里选品与投标 |
| **企业项目公开报价带** | 7 档 **$3,700–$72,000**（19 倍价差）/ 含 audit·SSO·RBAC·versioning 的生产级 **$80K–$220K** / 企业网关 **$250K 起** | 卖方销售页公开价，**非成交价** |
| ⭐ **治理赛道资本化** | **WhiteMatter（原 QuilrAI）11-10 $20M A 轮**，累计 $24M；2.25M agents / 116B 交互 / 决策约 2.24ms / **定价按 AUM（agents under management）** | 证明「一 agent 一 Guardian」这一类控制有真钱；⚠️ 11-10 非近 3 天，作背景 |

> **报价方法论（V1.72 延续）**：OpenAI×Upwork 11-10 联合研究 —— AI 项目固定价预算调整后 **+52.4%**、挂牌时薪仅 **+1.5%**、前 90 天工时 **+40.6%**、罕见任务组合 8.4% vs 2.4% → 「**变大了不是变贵了**」。按**交付物 / 集成面 / 修订轮次 / 工时**拆报价，别只抬时薪（相关性分析非因果）。

---

## 5. 攻防 / 生态情报（11-13~11-15）

### 5.1 漏洞与实证

| 项 | 细节 | 命中模块 |
| --- | --- | --- |
| **IBM ContextForge `CVE-2026-110178`**（4.1，11-13 Bulletin） | `/v1/import` + `conflict_strategy=update` 不校验共享实体归属 → 持 team-scoped `admin.import` 者可覆写**其他租户** endpoint/headers/凭据并重定向流量；**Workarounds: None**；影响 v1.0.0~1.0.11，修 v1.1.0 | `mcp-tenant` / `mcp-registry` |
| **IBM `CVE-2026-110194`**（5.4，11-13） | OAuth DCR 从授权服务器 metadata 拿 registration endpoint **未校验即请求** → SSRF 打内网；官方缓解 `DCR_ALLOWED_ISSUERS` / `DCR_ENABLED=false` | `mcp-gateway` / `mcp-auth` |
| 🆕 **IBM `CVE-2026-110199`**（4.8，11-13） | 管理后台 URL 参数 status/error 通知经**客户端 HTML sanitizer 的 mutation XSS（mXSS）**绕过 → 管理员会话任意 JS → 凭据窃取 / 越权；修 v1.1.0 | 配置面准入（本期新增） |
| 🆕 **n8n `CVE-2026-126386`**（4.7） | 未认证 `/mcp-oauth/register` 接受任意 `redirect_uri`，可信域钓鱼；修 1.123.89 / 2.45.5 / 2.46.1 / 3.0.1；缓解＝反代限制 `/mcp-oauth/*` + 审查已动态注册 client | 连接建立期准入 |
| **pgEdge `CVE-2026-126197`**（8.8，11-13 发布 / 11-14 修改） | `$mcp_custom_tool$` 提前闭合 DO 块 + `$mcp_args$` 注入 plpgsql；**因使用 simple query protocol，注入可含额外语句与事务控制，绕过 `read_only`**；需至少一个 pl-do 自定义工具，sql/pl-func 走 bind 不受影响；修 1.1.0 | `McpReadOnlyEnforcementGate` |
| ⭐ **DE-CIX MCP server 门测试** | 8 个能力中 **5 个是采购动词**（book/scale/procure/deploy/manage）；scope 形式读 0.9545 auto-act / 写 0.50 advisory hold，句子形式「不询问直接采购部署」**0.803 放行**且「operator confirmation」0.8488 仍放行 → 评分不能当锁 | `McpTaskBoundReferenceMonitor` / 本期 `McpGateChain` |
| **AAIF 规格治理** | MCP 2025-12-09 捐入、Agent Skills 2026-10-22 加入（同期 Open Responses / OpenEnv / OpenSandbox）；10 托管项目、成员 247/305（不同时点）；Governance WG 授权领域组接 SEP、2026-07-28 起正式 Extensions Track、SEP-3398 需 sponsor；2026-09-14 首个厂商中立认证 **MCPA**（$250/90 分钟，**24% 考安全与治理**）；conformance suite **Java = Tier 2** | 规格版本依赖登记 + 全量复验 |
| **AWS AgentCore Gateway GA** | Responses API 服务端发现与执行工具、完整 MCP primitives、`outputSchema` + `readOnlyHint`/`destructiveHint` annotations、dynamic listing 按用户 scoping、Streamable HTTP、SaaS 一键集成、AgentCore payments(x402) 预览 | 云厂商把治理做成标准层（V1.66 延续） |
| **Nacos 3.3.0 / issue #16013（11-11~13）** | MCP 生命周期 draft/review/publish/online/offline、ops 可控的 MCP 工具导入出站策略、默认开启客户端鉴权、Spring Boot 4/Jackson 3；11-11 修复 MCP registry `/v0` 响应 `null protocol/path` 与 `official.status` 缺失（**live IT 暴露 `getFrontProtocol().equals()` NPE 导致 server 被静默丢弃**），进 3.3.1 | Alibaba 可选兼容层口径延续（**不进主 reactor**） |

### 5.2 商业 / 采购侧

- **Swift Media 11-13 采购周报五条**：① MCP 网关进 RFP（集中注册表 / per-agent scope / 检索内容注入过滤 / **可导出审计**）+ 建议「下个 agent 供应商 RFP 加 5 个 MCP 网关问题」；② 模型停用通知期 **30–90 天**进 MSA + 回归测试权 + 退出坡道；③ Agent FinOps 按业务单元分摊 + 周环比翻倍告警；④ 网络险 / E&O 承保问卷问「哪些动作无人工审批、kill switch 怎么工作、红队发现是否修复」；⑤ 联邦 agent 身份（一 agent 一 client ID、短时令牌、human sponsor、HR 离职流程加「禁用该员工拥有的 agent」）。
- **Sherweb MCP Server**（7,500+ MSP，15 家早期伙伴）：订阅 / 定价 / 用量 / 账单数据接 Copilot Studio、Cursor、Claude、Rewst、n8n、Pia → 垂直行业 MCP 商业化 + **服务商可把集成经验转售给客户**。
- **天眼查 MCP 11-15 上架魔搭**（Hosted 2026.11.15）：企业工商 / 股权穿透 / 风险诉讼 / 招投标数据，鉴权后云端调用 → 国内数据型 MCP 生态继续放量。
- **Cisco IQ MCP Server + RBAC**（10-26，非近 3 天）：16,000 客户 / 近 2,000 万设备，伙伴经 MCP 查客户资产 → 存量重型系统 MCP 化的海外对照。
- **嘉信理财 Schwab**（11-12 上线，$13.3T）：从只读到 agentic 下单但**每笔强制人工确认**，脚注自认「可能不准确」；11-13 报道明确当前**仅零售客户、RIA 排除**、仅接 Claude → 金融级 HITL 活广告（V1.71 延续）。

---

## 6. Java + Spring + AI 组合在该赛道的 8 条卖点

| # | 卖点 | 对应仓库资产 / 本期证据 |
| --- | --- | --- |
| 1 | **存量 Java 权限模型直接复用**，不必为 agent 重造一套身份体系 | `mcp-auth` / `mcp-tenant` / `mcp-governance`；Swift Media「联邦 agent 身份」、OnePlan「respects existing permissions」 |
| 2 | **执行门可现场验收**：12 项配置面清单 + 123 断言回归 + 0 误杀对照 | `examples/gate-orchestration/`（V1.73 新增）、`docs/enterprise-rfp-checklist.md` |
| 3 | **规格版本当依赖管**：AAIF 中立治理下，唯一能承诺「变更后全量复验」的是集成方 | `GateContext.specRevision` / `screenSpecRevision`；AAIF SEP / Extensions Track |
| 4 | **连接建立期准入**（DCR metadata / redirect_uri / 跨租户导入 / 管理面 URL）是 Java 过滤器天然位置 | `McpGateChain.ControlPlaneInputGuard` / `examples/connect-path-guard/` / `examples/registry-admin-guard/` |
| 5 | **私有化 / 等保 / 密评工件**：不能上云的钱最好赚，云网关给不了 | `mcp-monitor` / `examples/audit-ropa-export/` / `enterprise-rfp-checklist.md` |
| 6 | **确定性执行门，不赌模型**：评分、提示词护栏、审批弹窗文本一律只读审计 | DE-CIX A/B + arXiv 量化（in-prompt 100% 失守 / 模型外 monitor severe 0%）+ `McpTaskBoundReferenceMonitor` |
| 7 | **Spring AI Alibaba 可选兼容、不耦合**：主 reactor 基于官方 SDK，Alibaba 生态只作可选层 | `examples/spring-ai-alibaba-mcp-server/` / `docs/alibaba-integration-guide.md` / `docs/spring-ai-2.0-migration-notes-2026-11-10.md`；Nacos 3.3.0 口径修正 |
| 8 | **开源作品集可直接投递**：21 模块 + 353 篇文档 + 四语言 Client SDK + CI/Docker，对应 JD「Java 服务端 + Agent 编排 + MCP 协议落地」 | 小米 25–30k、深圳京发 7–20k、法本、海康威视、Jobgether $139.7–176.6K |

---

## 7. 24 / 48 / 72 小时行动清单

| 窗口 | 动作 |
| --- | --- |
| **24h** | 发 SEO 稿抢「AAIF 规格治理 / IBM 三 CVE / n8n 开放重定向 / DE-CIX 门测试」流量；把 12 项清单压成 1 页 PDF 进售前包；把 5 问写进 RFP 问卷模板 |
| **48h** | **回广发采购平台核实 13327 报名截止与材料清单**；跟踪 13322（今日 17:30 已截止）/ 13321 是否转正式招标；录 123 断言 3 分钟演示 |
| **72h** | 按 `gate-integration-map` 分期①评估把两个 SPI 接进 `mcp-registry` / `mcp-governance`（独立分支）；军队 190 万 11-16 / 中山 399 万 11-17 / 山东重工 11-20 备标；跟踪兴业 / 北京农商 / 上海农商正式招标文件 |

---

## 8. 来源清单（本期检索）

| # | 来源 |
| --- | --- |
| 1 | IBM Security Bulletin 7296701（`110178`）https://www.ibm.com/support/pages/node/7296701 |
| 2 | IBM Security Bulletin 7296696（`110194`）https://www.ibm.com/support/pages/node/7296696 |
| 3 | Junglewise `CVE-2026-110199` https://junglewise.ai/threats/cve-2026-110199-ibm-contextforge-mcp-gateway-1-0-0-through-1-0-11-is-vulnerable |
| 4 | AI Threat Alert `CVE-2026-126386`（n8n）https://aithreatalert.com/cve/CVE-2026-126386 |
| 5 | ThreatMonitor `CVE-2026-126197`（pgEdge）https://threatmonitor.io/cve/CVE-2026-126197 |
| 6 | Swift Media 11-13 采购周报 https://swiftmedia.ca/blog/ai-headlines-for-business-november-13-2026 |
| 7 | AI Governance Institute · MCP/Agent Skills 移交 AAIF https://aigovernance.com/news/mcp-and-agent-skills-move-to-vendor-neutral-governance-under-linux-foundation |
| 8 | Google Open Source Blog · Kurtis Van Gent 访谈 https://www.googblogs.com/scaling-ai-agents-with-open-standards-kurtis-van-gent-on-mcp-and-agent-skills-joining-the-aaif |
| 9 | Agent 协议地图（AAIF / 版本核对 11-08）https://ai.ozdemir.be/guides/agent-protocols |
| 10 | AI Daddy · AAIF 治理 / MCPA / SEP 细节 https://www.aidaddy.tech/learn/07-agentic-systems/03-tool-use-and-mcp |
| 11 | ScriptMasterLabs / dev.to · DE-CIX MCP 门测试 https://bizarro.dev.to/scriptmasterlabs01/can-ai-agents-safely-order-network-connections-8-capabilities-counted-5-of-them-procurement-35gj |
| 12 | AgentBeam · WhiteMatter $20M A 轮 https://agentbeam.com/blog/whitematter-guardian-agents-series-a-coding-agents-november-2026 |
| 13 | The Agent Times · AgentCore Gateway GA https://theagenttimes.com/agents/article/amazon-bedrock-agentcore-gateway-expands-into-full-enterpris-8e190323 |
| 14 | 广发采购平台 13321 http://gfjc.gf.com.cn/gyszjgg/13321.jhtml ／ 13322 http://gfjc.gf.com.cn/gyszjgg/13322.jhtml |
| 15 | 广发主站 · 态势感知及安全分析平台扩容征集 https://www.gf.com.cn/web/article/detail?articleId=01a1aaecb5257570934897d5a51a3212&columnId=4 |
| 16 | 中国政府采购网 · 河北 11-13 中标公告 https://www.ccgp.gov.cn/cggg/dfgg/zbgg/202611/t20261113_27677483.htm |
| 17 | Upwork 官方时薪指南 https://www.upwork.com/resources/upwork-hourly-rates ／ Upwork MCP Server https://upwork.com/ai/mcp |
| 18 | SnipeWork 市场指数（11-09~11-15）https://snipework.com/news/upwork-q3-2026-earnings-beat ／ botgathering 11-02 实测 https://botgathering.com/recipe/check-paid-freelance-demand-for-an-ai-agent-skill-without-an |
| 19 | 小米 / 深圳京发 JD（11-13）http://www.qzljrc.com/job3407169890.shtml ／ https://www.jdjob88.com/jobs/53713384.html |
| 20 | Channel Insider · Sherweb MCP Server https://www.channelinsider.com/ai/sherweb-mcp-server-msp-ai-automation ／ 魔搭 天眼查 MCP https://www.modelscope.cn/mcp/servers/TianYanCha/tyc-mcp |
| 21 | Nacos 3.3.0 https://www.besthub.dev/articles/nacos-3-3-ai-agent-registry-mcp-lifecycle-dns-discovery-spring-boot-4-support-08692e1dda4c ／ issue #16013 https://github.com/alibaba/nacos/issues/16013 |

---

## 9. ⚠️ 诚实边界

- **AAIF 治理移交不是近 3 天新闻**（MCP 2025-12-09 / Agent Skills 2026-10-22）；本期增量是采购与变更管理含义的解读。成员数 247/305 为不同时点口径；SEP / Extensions Track / MCPA 细节来自二手整理，正式引用须回官方复核。
- **DE-CIX 门测试**为第三方启发式评分实验（`calibrated=false`、作者自报、单次测量、10-19 实测 / 10-15 发布），**非近 3 天事件、非本框架实测拦截率**；只取方向性结论。WhiteMatter（11-10）、Cisco（10-26）、Nacos 3.3.0（10 月底）同理，作背景。
- **本框架不受** IBM / n8n / pgEdge / AWS 等第三方 CVE 直接影响；`examples/` 参考实现**未接入主模块**，不得当生产组件。
- **广发四份为预征集 ≠ 采购邀请**；本项目无企业主体 / 商业合同 / 中标 / 金融案例 / 等保测评，7 项资质全不满足 → 只能联合体技术分包，**禁虚构材料**。
- **全部报价 / JD 标价 / 预算为公开挂牌或预算上限，非成交价**；SnipeWork 16,910 jobs / $919 / $24hr 是**全平台周度总盘**，不是 MCP 专项；botgathering 为未验证单次扫描。
- 河北政采 48.9 万等国内公告**未点名 MCP，不计入可投清单**；Spring AI 2.0 **未执行升级**；`13327` 报名截止**未核实**。
