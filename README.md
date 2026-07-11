# 🏭 Spring AI MCP Enterprise

**企业级 MCP (Model Context Protocol) Server 框架**

> MCP 是 AI Agent 时代的"USB-C 接口"——让所有 AI 应用统一调用企业工具。
> 这个项目就是 **MCP 的 Spring Boot 企业级实现**。

---

## 📋 项目定位

| 维度 | 内容 |
|------|------|
| **是什么** | 基于 Spring Boot 3.4 + Spring AI MCP 的企业级 MCP Server |
| **解决什么** | 企业需要把内部系统(数据库/ERP/CRM/文档)暴露给 AI Agent，但缺安全/审计/管理框架 |
| **客户** | 需要接入 AI 的中大型企业、MCP 开发者、AI Agent 平台 |
| **对标** | agentic-trust(暂无Java版)、open-mcp(纯Python)、mcp-rs(Rust) |
| **核心特色** | **Java生态原生** + Spring Boot Starter 一键集成 + 企业安全(RBAC/审计/RateLimit) |

---

## 🏗 架构

```
┌─────────────────────────────────────────────────┐
│                  AI Agent / LLM                   │
│     (Claude / GPT / 通义千问 / 私有大模型)          │
└────────────────────┬────────────────────────────┘
                     │ MCP Protocol (HTTP/SSE/Stdio)
┌────────────────────▼────────────────────────────┐
│              MCP Enterprise Server                │
│                                                   │
│  ┌──────────┐  ┌──────────┐  ┌────────────────┐ │
│  │ REST API │  │ MCP SDK  │  │ Web管理控制台   │ │
│  └────┬─────┘  └────┬─────┘  └───────┬────────┘ │
│       └──────────────┼────────────────┘           │
│                      ▼                            │
│  ┌────────────────────────────────────────────┐  │
│  │          Tool Registry (注册中心)           │  │
│  │  (注册/发现/健康检查/版本管理/热加载)        │  │
│  └──────────┬──────────┬──────────┬───────────┘  │
│             ▼          ▼          ▼               │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐             │
│  │ DB工具  │ │ 搜索工具  ││ 系统工具 │   ...       │
│  └─────────┘ └─────────┘ └─────────┘             │
│                                                   │
│  ┌──────┐  ┌───────────┐  ┌────────────────┐     │
│  │安全层│  │ 审计日志   │  │  监控/告警      │     │
│  │(RBAC)│  │ (全记录)   │  │  (指标/Prom)   │     │
│  └──────┘  └───────────┘  └────────────────┘     │
└─────────────────────────────────────────────────┘
```

## 📁 模块结构

```
spring-ai-mcp-enterprise/
├── mcp-core/                      # 核心库：协议、安全、注册中心
│   └── src/main/java/com/mcp/enterprise/core/
│       ├── model/                 # 数据模型
│       ├── registry/              # 工具注册中心
│       └── security/              # 安全组件(RBAC/RateLimit/审计)
├── mcp-spring-boot-starter/       # Spring Boot Starter 自动配置
│   └── src/main/java/com/mcp/enterprise/autoconfigure/
├── mcp-server/                    # 可运行服务
│   └── src/main/java/com/mcp/enterprise/server/
├── mcp-tools/                     # 内置工具集合
│   ├── tool-database/             # 数据库查询工具
│   ├── tool-search/               # 搜索查询工具
│   └── tool-system/               # 系统命令工具
├── mcp-monitor/                   # 监控中心
│   └── src/main/java/com/mcp/monitor/
├── examples/                      # 客户端示例
│   ├── client-java/               # Java SDK 示例 (纯JDK HttpClient)
│   ├── client-python/             # Python 客户端示例
│   └── curl-examples.sh           # curl 调用脚本
├── docs/                          # 文档
│   ├── quickstart.md              # 快速上手指南
│   ├── architecture.md            # 架构说明
│   ├── api-docs.md                # API 文档
│   ├── blog-java-mcp-enterprise.md # 掘金/CSDN 博客稿件
│   └── market-research-2026-07.md # MCP 市场机会报告
├── .github/workflows/             # GitHub Actions CI/CD
│   └── maven-ci.yml               # 自动构建+测试+Docker推送
├── Dockerfile                     # 多阶段构建
├── docker-compose.yml             # Docker Compose (Server+Prometheus+Grafana)
├── config/prometheus/             # Prometheus 监控配置
└── mcp-server/src/main/resources/
    └── application-alibaba.yml    # Spring AI Alibaba 集成配置
```

---

## 🚀 快速开始

### 前提条件

- JDK 17+
- Maven 3.8+

### 启动服务

```bash
cd spring-ai-mcp-enterprise
mvn clean install -DskipTests
cd mcp-server
mvn spring-boot:run
```

服务启动后：
- MCP API: `http://localhost:8081/api/mcp/`
- 健康检查: `http://localhost:8081/actuator/health`
- MCP Enterprise Management: `http://localhost:8081/actuator/mcp-enterprise`

### 使用示例

```bash
# 1. 连接服务
curl -X POST http://localhost:8081/api/mcp/connect \
  -H "X-API-Key: {your-api-key}" \
  -d "clientName=my-ai-agent"

# 2. 列出可用工具
curl http://localhost:8081/api/mcp/tools

# 3. 查看服务状态
curl http://localhost:8081/api/mcp/health
```

---

## 💰 挣钱路线图

### 🚀 开源阶段（你做的事）
| 阶段 | 内容 | 时间 |
|------|------|------|
| V0.1 | 核心框架+安全+示例工具 | 本周 |
| V0.2 | 开放API+文档+GitHub Action CI | 第2周 |
| V0.3 | Spring Boot Starter 发布到 Maven Central | 第3周 |
| V0.4 | Web管理控制台（Vue3） | 第1个月 |
| V0.5 | 插件市场SPI（第三方可开发工具插件） | 第2个月 |
| V1.0 | 企业版功能：SSO/LDAP/集群/Prometheus | 第3个月 |

### 💼 挣钱模式（6种）

| # | 模式 | 目标客户 | 月收入预估 | 难度 |
|---|------|---------|-----------|------|
| ① | **MCP Server 部署实施** | 有AI需求但不会MCP的企业 | ¥3-20万/单 | ⭐⭐ |
| ② | **企业定制工具开发** | 需要对接内部系统的企业 | ¥5-30万/单 | ⭐⭐⭐ |
| ③ | **开源版→商业版升级** | 运维团队/企业IT | ¥1-5万/年 | ⭐ |
| ④ | **MCP 培训+咨询** | 开发团队转型AI | ¥5000-2万/天 | ⭐⭐ |
| ⑤ | **云托管MCP平台** | 中小企业 | ¥999-9999/月 | ⭐⭐⭐⭐ |
| ⑥ | **企业知识库插件** | 需要RAG的企业 | ¥2-10万/单 | ⭐⭐ |

### 🎯 起步优先级

**今天到第1个月：你一个人就能做的**

1. **接单**：上 Upwork/Freelancer/猪八戒，搜索 "MCP Server" "AI Agent" "Spring AI"
   - 客单价 ¥5-30万，需求真实存在
   - 用这个项目做 demo 去谈客户

2. **开源+SEO**：把项目推上 GitHub，写高质量 README 和文档
   - GitHub Stars > 500 → 自然流量
   - ChatGPT/Perplexity 搜索 "Spring Boot MCP" 时能搜到

3. **企业内推**：在 Spring / Java / AI 群和社区发项目
   - 适合的企业场景：内部知识库AI对接、数据库AI查询、流程自动化

---

## 🔥 为什么MCP是风口

```
┌─────────────────────────────────────┐
│        2025-2026 AI Agent 浪潮        │
├─────────────────────────────────────┤
│                                     │
│  LLM → AI Agent → MCP 工具协议      │
│                                     │
│  Anthropic 发布 MCP → 行业标准       │
│  OpenAI 跟进 → 微软/Google 支持      │
│  Spring AI 原生支持 → Java生态接入    │
│  YC W25/S25 连续押注 (agentic-trust) │
│  GitHub MCP 相关项目年增长 >500%     │
│                                     │
│  → 这就是2013年的Docker/AWS Lambda   │
│  → 基础设施窗口期 12-18个月           │
│  → 谁先做完整的企业Java版 = 赢家      │
└─────────────────────────────────────┘
```

---

## 🔗 相关资源

- [MCP 官方协议](https://modelcontextprotocol.io/)
- [Spring AI MCP](https://docs.spring.io/spring-ai/reference/api/mcp.html)
- [Agentic Trust (YC S25)](https://www.agentictrust.com/)
- [open-mcp (Python)](https://github.com/open-mcp)

---

## 📄 开源协议

Apache License 2.0

---

## 🌟 如果你觉得有用...

- ⭐ 给这个项目点 Star
- 🔀 Fork 后开发自己的工具
- 📢 分享给你的 Java/AI 朋友
- 🐛 提 Issue 或 PR
