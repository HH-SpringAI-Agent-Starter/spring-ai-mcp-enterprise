# 🚀 MCP Enterprise Server — 快速上手指南

**5 分钟让你的 Spring Boot 应用具备 MCP Server 能力**

---

## 前提条件

- JDK 17+
- Maven 3.8+
- （可选）Docker 24+

## 一、下载 & 启动

```bash
# 1. 克隆项目
git clone https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise.git
cd spring-ai-mcp-enterprise

# 2. 编译
mvn clean install -DskipTests

# 3. 启动服务
cd mcp-server
mvn spring-boot:run
```

启动后看到类似日志：
```
MCP Enterprise Server 启动完成
默认 API Key (管理员): a1b2c3d4e5f6...
默认 API Key (用户):   f6e5d4c3b2a1...
注册工具数: 3
```

## 二、验证服务

```bash
# 健康检查
curl http://localhost:8081/api/mcp/health

# 预期返回：
# {"status":"UP","toolCount":3,"activeSessions":0,...}
```

## 三、连接并调用 MCP Server

```bash
# 设置默认 API Key（从启动日志中获取）
export MCP_API_KEY=你的管理员Key

# 1. 连接
curl -X POST http://localhost:8081/api/mcp/connect \
  -H "X-API-Key: $MCP_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"clientName":"my-agent"}'

# 2. 列出工具
curl http://localhost:8081/api/mcp/tools \
  -H "X-API-Key: $MCP_API_KEY"

# 3. 调用工具
curl -X POST http://localhost:8081/api/mcp/tools/<tool-name>/invoke \
  -H "X-API-Key: $MCP_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"param1":"value1"}'
```

## 四、从 Spring Boot 集成

在你的 Spring Boot 项目中添加依赖：

```xml
<dependency>
    <groupId>com.mcp.enterprise</groupId>
    <artifactId>mcp-spring-boot-starter</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

然后在 `application.yml` 中配置：

```yaml
mcp:
  enterprise:
    security:
      api-key-enabled: true
      rate-limit-enabled: true
    registry:
      auto-scan-enabled: true
      scan-packages: com.yourcompany.tools
```

你的工具类只要继承 MCP 工具接口，启动时自动注册。

## 五、更多方式

### Docker 启动

```bash
docker-compose up -d
```

### 查看管理端点

```bash
# Actuator 管理
curl http://localhost:8081/actuator/mcp-enterprise

# 工具列表
curl http://localhost:8081/actuator/mcp-enterprise/tools

# 审计日志
curl http://localhost:8081/actuator/mcp-enterprise/audit
```

---

## 下一步

- 📖 阅读 [架构说明](architecture.md) 了解设计理念
- 🔧 查看 [API 文档](api-docs.md) 完整接口列表
- 🎯 编写你自己的 MCP 工具
