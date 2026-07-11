# 🇨🇳 Spring AI Alibaba MCP 集成指南

> 让 MCP Enterprise Server 原生兼容阿里云通义千问 / DashScope 生态

---

## 为什么需要 Alibaba 集成？

国内企业使用 MCP Server 时，AI 后端通常是阿里云通义千问（DashScope）而非 OpenAI。
MCP Enterprise Server 的 `mcp-alibaba` 模块提供零配置集成。

## 快速集成

### 1. 添加 Maven 依赖

```xml
<dependency>
    <groupId>com.mcp.enterprise</groupId>
    <artifactId>mcp-alibaba</artifactId>
    <version>0.0.2-SNAPSHOT</version>
</dependency>
```

### 2. 配置环境变量

```bash
# 阿里云 DashScope API Key（必填）
export DASHSCOPE_API_KEY=sk-xxxxxxxxxxxx

# MCP Server API Key
export MCP_API_KEY=your-admin-key
```

### 3. 配置 application.yml

```yaml
spring:
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}
    # 聊天模型
    chat:
      model: qwen-max
    # 嵌入模型
    embedding:
      model: text-embedding-v3

mcp:
  enterprise:
    integration:
      alibaba:
        enabled: true
        chat-model: qwen-max
        embedding-model: text-embedding-v3
        mcp-client-auto-connect: true
```

### 4. 启动

```bash
# 方式一：指定 alibaba profile
java -jar mcp-server.jar --spring.profiles.active=alibaba

# 方式二：使用集成模块
mvn spring-boot:run -pl mcp-server
```

---

## 配置参考

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `mcp.enterprise.integration.alibaba.enabled` | `true` | 是否启用 Alibaba 集成 |
| `mcp.enterprise.integration.alibaba.chat-model` | `qwen-max` | 通义千问聊天模型 |
| `mcp.enterprise.integration.alibaba.embedding-model` | `text-embedding-v3` | 嵌入模型 |
| `mcp.enterprise.integration.alibaba.server-port` | `8081` | MCP Server 端口 |
| `mcp.enterprise.integration.alibaba.auto-detect-cloud` | `true` | 自动检测阿里云环境 |
| `mcp.enterprise.integration.alibaba.mcp-client-auto-connect` | `true` | 自动连接 MCP Server |
| `mcp.enterprise.integration.alibaba.connect-timeout` | `10` | 连接超时(秒) |
| `mcp.enterprise.integration.alibaba.read-timeout` | `60` | 读取超时(秒) |

---

## 集成示例

### 通过 Spring AI ChatClient 调用 MCP 工具

```java
@Service
public class McpAgentService {

    private final ChatClient chatClient;

    public McpAgentService(ChatClient.Builder builder) {
        // Spring AI 自动从 MCP Server 获取工具并注入 ChatClient
        this.chatClient = builder.build();
    }

    public String querySystemInfo() {
        return chatClient.prompt()
            .user("查看当前服务器系统信息")
            .call()
            .content();
    }
}
```

### 通义千问 Agent + MCP 工具

```java
@RestController
@RequestMapping("/agent")
public class AgentController {

    @Autowired
    private ChatClient chatClient;

    @PostMapping("/ask")
    public String ask(@RequestBody String question) {
        // MCP 工具会自动传递给通义千问模型
        return chatClient.prompt()
            .user(question)
            .call()
            .content();
    }
}
```

---

## 在阿里云 ECS/ACK 部署

```bash
# 1. 构建 Docker 镜像
docker build -t mcp-enterprise:latest .

# 2. 运行（自动检测阿里云环境）
docker run -d \
  --name mcp-server \
  -p 8081:8081 \
  -e DASHSCOPE_API_KEY=sk-xxx \
  -e MCP_API_KEY=admin-key \
  -e SPRING_PROFILES_ACTIVE=alibaba \
  mcp-enterprise:latest

# 3. 验证
curl http://localhost:8081/api/mcp/health
```

---

## 与阿里云产品对比

| 阿里云产品 | 定位 | MCP Enterprise 定位 |
|-----------|------|-------------------|
| 阿里云百炼 | AI 应用开发平台（托管） | MCP Server 框架（开源） |
| 通义千问 API | 模型调用 | MCP 工具协议层 |
| 阿里云函数计算 | Serverless 计算 | 可与 FC 配合部署 MCP Server |
| 阿里云 API 网关 | API 管理 | 补充 MCP 协议的安全/审计层 |

**最佳实践**：百炼平台调用通义千问 → 通义千问通过 MCP 协议 → 调用 MCP Enterprise Server → 操作企业系统

---

## 常见问题

### Q: DashScope API Key 如何获取？
A: 登录 [阿里云百炼平台](https://bailian.console.aliyun.com/) → 模型广场 → API Key 管理

### Q: Maven 依赖下载失败？
A: 确保 `pom.xml` 中配置了阿里云镜像仓库：
```xml
<repositories>
    <repository>
        <id>aliyun-spring-ai</id>
        <url>https://maven.aliyun.com/repository/spring</url>
    </repository>
</repositories>
```

### Q: 支持其他国内大模型吗？
A: 目前原生支持阿里云 DashScope。百度文心、DeepSeek、月之暗面等可通过 Spring AI 标准 Adapter 接入。
