# A2A (Agent2Agent) 集成指南 — MCP + A2A 双协议网关

> 版本：V1.15 · 2026-08-31
> 模块：`mcp-integrations/mcp-a2a`（默认关闭，opt-in 启用）

## 一、为什么需要 A2A 网关

2026-08-20，Google 的 **A2A (Agent2Agent)** 协议正式并入 Linux Foundation 旗下的 **Agentic AI Foundation (AAIF)**，与 Anthropic 的 **MCP** 同属一个中立治理框架（AWS / Anthropic / Google / Microsoft / OpenAI 等 250+ 成员）。「协议之争」正式结束，业界共识的三层 Agent 协议栈是：

```
WebMCP  —— Agent 访问网页的结构化协议
MCP     —— Agent → 工具（纵向：数据库/API/文件，本框架已实现）
A2A     —— Agent → Agent（横向：任务委派/能力发现/跨组织协作，本模块实现）
```

**企业现状**：78% 的企业 AI 团队已在生产使用 MCP；A2A v1.0 已被 Salesforce Agentforce、ServiceNow Now Assist、Google ADK、Azure AI Foundry、AWS Bedrock AgentCore 原生支持。蚂蚁集团等头部企业的 JD 已明确写出「熟练掌握 MCP、A2A 研发架构」。

**本模块的价值**：让一个 MCP Server 同时成为 A2A Agent——把工具注册中心里的全部 MCP 工具自动派生为 A2A Agent Card / Skill，任意 A2A 编排器（跨厂商、跨云、跨组织）可以直接发现并调用你的企业工具，无需任何定制适配代码。

## 二、快速开始

### 1. 引入依赖（mcp-server 已默认引入，其他应用手动加）

```xml
<dependency>
    <groupId>com.mcp.enterprise</groupId>
    <artifactId>mcp-a2a</artifactId>
    <version>1.1.0</version>
</dependency>
```

### 2. 开启网关

```yaml
mcp:
  enterprise:
    a2a:
      enabled: true                     # 默认 false，显式开启
      base-path: /a2a                   # HTTP 基础路径
      agent-name: MCP Enterprise A2A Gateway
      agent-description: 将企业 MCP 工具以 A2A Skill 暴露，供任意 Agent 调用
      api-key: ${MCP_A2A_API_KEY:}      # 设置后所有 A2A 请求必须携带 X-A2A-Key 头
      task-timeout-ms: 30000             # 单次工具执行超时
```

### 3. 验证

```bash
# Agent Card（A2A 协议标准发现路径；/a2a/agent-card 为别名）
curl http://localhost:8081/.well-known/agent-card.json | jq '.skills[].id'
# 预期输出：database_query / search_web / calculator / finance_* 等全部已启用工具

# 健康检查
curl http://localhost:8081/a2a/health
# {"status":"UP","agent":"MCP Enterprise A2A Gateway","skills":12}
```

## 三、端点一览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/.well-known/agent-card.json` | A2A 标准发现路径（协议客户端默认请求） |
| GET | `/a2a/agent-card` | Agent Card 别名 |
| POST | `/a2a/rpc` | A2A JSON-RPC 2.0 分派 |
| GET | `/a2a/health` | 存活 + 技能数 |

## 四、A2A JSON-RPC 方法（POST /a2a/rpc）

所有请求遵循 JSON-RPC 2.0：`{"jsonrpc":"2.0","id":<any>,"method":"<method>","params":{...}}`。

### 4.1 message/send —— 一次性消息交互

```bash
curl -X POST http://localhost:8081/a2a/rpc -H 'Content-Type: application/json' -d '{
  "jsonrpc": "2.0", "id": 1, "method": "message/send",
  "params": {
    "message": {
      "text": "帮我计算 6*7",
      "metadata": {
        "skillId": "calculator",
        "arguments": { "expr": "6*7" }
      }
    }
  }
}'
```

响应：

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "message": { "role": "agent", "parts": [ { "text": "{\"success\":true,\"result\":42,...}" } ] },
    "contextId": "ctx-3f9a2b1c"
  }
}
```

### 4.2 task/send —— 任务式调用（推荐：带任务生命周期）

```bash
curl -X POST http://localhost:8081/a2a/rpc -H 'Content-Type: application/json' -d '{
  "jsonrpc": "2.0", "id": 2, "method": "task/send",
  "params": {
    "message": {
      "text": "查询订单表行数",
      "metadata": { "skillId": "database_query", "arguments": { "sql": "SELECT COUNT(*) FROM orders" } }
    }
  }
}'
```

响应含 `task` 对象：`id / status(completed) / artifacts[].parts[].text / messages`。

### 4.3 task/get / task/cancel

```bash
curl -X POST http://localhost:8081/a2a/rpc -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":3,"method":"task/get","params":{"id":"task-abc123"}}'

curl -X POST http://localhost:8081/a2a/rpc -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":4,"method":"task/cancel","params":{"id":"task-abc123"}}'
```

### 4.4 agent/quote

无需上下文的能力预览（返回技能列表），供编排器做轻量探测。

## 五、路由约定

| 方式 | 说明 |
| --- | --- |
| `metadata.skillId` | **推荐**：指定要调用的 MCP 工具名（= A2A Skill id） |
| `metadata.arguments` | 工具调用参数 Map，透传给 MCP 工具 |
| 文本前缀 `tool:<name>` | 兜底通道：无 metadata 的纯文本即可路由（例：`"tool:calculator 6*7"`） |

未指定工具时返回 `-32003` 错误并附可用技能列表。

## 六、错误码（A2A JSON-RPC）

| code | 含义 | 场景 |
| --- | --- | --- |
| -32600 | Invalid Request | 非 JSON-RPC 2.0 报文 |
| -32601 | Method Not Found | 未知方法 |
| -32602 | Invalid Params | 缺 message / 工具执行失败 |
| -32003 | Agent Not Found | 未知 skillId（附可用列表） |
| -32004 | Task Not Found | task/get / task/cancel 未知任务 |
| -32005 | Task Not Cancelable | 已完成的任务不可取消 |
| -32009 | Authentication Required | 未携带 X-A2A-Key（已配置 api-key 时） |

## 七、安全说明

- **默认关闭**：`mcp.enterprise.a2a.enabled=false`，未显式开启不暴露任何 A2A 面；
- **可选 API Key**：`api-key` 非空时强制 `X-A2A-Key` 请求头校验，防止 A2A 端口裸奔；
- **继承 MCP 安全层**：工具调用仍走 `McpToolManager.invoke`（权限/启用状态/审计统计），A2A 只是传输层换皮，不绕过任何工具级校验；
- **生产建议**：与 mcp-server 的 Bearer 校验（`enforce-bearer`）或外部 API 网关叠加使用。

## 八、演进路线（Roadmap）

- [ ] `message/stream` / `task/resubscribe`（SSE 流式，协议 streaming 能力置 true）
- [ ] Signed Agent Card（A2A v1.2 加密签名，防伪造 Agent Card 攻击）
- [ ] A2A Push Notifications（`task/notify` 回调）
- [ ] `securitySchemes` 声明 + OAuth2 Client Credentials 对接（复用 mcp-auth）

## 九、与市场信号对照

| 市场信号 | 本模块对应能力 |
| --- | --- |
| 蚂蚁/etc JD「MCP、A2A 研发架构」 | ✅ mcp-core（MCP）+ mcp-a2a（A2A）双协议齐备 |
| Sigma Software「MCP vs A2A 架构选型」岗位 | ✅ 提供 A2A 网关而非自研胶水层 |
| A2A 卡片伪造攻击（2025 实测 exploit） | 🔒 提供 api-key 校验 + 规划 Signed Card |
| Gartner：40% 企业应用年底含 Agent | ✅ 让 MCP 工具注册中心一次建设、两种协议消费 |