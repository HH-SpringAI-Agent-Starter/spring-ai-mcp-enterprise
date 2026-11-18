# MCP Server 挣钱情报日报（2026-11-18 · 近 3 天实时抓取）

> 任务 6 完整输出：企业需求 / 招标 / 招聘 / 开源动态 / 价格 / 用户 Java+Spring+AI 卖点。
> 抓取窗口 11-12~11-18，来源 18 项，全部附链接。⚠️ 报价为公开挂牌价/估算，非成交价。

## 一、今日最强信号（TOP 6）

1. ⭐⭐ **IBM ContextForge MCP Gateway `CVE-2026-110278`（CVSS 7.4，11-12）** —— per-server 身份转发配置被忽略 + **静态签名 payload 可重放**。身份被发给声明「不接收」的上游 server → 直接坐实本期第七段「身份传播面」。修 v1.1.1，无法升级须全局禁用身份传播（[来源](https://aigovernance.com/news/ibm-mcp-gateway-flaw-sends-user-identity-to-servers-configured-to-block-it)）。同期 `CVE-2026-110279` 客户端 header 覆盖上游凭据（[来源](https://aiagentstore.ai/ai-agent-news/topic/trading/2036-08-26)）。
2. ⭐ **Kaseya 11-17 首发 first-party MCP server**（Autotask / Datto RMM / IT Glue + 17 skills）—— 自报 **48% MSP 视 AI 为头号需求但仅 13% 变现**，20% 已在生产跑 MCP，原文定位「**MCP as the Governance Layer, not just a technical convenience**」→ 治理层是变现钥匙的最佳第三方背书（[来源](https://futurumgroup.com/insights/kaseya-bets-on-agentic-it-to-close-msps-ai-revenue-gap/)）。
3. ⭐ **广发研究 MCP&Skills 已上线智慧数平台**（11-13/11-16 二次报道）：21 行业 / 200+ 数据源 / 9 万+ 指标 / 1000+ 公司盈利预测 / 900+ 产业图谱 → **买方已自建好，钱从「帮你建」转到「帮你管/帮你治理」**（[观点网](https://www.guandian.hk/article/20261116/10065424.html) / [网易](https://dy.163.com/article/LBKQS44F0519D45U.html)）。
4. ⭐ **券商 AI 协议战定性**（11-16）：广发明确「依托 MCP 接入生态」，一次封装多处调用；**数据按客户经理归属严格隔离**；恒生研究院「业务本体地图才是壁垒、堆 Skill 无护城河」；第三方内测 Skill 准确率最高 95% **仍需人工把关** → 确定性治理执行门 + HITL 有硬需求（[联索科技](https://lexotech.com/news/news-content?did=87674686f0e2f2c8a1a985ff9a27f802)）。
5. **Impartner MCP 11-17 上线**：PRM 数据接入 Claude/ChatGPT/Copilot/Gemini Enterprise，「governed by existing permissions」+ 敏感字段隐去 → 买方话术已把「复用已有权限 + 字段最小化」写进产品定义（[来源](https://impartner.com/resources/blog/impartner-launches-mcp-server-to-connect-ai-assistants-with-live-prm-data)）。
6. **安全面持续放量**：AWS `CVE-2026-125926`（mcp-gateway-registry SSRF，1.0.3–1.34.0，1.35.0 修，存量 upstream URL 须人工复核）、`CVE-2026-100307`（管理 API 提权）；AWS 11-16 双洞 Timestream `126681` / Pricing `126816`；llm CLI `GHSA-v9h7` 泄漏 OPENAI_API_KEY（[agentbeam 攻击追踪](https://agentbeam.com/attacks) / [junglewise AWS 漏洞库](https://junglewise.ai/threats/technologies/aws)）。

## 二、哪些企业在招 MCP 人才 / 用 MCP（近期具体主体）

| 主体 | 事实 | 对位 |
| --- | --- | --- |
| **Kaseya**（MSP 平台） | 11-17 首发 Autotask/Datto RMM/IT Glue 的 MCP server + 17 skills，GitHub early access | first-party MCP + 治理层 |
| **Impartner**（PRM） | 11-17 上线 Impartner MCP，复用现有权限、敏感字段隐去 | 权限复用 + 字段最小化 |
| **天眼查** | 天眼查 MCP 上线 ModelScope（11-18 标注），覆盖工商/股权/风险诉讼/招投标 | 企业数据 MCP 化 |
| **券商群**（广发/华泰/招行信用卡） | 广发研究 MCP 上线、华泰「行知」MCP 能力集合、招行信用卡首批 A2A 接入千问智能体 | 券商能力一次封装多处调用 |
| **SnapLogic**（11-17） | Discovery Hub：MCP 中央注册 + 治理 + 可观测（历史期） | 竞品/买方对照 |
| **海康威视 / 法本 / 智慧树 / 小米**（历史期点名 JD） | Spring AI Alibaba + HITL + MCP Server/Skills；Java 扩展开发 MCP Server；工具调用 MCP 协议 | Java+Spring+MCP 直接命中 |

## 三、哪些项目在招标 / 什么价

| 项目 | 金额/截止 | MCP 关联 | 可投性 |
| --- | --- | --- | --- |
| 广发四份征集（态势感知/资讯数据/企业微信/AI 运维） | 13321 11-13 截 / 13322 11-15 截 / 13327 官方正文无截止须核实 | 五件套 + MCP 安全工具管理 + MCP 封装接入 | 预征集≠采购邀请；7 项资质不满足 → **只能联合体技术分包** |
| 山东重工集团级大模型平台（二次） | 11-20 截，潍坊，接受联合体 | 统一入口/服务/管理/**安全管控** + 智能体平台 + 大模型安全护栏 | 制造业集团级治理卡位，命中 registry+gateway+governance |
| 中原银行企业级 AI 网关 | 服务期 14 个月，1 家中标 | **AI 网关 + MCP 代理**写进正式公开招标 | 金融 MCP 网关主标位 |
| ZC26G280436（航空工业西安飞控所） | **683 万中标**已落地 | AI 网关 + MCP 注册 + 多模态平台 | 600–800 万级央企生产预算铁证，治理独占 ~20%≈137 万 |
| 国家管网 4089 万（讯飞 2686 万+联想 1402 万）/ 河北政采 48.9 万 | 11 月 | **未点名 MCP** | ⛔ 不计入可投清单，仅作智能体预算锚 |
| 重庆 AI 大单 3.4412 亿 / 中央民大 3712 万 / 呼市 900 万 | 已定标/11-23/11-25 | 含智能体应用场景 | 分包与案例背书窗口 |

## 四、什么价 —— 接单定价锚（本期更新）

| 来源 | 价格 | 口径 |
| --- | --- | --- |
| **WebsNexus**（MCP server 定制） | 单系统只读 **$299 起**；多系统写+OAuth+审计 **$899 起**；1–2 周 / 3–6 周 | 公开挂牌 fixed quote |
| **Innovation Insight**（AI agent 工程师外包） | mid **$3,600–4,800/月**（$23–30/hr）、senior **$4,800–6,600/月**（$30–41/hr）、lead **$6,600–7,800/月**（$41–49/hr）；美国自建 ~$16,200/月 | 全包月费，含设备/HR |
| **Datamata**（Java 岗，11-17） | 中位 **$122,863**（145 listing，4864 岗样本） | Java 技能基线 |
| **Freelance Task Insights**（11-14） | Python 类目里「MCP Server & Claude Connector Development」**8 个 job**；生产工程（Docker/测试/权限隔离）需求并列 | MCP 已进入自由职业需求清单 |
| **Upwork MCP rate limits** | 搜索 15/min·150/hr·750/day；提案 20/min·60/hr·150/day；**免费使用、标准费率 0–15% 另计** | 平台规则（11 月） |
| 历史定价锚（V1.67/11-09） | 生产级 $80K–220K；企业网关 $250K 起；Dawloom 同范围 7 档 $3,700–$72,000（19 倍价差） | 「涨的不是代码量，是治理层」 |

→ **报价策略**：按交付物/集成/修订轮次/工时拆（OpenAI×Upwork 固定价预算 +52.4%、时薪仅 +1.5%），别只抬时薪；报价 10 倍定律对位 21 模块治理层。

## 五、用户的 Java + Spring + AI 组合在该赛道的卖点（8 条）

1. **存量 Java 权限复用**：企业已有 Spring Security / RBAC / 数据归属体系，本框架在 MCP 调用点复用，不需重建身份体系——正对 Impartner「existing permissions」与券商「按客户经理归属隔离」。
2. **确定性执行门**：相对「提示词护栏 100% 失守 / Skill 准确率 95% 仍需人工把关」，模型外确定性门是可验收边界（七段式）。
3. **身份传播面首发能力**（本期）：CP1–CP6 对位 IBM 110278/110279，是同质化 MCP server 里的差异化卖点。
4. **私有化合规工件**：等保/密评/备案 + 审计留痕 + 字段脱敏，Python 系弱项、Java 主场（「不能上云的钱最好赚」）。
5. **Spring AI Alibaba 兼容层**：用户主技术栈，Nacos 3.3.0 MCP 生命周期 / Higress v2.2.6 周边生态密集发版，核心基于官方 SDK、Alibaba 可选不进主 reactor。
6. **七段式可演示样品**：`examples/` 下 9 组纯 JDK 守卫，javac + main 自检全绿，售前可现场跑、进标书可当「可演示案例」。
7. **定价权锚**：治理层 10 倍价差 + WebsNexus $299/$899 + 生产级 $80K–220K，把「写个 server」抬到「交付治理层」。
8. **AAIF 规格变更持续复验**：MCP/Agent Skills 归 Linux Foundation 中立治理，规格变更无厂商补丁通知——本框架把「规格版本当受监控依赖 + 变更后全量复验」做成年度托管服务。

## 六、明天候选

1. 发 SEO 稿抢「IBM 110278 身份转发 / 静态签名重放 / 身份传播面 / Kaseya MCP」流量。
2. 12 项身份面验收清单 + 15 断言输出压 1 页 PDF，交广州本地联合体主体。
3. 评估把 CP1/CP3/CP4/CP5 接进 `mcp-gateway`（独立分支先跑通 15 断言）。
4. 备标山东重工 11-20 + 中原银行 AI 网关；回广发平台核实 13327 报名截止。
5. 跨期待办：`exportPrometheus()` 挂 actuator、dataClassification 审批引擎、重定向逐跳 SNI 校验、独立分支评估 Spring AI 2.0 升级。

## 七、来源清单（18 项）

见正文各段超链接，覆盖：AI Governance Institute、Futurum Group、网易/观点网、联索科技、Impartner 官方、agentbeam 攻击追踪、junglewise AWS 漏洞库、AI Agent Store、DEV Community、freelancetaskinsights、WebsNexus、Innovation Insight、Datamata、UpHunt(Upwork MCP)、Hunting Cube、pdf-book-search(Freelance Rates)、Kongchang(Java→LLM)、广发智慧数平台。
