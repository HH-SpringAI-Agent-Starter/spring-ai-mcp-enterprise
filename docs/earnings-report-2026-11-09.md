# MCP 挣钱情报日报 · 2026-11-09（周一）

> 覆盖窗口 **11-07 ~ 11-09**（最近 3 天，全部为本期实时检索）。
> 一句话结论：**今天出现了本项目自建立以来最硬的一组「治理层 = 定价权」数字** —— 公开报价里「不带治理的 MCP server」$3.7K–15K，「带审计/SSO/RBAC/多租户的生产级 MCP」$80K–220K，「企业网关」$250K 起；同日官方 MCP 参考服务器被一次性披露 7 个 CVE、Anthropic 对 STDIO 架构缺陷明确「Won't Be Patched」，国内银行智能体平台采购集体进入「买底座不买场景」阶段。**治理层不再是合规成本，是把同一份工作量卖出 10 倍价格的那道分界线。**

---

## 一、今日最强信号（按挣钱价值排序）

### 🔥🔥🔥 1. 银行智能体平台采购潮进入「底座期」：招标标的从「一个场景」变成「一套开发与运营底座」

[同花顺财经 11-09](https://m.10jqka.com.cn/20261109/c681593732.shtml) / [银行智能应用动态 11-09](http://mp.weixin.qq.com/s?__biz=MzY4MzQ2MDgxMA==&mid=2247483715&idx=1&sn=a81636ba0d1e9736e600b18ce06906d2) 同步报道：

| 机构 | 采购标的 | 逐字要求（可直接对位本框架模块） |
| --- | --- | --- |
| **兴业银行** | 2026 智能体平台一期软件产品及研发实施（10-21 招标，软件**许可买断**，预算未披露） | 「具有高级认知和作业处理能力的金融智能体平台」+ 主流大模型支持 + **灵活插件集成与调用** + 知识库 + 流程自主规划 + 可视化编排 |
| **北京农商银行** | 企业级智能体平台供应商征集 | 覆盖智能体**开发、纳管、调用、评测、观测、运营全生命周期**；「Skill 和工具广场」要求内置通用 Skill 与工具**不少于 50 个**，支持自然语言自动生成 Skill、本地 Skill 导入 |
| **上海农商银行** | 智能体开发平台 POC 征集 | 插件管理「支持 **Skill、MCP 插件市场**功能」，内置单据 OCR、智能数据查询等金融行业插件；解决**智能体测评、发布、权限管理**问题 |
| **中原银行** | 组合采购（延续 11-02 记录） | 「**大模型安全围栏 + 企业级 AI 网关**」同时采 |
| **浙商银行** | 智能体基础软硬件采购 | **单笔超 6400 万元**，撑起 9 月银行 AI 中标金额近七成 |

配套量化：**2026 年 9 月银行 AI 相关中标项目 40 个、涉及 28 家银行，城商行 24 个占 60%**。

> **判读**：买方开始把「纳管 / 评测 / 观测 / 运营 / 权限管理 / MCP 插件市场」写成**可验收的工程口径**，而不是演示效果。这正是 `mcp-registry`（工具注册与插件市场）+ `mcp-governance`（权限/审批/参数校验）+ `mcp-monitor`（评测观测）+ `mcp-audit`（留痕）四个模块的原生职责。**「Skill 可计价」也意味着「治理注册表可计价」。**

### 🔥🔥🔥 2. 英国五大行集体封禁外部 AI Agent：治理第一次被折算成「存款利润」

The Telegraph 11-08 报道（经 11-09 中文汇总）：**汇丰、Nationwide、NatWest、巴克莱、劳埃德**五家英国最大银行已封禁或限制外部 AI Agent 访问客户账户，部分明确禁止 Agent 在客户未直接参与的情况下代为交易。

- **麦肯锡测算**：若 AI 建议促使储户将 **5%~10%** 的银行余额转入收益更高的储蓄产品，银行存款利润可能**下降至少五分之一**，极端情形下最多 **7000 亿英镑**存款发生迁移。
- 监管侧 **FCA** 正与业界制定金融 AI Agent 规则；美国摩根大通、花旗、美银、富国、嘉信已采取类似限制，**嘉信 CEO 称每周主动拦截 10~20 名客户使用 AI Agent**。
- 劳埃德 CFO 表示**尚未发现** AI Agent 访问本行账户的迹象 → 属**预防性**动作，不是事后补救。
- 国内镜像：豆包 SAEP、工行/招行拒绝 GUI Agent 授权、汇丰 MCP 门禁卡。

> **判读**：此前华尔街限制动作停留在「责任归属」层面，麦肯锡首次把 Agent 风险折算成**存款流失与利润折损**，数字比原则更有推动力。**「外部 Agent 只允许受控只读接入 + 强认证不可绕过」正在成为成熟市场默认基线** —— 逐字命中 `mcp-auth`（Bearer 强制校验 + Scope）+ `mcp-governance`（工具级 ACL / 读写分离 / HITL）+ `mcp-tenant`（按客户经理归属隔离）。国内银行设计开放接口时应直接以「只读/交易分级 + 强认证」为起点，售前可直接引用这段测算。

### 🔥🔥🔥 3. 官方 MCP 参考服务器七 CVE 批量披露（11-08）：「参考实现自己就是反面教材」

[AL-ICE 11-08 完整复盘](https://al-ice.ai/posts/2026/11/mcp-reference-servers-desktop-commander-seven-cve-batch-november-2026)：研究者 ShieldPi 于 **11-08** 一次性披露 **CVE-2026-41901 ~ CVE-2026-41907**，打的是 `modelcontextprotocol/servers` **官方参考实现**（mcp-server-git / sqlite / fetch）与第三方 Desktop Commander：

| CVE | 根因 | 治理层对照 |
| --- | --- | --- |
| `41903` (CWE-78) | mcp-server-git 带仓库自身配置调用 Git，`git_status` 触发 `core.fsmonitor`、`git_checkout` 触发 post-checkout hook → **标注 read-only 的工具在主机上任意代码执行** | 「只读注解」不等于权限边界；工具沙箱必须禁 hook/fsmonitor/pager |
| `41902` (CWE-22) | `validate_opened_repo` 只校验 worktree / git dir / common dir **三条路径**，漏了 `.git/objects`、`objects/info/alternates` 与符号链接 → 跨仓库读取外部 object 库 | 越界检查必须覆盖所有被底层实际读取的路径，且带**对照组断言** |
| `41901` (CWE-22/862) | mcp-server-sqlite `write_query` **只拒绝以 SELECT 开头**的语句，`VACUUM INTO '/tmp/escaped.db'` 自提交 → 任意路径写全库；DROP/CREATE TRIGGER/ALTER/PRAGMA 全过 | **教科书级「前缀检查失败」**：一个禁用关键字 ≠ per-tool statement grammar |
| `41904~41907` | Desktop Commander 默认 blocklist / fetch guard 盲点 | 默认 allowlist 而非 denylist |

> **判读**：这是**官方 worked examples** 连续第二个月教同一课（10 月 `CVE-2026-104120` fetch server、6 月 git server 路径穿越）。且 PyPI `mcp-server-sqlite` **上游已归档**，补丁「必须落在真正 vendored 代码的地方」→ 「升级官方依赖 ≠ 修好」再实锤。本期已据此新增可运行工程示例 `examples/tool-input-grammar/`（per-tool SQL statement grammar，含 `VACUUM INTO` 等 8 个攻击用例 + 对照组）。

### 🔥🔥 4. Anthropic 对 STDIO 架构缺陷明确「Won't Be Patched」：责任被正式下沉到每个集成方

[The Agent Times](https://theagenttimes.com/agents/article/zero-click-rce-in-ai-coding-agents-threatens-our-entire-mcp--13831b18) / [架构缺陷复盘](https://theagenttimes.com/agents/article/architectural-flaw-in-mcp-protocol-threatens-our-entire-agen-74b9ffa3)：

- `CVE-2026-30615`（Windsurf IDE 1.9544.26，CVSS **8.0**）：提示注入静默改写 `mcp.json` → 注册攻击者 STDIO server → **零点击 RCE**；同类 **12+ CVE**（LangFlow / GPT Researcher / LiteLLM `30623` / Agent Zero `30624` / DocsGPT / Flowise / Upsonic / Bisheng / Jaaz）。
- **Anthropic、Google、Microsoft 均拒绝修复**，定性为「spec-conformant / expected / by design」；根因在官方 SDK：`StdioServerParameters` 对 user-controllable `command` / `args` 不做中性化直接 spawn 子进程。影响 **Python / TypeScript / Java / Rust 官方 SDK 累计 1.5 亿+ 下载**。
- **唯一已知缓解**：把 STDIO command 收敛到 allowlist（`npx` / `uvx` / `python` / `python3` / `node` / `docker` / `deno`），**且不是 SDK 默认值，每个下游实现必须自己加**。
- 生态量化：AgentSeal 追踪 **16,000+** MCP server（2025-01 仅 714）；扫描 1,808 个中 **66%** 有安全发现、共 8,282 个问题、**427 critical**；Endor Labs 2,614 个实现中 **82% 路径穿越风险 / 67% 代码注入风险**；PipeLab 50+ 已知 MCP 漏洞、13 critical；30 天新增 30 个 CVE；首个恶意包 `postmark-mcp` 1,643 次下载影响约 300 组织。

> **判读**：话术从「升级官方 SDK ≠ 安全」升级为 **「官方明确不修，规范把责任下沉给每个集成方」**。这一句可以直接写进标书与售前 PPT，是治理增强层存在的最硬合法性依据。

### 🔥🔥 5. 广发证券第三个 MCP 征集浮出，且 **11-12 17:30 截止**（比 11-13 两份更紧）

[广发证券《2026 年态势感知及安全分析平台扩容项目》供应商征集公告](https://www.gf.com.cn/web/article/detail?articleId=01a1aaecb5257570934897d5a51a3212&columnId=4)：扩容奇安信 NGSOC，运营管理层明确包含 **「MCP 安全工具管理」+「安全技能库管理」**，配套 6 个安全智能体（告警研判 / 事件调查 / 响应处置 / 情报分析 / 工作流编排 / 运营助手）与 **AI 安全数据底座**（统一日志模型 / 实体识别与关联 / 高性能存储检索）。资质：成立 3 年+、注册资金 ≥500 万、**原厂或授权代理商**、近三年 ≥2 个金融行业案例 + ≥2 个安全项目案例、纳税信用 + 近两月完税凭证。

至此广发三份征集构成完整矩阵：

| 公告 | 截止 | MCP 内容 | 对应资产 |
| --- | --- | --- | --- |
| 态势感知平台扩容 | **11-12 17:30（剩 3 天）** | MCP 安全工具管理 + 安全技能库 + 安全智能体 | 本期新增银行/安全运营应答包 |
| 13321 智能金融资讯数据服务平台 | 11-13 17:30 | MCP 工具协议 + Skill + 子 Agent + 混合检索 | [`gf-securities-tender-response-2026-11-07.md`](gf-securities-tender-response-2026-11-07.md) |
| 13322 企业微信持续建设 | 11-13 17:30 | MCP 五件套写进资质 + 指定 Dify/hiagent | 同上 + [`enterprise-cloud-gateway-comparison-2026-11-08.md`](enterprise-cloud-gateway-comparison-2026-11-08.md) |

> ⚠️ 三份均为**招标启动前的供应商预征集**（公告明写「非采购邀请」「提交合格资料不必然获得正式邀请」），目标是**进供应商库**；资质硬门槛（原厂/授权、金融案例、完税、广州本地团队）不足时建议组联合体，**绝不可虚构材料**。

### 🔥 6. 国内新标与政务赛道（诚实标注相邻机会）

| 项目 | 金额 / 时间 | 与本框架关系 |
| --- | --- | --- |
| [上海黄浦区城市治理智能体建设项目](https://www.ccgp.gov.cn/cggg/dfgg/gkzb/202611/t20261107_27641623.htm) | **306 万**，11-07 公告，**11-30 开标** | 数据基础 + 知识语料 + 大模型赋能 3 大业务场景；未点名 MCP，属智能体底座相邻机会 |
| [上海市消防救援总队 AI 大模型垂域业务应用子系统](https://www.ccgp.gov.cn/cggg/zygg/gkzb/202611/t20261108_27642490.htm) | **713.75 万**，11-08 公告，**11-30 开标**，15 个月，**不接受联合体** | 含智能体开发 / 接口开发 / **密码应用功能模块（密评）** → 私有化 + 等保 + 密评刚需延续 |
| [2026AI先导-院人工智能底座知识工场升级](https://www.ccgp.gov.cn/cggg/zygg/zbgg/202611/t20261109_27642527.htm) | **89 万中标**（北银金融科技，11-09 公告） | 银行系金融科技子公司中标 AI 底座，验证「银行自建底座」路径 |

> ⚠️ **诚实标注**：以上三标公告均**未出现「MCP」字样**，不计入「MCP 直接可投清单」，仅作「智能体平台 + 私有化 + 密评」采购持续放量的旁证。真正点名 MCP 的仍是广发三标 + 银行智能体平台（上海农商「MCP 插件市场」）。

### 🔥 7. 云治理层再添 Google Cloud；采购指南把「治理」当选型维度

- [Google Cloud Agent Registry / Agent Gateway 文档（最后更新 2026-11-08）](https://docs.cloud.google.cn/agent-registry/manage-mcp-tools?hl=bg)：**中央注册表 + Agent Gateway 强制执行 IAM 策略 + 安全发现结果**；[MCP 服务器文档](https://docs.cloud.google.cn/agent-registry/use-agentregistry-mcp?hl=zh-tw)明确「使用 OAuth 2.0 + IAM 鉴权、**不接受 API Key**、为使用 MCP 工具的 agent 建立**独立身份**」，并提供 `agentregistry.read-write` 专用 scope。→ 继 Azure APIM / AWS AgentCore / 腾讯云 v3.9.8 之后**第四朵云**把 MCP 治理做成标准层，V1.66 定位复核继续成立。
- [DevOpsSchool《Best Vendor for an Enterprise MCP Gateway (Governance) in 2026》](https://www.devopsschool.com/blog/best-vendor-for-an-enterprise-mcp-gateway-governance-in-2026) 评 TrueFoundry / MintMCP / IBM ContextForge / Docker MCP Gateway / Obot 五家；[Strac《Best MCP Gateway in 2026: 8 Options Compared》](https://www.strac.io/blog/best-mcp-gateway) 给出金句：**「如果 enterprise 意味着适配既有采购合同，云巨头赢；如果意味着通过数据保护审查，候选名单短得多」**，并要求「工具响应内联检查 / redact-and-continue / 逐工具治理 / 审计账本 / 完全自托管」五项；[dev.to《Governed Execution Gateway》](https://dev.to/gde/the-governed-execution-gateway-securing-mcp-servers-and-tool-egress-proxies-1ee3) 给出入站参数校验 + 出站 payload 过滤 + 限流断环 + OTel 遥测四条非协商规则。→ 全部非 Java 栈，本框架差异化锚点不变。
- 🇨🇳 中文售前最佳素材：[腾讯云开发者社区《MCP 认证与安全：授权是可选的，责任不是》](https://cloud.tencent.com/developer/article/2779058) —— 原话「**在 MCP 规范里，授权（Authorization）是 OPTIONAL 的。协议不保证你的调用经过认证，它只规定如果你决定要做，必须怎么做**」，并给出三个真实翻车：token 被原样转发下游、discovery 元数据 URL 不校验内网、工具描述改了但审批记录只有最初那一版。[CSDN《MCP 协议安全攻防实战》](https://aisec.blog.csdn.net/article/details/170245168) 补四类风险（工具描述投毒 / 会话劫持 / DNS Rebinding / 供应链投毒）与纵深防御（Origin/Host 校验、安装前只读审计、**工具描述快照比对**）。

### ⚖️ 8. 治理颗粒度下沉到「个人」：北京朝阳法院 AI 办公劳动争议判例（11-09）

[北京青年报 11-09](http://mp.weixin.qq.com/s?__biz=MzY4MzQ2MDgxMA==&mid=2247483715&idx=1&sn=a81636ba0d1e9736e600b18ce06906d2) 报道：员工因使用外部 AI 工具处理公司内部文件被解除劳动合同，起诉公司，法院已审结。报道点出 AI 幻觉、数据泄密、版权侵权三类隐患；法学专家建议企业细化**AI 使用场景清单**、制度公示与合规培训。

> **判读**：此前跟踪的是董事会级办法 / 监管级指引，这起判例把问题落到「**某位员工用了哪个工具、处理了什么文件**」。银行 AI 制度的最后一公里是**员工行为规范而非平台能力** → 「哪些能用外部工具、哪些绝不能出内网」清单 + 生成内容纳入留痕与复核 = `mcp-governance` 工具级准入 + `mcp-audit` 不可篡改留痕。与英国五大行封禁是同一条逻辑的国内版。

---

## 二、谁在招人（Java + Spring + AI + MCP，最近 3 天）

| 企业 / 岗位 | 薪资 / 地点 / 时间 | JD 逐字要点 |
| --- | --- | --- |
| **小米科技 · 高级 AI 系统开发工程师（大模型与 RAG 方向）** | **25000–30000 元**，长春，11-07 14:50 发布 | 「设计并实现 Agent 工作流编排框架，支持**工具调用（MCP 协议）**、任务规划与自动化执行」+ 基于 **Java/Go/Python** 构建高并发 AI 微服务；要求 5 年+ 大型服务端、**Spring / SpringMvc / SpringBoot / Spring Cloud**、MySQL/MongoDB/ES/Redis；加分 vLLM/SGLang/TensorRT-LLM |
| **智慧树（上海卓越睿新，已上市）· 高级 Java 开发** | 上海徐汇，两周内入职 | 「基于 **Spring AI** 框架，构建 **Text-to-SQL、RAG 增强、MCP Server** 等 AI 能力」+ 多数据源接入 / 语义模型 / 知识库；985/211 + 3 年 |
| **成都迈思信息 · java 开发运维（医疗 EMPI）** | 北京 | 🆕 **新品类**：「参与 EMPI 患者身份能力的 **API 化、MCP 化封装**，支撑 AI 智能体与业务系统的标准化调用」+ MDM 主数据治理 + HL7 v2.x / FHIR `$match`；要求「有 **MCP Server/Tool 封装**、AI Agent 系统集成经验」 |
| 猎聘 · JAVA 全栈（PLM，Windchill） | 15–20k，上海长宁 | Spring 全家桶 + AI Coding（Cursor / Claude Code）深度实践 |

> **判读**：本期新增**医疗行业「存量 Java 系统 MCP 化封装」**（EMPI 患者主索引），与 11-03 Digital Dhara「connect various nodes of J2EE」、金山办公「现有平台向 MCP Server 标准化封装」、美团「MCP Server 接入管理与工具沙箱」构成同一条主线：**买方要的不是会写 MCP demo 的人，是能把已有 Java 系统的权限模型、数据源、审计要求一起搬进 MCP 的人。**

---

## 三、什么价（本期最有挣钱价值的一组数字）

### 3.1 公开报价：同一范围最高价是最低价的 **19 倍**

[Dawloom《MCP cost estimator》](https://dawloom.com/blog/mcp-server-cost-estimator)（数据校验 2026-10-13，ScalaCode 档 10-31 复核）对「小型只读连接器（≤10 工具 / 1 系统 / 内部员工 / 单 API Key）」给出：

| 来源 | 档位 | 公开报价 |
| --- | --- | --- |
| ScalaCode | 单系统 MCP server（1 集成 / 5–10 工具 / 基础鉴权） | **$8,000 – $15,000** |
| Make An App Like | Basic MVP（8–12 只读工具 / 单租户 / 1 API Key） | **$8,000 – $15,000** |
| AppMatic Tech | 单数据源集成（1 数据库或 API） | **$8,000+** |
| Clyrix Digital | Basic production（1–2 个 SaaS 系统） | **$12,000 – $35,000** |
| BeevR | 认证只读（3–6 周）@ $31–41/h（东欧） | **$3,700 – $9,800** |
| BeevR | 认证只读（3–6 周）@ $100–300/h（欧美） | **$12,000 – $72,000** |
| mcp-agency.com | MCP Foundation（单域 / 单环境） | **€5,000** |

工作量口径：43.9–82.8 person-days（约 9–17 周 / 1 名工程师），按 US/UK/西欧 $80–200/h 折算 **$28,080 – $132,400**。⚠️ 该工具自述「**没有独立的 MCP 造价调研，绝大多数发布者自己就卖 MCP 搭建服务，数字应视为销售页**」，且明确规则：**一旦 server 能写数据或面向客户，就必须追加 audit logging 工作包**。

### 3.2 自建总成本与「治理层 10 倍定律」

[AI Agents Plus《What a Custom MCP Server Costs to Build and Run》](https://www.ai-agentsplus.com/blog/what-a-custom-mcp-server-costs-to-build-and-run)：

| 范围 | 报价 | 关键差异 |
| --- | --- | --- |
| 简单单工具 server | $3,000 – $8,000 | 无鉴权 |
| 加 auth 与权限 | $8,000 – $25,000 | 基础鉴权 |
| 单系统专用（1–4 周） | **$15,000 – $45,000** | 连接 1 个内部系统 |
| 多系统 + 写权限 | $35,000 – $90,000 | 多系统集成 |
| **生产级内部产品（8–16 周，2 工程师 + 兼职平台 lead）** | **$80,000 – $220,000** | **含 audit logging / SSO / RBAC / versioning** |
| **企业网关（16–32 周）** | **$250,000 起** | 集中 auth / 策略执行 / 跨团队可观测 |
| 自建总成本带 | **$15,000 – $220,000 + 每年 10–30% 运行成本** | — |
| Reflekt Lab | scoping **$2,300–4,700**；首 workflow **$17,500 起** | — |
| Truto 全负载 | **$50,000 – $150,000 / 集成 / 年** | 含开发/QA/监控/支持 |

> **★ 10 倍定律（本期核心结论）**：从「单系统 $15K–45K」到「生产级 $80K–220K」，代码量没有增加 10 倍，**增加的是 audit / SSO / RBAC / versioning —— 也就是治理层**。企业网关 $250K 起，几乎全是治理与可观测。**本框架 21 个模块正好完整覆盖这条溢价带**：`mcp-auth`（SSO/Bearer）+ `mcp-governance`（RBAC/Scope/HITL/参数校验）+ `mcp-audit`（不可篡改留痕）+ `mcp-tenant`（多租户）+ `mcp-monitor`（可观测）+ `mcp-registry`（versioning）。**售前不要报「做一个 MCP server」的价，要报「交付一套通过数据保护审查的治理底座」的价。**

### 3.3 时薪与订阅制

- [Upwork 官方《Hourly Rates 2026》](https://www.upwork.com/resources/upwork-hourly-rates)：AI Developer **$30–50/h**、AI Engineer **$35–60/h**、ML Engineer **$50–200/h**、Deep Learning **$30–120/h**；「高级开发 / AI / 战略咨询」**$75–150+/h**、入门 $10–25/h、中级 $25–75/h。平台费率 0–15%（口径延续 V1.64 修正）。
- [dev.to《Pricing an MCP Server in 2026》](https://dev.to/whoffagents/pricing-an-mcp-server-in-2026-why-we-charge-19mo-when-the-market-average-is-0-nig)：Claude Code marketplace 约 **318 个 MCP server，绝大多数 $0**；少数付费 **$19/月（个人）~ $149/月（企业）**，且**能收费的清一色是 B2B 安全扫描（$99–149/月）、监控（$49+）、数据基础设施（$79+）**。
  > **判读**：**裸 MCP server 的市场均价是 $0；唯一能收费的层就是安全 / 治理 / 可观测。** 与本框架定位完全同构，可作为「为什么不做第 319 个 wrapper」的开场。

完整报价锚表、售前话术与投标引用格式见 [`mcp-governance-pricing-anchor-2026-11-09.md`](mcp-governance-pricing-anchor-2026-11-09.md)。

---

## 四、Java + Spring + AI 组合在该赛道的卖点（本期更新 7 条）

1. **银行「底座期」原生对位**：兴业 / 北京农商 / 上海农商要的「纳管·评测·观测·运营全生命周期 + MCP 插件市场 + 权限管理」，就是 `mcp-registry` + `mcp-governance` + `mcp-monitor` + `mcp-audit` 的模块清单，无需新建概念。
2. **存款利润防线的合规答案**：英国五大行封禁 + 麦肯锡 7000 亿英镑测算 → 「外部 Agent 只受控只读 + 强认证不可绕过」= `mcp-auth` Bearer/Scope + `mcp-governance` 工具级 ACL + `mcp-tenant` 按客户经理隔离，逐字可写进应答。
3. **官方不修的那一层，只有治理层能补**：Anthropic 对 STDIO 缺陷「Won't Be Patched」、官方 SDK 1.5 亿下载、唯一缓解是 STDIO command allowlist 且非默认 → 本框架给出 `examples/connect-path-guard/`（argv 启动 / 版本下限 / 连接路径准入）+ 本期新增 `McpResolvedIpGuard`（解析后真实 IP 校验）。
4. **官方参考实现的反面教材可直接演示**：七 CVE 里的「只读工具执行 hook」「SELECT 前缀检查被 VACUUM INTO 击穿」→ 本期新增 `examples/tool-input-grammar/` 用 per-tool statement grammar 与对照组断言给出正确写法，**可演示案例**可进 11-12 / 11-13 报名材料。
5. **治理层 = 10 倍定价权**：$15K–45K → $80K–220K 的分界线是 audit/SSO/RBAC/versioning，正是本框架的现成模块；裸 server 均价 $0，能收费的只有安全/治理/可观测。
6. **存量 Java 资产 MCP 化是最大存量市场**：医疗 EMPI「API 化、MCP 化封装」+ 智慧树 Spring AI + 金山办公 + 美团 + J2EE 现代化 → Java/Spring 是买方既有栈，不需要换语言、不引入新运行时。
7. **云网关不做、也不能做的部分**：Google Cloud / Azure / AWS / 腾讯云都把注册与鉴权做成标准层，但**跨运行时签字、私有化合规工件（ROPA / 等保 / 密评）、存量 Java 权限复用、连接路径准入**仍在空白；Strac 金句「通过数据保护审查的候选名单短得多」是本框架的目标位置。

---

## 五、诚实标注（延续口径，避免过度承诺）

- 本期国内新标（黄浦 306 万 / 上海消防 713.75 万 / 知识工场 89 万）**均未点名 MCP**，不计入「MCP 直接可投清单」。
- 广发三份、北京农商、上海农商均为**供应商预征集 / POC 征集**，**不等于采购邀请或资格预审**；资质门槛（原厂或授权代理、注册资金、金融案例、完税、广州本地团队）不满足时应组联合体，禁止虚构材料。
- 所有造价数字来自**卖方发布的销售页或行业估算**，Dawloom 自述「无独立造价调研」；报价离散度（19 倍）本身说明**这些不是市场成交价**，仅作锚定与议价参照。
- 「Anthropic 拒修」「200,000 servers at risk」等表述来自 OX Security 研究与二手媒体报道，部分数字（如 200,000）**未经独立核实**，本文已优先采用可交叉验证的 AgentSeal 16,000+ / Endor Labs 2,614 等口径。
- 新增 `examples/` 均为**纯 JDK 参考实现，不进 Maven reactor**，需接入 `mcp-gateway` / `mcp-monitor` 主模块才能生产可用；本期未改动任何主代码。
- 「授权是 OPTIONAL」为 MCP 授权规范事实，但**具体到 2026-07-28 版规范的条款号须以官方原文为准**，中文社区文章可能存在版本口径差异。

---

## 六、来源索引

1. 银行智能体平台采购潮：https://m.10jqka.com.cn/20261109/c681593732.shtml ｜ http://mp.weixin.qq.com/s?__biz=MzY4MzQ2MDgxMA==&mid=2247483715&idx=1&sn=a81636ba0d1e9736e600b18ce06906d2
2. 广发证券三份征集：http://gfjc.gf.com.cn/gyszjgg/13321.jhtml ｜ http://gfjc.gf.com.cn/gyszjgg/13322.jhtml ｜ https://www.gf.com.cn/web/article/detail?articleId=01a1aaecb5257570934897d5a51a3212&columnId=4
3. 政府采购公告：https://www.ccgp.gov.cn/cggg/dfgg/gkzb/202611/t20261107_27641623.htm ｜ https://www.ccgp.gov.cn/cggg/zygg/gkzb/202611/t20261108_27642490.htm ｜ https://www.ccgp.gov.cn/cggg/zygg/zbgg/202611/t20261109_27642527.htm
4. 官方参考服务器七 CVE：https://al-ice.ai/posts/2026/11/mcp-reference-servers-desktop-commander-seven-cve-batch-november-2026
5. STDIO 架构缺陷 / Won't Be Patched：https://theagenttimes.com/agents/article/zero-click-rce-in-ai-coding-agents-threatens-our-entire-mcp--13831b18 ｜ https://theagenttimes.com/agents/article/anthropic-refuses-to-patch-mcp-flaw-exposing-thousands-of-ag-4e41ccfc ｜ https://theagenttimes.com/agents/article/architectural-flaw-in-mcp-protocol-threatens-our-entire-agen-74b9ffa3
6. 定价：https://dawloom.com/blog/mcp-server-cost-estimator ｜ https://www.ai-agentsplus.com/blog/what-a-custom-mcp-server-costs-to-build-and-run ｜ https://www.upwork.com/resources/upwork-hourly-rates ｜ https://dev.to/whoffagents/pricing-an-mcp-server-in-2026-why-we-charge-19mo-when-the-market-average-is-0-nig
7. 招聘：https://www.cccyrc.com/job434728460.shtml ｜ https://www.zhipin.com/job_detail/61d83aeb91e4963a0ndy09W1GVRQ.html ｜ https://www.zhaopin.com/jobdetail/CC153170410J41045318102.htm
8. 云治理与选型：https://docs.cloud.google.cn/agent-registry/manage-mcp-tools ｜ https://www.devopsschool.com/blog/best-vendor-for-an-enterprise-mcp-gateway-governance-in-2026 ｜ https://www.strac.io/blog/best-mcp-gateway ｜ https://cloud.tencent.com/developer/article/2779058 ｜ https://aisec.blog.csdn.net/article/details/170245168

---

_本报告为 `spring-ai-mcp-enterprise` 每日开发任务 V1.67 的挣钱情报部分（Task 6）。情报仅作商业机会研判，投标前须核验官方原文与自身资质。_
