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

## 八、SSE 流式（V1.16）

A2A v1.0 的标准长连接语义，让编排器实时消费工具执行进度。

### 端点

| 方法 | 端点 | 说明 |
| --- | --- | --- |
| `message/stream` | `POST ${base}/rpc/stream`（`Accept: text/event-stream`） | 后台异步执行 MCP 工具，推送事件序列 |
| `task/resubscribe` | 同上 | 重放目标任务历史事件（含已完成任务），随后保持长连接 |

### 事件类型（对齐 A2A v1.0）

- `TaskStatusUpdateEvent`：working → completed/failed/canceled
- `TaskArtifactUpdateEvent`：工具结果产出 Artifact
- `MessageDeliveryEvent`：message/stream 最终投递的 agent 消息
- `TaskNotFoundEvent`：task/resubscribe 目标任务不存在

### curl 示例

```bash
# 流式调度
curl -N -X POST http://localhost:PORT/a2a/rpc/stream \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{"jsonrpc":"2.0","id":"1","method":"message/stream",
       "params":{"message":{"text":"帮我算 6*7",
         "metadata":{"skillId":"calculator","arguments":{"expr":"6*7"}}}}}'

# 重订阅已完成/进行中的任务
taskId=<上一步事件里的 taskId>
curl -N -X POST http://localhost:PORT/a2a/rpc/stream \
  -H "Content-Type: application/json" -H "Accept: text/event-stream" \
  -d '{"jsonrpc":"2.0","id":"2","method":"task/resubscribe","params":{"id":"'$taskId'"}}'
```

### 配置

```yaml
mcp:
  enterprise:
    a2a:
      streaming-enabled: ${MCP_A2A_STREAMING_ENABLED:true}
      security-scheme: ${MCP_A2A_SECURITY_SCHEME:}   # none | api-key | oauth2
      oauth2-token-url: ${MCP_A2A_OAUTH2_TOKEN_URL:}
```

### securitySchemes 声明（V1.16，mcp-auth 打通第一步）

Agent Card 的 `securitySchemes` 向 A2A 编排器声明鉴权方案：
- **api-key**（默认，配了 `api-key` 时）：`{ type: apiKey, in: header, name: X-A2A-Key }`
- **oauth2**：`{ type: oauth2, flows.clientCredentials.tokenUrl = 配置端点 }`（默认对接 mcp-auth `/oauth2/token`）
- **none**：空列表。显式 `none` 可覆盖 API Key 自动推导。

> V1.17 起，声明与强制校验一致：声明 `oauth2` 就真的校验 Bearer JWT（RFC 6750），不再是"假安全"。

## 九、OAuth2 Bearer 强制鉴权（V1.17，mcp-auth 深度打通第二步）

### 三种鉴权模式

| 模式 | 配置 | 校验方式 | 适用场景 |
| --- | --- | --- | --- |
| `none` | （默认） | 不鉴权，可置于网关 API Key 之后 | 内部 / 开发环境 |
| `api-key` | `mcp.enterprise.a2a.api-key` 非空 | `X-A2A-Key` 请求头 | 轻量服务间调用 |
| `oauth2` | `mcp.enterprise.a2a.jwt-secret` 非空 | `Authorization: Bearer <JWT>`（RFC 6750） | 企业级，与 mcp-auth 令牌互通 |

**模式推导优先级**：
1. `security-scheme` 显式声明（oauth2 / api-key / none）→ 尊重声明
2. `jwt-secret` 非空 → oauth2
3. `api-key` 非空 → api-key
4. 否则 → none

### 与 mcp-auth 令牌互通

`jwt-secret` 与 mcp-auth 的 `mcp.auth.jwt-secret` 配置同值时，mcp-auth OAuth2 Client Credentials 端点签发的 `access_token` 直接通过 A2A 网关校验。密钥派生规则完全一致（HS256，不足 32 字节补足到 32 字节）。

### 配置示例

```yaml
mcp:
  enterprise:
    a2a:
      enabled: true
      # V1.17: OAuth2 Bearer 强制鉴权（与 mcp-auth 同密钥即互通）
      jwt-secret: ${MCP_A2A_JWT_SECRET:***}
      security-scheme: oauth2
      oauth2-token-url: https://your-host/api/auth/oauth2/token
```

### curl 完整流程

```bash
# 1️⃣ 向 mcp-auth 换令牌（Client Credentials）
TOKEN=*** -s -X POST http://localhost:8081/api/auth/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=my-service&client_secret=change…cret" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

# 2️⃣ 无令牌 → 401
curl -s http://localhost:8081/a2a/health
# → {"error":{"code":-32009,"message":"Authentication required (Authorization: Bearer <JWT> - RFC 6750)"}}

# 3️⃣ 带 Bearer 令牌 → 通过
curl -s http://localhost:8081/a2a/health -H "Authorization: Bearer $TOKEN"
# → {"status":"UP","authMode":"oauth2",...}

# 4️⃣ 伪造令牌 → 401
curl -s http://localhost:8081/a2a/health -H "Authorization: Bearer ***"
```

### 未授权响应体（RFC 6750）

oauth2 模式返回：
```json
{"jsonrpc":"2.0","id":null,"error":{"code":-32009,"message":"Authentication required (Authorization: Bearer <JWT> - RFC 6750)"}}
```
api-key 模式返回：
```json
{"jsonrpc":"2.0","id":null,"error":{"code":-32009,"message":"Authentication required (X-A2A-Key)"}}
```

## 十、演进路线（Roadmap）

- [x] `message/stream` / `task/resubscribe`（SSE 流式，协议 streaming 能力置 true）—— **V1.16 已完成**
- [x] `securitySchemes` 声明—— **V1.16 已完成（声明层）**
- [x] OAuth2 Bearer 强制鉴权（RFC 6750，与 mcp-auth 令牌互通）—— **V1.17 已完成**
- [ ] Signed Agent Card（A2A v1.2 加密签名，防伪造 Agent Card 攻击）
- [ ] A2A Push Notifications（`task/notify` 回调）
- [ ] OAuth2 scope → MCP 工具级权限（token scope 映射 tools:read/tools:write）

## 十一、与市场信号对照

| 市场信号 | 本模块对应能力 |
| --- | --- |
| 蚂蚁/etc JD「MCP、A2A 研发架构」 | ✅ mcp-core（MCP）+ mcp-a2a（A2A）双协议齐备 |
| Sumo Logic $207-243K「OAuth/token 交换/多租户/限流」 | ✅ OAuth2 闭环（V1.17）+ 多租户（V1.13-14）+ 限流 |
| Photon/Citi OAuth2+OWASP+MCP | ✅ mcp-auth OAuth2 Client Credentials + A2A Bearer 强制校验 |
| A2A 认证三层：HTTPS+OAuth2+Signed Card | ✅ OAuth2 强制（V1.17）→ Signed Card（V1.18 规划） |
| AAIF 250+ 会员（MCP+A2A 同治理） | ✅ 双协议网关定位与 AAIF RFP 新语言完全对齐 |