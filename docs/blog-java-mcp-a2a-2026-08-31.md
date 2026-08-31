# MCP 之后是 A2A：2026-08-20 协议归位，Java 开发者如何用一套代码同时讲两种 Agent 语言

> 本文同步发布：掘金 / CSDN | 项目：[HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise](https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise)
> 作者：HH SpringAI Agent Starter

## 一、先看新闻：A2A 正式并入 AAIF

**2026 年 8 月 20 日**，Google 的 Agent2Agent（A2A）协议正式加入 Linux Foundation 旗下的 **Agentic AI Foundation（AAIF）**，与 Anthropic 的 Model Context Protocol（MCP）同框治理。成员包括 AWS、Anthropic、Bloomberg、Cloudflare、Google、Microsoft、OpenAI 等 250+ 组织。

这意味着什么？

- 「MCP 还是 A2A」的协议之争**正式结束**——两个协议本来就不在一个层：**MCP 是纵向的**（Agent 连工具：数据库、API、文件），**A2A 是横向的**（Agent 连 Agent：任务委派、能力发现、跨组织协作）；
- 企业参考架构定型为**三层 Agent 协议栈**：`WebMCP（网页）→ MCP（工具）→ A2A（Agent 协作）`；
- A2A v1.0 已被 Salesforce Agentforce、ServiceNow Now Assist、Google ADK、Azure AI Foundry、AWS Bedrock AgentCore 原生支持；
- MCP 已达 **1.1 亿月下载**、官方注册表 9,652 个 Server、78% 企业 AI 团队生产在用；
- Gartner 预测 **40% 的企业应用**将在 2026 年底包含 task-specific Agent（2025 年不足 5%）。

而招聘市场已经先一步动起来了：蚂蚁集团的 JD 明确写着「**熟练掌握 LangChain、MCP、A2A 研发架构**」；Sigma Software 的 AI 架构师岗位要求「**在 MCP、A2A、传统 API 之间做架构选型**」。**MCP+A2A 双协议能力已经从「加分项」变成了「写进 JD 的要求」。**

## 二、Java 开发者的尴尬与机会

MCP 生态 80% 是 Python、18% 是 Node.js，**Java 几乎空白**——但 90% 的中国企业后端是 Java/Spring。企业里真正的核心资产（订单库、ERP、风控系统）都是 Java 系统，而 AI Agent 想调用的恰恰是这些。

所以我们的判断是：**Java 不是 MCP 的短板，是 MCP 的蓝海**。这也是我们持续做 [spring-ai-mcp-enterprise](https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise) 的原因——把企业级 MCP Server 需要的安全（RBAC/API Key/OAuth2）、限流、审计、多租户、监控全部做成开箱即用的 Spring Boot 模块。

但只有 MCP 还不够。当你的 MCP Server 要接入**别人家的编排器**（跨云、跨厂商、跨组织）时，对方的第一语言可能是 A2A。于是 V1.15 我们做了 **mcp-a2a 模块：一个网关，同时讲 MCP 和 A2A 两种语言。**

## 三、设计：MCP 工具注册中心 = A2A 技能清单

核心思路只有一句话：**不写第二套工具实现，只做协议翻译。**

```
┌────────────────────────────────────────────────────┐
│              MCP Enterprise Server                  │
│                                                    │
│   ToolRegistry（工具注册中心）                       │
│   ┌──────────┬──────────┬──────────┐              │
│   │database  │search_web│calculator│ ... 全部工具  │
│   └────┬─────┴────┬─────┴────┬─────┘              │
│        │          │          │                     │
│        ▼          ▼          ▼                     │
│   ┌─────────────────────────────────────┐         │
│   │        McpToolManager（统一执行）     │         │
│   └───────┬──────────────────┬──────────┘         │
│           │ MCP 协议          │ A2A 协议            │
│           ▼                  ▼                     │
│   ┌──────────────┐   ┌───────────────────┐        │
│   │ /api/mcp/*   │   │ /.well-known/     │        │
│   │ (SSE/无状态)  │   │  agent-card.json  │        │
│   │              │   │ /a2a/rpc          │        │
│   └──────────────┘   └───────────────────┘        │
└────────────────────────────────────────────────────┘
```

落地细节：

1. **Agent Card 自动派生**：A2A 客户端通过 `GET /.well-known/agent-card.json`（协议标准发现路径）拿到 Agent Card，`skills[]` 直接从工具注册中心生成——`id` 就是 MCP 工具名，`inputSchema` 一并带上，**新增一个 MCP 工具 = 新增一个 A2A Skill，零额外代码**；
2. **JSON-RPC 2.0 分派**：`POST /a2a/rpc` 实现 `message/send`（一次性交互）、`task/send` / `task/get` / `task/cancel`（任务生命周期）、`agent/quote`（能力预览），一次工具调用 = 一个 Task，工具结果进入 Artifact；
3. **路由约定**：`metadata.skillId` 指定工具、`metadata.arguments` 传参；纯文本通道支持 `tool:<name>` 前缀兜底，不指定工具时返回 `-32003` 并附可用技能列表；
4. **安全继承**：A2A 只是「传输层换皮」，工具调用仍走 `McpToolManager.invoke`（启用校验/审计/统计），不绕过任何工具级安全；网关默认关闭（opt-in），可配 `X-A2A-Key` 鉴权。

## 四、30 秒上手

```yaml
mcp:
  enterprise:
    a2a:
      enabled: true
      api-key: ${MCP_A2A_API_KEY:}
```

```bash
# 发现能力
curl http://localhost:8081/.well-known/agent-card.json

# 让编排器调用 MCP 工具（task/send）
curl -X POST http://localhost:8081/a2a/rpc -H 'Content-Type: application/json' -d '{
  "jsonrpc": "2.0", "id": 1, "method": "task/send",
  "params": { "message": { "text": "6*7",
    "metadata": { "skillId": "calculator", "arguments": { "expr": "6*7" } } } }
}'
```

## 五、给 Java 团队的三条建议

1. **先把 MCP 建好**：MCP 生态成熟（1.1 亿月下载、注册表近万 Server），是企业 Agent 的工具底座，优先落地；
2. **A2A 按需加**：当出现跨厂商/跨组织/跨云的 Agent 协作需求时再加 A2A——本模块 opt-in 设计就是为这个节奏准备的；
3. **别造自己的协议**：Gartner 数据很残酷——40%+ 的 agentic 项目会在 2027 年底被砍，原因主要是成本、ROI 和风控。**用开放标准降低集成成本，把治理（鉴权、审计、限流、多租户）做进框架里**，才是企业 Agent 基建的生存之道——这正是本项目的立身之本。

## 六、下一步

- `message/stream` / `task/resubscribe`（SSE 流式）
- Signed Agent Card（A2A v1.2，防伪造卡片攻击——2025 年已有真实 exploit）
- 对接 OAuth2 Client Credentials 复用 mcp-auth

欢迎 Star、提 Issue、共建 Java 企业级 Agent 基建：[spring-ai-mcp-enterprise](https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise)

---

*参考：AAIF 08-20 官宣（A2A 并入）；A2A 150+ 组织采用（Linux Foundation）；MCP 110M 月下载 / 9652 注册 Server；Gartner 2026 Agent 预测；蚂蚁/多家 JD 技术要求*