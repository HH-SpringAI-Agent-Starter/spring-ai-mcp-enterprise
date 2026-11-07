# 💰 MCP 挣钱情报 · 2026-11-07（V1.65）

> 抓取窗口：2026-11-05 ~ 11-07（最近 3 天）｜全源：官方采购平台 / NVD·阿里云漏洞库·GitHub Advisory / Gartner·Knowledge Sourcing·You On AI / 招聘平台 / Upwork 费率页
> 定位：**不重复昨日 V1.64 记账**，只记 11-05 之后的新证据、新权威口径、新可交付动作。

---

## 0. 今日一句话结论

**MCP 的「治理」在这一周同时拿到了三样东西：权威品类（Gartner 首个 AI Agent Management MQ）、合规采购门槛（GDPR ROPA / 日志不可篡改）、以及必须外挂治理层的实证（11-06 官方 TS SDK 受众绕过 CVE + Gartner 逐家批评「第三方运行时治理缺失」）。** 而国内最紧的钱就在 6 天后——**广发证券两个征集公告的官方原文已核实，其「MCP Server 设计与开发适配能力」资质条款几乎是本框架 README 的逐条复刻**。今晚不写泛泛的市场稿，交付一份可直接用于 11-13 报名的应答素材包。

| 层级 | 今日新增 | 强度 | 对挣钱的含义 |
| --- | --- | --- | --- |
| 规范/实现 | `CVE-2026-111771` 官方 TS SDK 令牌受众绕过（11-06 披露）+ `102610` fast-agent 默认无鉴权开 shell + `123786` Agenta SSRF 带出 vault token | 🔥🔥🔥 | 「升级官方 SDK ≠ 安全」续证，且**升级后新校验默认关闭** → 治理层不可替代 |
| 权威 | Gartner 首个 AI Agent Management MQ + 382 条 MCP CVE / 79 Critical + 采用率 78% / 4 亿月连接 + KS 报告 CAGR 49% | 🔥🔥🔥 | 品类被正式承认 + 市场量化数字可进标书 |
| 产品 | DigitalOcean Action Gateway 治理规格、ISI / Agiloft / Subaio 三款受治理 MCP、GDPR ROPA 采购参考 | 🔥🔥 | 治理从「附加项」变成「产品定义 + 采购文档」 |
| 招标 | **广发证券两份征集公告官方原文核实**（11-13 17:30 截止 + 企业微信项目逐条资质） | 🔥🔥🔥 | **6 天窗口，最硬的销售素材** |
| 招聘 | 通义实验室 / 小米 / 商飞智能 / 纬英 / 聚垒 五条 Java+Spring+MCP（11-05 发布为主） | 🔥🔥 | 存量 Java 资产 MCP 化仍是主线 |
| 接单 | Upwork 0–15% 口径 11-06 三源复核 + Upwork Jobs API 可结构化选品 | 💵 | 找活这一步也可自动化 |

---

### 与昨日（V1.64）的差异 — 不重复记账
昨日已记：Wiz 90 天蜜罐三条 CVE + Qilin 勒索、11-03~11-05 NVD 波（123272/123271/111769/121419/121458）、IBM ContextForge 7 CVE、广发双征集「标题级」信息、周大福/美团 JD、Upwork 0–15% 口径修正、`examples/governance-hardening/` 参考实现。
**今日只增加**：① 11-06 当日新披露的 3 个 CVE 及其修复细节；② Gartner MQ 与市场规模/采用率的权威量化；③ 治理产品化规格（DigitalOcean / ISI / Agiloft / Subaio / GDPR ROPA）；④ 广发两份公告的**官方原文逐条资质与关键指标**（昨日仅标题）；⑤ 11-05 新发 5 条 Java JD；⑥ 告警规则参考实现与应答素材包两项工程增量。

---

## 1. 🔥🔥🔥 11-06 当日新 CVE：官方 SDK 的「受众」又没校验，而且升级后默认仍关闭

| CVE | 载体 | 根因 | 修复 / 坑 | 命中模块 |
| --- | --- | --- | --- | --- |
| **CVE-2026-111771**（11-06 披露） | **MCP TypeScript SDK** `requireBearerAuth()` / `verifyBearerToken()` | 只调 `verifyAccessToken()` 验 token 本身，**不比对 `aud` 或 `AuthInfo.resource` 与当前 MCP 服务资源标识** → 同一授权服务器签给别的服务的有效 token 被本 MCP 服务接受 = 跨服务授权绕过 | `@modelcontextprotocol/sdk` ≥1.32.0 / `server` ≥2.3.0 / `express` ≥2.0.2 / `server-legacy` ≥2.3.1；**⚠️ 升级后必须显式启用 `expectedResource`，否则新校验默认关闭** | `mcp-auth` + `mcp-gateway` |
| **CVE-2026-102610**（CVSS 6.4，11-06 NVD） | `fast-agent serve` ≤0.7.20 | HTTP transport 绑定 **`0.0.0.0:8000` 且 `auth=None` 默认、无任何警告**；项目 README quickstart 用的 `--shell/-x` 标志让 agent 带本地 execute shell 工具 → 网络邻接者一句话即可让 gpt-4o-mini 自主调 `execute` 回传 root 身份 / hostname / 文件内容；非交互 serve 模式下权限提示不介入 | 0.7.21；报告方要求「默认只绑 loopback，绑非 loopback 且无鉴权时必须大声告警」 | `mcp-gateway`(绑定) + `mcp-governance`(shell 工具禁用) + `mcp-monitor` |
| **CVE-2026-123786**（CVSS 8.5，11-06 NVD） | Agenta <0.122.5 | workflow revision `url` 只校验 scheme/netloc（`169.254.169.254` 直接放行）+ `follow_redirects=True` **且带新铸 Secret token** → SSRF 内网/云元数据 + 重定向跳把 token 交给攻击者 → 打开 vault API 读 provider secrets | 0.122.5；**最讽刺细节：同一代码库 webhook 路径早已用 DNS 解析阻断私网地址，workflow 路径从未获得同等校验** | `mcp-gateway`(出站白名单) + `mcp-auth` + `mcp-monitor` |

**关联（11-05 NVD 正式发布，CVSS 8.0）**：`CVE-2026-123271/123272`（ChatCLI / `mark3labs/mcp-go` OAuth resource confusion，RFC 9728 §3.3 缺失校验，CWE-346）。ChatCLI 修复版 **一次修掉 7 类相邻传输缺陷**：canonical URL 作唯一 resource indicator、SSE messages endpoint 限同源、拒绝跨源/降级重定向、授权服务器 metadata 必须声明 issuer 且走 HTTPS、`resource_metadata` 只在 server 自身 origin 跟随、**发现文档加大小上限**。

> **✅ 今日最值钱的一条交叉验证**：ChatCLI 修复清单 = 本框架昨天交付的 [`examples/governance-hardening/`](../examples/governance-hardening/README.md) 已覆盖条目（4MiB / fail-closed / 同源重定向 / 会话按用户 / 工具默认禁用 / 按 caller 授权 / HITL / 审计）。**昨晚写的参考实现，今早被第三方安全公告逐条背书** —— 这可以直接写进 11-13 广发报名的「可演示案例」。

**话术升级（可直接念给买方）**：官方连续两天在「受众 / 资源绑定」同一个点出洞，且修复需要**显式开启配置**；规范层已自认「无法在协议层强制 consent 等安全原则，交给实现者」。所以：**升级 SDK 是必要动作，但不是安全边界；边界在逐工具授权、资源强绑定、出站白名单与防篡改审计这一层。**

来源：[阿里云漏洞库 AVD-2026-111771](https://avd.aliyun.com/detail?id=AVD-2026-111771) · [eyeon.ai/f/3574](https://eyeon.ai/f/3574) · [AL-ICE 11-06 双 CVE 复盘](https://al-ice.ai/posts/2026/11/fast-agent-agenta-cve-2026-102610-cve-2026-123786-unauth-serve-ssrf-november-2026) · [AL-ICE resource confusion](https://al-ice.ai/posts/2026/11/mcp-oauth-resource-confusion-mcp-go-chatcli-november-2026) · [eyeon.ai/f/3551](https://eyeon.ai/f/3551)

---

## 2. 🔥🔥 权威层：Gartner 首个 MQ 把「治理」立成品类，同时逐家承认第三方运行时管不住

**Gartner 首个 Magic Quadrant for AI Agent Management Platforms**（Microsoft 列 Leader；Boomi / Automation Anywhere 为 Visionaries；Gravitee 为 Challenger；UnifyApps / **Dify** / AvePoint / Lyzr 为 Niche Players）。报告对几乎所有厂商的批评高度一致：

| 厂商 | Gartner 点名的缺口 |
| --- | --- |
| Gravitee | 网关策略能覆盖 agent-to-agent / tool / model / MCP / API 流量，**但只有流量经过 Gravitee 网关时才生效** |
| Boomi | 集中策略设计与本地执行分离（敏感数据留在客户环境）是优点，但**想要独立 agent 控制平面的客户会觉得平台太重** |
| **Dify** | 自托管 / 私有云 / **气隙部署**是优势，模型无关可切换 MCP servers；但「**最强控制只针对 Dify 内建的 agent，第三方运行时治理有限**」 |
| UnifyApps | agent discovery / 实时 guardrails / FinOps 好，但**最深执行仍在自有 runtime** |
| AvePoint | agent inventory / 生命周期 / owner attestation 好，**但缺实时运行时控制（检测或阻断 agent 行为）** |
| Automation Anywhere | 第三方 agent 治理不如平台内 agent 全面 |

> **🎯 与广发公告撞出的最锋利售前结论**：广发《企业微信持续建设项目》明确要求「**需使用我司的 Dify 或 hiagent 平台开发 Agent**」，而 Gartner MQ 恰好指出 Dify 的第三方运行时治理有限。→ **「买方指定低代码平台 + 外挂 Java/Spring 治理增强层」是最现实的联合体打法**，既不违抗甲方平台选型，又补齐鉴权/权限/限流/可观测/不可篡改留痕。本框架不是去替换 Dify，而是给 Dify 补上它自己承认缺的那一层。

**其他权威量化（今日核实，可进标书「市场与趋势」章节）**：

| 指标 | 数值 | 来源 |
| --- | --- | --- |
| MCP 企业 AI agent 栈渗透率 | **~78%**、**4 亿+ 月 SDK 连接** | You On AI 11-05 / 11-06 两次更新 |
| NVD 中提及 MCP 的 CVE | **382 条**（截至 10-21），其中 **2026 年 324 条、79 条 Critical** | for.you.com 治理综述 |
| 公开可访问 MCP server 无鉴权暴露 | 扫描 514 台，**195 台（38%）** 暴露无鉴权工具 | 同上 |
| Anaconda 扫描 | **73% 的 MCP server 共 143,000 个漏洞** | You On AI |
| RufRoot MCP bridge | **CVSS 10.0 RCE，暴露 233 个工具** | You On AI |
| CISA KEV | `CVE-2026-42271`（LiteLLM RCE）已进已知被利用清单 | You On AI |
| 2026 社区新建 MCP server | **16,000+** | Gartner（经 narrativegoldmine 汇编） |
| AI Agent 通信协议市场 | **2026 $235.0M → 2031 $1,725.0M，CAGR 49.0%**；**Managed MCP tool mediation 为最大商业品类**；亚太增速最快 | [Knowledge Sourcing Intelligence](https://www.knowledge-sourcing.com/report/ai-agent-communication-protocol-market) |
| Gartner 风险预警 | **40% agentic AI 项目若不转向 context mesh，2027 年前有取消风险**；要求 delegated OAuth 2.1 身份、agent→model/environment/agent 流量路径分离、scoped "Backend for Agent" toolsets | Gartner《How to Enable Agentic AI via API-Based Integration》 |
| 监管 | **OCC 新框架**把 agentic-AI 纳入既有模型风险 / 操作风险审查；审计方预测 **12–18 个月内开始索要 agent inventory**；NSA 5 月警示「MCP 快速扩散已超出安全模型发展」 | for.you.com |
| 生态 | MCP 交由 **Linux Foundation / Agentic AI Foundation** 治理（与 A2A 并列）；Google/Microsoft/Salesforce/Snowflake/ServiceNow 五公司联盟另推竞品协议；法国 84% 集中度发现 → 布鲁塞尔或按基础设施/反垄断介入 | You On AI |
| 资本 | **AIR Security 拿到 $50M** 专做「拦截 MCP 连接服务上 agent 调用」的防火墙 | You On AI |

**受监管行业采购基线已硬化**（KS 报告）：EU 数据驻留 / on-prem / 气隙部署 / **带 SBOM 的签名连接器** / **不可变审计日志** / EU AI Act 高风险流程一致性检查，已成 baseline 而非加分项 → 与国内「私有化 + 等保 + 密评」完全同构，印证「不能上云的钱最好赚」。

来源：[CX Today · Gartner MQ](https://cxtoday.com/gartner-unveils-inaugural-magic-quadrant-for-ai-agent-management) · [You On AI · MCP 态势（11-05）](https://www.youonai.ai/newsite/entity.html?e=model-context-protocol) · [You On AI · MCP 产品（11-06）](https://www.youonai.ai/newsite/entity.html?e=mcp) · [AI Agent Governance Shifts to Runtime Control](https://for.you.com/posts/ai-agent-governance-shifts-to-runtime-control-as-mcp-risks-4d0ab1fe) · [System Integration 汇编](https://narrativegoldmine.com/notes/pages/System-Integration)

---

## 3. 🔥🔥 治理产品化：四款受治理 MCP + 一份 GDPR 采购参考，规格已公开到可抄

### 3.1 DigitalOcean Action Gateway + Harness Runtime（public preview，今日最完整公开规格）
| 控制点 | 具体做法 | 本框架对应 |
| --- | --- | --- |
| Session tool selection | 一个 MCP endpoint 接 **16,000+ 工具 / 500+ 供应商**；**调用不在会话选定工具内的，即使有 allow 规则也拒绝**；「搜到工具 ≠ 有权运行」 | `mcp-registry` 准入 + `mcp-governance` 逐工具策略 |
| allow / ask / deny | **省略规则默认 ask**；同等粒度下 **deny 压过 ask**，approval 不能覆盖 deny | `mcp-governance` fail-closed 默认 |
| Bound approvals | 每次审批绑定 **session + tool version + arguments**（参数一变审批失效） | `mcp-governance` HITL + `mcp-audit` |
| 凭据执行时解析 | 「**模型看到工具结果，看不到 token**」；新连接归属创建者 | `mcp-auth` 凭据代理 |
| Harness Runtime | microVM 硬件隔离；egress **一旦命名允许主机即开启白名单、其余全拒**；沙箱不接受入站公网连接 | `mcp-core` 沙箱 + 出站白名单 |
| 审计 | 每个动作、策略决策、谁批准、**背后的 prompt** 全记录，**日志不过期** | `mcp-audit` + `mcp-monitor` |

> ⚠️ 与既有结论一致：**这些治理产品清一色非 Java 栈**（DigitalOcean / Bifrost / Docker / MS / AWS / Kong / IBM ContextForge），本框架仍是「唯一 Java/Spring 开源企业级治理栈」的差异化位。

### 3.2 三款「 governed MCP 」同周上线（11-05 官方原文核实）
- **ISI Markets**（伦敦，[官方发布 11-05](https://isimarkets.com/insights/isi-launches-mcp-server-to-bring-trusted-data-and-intelligence-into-clients-ai-workflows)）：标题即 **"Governed access"**；访问严格按**客户已订阅内容、数据 entitlements、AI 使用权**授权，检索内容附**来源引用**；4,000 万+ 新闻文章 / 年 50 万份研报 / 4,000 万+ 宏观与另类数据序列 / 200+ 经济体；可接 Claude / ChatGPT / Copilot / 自有 agent。→ **金融数据卖方把「授权粒度」直接做成产品卖点**，与广发「多源金融数据 + 混合检索」需求同构。
- **Agiloft**（CLM，[官方发布 11-05](https://www.agiloft.com/news/execution-focused-ai-agents)）：MCP Server 作外部工具访问中央合同库的标准接口，明确「保留既有 permissions / controls / security standards」；Bulk Update Agent **强制先预览全部受影响文件 + 显式人工确认 + 出成功/失败/跳过报告**。调研数字：采购负责人估计 **≥15% 供应商支出完全游离于合同之外**，**仅 36% 组织**自动把采购结果关联到合同 → HITL 的 ROI 话术。
- **Subaio**（丹麦 fintech，[FF News](https://ffnews.com/news/pressrelease-talk-to-your-finances-9ad63b5d)）：MCP Server 把 PSD2 受监管开放银行的实时账户数据接进 ChatGPT/Claude；**AI 永不拿到银行登录凭据、无转账权限、仅显式用户同意**；个人免费、企业付费。→ 与美加银行 10 月封锁 agent 形成对照，是金融 MCP「只读 + 最小权限 + 显式同意」的标准范式。

### 3.3 GDPR 采购参考：合规文档本身成了评估项
[Peliqan《GDPR-Compliant MCP Servers: The 2026 Procurement Reference》](https://peliqan.io/blog/gdpr-compliant-mcp-servers/) 把 **ROPA（处理活动记录）** 列为 DPA 问询 / 网络保险承保 / SOC 2 审计**都会索要的唯一工件**，要求 MCP server 自动产出：

| ROPA 字段 | MCP server 应自动记录 |
| --- | --- |
| 处理目的 | **per-tool purpose tag**（客服分流 / 财报 / 营销分析 / HR） |
| 数据类别 | per-connector 数据分类（客户主数据 / 交易 / 员工 / 通信） |
| 数据主体类别 | 客户 / 员工 / 潜在客户 / 合作方，按源系统打标 |
| 接收方 | LLM 供应商、接收写回的下游 SaaS、**审计日志目的地** |
| 第三国转移 | 每个 sub-processor 司法辖区 + SCC 引用 |
| 保留期 | 按数据类别在仓库层与日志层分别配置 |
| 安全措施 | 静态/传输加密、RBAC、脱敏、MFA、**审计日志** |

自动化 ROPA 每年为 DPO 省 **40–80 小时**；GDPR 第 15–22 条八项数据主体权利需逐条落到 MCP 架构（agent 通过 MCP 读写即在做 processing）。7 家供应商按 Schrems II 姿态 / CLOUD Act 可达性 / EU-only residency / 列级脱敏 / DSAR 工具横向对比。
→ **对国内的直接映射**：广发要求「服务行为自动留痕、**日志不可篡改**」「数据权限按客户经理归属严格隔离」，就是 ROPA 的「安全措施 + 数据主体隔离」两栏。本框架 `mcp-audit` + `mcp-tenant` 可作为该表的事实来源，写进应答材料比空谈「符合等保」有力得多。

---

## 4. 🔥🔥🔥 招标：广发证券两份公告官方原文核实，11-13 17:30 是最紧窗口（剩 6 天）

### 4.1 《智能金融资讯数据服务平台》供应商征集（延长至 11-13 17:30）
官方原文：[gfjc.gf.com.cn/gyszjgg/13321.jhtml](http://gfjc.gf.com.cn/gyszjgg/13321.jhtml)

- **背景**：已采购 Wind / 通联 / 同花顺等多源金融数据，沉淀大量内部知识（研究方法论、行业资料、业务经验）。
- **技术范围原文**：「基于多源数据，引入 **MCP 工具协议、Skill 技能包、子 Agent 编排、长期/会话记忆和混合检索**等技术，建设**券商级**智能金融数据服务平台，实现研究流程从『人工搜集分析』向『人机协同智能研究』升级」；「沉淀**可外部产品化**的能力组件……具备形成新收入和差异化服务的潜力」。
- **资质硬门槛**：① 独立法人 + 技术团队；② **成立 3 年以上、注册资金 ≥500 万**；③ **原厂商或授权代理商**；④ 近 3 年 ≥1 个**金融行业**同等规模成功案例 + 证明材料；⑤ 熟悉**金融数据服务与证券行业数据安全、保密及合规要求**；⑥ 近三年无重大违法；⑦ 与其他报名服务商无关联；⑧ 提前备齐**纳税信用评级/无欠税证明 + 近两月完税凭证**。
- **提交**：邮件 `gfjc@gf.com.cn`，邮件名「广发证券XXX项目+供应商名称」，联系人 **李华龙 020-66335630**；附件可编辑电子版 + 盖章扫描件（JPG/PNG/GIF/PDF 独立存储）。
- ⚠️ 公告明确：**这是招标启动前的供应商预征集，不等于采购邀请**，按时提交合格资料后不必然获正式投标邀请 → 目标是**进入供应商库**，不是中标。

### 4.2 《2026 年企业微信持续建设项目》供应商征集（同平台 13322.jhtml）
官方原文：[gfjc.gf.com.cn/gyszjgg/13322.jhtml](http://gfjc.gf.com.cn/gyszjgg/13322.jhtml) ｜ [bid5 转载（含关键指标全文）](https://mobile.bid5.cn/mobile-site-project/html/index/message.html?infoid=1091001944389551)

- **三层体系原文**：「**智能体(Agent) + 技能(Skill) + MCP 工具接入**」，6 个场景智能体（微管家业务专家 / 智能问数客群 / 智能问数单客户 / 打新智能体 / 服务日志记录专家 / 服务查询总结专家），配套可复用技能 + 客户信息、资产/持仓/交易/盈亏、业务准入规则、高级筛选引擎、留痕中心、企微会话与触客等 MCP 工具接入；「按 **MCP 工具接入—基础技能—场景智能体** 分层分批交付，首批优先交付服务日志记录场景」。
- **专业资质逐字（★ 与本框架 README 近乎逐条复刻）**：「具备技能(Skill)工程化能力（**定义、注册、编排、复用、版本管理与效果评测**）及 **MCP Server 设计与开发适配能力（工具定义与描述、参数校验、鉴权与权限控制、调用限流、日志与可观测性）**」；ASR/OCR/结构化数据；**与券商核心业务系统（客户、账户、交易、留痕）对接经验**；**须使用我司的 Dify 或 hiagent 平台开发 Agent**。
- **关键指标逐字**：数据权限**按客户经理归属严格隔离**、服务行为自动留痕、**日志不可篡改**、技能可复用可编排、**MCP 工具调用稳定且权限可控**、统计口径与金管家后台一致、常规查询秒级响应、大范围统计与报告生成支持异步；**广州本地服务团队**、上线后运维与持续迭代（含技能与 MCP 工具持续扩展）。
- 另需近 2 年 ≥2 个金融行业类似项目成功案例 + 可演示案例；第三方组件须原厂授权。

### 4.3 同采购人 11 月征集密度
| 项目 | 时间 | 备注 |
| --- | --- | --- |
| 智能金融资讯数据服务平台 | **11-13 17:30 截止** | MCP 五技术 + 券商级 + 可外部产品化 |
| 2026 企业微信持续建设项目 | 征集进行中 | MCP 五件套写进资质门槛 |
| 2026 网络安全威胁情报资讯 | 11-01 起 | [项目星采招网](https://www.xiangmuxing.com/notice/zbpt_show-htm-catid-6-itemid-1a813ac77c214860b9cfc725e158d7d4.html)，微步在线品牌 1 年 |
| 数据库审计设备过保替换 | 11 月 | [广发官网](https://www.gf.com.cn/web/article/detail?columnId=4&articleId=01a19a17173e7efc9d4eec54e38c8b87) |

→ **同一头部券商一个月内至少 4 个供应商征集，说明供应商库正在扩容，此时报名进库的性价比全年最高。**

### 4.4 国内其他标（诚实标注，避免虚增）
- **延续跟踪（历史已记，倒计时）**：山东重工集团级平台二次标 **11-20**（统一入口/服务/管理/安全管控，接受联合体）｜厦门大学 **11-11**｜军队大模型安全防护 190 万 **11-16**｜中山岸线视频算力 399 万 **11-17**｜呼和浩特卫健委 900 万 **11-23**｜中国林科院 300 万 **11-24**｜中央民大 3712 万 **11-25**｜广西数智方志 1450 万（11 月预采）。
- **今日新搜到的国内公告经核为泛硬件/等保标，非 MCP 直接机会**：黑龙江商业职业学院网络中心等保合规建设 69.8 万（11-11 截）、济源 5G+数字化应用基地强电设备 625.53 万（11-26 截）、合肥市一院 UPS 79 万（11-26 截）、国网山西 2026 第四次物资招标（11-30 截）、苏州苏能一体化电源中标公示（11-05）。**仅作「私有化 + 等保刚需持续放量」的旁证，不计入 MCP 可投清单。**

---

## 5. 🔥 招聘：11-05 新发岗位清一色 Java + Spring + MCP（存量资产 MCP 化仍是主线）

| 公司 / 岗位 | 薪资 / 地点 | JD 原文关键词 | 命中模块 |
| --- | --- | --- | --- |
| **通义实验室 · Token Foundry** 大模型应用后端工程师（**11-05 发布**） | 北京 / 杭州 | 「设计**工具调用协议（MCP、Tool Schema、Function Calling）**」+ 高并发大模型服务网关 + SLA / 容灾 / **限流熔断** + To B 商业化「可靠性、**安全性、计费**与运维体系」 | `mcp-gateway` + `mcp-governance` + `mcp-monitor` |
| **小米科技** 高级 AI 系统开发工程师（大模型与 RAG，**11-05 14:40**） | **25–30k** / 武汉 | 「设计并实现 Agent 工作流编排框架，支持**工具调用(MCP 协议)**」+ 基于 **Java/Go/Python** 高并发 AI 微服务 + 5 年大型服务端 + **Spring / SpringMVC / SpringBoot / Spring Cloud** | `mcp-registry` + `mcp-gateway` |
| **商飞智能** AI 应用工程师（招 10 人） | **30–50k** / 上海浦东 | 5 年 Java 后端 + **Spring Cloud / Nacos / Sentinel / Seata** + 「理解 **MCP 的 Client/Server 模型与工具发现-调用-结果回传链路**」+ SSE/WebSocket + token 计费口径；加分项「**agent Run 级可观测性和成本护栏**」 | `mcp-monitor` + `mcp-governance` + `mcp-auth` |
| **纬英科技** 资深后端（BOSS） | 广州 | 企业级智能体应用含「**MCP 接入**、记忆与工作流编排」+ SDD + Agent 编排 + Rules/Skills/**护栏规范** + **Eval 测试门控** + 架构审查验收 | `mcp-governance` + `mcp-registry` |
| **聚垒科技** AI 应用开发工程师 | 武汉 | 「擅长 **springboot / springai** 开发」+ 3 年 Java 服务端 + 医疗 AI 落地 | `mcp-spring-boot-starter` |

海外延续（历史已记，不重复）：Citi（CAD 120.8–170.8K，JD 原话 build MCP servers）/ Digital Dhara（auth/authz/auditability/HITL/guardrails）/ EPAM / Apple（Java Spring Boot + Spring AI + 护栏 + MCP）/ Mastercard / Infotel / Disney / PE / 周大福（中国香港，identity propagation + access controls + audit trails）。

**结论**：国内 JD 已从「会不会用 MCP」升级到「**能不能把 MCP 工具调用做稳定、做可观测、做成本护栏、做限流熔断**」——这正是 `mcp-governance` + `mcp-monitor` 的英文同义词，也是本框架相对纯 Python/TS MCP demo 的护城河。

---

## 6. 💵 接单：费率口径 11-06 三源复核 + 「找活」这一步也能自动化

| 平台 | 自由职业者费率（2026-11 核实） | 备注 |
| --- | --- | --- |
| **Upwork** | **0–15%**（按合同类型与供需在提案前显示、合同期内固定） | 额外 Connect **$0.15/个**；Freelancer Plus **$19.99/月**含 100 Connects + direct contract 0%；客户端 Basic 3–5%、Business Plus 8–10%；**旧「20/10/5% 滑动」或「flat 10%」均已过时** |
| Fiverr | 20% | 含 extras / tips |
| Freelancer | 10% 或 $5（取高） | Contest 亦 10% 或 $5 |

🆕 **Upwork Jobs API（[scrapercompany，端点与价格 11-06 复核](https://scrapercompany.com/upwork-jobs-api)）**：可按关键词 / 最低固定预算 / 时薪区间检索**公开 job post**，返回 `published_at`、`client_payment_verified`、`applicants` / `interviews` / `hired`、`workload` / `duration_weeks` / `contract_to_hire`、`skills`；2 credits/次成功调用、7,500 credits 一次性买断、失败不扣费。
→ **可落地动作**：把「MCP / Model Context Protocol / MCP server」设为关键词，按 `min_fixed_budget` 与 `client_payment_verified` 过滤，定时拉取生成选品清单（避开 `applicants` 已爆的红海单），再用 Upwork 官方 MCP Server + Upwork Now（11-02 公测，周活 ×20 / Q3 AI 技能人才 GSV +55% 至 $112M）在 AI 助手内完成提案草稿。这是把「找活」从手工翻页变成结构化情报的最小闭环。

来源：[Earninger · Upwork Fees（10-22 核）](https://earninger.com/store/upwork-freelance-marketplace) · [Pakera 11-06 复核](https://pakera.pk/fiverr-vs-upwork-pakistan-2026-which-is-better-fees-pros-cons-how-to-earn) · [独立开发者费率横评](https://zhangdapeng.dev/p/fiverr-upwork-freelancer-for-indie-hackers) · [猎者出海 · Upwork MCP 中文解读](https://www.liezhe.com/upwork-mcp)

---

## 7. 🧩 Java + Spring + AI 组合在该赛道的 7 条卖点（可直接进售前 / 标书）

1. **「协议层自认管不了，边界只能外挂」**：MCP 规范明确无法在协议层强制 consent，11-05/11-06 官方 SDK 连续在 aud / resource 绑定出洞且修复需显式开启 → 治理增强层不是可选项，是责任分界线。金句：**把责任放在我能治理的那一层**。
2. **「买方指定平台 ≠ 买方不需要治理」**：Gartner MQ 逐家承认第三方运行时治理缺失、Dify 被点名「最强控制只覆盖内建 agent」；本框架可与 Dify / hiagent 并存，专补鉴权、逐工具权限、限流、可观测、不可篡改留痕（正好是广发 13322 号公告的资质条款）。
3. **「唯一 Java/Spring 开源企业级治理栈」**：DigitalOcean / Bifrost / Docker / MS / AWS / Kong / IBM ContextForge 清一色非 Java 栈且治理锁在 Enterprise 付费墙；政企存量 Java 资产（券商核心、账户、交易、留痕）MCP 化只有 Java 栈能低风险改造。
4. **「可演示的合规工件」**：`mcp-audit` + `mcp-tenant` 直接产出 GDPR ROPA 的「安全措施 / 数据主体隔离」两栏 + 广发要求的「日志不可篡改」；`docs/enterprise-rfp-checklist.md` 的 16 类攻击面→治理模块对照表可当验收清单逐条演示。
5. **「参考实现不是 PPT」**：`examples/governance-hardening/`（4MiB / fail-closed / 同源重定向 / 会话按用户 / 工具默认禁用 / 按 caller 授权 / HITL / 审计）与今晚新增 `examples/monitor-alert-rules/`（攻击路径→告警规则）均为可运行代码，且 11-06 ChatCLI 修复公告逐条验证了前者的设计选择。
6. **「私有化 + 等保 + 密评主场」**：Gartner/KS 报告把 EU 数据驻留 / 气隙 / 带 SBOM 签名连接器 / 不可变审计列为基线，国内同构；Java 系在信创私有化交付上的工程成熟度是 Python 系弱项。
7. **「市场确定性」**：Gartner 首个 MQ 立品类 + 78% 渗透 / 4 亿月连接 + CAGR 49% + Managed MCP mediation 为最大商业品类 + 382 条 CVE / 79 Critical + AIR Security $50M 融资 → **买方预算不是「要不要做 AI」，而是「做了之后怎么管住」**。

---

## 8. 今晚交付 / 明天做什么

### 今晚已交付（V1.65）
| 文件 | 用途 |
| --- | --- |
| `docs/earnings-report-2026-11-07.md` | 本文件：11-05~11-07 全源实时情报 + 来源链接 |
| **`docs/gf-securities-tender-response-2026-11-07.md`** | 🆕 **广发 11-13 应答素材包**：逐条要求→模块→可演示证据→应答话术 + 资质自检与联合体建议 |
| `docs/blog-java-mcp-cve-111771-gartner-mq-gf-tender-2026-11-07.md` | 掘金/CSDN SEO 稿（受众绕过 CVE × Gartner MQ × 券商征集窗口） |
| `docs/enterprise-rfp-checklist.md` | 攻击面清单 12 → **16 类**，新增 11-06 三 CVE + Gartner MQ 第三方治理缺口 + GDPR ROPA |
| **`examples/monitor-alert-rules/`** | 🆕 兑现昨日待办⑤：Wiz 六条攻击路径 + 11-06 CVE → Prometheus 规则 + `McpAlertService` 自定义规则 + README（不进 reactor，零构建风险） |
| `docs/V1.65-release-notes.md` + README | 版本表 V1.65 行、招标/招聘/治理三区块更新、日期改 11-07 |

### 明天 / 未来 6 天候选（按优先级）
1. **⏰ 广发 11-13 17:30（剩 6 天）**：按应答素材包备齐附件（成立年限/注册资金/原厂授权/金融案例证明/纳税信用+近两月完税凭证），目标是**进供应商库**；若自身资质不足，优先找**广州本地有金融案例的集成商做联合体**（13322 号明确要求广州本地服务团队）。
2. 发今晚 SEO 稿抢「MCP 令牌受众绕过 / CVE-2026-111771 / Gartner AI Agent 管理象限 / 广发证券 MCP 征集」流量。
3. 山东重工 11-20「统一安全管控」联合体素材；厦大 11-11 / 军队 190 万 11-16 / 中山 399 万 11-17 / 呼市 900 万 11-23 / 中国林科院 300 万 11-24 / 中央民大 3712 万 11-25 倒计时。
4. 把 GDPR ROPA 七字段做成 `mcp-audit` 的元数据导出示例（对标 Peliqan 采购参考，可作为涉外/外资客户售前加分项）。
5. 跟踪 Gartner MQ 完整报告与五公司联盟竞品协议是否出现真实部署；跟踪 ISI / Agiloft / Subaio 受治理 MCP 的第三方评测。
6. 可选：用 Upwork Jobs API 做一次 MCP 关键词选品实测，产出「红海单 vs 蓝海单」清单。
