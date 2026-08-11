# 标题：MCP 无状态化之后：Java 企业级 MCP Server 的架构演进（Streamable HTTP 实战）

> 适合发布：掘金 / CSDN / 51CTO
> 作者：HH-SpringAI-Agent-Starter（spring-ai-mcp-enterprise 开源项目）
> 日期：2026-08-03

---

## 引言：MCP 史上最大更新，和 Java 开发者有什么关系？

2026 年 7 月 28 日，Model Context Protocol（MCP）发布了自 2024 年 11 月诞生以来**最大规模的架构重构**：

- 取消协议层 Session，改为**无状态架构**——每个请求自带完整处理信息
- **Streamable HTTP 取代 SSE** 成为新默认传输
- 强化 OAuth 2.0 / OIDC 企业身份适配
- 引入 Extensions / Tasks / MCP Apps 扩展生态

这意味着什么？**MCP Server 可以像标准 Web 服务一样挂在负载均衡器后面、横向扩容、被网关统一限流和观察**。对企业来说，AI 试点项目终于可以平滑推进到生产环境。

但对大多数 Java 开发者来说，一个问题悬而未决：**Python 生态的 MCP Server 教程铺天盖地，Java 呢？**

本文从实战角度，讲清楚三件事：
1. Streamable HTTP 到底长什么样（传输层拆解）
2. 无状态化对架构意味着什么
3. 一个 Java/Spring Boot 企业级实现长什么样（附可运行代码）

---

## 一、Streamable HTTP：不再是"连上就完事"的 SSE

### 旧时代（2025-03-26 规范）：SSE 长连接

```text
Client ──GET /sse──────────────────────────────► Server   (建立长连接)
Client ◄──event: endpoint────────────────────── Server
Client ──POST /message (JSON-RPC)──────────────► Server
Client ◄──event: message (响应)───────────────── Server
```

痛点：
- 连接有状态，无法水平扩展
- 负载均衡器/Nginx 需要特殊配置支持 SSE 长连接
- 网关限流、监控难以统一接入

### 新时代（2026-07-28 规范）：Streamable HTTP 双通道

```text
┌─ POST /message ── 客户端→服务端 JSON-RPC 请求/响应（无状态，可任意负载均衡）
│    每个请求独立，协议版本/身份信息随请求携带
│
└─ GET  /stream ── server→client 通知流（事件推送）
      tools/listChanged 通知
      心跳保活（15s）
```

核心区别：**POST 通道完全无状态**，这就是它能挂在 K8s HPA 后面的原因。GET 流只是可选的通知通道，断开不影响请求处理。

---

## 二、无状态化对架构意味着什么

| 维度 | SSE 时代 | Streamable HTTP 时代 |
|------|---------|---------------------|
| 扩展性 | 连接绑定实例，无法水平扩展 | 任意实例处理任意请求 |
| 负载均衡 | 需要 sticky session | 标准轮询即可 |
| 网关 | 需长连接透传 | 标准 HTTP 语义，直接套 CDN/网关/限流 |
| 监控 | 难以统一 | Prometheus/网关日志全兼容 |
| 故障恢复 | 断连需重连+重协商 | 请求失败直接重试 |

**对企业的价值一句话：MCP Server 从"特殊基础设施"变成了"普通 Web 服务"。**

---

## 三、Java 企业级实现：Spring Boot + Streamable HTTP

市面上的教程大多停留在"怎么连一个工具"。但企业落地需要的是：**安全、审计、限流、监控、可扩展**。这就是 spring-ai-mcp-enterprise 做的事情。

### 3.1 无状态核心端点（mcp-core）

```java
public class McpStatelessEndpoint {
    // 2026-07-28 规范版本
    public static final String MCP_2026_PROTOCOL_VERSION = "2026-07-28";

    // 能力声明：明确支持 streamable-http
    "transport", Map.of(
            "stateless", true,
            "supportedTransports", List.of("streamable-http", "sse"),
            "streamableHttp", Map.of(
                    "endpoint", "/api/mcp/v2",
                    "stream", "/api/mcp/v2/stream",      // GET: server→client 通知流
                    "message", "/api/mcp/v2/message",    // POST: JSON-RPC 请求/响应
                    "notify", "/api/mcp/v2/notify"       // POST: tools/listChanged 广播
            )
    ),

    // 每个请求独立处理，不依赖 session
    public Map<String, Object> handleStatelessMessage(Map<String, Object> message, String traceId) {
        return switch (method) {
            case "initialize"  -> handleInitialize(id, params);
            case "tools/list"  -> handleToolsList(id, params);
            case "tools/call"  -> handleToolCall(id, params);
            case "ping"        -> successResponse(id, Map.of("status", "ok"));
            default -> errorResponse(id, -32601, "Method not found");
        };
    }
}
```

### 3.2 GET 事件流通道（server→client 通知 + 心跳）

这是 Streamable HTTP 的关键新增：**工具注册表变化时主动推送通知**，客户端收到后重新拉取 tools/list。

```java
@GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter stream() {
    String streamId = UUID.randomUUID().toString();
    SseEmitter emitter = new SseEmitter(0L); // 无超时，靠心跳保活
    streamEmitters.put(streamId, emitter);

    // 连接建立：发送初始端点事件
    emitter.send(SseEmitter.event().id(streamId).name("endpoint")
            .data(Map.of("protocolVersion", "2026-07-28")));

    // 后台心跳线程：每 15s 发送 keep-alive，防止网关断开空闲连接
    Thread heartbeat = new Thread(() -> {
        while (streamEmitters.containsKey(streamId)) {
            Thread.sleep(15_000);
            emitter.send(SseEmitter.event().name("heartbeat")
                    .data(Map.of("t", System.currentTimeMillis())));
        }
    }, "mcp-stream-heartbeat-" + streamId);
    heartbeat.setDaemon(true);
    heartbeat.start();
    return emitter;
}

// 工具变更广播
@PostMapping("/notify")
public Map<String, Object> notifyToolsChanged() {
    // 向所有已连接流客户端推送 notifications/tools/list_changed
    for (SseEmitter emitter : streamEmitters.values()) {
        emitter.send(SseEmitter.event().name("notifications/tools/list_changed")
                .data(Map.of("changedAt", System.currentTimeMillis())));
    }
    return Map.of("status", "ok", "delivered", delivered);
}
```

### 3.3 curl 三连：验证你的 Server 支持 Streamable HTTP

```bash
# 1. 能力声明（看 transport.streamableHttp）
curl http://localhost:8081/api/mcp/v2

# 2. 无状态调用工具（POST 消息，无需 session）
curl -X POST http://localhost:8081/api/mcp/v2/tools/call \
  -H "Content-Type: application/json" \
  -d '{"name":"system_info","arguments":{}}'

# 3. 事件流（观察 endpoint + 15s 心跳）
curl -N http://localhost:8081/api/mcp/v2/stream
```

---

## 四、企业级框架：教程和生产的差距

| 需求 | 玩具级教程 | 企业级框架（spring-ai-mcp-enterprise） |
|------|-----------|-------------------------------------|
| 安全 | 无 | RBAC + API Key + JWT + IP 白名单 |
| 限流 | 无 | Redis 令牌桶 + 每工具限速 |
| 审计 | 无 | 全量审计日志（谁/何时/调了什么）|
| 监控 | 无 | Prometheus 指标 + 告警 |
| 部署 | 本地跑通 | Docker Compose + K8s（HPA/Ingress）|
| 规范 | 2025-03-26 | 2026-07-28 全面适配（无状态 + Extensions + Tasks）|
| 生态 | 单机 | Spring AI Alibaba（DashScope/通义千问）原生集成 |

项目已通过 **142 个单元测试**，覆盖安全、工具管理、端点、监控、集成全部模块。

---

## 五、下一步与思考

MCP 无状态化让"三五个人也能挑战大厂"成为可能——因为协议本身不再需要专用基础设施。对于 Java 开发者，这意味着：

1. **这是 2026 年最值得押注的 AI 工程化方向**：Python 教程泛滥但 Java 企业级空白
2. **企业真正缺的是"能落地"的框架**，不是"能连工具"的 Demo
3. **合规是下一个风口**：中国 GB/Z 185-2026《智能体互联互通》刚发布，企业需要合规的 MCP 方案

如果你也在做 Java 企业级 MCP，欢迎关注/参与 spring-ai-mcp-enterprise（Apache 2.0 开源）：

- GitHub: https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise
- 特性：Streamable HTTP + 无状态核心 + RBAC + 审计 + 限流 + K8s 全套

**MCP 的下一个三年，属于能把它变成"普通 Web 服务"的团队。Java 开发者，该上场了。**

---

> 项目状态：V1.0 已发布（142 tests / 0 failures）· Streamable HTTP 传输已补齐 · CI 全绿
> 本稿由 cron 70a53bf4 晚间任务生成，供掘金/CSDN 发布
