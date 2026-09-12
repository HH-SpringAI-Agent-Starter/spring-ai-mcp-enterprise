# Spring AI 工具桥接指南（V1.23）

> 一句话：**把 Spring AI 生态里已经写好的 `@Tool` 工具，零改造自动变成企业级 MCP 工具**，并自动继承 RBAC 权限、RateLimit 限流、审计日志、工具级 Scope 授权、健康检查、灰度治理——不需要动一行业务代码。

## 为什么需要它

你是 Spring AI / Spring AI Alibaba 用户，团队里已经有一堆用 `@Tool` 注解写好的工具方法（查订单、算指标、查天气……）。现在要把它们暴露给 AI Agent（Claude / ChatGPT / 千问 / 自研 Agent 平台）用，直接上 MCP 的话，你马上要面对：

- 工具怎么注册、怎么发现、怎么版本管理？
- 谁来鉴权？API Key 还是 OAuth2？工具级权限怎么控？
- 限流、熔断、审计、Scope 授权谁来管？
- MCP 协议端点（SSE / Streamable HTTP / Stateless）谁来实现？

本模块（`mcp-springai-tools`）就是这座桥：**Spring AI 工具 → 企业 MCP 工具**。它扫描 Spring 容器里的三类工具源，包装成 `McpToolExecutor` 注册进企业 `McpToolManager`，之后的 MCP 端点、安全、限流、审计全部由框架接管。

## 工作原理

```
┌─────────────────────────────┐        ┌──────────────────────────────────────┐
│  你的 Spring AI 工具          │        │  mcp-springai-tools（V1.23）           │
│  ┌───────────────────────┐  │        │                                       │
│  │ @Tool 方法 Bean        │──┼───────▶│ SpringAiToolCollector                 │
│  │ ToolCallback Bean      │  │        │  ① ToolCallback Bean                  │
│  │ ToolCallbackProvider   │  │        │  ② ToolCallbackProvider（MCP Client）  │
│  └───────────────────────┘  │        │  ③ @Tool 注解 Bean                    │
└─────────────────────────────┘        │          │  按名去重                    │
                                       │          ▼                            │
                                       │ SpringAiToolCallbackExecutor          │
                                       │  （implements McpToolExecutor）        │
                                       │          │                            │
                                       │          ▼                            │
                                       │ McpToolManager.registerExecutor()     │
                                       └──────────┬───────────────────────────┘
                                                  │ 自动获得
                                                  ▼
                    ┌───────────┬───────────┬───────────┬───────────┐
                    │ RBAC 权限  │ 限流/超时  │ 审计日志   │ Scope 授权 │
                    └───────────┴───────────┴───────────┴───────────┘
                    ┌───────────┬───────────┬───────────┐
                    │ 健康检查   │ MCP 端点   │ Skill 治理 │
                    │           │ (SSE/HTTP) │  (registry)│
                    └───────────┴───────────┴───────────┘
```

## 快速接入（3 步）

### 第 1 步：加依赖

```xml
<dependency>
    <groupId>com.mcp.enterprise</groupId>
    <artifactId>mcp-springai-tools</artifactId>
    <version>1.1.0</version>
</dependency>
```

### 第 2 步：写你熟悉的 Spring AI 工具（业务代码零改动）

```java
@Component
public class OrderTools {

    @Tool(name = "queryOrder", description = "按订单号查询订单状态与金额")
    public String queryOrder(@ToolParam(description = "订单号") String orderId) {
        return orderService.find(orderId).toJson(); // 返回 JSON 字符串即可
    }

    @Tool(name = "refundOrder", description = "发起订单退款（需管理员权限）")
    @ToolAuthorization(roles = "admin")   // 可选：配合企业 RBAC 的角色声明
    public String refundOrder(@ToolParam(description = "订单号") String orderId,
                              @ToolParam(description = "退款原因") String reason) {
        return refundService.refund(orderId, reason).toJson();
    }
}
```

### 第 3 步：启动

什么都不用配。应用启动时自动完成：

```
⚡ Spring AI 工具桥接：发现 2 个 Spring AI 工具，自动注册为企业 MCP 工具（RBAC/限流/审计/Scope 已生效）
```

之后 `GET /api/mcp/tools` 就能看到工具，Claude / ChatGPT / 千问 Agent 直接可调。

## 配置项

前缀 `mcp.springai-tools.*`，全部可选：

```yaml
mcp:
  springai-tools:
    enabled: true                    # 总开关
    scan-tool-annotated-beans: true  # 扫描 @Tool 注解 Bean（默认开）
    include-tool-callback-providers: true  # 收集 ToolCallbackProvider（默认开，兼容 MCP Client 动态工具）
    name-prefix: ""                  # 工具名统一前缀，如 "sa_"，避免与内置工具重名
    category: springai               # 工具分类（企业侧分类治理用）
    version: "1.0"                   # 工具版本
    default-roles: "admin,user"      # 默认所需 RBAC 角色
    required-scopes: ""              # 默认所需 OAuth2 scope（逗号分隔，如 tools:order:read）
    timeout-ms: 30000                # 单次调用超时
    rate-limit-per-second: 10        # 每秒限流阈值
```

关闭整个桥接：`mcp.springai-tools.enabled=false`。

## 三类工具源说明

| 来源 | 说明 | 典型场景 |
|---|---|---|
| `ToolCallback` Bean | 直接注册的工具回调 | `FunctionToolCallback`、`MethodToolCallback` |
| `ToolCallbackProvider` Bean | 工具回调提供者 | Spring AI MCP Client 动态发现的远端工具（`ToolCallbackProvider.from(...)` 或 `StaticToolCallbackProvider`） |
| `@Tool` 注解 Bean | 最主流的写法 | 任何 Spring Bean 里的 `@Tool` 方法，自动转 `MethodToolCallback` |

同名工具按**最终注册名去重**，只保留先发现的；多来源冲突会打 WARN 日志，避免静默覆盖。

## 调用链示例

```bash
# 1. 拿 API Key / Token（OAuth2 或 API Key 均可）
curl -X POST http://localhost:8080/api/mcp/oauth2/token \
  -H "Content-Type: application/json" \
  -d '{"client_id":"demo","client_secret":"demo-secret","grant_type":"client_credentials"}'

# 2. 列出工具（能看到 springai 分类的 queryOrder）
curl http://localhost:8080/api/mcp/tools -H "Authorization: Bearer <TOKEN>"

# 3. 调用工具（自动经过 RBAC + 限流 + 审计 + Scope 校验）
curl -X POST http://localhost:8080/api/mcp/tools/call \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"name":"queryOrder","arguments":{"orderId":"SO-20260912-001"}}'
```

## 与 Spring AI Alibaba 配合

本模块与 `mcp-alibaba` 集成模块是**互补关系**：

- `mcp-alibaba`：把企业 MCP Server 的工具注册进 **DashScope/千问 Agent** 的 Tool Context（让千问能调企业工具）；
- `mcp-springai-tools`：把你们**已有的 Spring AI 工具**反向注册进企业 MCP Server（让任何 MCP 客户端都能调）。

两者都引入后，你的工具资产在「Spring AI 侧」和「MCP 侧」双向打通，一套工具定义，双重消费入口。

```yaml
spring:
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}
      chat:
        options:
          model: qwen-max
```

## 测试

模块自带 15 个测试（`SpringAiToolCallbackExecutorTest` / `SpringAiToolCollectorTest` / `SpringAiToolsPropertiesTest`），覆盖：Schema 转换、调用桥接（含 Spring AI String 返回双重编码归一化）、三类来源收集、按名去重、前缀、缺参兜底、关闭开关。

```bash
mvn -pl mcp-integrations/mcp-springai-tools test
```