# 市场雷达 2026-08-28 — MCP 人才/需求/价格

> 数据源：web_search（yuanbao，freshness=week，中英文双语检索）| 统计窗口：2026-08-21 ~ 08-28

## 一、招聘信号（谁在招、什么价）

### 海外（英文源）

| 公司/来源 | 岗位 | 薪资/时薪 | 关键要求（与本框架的对应） |
|-----------|------|-----------|------------------------------|
| **Exerizon**（华沙，100% 远程 B2B，保险客户） | Mid-level Java Engineer (AI Agents, MCP) | B2B 时薪制（未公开，长期合同） | Java17 + Spring Boot + **Spring AI MCP 集成** + JSON-RPC 2.0 + SSE/WebFlux —— mcp-server 传输层直接对口 |
| **EPAM**（印度 Coimbatore/Bangalore/Pune/Hyderabad 多城） | Lead / Senior Java Engineer – AI Native | 未公开（EPAM 惯例中等偏上） | 生产级 MCP server 生态（安全/版本化/可观测性）+ agentic SDLC 流水线 + Spring Boot/Cloud —— 逐条命中 mcp-core/mcp-monitor |
| **Sumo Logic**（Staff SWE，08-17 再验证） | Core AI Platform (MCP & Agent Infrastructure) | **$207K-243K/年 + Equity** | multi-tenant isolation + OAuth/token exchange + 工具注册表 + 限流/配额 + 可观测性 —— V1.11/V1.12 双模式命中 |
| **Yoh**（US，remote contract） | Senior AI Platform Engineer（Agentic AI & MCP） | **$75-90/hr**（已关闭，作为价格参考） | Java+Python + MCP 协议 + 生产级 agent 平台 |
| **Kenfont**（O'Fallon MO） | Agentic AI Lead Engineer | 长期合同（已过期 08-16） | Java + REST + Angular/React + LLM + MCP |

### 国内（中文源）

| 公司 | 岗位 | 地点 | 关键要求 |
|------|------|------|----------|
| **网易（智企·云商）** | 资深 Agent 平台全栈工程师（08-24 更新） | 杭州 | Java/Spring Boot + Agent 平台 + 工具调用 + WebSocket/SSE + **MCP 概念** |
| **阿里（企业智能事业部）** | AI Agent 开发工程师-网络产品 | 杭州 | Java/Python + Spring/JVM + **MCP 协议 + LangGraph/CrewAI + RAG** |
| **国内某大厂系（recruit.net 聚合）** | Java 后端 Leader | —— | 5-8 年 Java，**MCP/RAG/Spring AI/Spring AI Alibaba/AgentScope** 明确写入 JD —— Spring AI Alibaba 已进主流 JD |

**要点**：国内 JD 已把 `Spring AI Alibaba` 与 MCP 并列列为技能项——mcp-integrations/mcp-alibaba 模块的营销价值被验证。

## 二、外包/项目价格基准（卖框架的定价锚）

| 来源 | 交付物 | 价格 | 周期 |
|------|--------|------|------|
| iMagic Solutions | PoC MCP（3-5 工具） | **$8K-15K** | 1-2 周 |
| iMagic Solutions | 生产级 MCP（5-15 工具，OAuth/RBAC/限流/审计/可观测） | **$15K-40K** | 3-6 周 |
| iMagic Solutions | **企业多租户 MCP（SaaS 化）** | **$40K-80K** | 6-10 周 |
| SolGuruz | 单源 MCP | $20K-60K | 4-10 周 |
| SolGuruz | 多源企业 MCP | $75K-250K+ | 10-16 周（合规 +25-40%） |
| Inventiple 买方指南 | 资深自由职业者 | **$80-180/hr**（$5K-22K/单） | 2-6 周 |
| Inventiple 买方指南 | AI 专精工作室（单系统） | $25K-40K | 2-4 周 |
| Inventiple 买方指南 | AI 专精工作室（多系统平台） | $60K-200K | 6-14 周 |
| Empiric Infotech | 专职开发者 | $25/hr 或 $2,000/月 | 按月 |
| RaftLabs | 固定价 | $29-49/hr | —— |

**Upwork 实单样例**：*Senior MCP / API Developer for ChatGPT*（内容自动化小生，Aruba 客户，$54K 累计支出）：**$22-29/hr、30+ hrs/周、1-3 个月，contract-to-hire**——中小客户预算只有 $22-29/hr，但量大；高价值单在垂直工作室（$80-180/hr）。

## 三、平台/生态信号

1. **Upwork 官方 MCP Server**（08-10 免费开放，OAuth 2.1 + 动态客户端注册，mcp.upwork.com）：Claude/ChatGPT/Cursor 可直接招人/投 proposal。08-25 深度分析（soloaitool）指出：**AI 先读结构化 profile（技能标签/时薪/可用性）再谈匹配**——公开 GitHub 作品 + 结构化技能标签成为获客前置条件。
2. **MCP Server 目录超 11,000 条目**（Q1 +2,877，55% 标注企业级）：平台成熟度上升，但多数是低质条目——**安全/多租户/可观测性仍稀缺**。
3. **MCP Economy 岗位分类**（llmhire，08-13）：MCP Server Developer $150-250K（企业）/ $120-200K（创业）/ **$200-280K（MCP-native 创业公司）**；AI Integration Engineer（MCP 专精）$170-290K；MCP Product Engineer $200-360K。卖方市场：**有生产级作品者稀缺**。
4. **Spring AI 官方栈持续升温**：dev.to（08-19）Spring AI MCP 边界实践、worldprogramming（08-05）Spring AI 2.0 Stateless MCP 教程——官方周边热度高，Java 系 MCP 是蓝海中的主流腔调。

## 四、对 `spring-ai-mcp-enterprise` 的结论

1. **框架能力 = 市场定价锚的「企业多租户档」**：iMagic 把多租户 MCP 定价 $40K-80K，本框架 V1.11（Row）+ V1.12（Schema）+ 规划中 V1.13（Instance）三档隔离就是该档位的开箱即用底座；
2. **简历/报价的命中话术**（投标/Upwork profile 用）："Java 生态首个企业级 MCP Server 框架：RBAC + OAuth2.1 令牌交换 + 审计 + 限流 + 工具注册中心 + Row/Schema 双模式多租户 + Spring AI Alibaba 原生兼容"，逐条对 Sumo Logic / EPAM / 网易 / 阿里 JD 的硬技能；
3. **Upwork 结构化 profile 待办**：技能标签按 Upwork 分类法填写（Model Context Protocol / Java / Spring Boot / REST API / OAuth / Microservices / Docker / Kubernetes），时薪锚定 $80-120/hr 高档位（用 $40K-80K 档的项目经验背书）；把 GitHub 仓库链接 + CI 绿标 + 测试徽章放在 profile 头部；
4. **博客选题（本周）**：《Java MCP Server 多租户三档隔离实战》+《MCP 外包不透明？$8K-80K 价目表拆解》（SEO 长尾词：mcp server pricing / mcp multi-tenant / spring ai mcp）。