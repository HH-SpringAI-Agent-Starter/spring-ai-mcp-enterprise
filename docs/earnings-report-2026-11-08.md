# 💰 MCP 挣钱情报 · 2026-11-08（V1.66）

> 抓取窗口：2026-11-06 ~ 11-08（最近 3 天）｜全源：厂商官方产品动态 / 安全公告与 NVD / 招聘平台 / Upwork 与财报分析 / 政府采购网
> 定位：**不重复 V1.65 记账**，只记 11-07 之后的新证据、新竞争事实、新可交付动作。
> 今日为**周日**，国内无新开评标；重点是「定位复核」而非新增标的。

---

## 0. 今日一句话结论

**这一周，MCP 治理从「开源框架的差异化主张」变成了「三大云 + iPaaS 的标准产品层」；同一周，攻击面下沉到 OAuth 连接路径——工具级护栏架构上看不见的那一层。**

两件事合起来，逼出一个必须做的定位调整：**别再卖「我们有 MCP 治理」（买方会反问 Azure/腾讯已经有），改卖「跨运行时签字 + 私有化合规工件 + 存量 Java 资产接入 + 连接路径准入」这四件云厂商结构上不做的事。** 今晚的核心交付因此不是又一篇市场稿，而是一份可直接进标书的 [`enterprise-cloud-gateway-comparison-2026-11-08.md`](enterprise-cloud-gateway-comparison-2026-11-08.md)（含 5 条买方问答话术），外加把 V1.65 两项工程 TODO 真正闭环。

| 层级 | 今日新增 | 强度 | 对挣钱的含义 |
| --- | --- | --- | --- |
| 竞争 / 品类 | Azure APIM AI Gateway 公开预览、AWS AgentCore Gateway 五类扩展、腾讯云 v3.9.8（11-06 更新）、Celigo 11 月版 | 🔥🔥🔥 | 品类与预算不用再教育；**旧话术失效**，差异化必须换锚点 |
| 安全 | `CVE-2026-119025` MCP Inspector 握手期 RCE + 兄弟洞 9.6 + opencode「无 CVE 修复」+ LiteLLM 提权 RCE | 🔥🔥🔥 | 攻击在连接路径、零工具调用 → 新增一层可卖的交付 |
| 产品化规格 | Travel Code 11-06 写操作开放（首调永不执行 / 10 分钟一次性码 / 命名 scope）、Workspace369（AI 应用不能批准自己的请求 / 审计只存字段名） | 🔥🔥 | HITL 有了可抄的逐条验收规格 |
| 招标 | 广发 **11-13 17:30 剩 5 天**；周日无新增 MCP 直接标的 | ⏰ | 本周最硬的钱，材料已备齐，只差资质与联合体 |
| 招聘 | 11-06 五条 Java+Spring+MCP（智慧树 / 电力英才 / 箱箱共用 20-30k·13薪 / 创维 / 周大福中国香港） | 🔥 | 存量 Java 资产 MCP 化仍是主线 |
| 接单 | Upwork 收购 WorkMotion 全链路闭环、Q3 AI 技能 GSV 口径交叉验证、Jobs API 结构化选品、费率新细节 | 💵 | 找活 → 交付 → 合规雇佣的通道正在被平台打通 |

---

### 与昨日（V1.65）的差异 — 不重复记账

昨日已记：11-06 三 CVE（111771 / 102610 / 123786）、Gartner 首个 AI Agent Management MQ、DigitalOcean/ISI/Agiloft/Subaio 四款受治理 MCP、GDPR ROPA 七字段、广发双公告官方原文、11-05 五条 Java JD、Upwork 0–15% 口径、`examples/monitor-alert-rules/` 与广发应答包。
**今日只增加**：① 四大平台把治理做成标准层（含 11-06 腾讯云产品动态这一**带日期戳的硬证据**）；② 11-05~11-07 公开的「连接路径」攻击链与无 CVE 修复通道；③ Travel Code / Workspace369 的 HITL 逐条规格；④ 11-06 新发五条 JD；⑤ Upwork 收购链路与费率/GSV 口径核实；⑥ 三项工程增量与一份竞争应答文档。

---

## 1. 🔥🔥🔥 治理成了平台标准层：既是最强背书，也是最大竞争

| 平台 | 关键能力（截至 2026-11-08 公开信息） |
| --- | --- |
| **Microsoft Azure APIM「AI Gateway」公开预览** | 集中管理 models / **MCP servers** / policies / observability；token 限流与配额、Content Safety、模型回退；REST 经 OpenAPI 转 MCP 工具；token 指标按 consumer 流 App Insights、prompt/completion 日志进 Azure Monitor。合作方 Adaptiv 原话：「传统 API 网关理解一次 HTTP 事务，**AI 网关理解一次 AI 交互**」 |
| **AWS Bedrock AgentCore Gateway** | 全 MCP primitives（tools/prompts/resources + outputSchema + readOnly/destructive 注解）；**dynamic listing** 请求时按调用者身份实时列工具；Streamable HTTP streaming；session 按用户绑定防劫持（15min–8h）；**elicitation HITL 三模式**；OAuth 2.0 **RFC 8693 on-behalf-of**；PrivateLink |
| **腾讯云 云原生智能网关 v3.9.8**（产品动态 **2026-11-06 18:27 更新**） | MCP 网关支持 **OAuth 2.0 授权码**访问后端存量 HTTP 系统「**无需改造既有鉴权体系**」；按消费者/密钥配置模型可用范围；**组织节点逐级配额与成本计量**；意图路由自定义 |
| **Celigo MCP Management 11 月版** | Server / Gateway / Registry 三件套；**caller context 从已认证 MCP 请求解析身份，不接受模型自称**，无关联人员时返回空而非无限制（fail-closed）；registry API v0.1；end-user roles；step-level 执行日志；**安装的 server 默认 disabled 待审核**（与 Uber 生产实践同构） |
| **企业标准化共识**（sim.ai 横评） | 八条硬要求：审批清单+owner / 数据分类 / workspace 级凭据 / 增改批调权限分离 / 域名网络控制 / 执行记录 / schema 版本与变更控制 / 超时重试限流 →「**协议兼容 ≠ 企业标准化**」 |

**对本项目的双重含义（必须诚实写进售前）**：

- ✅ **利好**：一年前需要向甲方解释「为什么 MCP 需要治理」，现在由 Azure / AWS / 腾讯云的产品路线图共同背书；广发 13322 号把五件套写进资质门槛不再是孤例。
- ⛔ **压力**：`mcp-registry` / `mcp-gateway` / 限流 / 可观测这些**通用能力已商品化**，继续把它们当主卖点，选型会上会被一句「腾讯云已经支持了」直接打回。
- 🎯 **换锚点**：见 [`enterprise-cloud-gateway-comparison-2026-11-08.md`](enterprise-cloud-gateway-comparison-2026-11-08.md) 的能力对照表与 5 条问答 —— 落到**跨运行时治理、私有化合规工件、存量 Java 权限复用、连接路径准入**四件事上。

> 📌 生态背景（非近 3 天，供口径校正）：MCP 已于 2025-12-09 由 Anthropic 捐给 Linux Foundation 旗下 **Agentic AI Foundation**（Anthropic / Block / OpenAI 共同发起，Google / Microsoft / AWS / Cloudflare / Bloomberg 支持），官方称 10,000+ 公开 server。协议中立化 → 买方不必担心单一厂商锁定，反过来更愿意为「可自建的治理层」付费。

---

## 2. 🔥🔥🔥 攻击面下沉到「连接路径」：工具级护栏看不见

### 2.1 `CVE-2026-119025` — 官方调试工具在握手阶段执行代码

| 项 | 内容 |
| --- | --- |
| 载体 | **MCP Inspector**（官方调试工具，AWS/Cloudflare/Azure 文档都推荐用它验远程 server），约 11,000 stars / **768,000 周 npm 下载** |
| 评级 | CVE CVSS 4.0 **7.7**；`GHSA-q978-78c7-phqw` CVSS 3.1 **8.8**；公告 10-21、CVE 10-29、**Pillar Security 11-05 公开完整利用链** |
| 链 | 远程 server 的 OAuth discovery 返回 `authorization_endpoint` → Inspector 自动打开（CLI/TUI/Web 三处、交互终端默认开）→ Windows/WSL 上 npm `open` 10.x 把 URL 塞进 **PowerShell 双引号**并 base64 `-EncodedCommand` → PowerShell 求值 `$(...)` |
| 载荷 | `https://evil.test/$([IO.File]::WriteAllText('C:/Users/Public/pwned.txt','RCE'))` —— **合法 https URL，scheme 校验直接放行**；URL 序列化保留 `$ ( ) [ ] : , + &` |
| 为什么护栏没用 | **零工具调用、零审批提示、零模型参与**；注入在比 agent 低一层的连接路径；server 不必在配置时就恶意（过期域名 / 后续失陷 / typosquatting 同样成立） |
| 修复 | Inspector **2.12.0**（导入 `server.json` 用 2.14.0）；根治靠 `open` 11 的参数转义/单引号 + **argv 形状**；>80% `open` 包周下载仍解析到受影响版本 |
| 兄弟洞 | v1 Inspector `GHSA-4fc4-wcmh-4g86` **9.6**：OAuth callback query 把远程 session 翻转成本地 stdio 执行，**不限 OS** |

### 2.2 「无 CVE 的修复」让扫描器集体失明

- **opencode 1.18.33**（2026-09-28）同类 PowerShell 注入，PR 标题是 `refactor(core): share a browser opener...`，**无 CVE、无公告** → Dependabot / GitHub Advisory DB / SBOM 全部静默；Adversa AI 统计 10 起 Claude Code 相关攻击**仅 2 起有 CVE**。厂商回应「恶意 MCP server 不在威胁模型内」也值得写进售前争论。
- **LiteLLM `GHSA-7hp6-4w63-5g45`**（11-07 Daily Brief）：`internal_user` 利用**同一 salt 复用**于密钥加密与 session 签发，伪造 proxy-admin 凭据 → 经 **MCP stdio endpoint** 主机 RCE。修复 1.100.4 / 1.101.3 / 1.102.2 / 1.103.1 / 1.104.0rc2；临时缓解 `EXPERIMENTAL_UI_LOGIN=false`（会断 CLI SSO）。**与 V1.64 Wiz 蜜罐的 `CVE-2026-59822` 是同一组件连续两周被打**。
- **Check Point Yoba 活动**：假 ChatGPT / Claude / GPT-6 Astra 的 GitHub 安装包用合法签名 exe + **DLL sideloading** 投毒 → 不要从搜索结果仓库安装 MCP server / agent wrapper。
- 11-06 一周五洞复盘（APT / GPUStack 9.8 在野 / Inspector / OpenSSH / 泄露密钥）给出统一形状：**都不是模型漏洞，是 AI 工作底座的管道**。

> ✅ **今日最值钱的工程结论**：`mcp-governance` 的工具审批再严，也管不到「还没连上就已执行」的那一步。V1.66 新交付 [`examples/connect-path-guard/`](../examples/connect-path-guard/README.md)（字符级 URL 校验 → 发现文档上限 → argv 启动 → 远程默认 opt-in → 客户端版本下限），与服务端过滤器、监控告警构成三层分工。

---

## 3. 🔥🔥 HITL 终于有了可抄的逐条规格（11-06 / 11-07）

| 来源 | 可直接写进验收清单的做法 |
| --- | --- |
| **Travel Code MCP 写操作开放（11-06）** | 四类动作（下单/改单/取消/联系旅客）**首次调用永不执行**；确认发生在 **server 而非 model**；支持 MCP elicitation 时由 server 发 Confirm/Cancel，**模型不能代答**；不支持时返回 summary + **绑定 session 与精确参数、10 分钟有效的一次性码**；OAuth 命名 scope（`flights:search` … `orders:write`）；策略在**创建订单时**校验，超策略直接拒绝；连接外部系统时每工具 **Always / Ask / Off**、工具列表变更即暂停连接待重批、外部返回**只当数据不当指令**；诚实边界：**没有 submit-for-approval 步骤** |
| **Workspace369 CRM MCP（厂商文档 11-07 核）** | 127 工具；**12 类动作默认等待应用内审批**，审批链接 60 分钟过期；**AI 应用不能批准自己的请求**（自批分离）；每次调用按成员实时权限校验；**审计日志只存字段名、不存字段值**（隐私友好）；120 calls/min/connection、600/workspace |
| **Celigo / AgentCore** | 从已认证请求解析 caller 身份、无身份时 fail-closed 返回空；dynamic listing 按身份实时列工具；elicitation 三模式 |

→ 与广发 13322 号「日志不可篡改 / 按客户经理归属隔离」同构。建议把这些逐条补进 [`enterprise-rfp-checklist.md`](enterprise-rfp-checklist.md) 的验收列。

---

## 4. ⏰ 招标 / 采购：周日无新增直接标的，最紧窗口剩 5 天

| 标的 | 截止 | 状态 / 动作 |
| --- | --- | --- |
| **广发证券《智能金融资讯数据服务平台》供应商征集（13321）** | **11-13 17:30（剩 5 天）** | 逐条应答包已备：[`gf-securities-tender-response-2026-11-07.md`](gf-securities-tender-response-2026-11-07.md)。本周动作＝备齐原厂组件授权 / 金融行业案例证明 / 近两月完税凭证 / **广州本地服务团队**，资质不足即组联合体；目标进供应商库 |
| 广发《2026 企业微信持续建设项目》（13322） | 征集阶段 | 五件套写进资质 + 指定 Dify/hiagent + 日志不可篡改 → 用今日「跨运行时治理」话术应答 |
| 山东重工集团级大模型平台二次招标 | 11-20 | 「统一入口/服务/管理/**安全管控**」，接受联合体 |
| 厦大 11-11 ｜军队大模型安全防护 190 万 11-16 ｜中山 399 万 11-17 ｜呼市 900 万 11-23 ｜中国林科院 300 万 11-24 ｜中央民大 3712 万 11-25 | 倒计时 | 备「治理 + 网关 + 等保/密评」素材 |

> ⚠️ **诚实标注（延续 V1.65 口径）**：今日检索到的 [广州税务 2026 年服务器采购](https://www.ccgp.gov.cn/cggg/zygg/gkzb/202611/t20261106_27639376.htm)（11-06 发布、11-30 开标）是**通用服务器采购，非 MCP 直接机会**，不计入可投清单；[广发证券冠名的机器人技能大赛](https://www.sznews.com/news/content/2026-11/07/content_32210110.htm)（报名同至 11-13）与采购无关，不要混淆。**预征集 ≠ 采购邀请，不得虚构资质材料。**

---

## 5. 🔥 11-06 五条 Java + Spring + MCP 招聘（存量资产 MCP 化仍是主线）

| 企业 / 岗位 | 薪资 / 地点 | JD 关键点 |
| --- | --- | --- |
| **智慧树**（上海卓越睿新，上市）· 高级 Java 开发 | 上海徐汇，985/211 + 3 年，**两周内入职** | 「基于 **Spring AI** 框架构建 Text-to-SQL、RAG 增强、**MCP Server** 等 AI 能力」+ 多数据源接入 / 语义模型 / 知识库 |
| **箱箱共用** · Java 高级工程师 | **20–30k·13 薪**，上海徐汇，5 年+ | 「基于 Function Call / Tools 的 Agent 能力，结合 **MCP 协议、Skills、SubAgent** 构建可扩展智能体编排框架（**Harness**）」+ Spring Cloud / JVM 调优 |
| **电力英才网** · AI 全栈工程师（招 5 人） | 面议，**11-06 更新** | Java 业务系统 + Spring Boot；「业务接口和数据能力**封装为智能体 Tool**」；ChatBI / 本体 / 知识图谱 / **MCP 相关经验优先** |
| **创维** · 算法工程师（AI Agent 方向） | 深圳，硕士，**11-06 发布** | 「熟悉 **MCP**，具有**企业级 MCP 生态规范**，包括工具描述标准化、**权限控制**与错误重试机制开发设计经验者优先」 |
| **周大福珠宝集团**（中国香港）· AI Application Engineer | 中国香港，**11-06 发布** | API 与 **MCP** 知识服务集成 + **permission filtering** + 中/英/繁混合检索 + 访问边界回归测试（与 V1.64 记录的周大福 MCP 共享工具层 JD 同源、不同岗） |

> 与 V1.65 的「通义 / 小米 / 商飞」连起来看：**国内买方要的不是会写 MCP demo 的人，是能把已有 Java 业务系统的权限模型、数据源、审计要求一起搬进 MCP 的人** —— 恰好是本框架的主场。

---

## 6. 💵 接单赛道：Upwork 把「agent 找人 → 合规雇佣」链路补齐

| 事实 | 数字 / 口径 |
| --- | --- |
| **Upwork 子公司 Lifted 收购柏林 WorkMotion（EOR）** | 交易 **2026-10-30** 以现金完成、对价 **$129.7M**，WorkMotion **11-03** 发声明、11-07 深度分析（⚠️ 近 3 天是报道与口径核实，交易本身非近 3 天发生）。Lifted 自有实体 **30+ 国直雇**，contractor / agent-of-record 覆盖 **180+ 国**；此前已买 Bubty（$20.4M, 2025-06）+ Ascen（$40.8M, 2025-08）。WorkMotion 保留独立品牌、柏林总部与 **EU 数据存储** |
| **Q3 AI 技能 GSV 口径交叉验证** | V1.65 记「Q3 单季 $112M、同比 +55%」；11-07 分析给「AI 技能自由职业者 GSV **年化约 $450M**」→ **两者一致（$112M×4≈$448M）**，不是矛盾数据。⚠️ 该口径**不等于 MCP 通道收入**，客户可走传统搜索雇佣 AI 技能人才，不可混算 |
| **平台内置的人工关卡** | Upwork MCP 支持文档要求自由职业者**显式确认** agent 代写的 proposal；企业 offer 审批指引允许配置**最多 4 级人工审批** → 再次印证「HITL 是产品定义」 |
| **Jobs API 结构化选品**（兑现 V1.65 待办⑦） | 第三方 [Upwork Jobs API](https://scrapercompany.com/upwork-jobs-api)（端点与价格 **2026-11-06 核**）：2 credits/成功调用，返回 `type` / `hourly_min|max` / 固定 `budget` / `duration_label` / `skills` / `client_payment_verified` / `applicants|interviews|hired` / `published_at` → 可据此做「预算 ≥$X + 已验证付款 + 竞争人数少」的自动选品，避免把 Connects 烧在 ghost job 上 |
| **费率新细节（11 月复核）** | 自由职业者 **0–15%**（按合同类型浮动，旧 20/10/5% 过时）；额外 Connect **$0.15/个**；Freelancer Plus **$19.99/月**含 100 Connects 且直接合同 0% 费；客户侧 Basic **3–5%**、Business Plus **8–10%**、另有 contract initiation fee；托管 escrow 5 天安全期 |
| **官方 MCP 生态外溢** | Upwork AI Assistant（MCP）Chrome 扩展 **11-06 更新**至 1.2.0，走 Upwork 官方 MCP Server 做岗位发现 / 提案 / 数据分析 |

**报价公式（延续）**：目标到手 ÷ (1 − 实际费率) + Connects 沉没成本；先查 `client_payment_verified` 与 `applicants` 再决定是否投提案。

---

## 7. 🎯 用户的 Java + Spring + AI 组合：调整后的 7 条卖点

| # | 卖点 | 今日新增依据 |
| --- | --- | --- |
| 1 | **跨运行时、跨云的治理签字层** | 四大平台治理全部绑自家控制平面；Gartner MQ 已实证第三方运行时管不住 |
| 2 | **私有化 + 等保 / 信创 / 密评 + 日志不可篡改** | 托管云结构上无法交付；国内招标必选项 |
| 3 | **存量 Java / J2EE 权限模型复用** | 腾讯云「免改造」只透传鉴权，AgentCore 只转 OpenAPI；五条 JD 全要「把已有业务系统封成 Tool」 |
| 4 | **可复现验收，不是 PPT 治理** | 18 类攻击面 → 规则 → 逐条验收；`mcp_security_*` 已在过滤器落地；ROPA 七字段可导出 |
| 5 | **连接路径 / 客户端准入** | `CVE-2026-119025` + 无 CVE 修复通道，现有云网关与工具审批均未覆盖 |
| 6 | **合规工件产能** | ROPA 自动化省 DPO 40–80 小时/年；DPA / 保险 / SOC 2 必索 |
| 7 | **Apache-2.0 源码可交付、可二开** | 云厂商治理多在 Enterprise 付费墙 / 托管服务内；甲方做安全审计与信创适配需要源码 |

---

## 8. 今晚交付（V1.66）

| 类型 | 文件 |
| --- | --- |
| 商务（★ 最有挣钱价值） | [`enterprise-cloud-gateway-comparison-2026-11-08.md`](enterprise-cloud-gateway-comparison-2026-11-08.md)（能力对照 + 5 条买方问答 + 4 条销售动作 + 诚实边界） |
| 情报 / SEO | 本文件、[`blog-java-mcp-cloud-gateway-connect-path-2026-11-08.md`](blog-java-mcp-cloud-gateway-connect-path-2026-11-08.md)、[`V1.66-release-notes.md`](V1.66-release-notes.md) |
| 工程（兑现 V1.65 待办 ④⑤） | `examples/governance-hardening/McpSecurityMetrics.java` + 过滤器接入（**10 个 `mcp_security_*` 指标开箱可用**）；`examples/audit-ropa-export/`（ROPA 七字段 JSON/CSV 导出 + 完整性自检） |
| 工程（今日新增） | `examples/connect-path-guard/`（连接路径防护，6 攻击用例 + 3 对照组断言） |
| 清单 | `enterprise-rfp-checklist.md` 攻击面 **16 → 18 类**；`examples/monitor-alert-rules/README.md` 撤下「契约未落地」标注 |

> ✅ 三个新 Java 示例均**纯 JDK、零依赖**，V1.66 已实测 `javac` 编译通过 + 三个 `main()` 自检全绿（Windows 控制台中文显示为 GBK 乱码，文件本身 UTF-8，不影响交付）。全部位于 `examples/`，**不进 Maven reactor，21 模块主构建零风险**。

---

## 9. 明天（11-09，周一）做什么

1. **广发 11-13 剩 4 天**：把今日「云网关对照 + Q1/Q3 应答」压成一页 PDF 附进报名材料；核实原厂组件授权、金融案例证明、完税凭证、广州本地团队；资质不足立刻联系本地集成商组联合体。
2. 发 SEO 稿抢「**Azure AI Gateway / AgentCore Gateway / 腾讯云 MCP 网关 3.9.8 / MCP Inspector CVE-2026-119025 / 连接路径 RCE**」流量 —— 这批关键词本周刚起量、竞争稿少。
3. 把 Travel Code / Workspace369 的 HITL 逐条规格（首调不执行 / 自批分离 / 审计只存字段名 / 10 分钟一次性码）补进 `enterprise-rfp-checklist.md` 验收列。
4. 招标倒计时：厦大 11-11（2 天）/ 军队 190 万 11-16 / 中山 399 万 11-17 / 山东重工 11-20 / 呼市 900 万 11-23 / 中央民大 3712 万 11-25。
5. 工程候选：把 `McpSecurityMetrics.exportPrometheus()` 真正挂进 `mcp-monitor` 的 actuator 端点（当前仍在 examples 层）；为 `McpConnectPathGuard` 补 DNS 解析后真实 IP 校验防 rebinding。
6. 跟踪 Upwork 是否披露 MCP 通道的 conversion 数据（目前只给了 AI 技能 GSV，未给「agent 发现的岗位 → 付费合同」转化链，投提案时别把两者混算）。

---

## 来源

- Azure AI Gateway 公开预览：https://adaptiv.nz/?p=996227 ｜ AgentCore Gateway：https://dev.to/om_shree_0709/amazon-bedrock-agentcore-gateway-just-became-the-enterprise-mcp-infrastructure-layer-heres-what-m4o
- 腾讯云产品动态 v3.9.8（2026-11-06）：https://cloud.tencent.cn/document/product/1826/134899 ｜ Celigo 11 月版：https://www.celigo.com/builders-hub/celigo-november-2026-release
- 企业标准化横评：https://www.sim.ai/library/enterprise-mcp-server-standardization-platforms ｜ CRM MCP 审批横评：https://workspace369.com/blog/best-crm-mcp-servers ｜ Travel Code 11-06：https://travel-code.com/news/travel-code-mcp-connectors
- `CVE-2026-119025` 分析：https://dev.to/piekwerk/mcp-inspectors-oauth-flow-could-run-powershell-on-windows-inside-cve-2026-119025-and-why-npm-1hei ｜ 一周五洞：https://hangingcontext.com/stories/2026-11-06/ai-supply-chain-flaws-week ｜ opencode 无 CVE：https://deniz.in/opencode-fixed-a-no-consent-mcp-oauth-rce-in-1-18-33-and-no-cve-was-ever-issued ｜ 11-07 Daily Brief（LiteLLM 提权 / EU AI Act）：https://zinbiel.net/daily-ai-engineering-brief-2026-11-07
- 招聘：https://www.zhipin.com/job_detail/61d83aeb91e4963a0ndy09W1GVRQ.html ｜ https://m.liepin.com/job/1977050753.shtml ｜ https://www.epjob88.com/jobs/53710986.html ｜ https://c.460.net.cn/a/491249.html ｜ https://www.thejobsmap.com/job/cef7fd74-b763-47c9-a831-42eaabf54b46
- Upwork / WorkMotion：https://digidai.github.io/2026/11/07/upwork-agents-freelancers-workmotion-deal ｜ https://staffinghub.com/news/staffinghub-brief-staffing-hours-reach-a-new-2026-high-but-engagement-is-at-its-lowest-since-2011 ｜ Jobs API：https://scrapercompany.com/upwork-jobs-api ｜ 费率：https://earninger.com/store/upwork-freelance-marketplace
- 采购平台：https://www.ccgp.gov.cn/cggg/zygg/gkzb/202611/t20261106_27639376.htm ｜ 广发官方征集：http://gfjc.gf.com.cn/gyszjgg/13321.jhtml ｜ http://gfjc.gf.com.cn/gyszjgg/13322.jhtml
