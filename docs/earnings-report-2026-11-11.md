# 财富机会雷达 · 2026-11-11（周三 21:30 CST）

> 抓取窗口 **11-09 ~ 11-11**（实时 web_search，含 11-11 当天带日期戳数据）；项目版本 **V1.69**。
> 本期一句话：**治理的起点被四股力量同时推回「工具注册进来的那一刻」——注解不是安全边界，元数据的诚实性第一次有了全生态量化基线，也第一次成了可写进采购条款的验收项。**

---

## 0. 今日结论（先看这个）

| # | 结论 | 硬证据 | 对「挣钱」的含义 |
| --- | --- | --- | --- |
| 1 | **注解不是安全边界**：`hints` 是给客户端看的 UX / 规划信号，授权必须在服务端 | [PowerDuck](https://www.powerduck.com/blog/mcp-tool-annotations-readonly-destructive)「hints, not enforcement」；[CSDN 信任模型](https://devpress.csdn.net/aibjcy/6af303c1e5f4ca4eff8fa677.html)「Hint 后缀是刻意设计」；[Harriet 11-09](https://harriethq.com/blog/what-is-an-mcp-gateway) 引规范「annotations should be considered untrusted」 | 卖「注册期独立定级 + 审批门」，不卖「加了 hint 就安全」 |
| 2 | **元数据谎报是全生态基线缺陷，不是个别 bug** | [INITE Atlas 11-11](https://inite.ai/en/atlas/mcp/report)：11,393 servers 中 **25% 缺 hint**、**4% 谎报非破坏性**、**23% 免登录仍改数据** | 首次有可引用的百分比 → 直接进标书「现状风险」章节 |
| 3 | **主流框架已把「发现期逐工具审批」做成产品能力，且明确否认元数据可作授权依据** | [Microsoft Agent Framework Python 1.25.0（11-09 发布）](https://www.cloudproinc.com.au/index.php/2026/11/11/microsoft-agent-framework-mcp-approval-policies-governance-guide) + 11-11 治理指南 | 买方会问「catalogue 变了审批还有效吗」 → 我们今晚补上**元数据漂移复验** |
| 4 | **传输层缺口从开源扩散到商业 IDE/低代码平台** | [Siemens SSA-710379（11-10）](https://severitydaily.com/siemens-mendix-studio-pro-mcp-server-cve-2026-112136-8-8-cvss-3-1-ui-r-vs-9-2-cvss-4-0-ui-n-at-p-cwe-1327-loopback-port-7782-ssa-710379-v11-16-0-v11-12-7-cve-2026-112137-csrf-origin-content-type) 双 CVE | 「绑 localhost 就安全」被官方否掉 → 售前可现场演示四件套 |
| 5 | **最紧的钱：广发三份征集进入 24 小时** | 11-12 / 11-13 / 11-15 17:30 截止（V1.68 官方原文核实） | 今晚把「注册期治理 + 24 类攻击面」压成一页纸附报名材料 |

---

## 1. 🔥🔥🔥 注册期 / 注解完整性：本期最强新叙事

### 1.1 INITE Atlas 全生态普查（数据戳 2026-11-11）

[inite.ai/en/atlas/mcp/report](https://inite.ai/en/atlas/mcp/report) 每天对每个 Atlas 厂商的 MCP server 发起会话并列出工具，**本页数字标注 `numbers from 2026-11-11 · checks since 2026-10-19`**：

| 指标 | 数值 | 占比 | 治理含义 |
| --- | --- | --- | --- |
| 当日检查 server 数 | **11,393** | — | 样本量足以支撑采购「现状风险」引用 |
| 无需登录即响应 | 5,954 | **52%** | 鉴权不是默认项 |
| 要求登录 | 5,252 | 46% | 其中 OAuth 7,035（91%），客户端自注册 6,844（97%） |
| **无 read-only / destructive hint** | **2,222** | **25%** | 四分之一工具连风险标签都没有 |
| **删除/发送/支付却标记「非破坏性」** | **326** | **4%** | **注解谎报实证** |
| 标只读却删除/支付 | 27 | ~0% | 极端谎报 |
| **免登录 server 仍会改数据** | **2,051** | **23%** | 无鉴权 + 写权限同时存在 |
| 参数无描述 | 4,226 | **47%** | 近半工具参数不可判定 |
| 有 `outputSchema`（typed result） | 2,645 | 29% | 结构化输出仍未普及 |
| 工具定义 context 成本 | 中位 ≈**1,355 tokens**；前 10% ≈7,876；最重 **390,757 tokens**（iDos Games 174 tools） | — | 「工具越多越难审」，支撑 tool-count 上限与 `search + execute` 模式 |
| 描述评分（9,069） | A 63% / B 30% / C 7% | — | 评分只测描述质量，**不测是否安全** |
| 30 天可用性（15,300 servers） | 中位 100%、14,232 家 ≥99% | — | 可用性不是治理问题，权限才是 |

> ⚠️ 口径：这是**厂商 MCP server 的公开普查**，不是 CVE 统计；「谎报」是工具描述与声明 hint 的自动比对，**不等于已被利用**。

### 1.2 Microsoft Agent Framework Python 1.25.0（11-09 发布 / 11-11 治理指南）

- 新增 **callable MCP discovery approval policies**：按工具逐个决定是否需要人工批准，而非全量固定开关；支持 Foundry Toolbox 的 **tool-name / read-only 过滤**、**explicit caller overrides**，以及**「MCP catalogue 更新后重刷审批决策」**（server 可在应用不重新构建的情况下改工具/元数据）。同时要求 Python ≥3.11、新增 MCP discovery 限制 → 应作为「受治理升级」而非小安全开关。
- 11-11 [CPI Consulting 治理指南](https://www.cloudproinc.com.au/index.php/2026/11/11/microsoft-agent-framework-mcp-approval-policies-governance-guide) 四条可直接进售前问答的判词：
  - 「**Metadata should be verified rather than blindly trusted**」「tool metadata ... **an input to your policy rather than the final security decision**」
  - 「**Read-only does not always mean harmless**」：只读检索公开文档 ≠ 只读检索高管邮箱
  - 「**Overrides need their own controls**」：override 必须记录谁请求、绕过哪条策略、理由、最终结果
  - 建议「approved list of MCP servers + known tool names + **independently defined risk categories**」
  - 并点出「**approval fatigue**」：全量审批会让员工无脑点同意 → 按风险分档是刚需

### 1.3 上架规则把 hint 变成自动权限判定依据

[Dawloom 11 月整理](https://dawloom.com/blog/submit-mcp-server-to-chatgpt-and-claude)：

| 平台 | 硬规则 |
| --- | --- |
| **OpenAI 目录** | `readOnlyHint` / `destructiveHint` / `openWorldHint` **必须显式 boolean**；每个操作独立工具；响应只返回与请求直接相关的数据，禁 session/trace/request IDs/timestamps；隐私政策须披露用户数据类型 |
| **Anthropic 目录** | 需 `title` + read/write hint（**决定 auto-permissions：只读可免逐次确认、destructive 必弹窗**）；**拒收 catch-all 工具**（原文点名带 `method` 参数的 `api_request`）；默认自动扫描上架为 Community connector，Verified review 才逐工具功能测试 |

[PowerDuck](https://www.powerduck.com/blog/mcp-tool-annotations-readonly-destructive) 补充四条工程口径：注解**不被协议强制执行**；「**Overstating safety is more dangerous than overstating risk**」；写外部系统（发邮件 / 触发 webhook）即便不改自己的库也是写；从 OpenAPI 生成注解时 `POST /search` 实为只读、`GET` 触发动作实为写，**必须人工复核**；确认框要展示受影响资源与批量条数，只显示工具名的确认「trivially accepted」。

### 1.4 云厂商与竞品同向卡位

- [Google Cloud Agent Registry 文档](https://docs.cloud.google.cn/agent-registry/manage-mcp-tools)（**最后更新 2026-11-10**）：Agent Registry 作中央注册表 + **Agent Gateway 强制执行有效政策**；IAM 定义「谁在什么条件下访问**特定工具**」；Gemini Enterprise 查看安全发现结果 → 逐工具 IAM 成第五朵云的标准产品层。
- [mcp-gatehouse v0.5.0](http://hol.org/registry/plugins/nickgeorgeseo%2Fmcp-gatehouse)（HOL registry，updated **Nov 7, 2026**，Python 3.10+ / MIT）：READ/WRITE/DESTRUCTIVE 分层 + 诚实 hints + **fail-closed 审批门**（无 approver / 异常 / 超时＝拒绝）+ append-only JSONL 审计 + 密钥脱敏 + denylist，无代理无外部策略服务。→ 同构竞品已出现，但**是单 server 内嵌 Python 库**；本框架差异化在 Java/Spring 跨 21 模块 + **元数据漂移复验** + 多租户/网关/审计工件。
- [Harriet 11-09 网关指南](https://harriethq.com/blog/what-is-an-mcp-gateway)：网关清单（Docker MCP Gateway / IBM ContextForge Apache-2.0 / Kong / Portkey / Azure APIM / Cloudflare）**仍清一色非 Java 栈或治理锁在付费层**；引规范「MCP does not decide whether the AI should have that access」。

---

## 2. 🔥🔥 传输层：Siemens 双 CVE 把「绑 localhost」打成伪安全

### 2.1 Siemens SSA-710379（V1.0，2026-11-10）

| CVE | 根因 | 评分 | 官方缓解 |
| --- | --- | --- | --- |
| **`CVE-2026-112136`** | CWE-1327「MCP Server 未把 HTTP listener 限制在 loopback」→ 网络可达者可远程交互，端口 **7782** | CVSS 3.1 **8.8 (UI:R)** / CVSS 4.0 **9.2 (UI:N, AT:P)** | 主机防火墙阻断外部 7782（标准 Windows 默认已如此）；需要远程访问走 VPN/SSH 隧道，不要开直连入站规则 |
| **`CVE-2026-112137`** | CWE-352 CSRF「未校验 `Content-Type` 与 `Origin`」→ 恶意页面让开发者浏览器向本地 server 发请求 | 5.4 / 5.1 | 用启用 Private Network Access 的 Chromium/WebKit，或用 uBlock/NoScript 挡外部站点 fetch localhost |

影响版本较窄：Mendix Studio Pro 11 `>= V11.15.0` 与 `= V11.12.6 < V11.12.7`，修 `V11.12.7+`。CVE 由 Siemens 10-19 预留、11-10 11:30 UTC 发布、NVD 12:17 UTC 收录、CISA SSVC 16:34 UTC 标注 `Exploitation none / Automatable no / Technical Impact total` → **目前无公开在野利用**。3.1 的 `UI:R` 与 4.0 的 `UI:N / AT:P` 是同一 scorers 用两代标准表达「部署是否可达」这一前提，**不是两份矛盾公告**。

### 2.2 与 Dynatrace 123811 及 11 月漏洞榜是同一条链

- [`CVE-2026-123811`](https://aithreatalert.com/cve/CVE-2026-123811)（7.5）：`--http` 无鉴权 / 无会话校验 / 无 Host+Origin → 浏览器经 DNS rebinding 直接 `execute_dql` 读日志、`create_dynatrace_notebook` 留恶意链接；**同 server 的 `send_event` 因 `requestHumanApproval` 门挡住同一攻击链**；修 2.0.0。合规映射 EU AI Act Art.15 / ISO 42001 A.6.2.6 / NIST AI RMF MEASURE 2.7 / OWASP LLM02、LLM06。
- [Adversa《Top MCP security resources — November 2026》](https://adversa.ai/blog/top-mcp-security-resources-november-2026) 九类资源中与传输/元数据直接相关的：
  - `@modelcontextprotocol/server-everything` 绑所有网卡 + **wildcard CORS** + 无鉴权 + `get-env` 返回整个 `process.env` → 任意网页跨域握手读走 API key（修 **2.0.1**）；
  - **Basic Memory** 按建议绑 localhost 仍被 DNS rebinding 打穿 —— 缺的是 **Host/Origin 校验**不是绑定地址；FastMCP 的 `HostOriginGuardMiddleware` **默认关闭**（升 0.24.0）；
  - **Envoy AI Gateway / agent-router** session resume 不校验内嵌 JWT subject + Helm 默认种子 `default-insecure-seed` → 可伪造他人 `Mcp-Session-Id`（多租户升 1.2.0-rc1）；
  - 官方 **TS server SDK 2.0.0–2.2.0** 按 JSON-RPC id 匹配消息（客户端都从 0 编号）→ 串客户消息；2.3.0 起拒绝共享 transport（stdio 不受影响）；
  - fastmcp M2M OAuth provider 不绑定 issuer → client secret 可被导向攻击者 AS，升 4.1.2 **还要显式设 issuer 并轮换凭据**；
  - 21,097 server 研究产出 13 条公告（含 Anthropic Git MCP Server 2 CVE）；投毒工具描述的日志攻击成功率 62.64–100%，16/18 配对任务完成率几乎不变 → **攻击不降性能，靠「看起来正常」无法发现**。
- [Harriet 11-09](https://harriethq.com/blog/what-is-an-mcp-gateway) 与 [dev.to OX Security 复盘](https://mailto:peter@dev.to/techsifted/anthropics-mcp-has-a-security-flaw-heres-what-developers-need-to-know-25hd)：扫描 200,000+ server、7,000+ 公开暴露；「MCP 配置当不可信输入」「沙箱」「监控工具调用」「只装可信来源」；Anthropic 更新 guidance **不等于修复**。

> **交叉验证（本期最值钱）**：V1.66 `connect-path-guard`、V1.67 `McpResolvedIpGuard`、V1.68 `stdio-command-allowlist` 三层参考实现，在 11-10 被 Siemens 官方公告 + 11 月 Adversa 榜单**再次逐条背书**；今晚新增 `examples/local-listener-guard/` 补齐「入站监听 + Content-Type/Origin/Host」这一层。可写进广发 11-12 报名的「可演示案例」。

---

## 3. 💰 招标 / 采购：能投的、倒计时的、不能算的

### 3.1 最紧窗口（今晚就该动）

| 项目 | 截止 | 剩余 | 硬门槛 / 动作 |
| --- | --- | --- | --- |
| **广发证券《2026 态势感知及安全分析平台扩容》** | **11-12 17:30** | **1 天** | 含「**MCP 安全工具管理**」+ 安全技能库 + 6 个安全智能体 + AI 安全数据底座；资质：原厂或授权代理、近三年 ≥2 金融案例 + ≥2 安全项目案例 |
| 广发 `13321`《智能金融资讯数据服务平台》 | 11-13 17:30 | 2 天 | MCP 工具协议 + Skill + 子 Agent + 混合检索、券商级、能力可外部产品化 |
| 广发 `13322`《2026 企业微信持续建设》 | 11-15 17:30 | 4 天 | **MCP Server 五件套写进资质**（工具定义与描述/参数校验/鉴权与权限控制/调用限流/日志与可观测性）+ 按客户经理归属隔离 + 日志不可篡改 + 指定 Dify/hiagent + 广州本地团队 |

⚠️ 三份均明确「**供应商预征集 ≠ 招标公告及资格预审公告**」，按时交资料不必然获得正式采购邀请；本项目**无商业合同、无中标、无金融案例、无等保测评** → 只能作**联合体技术分包**，禁虚构材料。应答包见 [`bank-agent-platform-tender-response-2026-11-09.md`](bank-agent-platform-tender-response-2026-11-09.md)、[`gf-securities-tender-response-2026-11-07.md`](gf-securities-tender-response-2026-11-07.md)、[`financial-agent-tender-compliance-2026-11-10.md`](financial-agent-tender-compliance-2026-11-10.md)。

**今晚新增可附材料**：[`tool-annotation-integrity-governance-2026-11-11.md`](tool-annotation-integrity-governance-2026-11-11.md)（注册期验收清单 + 四家规则矩阵）、`enterprise-rfp-checklist.md`（**24 类攻击面**）、两个可现场 `javac`+`main()` 演示的示例。

### 3.2 金融买方把 MCP 写进下一代架构（11-11 一手报道）

- [中信银行（同花顺 11-11）](https://m.10jqka.com.cn/20261111/c681716629.shtml)：下一代 App 拟采用「**Agent + Skill + MCP**」为核心的先进架构；「小信」累计服务超 **876 万户**、智能问题解决率 99.02%；**生成式大模型暂不能直接面客**（模型幻觉/算法黑箱未获监管放行），手机银行使用的生成式内容**都需经人工审核或预分析后调用** → **HITL 是监管硬约束，不是产品选项**，与 V1.68「法律责任分界线」完全同构。
- [广发证券（同花顺 11-09）](https://stock.10jqka.com.cn/20261109/c681591251.shtml)：自研投顾平台已集成**近 50 个专业智能体**、覆盖 300+ 营业部与数千名一线投顾；2025 年国泰海通 32.35 亿 / 华泰 26.79 亿信息技术投入 → 与三份征集互为印证，工具注册/纳管是刚需。
- [Ofweek 11-10 复述零壹智库 10 月全景](https://cloud.ofweek.com/news/2026-11/ART-178805-8420-30711294.html)：常熟农商「桌面智能体以 **A2A、MCP、CLI** 对接内部系统」；**数据类需求单独挂单**（国投瑞银智能体外部资讯数据服务 ≤10 万/年、不多于 3 家中标；国投证券「大师诊股」第三方数据；安徽担保高质量数据集）；**中信建投订阅智能体安全检测工具**、民生银行 AI 邮件安全智能体、上海银行单列大模型安全平台模块；15 条中标对应 12 家具名中标方，今日投资、高伟达各两单 → **安全评测 / 治理模块 / 合规工件 / 持续供数**是可切入的细分件。
- [兴业银行 / 北京农商 / 上海农商（移动支付网 11-09）](https://www.mpaypass.com.cn/news/202611/09092914.html)：兴业 10-21 招标「高级认知和作业处理能力的金融智能体平台」（软件许可买断）；北京农商覆盖**开发/纳管/调用/评测/观测/运营全生命周期** + ≥50 通用 Skill；上海农商 **「Skill、MCP 插件市场」** + 权限管理 → 「插件市场」天然要求**注册期准入**。

### 3.3 邻近机会（诚实标注：**未点名 MCP，不计入可投清单**）

- [上海电机学院 SDJU-2026224 快速采购（**11-11 当天发布**）](https://wmzx.sdju.edu.cn/2026/1111/c2200a157466/page.htm)：AI 赋能力学课程改造十大模块含「**外部智能体包导入与注册**」「教师自助配置参数化工具」「变更留痕可回滚」「智能体结论可回链至语料条目版本、来源可追溯」 → **注册期治理 / 审计口径已下沉到高校采购**，但公告未写 MCP，只能作趋势佐证。
- [太原理工 11-10 采购意向](https://cgzb.tyut.edu.cn/info/1092/34600.htm)：机械工程智能学科引擎 48.5 万（教学/科研两类学科智能体、知识图谱、云部署含安全一体化），未点名 MCP。
- 国家管网 4089 万智能体大单（11-06 中标候选公示）、军队大模型安全防护 190 万（11-16）、中山 399 万（11-17）、山东重工集团级平台（11-20）、呼市 900 万（11-23）、中央民大 3712 万（11-25）延续倒计时。

---

## 4. 🏢 招聘：本期诚实口径

**本轮 11-09 抓到的国内 Java 岗均未点名 MCP，不计入 MCP 直接机会**：兰笺（苏州）Java ≥1 万、厦门海菲生物 10–15k·13 薪、深圳智慧林 7–20K（应届）、奇瑞墨甲机器人 Web/Java 20–30k（语音 AI + **限流/熔断/降级服务治理**，属**邻近可迁移赛道**）。

**仍在有效期内的点名 MCP 主清单**（历史已录）：上交所技术公司（MCP 协议及 Skill 开发 / RAG / 多智能体 / 信创）、美团（Tool Use / MCP）、建发 50–80k、上海整车厂 Harness 40–70k·15 薪、阿里云 60–90K·16 薪、Apple（Java Spring Boot + Spring AI + 护栏 + MCP）、EPAM / Infotel / Mastercard / BOFA / JPMC、医疗 EMPI「API 化、MCP 化封装」新品类。

---

## 5. 💵 接单与定价（11 月三源复核）

| 项 | 数值 | 来源 |
| --- | --- | --- |
| Upwork 卖方服务费 | **0–15% / 合同**（提交 proposal 时显示、合同期内锁定）；旧「20/10/5%」明确过时 | [llms.blog](https://www.llms.blog/posts/ai-freelancing-find-first-clients) / [earninger](https://earninger.com/store/upwork-freelance-marketplace) / [richtactic](https://richtactic.com/blog/fiverr-alternatives) |
| Connects / 会员 | $0.15/个；Basic 免费（可能含 10/月）；**Plus $19.99/月含 100 Connects**；Direct Contracts 费率 Plus **0%** / Basic **5%** | 同上 |
| 客户侧 | Basic 3–5%、Business Plus 8–10%、另有合同启动费 | earninger |
| Fiverr / Contra | Fiverr 卖方得 **80%**（买家另付 5.5% + <$200 加 $3.50）；Contra 0% 佣金 / Pro $29 月 或 $199 年 | llms.blog |
| 时薪带 | AI/ML 工程 **$120–250/hr**、云架构 $65–130、安全 $40–90、高级全栈 $70–140；美国自由职业者均 $47.71/hr、软件开发 $52–60/hr | [2026 freelance rates](https://www.pdf-book-search.com/go.php?id=107220)（引 Upwork 2025） |
| 结构性信号 | Upwork Q2 2026 活跃客户 **763,000（同比 −4%）**，CEO 8 月称「lower-complexity work continues to shift toward automation」；**AI 增强交付 +30–60% 溢价**，高判断力专业化工作吃走增长 | richtactic / llms.blog |
| 官方 MCP 接单基础设施 | [Upwork MCP Server](https://www.upwork.com/ai/mcp)：OAuth 2.1、Client/Freelancer/Agency 三角色、**draft-and-confirm**（proposal / invitation / milestone / message 均为草稿，人工确认才生效；offer 与付款必须回 upwork.com 完成） | 官方页 |

> 报价锚沿用 [`mcp-governance-pricing-anchor-2026-11-09.md`](mcp-governance-pricing-anchor-2026-11-09.md)：同范围 7 档 **$3,700–$72,000**（19 倍价差）、含 audit/SSO/RBAC/versioning 的生产级 **$80K–$220K**、企业网关 **$250K 起**；⚠️ 全部为卖方销售页公开价，**非成交价**。

---

## 6. 🧩 用户的 Java + Spring + AI 组合在赛道的卖点（V1.69 版）

| # | 卖点 | 本期新增证据 |
| --- | --- | --- |
| 1 | **唯一 Java/Spring 开源企业级 MCP 治理栈** | 网关横评（Docker/IBM/Kong/Portkey/Azure/Cloudflare）清一色非 Java 或治理锁 Enterprise 付费墙 |
| 2 | **21 模块直接对位「MCP runtime 七件套」** | [Arcade.dev《Build or buy an MCP runtime》](https://new.benhalpern.com/arcade/should-you-build-or-buy-an-mcp-runtime-for-enterprise-ai-agents-in-2026-36jg)：OAuth lifecycle / credential vaulting / multi-user auth / **permission intersection** / audit pipeline / policy enforcement / observability；四个 tipping point（三集成阈值、user-delegated actions、异步长时读写、OTel 审计日志）；「The real choice in 2026 is **risk, not cost**」「buy 是多用户生产默认」 |
| 3 | **注册期注解完整性 + 元数据漂移复验**（今晚新增） | INITE 11-11 普查 + MS 1.25.0 catalogue refresh + OpenAI/Anthropic 上架规则 → 可现场跑 `McpToolAnnotationGate` |
| 4 | **传输层四件套可演示**（今晚新增） | Siemens 双 CVE + Dynatrace 123811 + server-everything → `McpLocalListenerGuard` 17 攻击用例 |
| 5 | **存量 Java 权限 / 审计直接复用** | UiPath×Google Cloud「复用已有 governance/permissions/audit controls」；Arcade「config hell」正是自研 runtime 的隐形成本 |
| 6 | **私有化 + 等保/密评交付能力** | 「不能上云的钱最好赚」；[The Agents Index 11-11 采购条目](https://theagentsindex.com/blog/sso-census) 已把 MCP server / SOC 2 / HIPAA / PCI DSS v4.0 / SSO / RBAC / data residency / Zero Data Retention 并列为筛选维度，且大量条目「Checked 11 November 2026」 |
| 7 | **HITL 是责任分界与监管硬约束** | 中信银行 11-11「生成式内容需人工审核或预分析后调用」；T-Investments 责任转移；Upwork draft-and-confirm；Dynatrace 同体对照 |
| 8 | **可进标书的可复现验收** | `enterprise-rfp-checklist.md` **24 类攻击面**逐条给验收方式 + 7 组纯 JDK 示例现场编译自检 |

---

## 7. 来源清单（本期 16 条主要来源）

1. INITE Atlas《The state of vendors' MCP servers》11-11 — https://inite.ai/en/atlas/mcp/report
2. CPI Consulting《Microsoft Agent Framework MCP Approval Policies Governance Guide》11-11 — https://www.cloudproinc.com.au/index.php/2026/11/11/microsoft-agent-framework-mcp-approval-policies-governance-guide
3. Google Cloud《管理 MCP 服务器和工具》更新 11-10 — https://docs.cloud.google.cn/agent-registry/manage-mcp-tools?hl=bg
4. Severity Daily / Siemens SSA-710379 11-10 — https://severitydaily.com/siemens-mendix-studio-pro-mcp-server-cve-2026-112136-8-8-cvss-3-1-ui-r-vs-9-2-cvss-4-0-ui-n-at-p-cwe-1327-loopback-port-7782-ssa-710379-v11-16-0-v11-12-7-cve-2026-112137-csrf-origin-content-type
5. EyeOn AI / Mendix 双 CVE — https://eyeon.ai/f/3664
6. AI Threat Alert / CVE-2026-123811 — https://aithreatalert.com/cve/CVE-2026-123811
7. Adversa《Top MCP security resources — November 2026》 — https://adversa.ai/blog/top-mcp-security-resources-november-2026
8. Dawloom《Submit an MCP server to the ChatGPT and Claude directories》 — https://dawloom.com/blog/submit-mcp-server-to-chatgpt-and-claude
9. PowerDuck《Marking MCP Tools Read-Only or Destructive》 — https://www.powerduck.com/blog/mcp-tool-annotations-readonly-destructive
10. CSDN DevPress《一文搞懂 MCP 开发》信任模型 — https://devpress.csdn.net/aibjcy/6af303c1e5f4ca4eff8fa677.html
11. HOL registry / mcp-gatehouse v0.5.0（11-07） — http://hol.org/registry/plugins/nickgeorgeseo%2Fmcp-gatehouse
12. Harriet《What is an MCP gateway》11-09 — https://harriethq.com/blog/what-is-an-mcp-gateway
13. Arcade.dev / dev.to《Build or buy an MCP runtime》 — https://new.benhalpern.com/arcade/should-you-build-or-buy-an-mcp-runtime-for-enterprise-ai-agents-in-2026-36jg
14. 同花顺《中信银行重构手机银行服务逻辑》**11-11** — https://m.10jqka.com.cn/20261111/c681716629.shtml
15. 同花顺《AI 重塑证券业》11-09 — https://stock.10jqka.com.cn/20261109/c681591251.shtml
16. Ofweek / 零壹智库 10 月金融智能体招标 11-10 — https://cloud.ofweek.com/news/2026-11/ART-178805-8420-30711294.html
17. 上海电机学院 SDJU-2026224 快速采购 **11-11** — https://wmzx.sdju.edu.cn/2026/1111/c2200a157466/page.htm
18. Upwork 官方 MCP Server — https://www.upwork.com/ai/mcp
19. The Agents Index《AI Agent Tools With Documented Enterprise Security Controls》Checked 11-11 — https://theagentsindex.com/blog/sso-census
20. 费率三源 — https://www.llms.blog/posts/ai-freelancing-find-first-clients · https://earninger.com/store/upwork-freelance-marketplace · https://richtactic.com/blog/fiverr-alternatives

---

## 8. ⚠️ 诚实边界（延续口径）

- **INITE 数据是厂商 server 的公开普查**，不是 CVE 统计；「谎报」为描述与 hint 的自动比对，**不代表已被利用**；52% 免登录包含刻意公开的数据服务，不等于越权。
- **Siemens 双 CVE 影响版本范围较窄且目前 `Exploitation: none`**；本框架**不受该 CVE 影响**，只是把它抽象为通用准入模式。
- **MCP v2 规范正式定稿（2026-07-28）/ 捐赠 Linux Foundation AAIF（2025-12-09）等时间线来自二手站点**（[kwkr.cn](https://www.kwkr.cn/news/48305.html)、[sourceit](https://sourceit.com.sg/blogs/news/what-is-mcp-model-context-protocol-enterprise-tools)），**待官方仓库复核，不作为本期结论依据**；本期不宣称已完成 Spring AI 2.0 升级。
- 广发三份、银行平台均为**预征集 / POC**，非采购邀请；本项目**无商业合同、无中标、无金融案例、无等保测评 / 算法备案**；技术映射不得表述为「已通过合规测评」。
- 所有公司报价为**卖方销售页公开价**，非成交价；Upwork 费率以本期三源为准，V1.63 旧口径弃用。
- 今日新搜到的国内公告（上海电机学院 / 太原理工 / 国家管网）**均未点名 MCP**，未计入可投清单。
- `examples/` 下两个新示例为**参考实现**，未接入主模块、未进 reactor；`mcp-monitor` 的 `exportPrometheus()` 挂 actuator、连接路径重定向逐跳重校验与 SNI/Host 一致性仍为**跨期待办**。

---

## 9. 明天（11-12）做什么

1. **⏰ 广发 11-12 17:30 截止（剩 1 天）**：把注册期验收清单 + 云网关对照 + 报价锚 + 4 组可演示示例压成 **1 页 PDF** 附报名材料；核原厂授权 / 案例 / 完税 / 广州本地团队，资质不足立即找广州本地集成商组联合体。
2. 发本期 SEO 稿抢「**MCP 工具注解不是安全边界 / INITE 11393 普查 / Siemens Mendix 双 CVE / Microsoft Agent Framework 1.25.0 / 广发 MCP 征集**」流量。
3. 把「25% 缺 hint / 4% 谎报 / 23% 免登录改数据」补进 `enterprise-rfp-checklist.md` 现状风险章节与售前 Q&A。
4. 山东重工 11-20 + 军队 190 万 11-16 / 中山 399 万 11-17 / 呼市 900 万 11-23 / 中央民大 3712 万 11-25 备标。
5. 跨期待办：`exportPrometheus()` 挂 actuator、连接路径补重定向逐跳重校验与 SNI/Host 一致性、把 `McpToolAnnotationGate` 真正接进 `mcp-registry`、独立分支评估 Spring AI 2.0 升级。
6. 跟踪兴业 / 北京农商 / 上海农商正式招标文件，以及 Google Cloud 是否把「工具描述变更强制重新审批」纳入产品层。
