# 企业级 MCP Server 客户端接入实战：Java / Python / curl 全攻略

> 原文发表于：掘金 / CSDN（准备中）
> 项目地址：https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise
> 作者：HH SpringAI Agent Starter

## 一、为什么写这篇文章？

MCP（Model Context Protocol）正在快速成为 AI Agent 调用企业系统的“通用语言”。
但市面上的教程大多聚焦在**如何搭建 MCP Server**，对于**企业已有系统如何接入 MCP Server** 的客户端实践却语焉不详。

本文基于 `spring-ai-mcp-enterprise` 企业级 MCP Server 框架，给出三种最常见的客户端接入方式：

- **Java**：纯 JDK HttpClient，零依赖，适合后端系统
- **Python**：标准库 `urllib`，无需安装额外包，适合脚本/运维
- **curl**：一键测试，适合 DevOps 和接口调试

并覆盖两种协议：

- 有状态 REST API（向后兼容）
- 2026-07-28 无状态 Streamable HTTP

## 二、环境准备

```bash
git clone https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise.git
cd spring-ai-mcp-enterprise
mvn clean install -DskipTests
cd mcp-server
mvn spring-boot:run
```

启动后日志会输出默认管理员 API Key，复制备用。

## 三、curl 快速验证

```bash
export MCP_API_KEY=你的管理员Key

# 健康检查
curl http://localhost:8081/api/mcp/health -H "X-API-Key: $MCP_API_KEY"

# 连接服务
SESSION=$(curl -s -X POST http://localhost:8081/api/mcp/connect \
  -H "X-API-Key: $MCP_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"clientName":"curl-demo"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['sessionId'])")

# 列出工具
curl http://localhost:8081/api/mcp/tools -H "X-API-Key: $MCP_API_KEY"

# 调用工具
curl -X POST http://localhost:8081/api/mcp/tools/system_info/invoke \
  -H "X-API-Key: $MCP_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{}'

# 断开连接
curl -X POST http://localhost:8081/api/mcp/disconnect \
  -H "X-API-Key: $MCP_API_KEY" \
  -H "Content-Type: application/json" \
  -d "{\"sessionId\":\"$SESSION\"}"
```

## 四、Java 客户端示例

位置：`examples/client-java/McpEnterpriseClient.java`

核心代码片段：

```java
HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:8081/api/mcp/health"))
        .header("X-API-Key", apiKey)
        .GET()
        .build();

HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
JsonNode health = new ObjectMapper().readTree(response.body());
```

Java 17+ 可直接运行，无需任何第三方依赖。

## 五、Python 客户端示例

位置：`examples/client-python/mcp_client.py`

```python
import json
import os
import urllib.request

class McpEnterpriseClient:
    def __init__(self, base_url="http://localhost:8081", api_key=None):
        self.base_url = base_url.rstrip("/")
        self.api_key = api_key or os.environ.get("MCP_API_KEY", "default-admin-key")

    def health(self):
        req = urllib.request.Request(
            f"{self.base_url}/api/mcp/health",
            headers={"X-API-Key": self.api_key}
        )
        with urllib.request.urlopen(req, timeout=30) as resp:
            return json.loads(resp.read().decode("utf-8"))
```

Python 3.8+ 标准库即可运行。

## 六、Spring AI Alibaba 集成

位置：`mcp-examples/mcp-client-spring-ai`

在 `application.yml` 中配置：

```yaml
spring:
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}
    chat:
      options:
        model: qwen-max

mcp:
  enterprise:
    server:
      url: ${MCP_SERVER_URL:http://localhost:8081}
      api-key: ${MCP_API_KEY}
```

控制器自然语言调用工具：

```java
@RestController
public class AgentController {
    private final ChatClient chatClient;

    public AgentController(ChatClient.Builder builder, List<ToolCallback> mcpTools) {
        this.chatClient = builder.defaultTools(mcpTools).build();
    }

    @PostMapping("/agent/ask")
    public String ask(@RequestBody String question) {
        return chatClient.prompt().user(question).call().content();
    }
}
```

启动后访问：

```bash
curl -X POST http://localhost:8082/agent/ask \
  -H "Content-Type: text/plain" \
  -d '查看当前服务器系统信息'
```

## 七、Streamable HTTP 无状态模式（2026-07-28 规范）

项目已支持新版无状态 MCP 规范，客户端调用更简单：

```bash
# 能力声明
curl http://localhost:8081/api/mcp/v2

# 初始化
curl -X POST http://localhost:8081/api/mcp/v2/initialize \
  -H "Content-Type: application/json" \
  -d '{"protocolVersion":"2026-07-28","clientInfo":{"name":"curl-demo"}}'

# 无状态列出工具
curl http://localhost:8081/api/mcp/v2/tools

# 无状态调用工具
curl -X POST http://localhost:8081/api/mcp/v2/tools/call \
  -H "Content-Type: application/json" \
  -d '{"name":"system_info","arguments":{}}'
```

## 八、企业接入建议

| 场景 | 推荐方式 |
|------|---------|
| 后端微服务调用 | Java HttpClient / Spring AI |
| 运维脚本 / 数据同步 | Python 标准库 |
| 接口调试 / CI 验证 | curl |
| AI Agent 自然语言交互 | Spring AI Alibaba + MCP |
| 无状态 / 多 Agent 共享 | Streamable HTTP v2 |

## 九、总结

本文提供了从 `curl` 到 `Spring AI Alibaba` 的完整 MCP 客户端接入路径。
企业可以根据自身技术栈选择最低成本的接入方式，快速让现有系统具备 AI Agent 调用能力。

下一步建议：

1. 阅读项目 `docs/quickstart.md` 完成 Server 部署
2. 参考 `docs/alibaba-integration-guide.md` 接入通义千问
3. 查看 `examples/` 目录获取更多语言示例

---

**相关链接**

- GitHub: https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise
- 快速上手指南：docs/quickstart.md
- Alibaba 集成指南：docs/alibaba-integration-guide.md
