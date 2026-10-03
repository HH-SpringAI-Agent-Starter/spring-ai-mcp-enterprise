# Spring AI Alibaba 1.0 GA — MCP Server 示例

> 用 **Spring AI Alibaba 1.0 GA** 把普通 Java 方法发布成 MCP 工具，并挂到
> [Spring AI MCP Enterprise](../../) 的联邦网关 / 注册中心后面，自动获得企业级安全治理。

本示例**独立于主工程 reactor 构建**（在 `examples/` 下，不进父 `pom.xml` 的 `<modules>`），
可单独 `mvn spring-boot:run`。

## 它演示什么

| 能力 | 实现 |
|------|------|
| 通义千问 / DashScope 模型接入 | `spring-ai-alibaba-starter-dashscope` |
| 用 `@Tool` 把 Java 方法变 MCP 工具 | `OrderQueryTool` + `ToolConfig`（MethodToolCallback） |
| MCP Server 暴露（Streamable HTTP） | `spring-ai-alibaba-starter-mcp` + `spring-ai-mcp-server-spring-boot-starter` |
| 联邦治理（RBAC/审计/HITL/限流） | 挂到本框架 `mcp-gateway` 后自动获得 |

## 运行

```bash
# 1. 配置通义千问 Key
export DASHSCOPE_API_KEY=sk-xxxx

# 2. 启动（独立 Maven 工程）
cd examples/spring-ai-alibaba-mcp-server
mvn spring-boot:run

# 3. 验证工具已暴露
curl -X POST http://localhost:8080/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'
```

## 接入企业治理层（关键一步）

把本 Server 的 `http://localhost:8080/mcp` 作为 upstream 写进
本框架 `mcp-gateway` 的 `application.yml`：

```yaml
mcp:
  enterprise:
    gateway:
      upstreams:
        - name: order
          url: http://localhost:8080/mcp
          enabled: true
```

之后 `order__getOrder` / `order__listOrders` 两个工具即被命名空间隔离，
并自动套用 `mcp-governance` 的 RBAC / 审计 / HITL / 限流。

## 对齐 Spring AI Alibaba 1.0 GA 的官方企业级 MCP 方案

- **Nacos MCP Registry（分布式注册/负载均衡）** ↔ 本框架 `mcp-registry`
- **Higress AI 网关（API→MCP 代理）** ↔ 本框架 `mcp-gateway`
- **本示例补的缺口**：官方方案做"接入与路由"，本框架补"治理与可观测"

> 坐标说明：示例使用 Spring AI Alibaba `1.0.0.0` GA。若官方 BOM/artifactId 有微调，
> 以 [alibaba/spring-ai-alibaba](https://github.com/alibaba/spring-ai-alibaba) 最新发布为准。
