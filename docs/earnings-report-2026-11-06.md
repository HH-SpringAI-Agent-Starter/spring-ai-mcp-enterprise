# 💰 MCP 挣钱情报 · 2026-11-06（V1.64）

> **数据窗口**：2026-11-04 ~ 11-06 实时抓取（web_search）+ 本地「财富机会雷达 2026-11-04」私有化清单
> **项目**：[spring-ai-mcp-enterprise](https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise) — 21 模块 Java/Spring Boot 企业级 MCP Server 治理框架
> **定位**：只写「谁在付钱、付多少、怎么接到」，不做新闻摘录。所有金额、日期、JD 原文均标注出处，可直接进标书/售前一页纸。

---

## 0. 今日一句话结论

**MCP 已经不是「未来风险」，而是「正在被打的入口」——攻防两端在同一周同时坐实。**

- **攻**：Wiz.io 用 90 天蜜罐实录了攻击者把 LiteLLM / MCP / LangChain / Flowise / Langflow / OpenWebUI / Node-RED 当**头等云入口**打：`CVE-2026-59822` 一个字符的 bearer token 就能过 MCP Gateway 鉴权，链式未认证 RCE 被归因到 **Qilin 勒索团伙**；攻击者甚至从运行中的 Python 进程内存里直接抠出 proxy master key（磁盘上根本没有这个文件）。
- **守/买**：**广发证券 3 天内第二个把 MCP 写进核心建设内容的供应商征集**（11-13 17:30 截止）、**山东重工集团级大模型平台二次公开招标**（11-20 截止，明确「统一安全管控」）、**重庆 3.4412 亿 AI 大单 11-03 定标**（含智能体应用场景）；招聘侧周大福（中国香港）把「一致的身份传播、访问控制与审计追踪」逐字写进 MCP 共享工具层 JD。

> **结论：把责任放在「我能治理的那一层」——这正是 Java/Spring 栈最确定的收费理由。**
> 官方 SDK 与云厂商给的是「连接 + 工具」，招标方与攻击者同时证明缺的是「治理」。本框架 21 模块的定位从「治理增强层」升级为 **「攻防可验证的治理控制平面」**。

### 与昨日（V1.63）的差异 — 不重复记账

| 维度 | V1.63（11-05） | **V1.64（11-06）** |
| --- | --- | --- |
| 安全叙事 | 超大厂怎么建（Uber 800+ servers）+ 规范空白（4 MiB DoS、扫描器漏报） | **攻击者已产业化**（90 天实战蜜罐、勒索团伙、内存窃取凭据）+ 11-03~11-05 NVD 新 CVE 波 |
| 采购叙事 | 广发首次把五件套写成资质门槛、ISI/Agiloft 受治理 MCP 上线 | **同一券商双连发** + 集团级平台标 + 巨单从招标走到定标 |
| 方法论 | 生产架构对标、12 项合规对照表 | **「选型 = 把责任放在能治理的那一层」**（Spring AI 2.0 三条路径）+ 私有化招标必选项 |
| 交付物 | 情报 / SEO / 版本表 | 情报 / SEO / 版本表 + **`examples/governance-hardening/` 参考实现** + 两份既有文档加固 |

---

## 1. 🔥🔥🔥 攻击面：AI 网关 / MCP 已被当成产业化云入口（今日最强新证据）

**来源**：[Wiz.io 研究（secnews.in 11-06 转述）](https://secnews.in/article/article-hackers-target-ai-infrastructure-rce-and-credential-theft/) — 约 90 天蜜罐，模拟 LiteLLM、MCP、LangChain、Flowise、Langflow、OpenWebUI、Node-RED 部署，观察到**定向且持续**的攻击。

| 攻击路径 | 实证细节 | 对本框架的含义 |
| --- | --- | --- |
| `CVE-2026-59822` LiteLLM **MCP Gateway 认证绕过** | MCP 函数可接受**一个字符**的 bearer token | 网关统一鉴权必须 fail-closed，不能信「有个 token 就算过」→ `mcp-gateway` + `mcp-auth` |
| `CVE-2026-42271` 测试端点命令注入 | 提交伪造 MCP server 配置，`command` 字段启动 Python downloader + 矿机，**握手仍返回正常** | 「注册/连通性测试」是高危面，工具配置必须参数校验 + 沙箱 → `mcp-registry` + `mcp-governance` |
| `CVE-2026-48710` Starlette host-header 绕过 | 与上两条链式 → **完全未认证 RCE**，归因 **Qilin 勒索 affiliates** | Host/Origin 校验属网关层，不是业务层 → `mcp-gateway` |
| **凭据从进程内存里抠** | 攻击者在已控主机上直接查询运行中的 Python 进程，取回 LiteLLM **proxy master key**（磁盘无此文件），随后列后端模型挑 API-key 盗窃 / 盗用付费模型额度 | 长驻密钥 + 集中式代理 = 单点凭据仓库；需短期令牌、按调用授权、密钥不落进程明文 → `mcp-auth` + `mcp-monitor` |
| 盲提示注入拿 shell | 内容里埋指令诱导有 shell 权限的 agent 执行，DNS 带外回调确认；Base64 + Pastebin 降低日志/过滤可见性 | 工具入参出参全留痕、出站流量白名单 → `mcp-monitor` + `mcp-governance`(HITL) |
| XMRig 伪装 AI 工具 | Node-RED 部署矿机；Langflow 上藏在隐藏 `.claude/` 目录改名 `unicorn` | 监控必须覆盖 shell 派生与异常子进程，而非只看 HTTP 状态码 → `mcp-monitor` |

**Wiz 给防守方的原话建议**：把任何 AI 代理/网关当**凭据与云权限存储**对待 —— 强制认证、按最小权限收敛、限制出站、快速打补丁、监控 shell 派生/压缩包下载/异常子进程。

> 📌 **可直接进标书的话术**：「本方案不假设上游 SDK/代理是安全的。攻击面覆盖鉴权绕过、注册期命令注入、跨源重定向、进程内存凭据、工具侧 shell 五类，逐类在网关/注册中心/治理/监控四层设置独立控制点，并提供可验证的审计证据。」→ 详见 [`docs/enterprise-rfp-checklist.md`](enterprise-rfp-checklist.md) 今晚新增的「攻击面 → 治理模块」一页纸。

---

## 2. 🔥🔥 11-03~11-05 NVD 新 CVE 波：「MCP 是审核更少的接口」

| CVE | 发布 | 问题（根因） | CVSS | 命中模块 | 来源 |
| --- | --- | --- | --- | --- | --- |
| `CVE-2026-123272` / `CVE-2026-123271` | 11-05（NVD） | **mcp-go / ChatCLI OAuth resource confusion**：客户端让服务端自行指定 token 的 `resource`，未做 RFC 9728 §3.3「元数据声明的 resource 必须就是我所访问的资源」校验 → 恶意 server 可命名同一授权服务器下的另一 resource，拿到 audience 绑定的 token 后重放。CWE-346，同一研究员同期报出两处 | 8.0 High | `mcp-auth`（audience/issuer 强校验）、`mcp-gateway` | [AL-ICE 分析](https://al-ice.ai/posts/2026/11/mcp-oauth-resource-confusion-mcp-go-chatcli-november-2026) |
| `CVE-2026-111769` | 11-05（NVD） | **官方 TypeScript SDK** `<1.32.0` / `@modelcontextprotocol/client` `2.0.0~2.3.0`：跨源重定向保留自定义头与 `mcp-session-id`，HTTP 307/308 还重发 body（含 `refresh_token` / `client_secret`）；修 1.32.0 / 2.3.0 | 6.5 Medium | `mcp-auth`、`mcp-gateway`（同源校验/禁跟随重定向） | [Kalir Pulse](https://pulse.kalir.io/en/cves/CVE-2026-111769) |
| `CVE-2026-121458` | 11-03（NVD） | **Instatic**：MCP 内容读取工具**跳过**主 HTTP API 已实现的对象级授权，低权用户可读他人私有草稿、枚举 posts 表 | 7.1 | `mcp-governance`（工具级 ACL 复用业务授权） | [AL-ICE 分析](https://al-ice.ai/posts/2026/11/instatic-surrealdb-mcp-auth-cve-2026-121458-cve-2026-121419-november-2026) |
| `CVE-2026-121419` | 11-03（NVD） | **SurrealDB 3.1.0~3.3.0**：MCP 会话指纹由「access method 名」而非「用户身份」推导 → 同方法用户指纹碰撞，持低权 token + 受害者 `mcp-session-id` 即可劫持会话、以受害者角色执行工具 | 8.6 | `mcp-tenant`（租户/主体隔离）、`mcp-auth` | 同上 |
| IBM ContextForge MCP Gateway **7 个 CVE**（近 90 天） | 最新 10-20 | 含 `CVE-2026-78573` **默认凭据认证绕过 9.8 critical**、jq 注入凭据泄漏 8.8、A2A 端点 SSRF 8.2、DNS rebinding SSRF 7.7、嵌套 payload 检查绕过、路径穿越、OAuth registration SSRF | 最高 9.8 | **「用了网关 ≠ 安全」再实锤** | [Junglewise 11-05 快照](https://junglewise.ai/threats/technologies/contextforge-mcp-gateway) |

**模式总结（AL-ICE 原话 lineage）**：AWS API MCP Server 跳过 audience 校验 → TS SDK 会话混淆 → Chainlit URL grants 被分号击穿 → RHOAI 网关跨租户配额 → 本周 Instatic（**身份对、对象检查没做**）+ SurrealDB（**会话没绑到用户**）。

> **这不是编码疏漏，是「规范阅读习惯」问题**：实现者接好了 RFC 9728 discovery 与 RFC 8707 resource indicator，唯独漏掉「验证元数据描述的是我实际访问的那台 server」这一步——因为测试环境里元数据永远是对的。
> 对照本框架：**4 MiB 请求体上限 + fail-closed + 同源重定向拒绝 + 会话按用户指纹 + 工具默认禁用 + 按 caller 授权 + 全程审计**，今晚已落成可参考的实现片段 → [`examples/governance-hardening/`](../examples/governance-hardening/README.md)。

---

## 3. 🔥🔥 招标 / 采购：券商双连发 + 集团级平台标 + 巨单定标

| 项目 | 关键时间 | 金额 | MCP 相关要求（原文要点） | 切入口 | 来源 |
| --- | --- | --- | --- | --- | --- |
| **广发证券《智能金融资讯数据服务平台》供应商征集（延长）** | **11-13 17:30 截止** | 未公开（预征集） | 「引入 **MCP 工具协议、Skill 技能包、子 Agent 编排、长期/会话记忆和混合检索**…建设**券商级**智能金融数据服务平台」「沉淀**可外部产品化**的能力组件」；资质：成立 3 年+、注册资金 ≥500 万、原厂或授权代理、近 3 年 ≥1 个**金融行业**成功案例、熟悉证券行业数据安全与合规 | `mcp-registry` + `mcp-governance`（工具/技能/子 Agent 的治理）+ `mcp-tenant` + 审计留痕 | [gfjc.gf.com.cn 13321](http://gfjc.gf.com.cn/gyszjgg/13321.jhtml) |
| **广发证券《2026 企业微信持续建设项目》**（11-04 记录，延续） | 征集进行中 | 未公开 | 「**MCP 工具调用稳定且权限可控**」「统计口径与金管家后台一致」「**留痕可完整回溯**」「定制开发部分（含智能体、技能、MCP 工具）**知识产权归需求方**」 | 五件套（工具定义/参数校验/鉴权限权/调用限流/日志可观测）+ 多租户隔离 + 防篡改留痕 | [gfjc.gf.com.cn 13322](http://gfjc.gf.com.cn/gyszjgg/13322.jhtml) |
| **山东重工集团 人工智能算力及平台建设项目（二次公开招标）** | **11-20 截止**，潍坊，接受联合体 | 未公开 | 建「集团级大模型平台底座」「**统一入口、统一服务、统一管理、统一安全管控**」，覆盖大模型开发管理与**智能体应用平台**、**大模型安全护栏**、企业知识库、代码开发平台；含应用场景实施 | `mcp-registry`（统一目录）+ `mcp-gateway`（统一入口）+ `mcp-governance`（安全护栏） | [ebnew 710582880](https://www.ebnew.com/businessShow/710582880.html) |
| **重庆 AI 大单（11-03 中标候选人公示）** | 已定标 | **3.4412 亿元** | 重通院 + 中移系统集成 + 科大讯飞 + 中智股份联合体；范围「算力基础设施、行业模型、高质量数据集、**智能体及应用场景建设**」 | 从招标到定标落地 → 智能体工具接入/治理可作分包与案例背书 | [网易订阅](https://dy.163.com/article/LAT97IUB05568W0A.html) |
| **中央民族大学 智算集群+多模态大模型**（GC-HGX261278） | 11-25 09:00 截 | **3712 万** | 含「民族语言多模态大模型智能体」 | 智能体层/知识库层分包 | 财富机会雷达 11-04 |
| **广西「数智方志·国门工程」** | 11 月预采 | **1450 万** | 桂海方志大模型 + **6 个智能体协同** + 大模型备案 + **等保 + 密评** + 数据标注标准 | 治理/合规/多智能体编排 | 财富机会雷达 11-04 |
| **呼和浩特卫健委 卫生健康智能体** | 11-23 截 | **900 万**（包1 数据治理+智能体 770 万） | 数据采集治理 + 智能体，**接受联合体** | 联合体投标最佳样本 | 财富机会雷达 11-04 |
| **上海市公安局 视觉大模型能力支撑子系统** | 11-03 已中标 | **1149.89 万** | 全警种两网两级视觉大模型底座 | 已定标，做分包/运维 | 财富机会雷达 11-04 |

**🔥 本期最重要的采购信号**：**同一家头部券商在 3 天内连发两个把 MCP 写进核心建设内容的征集**。11-04 那条是「MCP Server 工程能力」写进**供应商资质门槛**；11-06 这条更进一步——MCP 与 Skill、子 Agent、混合检索并列成为**券商级投研平台的技术底座**，且明确「能力组件可外部产品化」。这说明金融买方已从「试点」进入「平台化采购」，预征集→正式招标通常 30~60 天，**现在是卡位窗口**。

**🔥 私有化铁律（财富机会雷达 11-04 原话）**：**「不能上云的钱最好赚。」** 招标书里「私有化部署」已从技术选项变成**必选项**；大模型备案、等保测评、密评三项已是标配预算项，纯服务费、毛利高。**Java + Spring AI + 私有化 RAG + 多智能体是这类标的主场，Python 系（LangChain 派）反而是弱项**。存量 Deadline 提醒：厦大 **11-11**、军队大模型安全防护 190 万 **11-16**、中山 399 万 **11-17**。

---

## 4. 🔥 招聘：治理四件套被逐字写进 JD（今日最精准一条）

| 企业 / 岗位 | 地点 | 报价 | JD 原文要点 | 命中模块 |
| --- | --- | --- | --- | --- |
| **周大福珠宝集团 · Assistant Manager, Enterprise AI Engineering** | **中国香港**团队（覆盖内地/香港/海外） | 未公开（管理岗，5 年+） | 「Develop a **shared tool layer using MCP** to connect AI agents with enterprise systems and knowledge, with consistent **identity propagation, access controls and audit trails**」+ 模型网关按**数据地域**路由 + 用量配额与成本控制 + 自动评测回归 + 日志/追踪/告警/成本监控 | `mcp-gateway` + `mcp-auth` + `mcp-audit` + `mcp-tenant` + `mcp-monitor` — **逐条 1:1** |
| **美团 · CatPaw Managed Agent 平台 / AI Agent 开发工程师** | 北京 | 未公开 | 「**MCP Server 的接入与管理、工具沙箱与权限管控**」+ Skill 加载触发 + SubAgent 生命周期 + Workflow 确定性编排 + RRULE 定时任务/断点续跑 + 可观测性；技术栈 **Java/Spring Boot 列第一** | `mcp-registry` + `mcp-governance` + `mcp-tenant` + `mcp-monitor` |
| **美团 · Java 研发工程师（算法工程方向）** | 北京，11-04 更新 | 未公开 | 3 年+ 分布式、精通 Java/微服务/MySQL/NoSQL；「AI Agent 探索和实践」、**Harness Engineering**、AI Coding | 存量 Java 系统 Agent 化 |
| **猎聘 · 深度学习（AI 基础设施）** | 深圳，10-28 更新 | **35–55k · 16 薪** | 「工具调用（Tool Calling / **MCP** / Skill）」；**Spring AI 显式列为框架之一**；多模型路由/性能/成本；Java/Python/Go | `mcp-gateway` + `mcp-monitor` |
| **赛舵智能 · AI 软件工程师（全栈）** | 上海徐汇 | **20–40k** | 「基于 **MCP** 等协议构建工具层，开发维护 Tools、Skills、**MCP Server 及 CLI 工具链**」+ tracing/日志/监控/告警 + eval 回归 + 超时/重试/降级 | `mcp-tools` + `mcp-monitor` + `mcp-registry` |
| **Disney Animation · Sr. SWE, Emerging Technology** | 温哥华 | 未公开 | 「**Develop MCP servers, APIs, and integrations (including managing MCP servers for secure tool-use by AI agents)**」+ guardrails + LLM security practices + A2A | `mcp-governance` + `mcp-auth` |
| **Mastercard · Senior Software Engineer (Java), Fraud Platform** | 都柏林 | 未公开 | JDK 17+ / **Spring Boot** / Hibernate / REST、secure-by-design、PCI-DSS、分布式缓存 | 金融 Java 底座 |
| **PE 系 Applied AI Engineer（Cursor / Claude / MCP / Fullstack）** | 远程 | 未公开 | 「Curate and maintain a best-in-class set of AI development tools, **MCP servers, and reusable components** for deployment across the portfolio」+ 遗留系统 AI-first 重构 | `mcp-registry`（共享工具层） |

**国内明确报价延续（V1.63）**：建发集团 **50–80k**（厦门，RAG/Skill/MCP 开发 + Java/Spring Boot/Cloud）、上海整车厂 Harness **40–70k·15 薪**。

> **卖点提炼**：周大福这条是「治理四件套」第一次以**采购方/雇主方口吻**完整出现（identity propagation / access controls / audit trails / 数据地域路由 / 配额成本）。过去是我们论证「企业需要治理」，现在是**企业自己把治理写进岗位说明书**——售前材料可以直接引用 JD 原文，不必再自建论证链。

---

## 5. 💵 接单：Upwork 官方数字复核 + 可落地操作细则 + 报价方法论

**官方财报电话会（11-02，[PYMNTS / Pivot News](https://pivotnews.ai/talent/upwork-ceo-says-ai-skilled-talent-volume-rose-55-to-112)）**

| 指标 | 数值 | 解读 |
| --- | --- | --- |
| Upwork MCP Server 周活 | 自 8 月上线 **×20** | MCP 已是真实交易入口，不是概念 |
| Q3 AI 技能人才 GSV | **+55% YoY 至 $112M** | 结构性增量 |
| 总 GSV | **−10% 至 $914.1M** | 大盘收缩，钱只往 AI 实施类集中 |
| GSV / 活跃客户 | **+8% 至 $5,418** | 客单价上行、客户向大额迁移 |

**Upwork Now 公测细则（11-02，[Digital Solo Hub](https://digitalsolohub.com/ai-automation-jobs-on-upwork)）**：客户端开「Hire Now」、自由职业者开「Work Now」；**5 分钟内不响应邀请/消息会话即结束**，网页与移动端 5 分钟无活动也结束；录用后客户预期**当日开工**，且计入 JSS。→ 建议只在「能当天修好一个坏掉的 Zap / 卡住的工作流」时开启，**绝不用于未界定范围的构建**。

**费率口径修正（10-22 核对 Upwork 官方，[Earninger](https://earninger.com/store/upwork-freelance-marketplace)）**：自由职业者 **0–15%**（提案前显示、合同期内固定）+ 额外 Connect **$0.15/个**；客户 Basic **3–5%**、Business Plus **8–10%**；⚠️ 很多旧文仍在写「20/10/5% 滑动」或「统一 10%」，**已过时**，报价公式用错会直接吃掉利润。

**定价公式（可直接抄）**：`Phase 1 = 工时 × 底价 × 1.25（意外缓冲） ÷ (1 − 显示费率)`。例：10h × $25 × 1.25 ÷ 0.90 ≈ **$347**。月费（工具 / AI / 托管）**单独向客户收取，不藏进报价**；必须等里程碑显示 **「Funded」** 才开工；扩范围先开新里程碑。

**报价梯度（沿用 V1.47/55/63 官方定价页与乙方实测）**：单连接器 $1.5K–5K｜多工具 + auth $5K–15K｜**企业网关 $15K–40K**｜定制平台 $30K–80K｜运维 $2K–7K；Reflekt Lab scoping $2.3–4.7K / 首 workflow $17.5K+ / 多系统写 $35K–90K / 受监管六位数；UpHunt 指南 **MCP 实现 $150–300/hr**。

**方法论（财富机会雷达 11-04，比单价更重要）**：
- Upwork **「AI 编排者（orchestrator）」比「AI 执行者」年收入高 45%**，该岗位需求 **+72%**；AI 集成需求 **+178%**、AI cleanup **+70%**（Freelancer +87%，10,760 条）。
- **不要按「接代码单」报价，要按「我帮你把 AI 塞进业务流程并验收」报价——同样工作量差 2–3 倍。**
- 提案别承诺「fully autonomous」，主动写明「哪一步需要人工审批」反而更可信（与 Agiloft 写操作强制人工确认、HITL 治理同一逻辑）。

---

## 6. 🌐 生态 / 标准层：批评已从「会不会赢」转向「赢了之后的治理」

- **《The State of Agentic AI Standards in 2026》**（[dev.to](https://dev.to/alexmercedcoder/the-state-of-agentic-ai-standards-in-2026-mcp-a2a-webmcp-osi-and-the-protocol-stack-taking-3o2l)）：MCP 已是「**不再有争议的工具层**」；A2A 1.0 带签名 Agent Cards、150+ 生产组织、5 语言 SDK；**Snowflake 运行托管 MCP Server 并专门收购一家企业 MCP 治理公司**（agent 身份 / 权限 / 审计），Dremio 用 MCP 暴露受治理的 Iceberg 数据。原文：「当最保守的企业数据平台开始围绕协议构建治理产品，标准化争论就结束了。」公开批评已转向 **上下文膨胀、工具接口即注入面、治理归属** → **治理是「赢了之后的问题」，不是可选插件**。
- **Spring AI 2.0 三条路径**（[CSDN DevPress 11-06](https://devpress.csdn.net/aibjcy/6aeb1a0f40858c2d88188388.html)）：官方集成 MCP Java SDK 2.0.0、Streamable HTTP 默认、`@McpTool/@McpResource/@McpPrompt`。文章列出 demo → 生产的约束清单：鉴权接既有 OAuth2/JWT + **工具级授权**、分级超时、**按租户/用户/工具/模型分别限流**、成本按调用链归集可对账、**审计全留痕**（提示词/检索片段/工具入参出参/决策依据）、检索空/模型超时/工具不可用的**降级出口**、幂等键/副作用补偿/人工复核/断点续跑。
  > **原文金句（可直接做 SEO 标题）：「选型的第一原则不是哪个框架功能强，而是哪条路径把责任放在我能治理的那一层。」**
  > 这份清单与本框架 21 模块几乎 1:1 —— 不是我们自夸，是第三方按生产约束推导出来的同一份清单。
- **dev.to Java MCP 实践文**同样强调：「Authentication and authorization **must be enforced before exposing** network-accessible MCP servers. Multi-tenant applications **must preserve tenant isolation across MCP calls**.」
- **国内云厂商 MCP 市场放量**：腾讯云开发者 MCP 广场 11-06 新上架「企查查-风险信息 MCP」（StreamableHTTP/SSE，2K 分享）、「网页内容获取 Server」——**都只给连接和工具，不给治理**，正是治理增强层的增量空间。
- **Agiloft（11-05）细节补充**（[官方稿](https://www.agiloft.com/news/execution-focused-ai-agents)）：调研称采购负责人估计**至少 15% 供应商支出完全游离于合同之外**，仅 **36%** 组织自动把采购结果关联到合同；Bulk Update Agent **强制显式人工确认 + 先预览 + 输出成功/失败/跳过报告**；MCP Server beta 首批 Claude，明确「保留既有 permissions、controls、security standards」。

---

## 7. 🧩 Java + Spring + AI 组合在该赛道的 7 条卖点（可直接进售前 / 标书）

| # | 卖点 | 今日证据 |
| --- | --- | --- |
| 1 | **唯一 Java/Spring 开源企业级治理栈**，开源即给治理 | 网关横评 Bifrost/Docker/MS/AWS/Kong 清一色非 Java 栈，RBAC/SSO/签名审计锁在 Enterprise 付费墙；IBM ContextForge 自身 7 CVE 含 9.8 默认凭据绕过 |
| 2 | **存量资产接入，不重写业务系统** | 美团「现有平台向 MCP Server 标准化封装」/ 金山办公 / J2EE 现代化；山东重工要求「统一安全管控」的集团级平台 |
| 3 | **攻防实证可逐条交付** | Wiz 90 天蜜罐的 6 条攻击路径全部有对应控制点（第 1 节表），可写进标书「威胁建模」章节 |
| 4 | **合规就绪** | 等保 2.0 三级 / 多租户隔离 / 防篡改留痕 / 密评 / 信创，对齐「不能上云」的政务与金融标（广西 1450 万、呼市 900 万、中央民大 3712 万） |
| 5 | **与第三方生产约束清单 1:1** | Spring AI 2.0 三条路径文章逐条列出的鉴权/限流/审计/降级/幂等，等于本框架模块清单 |
| 6 | **官方 SDK 直连、不被上游绑架** | 核心治理层基于官方 MCP Java SDK；Spring AI Alibaba 仅可选兼容层、不进主 reactor（见 [`spring-ai-alibaba-compatibility-note-2026-11-01.md`](spring-ai-alibaba-compatibility-note-2026-11-01.md)） |
| 7 | **一人可交付** | 21 模块 + Docker Compose + CI，个人/小团队可接 $5K–40K 企业网关单，也可作为 600–800 万级央企标的治理分包（ZC26G280436 683 万锚点） |

---

## 8. 今晚交付 / 明天做什么

**今晚（V1.64 已落地）**
- `docs/earnings-report-2026-11-06.md`（本文件）
- `docs/blog-java-mcp-wiz-honeypot-gf-securities-governance-2026-11-06.md`（掘金/CSDN SEO 稿）
- `docs/V1.64-release-notes.md` + README 招标/招聘区块与版本表
- 🆕 `examples/governance-hardening/`（**参考实现**：4 MiB 上限 / fail-closed / 同源重定向拒绝 / 会话按用户 / 工具默认禁用 / 按 caller 授权 / 审计）
- 🆕 `docs/mcp-security-hardening-quickstart.md` 增补第 6 步；`docs/enterprise-rfp-checklist.md` 增补「攻击面 → 治理模块」售前一页纸

**明天候选（按优先级）**
1. **广发《智能金融资讯数据服务平台》11-13 17:30 截止** — 备齐「MCP 工具协议 + Skill 技能包 + 子 Agent 编排 + 混合检索 + 金融行业案例」应答素材，这是**最紧的窗口**。
2. 发 SEO 稿抢「Wiz 蜜罐 / LiteLLM 一个字符 bearer token / 广发证券 MCP 征集 / 山东重工」流量。
3. 山东重工 **11-20** 二次标备「统一入口/统一安全管控」交付素材（接受联合体，可找集成商带资质）。
4. 倒计时：厦大 **11-11**（5 天）/ 军队 190 万 **11-16** / 中山 399 万 **11-17** / 呼市 900 万 **11-23** / 中央民大 3712 万 **11-25**。
5. 把 Wiz 六条攻击路径做成 `mcp-monitor` 可落地的告警规则示例（shell 派生、异常子进程、出站白名单、非预期压缩下载）。
6. 跟踪 ISI / Agiloft 受治理 MCP 的第三方评测，补「写操作 HITL」对照案例。

---

*本文情报全部来自 11-04~11-06 公开可检索来源 + 本地财富机会雷达 11-04，链接已逐条标注。金额与截止日期以采购方原始公告为准；投标前请复核最新澄清/变更公告。*
