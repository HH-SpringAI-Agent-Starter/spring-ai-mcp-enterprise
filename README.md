# Spring AI MCP Enterprise — Java 企业级 MCP Server 框架

> **Java Spring Boot 构建的 MCP（Model Context Protocol）Server，让 AI Agent 安全调用数据库查询、网络搜索、系统监控等企业工具。**
> **零配置启动 · SPI 扩展 · Streamable HTTP 无状态调用 · 容器化部署 · Maven Central 发布就绪**

[![Build](https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise/actions/workflows/maven-ci.yml/badge.svg)](https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise/actions/workflows/maven-ci.yml)
[![Java](https://img.shields.io/badge/Java-17%2B-blue)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-brightgreen)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![GitHub stars](https://img.shields.io/github/stars/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise?style=social)](https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise)
[![中文](https://img.shields.io/badge/🇨🇳-中文文档-brightgreen)](README.zh-CN.md)

> **🇨🇳 [中文版 README](README.zh-CN.md)** — 更适合中国 Java 开发者阅读

---

## 📋 目录

- [是什么？](#-是什么)
- [核心特性](#-核心特性)
- [快速开始](#-快速开始)
- [架构](#-架构)
- [与竞品对比](#-与竞品对比)
- [MCP 2026-07-28](#-mcp-2026-07-28-规范)
- [FAQ](#-faq)
- [路线图](#-路线图)
- [贡献](#-贡献)
- [许可](#-许可)
- [贡献](#-贡献-1)

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

### 🔐 OAuth2 / EMA 企业授权（V1.8+，M2M 凭证）

把「长期共享 API Key」升级为「短期令牌 + 轮换刷新」，并支持委托企业 IdP 集中鉴权（EMA，Enterprise-Managed Authorization，已被 Anthropic/Microsoft 及主流 SaaS 采纳）：

| 端点 | 说明 |
| --- | --- |
| `POST /oauth2/token` | Client Credentials 签发 / Refresh Token 轮换换发 |
| `GET /oauth2/introspect` | RFC 7662 令牌内省（供网关/资源服务器校验） |
| `POST /oauth2/revoke` | RFC 7009 令牌吊销（access_token / refresh_token） |
| `POST /oauth2/clients` | 注册 OAuth2 客户端（返回一次性明文 secret） |
| `DELETE /oauth2/clients/{id}` | 吊销客户端 |
| `GET /oauth2/stats` | 活动客户端数 / TTL / EMA 委托状态 |

**V1.9 安全增强：**
- **Refresh Token 轮换**：每次刷新换发全新 access+refresh 对，旧 refresh 立即作废
- **重用检测**：被轮换的 refresh token 再次使用 → 判定泄露，**整族吊销**（防重放）
- **网关 Bearer 自动校验**：`mcp.enterprise.security.oauth2.enforce-bearer=true` 开启后，所有非公开路径强制 `Authorization: Bearer` 校验（Fail-Closed），校验结果（client/scope/roles）以 `request attribute: mcp.tokenInfo` 暴露给下游
- **jti 防碰撞**：同一秒内签发的令牌也全局唯一（防重放/防碰撞）

```yaml
mcp:
  enterprise:
    security:
      oauth2:
        signing-key: ${OAUTH2_SIGNING_KEY}   # 生产环境务必使用强随机密钥
        token-ttl-seconds: 3600              # access token 有效期
        refresh-token-ttl-seconds: 2592000   # refresh token 有效期（默认 30 天）
        enforce-bearer: false                # true = 网关强制 Bearer 校验（Fail-Closed）
```

```bash
# 1. 注册客户端 → 拿到 client_secret
curl -s -X POST 'http://localhost:8080/oauth2/clients?clientId=agent-1&scopes=tools:read%20tools:call'
# 2. 签发 access_token + refresh_token
curl -s -X POST 'http://localhost:8080/oauth2/token' \
  -d 'grant_type=client_credentials&client_id=agent-1&client_secret=<SECRET>'
# 3. 调用工具（Bearer 模式）
curl -s 'http://localhost:8080/api/mcp/v2/message' -H 'Authorization: Bearer <ACCESS_TOKEN>' ...
# 4. 刷新轮换
curl -s -X POST 'http://localhost:8080/oauth2/token' \
  -d 'grant_type=refresh_token&client_id=agent-1&client_secret=<SECRET>&refresh_token=<REFRESH>'
# 5. 吊销
curl -s -X POST 'http://localhost:8080/oauth2/revoke' -d 'token=<TOKEN>&token_type_hint=refresh_token'
```

> 📖 完整指南见 [docs/oauth2-guide.md](docs/oauth2-guide.md)，客户端示例见 `examples/`（Java / Python / Node / curl）

### 🧩 Dify 工作流集成（V1.10）

Dify 可一键挂载本框架为 MCP 工具，让可视化编排的 Agent 直接调用企业数据库/搜索/系统工具：

- 在 Dify → 工具 → 自定义工具 → 添加 MCP 工具，选择 **Streamable HTTP**；
- Server URL 填 `http://<host>:8081/api/mcp/message`，Header 带 `Authorization: Bearer <API_KEY>`；
- 自动拉取工具列表（`database_query` / `search_web` / `execute_command` / finance 等），拖入 Agent 节点即用。

> 📖 完整指南见 [docs/dify-integration-guide.md](docs/dify-integration-guide.md)，导入模板见 `mcp-examples/dify/`

### 🔄 Streamable HTTP 调用（2026-07-28 规范新默认）
支持 **Streamable HTTP 无状态传输**：
- `POST /api/mcp/v2/message` — JSON-RPC 请求/响应（无需 session，可直接挂负载均衡）
- `GET  /api/mcp/v2/stream` — server→client 通知流（tools/listChanged + 15s 心跳）
- `POST /api/mcp/v2/notify` — 工具变更广播

同时兼容 **SSE 流式调用**（2025-03-26 协议，`/api/mcp/sse`），AI Agent 可流式接收工具执行结果。

### 📊 管理 API
内置 `McpAdminEndpoint`：注册/注销/查看工具详情/健康检查。

### 🐳 容器化部署
Docker Compose 一键启动，支持 `monitoring`、`with-db`、`full` 等多环境 profile。

### 🤖 Spring AI Alibaba 集成

原生兼容 **DashScope / 通义千问** 生态（可选模块 `mcp-alibaba`），国内企业零成本接入阿里云 AI 后端。

**你的技术栈是 Spring AI Alibaba？直接兼容：**

```xml
<dependency>
    <groupId>com.mcp.enterprise</groupId>
    <artifactId>mcp-alibaba</artifactId>
    <version>1.0.0</version>
</dependency>
```

```yaml
spring:
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}
mcp:
  enterprise:
    integration:
      alibaba:
        enabled: true
        chat-model: qwen-max          # 通义千问聊天模型
        embedding-model: text-embedding-v3
        mcp-client-auto-connect: true # 自动连接 MCP Server
```

启动后自动：连接 MCP Server → 发现并缓存工具 → 通过 `ChatClient` 让通义千问直接调用 MCP 工具。

```bash
# 默认构建（V1.2 起已包含 alibaba 集成 + Spring AI Client 示例）
mvn clean install

# 向后兼容：历史全量构建命令（行为同默认构建）
mvn clean install -Pfull
```

> 📖 详细配置见 [docs/alibaba-integration-guide.md](docs/alibaba-integration-guide.md)

### 🌐 MCP 联邦网关（V1.25）

**一个入口，聚合公司里所有的 MCP Server。** `mcp-gateway` 把任意多个下游（部门服务、Python FastMCP、第三方 SaaS MCP、被收购的遗留系统）拉进同一治理平面：联邦工具按 `<prefix>__<tool>` 命名空间隔离，注册进 `McpToolManager` 后**自动继承 RBAC / RateLimit / 审计 / 工具级 Scope / 健康检查**——Agent 无需感知上游差异。

- 零依赖 Streamable HTTP 客户端（JDK HttpClient，自动 initialize → initialized → method 握手，兼容单 JSON 与 SSE 响应，api-key Bearer 透传）；
- 启动自动同步（`sync-on-startup`）；同步失败**保留已注册工具**（可用性优先）并标记 unhealthy；`enabled: false` 或 `DELETE /tools` 一键下线上游；
- 管理 REST API：`GET /api/admin/gateway/upstreams` 状态、`POST /api/admin/gateway/sync` 全量刷新、`POST .../upstreams/{name}/sync` 单上游刷新、`DELETE .../upstreams/{name}/tools` 下线、`GET /api/admin/gateway/health` 聚合健康；
- 自定义上游协议：实现 `McpUpstreamClientFactory` SPI 即可替换默认 Streamable HTTP 客户端。

```yaml
mcp:
  enterprise:
    gateway:
      sync-on-startup: true
      upstreams:
        - name: hr                    # 工具前缀 = hr（hr__get_employee）
          url: http://hr-mcp:8080/mcp
          api-key: ${HR_MCP_API_KEY:}
        - name: finance
          url: http://finance-mcp:8080/mcp
        - name: legacy
          url: http://legacy:8080/mcp
          enabled: false              # 被收购遗留系统，先禁掉
```

> 📖 详见 [docs/federation-gateway-guide.md](docs/federation-gateway-guide.md) ｜ 发布说明 [docs/V1.25-release-notes.md](docs/V1.25-release-notes.md)

### 🛡 MCP 治理（V1.26）——人类在环审批（HITL）

**回答企业安全评审必问的三句话：①“高权限工具 Agent 能直接调？” ②“审计日志里有手机号/身份证/密钥？” ③“谁批准的这次调用？”** `mcp-governance` 用一套配置全部答掉：

- **风险分级 T0-T4**（OWASP MCP Governance & Risk 五级模型对齐）：显式 `tool-tiers` > 工具分类 > 关键词启发式 > 默认 T2；
- **人工审批闸门**：T3/T4 工具调用先入审批队列（`-32092 approval_required`），管理员批准后颁发**一次性令牌**（`X-MCP-Approval-Id`），防重放/防工具替换/防调用方替换；
- **Fail-closed**：deny-tiers 硬拒 / 审批服务不可用即拒 / 配置歧义不乱放行；
- **敏感数据脱敏**：邮箱、身份证、银行卡、手机号、apiKey/secret/password/token 键值在**入库审计前**打码，审计日志可安全导出；
- **灰度优先**：出厂 `enforce=false` 只登记不拦截，验证分级准确后一键 `enforce=true` 强制；
- **审批状态持久化（V1.28）**：`approval.store=jdbc` 把审批队列落到单表（方言无关：H2/MySQL/PG/SQL Server），多实例部署下审批-消费跨副本可见，重启不丢审批记录（审计合规）；无数据源自动回退内存；
- **管理 REST API**：`/api/admin/governance/approvals·stats·audit·policy`；审计出口 SPI 可换 Kafka/JDBC。

```yaml
mcp:
  enterprise:
    governance:
      enforce: true                 # 灰度 false → 强制 true
      tool-tiers:                   # 显式覆盖（生产建议必配）
        finance_transfer: T4
      require-approval-tiers: [T3, T4]
      deny-tiers: []
```

> 📖 详见 [docs/governance-guide.md](docs/governance-guide.md) ｜ 发布说明 [docs/V1.26-release-notes.md](docs/V1.26-release-notes.md)

### 🌐 MCP + A2A 双协议网关（V1.15 → V1.18）

**Agent 天花板能力：一个网关同时讲 MCP 和 A2A 两种语言。** 2026-08-20 Google A2A 正式并入 Linux Foundation AAIF（与 MCP 同框架治理），「MCP（Agent→工具）+ A2A（Agent→Agent）」双层栈已成为企业参考架构——蚂蚁集团等 JD 已明确要求「MCP + A2A 研发架构」。

`mcp-integrations/mcp-a2a` 把工具注册中心的全部 MCP 工具自动派生为 A2A Agent Card / Skill，任意 A2A Agent（如 Google ADK、Azure AI Foundry、LangGraph 编排器）可直接调用：

| 端点 | 说明 |
| --- | --- |
| `GET /.well-known/agent-card.json` | **A2A 协议标准发现路径**（Agent Card：技能列表自动派生自工具注册中心 + securitySchemes 声明；V1.18 配置签名后返回 `SignedAgentCard` 信封） |
| `GET /a2a/agent-card` | Agent Card 别名（同上，可签名） |
| `POST /a2a/rpc` | A2A JSON-RPC 2.0 分派：`message/send` / `task/send` / `task/get` / `task/cancel` / `agent/quote` |
| `POST /a2a/rpc/stream` | **V1.16 SSE 流式**：`message/stream`（异步实时推状态）/ `task/resubscribe`（历史重放） |
| `GET /a2a/agent-card/verify` | **V1.18 自验证端点**：输出签名校验结果（valid/algorithm/keyId/signedAt） |
| `GET /a2a/health` | 存活检查 + 技能数 + authMode + signedCard |

V1.16 起 A2A 网关支持 **SSE 流式调度**（TaskStatusUpdateEvent / TaskArtifactUpdateEvent / MessageDeliveryEvent），并在 Agent Card 上 **声明 securitySchemes**（api-key / oauth2，mcp-auth 打通第一步）。

**V1.17 强制鉴权：** 三种模式（none / api-key / oauth2）按配置自动推导，`jwt-secret` 与 mcp-auth 同值时令牌互通——mcp-auth Client Credentials 签发的 `access_token` 可直接通过网关 Bearer 校验（RFC 6750）。

**V1.18 Signed Agent Card（A2A v1.2 供应链安全基线）：** 配置 `card-signing-key` 后，agent-card 返回 `{agentCard, signature}` 信封（JWS HS256 + 规范化 JSON），响应头同时携带 `X-Agent-Card-Signature`；客户端可用 `A2aAgentCardSigner.verify(jws, secret)` 一行验签，防 DNS 劫持 / 中间人篡改能力发现——与 mcp-auth / 网关鉴权同钥闭环。

```yaml
mcp:
  enterprise:
    a2a:
      enabled: ${MCP_A2A_ENABLED:false}          # 默认关闭（opt-in）
      api-key: ${MCP_A2A_API_KEY:}              # 可选：设置后要求 X-A2A-Key 头
      streaming-enabled: ${MCP_A2A_STREAMING_ENABLED:true}  # V1.16 SSE 流式
      security-scheme: ${MCP_A2A_SECURITY_SCHEME:}          # V1.16 none|api-key|oauth2
      oauth2-token-url: ${MCP_A2A_OAUTH2_TOKEN_URL:}        # V1.16 oauth2 token 端点
      jwt-secret: ${MCP_A2A_JWT_SECRET:}                    # V1.17 启用 OAuth2 Bearer 强制鉴权（与 mcp-auth 同值互通）
      card-signing-key: ${MCP_A2A_CARD_SIGNING_KEY:}        # V1.18 Signed Agent Card 签名密钥（非空启用）
      card-key-id: ${MCP_A2A_CARD_KEY_ID:mcp-a2a-1}         # V1.18 JWS kid
```

```bash
# 1. 发现 Agent Card（A2A 客户端标准入口）
curl http://localhost:8081/.well-known/agent-card.json
# 2. 任务式调用（metadata.skillId = MCP 工具名）
curl -X POST http://localhost:8081/a2a/rpc -H 'Content-Type: application/json' -d '{
  "jsonrpc": "2.0", "id": 1, "method": "task/send",
  "params": { "message": { "text": "6*7",
    "metadata": { "skillId": "calculator", "arguments": { "expr": "6*7" } } } }
}'
```

> 📖 完整指南见 [docs/a2a-integration-guide.md](docs/a2a-integration-guide.md)，设计解读见 [docs/blog-java-mcp-a2a-2026-08-31.md](docs/blog-java-mcp-a2a-2026-08-31.md)

---

### 🎯 工具级 Scope 权限映射（V1.19，Token Scope → Tool ACL）

**「拿到令牌」≠「能调所有工具」。** OAuth2 `scope` 细粒度映射到 MCP 工具级权限，对标企业 JD：Greelow *per-user scoping* / NTT DATA *authorization checks + least-privilege* / Sumo Logic *token 权限 + 多租户隔离*。

| 能力 | 说明 |
| --- | --- |
| `ScopeMatcher` | 企业 scope 通配匹配：精确 `tools:finance:read` ／ 单段 `tools:finance:*` ／ 多段 `tools:**` ／ 全匹配 `*` |
| `ToolScopePolicy` | 授权决策：工具显式声明 > `tool-overrides` > `category-defaults` > 无约束放行（向后兼容） |
| `invokeWithScope` | 执行前 fail-closed：拒绝时**执行器零调用**，返回 403 语义 `insufficient_scope` |
| REST `/invoke` | 越权 → **HTTP 403** + `WWW-Authenticate: Bearer error="insufficient_scope"`（RFC 6750 §3.1）+ 审计记录 |
| Streamable HTTP | JSON-RPC 错误码 **-32090** `insufficient_scope` + HTTP 403 + `WWW-Authenticate` |
| `tasks/create` | **scope 预检**：无权限任务不入队（fail-fast） |
| 能力自描述 | `tools/list`/`discover` 输出 `requiredScopes`；`GET /api/mcp/scope/policy` 观察全局授权矩阵 |

```yaml
mcp:
  enterprise:
    security:
      scope:
        enabled: ${MCP_SCOPE_ENABLED:false}      # 总开关（默认 false = 与 V1.18 行为一致）
        category-defaults:                        # 分类兑底：finance → tools:finance:*
          finance: "tools:finance:*"
          database: "tools:database:*"
        tool-overrides:                           # 工具名覆盖（运维免改代码）
          finance_indicator: "tools:finance:read"
```

```bash
# 1. 拿受限令牌（仅 tools:finance:read）
TOKEN=$(curl -s -X POST http://localhost:8081/api/auth/oauth2/token \
  -d 'grant_type=client_credentials&client_id=mcp-service&client_secret=change-me-client-secret&scope=tools:finance:read' | jq -r .access_token)
# 2. 调金融工具 → 成功
curl -s -X POST http://localhost:8081/api/mcp/tools/finance_indicator/invoke \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"indicator":"cagr","params":{"beginValue":100,"endValue":200,"years":3}}'
# 3. 调数据库工具 → 403 insufficient_scope
curl -s -i -X POST http://localhost:8081/api/mcp/tools/db_query/invoke \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"sql":"SELECT 1"}'
```

> 📖 完整指南见 [docs/scope-authorization-guide.md](docs/scope-authorization-guide.md)，设计解读见 [docs/blog-java-mcp-scope-acl-2026-09-04.md](docs/blog-java-mcp-scope-acl-2026-09-04.md)

> 💰 变现通道：[docs/upwork-mcp-guide.md](docs/upwork-mcp-guide.md)（Upwork 官方 MCP Server 接入）· 安全审查对照表：[docs/security-review-checklist.md](docs/security-review-checklist.md) · JD 话术包：[docs/pitch-30s-2026-09-05.md](docs/pitch-30s-2026-09-05.md)

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
| `mcp-tools/tool-http` | **通用 HTTP 调用工具**（域名白名单防 SSRF，对接内部 REST API） |
| `mcp-tools/tool-finance` | **金融场景模板**：财务指标计算（CAGR/ROE/PEG/复利/定投/利润率）+ 合规日历（财报披露窗口）+ 风险评分（五维加权），面向研报助手、投顾/风控机器人 |
| `mcp-monitor` | 📊 **监控/可观测（V1.24）**：指标聚合 + Prometheus 导出 + 审计 + 告警 + **内置告警规则（`config/prometheus/alerts.yml`）+ Grafana 自动 Provisioning 看板（`config/grafana/`，`docker compose --profile monitoring` 即用）** |
| **`mcp-auth`** | 🔐 **企业认证层**：OAuth2/SSO + JWT + API Key + **Client Credentials（机器对机器）**（新增！） |
| **`mcp-tenant`** | 🏢 **多租户三档隔离（V1.11 Row + V1.12 Schema + V1.13 Instance）+ V1.14 生命周期管理**：Row 模式（TenantContext + TenantAwareJdbcTemplate fail-closed）+ Schema 模式（TenantSchemaDataSource 自动切换 schema/provision/方言适配）+ Instance 模式（TenantInstanceRegistry 每租户独立 DataSource/连接池、运行时开通/停用）+ **生命周期 REST API（/api/admin/tenants：开通/替换/挂起/恢复/销毁 无需重启）**，X-Tenant-Id 头注入，三模式配置互斥 fail-fast，防跨租户越权 |
| **`mcp-registry`** | 🧩 **Skill Registry 技能注册表（V1.21 → V1.22）**：Skill/Spec 注册（注册即激活）+ 语义化版本管理（历史快照/裁剪）+ 显式激活 + **故障回滚（一键退回上一已部署版本）** + **灰度路由（权重制 0-100，未分配权重自动回退 ACTIVE）** + **V1.22 持久化（JDBC，opt-in：`store=memory|jdbc`，重启/滚动发布后版本、ACTIVE、灰度权重自动恢复；`SkillRegistryStore` SPI + `JdbcSkillRegistryStore` 单表方言无关 + best-effort 落库 + `reload()` 启动恢复）**，管理 REST API（/api/admin/skills）+ 客户端发现端点（/api/mcp/skills），21 测试全绿，命中沃尔玛/禾蛙 JD 的 Skill Registry 要求 |
| `mcp-integrations/mcp-alibaba` | Spring AI Alibaba 集成（可选） |
| **`mcp-integrations/mcp-a2a`** | 🌐 **A2A 双协议网关（V1.15）**：MCP 工具 → A2A Agent Card/Skill，JSON-RPC 分派（message/send、task/send/get/cancel），任意 A2A Agent 可直接调用企业 MCP 工具 |
| **`mcp-springai-tools`** | 🎯 **Spring AI 工具桥接（V1.23）**：@Tool / ToolCallback / ToolCallbackProvider 三类工具源自动注册为企业 MCP 工具（自动继承 RBAC / 限流 / 审计 / 工具级 Scope / 健康检查），业务代码零改动，15 测试尽绿 |
| **`mcp-gateway`** | 🌐 **MCP 联邦网关（V1.25）**：聚合任意多个下游 MCP Server（Streamable HTTP / JSON-RPC，零依赖客户端，兼容单 JSON 与 SSE 响应）——联邦工具自动命名空间隔离（`<prefix>__<tool>`）+ 继承 RBAC/限流/审计/Scope + 启动自动同步 + 失败保留/禁用移除 + 管理 REST API（/api/admin/gateway/upstreams·sync·tools·health），22 测试全绿，命中 Sumo Logic「联邦式 MCP 托管」/MintMCP 网关赛道需求 |
| **`mcp-governance`** | 🛡 **MCP 治理（V1.26）**：人类在环人工审批（HITL）——T3/T4 高风险工具调用先入审批队列，批准后颁发一次性令牌（`X-MCP-Approval-Id`）防重放；风险分级 T0-T4（OWASP MCP Governance 对齐：显式 tool-tiers > 分类语义 > 关键词 > 默认）+ deny-tiers 硬闸门 + Fail-closed + 敏感数据脱敏（邮箱/身份证/银行卡/手机号/密钥，递归 Map）+ 审计事件 + 管理 REST API（/api/admin/governance/*），28 测试全绿，命中 iMagic $40K+「HITL checkpoint」/GSWE 采购「写操作审批+审计」验收项 |
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
| **中文文档** | ✅ [README.zh-CN.md](README.zh-CN.md) 完整中文文档 | ❌ 英文为主 | ❌ 英文为主 |

---

## 📅 MCP 2026-07-28 规范

### 什么是 MCP 2026-07-28？

2026 年 7 月 17 日，MCP 发布了 **史上最大规模修订的规范候选版**（2026-07-28 候选版），7 月 28 日正式发布。核心变化：

- 🏭 **无状态核心** — 支持无状态 HTTP 架构，Kubernetes / Cloud Run 弹性伸缩
- 🔍 **能力发现** — Server 自动广播能力，Client 动态发现
- 🔒 **企业授权加固** — OAuth 2.0 / Identity Provider 统一管控
- 🧩 **Extensions** — 第三方扩展协议，MCP 成为可扩展的企业应用平台

### MCP Enterprise 已全面覆盖企业级需求

| 2026-07-28 特性 | MCP Enterprise 支持 | 实现模块 |
|-----------------|---------------------|---------|
| 无状态核心 | ✅ 已支持 | mcp-core McpStatelessEndpoint |
| Streamable HTTP 传输 | ✅ 已支持 (GET 事件流 + POST 消息) | mcp-server McpStatelessController |
| 工具变更通知 (listChanged) | ✅ 已支持 (SSE 广播) | mcp-server /api/mcp/v2/stream + notify |
| 能力发现 | ✅ 已支持 | mcp-core ToolRegistry |
| RBAC 权限 | ✅ 已支持 | mcp-core McpSecurityManager |
| 企业授权 (OAuth2/SSO) | ✅ 已支持 | mcp-auth 模块 |
| 审计日志 | ✅ 已支持 | mcp-monitor McpAuditLogger |
| 速率限制 | ✅ 已支持 | mcp-core RateLimiter |
| Prometheus 指标 | ✅ 已支持 | mcp-monitor McpMetricsCollector |
| Tools 无状态 REST | ✅ 已支持 | mcp-core McpToolManager |

> ✅ 2026-07-28 规范已全面适配（V0.11+），Streamable HTTP 为新默认传输。

---

## ❓ FAQ

### Q: MCP Enterprise 是免费的么？

**是的。** 完全开源免费，采用 Apache 2.0 许可。GitHub 仓库包含完整的框架源码、内置工具、Streamable HTTP/SSE 端点、安全机制和管理 API。

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
| **V0.6** | **GitHub SEO + 仓库公开 + Topics** | **✅ 已完成** |
| **V0.7** | **Full Build Profile + 市场日报更新** | **✅ 已完成** |
| V0.8 | Maven Central 发布脚本 + Sonatype 注册 | ✅ 已完成 |
| V0.9 | 健康看板 + 工具调用统计 | ✅ 已完成 |
| V0.10 | 中文社区推广 + 万星增长计划 | ✅ 已完成 |
| **V0.11** | **MCP 2026-07-28 全面适配** | **✅ 已完成** |
| **V0.12** | **WAIC 2026 市场更新 + 版本升级** | **✅ 已完成** |
| **V0.13** | **Spring AI 2.0 兼容性 + 项目基础设施完善** | **✅ 已完成** |
| **V0.14** | **版本同步修复 + 市场报告更新 + 变现路径落地** | **✅ 已完成** |
| **V0.15** | **MCP 2026-07-28 完整合规 + Marketplace Ready** | ✅ 已完成 |
| **V0.16** | **无状态营销战役 + 生态 PR 启动** | ✅ 已完成 |
| **V1.0** | **正式发布 + 生产文档（部署/运维手册）** | 🔄 进行中 |
| **V1.1** | **tool-http(SSRF防护) + OAuth2 client-credentials + 白皮书** | ✅ 已完成 |
| **V1.2** | **Spring AI Alibaba 集成纳入默认构建 + 市场调研/SEO 博客** | ✅ 已完成 |
| **V1.3** | **金融场景模板 tool-finance（CAGR/ROE/PEG/复利/定投）** | ✅ 已完成 |
| **V1.4** | **金融模板二期：合规日历 + 风险评分** | ✅ 已完成 |
| **V1.5** | **2026-07-28 最终版网关友好：Mcp-Method/Mcp-Name 标头 + 传输验证 + ttlMs/cacheScope + 确定性排序** | ✅ 已完成 |
| **V1.7** | **网关限流路由表（按操作 QPS 运行时管理）+ Prometheus 指标导出** | ✅ 已完成 |
| **V1.8** | **OAuth2 Client Credentials 短时凭证 + EMA 企业集中授权（Token/Introspect/吊销）+ 市场调研 08-20** | ✅ 已完成 |
| **V1.9** | **OAuth2 Refresh Token 轮换 + 重用检测（RFC 9700）+ 网关 Bearer 强制校验 + jti 防碰撞 + RFC 7009 吊销端点** | ✅ 已完成 |

| **V1.10** | **企业采购对照表(RFP清单) + 兼职报价单 + MCP Registry 收录申请 + Dify 集成示例 + 多租户预研 + 市场雷达 08-25（MCP 岗位薪酬带）** | ✅ 已完成 |
| **V1.11** | **多租户 Row-level 隔离（mcp-tenant：TenantContext + TenantAwareJdbcTemplate + fail-closed）+ 市场雷达 08-26（多租户进 JD）** | ✅ 已完成 |
| **V1.12** | **多租户 Schema 级隔离（TenantSchemaDataSource 自动切换 + provision + 方言适配）+ 市场雷达 08-27（Sumo $207-243K 平台岗/Upwork 官方 MCP）** | ✅ 已完成 |
| **V1.13** | **实例级多租户（TenantInstanceRegistry 每租户独立 DataSource/连接池 + 运行时开通/停用 + ${ENV} 密钥占位 + initialize-DDL + 三模式互斥守卫）+ 市场雷达 08-29（Anthropic $300K/NTT DATA/Cotality $129-160K/Upwork 双新单）** | ✅ 已完成 |
| **V1.15** | **MCP + A2A 双协议网关（mcp-integrations/mcp-a2a：工具注册中心自动派生 Agent Card/Skill + JSON-RPC 分派 + .well-known 标准发现）+ 市场雷达 08-31** | ✅ 已完成 |
| **V1.16** | **A2A SSE 流式（message/stream + task/resubscribe）+ Agent Card securitySchemes 声明（mcp-auth 打通第一步）+ 市场雷达 09-01** | ✅ 已完成 |
| **V1.17** | **A2A 网关 OAuth2 Bearer 强制鉴权（RFC 6750，A2aJwtTokenValidator 与 mcp-auth 同密钥派生）+ 三模式 authMode 推导 + 市场雷达 09-02（Photon-Citi/SumoLogic/TalentAlly/AAIF）** | ✅ 已完成 |
| **V1.18** | **Signed Agent Card（A2A v1.2 供应链安全基线：JWS HS256 签名 + 规范化 JSON + X-Agent-Card-Signature 头 + 自验证端点 + 9 新测试）+ 市场雷达 09-03（A2A v1.0 GA/Greelow $6-9K·月/Sumsub/Upwork MCP Server）** | ✅ 已完成 |
| **V1.19** | **工具级 Scope 权限映射（Token Scope → Tool ACL：ScopeMatcher 通配 + ToolScopePolicy 决策 + invokeWithScope fail-closed + REST 403 RFC 6750 insufficient_scope + Streamable HTTP -32090 + tasks/create 预检 + tools/list 暴露 requiredScopes + scope/policy 观察端点，26 新测试）+ 市场雷达 09-04（Commerzbank MCP 网关岗/NTT DATA Empiric 价目）** | ✅ 已完成 |
| **V1.20** | **开发变现通道（Upwork 官方 MCP Server 接入指南 + 配置示例 / 安全审查对照表 / 三类 JD 30 秒话术包 / 掘金 CSDN 稿件）+ 市场雷达 09-05（Photon-Citi MCP 岗/沃尔玛中国 ¥30-55K/WF Next $7-12K/Upwork 官方 MCP 上线）** | ✅ 已完成 |
| **V1.21** | **Skill Registry 技能注册表（mcp-registry：Skill/Spec 注册 + 语义化版本管理 + 显式激活 + 故障回滚 + 灰度路由 + 管理/发现 REST API，12 测试全绿）+ 使用指南 + proposal 模板库 5 份 + 掘金 CSDN 稿件 + 市场雷达 09-06（禾蛙 ¥80-120万 MCP 平台岗/Anthropic ×3 岗 $300-485K/Cognizant 截止 09-09）** | ✅ 已完成 |
| **V1.22** | **Skill Registry 持久化（JDBC，opt-in：`SkillRegistryStore` SPI + `JdbcSkillRegistryStore` 单表方言无关 + upsert 保留 active + best-effort 落库 + `reload()` 启动恢复；9 新测试/H2，模块 21 全绿）+ 持久化指南 + 掘金 CSDN 稿件 + 市场雷达 09-11（花旗 Java+MCP 岗/SumoLogic $207-243K Java MCP/PTC AI Control Plane $135-155K/Upwork Skills Platform Java 单）** | ✅ 已完成 |
| **V1.23** | **Spring AI 工具桥接（mcp-integrations/mcp-springai-tools）：@Tool / ToolCallback / ToolCallbackProvider 自动注册为企业 MCP 工具（RBAC/限流/审计/Scope 自动生效）+ 集成指南 + 掘金CSDN稿 + 市场雷达 09-12（沃尔玛 ¥30-55K MCP 网关岗/Sumo Logic MCP 平台岗/OneSeven $4-5K月/Snowflake 收购 Natoma/火山引擎 ¥37.7万 AI Coding 大单）** | ✅ 已完成 |
| **V1.24** | **可观测性治理开箱即用：内置 Prometheus 告警规则（工具/网关错误率与延迟/存活/流量骤降，9 条）+ Grafana 自动 Provisioning 看板（11 面板总览，docker compose --profile monitoring 即用）+ 可观测性指南（指标体系/企业接入/SLI-SLO/排查手册）+ 市场雷达 09-13（Caterpillar Java+Agent Lead 今日截止/Sumo Logic $207-243K/adidas MCP+Agentic Infra/外包公允价值 $2-12K月）** | ✅ 已完成 |
| **V1.25** | **MCP 联邦网关（mcp-gateway）：聚合任意多个下游 MCP Server（Streamable HTTP/JSON-RPC 零依赖客户端，兼容 JSON+SSE 响应，initialize→initialized→method 握手序列，Bearer 透传）——联邦工具命名空间隔离（prefix__tool）并自动继承 RBAC/限流/审计/Scope/健康检查 + 启动自动同步 + 失败保留旧工具/禁用移除 + 管理 REST API（upstreams/sync/tools/health）+ 联邦网关指南 + 掘金CSDN稿 + 市场雷达 09-14（MintMCP 网关融资 50+客户/Sumo Logic 联邦式托管 $207-243K/OneSeven Java+Spring MCP $4-5K月/Cotality MCP Server $10.7-13.3K月）** | ✅ 已完成 |
| **V1.26** | **MCP 治理（mcp-governance）：人类在环 HITL 审批——T3/T4 高风险工具先入审批队列，批准后一次性令牌（X-MCP-Approval-Id）防重放/防工具与调用方替换；风险分级 T0-T4（OWASP 对齐）+ deny-tiers 硬闸门 + Fail-closed + 敏感数据脱敏（邮箱/身份证/银行卡/手机号/密钥/递归 Map）+ 灰度 enforce=false 优先 + 管理 REST API（approvals·stats·audit·policy）+ 治理指南 + 掘金 CSDN 稿 + 市场雷达 09-17（Sumo Logic $207-243K Java MCP/Anthropic $300-560K/EPAM·WhiteCoat·Citi Java-MCP 岗/定制 MCP $3-10K€ 固定价·生产级 $15-40K）** | ✅ 已完成 |
| **V1.27** | **Release 工程修复 + Go 客户端示例：修复 Dockerfile 缺失 mcp-governance pom（docker build 恢复）+ GitHub Actions 产物清单补全 V1.25/26 新模块（gateway/governance/a2a/springai-tools）+ 新增 Go 客户端（examples/client-go，标准库零依赖，4 语言 7 客户端齐）+ 市场雷达 09-18（OneSeven Java+Spring MCP $4-5K/月要求 GitHub 作品/Upwork Lifted Java/Go 长期合同/沃尔玛 ¥30-55K Skill Registry 对应/Upwork 产品 $750-5K·$41 红海信号/MCP SDK 下载 97M/月 970x）** | ✅ 已完成 |

| **V1.14** | **租户生命周期管理 REST API（/api/admin/tenants：运行时开通/替换/挂起/恢复/销毁 独立实例池，TenantLifecycleManager + 404/409 语义化错误，10 集成测试/9 单测全绿）+ 仓库清理 + 市场雷达 08-30（蚂蚁 25-50K·15薪 MCP+A2A 岗/Upwork 官方 MCP Server 发布/Glama 首个全职工程师岗）** | ✅ 已完成 |

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

## 🤝 贡献

欢迎贡献！详见 [CONTRIBUTING.md](CONTRIBUTING.md)。

提交 Issue 请使用 [Bug Report](.github/ISSUE_TEMPLATE/bug_report.md) 或 [Feature Request](.github/ISSUE_TEMPLATE/feature_request.md) 模板。

---

<p align="center">
  <b>Java + Spring + AI = MCP Enterprise</b><br>
  <sub>MCP 市场的 Java 蓝海 · 中国企业级 AI 集成基础设施</sub>
</p>
