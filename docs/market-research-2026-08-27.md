# 市场调研 2026-08-27 — MCP 企业需求 / 招聘 / 报价雷达（周四）

> 调研时间：2026-08-27 21:30 | 范围：最近 3-7 天重点 + 平台侧信号 | 方法：web_search 一手数据

## 一、核心信号：MCP 岗位从「加分项」全面进入「平台岗」，Java 存量改造单成为主流

### 1. 新增/活跃岗位（本周重点，均为 Java/Spring 匹配）

| 企业/渠道 | 岗位 | 薪酬 | 与我们的匹配点 |
|------|------|------|----------------|
| **Sumo Logic**（Redwood City / 远程） | Staff SWE — Core AI Platform (MCP & Agent Infrastructure) | **$207-243K/年 + Equity** | 构建 MCP-first 平台：**多租户隔离 + OAuth/token exchange + 工具注册表 + 限流配额 + 可观测性**——JD 每条要求与本框架逐条对上（V1.11 Row + V1.12 Schema 双模式） |
| **OneSeven Tech**（远程，US 客户） | Senior Backend Engineer — MCP Infrastructure | **$4,000-5,000/月**（6 个月可延期合同） | Java + SQL Server 存量上建 MCP 层；要求生产级 tool-use/agent 编排——mcp-server + tool-http/tool-database 直接可交付 |
| **Exerizon**（华沙，100% 远程 B2B） | Mid-level Java Engineer (AI Agents, MCP) | B2B 合同（15-20h/周起），未公开 | 为全球保险公司建 **Spring AI MCP 集成** 的生产级 MCP Server；Java 17+/Spring Boot/WebFlux/JSON-RPC 2.0+SSE——与 mcp-server 传输层同构 |
| **Sumsub**（全球远程） | AI / MCP Senior Backend Engineer (Java, Product-Focused) | 未公开（对标市场 $150K+） | Java 17 + 把 AI/MCP 能力嵌入验证/合规产品——tool-finance/合规日历场景同赛道 |
| **Mastercard**（印度 Pune） | Senior SWE（Java full stack, mcp servers） | 未公开（大厂带宽） | Java/Spring Boot 存量 + MCP server + 流处理——大厂 Java MCP 化实证 |
| **EPAM Systems**（印度/全球） | Lead Java Engineer — AI Native | 未公开（Lead 级） | 设计/部署 **MCP server 生态（安全控制、版本化、可观测性）+ agentic SDLC 流水线**，要求生产级而非 PoC |
| **Ampstek**（阿姆斯特丹，混合） | Senior Java Developer (MCP, Agentic AI) | 合同制，未公开 | Java17+/Spring Boot/Azure + MCP agentic 集成，企业治理/标准语境 |
| **日新軟體**（台北） | Software Engineer (Java / AI Solution) | 面议（6-10 人应征） | 开发维护 MCP Server + LLM 数据交换，Docker 化部署 + 单测——框架开箱即用 |
| **Insight Global**（Charlotte NC，合同） | AI Full Stack Java/Angular — MCP servers | **$43-54/hr**（≈$89-112K/年） | Java 25 + Spring Boot 3.x + MCP server/RAG；明确「只要真做过 AI-ready 应用的人」 |

### 2. 平台侧信号（本周最重要）

- **Upwork 官方 MCP Server 上线**（2026-08-10 发布，dev.to 08-16 详析）：Claude/ChatGPT/Cursor 内可直接「发布职位、筛选人才、起草 offer、汇总 proposal」。MCP 进入**人才市场基础设施层**——对我们是双重利好：(a) 我们自己的变现通道（Upwork 上 MCP 岗位可通过官方 MCP 直接自动化跟单）；(b) 我们的框架可成为「企业接入 AI 招聘/HR 系统」的样板案例。
- Freelancer.com 已开设 MCP 专家 hire 页（明确列出评估标准：懂协议、提安全风险、有公开 GitHub MCP 作品）——**「公开 GitHub 作品」被平台列为筛选条件，本仓库就是现成门面**。

### 3. 薪酬带（延续 08-26，新增两个数据点）

| 角色 | 区间 | 来源 |
|------|------|------|
| Staff SWE — MCP 平台（多租户/安全深度） | $207-243K + Equity | Sumo Logic 2026-08 |
| Senior MCP 基础设施（远程合同） | $4,000-5,000/月 | OneSeven Tech |
| MCP 合同（美国 onsite） | $43-54/hr | Insight Global |
| Senior MCP Engineer | $175-220K | secondtalent（延续） |
| MCP 平台开发（国内） | 40-60K/月 | 猎聘 08-26（延续） |

## 二、趋势解读（本周更新）

1. **「存量 Java 上建 MCP 层」成为主流单**：Exerizon（保险）、OneSeven（SQL Server 存量）、Mastercard、EPAM 四单都是「现有 Java/企业系统 + MCP 化」——比「从零搭 MCP」更常见，且恰好是 Java 工程师的舒适区，**议价权在 Java 存量经验一边**；
2. **安全/多租户/可观测性被写进 JD 的比例陡增**：Sumo Logic 的 Staff 岗把 multi-tenant isolation、token exchange、rate limit、observability 全部列为必须——「治理能力」成为高级岗分水岭，我们的 framework = 简历上的可演示证据；
3. **自由职业通道官方化**：Upwork MCP Server 让「AI 在对话里替我招人」，意味着 MCP 人才的可被发现性提升，但也意味着简历要能被 AI 解析——**README 结构化、keywords 覆盖、GitHub 公开作品**现在直接进入 AI 筛选管道；
4. **国内平台岗需求坚挺**：阿里 TRE / 诺亚（08-26 雷达）仍在开放，台湾日新軟體新增 MCP Server 岗（Docker + 单测 + 技术文档）——华语区 Java MCP 供给稀缺依旧。

## 三、用户 Java+Spring+AI 卖点（本周版）

> 「我开源的 **spring-ai-mcp-enterprise** 是 Java 生态首个生产级企业 MCP Server 框架：RBAC/OAuth2/EMA 集中授权/审计/限流/REST API + **Row 与 Schema 双模式多租户隔离**（V1.11+V1.12）+ Spring AI Alibaba 原生兼容 + 15+ 工具模板。正在招聘 MCP 平台岗的企业（Sumo Logic $207-243K、阿里系 40-64万/年、EPAM Lead、OneSeven 远程合同）JD 里的每一条硬技能，都能在这个开源仓库里被直接验证——附 CI 绿标与 26 个单测。」

## 四、落地行动（本周建议）

1. **投标/简历话术更新**：把 V1.12 Schema 隔离写入 mcp-freelance-offer.md 与 RFP checklist（docs/），对标 Sumo/EPAM JD 逐条勾选；
2. **Upwork 官方 MCP 注册**：用 Upwork MCP Server 建立「Java MCP」关键词的岗位监控（AI 自动跟单），观察 1 周内的真实需求密度与报价区间；
3. **README 结构化优化**（为 AI 简历筛选）：Features 区增加 keywords（multi-tenant isolation / OAuth2 / observability / SSRF protection），确保 AI 检索命中；
4. **V1.13 实例级隔离**预研（每租户独立 DataSource/连接池），补齐三档隔离故事线。详见 [V1.12-release-notes.md](V1.12-release-notes.md)。