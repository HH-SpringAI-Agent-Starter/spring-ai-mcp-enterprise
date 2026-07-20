# Spring AI MCP Enterprise — 企业级 MCP Server 框架（Java/Spring Boot）

> **Java Spring Boot 构建的企业级 MCP（Model Context Protocol）Server，让 AI Agent 安全调用数据库查询、网络搜索、系统监控等企业工具。**
> **零配置启动 · SPI 扩展 · SSE 流式调用 · RBAC 权限 · 审计日志 · 容器化部署 · Maven Central 发布就绪**

[![Build](https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise/actions/workflows/maven-ci.yml/badge.svg)](https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise/actions/workflows/maven-ci.yml)
[![Java](https://img.shields.io/badge/Java-17%2B-blue)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-brightgreen)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0.0--M6-orange)](https://spring.io/projects/spring-ai)
[![MCP](https://img.shields.io/badge/MCP-0.8.0-blueviolet)](https://modelcontextprotocol.io)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![GitHub Stars](https://img.shields.io/github/stars/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise?style=social)](https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise)

---

## 📋 目录

- [为什么需要 MCP Enterprise？](#-为什么需要-mcp-enterprise)
- [核心特性](#-核心特性)
- [快速开始（3 分钟）](#-快速开始3-分钟)
- [架构总览](#-架构总览)
- [模块说明](#-模块说明)
- [客户端调用示例](#-客户端调用示例)
- [部署指南](#-部署指南)
- [技术栈](#-技术栈)
- [Spring AI Alibaba 集成](#-spring-ai-alibaba-集成)
- [与 Python MCP 框架对比](#-与-python-mcp-框架对比)
- [FAQ](#-faq)
- [路线图](#-路线图)
- [贡献指南](#-贡献指南)
- [许可](#-许可)

---

## 🎯 为什么需要 MCP Enterprise？

### 现状痛点

中国 **90%+** 的企业后端是 **Java / Spring Boot** 技术栈，但 MCP Server 生态几乎被 **Python（80%）** 和 **Node.js（18%）** 垄断。Java 开发者要搭建 MCP 服务，要么：

1. 用 Python 重写工具层 → 维护两套代码，团队切换成本高
2. 用 Node.js 跑 MCP SDK → 与 Spring 生态脱节，没有统一配置管理
3. 自研 RPC 桥接 → 重复造轮子，缺少标准协议

**MCP Enterprise 就是为填补这个空白而生。**

### MCP 2026-07-28 规范候选版意味着什么？

2026 年 7 月 17 日，MCP 发布了史上最大规模修订的规范候选版（2026-07-28），核心变化包括：

- 🏭 **无状态核心**：支持无状态 Server 架构，便于云原生部署
- 🔍 **能力发现**：Client 可自动发现 Server 能力
- 🧩 **Extensions**：支持第三方扩展
- 🔒 **企业授权加固**：OAuth 2.0 / 身份提供商管控

**MCP Enterprise 已经原生支持这些企业级能力**——RBAC 权限、Rate Limiter、审计日志、OAuth2/SSO 认证。

### 它能做什么？

| 场景 | 描述 | 一句话价值 |
|------|------|-----------|
| 🔍 **AI 数据库查询** | Agent 通过 SQL 获取实时业务数据 | 告别「查一下数据库发截图」 |
| 🌐 **AI 网络搜索** | Agent 调用 Web 搜索获取最新信息 | 2026 趋势、竞品动态实时可查 |
| ⚙️ **AI 系统监控** | Agent 获取 JVM/CPU/内存/GC 状态 | 运维问题 AI 直接诊断 |
| 🗃️ **企业数据治理** | 安全可控的数据查询，SQL 注入防护 | 审计合规无死角 |

---

## ✨ 核心特性

### 🔌 SPI 工具扩展 —— 一行注解注册
```java
@Component
public class MyToolExecutor implements McpToolExecutor {
    @Override
    public String getName() { return "my_tool"; }
    @Override
    public String execute(Map<String, Object> args) {
        return "Hello from " + args.get("name");
    }
}
```

### 🛡️ 企业级安全（对标生产环境）
- **SQL 注入防护**：仅允许 `SELECT`/`WITH` 查询，禁止写操作
- **IP 白名单**：限制可调用 MCP 的客户端 IP
- **RBAC 权限**：`admin`/`user`/`viewer` 三级角色控制
- **Rate Limiter**：每分钟/每小时调用次数限制
- **审计日志**：每次工具调用记录谁、何时、做了什么
- **OAuth2/SSO**：企业级统一身份认证（mcp-auth 模块）

### 🔄 SSE 流式调用
支持 Server-Sent Events 协议，AI Agent 可流式接收工具执行结果，支持大结果分片传输。

### 📊 监控体系
- Prometheus 指标暴露
- Grafana 可视化面板
- 工具调用耗时 / 成功率 / 错误率
- 告警规则配置（自定义阈值 + 回调通知）

### 🐳 容器化部署
```bash
# 一键启动
docker compose up -d

# 带监控 + 数据库
docker compose --profile full up -d
```

---

## 🚀 快速开始（3 分钟）

### 前提条件
- Java 17+
- Maven 3.9+
- Docker & Docker Compose（可选，用于容器部署）

### 1. 克隆项目
```bash
git clone https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise.git
cd spring-ai-mcp-enterprise
```

### 2. 启动 MCP Server
```bash
# 方式一：直接启动（推荐开发调试）
mvn clean install -DskipTests -Pfast
cd mcp-server
mvn spring-boot:run

# 方式二：Docker 部署（推荐生产环境）
docker compose up -d
```

### 3. 调用工具
```bash
# 列出可用工具
curl -s http://localhost:8080/api/mcp/tools | jq .

# 调用数据库查询工具
curl -s -X POST http://localhost:8080/api/mcp/execute \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-api-key" \
  -d '{"tool":"database_query","args":{"sql":"SELECT 1 AS test"}}' | jq .

# 调用 Web 搜索工具
curl -s -X POST http://localhost:8080/api/mcp/execute \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-api-key" \
  -d '{"tool":"web_search","args":{"query":"2026 AI trend"}}' | jq .
```

### 4. 从 AI Agent 调用（Spring AI Client）
参考 [`mcp-client-spring-ai`](mcp-examples/mcp-client-spring-ai/) 模块：
```java
McpSyncClient client = McpClient.sync(
    HttpClient.forUrl("http://localhost:8080/api/mcp"),
    McpClientConfig.builder().apiKey("your-api-key").build()
);
List<ToolDefinition> tools = client.listTools();
String result = client.execute("database_query", Map.of("sql", "SELECT * FROM users LIMIT 5"));
```

---

## 🏗️ 架构总览

```
┌─────────────────────────────────────────────────────────────────┐
│                       AI Agent Layer                             │
│  (Claude / 通义千问 / DeepSeek / 文心一言 / 自定义 AI Agent)      │
└──────────────┬──────────────────────────────────────┬──────────┘
               │ SSE / REST API                        │ SSE / REST API
               ▼                                        ▼
┌─────────────────────────────────────────────────────────────────┐
│                     MCP Enterprise Server                        │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────┐  ┌──────────┐  ┌────────┐  ┌──────────────────┐  │
│  │ SSE     │  │ Admin    │  │ Health │  │ Tools            │  │
│  │ Endpoint│  │ Endpoint │  │ Check  │  │ Registry(SPI)    │  │
│  └────┬────┘  └────┬─────┘  └───┬────┘  └────────┬─────────┘  │
│       │            │            │                 │              │
│  ┌────┴────────────┴────────────┴─────────────────┴──────────┐ │
│  │               Security Layer                               │ │
│  │  RBAC · JWT · API Key · IP Whitelist · Rate Limiter       │ │
│  └────────────────────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │               Monitor Layer                                 │ │
│  │  Metrics(Prometheus) · Audit Log · Alert                   │ │
│  └────────────────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌───────────┐  ┌────────────┐                │
│  │ Database    │  │ Web Search│  │ System     │  ← SPI 扩展    │
│  │ Query Tool  │  │ Tool      │  │ Info Tool  │                │
│  └─────────────┘  └───────────┘  └────────────┘                │
└─────────────────────────────────────────────────────────────────┘
```

---

## 📦 模块说明

| 模块 | 说明 | 依赖 |
|------|------|------|
| **mcp-core** | 核心框架：工具注册、安全、SSE 端点 | ✅ 核心模块 |
| **mcp-spring-boot-starter** | Spring Boot Starter：自动配置 + 属性绑定 | 依赖 mcp-core |
| **mcp-server** | 可运行 Server 应用 | 依赖所有模块 |
| **mcp-tools/tool-database** | 数据库查询工具（SELECT 安全模式） | 依赖 starter |
| **mcp-tools/tool-search** | Web 搜索工具 | 依赖 starter |
| **mcp-tools/tool-system** | 系统监控工具（JVM/CPU/内存） | 依赖 starter |
| **mcp-monitor** | 监控 + 审计 + 告警（Prometheus 集成） | 可选 |
| **mcp-auth** | OAuth2/SSO/JWT 企业认证 | 可选 |
| **mcp-integrations/mcp-alibaba** | Spring AI Alibaba DashScope 集成 | 可选 |
| **mcp-examples/mcp-client-spring-ai** | Spring AI Client 调用示例 | 示例 |

---

## 🔌 客户端调用示例

### curl 调用
```bash
# 列出工具
curl -s http://localhost:8080/api/mcp/tools | jq .

# 执行工具
curl -s -X POST http://localhost:8080/api/mcp/execute \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-api-key" \
  -d '{"tool":"database_query","args":{"sql":"SELECT version()"}}'
```

### Java Spring AI Client
```java
// 完整示例见 mcp-examples/mcp-client-spring-ai
McpSyncClient client = McpClient.sync(
    HttpClient.forUrl("http://localhost:8080/api/mcp"),
    McpClientConfig.builder().apiKey("your-api-key").build()
);
client.listTools().forEach(t -> System.out.println(t.getName()));
String result = client.executeTool("web_search", Map.of("query", "MCP Enterprise"));
```

### Python Client（适合数据科学家 / ML 工程师）
```python
import requests, json

api_key = "your-api-key"
base_url = "http://localhost:8080/api/mcp"

# 列出工具
tools = requests.get(f"{base_url}/tools", headers={"X-API-Key": api_key}).json()
print(json.dumps(tools, indent=2, ensure_ascii=False))

# 执行工具
result = requests.post(f"{base_url}/execute",
    headers={"X-API-Key": api_key, "Content-Type": "application/json"},
    json={"tool": "database_query", "args": {"sql": "SELECT NOW()"}}).json()
print(result)
```

### Node.js Client
```javascript
const MCPClient = require('@modelcontextprotocol/sdk').Client;
const client = new MCPClient({
  transport: new HttpTransport('http://localhost:8080/api/mcp', {
    headers: { 'X-API-Key': 'your-api-key' }
  })
});
const tools = await client.listTools();
```

---

## 🐳 部署指南

### 生产部署（Docker Compose）
```bash
# 下载 docker-compose.yml
curl -O https://raw.githubusercontent.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise/main/docker-compose.yml

# 编辑配置
# 修改 MCP_API_KEY、数据库连接等

# 启动
docker compose --profile mysql up -d
```

### Cloud Run（Google Cloud）
```bash
gcloud run deploy mcp-enterprise \
  --source . \
  --set-env-vars "MCP_API_KEY=your-key,SPRING_PROFILES_ACTIVE=cloudrun" \
  --allow-unauthenticated
```

---

## 🛠️ 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 17+ | 开发语言 |
| Spring Boot | 3.4.13 | 应用框架 |
| Spring AI | 1.0.0-M6 | AI/LLM 集成 |
| MCP SDK (Java) | 0.8.0 | MCP 协议实现 |
| Spring AI Alibaba | 1.0.0-M6.1 | 通义千问 DashScope 集成 |
| Prometheus | 最新 | 监控采集 |
| Grafana | 最新 | 监控可视化 |
| MySQL | 8.0+ | 审计日志持久化 |

---

## 🤖 Spring AI Alibaba 集成

`mcp-alibaba` 模块让 MCP Enterprise 原生对接 **通义千问（DashScope）**：

```properties
# application-alibaba.yml
spring:
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}
    mcp:
      server:
        url: http://localhost:8080/api/mcp
```

通义千问 Agent 可以直接通过 MCP 协议调用你注册的工具。示例代码见 [`mcp-examples/mcp-client-spring-ai`](mcp-examples/mcp-client-spring-ai/)。

---

## 📊 与 Python MCP 框架对比

| 维度 | MCP Enterprise (Java) | Python MCP SDK |
|------|----------------------|----------------|
| **运行时** | JVM (Java 17+) | Python 3.9+ |
| **企业安全** | ✅ 内置 RBAC / JWT / 审计日志 / Rate Limiter | ❌ 需自行实现 |
| **Spring 生态** | ✅ 原生集成（配置、依赖注入、AOP） | ❌ 不兼容 |
| **Spring AI** | ✅ 原生支持 | ❌ 需额外桥接 |
| **性能** | 🚀 高并发（虚拟线程 + 响应式） | 🐢 GIL 限制 |
| **监控** | ✅ Prometheus + Grafana 开箱即用 | ❌ 需额外配置 |
| **容器化** | ✅ Docker Compose 多 profile | ✅ Docker |
| **Maven Central** | ✅ 发布就绪 | ✅ PyPI |
| **Java 开发者** | ✅ 零学习成本 | ❌ 需学习 Python |
| **中国企业适配** | ✅ 阿里云 / 通义千问 / 国产数据库 | ⚠️ 部分支持 |

**一句话：Java 团队做 MCP 就用 MCP Enterprise。**

---

## ❓ FAQ

### Q: MCP Enterprise 和普通 MCP Server 有什么区别？
A: 普通 MCP SDK 只提供协议层支持。MCP Enterprise 在此之上增加了企业级安全、监控、审计、认证、多环境部署等生产环境所需的能力。

### Q: 没有数据库也可以跑吗？
A: 可以。`mcp-server` 内嵌 H2 数据库，零配置即可启动。生产环境可切换到 MySQL。

### Q: 这项目是开源的吗？
A: 是的。**Apache 2.0 许可证**，完全免费，可商用。代码在 [GitHub](https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise)。

### Q: 如何贡献代码？
A: 看 [CONTRIBUTING.md](CONTRIBUTING.md)。提交 Issue 或 PR 都可以，欢迎 Java 开发者参与。

### Q: 支持哪些 AI 模型？
A: 任何支持 MCP 协议的 AI Agent 都可以调用：Claude、通义千问、DeepSeek、GPT（通过 MCP 桥接）、文心一言等。

### Q: 部署需要多大资源？
A: 最小配置 256MB 内存 + 1 核 CPU 即可运行。推荐生产配置 1GB 内存 + 2 核 CPU。

---

## 🗺️ 路线图

### V0.x（当前 — 验证核心）
- [x] V0.1 核心框架：工具注册、SPI 扩展、SSE 端点
- [x] V0.2 安全层：RBAC、SQL 注入防护、IP 白名单
- [x] V0.3 三工具模块：数据库、搜索、系统监控
- [x] V0.4 GitHub Actions CI/CD
- [x] V0.5 Docker Compose 多环境部署
- [x] V0.6 SEO 优化 + README 英文版
- [x] V0.7 Maven Central 发布准备
- [x] V0.8 mcp-auth OAuth2/SSO 企业认证模块
- [x] V0.9 mcp-monitor 监控/审计/告警模块
- [x] **V0.10 中文社区推广 + 万星增长计划**
- [x] **V0.11 MCP 2026-07-28 规范全面适配（无状态核心/Extensions/Tasks）**
- [x] **V0.12 WAIC 2026 市场更新 + 版本升级 ← 当前**
- [ ] V0.13 MCP Apps 子协议支持
- [ ] V0.14 更多工具：Redis、Kafka、文件系统
- [ ] V0.15 WebSocket 传输支持
- [ ] V1.0 正式版发布 + Maven Central

---

## 🤝 贡献指南

欢迎任何形式的贡献！提交 Issue、PR、文档改进、Bug 反馈都可以。

1. Fork 本仓库
2. 创建特性分支：`git checkout -b feature/amazing-feature`
3. 提交改动：`git commit -m 'feat: add amazing feature'`
4. 推送到分支：`git push origin feature/amazing-feature`
5. 提交 Pull Request

详细规则见 [CONTRIBUTING.md](CONTRIBUTING.md)。

---

## 📄 许可

Apache License 2.0 — 免费商用，只需保留版权声明。

[查看全文](LICENSE)

---

## ⭐ 支持项目

如果这个项目对你有帮助，**点个 Star ⭐** 是对开源最大的鼓励！

项目地址：https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise

**关注 MCP 2026-07-28 规范发布 —— 这是 Java 开发者进入 MCP 生态的最好时机！**
