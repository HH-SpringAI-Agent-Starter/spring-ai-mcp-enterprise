# Spring Boot + MCP Server：企业级 AI Agent 工具框架实战

> 作者：MCP Enterprise 团队 | 2026年7月

---

## 一、MCP 为什么是 Java 开发者的下一个风口？

2025年底，Anthropic 将 MCP (Model Context Protocol) 移交给 Linux Foundation 治理。紧接着 OpenAI 宣布支持 MCP、Google Gemini API 加入 MCP 集成、Spring AI 原生提供 MCP SDK。

**MCP 正在成为 AI Agent 时代的 "USB-C 接口"**——一套让 AI 应用统一调用企业后端系统的标准协议。

而 Java/Spring Boot 生态作为企业后端的事实标准，天然需要一套与之匹配的 MCP 企业级框架。

## 二、痛点分析：为什么现有的方案不够？

| 方案 | 语言 | 企业功能 | Spring 兼容 | 适合中国企业 |
|------|------|----------|------------|------------|
| agentic-trust | Python | 部分 | ❌ | ❌ |
| open-mcp | Python | 无 | ❌ | ❌ |
| mcp-rs | Rust | 无 | ❌ | ❌ |
| 自建 | 任意 | 需从头开发 | 需大量适配 | ✅ 但成本高 |
| **MCP Enterprise** | **Java** | **✅ 完整** | **✅ 原生** | **✅✅** |

## 三、实战：5 分钟搭建企业级 MCP Server

### 3.1 项目结构

Spring AI MCP Enterprise 采用模块化设计：

```
spring-ai-mcp-enterprise/
├── mcp-core/               # 核心库：协议、模型、安全
├── mcp-spring-boot-starter/ # 一键集成 Starter
├── mcp-server/             # 可运行服务（入口）
├── mcp-tools/              # 预置工具集
│   ├── tool-database/      # 数据库查询
│   ├── tool-search/        # 搜索
│   └── tool-system/        # 系统命令
└── mcp-monitor/            # 监控中心
```

### 3.2 核心能力

**🔐 企业安全层（开箱即用）：**
- API Key 认证：每个客户端独立密钥
- RBAC 角色鉴权：admin/user/readonly 分级
- Rate Limit：按工具设置每秒调用上限
- 审计日志：全链路调用记录
- IP 白名单：网络层访问控制

**🔧 工具注册中心：**
- SPI 机制自动发现工具类
- 运行时热注册/注销
- 按分类/权限查询
- 健康检查

**📊 监控告警：**
- Actuator 端点暴露内部状态
- Prometheus 指标采集
- Grafana 可视化面板

### 3.3 代码示例

**创建自定义 MCP 工具：**

```java
@Component
public class UserQueryTool implements McpTool {
    
    @Override
    public String getName() { return "user-query"; }
    
    @Override
    public String getDescription() { return "查询用户信息"; }
    
    @Override
    public ToolResult execute(ToolInput input) {
        String userId = input.getString("userId");
        // 业务逻辑...
        return ToolResult.success(Map.of("name", "张三", "email", "zhang@example.com"));
    }
}
```

**Spring Boot 配置：**

```yaml
mcp:
  enterprise:
    security:
      api-key-enabled: true
      rate-limit-enabled: true
      audit-log-enabled: true
    registry:
      auto-scan-enabled: true
      scan-packages: com.yourcompany.tools
```

### 3.4 与 Spring AI Alibaba 集成（🇨🇳 国内首选）

国内企业使用通义千问 + DashScope，只需添加 `mcp-alibaba` 模块：

```xml
<dependency>
    <groupId>com.mcp.enterprise</groupId>
    <artifactId>mcp-alibaba</artifactId>
    <version>0.0.2-SNAPSHOT</version>
</dependency>
```

配置 application.yml：

```yaml
spring:
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}
    mcp:
      server:
        endpoints:
          - url: http://localhost:8081/api/mcp
            headers:
              X-API-Key: ${MCP_API_KEY}

mcp:
  enterprise:
    integration:
      alibaba:
        enabled: true
        chat-model: qwen-max
        auto-detect-cloud: true
```

启动指定 profile：

```bash
export DASHSCOPE_API_KEY=sk-xxxx
export MCP_API_KEY=admin-key
cd mcp-server
mvn spring-boot:run -Dspring-boot.run.profiles=alibaba
```

**自动检测阿里云 ECS 环境**、**自动配置 DashScope 客户端**、**集成到 Spring AI ChatClient**。

### 3.5 Spring AI MCP Client 示例（生产推荐）

`mcp-examples/mcp-client-spring-ai` 模块演示了生产级调用方式：

```java
@SpringBootApplication
public class McpClientApplication {
    public static void main(String[] args) {
        SpringApplication.run(McpClientApplication.class, args);
    }
}

@Component
public class DemoRunner implements CommandLineRunner {
    @Override
    public void run(String... args) {
        // Spring AI 的 ChatClient 自动集成 MCP 工具
        String result = chatClient.prompt()
            .user("查询当前服务器信息")
            .call()
            .content();
        // ChatClient 自动发现并调用 MCP Enterprise Server 的工具
    }
}
```

application.yml 配置：

```yaml
spring:
  ai:
    mcp:
      client:
        enabled: true
        endpoints:
          - url: http://localhost:8081/api/mcp
            headers:
              X-API-Key: ${MCP_API_KEY}
```

## 四、企业应用场景

| 场景 | 说明 | 适合企业 |
|------|------|---------|
| 内部知识库 AI 查询 | MCP Server 对接文档库 → AI Agent 问答 | 所有 |
| 数据库自然语言查询 | 自然语言 → SQL → 执行 → 返回结果 | 有业务系统的企业 |
| 流程自动化审批 | MCP 调用审批接口，AI 辅助决策 | 审批流程多的企业 |
| ERP/CRM 智能助手 | MCP 对接 SAP/用友/金蝶 | 大型企业 |
| API 网关 AI 化 | 现有 API 通过 MCP 暴露给 AI | 技术团队 |

## 五、市场机会

MCP 2026年处于**基础设施窗口期**，类比 2013 年的 Docker：

1. **大厂纷纷入场**：OpenAI、Google、微软、Anthropic、Spring AI 全部支持
2. **企业需求真实**：招标网出现大量 "AI Agent 工具集成" 需求
3. **Java 版空白**：MCP 市场几乎只有 Python 方案，Java/Spring 版是蓝海
4. **AI 基础设施投资持续**：2026年 Together AI C轮8亿美元，SAP 10亿欧元收购 Prior Labs

## 六、快速上手

```bash
git clone https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise.git
cd spring-ai-mcp-enterprise
mvn clean install -DskipTests
cd mcp-server
mvn spring-boot:run

# 验证
curl http://localhost:8081/api/mcp/health
```

## 七、总结

Spring AI MCP Enterprise 不是又一个 "AI 玩具"——它解决的是企业级 AI Agent **怎么安全、可控、可审计地对接企业系统**这个真实问题。

如果你：
- 是 Java/Spring Boot 开发者，想进入 AI 赛道
- 所在企业正在探索 AI Agent 落地
- 想寻找一个利基市场的开源项目

👉 **欢迎 Star / Fork / PR：**
https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise

---

*Apache 2.0 开源协议 · 欢迎贡献*
