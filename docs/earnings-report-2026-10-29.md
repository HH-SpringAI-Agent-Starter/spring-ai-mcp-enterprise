# 💰 收益 / 招标 / 招聘情报日报 — 2026-10-29（V1.56）

> 数据来源：WebSearch 实时抓取（2026-10-26 ~ 2026-10-29），含 ZC26G280436 中标结果、10-27 官方 CVE 批量公告、CSA/NHI/TrueFoundry 治理分析、Moss/猎聘/字节/广电招聘、Upwork 接单范式。
> 本框架定位：**Spring AI / Java 生产级 MCP「治理增强层」**——官方 Nacos/Higress/Spring AI Alibaba 给「注册+路由+协议转换」，本框架补 **RBAC / HITL / 审计 / 多租户 / 限流 / 等保** 治理空白。

---

## 🔥 头条一：ZC26G280436 已开标 — 央企「MCP 注册中心」最贵标 683 万落定（10-27）

- **招标编号 ZC26G280436「AI网关、MCP注册、多模态平台」中标结果公告（2026-10-27 14:03，中招联合）**
  - **中标人：西安志诚电子有限责任公司**
  - **中标价格：683 万元（人民币）**
  - 招标人：中国航空工业集团公司西安飞行自动控制研究所；监督：中国航空工业集团
- **意义（对本框架 = 最强「报价锚 + 中标验证」）**：
  - 这是目前公开可查**金额最高的「MCP 注册中心 + AI 网关 + 多模态」央企标**，直接证明「MCP 注册中心」已从概念进入 **683 万级预算**的央企生产采购。
  - 建设内容（AI 网关 + MCP 注册 + 多模态平台 + 2 台智算）完美映射 `mcp-gateway` + `mcp-registry` + `mcp-alibaba` + `mcp-governance`。
  - 本仓库已封板全套投标资产，可直接复用为**同类标的（军工/央企 AI 网关+MCP 注册）技术方案底稿**：
    - 投标包索引 → `docs/zc26g280436-bid-package-2026-10-17.md`
    - 技术方案模板 → `docs/tender-proposal-ZC26G280436-2026-10-03.md`
    - 投标响应正文 → `docs/zc26g280436-bid-response-2026-10-20.md`
    - 等保对照 → `docs/mcp-governance-dengbao-compliance.md`
    - 监管合规映射 → `docs/mcp-governance-regulatory-compliance-2026.md`
    - 安全事件简报 → `docs/mcp-security-incident-brief-2026-10.md`
  - 🆕 **本次新增《ZC26G280436 中标拆解与报价锚定》** → `docs/zc26g280436-win-analysis-2026-10-29.md`：683 万拆解 + 我方框架逐模块对标 + 后续同类标报价策略 + 乙方自检 7 问话术。

---

## 🔥 头条二：10-27 官方批量 CVE — 9+ MCP 工具服务「命令注入」波（最强护城河新弹药）

- **GitHub Security Advisories（2026-10-27 集中发布）**：约 9 个 MCP 工具服务存在 **OS 命令注入**，agent 可调用的工具参数被直接拼进 shell 命令（`execSync`/`run_shell`/CLI flag），**未经 shell 元字符中性化**，prompt-injection 即可驱动任意命令执行：
  - `CVE-2026-117558` ai-sdlc ≤0.31.2｜`CVE-2026-117559` DroidMind ≤0.4.0（allowlist bypass）｜`CVE-2026-117560` ATXP ≤1.0.0｜`CVE-2026-117561` sf-mcp ≤1.3.2｜`CVE-2026-117567` esp-mcp｜`CVE-2026-117568` mcp-ffmpeg ≤1.0.5｜`CVE-2026-117569` Kali-GPT｜`CVE-2026-117570` nmap-mcp｜`CVE-2026-117573` hagent
  - 攻击面：**MCP tool server = agent 的双手，多数不把工具参数当不可信输入**。
- **→ 直接命中本框架 `mcp-governance`**：
  - 工具参数 **语义级校验 / 元字符 allowlist / 危险命令拦截**（T0–T4 风险分级 + deny-tiers fail-closed）
  - **沙箱化执行 + 进程最小权限**（mcp-core 资源隔离）
  - 审计落库（JDBC）+ 链路追踪（traceId），事后可取证「是谁的 prompt 触发了这条 shell」
- 可进投标/审计报告，接续 `docs/mcp-security-incident-brief-2026-10.md`。

---

## 🔥 头条三：治理成「身份/控制平面」问题（NHI / CSA / TrueFoundry 三方同日背书，10-27）

- **NHI Mgmt Group × Willow（10-27）—「10 MCP Security Risks」**：MCP 已成 AI agent 连企业工具默认方式，但协议**不带内建认证**、把执行丢给实现者，已产生 **6 个 critical CVE + 43% server 命令注入脆弱** → MCP 治理是**身份与控制平面问题**，非单纯应用集成。护城河话术：集中授权策略 + 密钥不落配置文件 + 逐字段校验工具 schema。
- **CSA（云安全联盟）× Akto 分析（10-27）—「你的 CLI/IDE 是新攻击面」**：AI coding agent 以开发者身份跑、持开发者凭据、听它读到的任何指令。治理模型 = **inventory 每个 agent/extension/skill/MCP server + approved/pinned MCP registry + least-agency（OWASP agentic）+ 记录每次工具调用 + 单一 Owner**。→ 命中 `mcp-registry`（pinned/approved 注册中心）+ `mcp-audit`（逐调用日志）。
- **TrueFoundry 博客（10-27）—「Claude Enterprise Security」**：企业需**集中式 AI Gateway + MCP Gateway** 作为跨团队统一执行层（策略/工具治理/追踪/监控）。→ 命中 `mcp-gateway` + `mcp-governance`「集中治理执行层」定位。

---

## 🏢 招聘（清一色 Java + MCP，10-26 ~ 10-29 新出）

| 公司 / 岗位 | 地点 | 薪资 | MCP / Java 信号 |
|---|---|---|---|
| **Moss**（欧洲 fintech 独角兽，€35M C 轮、估值破 €10 亿）· Applied AI Engineer | Remote（华沙/柏林/伦敦/慕尼黑） | — | 10-27 发；JD 原话 **RAG, MCP, knowledge graphs**；Python **and/or Java**；金融 workflow agent 端到端 owner |
| **猎聘 · AI 架构/AI 原生/AI 服务端研发负责人** | 上海/电商 | **90-120k·16薪** | 10y+ Java；精通 RAG/Agent/Function Call/向量库/**MCP**/多Agent；**自建或参与开源 AI 工程（Agent / MCP Server）优先**；1 小时内在线 |
| **猎聘 · AI 服务端研发负责人** | 上海/咨询 | **100-160k·16薪** | 同上 MCP 要求 + 30+ 人团队管理 |
| **字节跳动 · 火山方舟 LLM Backend Engineer（2027 校招）** | 圣何塞 | **$128K–256K** | 理解 Agent/Function Calling/**MCP**；Java/C++/Python；云原生/限流/降级 |
| **广东广电网络 · AI 平台研发 / Agent 应用开发实习生** | 广州 | 实习 | **MCP Server**、Java/Python；Agent/Tool Calling/Multi-Agent；10-14~10-23 报名 |

> 持续在招（历史已记）：JPMC / BOFA / NVIDIA / EPAM / 上交所 / 申万宏源 / 建信 / 阿里云 / 美团 / 千问 / 沃尔玛 / 实习僧 / Capgemini Polska / Cayuse / Inabia。

**卖点话术（可直接发简历/Proposal）**：「我用 **Java 17 + Spring Boot 3 + Spring AI Alibaba** 交付过生产级 MCP Server 与治理网关，覆盖 RBAC / HITL 审批 / JDBC 审计 / 多租户隔离 / 限流——本仓库即作品集（21 模块、治理测试全绿）。」

---

## 💸 接单 / Upwork（10-29 新范式）

- **HighCircl（10-29）—「Upwork 2026 还值不值？冷提案不灵」**：对资深自由职业者，**只有 invites + repeat clients 才有效**；冷提案是负期望。服务费 0%–15%，Connects $0.15/个，boost 拍卖不建议参与。→ 启示：**用 GitHub 仓库作品集 + 行业垂直（金融/军工）拿 invite，而非海投**。
- **Dawloom —「谁在造定制 MCP Server？问他们 7 个问题」**（买家自检清单，本框架即答案）：
  1. **一个可连的 live server URL**（本仓库 examples/ 即 live proof）
  2. **先给工具清单再写码**（tool name/input/error 一页纸）
  3. **只读/写工具分离**（readOnlyHint / destructiveHint 标签）
  4. **OAuth + 专属 token**
  5. **说得出 spec 版本（2026-07-28）+ 老客户端兼容**
  6. **限流与日志策略**
  7. **域名/repo/托管/目录 listing 全在你名下**
- **Upwork MCP 成 agent 获客前端**：`mcp.upwork.com` + Robauto `https://mcp.robauto.ai`（品牌解析层 MCP endpoint）。
- **报价梯度（沿用 10-28 Reflekt Lab 实测）**：scoping $2.3K–4.7K / 首 workflow $17.5K+ / 简单单工具 $3K–8K / 含 auth $8K–25K+ / 多系统写 $35K–90K / 受监管六位数。

---

## 🌐 市场 / Registry 信号

- **Open Contracting MCP Server（46 tools，hosted，official registry）** + **Brazil Pncp MCP Server（44 tools，hosted+本地）**：政府采购数据（招标/授标）已做成 **hosted MCP server** 上线官方 registry（pipeworx 网关）。→ MCP 正从「内部工具」走向「公开数据服务基础设施」。
- **Spring AI Alibaba 2.0 + Nacos 3.1.0 A2A 注册** 持续巩固「注册+路由」官方底座，治理空白 = 本框架「治理增强层」再坐实。

---

## 📌 本框架可立即复用的「挣钱钩子」

1. **ZC26G280436 683 万中标 = 报价锚**：同类军工/央企「AI 网关+MCP 注册」标报价可锚定 600–800 万区间；本仓库投标资产全套可改头换面复用。
2. **10-27 命令注入 CVE 波 = 最新安全弹药**：进标书「安全合规证明」与审计报告，证明「升级官方 SDK ≠ 安全」。
3. **乙方自检 7 问 = 获客话术**：用 GitHub 仓库 + examples/ live server 直接通过 Dawloom 七问，拿金融/央企 invite。
4. **金融 MCP 治理矩阵**已成形（天风 1800 万 / 中原 / 上海农商 / 人保资产 / 金融电子化），人保资产 10-30 截标仍在窗口。

---

## 🔗 来源

- 中招联合 ZC26G280436 中标结果公告（2026-10-27）
- GitHub Advisory / NVD：CVE-2026-117558~117573（2026-10-27）
- NHI Mgmt Group「10 MCP Security Risks」（2026-10-27）｜ CSA×Akto 端点治理分析（2026-10-27）｜ TrueFoundry Claude Enterprise Security（2026-10-27）
- Moss / 猎聘 / 字节跳动 / 广东广电网络 招聘页（2026-10-26~29）
- HighCircl「Is Upwork still worth it 2026」｜ Dawloom「Who builds custom MCP servers」｜ Electra Index Open Contracting / Brazil Pncp MCP
