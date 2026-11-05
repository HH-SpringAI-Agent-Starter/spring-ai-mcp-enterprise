# Earnings Report 2026-11-05 — Uber 800+ 网关生产实证 × 官方 Java SDK 自身中招的「全 SDK 级 DoS」× 受治理 MCP 同日双上线 × Upwork Now 20 倍通道

> 抓取窗口：2026-11-03 ~ 2026-11-05（web_search 实时全源）｜ 产出侧：spring-ai-mcp-enterprise **V1.63**
> 代码侧 Task 1–5（Spring AI Alibaba 集成 / 四语言 Client SDK / GitHub Actions CI / Docker 部署 / 中文文档）经结构核查**全部已交付并通过 CI**，本期无破坏性代码变更；核心仍是 Task 6（挣钱情报）+ 开源影响力放大。

## 一、今日最强信号总览

| 等级 | 信号 | 命中模块 | 挣钱含义 |
| --- | --- | --- | --- |
| 🔥🔥🔥 | **Uber 内部 MCP Gateway 生产实证：800+ servers / 5,000+ tools，默认全部禁用待审核启用、按 caller 类型授权** | mcp-gateway / mcp-governance / mcp-registry | 本框架"注册中心+逐工具策略"拿到**超大厂生产级背书**，可直接进标书做架构对标 |
| 🔥🔥🔥 | **全 SDK 级无界请求体 DoS：CVE-2026-108395 命中官方 Java SDK `mcp-core`，且扫描器集体漏报** | mcp-core / mcp-governance / mcp-monitor | **"官方 Java SDK 自身也不安全"** + 规范层空白，治理增强层最强技术话术 |
| 🔥🔥 | **11-05 同日两款「受治理 MCP」上线：ISI Markets（按订阅/数据权限授权）+ Agiloft（CLM，写操作人工确认）** | mcp-auth / mcp-tenant / mcp-governance (HITL) | 高敏数据/合同业务上 MCP，**治理是产品的一部分而非附加项** |
| 🔥🔥 | **Upwork Now 公测：MCP server 上线后周活 ×20，Q3 AI 技能人才 GSV 同比 +55% 至 $112M** | 接单通道 | 平台官方财报数字证明 MCP = 真交易入口，AI 技能在总盘 −10% 下逆势涨价 |
| 🔥🔥 | **国内点名 MCP 的 Java 岗出现 50–80k / 40–70k·15薪 明确报价，金山办公/美团把"存量平台 MCP 化"写进职责** | mcp-spring-ai-tools / mcp-registry | 求职与接单双通道价格锚上移，且需求从"新建"转向"改造存量 Java 资产" |
| 🔥 | **企业网关选型对比清一色非 Java 栈，治理能力普遍锁在 Enterprise 付费墙后** | 全栈 | "唯一 Java/Spring 开源企业级治理栈"差异化再夯实 |
| 🔥 | **重庆 3.4 亿 AI 大单（含智能体及应用场景）+ 中央民大 3712 万（11-25 截）** | mcp-gateway / mcp-governance | 算力/智能体预算持续放量，治理作子项 |

---

## 二、Uber MCP Gateway 生产实证（本期最强护城河背书）🔥🔥🔥

InfoQ 及多家行业媒体 11-02 集中报道 Uber 工程博客（10 月发布，非实验室 Demo，是数月生产硬化结果）：

| Uber 的治理设计 | 原文要点 | 本框架对应 |
| --- | --- | --- |
| **规模** | 内部网关托管 **800+ MCP servers / 5,000+ tools**，统一处理发现、授权、协议转换与执行 | `mcp-registry` + `mcp-gateway` |
| **默认禁用** | 每个 server 和 tool **起始状态为 disabled，须归属团队审核后显式启用**，且与运行时权限检查分离 | `mcp-registry` 准入分级 + `mcp-governance` |
| **按调用者身份授权** | 依 caller 是 **human / service account / autonomous agent** 应用不同的 per-server charter policy 与 per-tool override | `mcp-governance` + `mcp-auth` + `mcp-tenant` |
| **请求路径治理** | 授权与 **PII 脱敏在 request path 上执行**；发现端点是否过滤未授权工具被视为不可含糊的设计细节 | `mcp-governance`（入参脱敏/fail-closed） |
| **增量发现（Omni MCP）** | 四个元工具 `discover_server` / `discover_tools` / `get_tool_schema` / `invoke_tool`，懒加载 schema，避免 5,000 工具定义冲爆上下文 | `mcp-registry`（工具目录/元数据索引） |
| **Response Projection** | 类 GraphQL 字段裁剪，缩小遗留服务大 JSON、省 token、降低幻觉面 | `mcp-gateway` + `mcp-monitor` |
| **组织结论** | "**MCP Gateway 既是协议路由器，也是产品管理平台**"：工具所有者=发布者、安全=审批者、Agent 构建者=消费者 | 本框架治理分层定位 |

**为什么这条最值钱**：此前"治理增强层"多靠 CVE、报告、招聘 JD 佐证；这次是**全球最大规模之一的生产系统把"默认禁用 + 逐工具策略 + 按调用者身份授权 + 审计"写成了标准架构**。可直接用于：① 投标技术方案的"业界对标"章节；② SEO 稿的权威锚点；③ 说服甲方"注册中心不是可选组件，是规模化的前提"。

> 附带的工程提醒（可写进交付话术）：Uber 也点明遗留问题——发现跳数带来延迟叠加、tool schema 与后端版本漂移需 CI 契约测试、可观测性必须能串起 `discover_server → invoke_tool` 才能定位策略错配。这三条恰是 `mcp-monitor` 的验收项。

---

## 三、安全治理护城河：官方 Java SDK 自己也中招 🔥🔥🔥

### 1. 全 SDK 级「无界请求体」DoS（11-03 披露，同一设计缺陷跨语言重演 6~8 次）
- **CVE-2026-108395：官方 Java SDK `mcp-core` 的 HTTP transport 无界读取 request body**（同族还有 Py `CVE-2026-109527`、npm `CVE-2026-39313`、`rmcp` / `mcp-go` / TypeScript SDK）。
- 修复方式高度一致：在 transport 安全层加 **4 MiB 默认上限**（`DEFAULT_MAX_REQUEST_BODY_SIZE` / `DefaultMaxRequestBodyBytes`）；**无需认证、无需凭据、无需用户交互**，一个超长/无限请求即可打挂进程，可脚本化重复攻击，纯可用性打击。
- 规模：TypeScript SDK 约 **2.57 亿次下载/月**、Python SDK 约 **2.35 亿次下载/月**；缺陷在 SDK 层而非单个 server，**任何暴露 HTTP transport 的 server 一出生就继承**。
- 厂商动作：Red Hat 评级 **Important**、评估 **44 个产品**、发布 3 个 errata 并给出机器可读 **VEX**；Debian 标记 **grave**（unstable 当时仍未修）；OpenMetadata / Red Hat Open Data Hub 及数百个 "bump mcp" PR 在 10 月陆续跟进。

### 2. 最刺眼的一点：**开发者侧供应链工具集体看不见这个洞**
- 两条 SDK CVE **只以仓库级 advisory 存在，从未晋升进 GitHub Advisory Database** → `osv-scanner`、`pip-audit`、Dependabot、以及包装 pip-audit 的 MCP 专用扫描器，**对有漏洞的 server 全部返回 clean**。
- 只有 Red Hat / Debian / SUSE 这类吃自家 CNA + NVD 源的企业发行版跟踪器才收录。
- **截至 11-03，MCP 官方 security best-practices 文档仍无 request-body 大小限制指引**；NSA / CISA 的 MCP 信息表也只把资源耗尽 DoS 当一般风险泛泛提及，指向不了具体控制项。规范没写 → 每个 SDK 各修各的、默认值各异。

### 3. 本周其他三连（延续"升级官方 SDK ≠ 安全"）
| 编号 | 问题 | 修复/状态 | 命中模块 |
| --- | --- | --- | --- |
| CVE-2026-115563 / CVE-2026-104850（均 7.5） | TS/Py SDK OAuth 发现未校验 **issuer**，恶意 server 用伪造 metadata 把 client secret / refresh token / authorization code 导向攻击者端点；无人值守流（client credentials / 长命 refresh token）最危险 | Py SDK 1.30.0 / 2.2.0 用 `validate_metadata_issuer()` 先算 expected issuer 再比对 | `mcp-auth` |
| CVE-2026-116635 / GHSA-r6mm-377j-qg8m | **AWS Serverless MCP Server 路径穿越**：影响 MCP agent 者可越目录读 JSON，开写权限则可越界创建/覆写文件 | **仅 v0.2.1 修复，官方明确无 workaround** | `mcp-core`（路径中性化/沙箱） |
| CVE-2025-6514（存量史） | `mcp-remote` **437K+ 下载、CVSS 9.6**，构造 OAuth endpoint 触发 RCE | 历史案例，仍在企业治理指南中被反复引用 | `mcp-auth` + `mcp-core` |

### 4. 规范级背书：AI Agent Governance 三层框架（dev.to 企业实践指南，11 月）
- **三层缺一不可**：① 供应链验证（模型 / MCP server / skill / policy 上线前确认来源可信、过扫描、未篡改）② 运行时执行（策略引擎对**每次工具调用、prompt、response** 做判定）③ 防篡改审计（策略决策、工具调用、审批、内容事件进不可篡改链）。
- **明确的"传统安全层不够用"论证**：IAM 验不了 agent 二进制是否被换、DLP 没有"工具调用/决策链/stdio 本地调用"的原语、**API 网关看不到 MCP 工具参数的语义**、代码扫描查不出权重篡改/注入/数据集后门。Agent 是非确定性、常走 stdio、单会话内可换角色 → 打破了传统栈的全部前提。
- **合规压力清单**：NIST AI RMF、EU AI Act、CMMC L2/3、HIPAA、SR 11-7、21 CFR Part 11 都要求 provenance + 访问控制 + 人类监督 + 防篡改记录；原话 "**'We trust the model provider' is not an audit response**"。
- **已发生的真实事故**（可用于售前）：Postmark 恶意 MCP server 静默 BCC 每封邮件给攻击者；Smithery 平台路径穿越泄露 **3,000+ 托管 MCP server 凭据**；GitHub MCP server 被 prompt injection 把私有仓数据外泄到公开 PR。

> **本框架话术升级**：本期不要再说"我们比官方 SDK 安全"，而要说 **"官方 SDK 修的是协议实现层的洞；即使全修完，规范层仍没定义请求体上限、供应链扫描器仍看不见、逐工具授权与防篡改审计仍不在协议里 —— 这三层只能由治理增强层补"**。CVE-2026-108395 命中 **Java SDK** 是这条话术最锋利的入口（用户技术栈就是 Java）。

---

## 四、11-05 同日两款「受治理 MCP」上线：治理从附加项变成产品定义 🔥🔥

| 产品 | 发布 | 治理设计（原文要点） | 命中模块 |
| --- | --- | --- | --- |
| **ISI Markets MCP Server**（伦敦，金融/宏观数据） | **11-05** | 标题即 "**Governed access**"；访问严格遵循**每个客户已订阅的内容、数据 entitlements 与 AI 使用权**；检索内容附**来源引用**供复核；接入 Claude / ChatGPT / Copilot 及客户自有 agent。规模：4,000 万+ 新闻、年 50 万份研报、4,000 万+ 宏观/另类数据序列、200+ 经济体 | `mcp-auth` + `mcp-tenant` + `mcp-audit` |
| **Agiloft MCP Server**（CLM 合同生命周期） | **11-05** | MCP Server 作为**外部工具访问中央合同库的标准接口**（Claude 为 beta 首个客户端）；明确"在接入可信 AI 体验的同时**保留既有 permissions、controls、security standards**"；Bulk Update Agent 改数据**必须显式人工确认**、先预览受影响文件、再出成功/失败/跳过报告 | `mcp-governance` (HITL) + `mcp-auth` + `mcp-audit` |

**含义**：一个卖数据、一个管合同，都是最敏感的企业资产，同一天把 MCP 对外开放——但两家都不约而同把"按订阅/按权限/写操作人工确认/留痕"写进发布稿。**"只给连接、不给治理"的裸 MCP 在高价值场景已经不成立**。这正好接上 11-03 剑鱼标讯 MCP 品类爆发的判断：垂直 MCP 越多，治理增强层的溢价空间越大。

**对照组（11 月企业网关选型长文）**：Bifrost(Go) / Docker MCP Gateway(MIT) / Microsoft(K8s+APIM) / AWS AgentCore Gateway / Kong 五家横向对比，逐项列 "Tool filtering / OAuth 2.1 + per-tool ACLs / Enterprise audit & RBAC / Open-source"——
- **清一色非 Java 栈** → 本框架"唯一 Java/Spring 开源企业级治理栈"差异化再夯实；
- **RBAC / SSO / HMAC 签名审计 / guardrails 普遍只在 Enterprise tier**（Bifrost Enterprise 明确是 OSS 的严格超集、付费才有）→ **治理就是行业公认的付费墙**，而本框架开源即给；
- Bifrost 的 "virtual keys deny-by-default：无 `mcp_configs` 的 key 拿到 0 个工具" 与 Uber "默认禁用" 是同一个设计共识 → 可并列入标书作第三方佐证。

**中文侧同步印证**：《MCP：从工具连接到企业 AI 治理的落地指南》给出七维治理框架（身份认证 / 权限控制 / 审计日志 / 数据安全 / 限流熔断 / 配置管理 / 可用性监控）与 9 条最小合规基线（工具读写属性标注、写操作单独申请、用户身份透传、**关键工具日志≥180 天**、统一入口网关禁直连、用户级/工具级配额与超时、高危工具人工确认、配置变更走代码评审）→ 与本框架模块几乎一一对应，可直接作验收清单引用。

---

## 五、挣钱通道：Upwork 官方数字 + 乙方真实报价 🔥🔥

### 1. Upwork Now 公测（11-02 发布，GlobeNewswire 11-03 转载）—— 平台侧财报级证据
- **Upwork MCP server**（今年早些时候上线）让客户在 Claude / ChatGPT 等 AI 工具内直接完成雇佣/接单/管理工作；**上线以来周活用户增长 20 倍**。
- 11-02 推出 **Upwork Now** 公测：客户发布 job 时开 "Hire Now" 触达在线可立即开工的自由职业者；自由职业者开 "Work Now" 亮可用性；**典型雇佣 <30 分钟、最快 <10 分钟、首次回复约 5 分钟**；调研 **57% 客户希望 1 天内完成雇佣**。
- **Q3 财报数字（最有说服力）**：AI 技能人才 **GSV 同比 +55% 至 $112M**；总 GSV **−10% 至 $914.1M**、但**每活跃客户 GSV +8% 至 $5,418** → 大盘收缩时，**AI/MCP 相关技能是逆势涨价与集中的方向**。
- 结论：**MCP 不只是开发工具，已是真实交易入口**。对个人而言，"能被 AI 工具直接调用/能被 AI 中介快速匹配"的能力就是报价权。

### 2. 乙方真实报价与交付包（Codigee，11 月在售）
- **单独做一个（基于既有产品 API 的）MCP server：$5,000**；**包含在 MVP / App Launch 里起步 $15,000**；客户保留自有 Claude/ChatGPT 订阅，代码与 server 所有权归客户。
- 其承诺的交付要点几乎就是本框架的治理清单：**每个管理员独立 OAuth**（或 per-admin key）、**按角色分配不同工具集**、**每次写操作进 change journal（记录前后值）可 `list_changes` / `revert_change` 撤销**、**破坏性动作强制人工确认**、**导入先 preview 再写入**、模型不直连数据库。
- **可直接抄的报价锚**：$5k 裸连接 / $15k 起带产品化 / 历史区间 $8K–25K（含 auth）/ $35K–90K（多系统写）/ 受监管行业六位数。本框架的差异化 = **用开源治理底座把"$15k 才能买到的治理"变成开箱即得**，报价可锚在"含 auth + 审计 + 多租户"档位。

### 3. 自由市场费率复核（延续）
Upwork 0–15% / Fiverr 20% / Freelancer 10% / Contra·SynapticRelay 0%；UpHunt 指南 AI Agent / MCP 实现 **$150–300/hr**，2026 增长最快 Upwork 品类。历史提醒仍有效：**冷提案负期望，invites / repeat 客户才是有效渠道**（HighCircl）。

---

## 六、招聘：国内出现 50–80k 明确报价，需求转向"存量 Java 平台 MCP 化" 🔥🔥

| 企业 / 岗位 | 地点 · 薪资 · 时间 | JD 原文要点 | 命中模块 |
| --- | --- | --- | --- |
| **建发集团 · AI 应用架构师**（猎聘） | 厦门 · **50–80k** · **11-01 更新** | 统筹集团 AI 应用架构、建统一底座避免各业务线重复造轮子；要求 "**RAG、Skill 开发、MCP 开发**及主流智能体框架" + "**懂 Java 语法、Spring Boot/Cloud**、数据库表设计与 SQL" | 全栈 + `mcp-registry` |
| **Harness 框架设计/开发（云侧）**（猎聘，整车制造） | 上海 · **40–70k·15薪** · 10-23 更新 | 主导云侧 Harness：模型接入层 / **Skill 注册中心 / Tool 工具网关 / 协议适配层(MCP·A2A·A2UI)** / Agent 编排；"主导云端协议标准化：**MCP 工具注册**、A2A 通信"；配额限流、多模型路由、**隐私与权限边界**、链路追踪；要求精通 **Java/Kotlin（Spring 系）** | `mcp-registry` + `mcp-gateway` + `mcp-governance` + `mcp-monitor` |
| **金山办公 · 后端研发专家（AI 研发效能组）**（BOSS直聘） | 武汉 · 5–10 年 · 在招 | "**推进现有平台能力向 MCP Server 的标准化封装**，为 Agent 提供稳定可靠的工具调用与数据对接能力"；Java / SpringCloud / MySQL / 分布式 / 微服务 | `mcp-registry` + `mcp-spring-ai-tools` |
| **美团 · AI Agent 开发工程师**（智联） | 上海 · 1–3 年 · 在招 | CatPaw Managed Agent 平台：工具调用（Function Call / **MCP**）、"**MCP Server 的接入与管理、工具沙箱与权限管控**"、SubAgent 生命周期、Workflow 确定性编排、RRULE 定时任务与断点续跑；栈含 **Java/Spring Boot** | `mcp-governance`（沙箱/权限） + `mcp-monitor` |
| **珠宝通 · AI 项目经理**（BOSS直聘） | 长沙 · 在招 | Java / Spring Boot / Vue 全栈 + 智能体平台搭建、AI 模型调优 | `mcp-spring-ai-tools` |

**结构性变化（比薪资更重要）**：金山办公与美团写的都不是"从零搭一个 MCP Server"，而是 **"把已有平台能力标准化封装成 MCP / 对 MCP Server 做接入、管理与沙箱管控"**。这与 11-03 Digital Dhara "design MCP servers to connect various nodes of J2EE" 完全同构 → **"企业遗留 Java 资产安全接入 agentic 工作流"已经从海外个案变成国内一线厂商的正式岗位职责**。本框架 `mcp-registry`（统一注册/协议适配）+ `mcp-governance`（准入/沙箱/权限）正是这条路的主场。

**社区信号**：CSDN DevPress 新文《手写 MCP Server，让大模型调用 Java 工具》用 **Spring AI 2.0 `@McpTool` + JSON Schema 自动生成 + MCP Inspector 验证**，核心痛点是"同一查订单方法要为 Claude / Cursor / 自家 Agent 各写一遍胶水代码" → 中文 Java 社区对 MCP 教程的需求仍在放量，`mcp-examples` 是现成引流入口。

---

## 七、国内预算与招标追踪（治理作子项）

| 项目 | 金额 / 时间 | 与 MCP 治理的关系 |
| --- | --- | --- |
| **重庆 AI 大单（算力基础设施 + 行业模型 + 高质量数据集 + 智能体及应用场景建设）** | **3.4412 亿元**，11-03 中标候选公示；重通院 + 中移系统集成 + 科大讯飞 + 中智股份联合体（第二 3.455 亿 / 第三为重庆高新智算） | 招标范围明确含 **"智能体及应用场景建设"** → Agent 工具接入/治理是子项；延续移动 116.66 亿算力巨单主线 |
| **中央民族大学 智算集群与多模态大模型采购**（GC-HGX261278） | **3712 万元**，**11-25 09:00 截标**（国 e 采，需 CA 数字证书） | 采购需求含 "**民族语言多模态大模型智能体**"、算力和模型优化组件；高校智能体平台接力 |
| **上海外国语大学 大模型平台设备采购一期** | **589.28 万元**，11-04 中标公告，北京华通安和（浪潮算力/管理服务器） | 高校大模型平台基线持续放量 |
| **倒计时提醒** | 厦大 **11-11 截**（剩 6 天）/ 军队大模型安全防护 190 万 **11-16 截** / 中山 399 万 **11-17 截** / 某单位 441 万 **11-19 截** / 广发证券正式招标挂网待跟踪 | 备标素材统一为「治理 + 网关 + 等保三级 + 防篡改留痕」 |

---

## 八、用户 Java + Spring + AI 组合的卖点（本期升级版）

1. **"官方 SDK 自己也有洞，且洞在规范层"** — CVE-2026-108395 命中**官方 Java SDK `mcp-core`**，加上 best-practices 无 body 上限指引、两条 SDK CVE 未进 Advisory DB 导致扫描器全盲 → 用户是 Java 栈，能一眼看懂并讲清这个技术差异，这是最锋利的售前切入。
2. **超大厂生产架构对标** — Uber 800+/5000+ 的"默认禁用 + 逐工具 charter + 按 caller 授权 + PII 请求路径脱敏"，本框架 `mcp-registry`/`mcp-governance`/`mcp-tenant` 逐条对得上，标书里可以写"与业界最大规模生产实践同构"。
3. **治理是行业付费墙，本框架开源即给** — 五家网关对比里 RBAC/SSO/HMAC 审计/guardrails 全在 Enterprise tier；ISI / Agiloft 把治理写进产品定义。 ⇒ 开源治理底座 = 差异化定价权。
4. **适配买方最痛的"存量改造"** — 金山/美团/Digital Dhara 三条 JD 说明需求是"把已有 Java 平台安全接给 Agent"，不是重写；Java + Spring Cloud + 统一注册/协议适配是天然主场（Go/TS 网关要跨栈改造）。
5. **信创 + 私有化 + 等保三级** — 太平金科、湖北疾控、军队标连续验证，Java 栈在国产化基座上无障碍；`mcp-audit` 防篡改留痕对齐"日志≥180 天 / 不可篡改"验收项。
6. **报价锚清晰** — 求职 50–80k / 40–70k·15薪 国内明确报价；接单 $5k 裸连接 / $15k 起产品化 / $8K–25K 含 auth / $35K–90K 多系统写；平台侧 Upwork AI 技能 GSV +55% 证明需求侧在涨。
7. **作品集即交付包** — 21 模块 + 四语言 Client SDK + CI/Docker + 中文文档，可直接应答广发证券"MCP 五件套"资质门槛与乙方自检清单（live URL / 工具清单 / 读写分离 / OAuth / spec / 限流日志 / 所有权）。

---

## 九、可直接进标书的治理合规对照表（本期新增·可复用）

| 合规/验收要求 | 来源 | 本框架模块 | 可举证材料 |
| --- | --- | --- | --- |
| 工具/server **默认禁用、审核后启用** | Uber 生产实践、Bifrost deny-by-default | `mcp-registry` + `mcp-governance` | 注册中心准入分级、工具状态机 |
| **按调用者身份**（人/服务账号/自治 Agent）差异化授权 | Uber charter policy | `mcp-auth` + `mcp-tenant` | RBAC / Scope / 租户隔离 |
| **逐工具读写分离、写操作单独申请** | 中文治理指南 9 条基线、广发征集 | `mcp-governance` | Scope ACL、工具分级 |
| **高危动作强制人工确认（HITL）** | Agiloft、Codigee、21 CFR Part 11 | `mcp-governance` | HITL 审批流（V1.26） |
| **防篡改审计 + 关键日志 ≥180 天** | 广发"日志不可篡改"、NIST/EU AI Act | `mcp-audit` + `mcp-monitor` | 审计日志链、SIEM 导出 |
| **请求体大小限制 / 超时 / 配额 / 熔断** | CVE-2026-108395（4 MiB 共识） | `mcp-core` + `mcp-governance` + `mcp-monitor` | RateLimit、入参校验、fail-closed |
| **issuer / audience 绑定，凭据不出网** | CVE-2026-115563 / 104850 / 116684 | `mcp-auth` + `mcp-gateway`（egress 白名单） | OAuth 校验、出网隔离 |
| **入参/出参脱敏、PII 不进上下文** | Uber request-path redaction、指南 | `mcp-governance` | 参数过滤、Response Projection 思路 |
| **统一入口网关，禁止绕过直连** | 中文治理基线、MCPHub CVE-2026-120323 | `mcp-gateway` + `mcp-registry` | 单一入口、server 准入分级 |
| **供应链验证（来源/扫描/未篡改）** | AI Agent Governance 三层框架 | `mcp-registry` + `mcp-governance` | 签名准入、版本/变更评审 |
| **配置变更走代码评审、灰度、版本化** | 中文治理基线 | `mcp-registry` + `mcp-monitor` | 配置版本化、契约测试 |
| **协议实现层之外的"规范空白"兜底** | 官方 best-practices 无 body 上限、扫描器盲区 | 治理增强层整体 | 本框架 = 协议实现之外的独立控制平面 |

---

## 十、明天做什么（候选）

1. **发 SEO 稿**抢「Uber MCP Gateway 800 servers / CVE-2026-108395 Java SDK / MCP 请求体 4MiB / Upwork Now / 建发 50-80k MCP 岗」长尾流量。
2. **把第九节对照表抽成独立单页**（`docs/enterprise-rfp-checklist.md` 已有基础，可追加"2026-11 版治理验收矩阵"），作为发给集成商/甲方的售前一页纸。
3. **把 CVE-2026-108395 的 4 MiB 上限与 fail-closed 检查项**补进 `docs/mcp-security-hardening-quickstart.md` 与 `docs/mcp-governance-regulatory-compliance-2026.md`（避免只做情报、不落产品文档）。
4. **备标倒计时**：厦大 11-11（6 天）/ 军队 190 万 11-16 / 中山 399 万 11-17；跟踪广发证券正式招标挂网。
5. **可选工程项（验证后再动）**：给 `mcp-registry` 补一份「default-disabled + 按 caller 类型授权」的最小可运行示例，直接对标 Uber 架构做引流 Demo。
6. 确认 `git push origin main` 正常（V1.62 已固化 remote username + GCM 非交互修复，本期沿用）。

---

## 附：本期主要来源

- Uber MCP Gateway / Omni MCP 增量发现（11-02 报道）：https://plainenglish.io/software-development/uber-mcp-gateway-omni-mcp-incremental-discovery
- 全 SDK 无界请求体 DoS / 扫描器盲区（11-03）：https://wpnews.pro/news/the-first-of-its-kind-how-one-unbounded-request-body-became-a-denial-of-service
- AI Agent Governance 企业实践三层框架：https://dev.to/jwilliamsr/ai-agent-governance-a-practical-guide-for-enterprise-teams-1p1d
- 官方 SDK OAuth 双 CVE 修复解析：https://developmentcurated.com/testing-and-security/official-sdks-fix-oauth-vulnerabilities-in-mcp-servers
- AWS Serverless MCP Server 路径穿越 CVE-2026-116635：https://www.xcademia.com/news/aws-discloses-path-traversal-vulnerability-in-serverless-mcp-server-urges-upgrade-to-v0-2-1
- ISI Markets MCP Server（11-05）：https://isimarkets.com/insights/isi-launches-mcp-server-to-bring-trusted-data-and-intelligence-into-clients-ai-workflows
- Agiloft MCP Server（11-05）：https://www.agiloft.com/news/execution-focused-ai-agents
- Upwork Now 公测 / Q3 数字（11-02 发布，11-03 稿）：https://www.manilatimes.net/2026/11/03/tmt-newswire/globenewswire/upwork-introduces-upwork-now-to-help-clients-hire-experts-in-minutes/2459951 ；https://dailyaibrief.com/news/upwork-launches-upwork-now-ai-agents-clients-iFtQKko4
- 企业 MCP 网关选型与成本对比：https://dev.to/anthonymax/enterprise-mcp-gateway-solutions-providers-alternatives-and-cost-34kc
- 中文治理落地指南（七维框架 + 9 条基线）：http://www.sheratonhq.com/news/195655
- 乙方 MCP 交付与 $5k/$15k 报价：https://codigee.com/blog/custom-mcp-server-admin-panel
- 招聘：建发 AI 应用架构师 https://m.liepin.com/a/81267979.shtml ｜ Harness 框架 https://www.liepin.com/a/81039593.shtml ｜ 金山办公 https://www.zhipin.com/job_detail/aaa28b125d5e09c00nZ_3d-_EFtT.html ｜ 美团 https://www.zhaopin.com/jobdetail/CC383625320J40896165109.htm
- 招标：重庆 3.4 亿 https://finance.sina.com.cn/roll/2026-11-04/doc-inixqaau9399187.shtml ｜ 中央民大 3712 万 https://www.zycg.gov.cn/freecms/site/zygjjgzfcgzx/ggxx/info/2026/3e0052a6-9537-48e3-8d67-eff376c93c59.html ｜ 上外 589.28 万 https://www.ccgp.gov.cn/cggg/zygg/zbgg/202611/t20261104_27621233.htm
- Java 社区教程：https://devpress.csdn.net/aibjcy/6ae952bce5f4ca4eff8e140d.html
