# 收益 / 招标 / 招聘 / 安全情报日报 — 2026-10-19（周一 · V1.47）

> 数据窗口：**2026-10-17 ~ 2026-10-19（最近 3 天，叠加本旬累计）**。来源：WebSearch 实时检索（Upwork Hire 页、VulnCheck/AL-ICE、中招联合/365trade、中原银行/ICT标局、91招标网、上海海关、浙江财经大学、阿里云 HiringCafe、美团/智联/猎聘、夸克招聘、EPAM/Glassdoor、youcanbuildthings、CSDN、bovo-digital）。
> 一句话：**本周最强叙事 = Upwork 官方把"MCP server 开发"做成了明码标价的独立收费品类**（单连接器 $1.5K–5K、企业网关 $15K–40K、定制平台 $30K–80K），等于市场给"MCP 开发"发了职业认证；同时**银行级「企业级 AI 网关 + MCP 代理」标已开标**（中原银行中标候选）、**官方 MCP Kotlin SDK 又被曝未授权 DoS（CVE-2026-108714，无修复）**——"升级官方 SDK≠安全"再获实锤。ZC26G280436（**10-20 09:00 截，明天最后 1 天**）仍是本周最硬对标标的。

---

## 一、🔥 最强信号：Upwork 官方把"MCP server 开发"做成明码标价的收费品类

**Upwork 已上线官方 [`Hire MCP server developers`](https://www.upwork.com/hire/mcp-server-developers) 指南与标准化定价（2026-10 实测）**——这是 MCP 开发从"小众技能"变成"独立收费品类"的标志性事件。官方给出的交付分层与价格带：

| 交付类型 | 价格带（官方） | 对应能力 | 与本框架关联 |
| --- | --- | --- | --- |
| 单连接器 MCP Server | **$1,500 – $5,000 / 项目** | 1 个数据源/API 暴露、基础工具 Schema、文档+测试 | `mcp-tools` + 示例 Client SDK |
| 多工具 Server（带 OAuth + 逐用户 scoping） | **$5,000 – $15,000 / 项目** | 3–5 工具、auth、审计日志 | `mcp-auth` + `mcp-governance` + `mcp-audit` |
| 企业级 MCP 网关 | **$15,000 – $40,000 / 项目** | 多系统统一、集中鉴权、监控、限流 | `mcp-gateway` + `mcp-registry` + `mcp-monitor` |
| 定制生产级 MCP 平台 | **$30,000 – $80,000 / 项目** | 专有业务逻辑、生产安全、部署交接 | 全栈 21 模块组合 |
| 持续运维/支持 | **$2,000 – $7,000 / 项目** | 规格/SK 更新、新连接器、可靠性修复 | 治理+监控长尾 |
| 时薪 | **$30 – $150 / 小时** | — | — |

**对本框架意味着什么（直接可引用进接单 pitch / SEO）：**
- Upwork 官方背书 = MCP 开发已成为企业采购目录里的标准条目，**买方不再需要被教育"MCP 是什么"**，只会比"谁更稳、谁带治理"。
- 价格带里 **$5K 以上的档位几乎都强制要求 auth + scoping + 审计**——这正是本框架 `mcp-governance`+`mcp-auth`+`mcp-audit` 的差异化卖点，也是纯"注册+路由"方案（Nacos/Higress/官方 SDK）天然缺的一层。
- 接单者用本框架作交付底座，比从零搭一个裸 MCP Server 更容易跨过 $5K→$15K 的门槛（直接带 RBAC/审计/HITL）。

---

## 二、招聘：Java + MCP / AI Agent 持续井喷（最近 3 天新出 / 仍在招）

| 公司 / 来源 | 岗位 | 地点 | 薪资 / 要求 | 与本框架关联 |
| --- | --- | --- | --- | --- |
| **阿里云智能 · Agentic Tools 开发专家（SDK 团队）** | Agentic Tools SDK | 北京/杭州 | 团队已上线 `api.aliyun.com/mcp`（**OpenAPI MCP Server，一键接入阿里云 2W+ API，Core 模式**），自研 CloudSpec IDL；要 **Java/Golang/Python + MCP + IaC + 分布式 AI 基础设施** | 直接命中 `mcp-registry`+`mcp-alibaba`；MCP 成官方 API 分发基础设施最强信号 |
| **美团 · AI Agent 开发工程师**（智联） | AI Agent 系统架构 | 北京 | 职责含 **工具调用（Tool Use / MCP）、多 Agent 协同**、记忆管理、Planner/Executor/Evaluator；要 Java/Python/Go 至少一门 + 生产级 AI 系统落地 | `mcp-spring-ai-tools`+`mcp-governance`（多 Agent 编排） |
| **美团 · 算法工程方向（CatPaw Managed Agent）** | Java 研发 | 北京 | 骑行智能策略平台 + **AI Agent 探索落地**；截止 **10-26**；要 Java + 微服务 + AI First | CatPaw 已点名 MCP Server 接入/沙箱权限管控，最对口 |
| **阿里 · 千问事业部 助手 Agent 交互技术专家** | 后端核心模块 | 杭州 | 精通 **Java/Golang**，Agent/工具调用/RAG/记忆上下文，5 年，AI 硬件开放体系 | 全栈对齐 |
| **Exerizon（Warsaw）· Mid-level Java Engineer（AI Agents, MCP）** | MCP Server 构建 | 全球远程（B2B） | 为**全球保险公司**从零建高可用 MCP Server（Java 17+/Spring Boot/Spring AI MCP，JSON-RPC 2.0+SSE），时薪制 | 全栈对齐 `mcp-server` |
| **EPAM · Senior/Lead Java Engineer – AI Native** | 构建并部署 MCP Server | 印度 Coimbatore | Spring Boot 把 Java 服务/库暴露给 LLM Agent；Lead 岗 JD 原话要"**security controls, versioning and observability**"的 MCP server 生态（生产级） | 字面命中 `mcp-governance`+`mcp-monitor` |
| **TEKsystems（Toronto）· Senior AI $208–249K CAD/年** | 建 MCP servers | 加拿大 | 暴露 PHSD 医疗数据（FHIR/R4/SMART on FHIR），Java Spring Boot + Python + Bedrock + OAuth2/OIDC/RBAC | `mcp-auth`+`mcp-governance` 英文同义词 |
| **NVIDIA · Senior Staff SWE – AI Agent Platform** | $200K–391K | 美国 Santa Clara | 申请至 **10-20**；点名 **MCP/A2A** + 多租户 K8s + OAuth2/JWT/Vault + 审批工作流 | `mcp-tenant`+`mcp-auth`+`mcp-governance` 最高薪对口 |
| **香港大学 IT Services（CPJobs）· Senior Developer** | Spring+Java+MCP | 香港 | "integrations leveraging the Model Context Protocol (MCP)" + Spring Boot，11-01 截止 | 高校/政务级 Spring+Java+MCP 实锤岗 |
| **歌尔股份 / 西南财大校招 / 中国地质大学就业网** | Agent/AI 应用 | 东莞/杭州/广州 | Spring Boot/Cloud + **MCP 协议** + RAG/Multi-Agent | 清一色 Java+MCP 蓝海岗 |

**Java+AI 复合岗市场底色（延续）：** 3–5 年 **45K–75K/月**（一线）、二线 25–35K·16薪；海外 MCP Expert（Java）**€600–900/天**；供需比仍偏紧，复合人才溢价高。

---

## 三、招标 / 采购：银行级「AI 网关 + MCP 代理」已开标，政务教育渗透加深

**活跃 / 新发（最近 3 天）：**
- 🔥🔥 **ZC26G280436「AI 网关、MCP 注册、多模态平台」**（航空工业西安飞行自动控制研究所）：**投标 10-20 09:00 截（明天！本周最硬 Deadline）**，国产平台 + MCP 组件 + 2 台智算，3 个月到货，不接受联合体 → 最硬对标 `mcp-registry`+`mcp-gateway`+信创。投标响应包索引 → [`docs/zc26g280436-bid-package-2026-10-17.md`](docs/zc26g280436-bid-package-2026-10-17.md)。
- 🔥 **中原银行「企业级 AI 网关项目」中标候选人公示（10-19）**：建设范围明确含 **AI 网关、AI Agent 代理、MCP 代理**、大模型代理、管理平台；服务期 14 个月（2 月建设 + 12 月维保），第二中标候选杭州谐云 76.8 万、第三北京华毅 85 万 → **银行赛道把「AI 网关 + MCP 代理」写进正式标书**，对齐 `mcp-gateway`+`mcp-registry`，金融 MCP+治理需求实锤。
- **上海海关·百炼大模型管理平台升级及运维 107.4 万中标（10-10）**：含本地部署 **AI 网关 + 智能数据总线 + 统一可观测监控**等六类组件、1 人/年驻场；海关总署级大模型平台本地化 → 政务 AI 平台持续点名"AI 网关"，对齐 `mcp-gateway`。
- **浙江财经大学·彩云云库软件服务 MCP/CLI 接口成交（10-19）**：标的含"AI 引擎模块、**MCP/CLI 接口**"，教育行业 MCP 渗透 → 对齐 `mcp-registry`+`mcp-tools`。
- **长庆油田 130 万（专题2·MCP 服务）、襄阳 45.43 万政务大模型 10-16 已截**，结果待公示；宁夏 287.2 万已中标；湖北疾控 150 万（等保三级）11 月采购；中国金融电子化集团智算服务器 11-10 截；中科院高能所 Token 订阅 199 万 11-11 截；南方电网/中电科二十四所 11 月截。

**叙事升级：** 本月 MCP 招标已从"能源/军工"扩散到 **银行（中原/金融电子化）、海关、教育（浙财）、卫健（湖北疾控/宁夏）**——"指名 MCP"成为跨行业采购常态，治理层（等保/审计/多租户）是每类标的的共性加分项。

---

## 四、安全 / CVE：官方 SDK 再曝未授权 DoS，护城河再夯实（最近 3 天）

| CVE / 事件 | CVSS | 日期 | 本质 | 本框架对应控制 |
| --- | --- | --- | --- | --- |
| **CVE-2026-108714**（官方 MCP **Kotlin** SDK 内存耗尽 DoS） | **7.5** | 10-11（HaiND 批次） | `Application.mcpWebSocket` 未设 `maxFrameSize` 上限，声明近 2GiB 载荷的帧头即可强制巨大堆分配 → **未授权远程 DoS**；0.15.0 仍为最新、无修复命名 | `mcp-core` 治理层 RateLimit + 资源隔离 + 请求体/帧上限 |
| **CVE-2026-108542**（elvix-sdk MCP SSRF） | 6.3 | 10-11 | MCP 请求处理器的 path 参数 → 服务端请求伪造，公开利用、厂商未响应 | `mcp-governance` SSRF 防护 + 输入校验 |
| **CVE-2026-108753**（Agnai 硬编码凭据） | **9.4** | 10-11 | `docker-compose.yml` 固定 admin 密码 + 公开 JWT 密钥 → 未授权登录/伪造 admin JWT | `mcp-auth` 密钥外部化 + 不写死凭据 |
| **CVE-2026-108759**（mistral.rs 沙箱逃逸） | 6.8 | 10-11 | 跟随符号链接读写沙箱外文件；记录明确把"prompt-injected agents"列为攻击者 | `mcp-governance` Fail-closed + 工具级 ACL |
| **CVE-2026-108760 / 108714 同源**（22 个 agent/MCP CVE 单日） | 多 | 10-11 | 单一研究者经 VulnCheck 单日披露 **68 个 AI Agent/MCP CVE**，其中 agent 相关 22 个、4 个 ≥7.0；四类结构性缺陷重复：scope 不真 scope、基础设施接受未授权输入、沙箱校验名字而非文件、自托管默认带密钥 | 全栈治理模块组合对症 |

**叙事升级：** 本月已从"认证≠授权"单点，升级为"**连官方 SDK 自己都缺治理**"（Java mcp-core CVE-2026-108395 7.5 DoS + 今次 Kotlin SDK CVE-2026-108714 7.5 DoS）——"升级官方 SDK≠安全"成为 10 月最不可反驳的卖点，本框架"治理增强层"定位再获实锤。

---

## 五、接单 / 定价梯度（Upwork 官方 + 自由职业，2026-10 最新）

- **🔥 Upwork 官方标准化定价（新，最权威锚点）**：见第一节表格——单连接器 $1.5K–5K、多工具+auth $5K–15K、企业网关 $15K–40K、定制平台 $30K–80K、运维 $2K–7K、时薪 $30–150。
- **自由职业实战锚点（延续）**：youcanbuildthings 三档 **$1K（单连接器）/ $3–5K（多工具）/ $5–10K（全管道）**，真实案例 $3,500/14h = **$250/hr**；CSDN《AI 副业全景图》列 **MCP 生态开发 单个项目 5,000–8 万**（"2026 最蓝海赛道"）；AI Agent 企业方案 **3–15 万 + 月维护 5,000–1 万**；bovo-digital **MCP 基础设施岗 €800–1,000/天**（最高溢价）。
- **Upwork 费率**：服务费按合同 **0%–15%**（2026 有效），Connects **$0.15/个**；官方 `mcp.upwork.com` 已成 agentic hiring 获客前端（AI 按结构化 profile 字段初筛）——**本框架仓库 = 可直接放进 Upwork/GitHub 作品集的"企业级 MCP 治理"证据**。

**给接单者的结论：** 价格带里 $5K+ 档几乎强制要求 auth+scoping+审计，本框架作交付底座可平滑跨过从"裸 Server"到"治理增强 Server"的溢价门槛。

---

## 六、Java + Spring + AI 在本赛道的卖点（可直接进投标/接单 pitch）

1. **赛道卡位精准**：MCP 已成事实标准（月 SDK 下载 9700 万、10,000+ 生产 Server、500 强 28% 生产运行），但 **Upwork 官方定价页 + 68 个 HaiND CVE + 官方 Java/Kotlin SDK 双 DoS** 共同证明"带治理的交付"才是高单价档的硬门槛——而治理层恰好是 Java/Spring 企业级最擅长的。
2. **技术栈即信任背书**：Spring Boot / Spring AI / Spring AI Alibaba（Nacos MCP Registry + Higress 网关）是央国企/银行信创首选栈，本框架是"官方方案的治理增强层"，不重复造注册/路由，只补安全合规（RBAC/审计/HITL/Scope/多租户）。
3. **可交付资产现成**：21 个模块 + 等保三级对照表 + 监管合规映射（CIS 55 点 / Five Eyes / NIST / EU AI Act / MAS / 韩国）+ 安全事件简报 + ZC 投标响应包——**拿来即能进标书/进 Upwork 提案**。
4. **招聘市场验证**：阿里云（api.aliyun.com/mcp 团队）、美团（CatPaw/MCP）、千问、NVIDIA（$200–391K）、香港大学、Exerizon、EPAM **清一色点名 Java+MCP/Spring AI**，供需比仍偏紧。
5. **接单门槛低、单价高**：Upwork 官方已把 MCP 开发明码标价，资深 $30–150/hr、企业网关 $15K–40K、定制平台 $30K–80K；一个"企业级 MCP 治理增强"作品集即可打开双通道。

---

## 七、今天做了什么 / 明天做什么

**今天（V1.47）产出：**
- 本收益情报日报（全源实时，含 **Upwork 官方 MCP 定价页** / 中原银行 AI 网关+MCP 代理开标 / 浙财 MCP 接口成交 / HaiND 22 个 CVE 含官方 Kotlin SDK DoS / 阿里云·美团·千问·NVIDIA·EPAM JD）。
- 配套 SEO 稿 `docs/blog-java-mcp-upwork-official-pricing-2026-10-19.md`（掘金/CSDN：「MCP 开发明码标价」× 银行 AI 网关标 × 治理增强层 × Java 蓝海）。
- README 招标/招聘/治理区块更新（Upwork 官方定价 + 中原银行/上海海关/浙财 + HaiND CVE + ZC 倒计时改"明天最后 1 天"）+ 版本表 V1.47 行。
- 无破坏性代码变更，21 模块构建不受影响。

**明天（10-20，ZC 截标日）候选：**
1. **ZC26G280436 截标日收尾**：若已购标书，用投标响应包索引 + 等保三级对照 + 监管合规映射 + 安全事件简报 + SAS 背书段打包完整投标响应（加西安本地交付叙事）。
2. 发本 SEO 稿抢「Upwork MCP 定价 / 银行 AI 网关 MCP / MCP 开发收费 / Java MCP 招聘」搜索流量。
3. 长庆/襄阳 10-16 已截标结果公示后评估；湖北疾控 150 万（11 月）预研备齐合规资产。
4. 强化 GitHub Topics（治理/等保/CISA KEV/Spring AI Alibaba/多租户/接单）；评估主 mcp-alibaba 升 starter-mcp-registry 坐标（验证后再动）。
