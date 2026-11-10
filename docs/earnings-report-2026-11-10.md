# MCP 挣钱情报日报 · 2026-11-10（周二）

> 覆盖窗口 **11-08 ~ 11-10**（最近 3 天，全部为本期实时检索）。
> 一句话结论：**V1.67 证明了「治理层 = 定价权」（同一份工作量卖 10 倍价）；今天出现更硬的一层 —— 治理层 = 准入权 / 法律责任分界线。** UiPath×Google Cloud 把治理写成受监管行业允许 MCP 接入的**前提**；T-Investments 把「每笔操作强制人工确认」写进架构以**转移法律责任**；浙商证券把 7 项法规写成**强制测评依据**；Dynatrace 双 CVE 用同一个 server 的一正一反证明审批门真的挡住攻击；10 月中标数据则显示治理能力正被**单独拆单、单独计价**。一句话：**昨天是「有治理能卖更贵」，今天是「没治理连投标资格都没有」。**

---

## 一、今日最强信号（按挣钱价值排序）

### 🔥🔥🔥 1. 10 月金融智能体招标全景量化（11-10 零壹智库）—— 本期最硬的钱

[腾讯网 11-10](https://new.qq.com/rain/a/20261110A0DO1L00) / [零壹智库原文](http://mp.weixin.qq.com/s?__biz=MzA3MjEwMTQxNA==&mid=2650877860&idx=1&sn=4eff62a7ee78a55c5d52f668c448566f)：

| 指标 | 数值 | 判读 |
| --- | --- | --- |
| 10 月公开招标 | **23 条**，环比减少 1/3 以上 | 平台整包在收缩，但**标的类型在细分** |
| 中标/成交公告 | **15 条**，12 条披露金额合计 **1652.8 万元**，创下半年新低 | 金额下降 ≠ 需求消失，是**从大整包拆成小件** |
| 具名中标方 | 12 家，**仅今日投资、高伟达各获两单** | **没有常胜厂商**，格局未定 → 新供应商有窗口 |
| 平台类招标 | 8 条 / 34.8%，**新建平台不足 20%** | 买方在给已有底座**补模块**，不是从头买平台 |

**「拆单」成为主流打法**（国投证券一个需求拆四单，每单可单独比价、单独换供应商）：

| 标的 | 金额 | 中标方 |
| --- | --- | --- |
| 桌面 AI 智能工作台授权 | 165 万 | 火山引擎 |
| 智能体平台升级 + AI 知识库 | 205 万 | 腾讯云 |
| 「大师诊股」智能体本体 | 18.02 万 | 今日投资数据科技 |
| 诊股数据服务（持续供数） | 20.14 万 | 今日投资数据科技 |
| **数据分析建模平台永久授权**（含国产化适配/动态脱敏/**行内智能体对接**） | **350 万（本期最大单）** | 中电金信（浙江农商联合银行） |

> **最大一单不是智能体本体，是智能体要接的数据底座。** 其余：高伟达 273.5 万（华泰证券）、杭州源诚 239 万（中信金融资产不良资产智能体）、中保投资 98.44 万、交控资本 78.9 万。

**技术条款换了问法**（逐字可直接对位本框架模块）：

- 兴业银行：平台须**接入行内业务与数据平台**；
- **常熟农商银行：桌面智能体以 A2A、MCP、CLI 等方式对接内部系统** → MCP 第一次与 A2A/CLI 并列写进银行正式采购条款；
- 中原银行：**安全沙箱受控执行**；江苏农商联合银行：**大模型安全围栏 + 辅导完成算法备案**；常熟农商：权限管理、操作留痕与审计追溯；
- 民生银行采购 AI 邮件安全智能体软件、上海银行单列大模型安全平台模块、中信建投订阅智能体安全检测工具。

**浙商证券把「测评依据」写成强制清单**（已部署智能体合规安全测评）：`GB/T 45654-2025《网络安全技术 生成式人工智能服务安全基本要求》`、`《人工智能安全治理框架 2.0》`、`《网络安全法》`、`《数据安全法》`、`《个人信息保护法》`、`《生成式人工智能服务管理暂行办法》`、`《证券期货业网络和信息安全管理办法》`。

> **★ 判读（本期最有挣钱价值）**：买方从「买一个能演示的平台」转向「**按件买治理能力**」——数据、评测、平台订阅、模块增补各有独立编号与价格。整包门槛（成立 3 年 / 注册资金 500 万 / 2 个金融案例 / 本地团队）对个人开发者很高，但**拆单后「安全评测 / 治理模块 / 合规工件 / 持续供数」是独立可比价、可单独换供应商的细分单**，正是开源框架 + 联合体技术分包的切入口。逐条逐字对应 `mcp-governance`（权限/沙箱/围栏）+ `mcp-audit`（留痕/追溯）+ `mcp-registry`（A2A·MCP·CLI 接入与插件纳管）+ `mcp-monitor`（评测/观测）。详见 [`financial-agent-tender-compliance-2026-11-10.md`](financial-agent-tender-compliance-2026-11-10.md)。

### 🔥🔥🔥 2. UiPath × Google Cloud Gemini for healthcare（11-10 官方新闻稿）—— 治理 = 准入权

[Google Cloud Press Corner 11-10](https://www.googlecloudpresscorner.com/2026-11-10-UiPath-Accelerates-Enterprise-AI-Transformation-with-Google-Clouds-Gemini-for-Healthcare)：UiPath 成为 **Google Cloud Gemini for healthcare 首发合作伙伴**，通过 **UiPath MCP Server** 把 Gemini 接到 UiPath Platform 上编排的 automation 与 agent。原文逐字：

- 「**with the governance that regulated industries demand**」——治理是受监管行业**要求**的东西，不是加分项；
- 「those automations follow **the same governance, permissions, and audit controls already in use** by that organization」——复用企业已有权限与审计体系；
- 「With **runtime guardrails, auditability, and human oversight** built in」「where every step auditable」；
- 适用「**regulated, high-stakes environments like healthcare**」，目前为 joint customers **preview**。

> **判读**：V1.67 的「治理层 = 定价权」之上还有一层 **准入权** —— 不是「有治理能卖更贵」，而是「**没有治理就不允许接入**」。Google Cloud 从 V1.67 记录的「第四朵云把治理做成标准层」再进一步：通过合作伙伴的 MCP Server 把治理落地到医疗受监管场景。「**复用存量权限与审计**」正是本框架「存量 Java 权限模型直接搬进 MCP」的核心卖点，被超大厂产品发布词直接背书。

### 🔥🔥🔥 3. Dynatrace MCP 双 CVE（11-10 收录）—— 审批门第一次拿到「同体内一正一反」实证

| CVE | 分数 | 缺陷 | 修复 / 缓解 |
| --- | --- | --- | --- |
| [`CVE-2026-123811`](https://aithreatalert.com/cve/CVE-2026-123811) | **7.5 HIGH** | `--http` 起的 server 接受 JSON-RPC `tools/call` **无鉴权、无会话校验、无 Host/Origin 防护** → 浏览器经 DNS rebinding 打本地 MCP 端口，`execute_dql` 读日志/安全事件/用户会话（可能含 token），`create_dynatrace_notebook` 留带恶意链接的 notebook | 升级 2.0.0；**停用 `--http` 改回 stdio 或只绑 127.0.0.1 并前置带 Host/Origin allowlist 的认证反代**；最小 scope + 轮换凭据；审计 `execute_dql`；盘点开发者工作站与 CI 里带 `--http` 的启动 |
| [`CVE-2026-123812`](https://aithreatalert.com/cve/CVE-2026-123812) | 3.7 LOW | `create_dynatrace_notebook` **漏调 `requestHumanApproval`**（CWE-862），且**缺 `destructiveHint` 注解** → 间接 prompt injection 静默植入含攻击者 DQL 的 notebook，租户用户打开时执行 | 升级 1.8.7；禁用该工具 / 只读 token / **所有写工具强制 HITL** / 审计 notebook 创建 |

> **★ 判读**：**同一个 server、同一条攻击链，`send_event` 因为 `requestHumanApproval` 门挡住了攻击路径，`create_notebook` 漏门就成了入口。** HITL 第一次拿到可写进标书的「同体内对照实证」，比合规条文好用得多。「缺 `destructiveHint`」还给出新验收维度：**工具元数据声明本身也要在注册期校验** → 对位 `mcp-registry` 注册期准入 + `mcp-governance` 写工具审批门。与本期新增 [`examples/stdio-command-allowlist/`](../examples/stdio-command-allowlist/README.md) 的 `--http` / `0.0.0.0` 拒绝规则逐条呼应。

### 🔥🔥 4. 商业 MCP 密度信号 + HITL 成为「法律责任分界线」

- **合合信息三条产品线同日集中上线 MCP（11-09 [中国经济新闻网](https://www.cet.com.cn/wzsy/cyzx/10655992.shtml)）**：TextIn 上线智能文档处理领域**首批 MCP Server**（跨页表格/合并单元格/手写/公式，解析稳定率 99.99%、单页耗时较可比产品低 30%+，已支撑教育题库/RAG/文档翻译/RPA）；扫描全能王以专家技能+连接器上架主流办公智能体平台，开放 **30 余项原子能力**；启信慧眼 MCP 入选「**十大专业级技能**」，一键调用 **200+ 数据接口**（工商/司法风险/知识产权/经营数据）+「供应商管理专家」组件，银行对公客户经理一句话完成企业调研。→ 国产数据/文档类 MCP 从单点试水进入**成品化**，「一次开发、多模型通用」。
- **T-Investments 交易基础设施向第三方 AI Agent 开放（[The Value Engineering](https://thevalue.engineering/news/t-investments-mcp-protocol-ai-agents.html)）**：外部算法经 MCP 可分析行情、计算税负、下单（含止损），但**每笔操作强制用户显式确认**。原文明确点出这是把「算法故障/误触发」的**法律责任从券商转给投资者**的架构设计，同时用统一协议省掉为每个开发者建定制 API 的成本；配套「Invest. Premarket」黑客松奖池 **250 万卢布**（11-11 报名截止、12-05 莫斯科决赛）。
- **PitchMe 招聘 MCP Server（11-09 [HR Technology Wire](https://www.hrtechnologywire.com/press-releases/pitchme-lets-recruiters-search-and-shortlist-candidates-directly-from-chatgpt-groq-and-muse)）**：remote MCP 连 ChatGPT/Groq/Muse，逐字「works only within the **account, team, and permissions** the recruiter already holds」、**限时会话或撤销前有效**、SOC 2 + GDPR；Bullhorn/Airtable 回写可见可审。
- **Upwork 官方 MCP 连接器**（[opentools.ai](https://opentools.ai/tools/upwork-mcp)）：免费、OAuth 2.1、写操作 **draft-and-confirm**，binding offer / 合同接受 / 付款**必须回 upwork.com 人工确认**。

> **判读**：这四家都不是安全厂商，却都把「权限继承 / 强制确认 / 审计可见」写进产品定义。**HITL 不再只是安全最佳实践，而是责任归属与商业合规的合同边界**。T-Investments 尤其值钱：它把 HITL 明码标价成「券商的法律风险敞口」——这是售前最容易打动风控/法务的一句话。

### 🔥🔥 5. Spring AI 2.0 GA 迁移实测（本期工程增量，回应任务清单第 1 项）

[dev.to AWS Heroes Part 4](https://bizarro.dev.to/aws-heroes/building-ai-agents-with-spring-ai-and-amazon-bedrock-agentcore-part-4-provide-mcp-tools-for-2odf) / [Part 6](https://dev.to/aws-heroes/building-ai-agents-with-spring-ai-and-amazon-bedrock-agentcore-part-6-deploy-mcp-client-on-d4d) 已把示例升级到 **Spring AI 2.0 + Spring Boot 4.1 GA**，列出 MCP 客户端的 breaking changes：

| 项 | Spring AI 1.x | **Spring AI 2.0 GA** |
| --- | --- | --- |
| HTTP 传输类 | `WebClientStreamableHttpTransport`（WebFlux WebClient） | **`HttpClientStreamableHttpTransport`**（`java.net.http.HttpRequest.Builder`） |
| MCP URL 配置 | 单个 URL | **必须拆 `base.url` + `endpoint`（如 `/mcp`）两段** |
| ChatClient 默认选项 | `defaultOptions(ChatOptions)` | `defaultOptions(ChatOptions.Builder)` |
| 工具挂载 | `.tools(local)` + `.toolCallbacks(provider)` | **`.tools(localTools, callbacks)` 合并** |
| 额外 Bean | 不需要 | **须显式定义 `ToolCallingManager` bean** |

另：[Spring AI vs LangChain4j 2026 横评](https://dev.to/jamilxt/spring-ai-vs-langchain4j-which-java-ai-framework-should-you-choose-in-2026-4mop) 结论 —— Spring AI 2.0 把 MCP 当**一等集成点**（`@McpServer` / `@McpTool` / `@McpParam` 注解、STDIO/SSE/Streamable-HTTP、Micrometer+Actuator 内建可观测），LangChain4j 需手工接线；[Spring 生态 22 周年综述](http://www.lpqz.cn/news/61814) 提到 Spring Security 7.1 / Boot 4.1 新增 `InetAddressMatcher` / `InetAddressFilter` 做 SSRF 防护，官方已紧急修复「**MCP 会话耗尽**」高危漏洞。

> **判读**：本框架「核心治理层基于官方 SDK、Alibaba 仅可选兼容层、不被上游绑架」的定位再获第三方横评背书，**但兼容声明必须跟着 GA 更新**。本期新增 [`spring-ai-2.0-migration-notes-2026-11-10.md`](spring-ai-2.0-migration-notes-2026-11-10.md)，把 5 个 breaking change 逐条映射到受影响模块与验收方式。

### 🔥 6. 接单 / 招聘 / 报价（钱）

**Upwork 真实在售单**：[Senior AI Agent Engineer – Autonomous Orchestration](https://www.upwork.com/freelance-jobs/apply/Senior-Agent-Engineer-Autonomous-Orchestration_~022119453403578691332/) — **$1,500.00 Fixed-price**、Expert Experience Level、Ongoing project、11-08 发布、20–50 proposals、5 interviewing。要求 AI Agent Development / Agentic Workflows / Multi-Agent Systems / TypeScript / Node.js。→ 真实预算不是 demo 单。

**7 家 MCP 开发公司公开报价横评**（[AY Automate，逐家官网核实于 2026-11-09](https://www.ayautomate.com/blog/best-mcp-development-companies)）：

| 公司 | 公开定价 | 交付周期 | 定位 |
| --- | --- | --- | --- |
| AY Automate | 嵌入式工程师 **$3K/月**起，项目制 | 首个 server **2–5 周**（"Live in 10 days"） | 窄工具 + 认证 + 逐工具权限 + 日志 |
| Saigon Technology | **≤ $46/hr**，ISO 27001 | 原型 2–4 周 / 生产 1–3 月 | 全包 build + hosting + 加固 |
| NINtec | 未公开 | 单系统 **6–8 周** / 多系统 **10–14 周** | **受监管 fintech/healthcare：多租户 auth + audit + SLA** |
| Terralogic | 未公开，可 retainer | 1 周 discovery + 4–10 周 | M365/Google/Salesforce/Jira/QuickBooks 集成 |
| Wiserbrand | 未公开 | 4–8 周 | **Governed MCP layer** + MCP-vs-API 评估 |
| Mobisoft Infotech | 未公开 | 简单 2–4 周 / 复杂 6–12 周 | 咨询式设计 |
| Braincuber | **$15,000 – $250,000** 公开价格带 | 未标注 | TS/Python 定制 server |

原文金句：「**The code is the easy part. The work that decides whether the server survives production is tool design, authentication, per-tool permissions, logging** and what happens when the protocol or your systems change.」→ 与 V1.67「治理层 10 倍定律（$15K → $220K）」形成**第二独立信源交叉验证**，并新增「交付周数」维度：从 2–5 周到 10–14 周，涨的同样是治理与合规工作量。

**费率复核（2026-11，[Yclas 横评](https://yclas.com/blog/online-marketplaces-for-freelance-services.html) / [费率实务](https://bestjobsearchapps.com/articles/en/how-upwork-freelance-jobs-work-and-what-they-actually-pay)）**：Upwork 自由职业者 **0–15%（典型 10%）** + Connect $0.15，客户侧 Basic 3–5% / Business Plus 8–10%；Fiverr 卖家 20% + 买家 5.5%；Freelancer.com 10% 或 $5。⚠️ 口径为**卖方平台费**，与 V1.63 记录的旧 20/10/5% 不同，以本期为准。

**招聘（Java + Spring + MCP）**：

- **阿里云智能·技术专家 AI Agent 方向**（BOSS 直聘，北京望京）**60–90K·16薪**：逐字「开发 LLM 应用基础设施，包括 RAG、Agent 开发框架、**MCP 协议**、Agent 评测机制」；
- **Superbo AI Engineer（AIE202611）**：MCP servers + REST API + API gateways + OAuth/API keys + **Text-to-SQL SQL guardrails** + PII 脱敏/GDPR + eval in CI + tracing；
- 信雅达科技 Java 工程师 18–25K（金融科技，10 年以上）；
- 「**有写过 MCP 服务吗**」进入 Java 大厂面试题（[面试向长文](http://www.lpqz.cn/news/114705)），考的是「链路 + 从零搭最小可运行 Spring Boot MCP Server + 真实跑通一次工具调用」→ 引流长文选题。

### 🔥 7. 竞争定位与安全量化

- **Agent Gateway 六平台横评**（[dev.to](https://dev.to/varshithvhegde/agent-gateways-are-coming-here-are-the-first-6-platforms-building-them-2026-pfj)）：TrueFoundry（LLM 路由 + MCP 深治理 + A2A + SOC 2/HIPAA/ITAR + VPC/on-prem/**air-gapped**）、AgentGateway.dev、SnapLogic、Pragatix、**Operant AI**（Shadow Escape 零点击攻击研究、Gartner 6 份报告、inline PII redaction、理解工具调用语义的实时检测）、**Obot**（开源，v0.14 MCP Registry Support，secrets 放 shim 层不暴露给 server，把 MCP Dev Summit 捐给 Linux Foundation AAIF）。作者判断「**agent gateway 品类 = 2015 年的 API gateway 品类**」。→ **六家仍无 Java/Spring 开源栈**，本框架差异化延续；但也提示「治理」已被云厂商与创业公司做成独立品类，不能只喊口号。
- **腾讯云《MCP 认证与安全：授权是可选的，责任不是》**（[原文](https://cloud.tencent.com/developer/article/2779058)）：`CVE-2026-61560`（9.8，mcp-gitlab SSE 全工具无认证 + `file_path` 未净化读 `/proc/self/environ` 窃取 `GITLAB_PERSONAL_ACCESS_TOKEN`）、`CVE-2026-54504`（8.8，文档 server 绑所有网卡 + 文档管理 API 无认证）。第三方扫描：官方 Registry 抽查 518 个 server 约 **41% 零认证**；约 2 万台调查仅 **8.5% 用 OAuth**、**53% 仍静态 API Key**；**492 台**公网无认证暴露。⚠️ 三份统计口径不同，**不可相加**。随文给出可直接抄进评审表的清单：RFC 9728 元数据 + 最小 scope、**每请求校验签发方与 audience**、**绝不把收到的 token 转发给下游**、**state handle 不当认证且须绑定用户身份**、PKCE S256、RFC 8707 `resource`。
- **官方七 CVE 深度复盘**（[AL-ICE 11-08](https://al-ice.ai/posts/2026/11/mcp-reference-servers-desktop-commander-seven-cve-batch-november-2026)）：`41903` git 带仓库自身 `core.fsmonitor`/`core.hooksPath` 执行代码，官方修法即 `-c core.hooksPath=/dev/null -c core.fsmonitor= -c core.pager=cat` + 净化 Git 环境变量；`41902` `validate_opened_repo` 只校验 worktree / git dir / common dir **三条路径，漏了 `.git/objects/info/alternates` 与符号链接 objects/ 这第 4 条**（Git 每次对象查找都走）；`41901` `VACUUM INTO` 击穿 SELECT 前缀检查，且 PyPI 上游已归档、补丁须落到 vendored 代码。→ 本期三个工程示例逐条对位：`tool-input-grammar`（41901）、`connect-path-guard`（41902 路径）、`stdio-command-allowlist`（41903）。

---

## 二、Java + Spring + AI 组合在本赛道的卖点（本期校准）

| # | 卖点 | 对位模块 | 今日证据 |
| --- | --- | --- | --- |
| 1 | **唯一 Java/Spring 开源企业级 MCP 治理栈**：云厂商/开源网关清一色非 Java | 全 21 模块 | Agent Gateway 六平台横评；Spring AI 2.0 一等 MCP 支持 |
| 2 | **存量 Java 资产 MCP 化**：把已有权限模型/数据源/审计要求一起搬进来 | `mcp-registry`、`mcp-gateway`、`mcp-tenant` | 兴业「接入行内业务与数据平台」、常熟农商「A2A/MCP/CLI 对接内部系统」、阿里云 60–90K JD |
| 3 | **治理 = 准入权**：不是加分项，是投标与接入前提 | `mcp-governance`、`mcp-audit` | UiPath×Gemini「governance that regulated industries demand」、浙商 7 项强制测评清单、广发五件套写进资质 |
| 4 | **HITL = 法律责任分界线**（本期新增最强话术） | `mcp-governance` HITL、`mcp-audit` | T-Investments 用强制确认转移券商责任；Upwork draft-and-confirm；Dynatrace `123812` 漏门即成入口 |
| 5 | **注册期准入 + 工具默认禁用 + 启动层 allowlist**（本期补齐最后一块） | `mcp-registry`、`mcp-core` | Uber 800+ 沿用；`destructiveHint` 校验；`examples/stdio-command-allowlist/` |
| 6 | **私有化 / 信创 / 等保 / air-gapped** 交付能力 | `mcp-tenant`、`mcp-auth` | 银行要求广州本地团队、太平金科信创、TrueFoundry air-gapped 成卖点 |
| 7 | **基于官方 SDK、不被上游绑架**：Alibaba 仅可选兼容层、不进主 reactor | `mcp-integrations/mcp-alibaba` | Spring AI 2.0 GA breaking changes + 第三方横评；延续 11-01 去耦合定位 |

---

## 三、可投清单与倒计时（诚实标注）

| 截止 | 项目 | 状态 |
| --- | --- | --- |
| **11-11 09:00** | 厦门大学大模型智能计算平台 | 倒计时 1 天 |
| **11-12 17:30** | 广发证券《2026 态势感知及安全分析平台扩容》——含「**MCP 安全工具管理**」+ 安全技能库 + AI 安全数据底座 | 剩 2 天 |
| **11-13 17:30** | 广发证券《智能金融资讯数据服务平台》（13321，标题明确「**延长至 13 号**」）——MCP 工具协议 + Skill + 子 Agent + 混合检索，**能力可外部产品化** | 剩 3 天 |
| **11-15 17:30** | 广发证券《2026 企业微信持续建设》（13322）——把 MCP Server 五件套写进资质、日志不可篡改、指定 Dify/hiagent、广州本地团队 | 剩 5 天 |
| 11-16 / 11-17 | 军队大模型安全防护 190 万 / 中山视频算力 399 万 | 备标 |
| 11-20 / 11-23 / 11-25 | 山东重工集团级平台（统一安全管控）/ 呼市 900 万 / 中央民大 3712 万 | 备标 |

> **⚠️ 本期口径修正**：V1.67 把广发窗口写成「11-12 / 11-13 两份」，经[官方原文](http://gfjc.gf.com.cn/gyszjgg/13321.jhtml) [三份](https://www.gf.com.cn/web/article/detail?articleId=01a1aaecb5257570934897d5a51a3212&columnId=4) [核实](http://gfjc.gf.com.cn/gyszjgg/13322.jhtml) 实为**三份**，13322 截止 **11-15 17:30**（不是 11-13）。三份均明确「**本公告为供应商预征集，不等于招标公告及资格预审公告，按时提交合格报名资料后并不必然获得正式采购邀请**」；资质硬门槛＝成立 3 年以上 / 注册资金 ≥500 万 / 原厂或授权代理 / ≥2 个金融行业成功案例 / 广州本地服务团队 / 纳税信用与近两月完税凭证。**本项目无商业合同、无中标记录、无金融行业案例** → 只能作联合体技术分包，**严禁虚构材料**。

---

## 四、明天做什么

1. **广发 11-12 剩 2 天 / 11-13 剩 3 天 / 11-15 剩 5 天**：把云网关对照 + 报价锚 + 本期三个可演示示例压成 1 页 PDF 附报名材料；先做资质自检，资质不足则找广州本地集成商组联合体。
2. 发本期 SEO 稿抢「UiPath Gemini MCP 治理 / Dynatrace CVE-2026-123811 / 10 月金融智能体中标 1652.8 万 / 拆单采购 / Spring AI 2.0 迁移」流量。
3. 把浙商证券 7 项强制依据逐条做成模块映射与验收方式（本期 `financial-agent-tender-compliance-2026-11-10.md` 已起头）。
4. 把 `destructiveHint` 注解校验 + 写工具审批门落进 `mcp-registry` 注册期示例（对位 `CVE-2026-123812`）。
5. 跨期待办：`exportPrometheus()` 挂进 `mcp-monitor` actuator；连接路径补重定向逐跳重校验与 SNI/Host 一致性。
6. 跟踪兴业 / 北京农商 / 上海农商正式招标文件、Google Cloud 是否把逐工具权限与 HITL 纳入产品层、11-11 T-Investments 黑客松报名结果。

---

## 五、诚实边界

- **1652.8 万元**是 10 月 **12 条披露金额的中标公告合计**，非全市场规模；23 条招标为不完全统计，月度序列采用回溯修订底稿（9 月修订为 36 条）。
- 全部公司报价为**卖方销售页公开价 / 价格带**，不是成交价，更不是本项目已获订单；`$15K–$250K` 与 V1.67 的 `$80K–$220K` 来自不同信源，可交叉验证但不可相加。
- 广发三份为**预征集 / POC**，本项目无商业合同、无中标、无金融案例；UiPath×Gemini 为 **preview** 且限联合客户。
- T-Investments 为单一媒体报道（源标注 Telegram），250 万卢布是**黑客松奖池**非项目预算。
- `CVE-2026-123811/123812` 为第三方威胁情报站收录，修复版本号以官方 GHSA 为准；41% / 8.5% / 53% / 492 台来自三份口径不同的扫描，**不可相加**。
- 本期新增工程示例仍是 `examples/` 下的**参考实现**，`mcp-monitor` 主模块未内置指标导出；stdio command allowlist **不做进程隔离**，不能当沙箱用。

---

## 六、本期来源（全部 11-08 ~ 11-10 实时检索）

1. [10月金融智能体招标降三分之一，中标金额创四个月新低 · 腾讯网 11-10](https://new.qq.com/rain/a/20261110A0DO1L00) / [零壹智库原文](http://mp.weixin.qq.com/s?__biz=MzA3MjEwMTQxNA==&mid=2650877860&idx=1&sn=4eff62a7ee78a55c5d52f668c448566f)
2. [UiPath × Google Cloud Gemini for Healthcare · 11-10 官方](https://www.googlecloudpresscorner.com/2026-11-10-UiPath-Accelerates-Enterprise-AI-Transformation-with-Google-Clouds-Gemini-for-Healthcare)
3. [兴业银行金融智能体平台 · 同花顺 11-09](https://m.10jqka.com.cn/20261109/c681593732.shtml)
4. [合合信息三条产品线同时上线 MCP · 中国经济新闻网 11-09](https://www.cet.com.cn/wzsy/cyzx/10655992.shtml)
5. [PitchMe 招聘 MCP Server · HR Technology Wire 11-09](https://www.hrtechnologywire.com/press-releases/pitchme-lets-recruiters-search-and-shortlist-candidates-directly-from-chatgpt-groq-and-muse)
6. [CVE-2026-123811 / 123812 · AI Threat Alert](https://aithreatalert.com/cve/CVE-2026-123811) · [123812](https://aithreatalert.com/cve/CVE-2026-123812)
7. [官方参考服务器七 CVE 深度复盘 · AL-ICE 11-08](https://al-ice.ai/posts/2026/11/mcp-reference-servers-desktop-commander-seven-cve-batch-november-2026)
8. [腾讯云：MCP 认证与安全 — 授权是可选的，责任不是](https://cloud.tencent.com/developer/article/2779058)
9. [Best MCP Development Companies 2026（7 家报价，核于 11-09）](https://www.ayautomate.com/blog/best-mcp-development-companies)
10. [Upwork Senior AI Agent Engineer $1,500 fixed](https://www.upwork.com/freelance-jobs/apply/Senior-Agent-Engineer-Autonomous-Orchestration_~022119453403578691332/) · [Upwork 官方 MCP](https://opentools.ai/tools/upwork-mcp)
11. [自由平台费率横评 2026-11](https://yclas.com/blog/online-marketplaces-for-freelance-services.html) · [费率实务](https://bestjobsearchapps.com/articles/en/how-upwork-freelance-jobs-work-and-what-they-actually-pay)
12. [T-Investments 交易 MCP](https://thevalue.engineering/news/t-investments-mcp-protocol-ai-agents.html)
13. [Spring AI 2.0 MCP 迁移 Part 4 / Part 6 · dev.to](https://bizarro.dev.to/aws-heroes/building-ai-agents-with-spring-ai-and-amazon-bedrock-agentcore-part-4-provide-mcp-tools-for-2odf) · [Spring AI vs LangChain4j 2026](https://dev.to/jamilxt/spring-ai-vs-langchain4j-which-java-ai-framework-should-you-choose-in-2026-4mop)
14. [Agent Gateways 六平台横评 · dev.to](https://dev.to/varshithvhegde/agent-gateways-are-coming-here-are-the-first-6-platforms-building-them-2026-pfj)
15. [广发 13321 / 态势感知扩容 / 13322 官方原文](http://gfjc.gf.com.cn/gyszjgg/13321.jhtml)
