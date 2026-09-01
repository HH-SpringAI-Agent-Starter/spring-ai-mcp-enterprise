# 市场雷达 2026-09-01 —— MCP Server 企业需求 / 招标 / 招聘

> 扫描窗口：近一周（2026-08-26 ~ 09-01）
> 聚焦：Java + Spring + MCP / A2A / SSE 流式

---

## 一、本周高价值招聘 / 外包信号

| 公司 / 岗位 | 地点 / 形式 | 薪酬 | 关键要求（与项目卖点） |
| --- | --- | --- | --- |
| **Ampstek** — Senior Java Developer (MCP, Agentic AI) | 阿姆斯特丹，Hybrid，Contract | 未公开 | Java 17+ / Spring Boot / Azure / MCP，Agentic 集成实战 ≥1 年 |
| **OneSeven Tech (OST)** — Senior Backend Engineer (MCP Infra) | 远程（拉丁美洲），长期 Contract | **$4000–5000/月（USD，Deel 发放）** | Java + Spring Boot + **WebFlux**，MCP 服务器组件，SSE/工具编排 |
| **CriticalRiver** — Senior MCP Dev / AI Agent Engineer | 海得拉巴，On-site | 未公开 | MCP + **Streamable HTTP** + OAuth2 + token 管理 + Cloud Run |
| **Bitrock** — Senior SE (AI Agent Dev) | 远程 | **$120K–150K/yr** | Java/Spring + **MCP** + Spring AI，OAuth2/JWT |
| **Sumo Logic** — Staff SE (Core AI Platform, MCP) | Redwood City | **$207K–243K/yr + Equity** | Java/Scala/Go + MCP 平台，**SSE 事件流**、多租户、限流、可观测 |
| **EPAM** — Lead Java Engineer (AI Native) | 钦奈，8–12 年 | 未公开 | Spring Boot + **MCP server 生态** + agentic SDLC |
| **Skm Group** — Sr Full Stack (MCP & AI Agents) | 爱尔兰，远程 | **€54K–120K/yr** | MCP + OAuth2 + 流式架构 |
| **MCP B2B 外包** — Mid-level Java Engineer (MCP Server) | 华沙，B2B 兼职 15-20h/w | 时薪 Competitive | Java 17+ / Spring Boot / **SSE 传输层** / JSON-RPC，保险业 |
| **火石创造** — 高级 Java 工程师 (MCP / Spring AI) | 重庆，全职 | 未公开（国内） | Spring AI + MCP + 智能体工作流引擎 + Function Calling |

## 二、市场洞察

1. **关键词三件套反复出现**：`SSE 传输 / Streamable HTTP / token 管理` —— 这正是 V1.16 补齐的 A2A 流式 + securitySchemes 的那块能力。
2. **薪酬带**：海外 MCP 序列岗位集中 **$120K–243K/yr** 或 **$4000–5000/月**；兼职工时价可观。国内（火石创造等）走 Spring AI + MCP 栈。
3. **多协议需求真实存在**：多个 JD 点名 A2A（蚂蚁类、"MCP vs A2A 选型"、Banyan "MCP/A2A"），与项目双协议网关路线完全吻合。
4. **外包实单可切入**：保险业 MCP Server B2B 单（华沙）、OneSeven US 客户 MCP 基建单，都要求 Java + Spring——正是本项目的技术靶心。

## 三、用户（Java + Spring + AI）在该赛道的卖点

- **双协议一次建**：MCP 全能力（安全/限流/审计/多租户） + A2A 双协议网关（V1.15），本仓库直接可演示。
- **流式 + 鉴权开箱即用**（V1.16）：A2A SSE `message/stream` / `task/resubscribe` + Agent Card `securitySchemes` 声明，命中当下 JD 高频词。
- **能讲规范、能敲代码**：JSON-RPC 2.0、SSE、OAuth2 都能落地，而非"只会低代码套壳"。
- **开源背书**：HH-SpringAI-Agent-Starter 组织的开源项目可作为面试 / 投标的 public proof-of-work。

## 四、待办动作（V1.17 附近）

- [ ] 针对 Sumo Logic / OneSeven 类"SSE 流式 + 多租户 + 可观测"JD 打磨一页 pitch
- [ ] 把 A2A + 流式能力提交 mcp.so / smithery 注册表标注
- [ ] 准备一个 2-3 分钟的 demo：A2A 编排器调用本网关计算器/金融工具的流式效果
