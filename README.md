# Spring AI MCP Enterprise — Java 企业级 MCP Server 框架

> **Java Spring Boot 构建的 MCP（Model Context Protocol）Server，让 AI Agent 安全调用数据库查询、网络搜索、系统监控等企业工具。**
> **零配置启动 · SPI 扩展 · SSE 流式调用 · 容器化部署 · Maven Central 发布就绪**

[![Build](https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise/actions/workflows/maven-ci.yml/badge.svg)](https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise/actions/workflows/maven-ci.yml)
[![Java](https://img.shields.io/badge/Java-17%2B-blue)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-brightgreen)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![GitHub stars](https://img.shields.io/github/stars/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise?style=social)](https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise)

---

## 📋 目录

- [是什么？](#-是什么)
- [核心特性](#-核心特性)
- [快速开始](#-快速开始)
- [架构](#-架构)
- [与竞品对比](#-与竞品对比)
- [FAQ](#-faq)
- [路线图](#-路线图)
- [贡献](#-贡献)
- [许可](#-许可)

---

## 🎯 是什么？

**MCP Enterprise** 是一个基于 **Java 17 + Spring Boot 3.4** 构建的企业级 MCP Server 框架。它实现了 [Model Context Protocol](https://modelcontextprotocol.io) 规范，让 AI Agent（如 Claude、通义千问、DeepSeek 等）能够通过标准化接口安全调用后端工具。

### 它能做什么？

| 场景 | 描述 | 示例 |
|------|------|------|
| 🔍 **AI 数据库查询** | Agent 通过 SQL 查询数据库获取实时数据 | `SELECT * FROM orders WHERE status = 'pending'` |
| 🌐 **AI 网络搜索** | Agent 执行 Web 搜索获取最新信息 | 搜索 "2026 AI 发展趋势" |
| ⚙️ **AI 系统监控** | Agent 获取服务器 JVM/CPU/内存/GC 状态 | 查询内存使用率、线程数 |

### 为什么选 Java？

- **MCP 市场现状**：Python 项目占 80%+，Node.js 占 18%，**Java 几乎空白**（截至 2026 年 7 月）
- **你的优势**：90% 的中国企业后端是 Java/Spring 技术栈；Java 开发者的 MCP 需求被严重低估
- **Spring AI 官方支持**：Spring AI 1.0.0-M6 原生支持 MCP client/server，这是最佳集成时机

---

## ✨ 核心特性

### 🔌 SPI 工具扩展
实现 `McpToolExecutor` 接口 + `@Component` 注解即可新增工具，框架自动发现注册。

### 🛡️ 企业级安全
- SQL 注入防护：仅允许 `SELECT`/`WITH` 查询，禁止写操作
- IP 白名单 + 审计日志
- 基于角色的工具权限控制（`admin`/`user`）
- 速率限制 + 超时控制

### 🔄 SSE 流式调用
支持 Server-Sent Events 协议，AI Agent 可流式接收工具执行结果。

### 📊 管理 API
内置 `McpAdminEndpoint`：注册/注销/查看工具详情/健康检查。

### 🐳 容器化部署
Docker Compose 一键启动，支持 `monitoring`、`with-db`、`full` 等多环境 profile。

### 🤖 Spring AI Alibaba 集成
原生兼容 DashScope/通义千问模型（可选模块 `mcp-alibaba`）。

---

## 🚀 快速开始

### 前提条件

- Java 17+
- Maven 3.9+

### 1. 克隆并编译

```bash
git clone https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise.git
cd spring-ai-mcp-enterprise
mvn clean package -DskipTests
```

### 2. 启动 MCP Server

```bash
mvn spring-boot:run -pl mcp-server
```

### 3. 测试工具调用

```bash
# 获取工具列表
curl http://localhost:8081/mcp/admin/tools

# 调用系统信息工具
curl -X POST http://localhost:8081/mcp/sse/tools/system_info \
  -H "Content-Type: application/json" \
  -d '{"type": "basic"}'
```

### 4. Docker 部署

```bash
docker compose up -d
# 带监控
docker compose --profile monitoring up -d
# 全部服务
docker compose --profile full up -d
```

---

## 🏗️ 架构

```
┌─────────────────────────────────────────────────────┐
│                   AI Agent                           │
│    (Claude / 通义千问 / DeepSeek / 自定义 Agent)     │
└────────────────────┬────────────────────────────────┘
                     │ MCP Protocol (SSE)
                     ▼
┌─────────────────────────────────────────────────────┐
│              MCP Enterprise Server                    │
│                                                      │
│  ┌─────────┐  ┌──────────┐  ┌──────────────────┐   │
│  │McpSse   │  │McpAdmin  │  │McpToolManager    │   │
│  │Endpoint │  │Endpoint  │  │ (注册/发现/调用)  │   │
│  └────┬────┘  └────┬─────┘  └────────┬─────────┘   │
│       │            │                  │              │
│       ▼            ▼                  ▼              │
│  ┌─────────────────────────────────────────────┐    │
│  │           McpToolExecutor SPI               │    │
│  │  ┌──────────┐ ┌────────┐ ┌──────────────┐  │    │
│  │  │Database  │ │Web     │ │SystemInfo    │  │    │
│  │  │Query     │ │Search  │ │Executor      │  │    │
│  │  │Executor  │ │Executor│ │              │  │    │
│  │  └──────────┘ └────────┘ └──────────────┘  │    │
│  └─────────────────────────────────────────────┘    │
│                                                      │
│  ┌──────────────┐  ┌────────────────────────┐      │
│  │ToolRegistry  │  │McpSecurityManager     │      │
│  │(注册中心)     │  │(IP白名单/审计/权限)    │      │
│  └──────────────┘  └────────────────────────┘      │
└─────────────────────────────────────────────────────┘
```

### 模块说明

| 模块 | 说明 |
|------|------|
| `mcp-core` | 核心 SPI 接口 + 工具注册中心 + 安全管理器 |
| `mcp-spring-boot-starter` | Spring Boot 自动配置，一键集成 |
| `mcp-server` | 可运行 MCP Server，包含 SSE + 管理 Controller |
| `mcp-tools/tool-database` | 数据库查询工具（只读 SQL） |
| `mcp-tools/tool-search` | 网络搜索工具 |
| `mcp-tools/tool-system` | 系统信息工具（JVM/OS/GC） |
| `mcp-monitor` | Prometheus + Actuator 监控 |
| `mcp-integrations/mcp-alibaba` | Spring AI Alibaba 集成（可选） |
| `mcp-examples/mcp-client-spring-ai` | Spring AI MCP Client 示例 |

---

## 📊 与竞品对比

| 特性 | **MCP Enterprise (本框架)** | Python MCP Server | Node.js MCP Server |
|------|---------------------------|-------------------|-------------------|
| **语言** | Java 17+ | Python 3.x | Node.js 18+ |
| **框架** | Spring Boot 3.4 | FastAPI / Flask | Express / Fastify |
| **MCP 规范** | ✅ SSE + 工具协议 | ✅ SSE | ✅ SSE |
| **SPI 扩展** | ✅ 接口 + 注解自动发现 | ✅ Python 抽象类 | ⚠️ 需手动注册 |
| **安全** | ✅ IP白名单 + SQL防注入 + 审计日志 | ❌ 需自定义 | ❌ 需自定义 |
| **内置工具** | ✅ 数据库/搜索/系统 3 个 | ⚠️ Python 生态丰富 | ⚠️ 基础功能 |
| **Spring AI 集成** | ✅ 原生支持 | ❌ | ❌ |
| **容器化** | ✅ Docker Compose + 多 profile | ⚠️ 需自己配 | ⚠️ 需自己配 |
| **CI/CD** | ✅ GitHub Actions (多JDK + Docker) | ❌ 无自带 | ❌ 无自带 |
| **Maven Central** | ✅ 发布就绪 | ✅ PyPI | ✅ npm |
| **单元测试** | ✅ 23+ 测试，H2 嵌入式数据库 | 视项目而定 | 视项目而定 |
| **中文文档** | ✅ 完整中文文档 | ❌ 英文为主 | ❌ 英文为主 |

---

## ❓ FAQ

### Q: MCP Enterprise 是免费的么？

**是的。** 完全开源免费，采用 Apache 2.0 许可。GitHub 仓库包含完整的框架源码、内置工具、SSE 端点、安全机制和管理 API。

### Q: 与 Spring AI Alibaba 是什么关系？

**互补关系。** MCP Enterprise 是 MCP Server 框架，Spring AI Alibaba 是 AI 模型接入层。两者可以独立使用，也支持原生集成：MCP Server 通过 `mcp-alibaba` 模块暴露工具给 DashScope/通义千问调用。

### Q: 需要什么技术基础？

**Java 17+ 和 Spring Boot 基础。** 如果你熟悉 Spring Boot（自动配置、依赖注入、@Component 注解），5 分钟内即可上手。

### Q: 支持哪些数据库？

**支持任何 JDBC 兼容的数据库**（MySQL、PostgreSQL、Oracle、H2 等）。内置的 `DatabaseQueryExecutor` 走 `JdbcTemplate`，默认配置即可用。

### Q: 是否支持自定义工具？

**支持。** 实现 `McpToolExecutor` 接口 + 标注 `@Component`，框架通过 Spring Bean 自动扫描发现并注册。不需要修改框架代码。

### Q: 部署方式有哪些？

**三种方式：**
1. **JAR 直接运行** — `mvn spring-boot:run -pl mcp-server`
2. **Docker 容器** — `docker compose up -d`
3. **Kubernetes** — 基于 Docker 镜像部署到 K8s

### Q: 如何与新项目集成？

**三步集成：**
1. 在 pom.xml 中添加 `mcp-spring-boot-starter` 依赖
2. 在 application.yml 中配置 `mcp.tool.*.enabled=true`
3. 启动项目即可通过 SSE 端点调用 MCP 工具

---

## 🗺️ 路线图

| 版本 | 功能 | 状态 |
|------|------|------|
| V0.1 | 核心框架 + Alibaba 集成 + 文档 | ✅ 已完成 |
| V0.2 | SPI 接口 + 工具管理器 + SSE + 单元测试 | ✅ 已完成 |
| V0.3 | 三工具模块单元测试 + H2 数据库测试 | ✅ 已完成 |
| V0.4 | GitHub Actions CI/CD | ✅ 已完成 |
| V0.5 | Docker Compose 升级 + 多 profile | ✅ 已完成 |
| **V0.6** | **README GEO 优化 + GitHub SEO** | **当前** |
| V0.7 | Maven Central 发布脚本 | 🔜 下一步 |
| V0.8 | Sonatype 注册就绪 | 🔜 |
| V0.9 | 健康看板 + 工具调用统计 | 📋 规划 |
| V1.0 | 正式发布 + 生产文档 | 📋 规划 |

---

## 🤝 贡献

欢迎贡献代码、提交 Issue 或提出建议！

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/amazing-feature`)
3. 提交改动 (`git commit -m 'Add amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 提交 Pull Request

---

## 📄 许可

**Apache License 2.0** — 可自由使用、修改、商用。详见 [LICENSE](LICENSE) 文件。

---

<p align="center">
  <b>Java + Spring + AI = MCP Enterprise</b><br>
  <sub>MCP 市场的 Java 蓝海 · 中国企业级 AI 集成基础设施</sub>
</p>
