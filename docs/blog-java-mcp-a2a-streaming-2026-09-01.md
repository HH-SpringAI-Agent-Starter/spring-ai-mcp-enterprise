# 从一个 A2A 网关源码，看懂企业 Agent 调度的「流式 + 鉴权」两大硬骨头

> 关键词：A2A 协议、SSE 流式、Agent Card、securitySchemes、MCP、Spring Boot、Java
> 适用读者：正在部署企业 Agent 平台 / 做 MCP-A2A 双协议网关的 Java 后端工程师

---

## 为什么企业 Agent 绕不开 A2A 的「流式」和「鉴权」

2026 年，`MCP + A2A` 双协议已成为企业 Agent 的参考架构：**MCP 管"Agent→工具"，A2A 管"Agent→Agent"**。A2A 于 2025 并入 Linux Foundation AAIF，与 MCP 同获 AWS / Anthropic / Google / Microsoft / OpenAI 等 250+ 成员背书。

但很多团队把 A2A 网关搭起来后，发现两个"看起来不起眼、实际砸掉生产"的坑：

1. **只有同步问答，没有流式** —— 编排器调用一个耗时工具（如跑 SQL、调金融风控、等外部 API）时，如果只能阻塞等一个 `message/send` 的完整响应，长任务体验和可靠性都不合格。A2A 真正的语义是 `message/stream`（长连接实时推状态）+ `task/resubscribe`（断线重连 / 迟到订阅重放）。
2. **Agent Card 不声明鉴权方案** —— A2A 编排器拿到你的 Agent Card 后，不知道该用 API Key、还是 OAuth2 Bearer 来调用你。等于你部署了一个"裸奔但自己不知道"的服务。

这两块，正是企业 MCP 网关从"能跑"到"能被编排器正确接入"的分水岭。本文用一个真实的 Java 实现（Spring AI MCP Enterprise 的 mcp-a2a 模块）拆解这 9 个关键设计点。

---

## 一、SSE 流式的 5 个设计要点

### 1. 事件模型要对齐 A2A v1.0 规范

不要自造事件名。按规范用四种标准事件：

| 事件 | 含义 |
| --- | --- |
| `TaskStatusUpdateEvent` | working → completed/failed/canceled |
| `TaskArtifactUpdateEvent` | 工具结果产出 Artifact |
| `MessageDeliveryEvent` | message/stream 最终投递的 agent 消息 |
| `TaskNotFoundEvent` | task/resubscribe 目标任务不存在 |

在 Java 里用一个 `record` 承载即可：

```java
public record A2aStreamEvent(String event, String taskId, Map<String, Object> data) {}
```

### 2. 工具执行要异步，别阻塞 HTTP 线程

SSE 端点必须先返回 `SseEmitter`，让线程池去跑工具，否则长任务会占死连接线程：

```java
// daemon 线程池，绝不阻塞 HTTP 请求线程
ExecutorService streamExecutor = Executors.newCachedThreadPool(r -> {
    Thread t = new Thread(r, "a2a-stream");
    t.setDaemon(true);
    return t;
});
```

### 3. 订阅先「重放历史」，解决"任务已完成、订阅来晚"的竞态

这是流式最阴险的 bug：客户端 `task/resubscribe` 时任务早已完成，事件已经发完，此时直接挂订阅者会**永远等不到任何事件**。正确姿势：

```java
public boolean subscribe(String taskId, Consumer<A2aStreamEvent> consumer, Runnable onComplete) {
    // 1. 先把历史事件同步重放一遍
    for (A2aStreamEvent evt : history) consumer.accept(evt);
    // 2. 已终态 → 立即 complete；未终态 → 挂活跃订阅
    if (isTerminal(task)) { onComplete.run(); return true; }
    subscribers.add(new StreamSubscriber(consumer, onComplete));
    return true;
}
```

### 4. 时间线：同步发 working，异步发后续

初始 `TaskStatusUpdateEvent(working)` 应该在方法内**同步发出**并写入历史，保证"订阅即得"；后续 artifact / completed / delivery 由后台线程发。

### 5. 事件要双写：既推送给订阅者，也写入历史

`emit()` 里同时做两件事，历史供 `task/resubscribe` 重放使用：

```java
private void emit(String taskId, A2aStreamEvent e) {
    history.computeIfAbsent(taskId, ...).add(e);   // 写历史
    subscribers.get(taskId)?.forEach(s -> s.consumer.accept(e)); // 推送
}
```

---

## 二、securitySchemes 声明的 4 个要点

Agent Card 的 `securitySchemes` 是 A2A 编排器做鉴权协商的入口。

1. **api-key 模式**（有 API Key 时的默认）：
```json
[{"type": "apiKey", "in": "header", "name": "X-A2A-Key"}]
```

2. **oauth2 Client Credentials 模式**（对接企业 IdP / mcp-auth）：
```json
[{"type": "oauth2", "flows": {"clientCredentials": {"tokenUrl": "/oauth2/token", "scopes": {}}}}]
```

3. **显式 `none`** 可以覆盖 API Key 自动推导——防止"配了 key 却想裸奔供公网发现"的误暴露。

4. **顺带**：`capabilities.streaming` 跟随 `streaming-enabled` 配置，让编排器从卡片上一眼看出你这网关支持流式。

---

## 三、一个被市场反复验证的信号

最近一周的招聘 / 外包需求几乎都在点这三个关键词：**SSE 传输层 / Streamable HTTP / token 管理**。随便看几个：

- **Ampstek**（阿姆斯特丹，Contract）：Java + Spring Boot + MCP + Agentic AI，要求 1 年 MCP 实战
- **OneSeven Tech**（远程，$4000–5000/月）：Java + Spring Boot + WebFlux，MCP 基础设施，强调 SSE
- **CriticalRiver**（海得拉巴）：MCP + OAuth2 + Cloud Run，要求 Streamable HTTP、token 管理
- **火石创造**（重庆）：Java 高级，Spring AI + MCP + 智能体工作流
- **Sumo Logic**（美国，$207–243k/yr）：Staff，MCP 平台，SSE 事件流 + 多租户

结论很直白：**"Java + Spring + MCP + SSE 流式 + 鉴权"这套组合，正在被国内外企业整装采购。** 谁能把企业级 MCP/A2A 网关做得既规范又开箱即用，谁就能在这波 Agent 基建红利里分到羹。

---

## 四、落地 & 收益

- **开源影响力**：`spring-ai-mcp-enterprise`（HH-SpringAI-Agent-Starter 组织）V1.16 补齐 A2A 流式 + 鉴权声明，从"能跑"升级为"能被 A2A 编排器正确接入"。
- **个人卖点**：一份代码同时覆盖 MCP 全能力（安全/限流/审计/多租户/监控）+ A2A 双协议 + SSE 流式 + OAuth2 打通——这正好是市场上最稀缺的 Java Agent 基建画像。

（本文基于 HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise V1.16 的 mcp-a2a 模块编写，代码与集成指南见仓库 docs/。）
