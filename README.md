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
- **审计事件持久化（V1.29）**：`audit.store=jdbc` 把每次治理判定事件（工具/等级/调用方/判定/脱敏后参数）落到单表 `mcp_governance_audit`（方言无关），重启/滚动发布不丢审计轨迹，多实例有统一全局视图，SQL 直接查（合规取证/SIEM 对接）；只写不删防篡改，fail-soft 不挂业务；管理面板 `/api/admin/governance/audit` 零改动读取同一张表；
- **审计 × 链路追踪（V1.31）**：治理过滤器解析 **W3C traceparent**（兼容 X-Request-Id / MDC traceId / UUID 兜底，零外部依赖）→ 每条审计事件携带 `traceId`/`spanId`，检索与 CSV 导出支持按 traceId 回溯一次调用链的全部治理判定；老表自动 `ALTER TABLE ADD COLUMN` 升级；与 OpenTelemetry / Grafana Tempo / Jaeger 双向打通；
- **审计实时流导出（V1.32）**：`audit.store=http` 把治理判定**批量异步推送到 SIEM / Kafka REST Proxy / 任意 webhook**（Splunk HEC / ES / Loki），零外部依赖（JDK HttpClient）；攒批 + 定时兜底发送，fail-soft 不挂业务，本地视图/管理 API 不变，`sent/failed/pending` 诊断计数；
- **管理 REST API**：`/api/admin/governance/approvals·stats·audit·policy`；审计出口 SPI 可换 Kafka/JDBC/HTTP。

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
| **`mcp-governance`** | 🛡 **MCP 治理（V1.26）**：人类在环人工审批（HITL）——T3/T4 高风险工具调用先入审批队列，批准后颁发一次性令牌（`X-MCP-Approval-Id`）防重放；风险分级 T0-T4（OWASP MCP Governance 对齐：显式 tool-tiers > 分类语义 > 关键词 > 默认）+ deny-tiers 硬闸门 + Fail-closed + 敏感数据脱敏（邮箱/身份证/银行卡/手机号/密钥，递归 Map）+ 审计事件（V1.29 JDBC 落库可取证 + V1.30 检索/CSV 导出/TTL 保留 + **V1.31 traceId/spanId 链路追踪关联：W3C traceparent 解析（兼容 X-Request-Id/MDC/UUID）零依赖，检索与导出可按 traceId 回溯一次调用链全部治理判定，老表自动 ALTER 升级**）+ 管理 REST API（/api/admin/governance/*），62 测试全绿，命中 iMagic $40K+「HITL checkpoint」/GSWE 采购「写操作审批+审计」验收项 |
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

## 🤝 企业招标 / 招聘动态（MCP 赛道）

> 本框架的定位不是玩具，而是直接对齐**大厂 JD 与央企/军工 MCP 招标**的生产级方案。

### 📑 最新央企/军工/国企/卫健 MCP 与算力招标（可对标交付，更新 11-09）
- 🆕🆕🆕 **🔥🔥🔥 11-09「治理层 = 定价权」被公开报价坐实**：[Dawloom MCP cost estimator](https://dawloom.com/blog/mcp-server-cost-estimator) 同一「小型只读连接器」范围 7 档公开报价 **$3,700 ~ $72,000（19 倍价差）**；[AI Agents Plus](https://www.ai-agentsplus.com/blog/what-a-custom-mcp-server-costs-to-build-and-run) 自建 **$15K~$220K + 年运维 10~30%**，单系统 $15K-45K，而含 **audit logging / SSO / RBAC / versioning** 的生产级内部产品 **$80K~$220K**、企业网关 **$250K 起** → **代码量没涨 10 倍，涨的是治理层**；[dev.to](https://dev.to/whoffagents/pricing-an-mcp-server-in-2026-why-we-charge-19mo-when-the-market-average-is-0-nig) 约 318 个 MCP server 绝大多数 **$0**，能收费的清一色是安全扫描/监控/数据基础设施。★ 逐项对位 21 模块 + 三档报价模板 + 加价触发器 → [`docs/mcp-governance-pricing-anchor-2026-11-09.md`](docs/mcp-governance-pricing-anchor-2026-11-09.md)。⚠️ 均为卖方销售页/行业估算，非成交价。
- 🆕🆕🆕 **🔥🔥🔥 银行智能体平台采购进入「底座期」（11-09 同花顺/银行智能应用动态）**：[兴业银行](https://m.10jqka.com.cn/20261109/c681593732.shtml)「具有高级认知和作业处理能力的金融智能体平台」+ 灵活插件集成与调用；**北京农商**要求覆盖智能体「开发、纳管、调用、评测、观测、运营**全生命周期**」+ Skill 和工具广场内置 **≥50 个**通用 Skill；**上海农商** POC 明写「支持 **Skill、MCP 插件市场**功能」+ 解决智能体测评/发布/**权限管理**；中原「大模型安全围栏 + 企业级 AI 网关」、浙商单笔超 **6400 万**；9 月银行 AI 中标 **40 个 / 28 家 / 城商行占 60%**。→ 采购标的从「买一个场景」变成「买一套可验收的开发与运营底座」，逐条应答 + 广发三标矩阵 + 资质自检 + 6 天行动清单见 [`docs/bank-agent-platform-tender-response-2026-11-09.md`](docs/bank-agent-platform-tender-response-2026-11-09.md)。
- 🆕🆕🆕 **🔥🔥 广发证券第三份 MCP 征集浮出且最紧 —《2026 态势感知及安全分析平台扩容项目》11-12 17:30 截止（剩 3 天）**：[公告原文](https://www.gf.com.cn/web/article/detail?articleId=01a1aaecb5257570934897d5a51a3212&columnId=4) 运营管理层明确含「**MCP 安全工具管理** + 安全技能库管理」+ 6 个安全智能体 + AI 安全数据底座（统一日志模型/实体识别与关联）；资质要求原厂或授权代理、近三年 ≥2 金融案例 + ≥2 安全项目案例。与 13321 / 13322（均 11-13 17:30）构成三标矩阵 → 应答包见 [`docs/bank-agent-platform-tender-response-2026-11-09.md`](docs/bank-agent-platform-tender-response-2026-11-09.md)。⚠️ 三份均为**预征集 ≠ 采购邀请**，资质不足建议组联合体、禁虚构材料。
- 🆕🆕🆕 **🔥🔥🔥 11-08 官方 MCP 参考服务器七 CVE（`CVE-2026-41901`~`41907`）—「参考实现自己就是反面教材」**：[AL-ICE 完整复盘](https://al-ice.ai/posts/2026/11/mcp-reference-servers-desktop-commander-seven-cve-batch-november-2026)，ShieldPi 打官方 `modelcontextprotocol/servers`（git/sqlite/fetch）+ Desktop Commander —— `mcp-server-sqlite` `write_query` **只拒 SELECT 前缀** → `VACUUM INTO '/tmp/escaped.db'` 自提交任意路径写全库（**textbook prefix-check failure**）+ DROP/ALTER/PRAGMA/CREATE TRIGGER 全过；`mcp-server-git` 带 `core.fsmonitor`/hook 调用 Git → 标注 **`read-only`** 的 `git_status` 主机任意代码执行；`validate_opened_repo` 只校验 3 条路径漏了第 4 条 `.git/objects`；PyPI 上游**已归档，补丁无处落地**。🆕 已落地可运行参考实现 [`examples/tool-input-grammar/`](examples/tool-input-grammar/README.md)（per-tool statement grammar allowlist，8 攻击 + 7 对照组自检全绿）。
- 🆕🆕🆕 **🔥🔥 Anthropic 对 STDIO 架构缺陷明确「Won't Be Patched」— 责任正式下沉到每个集成方**：[The Agent Times](https://theagenttimes.com/agents/article/zero-click-rce-in-ai-coding-agents-threatens-our-entire-mcp--13831b18) / [架构缺陷复盘](https://theagenttimes.com/agents/article/anthropic-refuses-to-patch-mcp-flaw-exposing-thousands-of-ag-4e41ccfc)：`CVE-2026-30615`（Windsurf 零点击 RCE，CVSS **8.0**）等 **12+ 同类 CVE**，提示注入静默改写 `mcp.json` → 注册攻击者 STDIO server → **零工具调用/零审批/零模型参与**即在工具列表出现前执行；根因在官方 SDK `StdioServerParameters` 不中性化直接 spawn，影响 **Python/TS/Java/Rust 官方 SDK 累计 1.5 亿+ 下载**；**Anthropic / Google / Microsoft 定性「spec-conformant / by design」拒绝修复**，唯一缓解是 STDIO command allowlist 且**非 SDK 默认**。生态量化：AgentSeal 16,000+ server（1,808 个中 66% 有安全发现 / 427 critical）、Endor Labs 2,614 实现中 82% 路径穿越 / 67% 代码注入。→ 「升级官方 SDK ≠ 安全」升级为「**官方明确不修**」；[腾讯云开发者社区](https://cloud.tencent.com/developer/article/2779058) 金句「**在 MCP 规范里，授权是 OPTIONAL 的**」。
- 🆕🆕 **🌐 Google Cloud 成第四朵把 MCP 治理做成标准层的云（文档 11-08 更新）**：[Agent Registry / Agent Gateway](https://docs.cloud.google.cn/agent-registry/manage-mcp-tools?hl=bg) 中央注册表 + Gateway 强制 IAM 策略 + 安全发现结果；[MCP 服务器文档](https://docs.cloud.google.cn/agent-registry/use-agentregistry-mcp?hl=zh-tw) 明确「OAuth 2.0 + IAM、**不接受 API Key**、为使用 MCP 工具的 agent 建立**独立身份**」+ `agentregistry.read-write` 专用 scope。叠加 [DevOpsSchool 5 家](https://www.devopsschool.com/blog/best-vendor-for-an-enterprise-mcp-gateway-governance-in-2026) / [Strac 8 家](https://www.strac.io/blog/best-mcp-gateway) 网关选型指南 → 四层分工替代三层（云网关管流量与成本 / 本层管责任认定 / 客户端管连接准入 / **工具实现管入参文法**）；Strac 金句「如果 enterprise 意味着适配既有采购合同，云巨头赢；如果意味着通过数据保护审查，候选名单短得多」。
- 🆕🆕 **⚖️ 英国五大行集体封禁外部 AI Agent（The Telegraph 11-08）+ 朝阳法院 AI 办公劳动争议判例（11-09）**：汇丰/Nationwide/NatWest/巴克莱/劳埃德预防性限制外部 Agent 访问客户账户，**麦肯锡测算 5~10% 存款迁移 → 存款利润降至少 1/5、极端 7000 亿英镑**，FCA 正制定规则，嘉信每周主动拦截 10~20 客户 → 「**外部 Agent 只允许受控只读接入 + 强认证不可绕过**」成采购基线；国内判例把治理颗粒度下沉到「某员工用了哪个工具、处理了什么文件」→ 已补进 [`docs/enterprise-rfp-checklist.md`](docs/enterprise-rfp-checklist.md) 验收列。
- 🆕 **🛠 兑现 V1.66 待办⑤：连接路径补「解析后真实 IP 校验 + IP 固定」**：新增 [`examples/connect-path-guard/McpResolvedIpGuard.java`](examples/connect-path-guard/McpResolvedIpGuard.java)（全部 A/AAAA 记录筛查 + IPv4-mapped IPv6 / 6to4 / Teredo + pin 防 DNS rebinding TOCTOU，可注入解析器使自检不依赖外网），`self-test OK (16 attack cases + 5 control cases)`，V1.66 原 `McpConnectPathGuard` 回归重跑通过。RFP 清单攻击面 **18 → 20 类**；三个纯 JDK 示例 `javac` 编译 + `main()` 自检全绿，**不进 reactor 零构建风险**。
- ⚠️ **诚实标注（11-09 国内新标）**：[上海黄浦区城市治理智能体 306 万](https://www.ccgp.gov.cn/cggg/dfgg/gkzb/202611/t20261107_27641623.htm)（11-07 公告 / 11-30 开标）、[上海消防救援总队 AI 大模型垂域 713.75 万](https://www.ccgp.gov.cn/cggg/zygg/gkzb/202611/t20261108_27642490.htm)（11-08 公告 / 11-30 开标，含密码应用=密评、不接受联合体）、[知识工场 89 万中标](https://www.ccgp.gov.cn/cggg/zygg/zbgg/202611/t20261109_27642527.htm)（北银金融科技）**均未点名 MCP**，不计入 MCP 直接可投清单，仅作「智能体底座 + 私有化 + 等保/密评」持续放量旁证。
- 🆕🆕🆕 **🔥 11-08 定位复核｜三大云 + iPaaS 同一周把 MCP 治理做成标准产品层**：[Microsoft Azure APIM「AI Gateway」公开预览](https://adaptiv.nz/?p=996227)（models / **MCP servers** / policies / observability 集中管理、token 限流配额、REST 经 OpenAPI 转 MCP 工具）+ [AWS Bedrock AgentCore Gateway](https://dev.to/om_shree_0709/amazon-bedrock-agentcore-gateway-just-became-the-enterprise-mcp-infrastructure-layer-heres-what-m4o)（全 MCP primitives、**dynamic listing 按身份实时列工具**、elicitation HITL 三模式、RFC 8693 on-behalf-of）+ [**腾讯云云原生智能网关 v3.9.8（产品动态 11-06 18:27 更新）**](https://cloud.tencent.cn/document/product/1826/134899)（MCP 网关支持 **OAuth 2.0 授权码**访问存量 HTTP「无需改造既有鉴权体系」+ 组织逐级配额成本计量）+ [Celigo 11 月版](https://www.celigo.com/builders-hub/celigo-november-2026-release)（Server/Gateway/Registry 三件套 + **caller context 不接受模型自称** + 安装的 server **默认 disabled 待审核**）→ **「有没有 MCP 治理」不再是卖点**，差异化换锚点到「跨运行时签字 / 私有化合规工件 / 存量 Java 权限复用 / 连接路径准入」。★ 含 11 维能力对照表与 **5 条买方问答话术**、可直接进 11-13 报名材料 → [`docs/enterprise-cloud-gateway-comparison-2026-11-08.md`](docs/enterprise-cloud-gateway-comparison-2026-11-08.md)。
- 🆕🆕🆕 **🔥 攻击面下沉到「连接路径」— `CVE-2026-119025` 官方 MCP Inspector 握手期 RCE（11-05 Pillar 完整链）**：远程 server 在 OAuth discovery 的 `authorization_endpoint` 返回 `https://evil.test/$(...)`，Inspector 自动打开时 `open` 10.x 在 Windows/WSL 塞进 PowerShell 双引号求值 `$(...)` → **零工具调用、零审批、零模型参与，代码在工具列表出现前执行**，且合法 https URL 让「只校验 scheme」失效；兄弟洞 `GHSA-4fc4-wcmh-4g86` **9.6** 不限 OS；同期 opencode 1.18.33 同类洞是「**无 CVE 的 refactor 修复**」→ Dependabot/SBOM 集体失明（Adversa：10 起攻击仅 2 起有 CVE），LiteLLM `GHSA-7hp6-4w63-5g45` 复用 salt 提权 → MCP stdio 主机 RCE 与 `CVE-2026-59822` 同组件两周连打。🆕 三层分工已落地：[`examples/connect-path-guard/`](examples/connect-path-guard/README.md)（客户端连接路径，6 攻击用例 + 3 对照组断言）+ [`examples/governance-hardening/`](examples/governance-hardening/README.md)（**V1.66 起 `mcp_security_*` 10 指标在每个拒绝分支落地**）+ [`examples/monitor-alert-rules/`](examples/monitor-alert-rules/README.md)（告警规则）；🆕 [`examples/audit-ropa-export/`](examples/audit-ropa-export/README.md) 兑现 GDPR ROPA 七字段 JSON/CSV 导出。三份纯 JDK 示例已实测 `javac` + `main()` 自检全绿，**不进 reactor 零构建风险**；RFP 清单攻击面 **16 → 18 类**。
- ⏰ **广发证券 11-13 17:30 截止剩 4 天** → 逐条应答包 [`docs/gf-securities-tender-response-2026-11-07.md`](docs/gf-securities-tender-response-2026-11-07.md)，本期补上「云网关已有，为何还要你」问答；倒计时 厦大 11-11 / 军队 190 万 11-16 / 中山 399 万 11-17 / 山东重工 11-20 / 呼市 900 万 11-23 / 中央民大 3712 万 11-25。⚠️ **诚实标注（周日无新增 MCP 直接标的）**：[广州税务 2026 服务器采购](https://www.ccgp.gov.cn/cggg/zygg/gkzb/202611/t20261106_27639376.htm)（11-06 发布 / 11-30 开标）为通用服务器采购、广发冠名机器人大赛与采购无关，**均未计入可投清单**；预征集 ≠ 采购邀请，不得虚构资质材料。
- 🆕🆕🆕 **🔥 广发证券两份征集公告官方原文核实（11-07）— 11-13 17:30 截止，剩 4 天**：[13321 号《智能金融资讯数据服务平台》](http://gfjc.gf.com.cn/gyszjgg/13321.jhtml) 原文「引入 **MCP 工具协议、Skill 技能包、子 Agent 编排、长期/会话记忆和混合检索**…建设**券商级**智能金融数据服务平台」「沉淀**可外部产品化**的能力组件」；[13322 号《2026 企业微信持续建设项目》](http://gfjc.gf.com.cn/gyszjgg/13322.jhtml) 把「**工具定义与描述、参数校验、鉴权与权限控制、调用限流、日志与可观测性**」直接写进**供应商专业资质**，关键指标含「数据权限按客户经理归属严格隔离 / 服务行为自动留痕 / **日志不可篡改** / MCP 工具调用稳定且权限可控」，且**须使用其 Dify 或 hiagent 平台开发 Agent**。⚠️ 商务门槛：成立 3 年+ / 注册资金 ≥500 万 / 近 2–3 年 ≥1~2 个金融行业成功案例 + 证明材料 / 纳税信用 + 近两月完税凭证 / **广州本地服务团队**；公告明写「**预征集 ≠ 采购邀请**」，目标应为进供应商库，资质不足建议组联合体、绝不可虚构材料 → **逐条应答素材 + 6 天行动清单见 [`docs/gf-securities-tender-response-2026-11-07.md`](docs/gf-securities-tender-response-2026-11-07.md)**。
- 🆕🆕🆕 **🔥 11-06 当日 NVD / 阿里云漏洞库三 CVE — 官方 SDK 连续两天在「受众 / 资源绑定」出洞**：`CVE-2026-111771` **MCP TypeScript SDK** `requireBearerAuth()`/`verifyBearerToken()` 只验 token 本身、**不比对 `aud` 与 `AuthInfo.resource`** → 同一 IdP 签给别的服务有效 token 被本 MCP 服务接受；修 sdk 1.32.0 / server 2.3.0 / express 2.0.2 / server-legacy 2.3.1，**⚠️ 升级后 `expectedResource` 校验默认关闭、须显式启用**；`CVE-2026-102610` fast-agent ≤0.7.20 默认绑 `0.0.0.0:8000` + `auth=None` 且无警告，README quickstart 的 `--shell` 让未认证邻居拿到 root shell；`CVE-2026-123786` Agenta <0.122.5 workflow url 只校验 scheme/netloc + `follow_redirects=True` 带 Secret token → SSRF 打云元数据 + 重定向外带凭据（**同库 webhook 路径早已阻断私网、workflow 路径从未获得同等校验**）。叠加 11-05 `CVE-2026-123271/123272`（8.0）→ 话术升级「**升级 SDK 是必要动作，但不是安全边界**」；ChatCLI 修复版一次修掉的 7 类传输缺陷与 [`examples/governance-hardening/`](examples/governance-hardening/README.md) 逐条对应，**昨晚的参考实现今早被第三方公告背书**。
- 🆕🆕 **Gartner 首个 AI Agent Management Platforms Magic Quadrant — 治理被立成品类，且逐家承认「第三方运行时管不住」**：Microsoft 列 Leader；**Dify** 被点名「最强控制只针对内建 agent、第三方运行时治理有限」、AvePoint「缺实时运行时控制」、Gravitee「只有流量过它的网关才生效」、UnifyApps「最深执行仍在自有 runtime」→ **「买方指定 Dify/hiagent 做编排 + 外挂 Java/Spring 治理增强层」成为最现实打法**。同期权威数字：MCP 企业渗透 **~78%** / **4 亿+ 月 SDK 连接**、NVD 中 **382 条** MCP CVE（2026 年 324 条、79 Critical）、514 台公开 server 中 **195 台（38%）** 无鉴权暴露、2026 社区新建 **16,000+** server、[AI Agent 通信协议市场 **$235M→$1,725M / CAGR 49%**](https://www.knowledge-sourcing.com/report/ai-agent-communication-protocol-market)（Managed MCP mediation 为最大品类）、**OCC** 把 agentic-AI 纳入模型/操作风险审查、审计方 **12–18 个月**内索要 agent inventory。
- 🆕 **治理产品化四款同周落地 + GDPR ROPA 成采购文档**：[DigitalOcean Action Gateway](https://www.digitalocean.com/resources/articles/mcp-security)（16,000+ 工具 / 500+ 供应商，session tool selection「搜到 ≠ 有权运行」、allow/ask/deny 默认 ask 且 deny 优先、bound approvals 绑 session+tool version+args、凭据执行时解析「模型看不到 token」、microVM + egress allowlist、**审计不过期**）/ [ISI Markets](https://isimarkets.com/insights/isi-launches-mcp-server-to-bring-trusted-data-and-intelligence-into-clients-ai-workflows)（**Governed access**，按订阅/entitlements/AI 使用权授权 + 来源引用）/ [Agiloft](https://www.agiloft.com/news/execution-focused-ai-agents)（写操作 **preview + 显式人工确认 + 结果报告**）/ Subaio（PSD2 开放银行，AI 永不拿登录凭据、无转账权限、显式同意）；[Peliqan GDPR 采购参考](https://peliqan.io/blog/gdpr-compliant-mcp-servers/) 把 **ROPA 七字段**列为 DPA/保险/SOC 2 必索工件（自动化每年省 DPO **40–80 小时**）→ 与广发「日志不可篡改 + 按客户经理隔离」同构，已补进 [`docs/enterprise-rfp-checklist.md`](docs/enterprise-rfp-checklist.md)（**16 类攻击面**）。
- 🆕 **🛠 攻击路径 → 告警规则落地（兑现 V1.64 待办）**：新增 [`examples/monitor-alert-rules/`](examples/monitor-alert-rules/README.md)（Prometheus 4 组 15 条 + `McpAlertService` 应用内 6 条 + `mcp_security_*` 埋点契约 + 10 条逐项验收方式），覆盖「一个字符 bearer / 伪造 command 注册但握手正常 / host-header 绕过 / 内存抠主密钥 / 跨源重定向带 token / 受众不匹配 / 非白名单子进程」。**诚实标注**：`mcp_security_*` 为约定契约、`mcp-monitor` 尚未导出，需由网关拒绝分支补埋点；示例不进 reactor、零构建风险。
- ℹ️ **今日国内公告诚实标注**：新检索到的黑龙江商业职院等保 69.8 万（11-11 截）/ 济源 5G 基地强电 625.53 万（11-26 截）/ 合肥一院 UPS 79 万（11-26 截）/ 国网山西第四次物资（11-30 截）经核**均为泛硬件/等保标、非 MCP 直接机会**，仅作「私有化 + 等保刚需持续放量」旁证，未计入可投清单；真正倒计时仍是山东重工 11-20 / 厦大 11-11 / 军队 190 万 11-16 / 中山 399 万 11-17 / 呼市 900 万 11-23 / 中国林科院 300 万 11-24 / 中央民大 3712 万 11-25。
- 🆕🆕🆕 **🔥 广发证券 3 天内第二个 MCP 征集：《智能金融资讯数据服务平台》供应商征集（延长至 11-13 17:30）— 本期最紧窗口**：公告原文「引入 **MCP 工具协议、Skill 技能包、子 Agent 编排、长期/会话记忆和混合检索**…建设**券商级**智能金融数据服务平台」「沉淀**可外部产品化**的能力组件」；资质要求成立 3 年+、注册资金 ≥500 万、原厂或授权代理、近 3 年 ≥1 个**金融行业**成功案例 → 与 11-04《2026 企业微信持续建设项目》（MCP 五件套写进供应商资质门槛 + 权限可控 + 留痕可完整回溯）构成**同一头部券商采购双连发**，说明金融买方从试点进入平台化采购，命中 `mcp-registry`+`mcp-governance`+`mcp-tenant`+审计留痕（详见 [`docs/earnings-report-2026-11-06.md`](docs/earnings-report-2026-11-06.md)）。
- 🆕🆕 **山东重工集团 人工智能算力及平台建设项目（二次公开招标，11-20 截止，潍坊，接受联合体）**：建集团级大模型平台底座，「**统一入口、统一服务、统一管理、统一安全管控**」，模块含大模型开发管理与**智能体应用平台**、**大模型安全护栏**、企业知识库、代码开发平台 → 制造业集团级统一治理卡位，直接命中 `mcp-registry`+`mcp-gateway`+`mcp-governance`。
- 🆕🆕 **重庆 AI 大单 3.4412 亿元 11-03 中标候选人公示**：重通院 + 中移系统集成 + 科大讯飞 + 中智股份联合体，招标范围明确含「**智能体及应用场景建设**」→ 从招标走到定标，Agent 工具接入/治理可作分包与案例背书。
- 🆕 **「不能上云的钱最好赚」（财富机会雷达 11-04）**：私有化部署已从技术选项变成招标**必选项**，大模型备案 + 等保 + 密评三项成标配预算（纯服务费、毛利高）；广西数智方志 1450 万（6 智能体协同 + 备案 + 等保 + 密评，11 月预采）/ 呼和浩特卫健委 900 万（11-23 截，接受联合体）/ 中国林科院 300 万（11-24 截）→ **Java + Spring AI + 私有化 RAG 是这类标的主场，Python 系反而弱项**。
- 🆕🔥 **Wiz.io 90 天蜜罐：AI 网关/MCP 已被当产业化云入口打（11-06，最强安全实证）**：`CVE-2026-59822` 一个字符 bearer token 过 LiteLLM MCP Gateway 鉴权 + `CVE-2026-42271` 伪造 server 配置 `command` 启动矿机且**握手正常** + `CVE-2026-48710` host-header 绕过 → 链式未认证 RCE 归因 **Qilin 勒索**；攻击者从运行中的 Python 进程内存直接抠出 proxy master key。同期 NVD 新增 `CVE-2026-123272/123271`（OAuth resource confusion 8.0）/`CVE-2026-111769`（TS SDK 跨源重定向泄漏 refresh_token）/`CVE-2026-121419`（会话指纹按方法名→劫持 8.6）/`CVE-2026-121458`（MCP 工具跳过对象级授权）→ **「MCP 是审核更少的接口」**；已落成 12 类攻击面 → 治理模块售前一页纸（[`docs/enterprise-rfp-checklist.md`](docs/enterprise-rfp-checklist.md)）与参考实现（[`examples/governance-hardening/`](examples/governance-hardening/README.md)）。
- 🆕🆕🆕 **🔥 Uber 内部 MCP Gateway 生产实证（11-02 报道）— 治理增强层首次拿到超大厂生产级架构对标**：一个网关托管 **800+ MCP servers / 5,000+ tools**；**每个 server/tool 注册进来默认 disabled、须归属团队审核后显式启用**（与运行时权限检查分离）；依 caller 是 **human / service account / autonomous agent** 套 per-server charter policy + per-tool override；**授权与 PII 脱敏在请求路径执行**；Omni MCP 四元工具 `discover_server`/`discover_tools`/`get_tool_schema`/`invoke_tool` 懒加载 schema + Response Projection；官方结论"**Gateway 既是协议路由器，也是产品管理平台**" → 逐条命中 `mcp-registry`(准入分级/工具目录)+`mcp-governance`(逐工具策略/入参脱敏)+`mcp-tenant`+`mcp-auth`，可直接进标书「业界对标」章节；与 Bifrost「virtual keys deny-by-default」撞出同一设计共识（详见 [`docs/earnings-report-2026-11-05.md`](docs/earnings-report-2026-11-05.md)）。
- 🆕🆕🆕 **🔥 全 SDK 级「无界请求体」DoS（11-03）— CVE-2026-108395 命中的正是官方 Java SDK `mcp-core` HTTP transport**：无需认证/凭据/交互，一个超长请求即打挂进程；各家统一修为 **4 MiB 上限**；TS SDK 月下载 2.57 亿 / Py SDK 2.35 亿；Red Hat 评 Important 评估 **44 个产品**发机器可读 VEX、Debian grave。**最锋利处**：两条 SDK CVE 仅仓库级 advisory、**未进 GitHub Advisory Database → `pip-audit`/`osv-scanner`/Dependabot 全部漏报**；截至 11-03 官方 best-practices **仍无 request-body 大小限制指引**，NSA/CISA 信息表亦无具体控制项 → 话术升级「官方修实现层的洞；规范空白 + 逐工具授权/防篡改审计只能由治理增强层补」，命中 `mcp-core`(入参校验/限流/fail-closed)+`mcp-monitor`(413 告警)+`mcp-governance`。同周另有 OAuth issuer 未校验双 CVE-2026-115563/104850（7.5，恶意 server 把 client secret/refresh token 导向攻击者端点）、AWS Serverless MCP Server 路径穿越 CVE-2026-116635（**仅 v0.2.1 修复、无 workaround**）。
- 🆕🆕 **11-05 同日两款「受治理 MCP」上线 — 治理从附加项变成产品定义**：**ISI Markets**（伦敦，4,000 万+ 新闻 / 年 50 万份研报 / 4,000 万+ 宏观序列 / 200+ 经济体）标题即 "**Governed access**"，访问严格按**客户已订阅内容、数据 entitlements、AI 使用权**授权并附来源引用；**Agiloft**（CLM 合同生命周期）MCP Server 作外部工具访问中央合同库标准接口，明确"保留既有 permissions/controls/security standards"，Bulk Update 写操作**必须显式人工确认 + 先预览 + 出报告** → 高敏数据/合同业务无人敢上裸 MCP，命中 `mcp-auth`+`mcp-tenant`+`mcp-governance`(HITL)+`mcp-audit`。
- 🆕🆕 **11 月企业网关横评 — 治理 = 行业公认付费墙，且清一色非 Java 栈**：Bifrost(Go)/Docker MCP Gateway(MIT)/Microsoft(K8s+APIM)/AWS AgentCore Gateway/Kong 逐项对比 Tool filtering / OAuth2.1+per-tool ACLs / Enterprise audit&RBAC / Open-source；**RBAC、SSO、HMAC 签名审计、guardrails 普遍只在 Enterprise tier**（Bifrost 明示 Enterprise 是 OSS 严格超集）→ 本框架「**唯一 Java/Spring 开源企业级治理栈、开源即给治理**」差异化再夯实。同期中文《MCP 从工具连接到企业 AI 治理落地指南》给出七维框架 + 9 条最小基线（工具读写标注/写操作单独申请/身份透传/**关键日志≥180 天**/统一入口禁直连/配额超时/高危动作人工确认/配置走评审）→ 已抽成可进标书的 12 项治理合规对照表（[`docs/earnings-report-2026-11-05.md`](docs/earnings-report-2026-11-05.md) 第九节）。
- 🆕🆕 **国内算力/智能体预算接力（11-03~11-05）**：**重庆 AI 大单 3.4412 亿元**（11-03 中标候选公示，重通院+中移系统集成+科大讯飞+中智联合体；招标范围明确含"**智能体及应用场景建设**"）→ Agent 工具接入/治理作子项；**中央民族大学智算集群与多模态大模型 GC-HGX261278，3712 万元，11-25 09:00 截标**（含"民族语言多模态大模型智能体"）；**上海外国语大学大模型平台设备一期 589.28 万**（11-04 中标，北京华通安和/浪潮）→ 延续移动 116.66 亿算力巨单主线，命中 `mcp-gateway`+`mcp-governance`。
- 🆕🆕🆕 **🔥 广发证券《2026 年企业微信持续建设项目》供应商征集公告（gf.com.cn 采购平台）— 证券业首次把「MCP Server 工程能力」写成供应商资质门槛**：建设范围=**「智能体(Agent)+技能(Skill)+MCP 工具接入」三层体系**（6 个场景智能体 + 可复用技能 + 客户信息/资产持仓交易/业务准入规则/留痕中心等 MCP 工具接入）；**资质要求原文："MCP Server 设计与开发适配能力（工具定义与描述、参数校验、鉴权与权限控制、调用限流、日志与可观测性）"** + 金融大模型案例≥2 + 技能工程化（定义/注册/编排/复用/版本管理/效果评测）+ 须用采购方 Dify/hiagent 平台 + **数据权限按客户经理归属严格隔离、服务行为自动留痕且日志不可篡改** → 五件套逐条命中 `mcp-core`(注册/限流)+`mcp-governance`(鉴权/Scope)+`mcp-monitor`(可观测)+`mcp-tenant`(数据隔离)+`mcp-audit`(防篡改留痕)；征集公告=正式招标前 30~60 天卡位窗口，本框架可作集成商"治理底座"技术方案（详见 [`docs/earnings-report-2026-11-04.md`](docs/earnings-report-2026-11-04.md)）。
- 🆕🆕🆕 **🔥 TrendAI《代理式AI的影子工作流程》报告（11-03，工商时报）— 治理护城河最强量化背书**：分析 **19,000 个开源 MCP 服务器仓库，约 20% 存在可被利用的漏洞**；发现 **1,467 个对外暴露、完全没有鉴权机制的 MCP 服务器**；2025 年 95 个 MCP 相关 CVE，预估 2026 年 AI 相关 CVE 达 **2,800~3,600 个**；新攻击范式 **Return-to-Tool (RTT)**（恶意指令藏进客服工单/文档→代理 AI 用自身合法权限窃取权杖/加密勒索）→ "影子AI"=治理增强层两大数字可直接进标书/SEO，命中 `mcp-governance`(资产清册/访问控制)+`mcp-auth`(强制鉴权)+`mcp-core`(注入防护)。
- 🆕🆕 **本周 CVE 三连（11-01/11-02 发布）— 网关自身出洞**：**CVE-2026-120323（MCPHub 自建 MCP 网关）**非特权用户注册恶意 OpenAPI server 并配 passthroughHeaders=Authorization/Cookie/X-Api-Key → **调用者（含管理员）凭据被原样透传给攻击者** → "用了网关≠安全，网关需要治理增强层（server 准入分级/egress 白名单/凭据隔离）"，正是 `mcp-gateway` 原生设计；CVE-2026-120338 Prefab/FastMCP Generative UI 沙箱逃逸 **CVSS 10.0**（Pyodide→Deno 全权）+ CVE-2026-120315 LobeHub 路径穿越覆写启动脚本 8.8 → 命中 `mcp-core`(沙箱/最小权限/参数校验)。
- 🆕🆕 **太平金科「智能化研发工作台」单一来源采购（11-01 立项，腾讯云中）**：采购功能清单把 **MCP/Skills/专家团/记忆/权限管控** 写进保险业采购条目；硬约束=**私有化+信创**（海光 x86/鲲鹏 ARM+银河麒麟+国产 GPU≤8 卡）+双基座模型可切换+统一底座统一权限 → MCP+Skiils 成保险业采购验收标配，信创约束=Java 栈治理底座优势区，命中 `mcp-governance`+`mcp-tenant`。
- 🆕 **京北方（002987）2026 半年报**：原话"深度参与某银行金融大模型三期建设，提供人工智能开发、运营及 **MCP 服务**等全链条支持" → MCP 服务进入**上市公司财报披露级业务条目**，银行 MCP 外包服务已有成熟买单方（乙方模式），命中全栈。
- 🆕🆕 **🔥 招投标 MCP 品类爆发（MCP 从"开发协议"进化为"企业数据/业务接入标准"，11-03）**：**剑鱼标讯 MCP 正式上线**——十余年招投标数据资产全量通过 MCP 开放，接入 Coze/WorkBuddy/Trae，对外暴露 **7 大类 19 个工具**（查招标/找拟建/企业尽调/专项债），同一 APIKey 调用，原话"过去每类数据都要单独开发对接，如今 MCP 把接数据标准化、一次配置多端可用"；**中国招投标信息查询 MCP**（mcpworld.com 托管服务，查全国招标/中标，streamable-http，接入 Claude/Cursor/Cherry Studio）同步上架 → 垂直 MCP 已成独立商业品类，命中 `mcp-registry`(工具注册/数据接入)+`mcp-gateway`(AI 能力出口)，且"只给连接、不给治理"= 本框架治理增强层溢价空间（详见 [`docs/earnings-report-2026-11-03.md`](docs/earnings-report-2026-11-03.md) + [`docs/blog-java-mcp-tender-mcp-category-java-hiring-2026-11-03.md`](docs/blog-java-mcp-tender-mcp-category-java-hiring-2026-11-03.md)）。
- 🆕🆕 **自由职业市场全面 MCP 化（接单赛道 MCP 化，11-03）**：Fiverr MCP server（8-25 更新，无需账号仅搜索）+ Freelancer.com Hire MCP + Contra MCP + SynapticRelay MCP 相继上线——AI 助手直接在对话里搜人才/发标/管里程碑；费率复核 Upwork 0–15% / Fiverr 20% / Freelancer 10% / Contra&SynapticRelay 0% → 招聘/接单本身的 MCP 化反向放大"企业自建 MCP 治理能力"需求，命中 `mcp-governance`+`mcp-audit`。
- 🆕 **Apify Upwork Jobs Scraper MCP（$0.80 / 1000 jobs，11-03）**：把 Upwork 职位抓取做成 MCP 工具，数据抓取 MCP 商品化 → 印证"数据/信息差"类 MCP 已成可计费品类，对齐 `mcp-tools`+`mcp-registry`。
- 🆕🆕🆕 **安全护城河延续（11-03）**：🔥 **官方 TS/Python SDK 再三连击**——CVE-2026-104850（TS SDK OAuth 凭证泄漏 CVSS 7.5，恶意 server 骗取 refresh_token/client_secret，修复需 expectedIssuer）+ CVE-2026-116684（Py SDK authz 6.8，bearer **aud** 不校验 resource-confusion 越权）+ CVE-2026-116686（Py SDK SSRF 6.5，schema `$ref` 盲拉 URL/文件 CWE-918）→ "升级官方 SDK ≠ 安全"实锤，命中 `mcp-auth`(issuer/audience 绑定)+`mcp-core`(SSRF/出网隔离)+`mcp-governance`；🆕 **NHI Mgmt Group 发布《MCP Gateway and MCP Server Governance Guide》** 把"网关+客户端策略双重治理"（egress 控制/secret 出网隔离/HITL/审计日志导 SIEM）写成规范，逐条命中 `mcp-gateway`+`mcp-governance`（详见 [`docs/earnings-report-2026-11-03.md`](docs/earnings-report-2026-11-03.md)）。
- 🆕🆕🆕 **🔥 企业级 MCP 网关进入「采购指南 / 选型」时代（最强新叙事，11-02）**：CData 5 条采购准则（每请求强制身份/策略可审计/部署贴合/Token 吊销干净/合规）+ Tray 11 网关对比 + Metorial 10 最佳 + SumatoSoft 架构手册 + Mintive 工程选型——**西方买方已用"治理"维度挑网关，且列出的每个治理维度都是 `mcp-governance`/`mcp-auth`/`mcp-audit` 的英文同义词**；开源网关里 Bifrost(MIT)/Obot(MIT)/IBM ContextForge(Apache-2.0) **均非 Java 栈** → 本框架是"**唯一 Java/Spring 开源企业级治理栈**"差异化再夯实（详见 [`docs/earnings-report-2026-11-02.md`](docs/earnings-report-2026-11-02.md) + [`docs/blog-java-mcp-gateway-buying-guide-apple-tender-2026-11-02.md`](docs/blog-java-mcp-gateway-buying-guide-apple-tender-2026-11-02.md)）。
- 🆕🆕🆕 **🔥 中原银行企业级 AI 网关项目（招标编号 DC-2026-05-29-249）— 活标，金融 MCP 网关最强新主线**：建设范围明确含 **AI 网关、管理平台、大模型代理、AI Agent 代理、MCP 代理**；服务期 **14 个月（2 月建设 + 12 月原厂免费维保）**；1 家中标人；要求 ISO9001 + 2023 年以来至少 1 份同类型（AI 网关/大模型网关/API 网关/微服务网关/流量网关/安全网关）业绩 → **银行赛道把"AI 网关 + MCP 代理"写进正式公开招标**（接人保资产 10-30 截标后的金融主标位），命中 `mcp-gateway`+`mcp-registry`+`mcp-governance`，可作"金融 MCP 注册+网关治理"投标矩阵新锚点。
- 🆕🆕 **湖北省疾病预防控制中心「人工智能+预警预测场景建设」（2026-11 采购意向，10-08 发布）**：采购需求"以 **MCP 插件接入国家平台** + 与三大中心集成 + 面向全国疾控提供标准化 AI 服务、一次建设全国复用"；须满足 **多租户适配 + 等保三级 + 数据安全 + 突发公卫事件高并发高可用** → 完美对齐 `mcp-registry`+`mcp-tenant`+`mcp-governance`，首个点名 MCP+等保三级的卫健系统标。
- 🆕🆕 **厦门大学中国东南海疆治理实验室「大模型智能计算平台」公开招标（ccgp，提交截止 2026-11-11 09:00）** — 高校/科研院所智算接力放量，MCP 治理作子项。
- 🆕🆕 **某军队项目「计算机设备及软件采购」（2026-JC17-W1010，截止 2026-11-16）** — 构建 **大模型安全防护系统**（输入/输出内容安全防护 + 安全风险可视化监测 + 高可用架构），预算 **190 万元**；特定资质须 **武器装备科研生产单位二级以上保密资格或涉密信息系统集成乙级以上** → 安全赛道扩到军队大模型安全防护，命中 `mcp-governance`(合规/沙箱)+`mcp-core`。
- 🆕🆕 **安全治理护城河延续（11-02）**：eCorpIT「11 MCP CVE 加固清单」（MCP Inspector <0.14.1 / mcp-remote 0.0.5–0.1.15 / OX Security 统计 7,000+ 公开可达 server / token audience 校验 + egress 代理 + 沙箱）；NeuralTrust「registry+gateway 都必须有、GitHub Copilot registry-only 可改配置绕过、CVE-2026-103572（一个 trailing dot 绕过校验，Microsoft 10-13 修复）」；NHI「43% 命令注入、协议无内建认证、凭据生命周期无共享模型」；NIST **CVE-2026-11624 DNS rebinding（Origin 校验为主动修复义务）** → "升级官方 SDK ≠ 安全"叙事延续，`mcp-governance`(fail-closed/参数校验)+`mcp-core`(沙箱/Origin 校验/SSRF) 对症。
- 🆕🆕 **🔥 中山市岸线合围快封视频图像结构化算力支撑平台（广东·中央预算，¥3,994,611，11-17 截）** — AI 服务器+AI服务器2（共 ¥3,994,611.25），公安视频结构化算力 → 命中 `mcp-gateway`(AI 能力出口)+`mcp-governance`(合规/工具级 Scope)；安全赛道从金融/军工/网信扩到公安视频治理。
- 🆕🆕 **🔥 某单位信息化建设项目服务器、基础软件（上海·中央国家机关政府采购 GC-HGX261404，¥4,416,137，11-19 截）** — 信息化底座+服务器+基础软件，中央预算大单，MCP 治理作子项，对齐 `mcp-registry`+`mcp-governance`。
- 🆕🆕 **🔥 赤峰市公安局人工智能大模型算力硬件装备采购（内蒙古·竞争性谈判，¥1,790,040，11-02 截）** + **准格尔旗公安局数据采集系统服务器采购（内蒙古·询价，¥679,800，11-06 截）** — 公安 AI 算力/数据采集密集放量 → 命中 `mcp-governance`(合规/沙箱)+`mcp-gateway`。
- 🆕 **🌐 LobeHub 上线 `@pipeworx/india-tenders` PREMIUM MCP server（10-26）** — 把印度政府招标/采购数据（CPPP/GeM/国防/9 省州）做成 MCP 工具（search/retrieve/list）→ "招投标数据 MCP 化"成独立商业品类，本框架 `mcp-registry` 可对标承接国内政企招投标数据接入。
- 🆕🆕🆕 **🔥 ZC26G280436「AI网关、MCP注册、多模态平台」中标结果公告正式落地（10-27）— 公开最高价「MCP 注册中心」央企标铁证**：西安志诚电子 **683 万元中标（公示期无异议）**，招标人中国航空工业集团西安飞行自动控制研究所（编号 ZC26G280436，1 个月内到货、不接受联合体）→ **直接验证 `mcp-registry`+`mcp-gateway`+`mcp-alibaba`+`mcp-governance` 建设范围进入 600–800 万级央企生产预算**，治理独占 ~20%≈137 万；[`docs/zc26g280436-win-analysis-2026-10-29.md`](docs/zc26g280436-win-analysis-2026-10-29.md)（拆解+报价锚 600–800 万+自检7问）从"预测"变"实证"。中标结果公告：https://www.365trade.com.cn/jhwzb/888285.jhtml
- 🆕🆕🆕 **🔥 中国移动 2026–2027 新一代人工智能超节点设备集中采购 — 神州鲲泰第五中标候选 116.66 亿元（不含税，10-30 公告）**：投标报价 **11,665,750,379.84 元**、中标份额 2048 卡、公示截止 11-02 → AI 算力基建国家级巨单持续放量，央企 AI 平台标普遍把 **MCP 注册中心+网关+治理（等保）** 作子项，本框架"治理拼图"需求侧确定性再升一档。
- 🆕🆕 **🔥 浙江省公安科技研究所「大模型跨域安全硬件设备采购意向」（预计 2026-11 采购）**：含 **API 网关服务一体（数据库同步/文件交换/消息交换/API 网关）+ 智能体跨网异步会话 + 流式通信 + 多模态消息封装 + 阻断非规范智能体协议流量** → 公安/安全赛道把"智能体协议合规治理"写进采购需求，命中 `mcp-governance`(协议合规/工具级 Scope)+`mcp-gateway`(非法协议流量拦截)+`mcp-core`(沙箱)；安全赛道从金融/军工扩到公安网信。
- 🆕🆕 **🔥 SNS Insider 权威市场报告：2026 MCP 市场 $1.64B / CAGR 37.22%（最保守可靠口径）**：2026 全球 MCP 市场 **16.4 亿美元**、2035→**283.6 亿美元**、CAGR **37.22%**（2025 $1.20B）；美国 2025 $0.45B→2035 $10.27B；**2026-02 微软把 MCP 安全治理扩展到 Copilot、2026-03 Anthropic 路线图列"治理改进/企业就绪"** → 治理增强层长期确定性被权威报告坐实（IIM 中国两口径 42.8 亿/85 亿波动大，SNS 16.4 亿更稳）。
- 🆕🆕🆕 **🔥 天风证券 MCP Server 调用量突破 1800 万，登顶阿里百炼平台 MCP 全品类第一（10-28 同花顺）** — 头部券商已把 MCP 做成生产级调用入口（6 月发布天风 MCP Server，接入阿里百炼/阿里云百炼平台），从"面向开发者"到"面向 C 端智能体"双线布局 → **金融 MCP 治理（凭证/权限/审计/多租户）需求确定性再升一档**，直接命中 `mcp-governance`+`mcp-auth`+`mcp-audit`；叠加中原银行/上海农商/人保资产/金融电子化集团形成完整"**金融 MCP 注册+账号治理+网关治理**"投标矩阵。
- 🆕🆕🆕 **🔥 MCP Enterprise-Managed Authorization（EMA）扩展正式 stable（10-30 官方 RSS 头条）— "连接授权标准化"坐实，逐工具治理空白=本框架定位最干净话术**：Anthropic / Microsoft / Okta 站台，Asana / Atlassian / Canva / Figma / Linear / Supabase 已支持 EMA，用户首次登录即自动连授权范围内 server、免逐 server OAuth 弹窗。**但 EMA 只做连接级授权，完全不碰逐工具权限/审计/限流/HITL/多租户** → 本框架 `mcp-governance`+`mcp-auth`+`mcp-audit`+`mcp-tenant` 即"官方方案的治理增强层"，销售话术："官方帮你把门打开，我们决定谁能进哪个房间、动之前要不要签字、动完留不留底"。
- 🆕🆕 **🔥 10-27 命令注入 CVE 波细节坐实（ThreatAft 复盘 CVE-2026-117558~117573）**：9 server 在 2 分钟内由 VulnCheck 发布，**8/9 无修复版本**，7 条 CVSS 7.8/8.5，agent 参数直接拼 shell（`shell=True`/`execSync`）prompt-injection→OS 命令执行，5 个尝试的 allowlist 全被 `$()`/反引号击穿 → 命中 `mcp-governance`(参数校验/fail-closed)+`mcp-core`(沙箱)。Gruve.ai 同期发文"生产级 MCP 授权=逐工具权限+最小特权+OAuth+审计"直接对齐本框架治理模块。
- 🆕🆕 **🔥 官方 Python SDK 双 CVE（10-27）再证"升级官方 SDK ≠ 安全"**：CVE-2026-116686（SSRF 6.5，schema `$ref` 盲拉 URL，修复 1.30.0/2.2.0）+ CVE-2026-116684（bearer audience 不校验 6.8，resource-confusion 越权）→ 命中 `mcp-governance`(鉴权校验)+`mcp-core`。10 月累计 30+ MCP CVE，治理层成必选项。
- 🆕🆕 **长庆油田「专题2·MCP 服务」已中标候选公示（北京互时科技，10-16）** — 央企把 MCP 注册中心/服务当独立采购条目，从 ZC26G280436（683 万）扩散到油田场景，对齐 `mcp-registry`+`mcp-governance`。
- 🆕 **中国计量科学研究院「计量+AI」算力平台及信息化基础设施 924.62 万中标（10-29）** — AI 基础设施持续放量，MCP 治理作子项。
- 🆕🆕🆕 **🔥 Okta for AI Agents（10-22）把 MCP 治理做成身份层产品：CIMD-based registration GA + MCP Server Catalog governance + DCR + Multi-MCP Custom Auth Servers** — 身份厂商亲自下场证明"MCP server 治理是独立品类"，`mcp-auth`(委托授权/on-behalf-of)+`mcp-registry`(私有注册中心) 即开源同款，治理增强层定位获第三方厂商侧印证。
- 🆕🆕🆕 **🔥 NHI Mgmt Group / Jozu（10-27）"MCP server security: are registry and runtime controls enough?"** — 治理四问（what servers exist / which official / what credentials / what systems）+ 建议**内部私有 registry（mirror approved servers）+ 签名准入策略** → 直接命中 `mcp-registry`(私有化镜像/内部注册中心)+`mcp-governance`(签名准入/审计/运行时控制)，"光有官方 registry 不够"= 治理增强层最强背书。
- 🆕🆕 **dev.to《MCP Server Security: How to Vet a Server in 2026》（10-28）** — 核心"MCP server 安全是**采购决策**而非开发细节；官方 registry 只证 provenance 不证 safety；tool poisoning（描述投毒/rug pull/shadowing）+ credential over-reach 已发生" → 治理缺口=企业采购风险，本框架 `mcp-governance`(T0–T4+HITL+Scope)+`mcp-auth`(最小权限) 对症。
- 🆕🆕 **阿里云百炼 10-19~10-23 周报新增 11 款 MCP 服务（金融风控/银行卡核验/内容创作）+ Tiger OpenAPI Remote MCP（OAuth2，证券行情/账户/交易）** — 金融 MCP 生态在阿里云侧持续爆发，且"按 scope 限定能力"正是 `mcp-governance` Scope 映射范式 → 金融 MCP 治理需求侧放量。
- 🆕🆕🆕 **🔥🔥 ZC26G280436「AI网关、MCP注册、多模态平台」10-27 已开标：西安志诚电子 中标 683万元（目前公开最高价「MCP 注册中心」央企标）** — 中国航空工业集团西安飞行自动控制研究所（编号 ZC26G280436，10-20 09:00 截标，3 个月到货，不接受联合体）→ **直接验证 `mcp-registry`+`mcp-gateway`+`mcp-alibaba`+`mcp-governance` 建设范围进入 600–800 万级央企生产预算**。**🆕 中标拆解与报价锚定（核心挣钱资产）** → [`docs/zc26g280436-win-analysis-2026-10-29.md`](docs/zc26g280436-win-analysis-2026-10-29.md)（683 万拆解：治理独占 ~20%≈137 万 + 逐模块对标 + 同类标报价锚定 600–800 万 + 乙方自检 7 问话术）；**投标响应包索引/Checklist（已封板复用）** → [`docs/zc26g280436-bid-package-2026-10-17.md`](docs/zc26g280436-bid-package-2026-10-17.md) ｜ **技术方案模板** → [`docs/tender-proposal-ZC26G280436-2026-10-03.md`](docs/tender-proposal-ZC26G280436-2026-10-03.md) ｜ **投标响应正文** → [`docs/zc26g280436-bid-response-2026-10-20.md`](docs/zc26g280436-bid-response-2026-10-20.md) ｜ **等保对照** → [`docs/mcp-governance-dengbao-compliance.md`](docs/mcp-governance-dengbao-compliance.md) ｜ **监管合规映射** → [`docs/mcp-governance-regulatory-compliance-2026.md`](docs/mcp-governance-regulatory-compliance-2026.md) ｜ **安全简报** → [`docs/mcp-security-incident-brief-2026-10.md`](docs/mcp-security-incident-brief-2026-10.md)
- 🆕🆕🆕 **🔥 人保资产（PICC AM）2026 年资讯备用终端及 MCP 采购项目（10-22 公告，比选采购）— 金融央企第一次把"MCP 账号"单列采购**：采购内容含**资讯备用终端及 MCP 账号**，报名 10-22~10-26、**响应截止 2026-10-30 15:00（上海浦东世纪汇广场）**；MCP 接入已从概念进入资管/保险采购清单，**MCP 账号治理（凭证管理/鉴权/审计/多租户）成为采购条目** → 对齐 `mcp-registry`+`mcp-governance`+`mcp-auth`，与中原银行 AI 网关(含 MCP 代理)中标候选/上海农商银行 POC(MCP 插件市场+服务网关治理)/金融电子化集团智算 形成"**金融 MCP 注册+账号治理**"投标矩阵。**联系人 陈琦 021-38571848 chenqi@piccamc.com；**🔥 报名 10-26 今日截止、响应 10-30 15:00 截仅 4 天**，备齐"监管合规映射+等保表+ToFu 映射+安全简报"即可成稿（仓库 `docs/` 已全 prepared）。**
- 🆕🆕🆕 **🔥 RelayShield MCP Proxy Firewall（治理层产品化第三方验证，$49/月）— "治理能力可收费"被商业产品坐实**：RelayShield 把"在 Agent 与 MCP server 之间筛查每一次工具调用与响应、拦截已知恶意指标/钓鱼/脏邻 server"做成 $49/月订阅 + 免费预部署配置扫描器；**有人愿意为"治理/筛查"持续付费** → 本框架 `mcp-gateway`+`mcp-governance` 即"开源版 RelayShield + 企业级治理（RBAC/HITL/审计/多租户）"，差异化卖点被商业产品间接背书。
- 🆕🆕 **CSDN 流烟默 10-17 系列「MCP 第6篇：Spring AI Alibaba 封装 MCP：注册中心、网关与企业治理」— 官方生态自己写"企业级 MCP 治理（鉴权/限流/审计）"**：直引"基于 Nacos 动态服务发现 + 具备鉴权、限流、路由、灰度和审计能力的 MCP 网关"并单列"企业级 MCP 治理"章节 → **官方只给"注册+路由+协议转换"，治理/审计/限流/HITL 仍是空白**，本框架"治理增强层"定位被官方内容再坐实。
- 🆕🆕 **🔥 "Your MCP Server Is the Attack Surface Now" — 10 月 = MCP 安全史上最差月份（最强护城河叙事）**：Langflow RCE（CVE-2026-105697 CVSS 9.9，config `command` 无白名单直接 `bash -c`）/ x64dbg 无认证调试器（CVE-2026-107824 CVSS 9.3，暴露内存读写/任意文件写）/ 官方 fetch SSRF（CVE-2026-104120 7.3）/ Python SDK 凭证盗窃（Cycode 7.5）；**MCPTox benchmark 45 server 投毒成功率最高 72.8% 且模型越强越脆弱**；Koi Security 首个真实恶意 MCP server（postmark-mcp 静默 BCC 邮件）→ "升级官方 SDK ≠ 安全"，本框架 `mcp-governance`(T0–T4+fail-closed)+`mcp-auth`(独立授权)+`mcp-core`(限流/隔离) 对症；Adversa 10 月 digest（LiteLLM 居 CISA KEV / Grafana 会话伪造 SSRF / Obot deny-list 绕过 / 两恶意 server 过 5 扫描器零告警 / 82,000 份配置硬编码密钥 / CoSAI v2.0 / "Five gates before an MCP tool reaches production"）叠加背书。
- 🆕🆕🆕 **🔥 10-27 官方批量 CVE — 9+ MCP 工具服务「命令注入」波（最强护城河新弹药）**：GitHub Advisory 同日集中披露约 9 个 MCP 工具服务 **OS 命令注入**（CVE-2026-117558~117573：ai-sdlc / DroidMind / ATXP / sf-mcp / esp-mcp / mcp-ffmpeg / Kali-GPT / nmap-mcp / hagent），**agent 工具参数直接拼进 shell 命令、未中性化元字符，prompt-injection 即驱动任意命令执行** → 直击 `mcp-governance`(参数校验/危险命令 allowlist/fail-closed)+`mcp-core`(沙箱/最小权限执行)+`mcp-governance`(JDBC 审计+traceId 溯源)；可进标书「安全合规证明」与审计报告（接 `docs/mcp-security-incident-brief-2026-10.md`）。
- 🆕🆕🆕 **🔥 治理成「身份/控制平面」问题 — NHI×Willow / CSA×Akto / TrueFoundry 三方同日背书（10-27）**：① NHI Mgmt Group×Willow「10 MCP Security Risks」— 协议不带内建认证、6 critical CVE + 43% server 命令注入脆弱，治理=身份与控制平面；② CSA×Akto「你的 CLI/IDE 是新攻击面」— approved/pinned MCP registry + least-agency + 记录每次工具调用；③ TrueFoundry「Claude Enterprise Security」— 集中式 AI Gateway + MCP Gateway = 统一治理执行层 → 三方合力坐实 `mcp-registry`(pinned/approved)+`mcp-governance`+`mcp-audit`「集中治理增强层」定位。
- 🆕🆕🆕 **🔥 CSA 双闸门模型（10-20，云安全联盟）— 治理护城河本月最强信号**：CSA 发布《MCP Tool Exposure: Is a Privilege Grant? A Two-Gate Authorization Model for Agentic AI》，把"工具是否可用（能力准入）"与"具体动作是否放行（执行授权）"拆成**两道独立闸门**，要求运行时对 5 因素（谁/目标资源/参数/风险/任务）实时评估，直引 **Five Eyes Guidance**，若被 ISO/NIST/监管采纳即变审计基线。**→ 直接命中 `mcp-governance`+`mcp-auth` 运行时授权/HITL 卖点，比 CIS 55 点（静态工具清单）再进一层**；建议补进 [`docs/mcp-governance-regulatory-compliance-2026.md`](docs/mcp-governance-regulatory-compliance-2026.md) 监管合规映射表（新增 CSA 条目）。
- 🆕🆕🆕 **🔥 CVE-2026-111770（10-20 发布，CVSS 7.7 HIGH，可远程利用）— 官方 TS SDK 又爆"消息隔离"漏洞，"升级官方 SDK ≠ 安全"三连击**：`@modelcontextprotocol/server` 2.0.0–2.2.0 经 HTTP 可达时，**一个客户端的响应/通知会被投递给另一个客户端**（客户端共享单例 transport/McpServer，JSON-RPC id 从 0 自增必然碰撞，CWE-362 竞态）；修复 2.3.0 且**必须改代码**（每请求/会话新建 server+transport）。**→ 直接命中 `mcp-tenant`（租户隔离/独立实例池）+ `mcp-governance`（会话级 Scope/并发隔离）+ `mcp-core`（请求级上下文）**——官方把隔离责任甩给使用者，正是本框架"治理增强层"要补的洞；同周 CVE-2026-104850（TS OAuth，升级≠安全需 expectedIssuer）+ CVE-2026-108395（官方 Java mcp-core 7.5 DoS）形成三连击。可进标书/审计报告（接 [`docs/mcp-security-incident-brief-2026-10.md`](docs/mcp-security-incident-brief-2026-10.md)）。
- 🆕🆕🆕 **🔥 官方 TS SDK 一次性 5 CVE（10-20）且"升级后还要改配置才生效"——"升级官方 SDK ≠ 安全"升级为配置缺口实锤**：CVE-2026-111767~111771（跨客户端消息泄漏 7.7 / 任务不绑会话 8.6 / 单请求内存耗尽 7.5 / bearer audience 不校验 6.8 / 跨源重定向 6.5），修复 1.32.0/2.3.0 但 **CVE-2026-111768 必须设 `maxToolInputElements`、CVE-2026-111771 必须设 `expectedAudience`，升级 alone 不防** → 官方把隔离/限流/audience 校验责任甩回使用者，正是 `mcp-tenant`+`mcp-governance`+`mcp-core` 要补的洞；叠加 CVE-2026-104850（TS OAuth）+ CVE-2026-108395（Java DoS）成三连击护城河。可进标书/审计报告（接 [`docs/mcp-security-incident-brief-2026-10.md`](docs/mcp-security-incident-brief-2026-10.md)）。
- 🆕🆕🆕 **🔥 "连官方 SDK 都防不住回归" + CVE 命名 8–18 天盲区（10-26 新增最强护城河叙事）**：**CVE-2026-111770（TS SDK 跨客户端消息泄漏，CVSS 7.7）是 2026-02 CVE-2026-25536 的 8.5 个月后回归**——同一 JSON-RPC id 从 0 自增碰撞缺陷，1.x 线 2 月修过、2.x 线 10-20 又出现，说明"打补丁"不等于"治理能力在"；更隐蔽的是实测 **CVE 修复到进 NVD 全局 Advisory 库有 8–18 天延迟**（如 5 连 CVE 10-02 修复完成、10-20 才命名；Py/Java 3 连 DoS 10-14 修当天才进 NVD），原话"vulnerability management 只盯 CVE feeds 会盲 8 周" → **治理闭环必须含持续监测 + 审计落库（`mcp-monitor` + `mcp-governance` JDBC 审计），而非依赖补丁日**；本框架"治理增强层"价值被再夯一层。
- 🆕🆕 **🔥 Nacos 3.1.0 引入 A2A 注册中心 + "信任层才是硬仗"（10-26 生态坐实治理增强层）**：Nacos 3.1.0 正式引入 **A2A 注册中心（AgentCard 发布/发现）+ MCP 注册协议增强（私有化 MCP 注册中心 / 企业级模型市场）**；CSDN 直写"MCP + A2A 协议融合——从'协议就绪'到'信任层才是硬仗'"，A2A 信任层需要"证明我正在代表谁执行"（token exchange / on-behalf-of 机制）= 本框架 `mcp-auth` 的委托授权场景。→ **官方只给注册+路由+协议转换，认证/授权/审计/多租户/HITL 仍是空白 = 治理增强层定位再坐实**。
- 🆕🆕 **🔥 中国电信四川「基于 AI 的安全应用协同软件」直接采购（10-17~10-20 公示）点名 MCP 注册 + 安全沙箱 + 监管态势**：采购内容含智能体底座升级（Skill 编排/**MCP 注册**/记忆管理）+ 隐私计算与**安全沙箱** + 监管态势系统对接 → 运营商级央企把"MCP 注册中心 + 治理（安全沙箱/监管）"写进直接采购（预算已定、落地确定性高），命中 `mcp-registry`+`mcp-governance`+`mcp-auth`，是 ZC26G280436 收官后首个新增运营商级"MCP 注册"直接采购。
- 🆕🆕 **🔥 上海农商银行智能体开发平台 POC 征集（ITPOC20261002，10-23 标书获取截止）点名 MCP 插件市场 + 服务网关动态路由/鉴权/限流/统计**：建设含可视化工作流编排 + 多智能体协作（A2A）+ **MCP 协议接入** + 智能体标准化 API 与**服务网关（动态路由/访问鉴权/调用限流/调用统计）** + 插件管理（Skill、MCP 插件市场）→ 金融赛道从"AI 网关+MCP 代理"（中原银行）细化到"MCP 插件市场+网关治理"，命中 `mcp-gateway`+`mcp-registry`+`mcp-governance`，可作金融 MCP 治理投标素材。
- 🆕 **文件系统能力洗钱（arXiv 2610.22301，10-15）— "升级官方 SDK ≠ 安全"再实锤**：扫描 21,097 个 MCP server，16.4% 非文件系统分类却带文件能力，150 个动态确认可利用，13 公告含 **Anthropic 自家 Git MCP Server 2 CVE（CVE-2026-27735）** → 本框架 `mcp-core` 治理层（路径白名单/沙箱/短时凭证）对症；CN-SEC 量化：自动批准时工具投毒成功率 **84.2%**、82% 路径遍历、67% API 注入。
- 🆕 **MCP 实体档案（youonai）：采用封顶 + 安全成系统性风险** — 400M 月连接/78% 企业栈渗透/Agent Plugins 1.0 底座/Linux Foundation 治理；安全危机：CVE-2026-42271(LiteLLM, CISA KEV) + **CVSS 10.0** RufRoot(Ruflo MCP Bridge 暴露 233 工具) + **143,000 漏洞跨 73% 的 MCP server** → 治理缺口=系统性企业风险，本框架价值被双重认证。
- 🆕 **Nutanix 发布开源 MCP server（10 月）** — 为 Nutanix Cloud Platform，自带 **RBAC + 限流(防 agent swarm) + 审计 + HITL**；DE-CIX 开源 MCP server（10-15）用自然语言采购网络连接但**全程无 approval 步骤** → 治理成标配但只限自家平台，本框架 = 跨平台治理增强层。
- 🆕 **企业 AI 网关选型指南（9 厂商）** — 阿里云 AI 网关(MCP 服务管理)/腾讯云 AI 网关(团队隔离)/API7 AISIX(MCP 网关企业版 SSO)；Maxim AI「Top 5 Remote MCP Gateways 2026」中 **Bifrost（开源，virtual MCPs/tool filtering/6 种认证）** 直接竞争 `mcp-gateway` → 网关赛道拥挤，但"网关+跨 server 统一治理"仍是空白。
- 🔥🔥 **油气生产管理多智能体架构（专题2：MCP 服务）** — 中石油·长庆油田（**130 万**，**10-16 11:17 已截标（今日到点，结果待公示）**）→ 对应 `mcp-registry` + `mcp-governance` + `mcp-tools/tool-database`。**投标素材包** → [`docs/tender-asset-changqing-2026-10-04.md`](docs/tender-asset-changqing-2026-10-04.md) ｜ **安全事件简报（合规加分）** → [`docs/mcp-security-incident-brief-2026-10.md`](docs/mcp-security-incident-brief-2026-10.md)
- 🔥 **12316 平台「助农增收智能体 MCP 试点」** — 宁夏农业农村厅（**287.2 万已中标**，10-08 公示，中国电信宁夏 82.52 分第一，6 家投标）：**MCP 第一次写进政府采购标的名称**，标志政务 AI 进入"指名 MCP"时代；运营型+试点型是政务 AI 主流打法（周期长/续约高/数据积累强）→ 对齐 `mcp-registry` + `mcp-governance` + 中间量验收方案（首次响应时长/一次解决率/自动处理占比/采纳率）。
- 🆕 **湖北省疾控中心「人工智能+预警预测场景建设」** — **150 万**（2026-11 采购意向，10-08 发布）：明确 **MCP 插件接入国家平台 + 多租户适配 + 等保三级 + 数据安全** → 完美对齐 `mcp-registry` + `mcp-tenant` + `mcp-governance`。首个点名 MCP+等保三级的**卫健系统**标，行业从能源/军工扩到公共卫生。
- 🆕 **GSA 美国政府「MCP Server + AI Agent 政府 Hackathon」**（9-11月，Databricks/OpenAI/IBM 合作，联邦 open data → MCP server 原型）→ 政府级 MCP 需求信号，未来 6-12 月 gov 标将放量。
- 💡 **算力/AI 基础设施集采持续放量**：中国移动 2026-2027 PC 服务器集采框架上限 **13.41 亿**、云南智算 **约 1.37 亿**、山东超融合 **696 万**；央企 AI 平台标普遍把 **MCP 注册中心+网关+治理（等保）** 作子项。
- 🆕 **AI 算力集采 10-13 集中公示（喂生态非指名 MCP）**：某研究所 AI 服务器 **366.7 万**（ccgp 中标，H3C R4930 G7×2）、杭州市第七人民医院智能算力服务器 **179.65 万**、国产化高性能通用算力服务器 **225.34 万** 中标候选（北京龙讯旷腾）、国家海洋环境预报中心 **7.975 万**、嘉兴公安服务器采购合同落地 → 算力底座持续放量，央企 AI 平台标把 MCP 注册中心+网关+治理（等保）当子项，本框架是治理拼图。
- 🔥 **企业侧治理分层成标配（强验证 mcp-governance）**：**Atlassian 重建 MCP Server 至 220+ 工具 / 1500 万次/日调用**，明确做 **read/write/destructive 风险分层 + OAuth 2.1 PKCE + Guard DLP + 审计**（正是本框架 T0–T4 + HITL + JDBC 审计范式）；**Precisely 把数据清洗/SAP 自动化/地址校验等全系产品发布为 MCP Server**——每个厂商上 MCP 都需要治理层，本框架即"官方方案的治理增强层"。
- 🔥 **OX Security 扫描 15,465 个公开 MCP Server**：市场**零安全护栏**、15.6% 部署在海外（19 中国 / 18 俄罗斯）、2.3% 挂在过期域名（可 $4–12 劫持）、0.45% 走 ngrok 个人隧道 → 企业**自托管 + 治理**是唯一解，`mcp-governance`+`mcp-auth`+`mcp-tenant` 直接命中。
- 🆕 **金融智能体采购进入"治理前置"阶段（10-12 腾讯新闻）**：9 月金融智能体**招标 33 条、中标 14 条披露金额约 2109 万**（单笔最高光大银行 452.9 万），银行占 **57.6%**（建行上海分行智慧医院科研经费管理智能体、天津分行财务共享智能合规审核、中行上海分行智能体安全管理平台、中国进出口银行一月三条、中国信达/长城 AMC 再现身）；**MCP 协议、多智能体协同、可观测性与安全治理**已从技术话题变成采购文件具体条目 → 本框架 `mcp-governance`(HITL/审计/Scope/限流) + `mcp-monitor`(可观测) + `mcp-registry` 直接命中"治理前置"硬需求。
- 🔥🔥 **中国电信襄阳分公司·老河口市人工智能大模型赋能平台（询比，45.43 万，10-09 发、10-16 14:00 已截标（10-16 到点，结果待公示））**：含**知识库管理 + 工单分派智能体 + 政务办公智能体 + 社会治理智能体**调测；延续湖北疾控 150 万线索，华中政务 AI 标密集 → 对齐 `mcp-registry`+`mcp-governance`+中间量验收。
- 💡 **宁夏算力/AI 集采持续放量**：宁夏建材**智算服务器**采购（10-09）、宁夏电投煤电、宁夏煤业 DEEPSEEK 算力服务器租赁（9-30）——叠加 12316「助农增收智能体 MCP 试点」287.2 万中标，宁夏已成政务 MCP + 算力双热点，适合本地化交付团队深耕。
- 🆕🆕 **📡 HERE 把地理能力作 MCP Server 上架 AWS Marketplace（10-16）**：HERE Location Reasoning 以 **MCP Server** 形式（pay-as-you-go，运行于 Amazon Bedrock AgentCore）暴露路由/地理编码/交通/地点搜索给 AI Agent；LGND/Google/RustGIS 同步把卫星影像/地理推理通过 MCP 暴露 → **MCP = 数据/能力新分发标准**，本框架 `mcp-registry`+`mcp-gateway` 定位获国际厂商侧印证。
- 🆕 **中国金融电子化集团「金融 AI 服务开放平台」智算服务器采购**（中国人民银行集中采购中心，2026-10-16 发，11-10 截）：金融 AI 平台大单，银行赛道 **MCP+治理** 需求强烈 → 对齐 `mcp-governance`+`mcp-registry`。
- 🆕 **中科院高能物理研究所 Token 订阅采购 199 万**（2026-10-16 发，11-11 截）：AI Token 订阅类采购，印证企业/科研 AI 调用规模化。
- 🆕 **南方电网数字电网科技 9 月专项采购**（2026-10-16 发，11-09 截）；**中国电科二十四所招标**（重庆，11-06 截）——科研院所 AI/算力持续放量。
- 🆕🆕 **🔥 银行级「企业级 AI 网关」含 MCP 代理已开标（10-19 公示）**：**中原银行企业级 AI 网关项目**中标候选人公示——建设范围明确含 **AI 网关、AI Agent 代理、MCP 代理**、大模型代理、管理平台；服务期 14 个月（2 月建设 + 12 月维保），第二杭州谐云 76.8 万、第三北京华毅 85 万 → **银行赛道把「AI 网关 + MCP 代理」写进正式标书**，对齐 `mcp-gateway`+`mcp-registry`，金融 MCP+治理需求实锤。
- 🆕🆕 **湖北大数据集团·智能体工作台采购（询比，武汉，10-13 开标）**：省级大数据集团下场建“智能体工作台”，国资/政务 AI 中台赛道持续扩张 → 对齐 `mcp-registry`+`mcp-governance`+中间量验收；叠加宁夏 287.2 万已中标、中原银行 AI 网关中标候选，华中/国资“指名 AI 智能体”采购密集。
- 🆕 **上海海关·百炼大模型管理平台升级及运维 107.4 万中标（10-10）**：含本地部署 **AI 网关 + 智能数据总线 + 统一可观测监控**等六类组件、1 人/年驻场；海关总署级大模型平台本地化 → 政务 AI 平台持续点名"AI 网关"，对齐 `mcp-gateway`。
- 🆕🆕 **🔥 高校/科研院所智算接力放量（ZC 收官后新主线）**：**天津科技大学高性能智算集群服务器 342 万**（包1 服务器 300 万 / 包2 交换机+调度 42 万，10-30 开标）；**云南省地质环境监测院多模态大模型综合训推智算设备 180 万**（11-10 开标）；**中国金融电子化集团 AI 智算机房基础设施中标候选公示（10-19 结束，捷通智慧/艾瑞信/金诺珩）** → "AI 平台 + 智算底座 + 治理（等保）"已成预算常态，本框架是治理拼图。
- 🆕 **天津科技大学 342 万智算集群**（2026-10-12 发，10-30 开标）：包1 服务器 8 台 300 万 + 包2 交换机4台/调度系统/集成 42 万，小微企业 20% 价格扣除 → 高校智算赛道对齐 `mcp-gateway`+算力底座。
- 🆕 **云南省地质环境监测院 多模态大模型训推智算 180 万**（2026-10-14 发，11-10 开标，政采云）：多模态大模型综合训推设备 → 智算 + 多模态，可挂 `mcp-gateway` 作 AI 能力出口。
- 🆕 **浙江财经大学·彩云云库软件服务 MCP/CLI 接口成交（10-19）**：标的含"AI 引擎模块、**MCP/CLI 接口**"，教育行业 MCP 渗透 → 对齐 `mcp-registry`+`mcp-tools`。

### 🏢 点名 MCP 的企业招聘 / 接单（11-09）
- 🆕🆕🆕 **🔥 11-07~11-09 三条新 Java + Spring + MCP（新增「医疗存量系统 MCP 化」赛道）**：**小米科技 · 高级 AI 系统开发工程师**（长春 **25000–30000 元**，11-07 14:50 发布，「设计并实现 Agent 工作流编排框架，支持**工具调用（MCP 协议）**、任务规划与自动化执行」+ 基于 Java/Go/Python 构建高并发 AI 微服务，要求 5 年+ 大型服务端 + **Spring/SpringMvc/SpringBoot/Spring Cloud** + MySQL/MongoDB/ES/Redis，加分 vLLM/SGLang/TensorRT-LLM）/ **智慧树**（上海徐汇·已上市，「基于 **Spring AI** 构建 **Text-to-SQL、RAG 增强、MCP Server**」+ 多数据源/语义模型/知识库，985/211+3 年·两周内入职）/ 🆕 **成都迈思信息 · java 开发运维（医疗 EMPI，北京）**——JD 原话「参与 EMPI 患者身份能力的 **API 化、MCP 化封装**，支撑 AI 智能体与业务系统的标准化调用」+ MDM 主数据治理 + HL7 v2.x / FHIR `$match`，要求「有 **MCP Server/Tool 封装**、AI Agent 系统集成经验」。**结论**：与 11-03 J2EE 现代化、金山办公「现有平台向 MCP Server 标准化封装」、美团「MCP Server 接入管理与工具沙箱」同构 —— 买方要的是**把已有 Java 系统的权限模型、数据源、审计要求一起搬进 MCP** 的人，不是会写 demo 的人。
- 💵 **接单定价「什么价」本期最完整答案**：[Upwork 官方 2026 时薪](https://www.upwork.com/resources/upwork-hourly-rates) AI Developer **$30–50/h** / AI Engineer **$35–60/h** / ML Engineer **$50–200/h** / Deep Learning **$30–120/h**，高级开发·AI·战略咨询 **$75–150+/h**（费率 0–15% 口径延续）；[Dawloom](https://dawloom.com/blog/mcp-server-cost-estimator) 7 档 $3,700–$72,000；[AI Agents Plus](https://www.ai-agentsplus.com/blog/what-a-custom-mcp-server-costs-to-build-and-run) 生产级 **$80K–$220K** / 企业网关 **$250K 起** / Reflekt Lab scoping $2.3–4.7K + 首 workflow $17.5K / Truto 全负载 $50K–150K 每集成每年。→ **按「治理架构」报价，不按「写代码」报价**；三档模板见 [`docs/mcp-governance-pricing-anchor-2026-11-09.md`](docs/mcp-governance-pricing-anchor-2026-11-09.md)。
- 🆕🆕 **11-06 五条 Java + Spring + MCP（存量 Java 资产 MCP 化仍是主线）**：**智慧树**（上海徐汇·上市，985/211+3 年·两周内入职，「基于 **Spring AI** 构建 Text-to-SQL、RAG 增强、**MCP Server**」+ 多数据源/语义模型/知识库）/ **箱箱共用**（上海徐汇 **20–30k·13 薪**，「Function Call / Tools + **MCP 协议、Skills、SubAgent** 构建可扩展智能体编排框架 **Harness**」+ Spring Cloud/JVM 调优）/ **电力英才网 AI 全栈**（11-06 更新·招 5 人，Java 业务系统 + Spring Boot，「业务接口和数据能力**封装为智能体 Tool**」+ ChatBI/知识图谱/**MCP 优先**）/ **创维**（深圳·硕士，11-06 发布，「具有**企业级 MCP 生态规范**：工具描述标准化、**权限控制**与错误重试机制」）/ **周大福珠宝集团**（**中国香港**，11-06 发布，API 与 **MCP** 知识服务集成 + **permission filtering** + 中/英/繁混合检索 + 访问边界回归测试，与 V1.64 记录的周大福共享工具层 JD 同源不同岗）。**结论**：买方要的不是会写 MCP demo 的人，是能把已有 Java 系统的权限模型、数据源、审计要求一起搬进 MCP 的人。
- 💵 **Upwork 把「agent 找人 → 合规雇佣」链路补齐（11-07 分析）**：子公司 Lifted 以 **$129.7M 现金**收购柏林 EOR **WorkMotion**（交易 10-30 完成、11-03 声明；自有实体 **30+ 国直雇** / contractor+agent-of-record **180+ 国**，此前已买 Bubty $20.4M + Ascen $40.8M）；平台内置人工关卡依旧（MCP 文档要求自由职业者**显式确认** agent 代写 proposal、企业 offer 可配 **最多 4 级人工审批**）。⚠️ **口径交叉验证**：V1.65 的「Q3 单季 AI 技能 GSV $112M / +55%」与本期「年化约 $450M」**是同一数据两种口径（$112M×4≈$448M）**，且**不等于 MCP 通道收入**，投提案时不可混算。选品沿用 [Upwork Jobs API](https://scrapercompany.com/upwork-jobs-api)（`client_payment_verified` / `applicants` / `hourly_min|max`），费率 0–15% + Connect $0.15 + Freelancer Plus $19.99/月、客户侧 Basic 3–5% / Business Plus 8–10%。
- 🆕🆕🆕 **🔥 11-05 新发五条 Java + Spring + MCP（存量 Java 资产 MCP 化仍是主线）**：**通义实验室 Token Foundry**（北京/杭州，11-05 发布，JD 原话「设计**工具调用协议（MCP、Tool Schema、Function Calling）**」+ 高并发 LLM 网关 + SLA/**限流熔断** + To B「可靠性、安全性、**计费**与运维体系」）/ **小米科技**（武汉 **25–30k**，11-05 14:40，「Agent 工作流编排支持**工具调用(MCP 协议)**」+ Java/Go/Python 高并发 AI 微服务 + Spring/SpringBoot/Spring Cloud）/ **商飞智能**（上海浦东 **30–50k·招 10 人**，5 年 Java + Spring Cloud/Nacos/Sentinel/Seata +「理解 **MCP 的 Client/Server 模型与工具发现-调用-结果回传链路**」+ 加分项「**agent Run 级可观测性和成本护栏**」）/ **纬英科技**（广州，企业级智能体含 **MCP 接入** + SDD + Rules/Skills/护栏 + **Eval 测试门控**）/ **聚垒科技**（武汉，「擅长 **springboot / springai**」+ 3 年 Java 服务端 + 医疗 AI）。**结论**：JD 已从「会不会用 MCP」升级到「能不能把工具调用做稳定、做可观测、做成本护栏、做限流熔断」，正是 `mcp-governance` + `mcp-monitor` 的英文同义词。
- 💵 **Upwork 费率 11-06 三源复核 + 找活也能自动化**：自由职业者 **0–15%**（按合同类型与供需提案前显示、合同期内固定）、额外 Connect **$0.15/个**、Freelancer Plus **$19.99/月**含 100 Connects + direct contract 0%；Fiverr 20% / Freelancer 10% 或 $5；**旧「20/10/5% 滑动」与「flat 10%」口径均已过时**。🆕 [Upwork Jobs API](https://scrapercompany.com/upwork-jobs-api)（11-06 端点与价格复核）可按关键词 / 最低固定预算 / 时薪检索公开 job post，返回 `published_at` / `client_payment_verified` / `applicants`·`interviews`·`hired` / `workload` / `duration_weeks` / `contract_to_hire`，2 credits/次、7,500 credits 买断 → 可把「MCP 关键词选品、避开已爆红海单」做成结构化情报闭环，再接 Upwork 官方 MCP Server + Upwork Now（11-02 公测，周活 ×20 / Q3 AI 技能人才 GSV +55% 至 $112M）。
- 🆕🆕🆕 **🔥 周大福珠宝集团 · Assistant Manager, Enterprise AI Engineering（中国香港团队）— 今日最精准 JD**：原文「Develop a **shared tool layer using MCP** ... with consistent **identity propagation, access controls and audit trails**」+ 模型网关按**数据地域**路由 + 用量配额与成本控制 + 自动评测回归 + 日志/追踪/告警/成本监控；要求 5 年+ 且能持续手写后端代码（Java/Python/C#）→ **identity propagation / access controls / audit trails 逐条 1:1 命中** `mcp-gateway`+`mcp-auth`+`mcp-audit`+`mcp-tenant`+`mcp-monitor`，售前可直接引用 JD 原文，不必再自建论证链。
- 🆕🆕 **美团 CatPaw Managed Agent 平台（北京，智联）**：「**MCP Server 的接入与管理、工具沙箱与权限管控**」+ Skill 加载触发 + SubAgent 生命周期 + RRULE 定时任务/断点续跑 + 可观测性，技术栈 **Java/Spring Boot 列第一**；另美团 Java 研发工程师（算法工程，11-04 更新）要求 3 年+ 分布式 + AI Agent / **Harness Engineering** → 存量 Java 系统 Agent 化成主线。
- 🆕🆕 **国内明确报价三连**：猎聘 深圳 深度学习 **35–55k·16 薪**（工具调用 Tool Calling/**MCP**/Skill，**Spring AI 显式列为框架**）/ 赛舵智能 上海 AI 软件工程师 **20–40k**（开发维护 Tools、Skills、**MCP Server 及 CLI 工具链** + tracing/eval/降级）/ 建发 **50–80k** + 上海整车厂 **40–70k·15 薪**（V1.63 延续）。
- 🆕 **海外**：Disney Animation 温哥华 Sr SWE（「managing MCP servers for **secure tool-use by AI agents**」+ guardrails + A2A）/ Mastercard 都柏林 Senior Java（JDK17+Spring Boot+secure-by-design+PCI-DSS）/ PE 系 Applied AI Engineer（curate **MCP servers and reusable components** 作 portfolio 共享工具层）。
- 🆕💵 **Upwork 官方数字与报价口径复核（11-02 财报会）**：MCP Server 周活自 8 月 **×20**、Q3 AI 技能人才 GSV **+55% 至 $112M**，而总 GSV **−10% 至 $914.1M**、单客户 GSV +8% 至 $5,418 → 大盘收缩但 AI 实施类逆势涨；**费率口径修正**：自由职业者 **0–15%**（旧文 20/10/5% 或统一 10% 已过时）+ Connect $0.15；Work Now 5 分钟不响应即结束、当日开工、计入 JSS；报价公式 `工时 × 底价 × 1.25 ÷ (1 − 费率)`，必须等里程碑 **Funded** 才开工。**按「AI 编排者」报价（比执行者年收入高 45%、需求 +72%），不按「写代码」报价，同样工作量差 2–3 倍。**
- 🆕🆕🆕 **🔥 建发集团 · AI 应用架构师（猎聘，厦门，50–80k，11-01 更新）— 本期国内最高明确报价**：统筹集团 AI 应用架构、建统一底座避免各业务线重复造轮子；要求"**RAG、Skill 开发、MCP 开发**及主流智能体框架" + "**懂 Java 语法、Spring Boot/Cloud** 框架、数据库表设计与 SQL" → 集团级统一 AI 底座 = `mcp-registry`+`mcp-governance` 主场（详见 [`docs/earnings-report-2026-11-05.md`](docs/earnings-report-2026-11-05.md)）。
- 🆕🆕🆕 **🔥 Harness 框架设计/开发（云侧，猎聘，上海，40–70k·15薪，整车制造）**：主导模型接入层 / **Skill 注册中心 / Tool 工具网关 / 协议适配层(MCP·A2A·A2UI)** / Agent 编排；"主导云端协议标准化：**MCP 工具注册**、A2A 通信"；配额限流、多模型路由、**隐私与权限边界**、链路追踪、百万级 QPS；要求精通 **Java/Kotlin（Spring 系）** → 逐条命中 `mcp-registry`+`mcp-gateway`+`mcp-governance`+`mcp-monitor`。
- 🆕🆕🆕 **🔥 金山办公 · 后端研发专家（AI 研发效能组）（BOSS直聘，武汉，5–10 年）**：JD 原话"**推进现有平台能力向 MCP Server 的标准化封装**，为 Agent 提供稳定可靠的工具调用与数据对接能力"；Java / SpringCloud / MySQL / 分布式 / 微服务 → **需求从"新建 Server"转向"存量 Java 平台 MCP 化"**，命中 `mcp-registry`+`mcp-spring-ai-tools`。
- 🆕🆕🆕 **🔥 美团 · AI Agent 开发工程师（智联，上海）**：CatPaw Managed Agent 平台，工具调用（Function Call / **MCP**）、"**MCP Server 的接入与管理、工具沙箱与权限管控**"、SubAgent 生命周期、Workflow 确定性编排、RRULE 定时任务与断点续跑、执行日志与可观测；栈含 **Java/Spring Boot** → 命中 `mcp-governance`(沙箱/权限)+`mcp-monitor`。与 11-03 Digital Dhara "connect various nodes of J2EE" 同构，**企业遗留 Java 资产安全接入 agentic 工作流已成国内外共同主线**。
- 🆕 **珠宝通 · AI 项目经理（BOSS直聘，长沙）**：Java/Spring Boot/Vue 全栈 + 智能体平台搭建、AI 模型调优 → 传统行业信息化岗 MCP 化持续下沉。
- 🆕🆕🆕 **🔥 Upwork Now 公测（11-02 发布）— 平台侧财报级证据：MCP = 真交易入口**：Upwork MCP server 上线以来**周活用户 ×20**；Q3 **AI 技能人才 GSV 同比 +55% 至 $112M**（总 GSV −10% 至 $914.1M，每活跃客户 +8% 至 $5,418）；典型雇佣 <30 分钟、最快 <10 分钟、首次回复约 5 分钟、57% 客户希望 1 天内雇到 → 大盘收缩时 AI/MCP 技能逆势涨价与集中。
- 🆕🆕 **乙方真实报价与交付包（Codigee，11 月在售）**：基于既有产品 API **单独做一个 MCP server $5,000**，含在 MVP/App Launch **起步 $15,000**；交付要点=每管理员独立 OAuth / **按角色分配不同工具集** / 每次写操作进 change journal（前后值、可撤销）/ **破坏性动作强制人工确认** / 导入先 preview / 模型不直连 DB / 代码与 server 所有权归客户 → **几乎就是本框架治理清单**，可作报价锚（历史区间 $8K–25K 含 auth / $35K–90K 多系统写 / 受监管六位数）；提醒：冷提案负期望，invites / repeat 才有效。
- 🆕 **社区引流信号**：CSDN DevPress 新文《手写 MCP Server，让大模型调用 Java 工具》用 **Spring AI 2.0 `@McpTool` + JSON Schema 自动生成 + MCP Inspector 验证**，痛点="同一查订单方法要为 Claude/Cursor/自家 Agent 各写一遍胶水代码" → 中文 Java 社区 MCP 教程需求持续放量，`mcp-examples` 是现成答案。
- 🆕🆕🆕 **🔥 Robotaxi/RoboX · Java 研发工程师（苏州，招 3 人）30-60k·16薪**：JD 把"**AI Agent 开发、MCP 集成实践**"列为基本条件 + 精通 Java/JVM/分布式/RPC → 高薪自动驾驶平台把 MCP 集成写成 Java 岗硬技能，命中 `mcp-spring-ai-tools`+`mcp-governance`，仓库即作品集。
- 🆕🆕🆕 **🔥 Bench Zero（纽约 Bryant Park）· Senior/Enterprise Java Developer with AI（Contract 12-18+ 月）**：10+ 年 Java + Spring 生态，**"Experience with Spring AI is a strong plus"** → 与本框架技术栈（Java+Spring+Spring AI+MCP）逐字吻合的海外长期合同岗，命中 `mcp-core`+`mcp-spring-ai-tools`，开源仓库=接单敲门砖。
- 🆕🆕 **小米 · 高级AI系统开发工程师（大模型与RAG）25-30k**：职责"设计实现 **Agent 工作流编排框架，支持工具调用（MCP 协议）**"；要求扎实 Java + Spring/Spring Boot/Spring Cloud + 5 年服务端 → 大厂 AI 平台岗把 MCP 协议写进核心职责，命中 `mcp-core`+`mcp-spring-ai-tools`。
- 🆕🆕 **GSK（伦敦）· Senior Forward Deployed Engineer（Agentic Automation，截止 11-10）**：JD 含 "**tool use and Model Context Protocol (MCP)**" + Responsible AI/治理对话 + 受监管行业（GxP/GDPR/SOX）→ "MCP+合规"复合岗，医药巨头治理前置，命中 `mcp-governance`+`mcp-tenant`。
- 🆕 **Elucida Labs（San Jose）· Founding Staff AI Engineer**：职责含 "**Build tool-use capabilities and MCP servers**"（半导体制造科学计算场景）→ 垂直行业 build MCP server 成创业公司核心职责。
- 🆕 **全栈开发工程师（SaaS ERP+AI，深圳）15-30k**：要求"RAG 系统开发、AI Agent/**MCP 工具集成**、Prompt 工程"至少一项实践 + Java/Spring Boot/Spring Cloud → 传统企业信息化岗 MCP 化。
- 🆕 **深圳北芯生命科技 · Java+AI**：业务接口封装为智能体 Tool，ChatBI/知识图谱/**MCP 相关经验优先** → 医疗器械企业 Java 岗 MCP 化。
- 🆕 **生态佐证**：「**有写过 MCP 服务吗**」已进入 Java 后端大厂面试题体系（Spring Boot 最小 MCP Server 教程类内容在中文社区批量出现）→ 面试内容需求=SEO 引流新入口，本框架 `mcp-examples` 即"我搭过、跑通过"的现成答案。
- 🆕🆕🆕 **🔥 实习僧 · AI 应用开发工程师（Java 开发）18-30k/月，北京，27 届应届，截止 11-05**：JD 原话"围绕业务场景 **构建业务 Agent、MCP 工具与 Skills 体系**，推动业务智能化升级" + 熟练 Java/Spring Boot、大模型应用经验 → 应届岗把"建 MCP 工具与 Skills"写进核心职责，Java+MCP 从社招下沉到校招必考，本框架即"Java 生产级 MCP"参考实现+作品集。
- 🆕🆕🆕 **🔥 Mastercard Foundry R&D · Software Engineer II（Dublin）**：JD 明确"**Strong understanding of MCP, AGENTS.md, SKILLS, and tool-calling patterns**" + "**Java (JDK 21+) / Spring Boot**" + AI-native 全栈 → 全球顶级支付/金融公司把 MCP+Java21+Spring Boot 并列写进硬要求，金融支付赛道用 Java 栈做治理（等保/审计/多租户）天然匹配 `mcp-governance`+`mcp-tenant`+`mcp-auth`。
- 🆕🆕🆕 **🔥 Digital Dhara LLC（Charlotte, NC）· Agentic AI engineer with J2EE（Hybrid，21 小时前仍在招）**：JD 原话"**design MCP servers to connect various nodes of J2EE**" + "**authentication, authorization, auditability and human-in-the-loop controls**. Define **guardrails**" + "Banking, financial-services… highly regulated desirable" → **企业遗留 J2EE 现代化赛道开启**：用 MCP 把 J2EE 资产暴露给 agent，命 `mcp-core`+`mcp-registry` 主场（把 Java 历史系统安全接入 agentic 工作流）；仓库即作品集。
- 🆕🆕 **Infosys（Bangalore）· Java Spring Boot Developer 9-11 年**：Mandatory Skills 含 "**MCP (Model Context Protocol)**" + Java Spring Boot/Microservices/React/AWS/GenAI → 印度外包大厂把 MCP 列为 Java 岗强制技能，Java+MCP 人才池在涨，本框架降低企业"招到人就能落地"的门槛。
- 🆕🆕 **Cross Channel Recruitment · Staff Engineer（Remote，截止 12-08）**：JD 含 "**AWS Bedrock, MCP, LangChain/LangGraph**" + 安全漏洞管理 + SRE + 发布治理 → 命中 `mcp-governance`+`mcp-monitor` 英文同义词，本框架即答案。
- 🆕🆕🆕 **🔥 Apple · Software Engineer – Agent Development（jobs.apple.com）** — JD 原话"**Architect and maintain server-side agent services using Java (Spring Boot, Spring AI)**… **Apply agent safety guardrails — input validation, prompt-injection defences, output schema contracts, and sandboxed code execution**… **Experience implementing or consuming MCP (Model Context Protocol) servers and clients**" → **全球顶级消费电子把 Java Spring Boot + Spring AI + 智能体安全护栏 + MCP 写进核心职责**，命中 `mcp-governance`(输入校验/注入防御/沙箱)+`mcp-core`+`mcp-spring-ai-tools`，仓库即作品集。
- 🆕🆕🆕 **🔥 EPAM · Lead Java Engineer – AI Native（Gurgaon）** — 字面"**design, build and deploy MCP server ecosystems at project or account scale including security controls, versioning and observability**"；要求 8–12 年 Java + Spring Boot/Cloud/Security，带 8–15 人团队 → 最强 JD 模板，命中 `mcp-governance`+`mcp-monitor` 英文同义词。
- 🆕🆕🆕 **🔥 EPAM · Senior Java Engineer – AI Native（Pune）** — "**Build and deploy Model Context Protocol (MCP) servers that expose Java services, databases or internal tools to LLM-based agents**" + 日常 AI coding assistant 实战 → 全栈对齐 `mcp-spring-ai-tools`+`mcp-governance`。
- 🆕🆕🆕 **🔥 Genesis10 · Senior Java / Spring AI Developer（Pennington NJ，12+ 月合同，全球金融机构）** — "**architect, build, and scale backend services and Model Context Protocol (MCP) servers that securely connect LLMs to internal microservices, databases, and enterprise data stores**" → 金融 MCP 治理需求侧再印证，命中 `mcp-governance`+`mcp-auth`。
- 🆕🆕 **Infotel（法国 Euronext 上市）· Développeur Senior MCP Server – Java / Spring Boot** — 保险业经纪 agent 的 **MCP Server Santé（健康险）** 组件，BFF + Angular，目标 **2027-01 底生产**，SDD 方法论，估算 $45K–76K/年 → 法国保险赛道点名 Java Spring MCP，全栈对齐。
- 🆕🆕 **Upwork 实时在售定价（11 月，$149 起）**：Andrei S.（Moldova）**$149 / $349 / $799**（TS/Node, Claude Code）/ Derek H.（Canada）**$400 / $800 / $1,500**（TS, Cloudflare Workers）/ Andrei H.（Python/Go）**$300 / $800 / $2,000** → 个人接单起价 $149、含 auth $349–$1,500、含写操作 $799–$2,000；UpHunt 指南 **AI Agent/MCP 实现 $150–300/hr、2026 增长最快 Upwork 品类** → 接单定价权持续上行、治理即溢价。
- 🆕🆕🆕 **🔥 阿里云智能 · 管控系统研发专家（杭州/北京）** — 职责"主导 **镜像构建 MCP Server、运维 Skill 体系**、自然语言镜像构建"+ 精通 Java/Spring、5 年以上 → 本框架技术栈 100% 重合，仓库即作品集（`mcp-spring-ai-tools`+`mcp-governance`）。
- 🆕🆕🆕 **🔥 TWG Global AI · Senior Full-Stack Engineer, AI Engineering（NY/Santa Monica, $190–200K）** — JD 原话 "**MCP servers or clients**" + Java Spring Boot 后端 + multi-tenant infrastructure/auth/RBAC → 命中 `mcp-governance`+`mcp-tenant`。
- 🆕🆕🆕 **🔥 Genesys · Senior Software Engineer, AI Agents（Denver）** — JD 原话 "**Build and maintain MCP servers and clients**" + A2A 通信 + TypeScript/Node or Java → 命中 `mcp-governance`+`mcp-a2a`。
- 🆕🆕🆕 **🔥 Pine Labs · SDE I Forward Deployed Engineer（Noida, 12–16 LPA）** — "Design and develop AI agents... **MCP**... **guardrails, authorization, observability, HITL**" → 治理全套（`mcp-governance`+`mcp-auth`+`mcp-audit`）。
- 🆕🆕 **箱箱共用 · Java 高级工程师（猎聘·上海·20-30k·13薪）** — 职责"基于 **MCP 协议、Skills、SubAgent** 构建智能体编排框架（Harness）"+ 精通 Spring Cloud → 全栈对齐 `mcp-spring-ai-tools`。
- 🆕🆕🆕 **🔥 Citi（加拿大 Mississauga）VP Senior Applied AI Full-Stack Engineer — CAD 120.8K–170.8K**：JD 原话"**design, build, and maintain custom Model Context Protocol (MCP) servers and tools**"连接企业数据（Starburst/Trino），CIB 投行业务；Java/Spring Boot 后端硬要求 → 顶级银行把"建 MCP server 连企业数据"写进核心职责，命中 `mcp-governance`+`mcp-auth`+`mcp-registry`；仓库即作品集。
- 🆕🆕🆕 **🔥 Digital Dhara LLC（Charlotte, NC）Agentic AI engineer with J2EE — 发布 21 小时前（≈10-30）**：JD 原话"**design MCP servers to connect various nodes of J2EE**"+"**authentication, authorization, auditability and human-in-the-loop controls**. Define **guardrails**"+"Banking, financial-services... highly regulated desirable" → **金融服务业把治理四件套+guardrails 逐条写进 JD**，与 BOFA/JPMC 同列，直接命中 `mcp-governance`+`mcp-auth`+`mcp-audit`+`mcp-core`。
- 🆕🆕🆕 **🔥 EPAM Lead / Senior Java Engineer – AI Native（Gurgaon / Pune）**：字面"**Build and deploy Model Context Protocol (MCP) servers that expose Java services, databases or internal tools to LLM-based agents**"+"**security controls, versioning and observability**" = `mcp-governance`+`mcp-monitor` 英文同义词；要求 8–12 年 Java + Spring Boot/Cloud/Security + 微服务治理。
- 🆕🆕 **Upwork 真实在售 MCP 服务分层定价（live，Nov 2026 交付）**：Andrei S.（Moldova）**$149/$349/$799**（TS/Node, Claude Code）/ Derek H.（Canada）**$400/$800/$1,500**（TS, Cloudflare Workers）/ Andrei H.（Python/Go）**$300/$800/$2,000** → 个人接单起价 $149、含 auth $349–$1,500、含写操作 $799–$2,000，与官方企业档（$5K–80K，$5K+ 强制 auth+scoping+审计）形成完整价格阶梯，**治理即溢价**；UpHunt 指南 AI Agent Engineering $120–300/hr（MCP 实现 $150–300/hr）为 2026 增长最快 Upwork 品类。
- 🆕🆕 **猎聘 · Agent 研发工程师（独角兽）25-50k 上海**（9-24 更新）— JD 原话"**企业级 MCP/Skill 建设**，打通 DevOps 工具链以及办公工具生态"+ 沙箱隔离 + 企业数据安全治理 → 命中 `mcp-governance`+`mcp-registry`。
- 🆕🆕 **猎聘 ·（Java）研发工具链平台开发工程师 13-17k 上海**（10-13 更新）— 职责含"**研发工具链相关 AI 能力建设，包括 MCP 集成、Skill 开发**" → 全栈对齐 `mcp-spring-ai-tools`+`mcp-governance`。
- 🆕🆕 **阿里云智能 · 管控系统研发专家 杭州/北京** — "主导**镜像构建 MCP Server、运维 Skill 体系**、自然语言镜像构建"+ 精通 Java/Spring、5 年以上 → 本框架技术栈 100% 重合，仓库即作品集。
- 🆕🆕 **资深 Java 架构师（Robo 业务方向）苏州 50-80k·14薪**（10-20 更新）— "参与 **Agent 开发、MCP 集成与工程化**"+ 5 年+ Java 高并发 → 全栈对齐 `mcp-spring-ai-tools`。
- 🆕🆕 **大连微禹 · Java 高级工程师 成都 10-23**（硬性要求）— "熟悉 **Skill、MCP(Model Context Protocol)、Rule 机制配置**，了解 AI Harness 框架" → 全栈对齐。
- 🆕🆕 **猎聘 · Java 高级工程师 成都 10-23 13-17k** — "AI Agent 工作流（如 Cursor）、落地 AI 辅助编码 + **Skill/MCP 实践**" → 全栈对齐。
- 🆕🆕🆕 **🔥 Reflekt Lab 真实报价（开发公司口径，10-28）**：Scoping **$2,300–4,700**；首个 workflow 投产 **from $17,500**；Hosting **$50–100/月**；Running **from $470/月**；市场区间 简单单工具 **$3K–8K** / 含 auth **$8K–25K+** / 多系统写 **$35K–90K** / 受监管企业级 **六位数** → 本框架治理溢价能力（Claude Code Club 按"解锁价值"报价、HighCircl 称 Upwork 冷提案不灵需 invites/repeat、PRIMO.ai 称 MCP+A2A 成顶级架构范式）。
- 🆕🆕🆕 **🔥 JPMorganChase（Plano, TX）· Senior Lead Software Engineer – Agentic AI/Java/Python** — JD 原话"**setting up and maintaining MCP servers and building MCP-compatible tools/adapters**" + "secure coding, testing, observability, reliability, and responsible AI practices"；技术栈 **Spring AI / Spring ecosystem（Spring Boot/Security/Cloud）+ AWS（ECS/EKS/Docker/GitHub Actions）**，金融服务业（Consumer & Community Banking）→ **全球顶级银行把"MCP server 运维+治理"写进 JD**，直接命中 `mcp-governance`+`mcp-monitor`+`mcp-spring-ai-tools`，仓库即作品集。
- 🆕🆕 **大厂面试题转向 MCP 实战（hqwc.cn《大厂面试题"有写过 MCP 服务吗"》）** — 面试官考三件事：通信链路 / 从零搭最小可运行 server / 真跑通一次工具调用；Java 后端岗 MCP 已成必考 → 本框架即"Java 生产级 MCP 治理"参考实现+作品集。
- 🆕🆕 **dev.to 高赞范例《Building an MCP Server in Java with Spring AI》** — `@McpTool` + DTO 模式（只回 record 不回 entity 防数据泄漏）+ `@McpToolParam` 自动 JSON Schema + enum 强校验，正是本框架工程范式，可作 README/示例链接。
- 🆕 **江苏 24365 平台「AI 工业软件工程师」30 万/年（东莞 14 人）点名 Spring Cloud Alibaba + AI 算力底座** — Java+AI 复合岗持续放量；PRIMO.ai 趋势：AI 集成/agentic workflow 需求与费率溢价飙升，**MCP+A2A 成顶级架构范式**；自由职业费率复核（2026-10-29）：Upwork 0–15% / Toptal $60–200+/hr / Fiverr 20% / Malt 5%。
- 🆕🆕🆕 **🔥 Bank of America（美国银行）· Senior Agentic AI Engineer（Charlotte, NC）** — JD 原话"design **MCP servers** to connect various nodes of J2EE and make entire cycle agentic" + "**Design tool-calling and MCP patterns with appropriate authentication, authorization, auditability and human-in-the-loop controls**. Define **guardrails** to prevent uncontrolled code changes or production actions" + "Banking, financial-services, payments, or other **highly regulated** enterprise experience is highly desirable" → **顶级银行把治理四件套（认证/授权/审计/HITL）+ guardrails 逐条写进 JD**，直接命中 `mcp-governance`+`mcp-auth`+`mcp-audit`+`mcp-core`；比 JPMC 更明确点名治理，仓库即作品集。
- 🆕🆕 **EPAM Turkey · Lead AI Engineer（Java 17）** — "**Configure MCP servers**, apply multi-agent patterns, and set up **AI/LLM gateways** to manage routing, cost, limits, and fallbacks"；要求 Java 17+/Spring Boot 3.x（mandatory）+ 受监管行业（financial services preferred）交付经验 → 命中 `mcp-gateway`(routing/cost/limit/fallback)+`mcp-governance`。
- 🆕 **Cayuse · Associate SWE, AI Solutions（Remote，10-13 发）** — 帮科研机构建 **MCP servers** + "security, **tenant isolation**, usage tracking, and **auditing**"；监管科研域对口 → 命中 `mcp-tenant`+`mcp-governance`+`mcp-audit`。
- 🆕 **NetApp · Full Stack / AI Developer（Bangalore，5–8 年）** — Java Spring Boot + "**Experience building MCP (Model Context Protocol)**" 列为常规 full-stack 技能 → MCP server 开发已成主流 Java 厂标配技能，人才池在涨。
- 🆕 **Job Tracker MCP Server（LobeHub 社区）** — 用 **Java 17 + Spring Boot + Spring AI** 构建的 MCP server 已发布到 LobeHub 市场，技术栈与本框架 `mcp-spring-ai-tools` 完全一致 → 证明"Java + Spring AI MCP"已成可复用社区范式，框架目标用户群被验证。
- 🆕🆕 **Accso（Cape Town）· Senior Java Engineer – Agentic AI Engineering** — 8+ 年 Java/Spring Boot/Security/Data，**职责含 "MCP server configurations, and scaffolding that optimises AI collaboration"**，为 AXA/BMW/Deutsche Bahn 等德国大客户服务 → 欧洲咨询大厂把 MCP server 配置当标准交付物。
- 🆕 **牛客网 · AI 应用开发工程师（Java 开发，北京，27 届）** — 职责"围绕业务场景构建业务 Agent、**MCP 工具与 Skills 体系**"，要求熟练 Java + Spring Boot。
- 🆕 **trilogy nextgen（Charlotte, NC）· Junior Java/Spring Boot + AI/LLM/MCP** — 金融服务客户，0–2 年，签证担保，点名 MCP；延续在招。
- 🆕🆕🆕 **🔥 Capgemini Polska · Agentic Integration & MCP Engineer（Gdańsk，4+ 年 Python/TS/Java/.NET，10-22 在招，16 天倒计时）** — 职责"design and develop **MCP servers**、secure connectors、integrate AI agents with enterprise systems"，**明确要 identity / authorization / secrets management / auditability / guardrails for agent actions + evaluation/tracing/monitoring + Agentic Integration Center of Excellence** → 字面命中 `mcp-governance`+`mcp-auth`+`mcp-monitor` 英文同义词，欧洲大厂把"治理"写进 JD，本框架即答案。
- 🆕🆕 **🔥 Cayuse · Associate Software Engineer, AI Solutions（Remote，10-13 发）** — 帮科研机构建 **MCP servers**，JD 原话"authentication and permission checks、tenant isolation、usage tracking、auditing" → 命中 `mcp-tenant`+`mcp-governance`+"治理增强层"全套，监管科研域对口。
- 🆕🆕🆕 **🔥 EPAM · Lead Java Engineer – AI Native（印度 Gurgaon）** — 字面要"design, build and deploy **MCP server ecosystems** that connect enterprise systems to LLM agents" + "**security controls, versioning and observability**" = `mcp-governance`+`mcp-monitor` 英文同义词，最强 JD；要求 8–12 年 Java + Spring Boot + 评估 MCP connectors，带 8–15 人团队做 AI-native 工程。
- 🆕🆕 **🔥 建信金融科技 · 人工智能应用开发工程师（成都事业群）** — 职责"**mcp 服务发布**、智能体记忆管理"，要求 **Spring-ai-alibaba / agentscope-java** + MCP/Skill/Tool/RAG → 本框架技术栈 100% 重合，仓库即作品集可直接投。
- 🆕🆕 **🔥 Inabia · Java Full Stack Developer（纽约金融客户，$50–55/hr）** — Java/Spring Boot 微服务 + 生产级 agentic AI + **MCP + RAG + agent orchestration** + 安全/访问控制/可观测，8+ 年经验、金融客户。
- 🆕 **Market Cloud Ltd · Senior Java Developer AI LLM（伦敦）** — 生产级 generative AI + "**MCP-based integrations**" + Skills + guardrails + HITL + evals；Publicis Sapient（芝加哥 $165–205K）ADK+MCP 延续在招。
- 🆕 **Inabia · Java Full Stack Developer（$50–55/hr，纽约金融客户，10-09）** — 8+ 年 Java/Spring Boot + **agentic AI、MCP、RAG、agent orchestration**，金融场景私有化交付 → `mcp-spring-ai-tools`+`mcp-governance` 对口。
- 🆕 **北京数字政通 · 27 届校招 Agent 研发工程师（18–22K，长沙/武汉，10-08）** — Java/Spring Boot + **工具调用（MCP）** + 多智能体协同 → 校招即点名 MCP，人才池在长大。
- 🆕🆕🆕 **🔥 上交所技术公司 · 开发及产品类（上海·硕士）** — 招聘要求**明确点名"MCP 协议及 Skill 开发、RAG 全链路优化、Agent 多智能体协作"，信创优先** → **最高规格金融机构点名 MCP**，命中 `mcp-registry`+`mcp-governance`+`mcp-spring-ai-tools`，仓库即作品集可直接投。
- 🆕🆕 **申万宏源证券 · AI 应用研发工程师（上海，11–20k，10-15 更新）** — JD 原话"**MCP 开发**"提升准确率 + Spring AI/RAG/Context/Prompt/工具调用设计 → 券商点名 MCP，命中 `mcp-spring-ai-tools`+`mcp-governance`。
- 🆕 **TOPdesk · Senior Software Engineer（荷兰·远程）** — **5 年 Java + MCP tool integrations**，agentic engineering 环境（Claude Code/Cursor），日常 hands-on agentic delivery。
- 🆕 **华微软件 · AI 应用工程师（广州，10 人）** — 熟悉 **MCP 等 AI 应用技术** + Cursor/ClaudeCode/通义灵码；职责含 Agent 逻辑/工具调用/RAG/工作流。
- 🆕 **🔥 阿里云智能 · Agentic Tools 开发专家（SDK 团队·北京/杭州）** — 团队已上线 **`api.aliyun.com/mcp`（OpenAPI MCP Server，一键接入阿里云 2W+ API，Core 模式）**，自研 CloudSpec IDL 把数十万 API 变语义工具；JD 要 **Java/Golang/Python + MCP + IaC + 分布式 AI 基础设施** → 直接命中 `mcp-registry`+`mcp-alibaba`；**MCP 成官方 API 分发基础设施**的最强信号。
- 🆕 **🔥 美团 · AI Agent 开发工程师（北京，25-50k·15薪，截止 10-26）** — **CatPaw Managed Agent 平台**：MCP Server 接入与管理、工具沙箱与权限管控、Skill 体系、多智能体编排（SubAgent/Workflow/RRULE/可观测）→ **字面命中** `mcp-governance`(沙箱/权限)+`mcp-registry`，最对口 JD 模板。
- 🆕 **🔥 美团 · AI Agent 开发工程师（智联招聘）** — 职责"负责 AI Agent 系统架构与核心模块开发，含 **工具调用（Tool Use / MCP）、多 Agent 协同**、记忆管理、Planner/Executor/Evaluator"；要求 Java/Python/Go 至少一门 + 生产级 AI 系统落地 → 全栈对齐 `mcp-spring-ai-tools`+`mcp-governance`（多 Agent 编排）。
- 🆕 **阿里 · 千问事业部 助手 Agent 交互技术专家（杭州）** — 精通 Java/Golang，Agent/工具调用/RAG/记忆上下文，5 年，AI 硬件开放体系。
- 🆕 **Exerizon（Warsaw）· Mid-level Java Engineer（AI Agents, MCP）** — 为全球**保险公司**从零建高可用 MCP Server（Java 17+/Spring Boot/Spring AI MCP，JSON-RPC 2.0+SSE），B2B 远程，时薪制 → 全栈对齐。
- 🆕🆕🆕 **🔥 Moss（欧洲 fintech 独角兽，€35M C 轮、估值破 €10 亿）· Applied AI Engineer（Remote 华沙/柏林/伦敦/慕尼黑，10-27 发）** — JD 原话 **RAG, MCP, knowledge graphs**；Python **and/or Java**；金融 workflow agent 端到端 owner（架构→评估→后端→生产）→ 金融 MCP 治理需求侧再印证，命中 `mcp-governance`+`mcp-spring-ai-tools`。
- 🆕🆕🆕 **🔥 猎聘 · AI 架构/AI 原生/AI 服务端研发负责人（上海·电商，90–120k·16薪，1 小时内在线）** — 10y+ Java；精通 RAG/Agent/Function Call/向量库/**MCP**/多Agent；**「自建或参与开源 AI 工程（Agent / MCP Server）优先」** → 大厂点名「开源 MCP Server 作品集」，本仓库即答案。
- 🆕🆕 **🔥 猎聘 · AI 服务端研发负责人（上海·咨询，100–160k·16薪）** — 同上 MCP 要求 + 30+ 人团队管理，AI Coding 研发效能平台从 0 到 1。
- 🆕🆕 **🔥 字节跳动 · 火山方舟 LLM Backend Engineer（2027 校招，圣何塞，$128K–256K）** — 理解 Agent/Function Calling/**MCP**；Java/C++/Python；云原生/限流/降级 → 大厂校招即点名 MCP。
- 🆕 **广东广电网络 · AI 平台研发 / Agent 应用开发实习生（广州，10-14~10-23 报名）** — **MCP Server**、Java/Python；Agent/Tool Calling/Multi-Agent/Function Calling → 广电系国企也进 MCP 人才池。
- 🆕 **TEKsystems（Toronto）· Senior AI $208-249K CAD/年** — 建 MCP servers 暴露 PHSD 医疗数据（FHIR/R4/SMART on FHIR），Java Spring Boot + Python + Bedrock + OAuth2/OIDC/RBAC。
- 🆕 **trilogy nextgen（Charlotte, NC）· Junior Java/Spring Boot + AI/LLM/MCP** — 金融服务客户，0–2 年，签证担保，点名 MCP。
- **沃尔玛(中国) 高级 AI 平台/应用开发工程师 30-55k/月（10-27 截）** — JD 原话："建设 **AI/MCP 网关、MCP Server**，实现 Skill、Spec Registry"；加分项"Nacos/Higress/Spring AI 生态开源贡献优先"—— 与本框架技术栈 100% 重合，仓库即作品集可直接投。
- **实习僧 · AI 应用开发工程师（Java）18–30k/月 · 北京** — JD 原话"围绕业务场景构建业务 Agent、**MCP 工具与 Skills 体系**"；要求熟练 Java + Spring Boot，27 届，截止 11-05。
- **阿里云（校招/社招）· AI 应用中间件开发** — JD 原话"**AI 网关、MCP 代理、RAG**"模块；工作地含杭州/北京/深圳/成都/上海/**西安**（西安岗呼应 ZC26G280436 西安截标）→ 直接命中 `mcp-gateway`+`mcp-alibaba`。
- **法本信息 · Java 开发 AI 方向 9k–1.2 万 · 重庆** — 职责点名 **MCP / Tool Calling / Agent**，且要求 Python→Java 改造经验。
- **一览英才 · JAVA 高级研发工程师 20–30K · 深圳** — Spring Cloud Alibaba + AI（能耗预测/故障诊断），信创组合。
- **猎聘 · AI 后端工程师 12–22k · 广州** — 加分项"熟悉 **MCP**、Function Calling、多模态"，Java(Spring Boot/Cloud) + AI 中台。
- **Satrio · MCP Expert $60–120/hr · 全球远程**（10-07 发布，10-23 截）— 用 MCP 工具设计 RL 环境训练下一代 AI，要求 C++/Python/**Java**/Go/TS/Rust 之一。
- **Darwin Recruitment · MCP Expert (Java API Integration)**（阿姆斯特丹，12 月合约，费率可谈，10-03 上架）— 要求**生产级 MCP Server（Java）+ Azure** 连接企业 API。
- **OneSeven Tech · Senior Backend Engineer · MCP Infrastructure**（拉美远程，$4K–5K/月，6 月合约可续）— **Java + Spring Boot + WebFlux + SQL Server** 生产级 MCP 组件。
- **InAddition Consultants · Software Engineer (AI Solution / MCP Focus)**（台北，1.1M–1.3M TWD/年）— **用 Java 实现 MCP Server** + Docker + 完整测试。
- **Intellias · Tech Lead Engineer (MCP Server Registry)**（多国远程）— 企业级 **MCP Registry + 元数据治理 + 语义发现 + OAuth/JWT + Vault + AWS AgentCore**。
- **Cloudflare · Senior Systems Engineer, MCP Server Portals**（里斯本，€66K–91K）— MCP Portals：OAuth + Zero Trust + 跨全球网络代理 MCP 流量。
- **Upwork 官方 MCP Server 已成接单基础设施**（`mcp.upwork.com`）；AI Agent/MCP 类 **$120–300/hr**（资深 $200+ 标配）；项目包 MCP Server $90/$180/$350（进阶 $1,500），+$75/工具，+$200/系统；行业实测常态 **$1,500–5,000/项目**、复杂 **$10,000+**；SecurityScorecard 招 Senior MCP Engineer（生产级 OAuth/租户隔离/审计，ChatGPT/Claude/Copilot/Codex 客户端，$10–250/hr，50+ 提案）。组合拳：本框架作交付底座 + Upwork MCP 作获客前端。（⚠️ 2026-07 费率改革：服务费按合同 **0%–15%**，旧 20%/10%/5% 阶梯已作废；Connects **$0.15/个**——报价务必算到手价）
- 🆕 **猎聘 · 高级 Java 开发工程师（上市公司核心岗 25K·武汉·5-8年）**（今日 10-12 更新，52 分钟前在线）— JD 原话"**MCP 协议接入、AI Agent 系统开发、LangChain4j 应用开发、RAG 知识库**"，要求 Spring Cloud 微服务 + 高并发治理 → 与本框架技术栈 100% 重合，仓库即作品集。
- 🆕 **天津 · AI 开发工程师（25-35k·石化·3-5年）**（10-12 在招）— 要求 **Spring AI / Agent Scope / RAG / 企业级智能体平台或 AI 中台 / 多租户隔离** → 直接命中 `mcp-governance`(多租户) + `mcp-spring-ai-tools`。
- 🆕 **Cotality · Senior Software Engineer (MCP Servers)**（达拉斯，截至 10-11）— **MCP Server + Apigee X 网关 + OAuth 2.1 + MCP Inspector 测试**，要求 Python/JS/**Java** 5+ 年 → 命中 `mcp-gateway`+`mcp-auth`。
- 🆕 **EPAM · Senior Java Engineer – AI Native**（印度 Coimbatore）— **用 Spring Boot 构建并部署 MCP Server** 把 Java 服务/库暴露给 LLM Agent，5–10 年 → 全栈对齐。
- 🆕 **InAddition · Software Engineer (AI Solution / MCP Focus)**（台北，1.1M–1.3M TWD/年）— **用 Java 实现 MCP Server** + Docker + 完整测试 → 全栈对齐。
- 🆕🆕🆕 **🔥 RelayShield MCP Proxy Firewall（$49/月，治理层产品化第三方验证）** — 把"在 Agent 与 MCP server 之间筛查工具调用与响应、拦截已知恶意指标/钓鱼/脏邻 server"做成订阅产品 + 免费预部署配置扫描器；**有人愿意为"治理/筛查"持续付费** → 本框架 `mcp-gateway`+`mcp-governance` 即"开源版 RelayShield + 企业级治理（RBAC/HITL/审计/多租户）"，差异化卖点被商业产品间接背书。UpworkBridge 扫 10 类目 **AI/ML Implementation $85–175/hr 为最高档**（$100–250/hr 政府/国防 IT 次高），OAuth 2.0 token 管理 + 智能评分(0–100) + 提案生成。
- 🆕 **猎聘 · 箱箱共用 Java 高级工程师（20-30K·13薪·上海）** — JD 原话"**MCP 协议、Skills、SubAgent 及 Agent 设计模式**，构建可扩展智能体编排框架(Harness)" → 全栈对齐。
- 🆕 **猎聘 · 全栈(Java+AI应用) 9-13K·上海浦东** — 要求 **Spring AI / MCP / LangChain** 大模型接入、Agent、知识库 → `mcp-spring-ai-tools`。
- 🆕 **鱼泡直聘 · AI 开发工程师 8-12K·北京丰台** — 职责"遵循 **MCP 开发规范**，采用 Java 语言参与 AI 功能模块开发" → 全栈对齐。
- 🆕 **Mindera · Senior Engineer AI Specialist（罗马尼亚 Sibiu，远程友好）**（10-14 在招）— JD 原话"**building MCP servers**"：Java 17/21+ Spring Boot + Spring AI + LangGraph/LangChain4j，要求 RAG/MCP 实战深度（非年限堆叠）→ 全栈对齐 `mcp-spring-ai-tools`。
- 🆕 **Built In · Mid-level Java Engineer (AI Agents, MCP)**（全球远程，波兰语）— 为**全球保险公司**从零建高可用 MCP Server（Java 17+ Spring Boot，Spring AI MCP / 核心 SDK，JSON-RPC 2.0 + SSE），REST/gRPC/Kafka 连接器，**$75–150/hr·3-5 年** → 全栈对齐。
- 🆕 **杭州火石创造 · 高级 Java 工程师（MCP / Spring AI 方向）15–30k·5-10 年**（10-14 在招）— JD 原话"基于 Spring AI 框架开发企业级智能体平台核心服务""负责 **MCP 服务接口设计**，实现智能体与业务连接" → 全栈对齐。
- 🆕 **智联 · 高级后端工程师（LLM 应用工程化 / MCP 多智能体协作）苏州·5-10 年**（10-14 在招）— JD 原话"设计并实现 **MCP 协议下的 Client/Server 服务框架**，支持多 Agent 工具调用、跨系统协作""权限与沙箱""多租户与可扩展部署" → 命中 `mcp-governance`+`mcp-tenant`。
- 🆕 **中恒信 · AI 集成与平台开发工程师（Java/AI）青岛·应界可投**（10-14 在招）— JD 明确"探索并集成如 **MCP 等协议**，标准化 LLM 与内部/第三方工具的交互能力" → 全栈对齐。
- 🆕 **凯盛数智（中国建材集团）· 大模型应用开发工程师（AI Agent 方向）**（上海·硕士，10-10 截止）— JD 原话"**MCP(Model Context Protocol)、Workflow、Memory、Multi-Agent**"核心能力建设 → 全栈对齐 `mcp-spring-ai-tools`。
- 🆕 **物产中大云商（世界500强）· 27届校招 开发工程师（AI 应用）**（浙江）— "基于 **Java + Spring AI** 进行 AI 应用…RAG、**MCP**、Skills 技术方案落地" → 国企校招即点名 MCP+Spring AI。
- 🆕 **华微软件 · AI 应用工程师**（广州，10-12 在招）— 职责"熟悉 **MCP** 等 AI 应用技术"，大模型+智能体平台（Dify/扣子）→ 全栈对齐。
- 🆕 **广州轻快创新 · Java 后端开发（AI 全栈）15–20k·13薪**（广州·3-8年，10-10 更新）— JD 原话"探索 AI 在业务…应用，参与 **AI Agent、MCP** 等相关能力建设" → 与本框架技术栈 100% 重合，仓库即作品集。
- 🆕 **四川辰海数聚 / 宜宾人才 · Java 高级工程师（AIoT）**（宜宾·5-10年，10-11 截止）— 大模型流式交互集成、为前端 Agent 对话/知识库提供实时数据 → 全栈对齐。
- 🆕 **EPAM · Lead Java Engineer – AI Native**（印度 Coimbatore）— JD 原话"**Build and deploy MCP servers that expose Java services, databases or internal tools to LLM-based agents**"，要求 8–12 年 Java + Spring Boot/Cloud/Security，明确要"**security controls, versioning and observability**"的 **MCP server 生态（生产级，非 POC）**" → **字面命中本框架 `mcp-governance` + `mcp-monitor`**，是最对口的 JD 模板。
- 🆕 **MoOngy（里斯本，金融服务）· AI Engineer (Java/Python)** — "Build and evolve **MCP servers** and AI platform services"，Spring Boot/FastAPI/Kafka/K8s，截止 11-08 → 全栈对齐。
- 🆕 **trilogy nextgen（美国 Charlotte, NC）· Junior Java/Spring Boot + AI/LLM/MCP** — 金融服务客户，0–2 年，签证担保，点名 MCP → 全栈对齐。
- 🆕 **Publicis Sapient（芝加哥）· Principal Java Microservices Architect – AI & Agentic Platforms** — $165K–205K，JD 原话"**Agent Development Kit (ADK) & Model Context Protocol (MCP)**"，Java 17/21 + Spring Boot → 全栈对齐。
- 🆕🆕 **🔥 NVIDIA · Senior AI Engineer（以色列 Yokneam，2026-10-16 发布）** — JD 原话"**Develop and maintain MCP servers and tool integrations** used by internal AI systems" + Agent 评估/Temporal → 直接命中 `mcp-server` 生产运维岗。
- 🆕🆕 **🔥 NVIDIA · Senior Staff SWE – AI Agent Platform（美国 Santa Clara，$200K–391K，申请至 10-20）** — JD 字面点名"**agent protocols and frameworks like MCP, A2A**" + **多租户 K8s（namespace 隔离）+ OAuth 2.0/JWT/Vault + 审批工作流** → 完美命中 `mcp-tenant`+`mcp-auth`+`mcp-governance` 英文同义词，最高薪对口岗。
- 🆕 **香港大学 IT Services（CPJobs，2026-10-16）· Senior/Software Developer** — JD 明确"**integrations leveraging the Model Context Protocol (MCP) to enable large-scale AI adoption**" + Spring Boot/Java Servlets/Azure，11-01 截止 → 高校/政务级 **Spring+Java+MCP** 实锤岗（呼应本框架 Spring 技术栈）。
- 🆕 **歌尔股份（猎聘）· Agent 开发工程师（Java 后端）20–35K·东莞** — Spring Boot/Cloud + **MCP 协议** + RAG/Multi-Agent，3 年+。
- 🆕 **西南财经大学 27 届校招 · 开发工程师（AI 应用）杭州** — Java/Python + Spring Boot/Cloud + **Spring AI + MCP/Skills 优先**。
- 🆕 **中国地质大学就业网 · AI 应用开发 广州** — Java（Spring Boot/Cloud）+ 大模型/RAG/Agent + **了解 MCP**。

- 🆕🆕 **阿里云 · 校招 AI 应用中间件开发** — JD 原话“**AI 网关、MCP 代理、RAG**”模块；工作地含杭州/北京/深圳/成都/上海/**西安** → 直接命中 `mcp-gateway`+`mcp-alibaba`，西安岗呼应 ZC26G280436 收官。
- 🆕 **猎聘 · AI 应用研发工程师（物流履约，阿里系）** — 字面要求“**掌握 MCP 协议、Context Engineering、可复用 AI Skills**”、LangChain/AutoGen 多步推理编排 → 全栈对齐 `mcp-spring-ai-tools`+`mcp-governance`。
- 🆕 **实习僧 · AI 应用开发工程师（Java，18-30k·北京）** — 职责“围绕业务场景构建**业务 Agent、MCP 工具与 Skills 体系**” → 本框架技术栈 100% 重合，仓库即作品集。
- 🆕 **猎聘 · Java 后端开发（12-18k·上海）** — 点名 **Spring AI / LangChain4j + RAG + 多租户管理 + 审计日志 + 任务调度**，金融/保险私有化交付 → 命中 `mcp-tenant`+`mcp-governance`+`mcp-audit`。

### 💰 为什么 Java + Spring + AI 是 MCP 蓝海（78% 已用，但 91.5% 裸奔）
- 🔥 **MCP 已成企业标配**：企业 AI 团队采用率一年 **31% → 78%**，500 强 **28%** 已生产运行 MCP server，>10,000 server 在生产；250+ AI 工程师企业 **89%** 生产采用。
- 🔥 **安全治理是最大鸿沟（最强验证本框架）**：仅 **8.5%** 实现强制 OAuth 2.1、**43%** 命令注入漏洞、**492** 个 server 零认证裸奔、**18%** 任何工具权限 scoping；NSA+CISA 联合公告要求治理由部署方自建 → 本框架 `mcp-governance`（RBAC/审计/HITL/限流/网关集中鉴权/EMA）**直接命中痛点**。
- 🔥 **CISA KEV 首个 MCP 漏洞 + Protocol Pivoting（10 月最新，治理护城河最强证据）**：LiteLLM MCP 端点接受**任何无效 bearer token** → **CVE-2026-59822 成为第一个登上 CISA 已知被利用漏洞清单（KEV）的 MCP 漏洞**；Google `mcp-toolbox` SSRF **CVE-2026-14540（CVSS 8.0）**；同款信任边界缺陷横跨 Google/JPMorgan/Weaviate/法国政府/印尼政府（Ars Technica 10-05 头条「Protocol Pivoting」）；Grafana MCP 会话伪造 CVE-2026-19516；CoSAI 发布 **MCP 安全模型 v2.0** → 本框架 `mcp-auth`（V1.9 Bearer 强制校验 + jti 防碰撞 + 吊销端点）+ `mcp-governance`（HITL/审计/Scope/SSRF 防护）**直接命中全部缓解项**；**安全事件简报（可进标书/审计报告）** → [`docs/mcp-security-incident-brief-2026-10.md`](docs/mcp-security-incident-brief-2026-10.md)
- **Spring AI Alibaba 1.0 GA（2026-10）**：企业级 MCP = **Nacos MCP Registry + Higress AI 网关**，与本框架 `mcp-registry`+`mcp-gateway` 定位同款；但评测机构 FollowAgents 直指其 **"1 safety controls not found"（缺安全控制）** → 本框架补上最缺的**安全治理层**，定位"Spring AI Alibaba 的治理增强层"。当前稳定 1.0.0.2 GA、starter 实跑 1.1.2.0、11k stars（+399/月）、275 contributors。GA 示例 → [`examples/spring-ai-alibaba-mcp-server/`](examples/spring-ai-alibaba-mcp-server/) ｜ SEO 稿（治理角度）→ [`docs/blog-java-mcp-security-governance-2026-10-08.md`](docs/blog-java-mcp-security-governance-2026-10-08.md) ｜ **MCP 安全加固 5 步速查** → [`docs/mcp-security-hardening-quickstart.md`](docs/mcp-security-hardening-quickstart.md)
- 🛡️ **等保 2.0 三级合规对照表**：`mcp-governance` 逐条映射等保三级，每条有模块+测试证据 → [`docs/mcp-governance-dengbao-compliance.md`](docs/mcp-governance-dengbao-compliance.md)。**可直接进央企/军工/卫健标书作"合规就绪"证明。**
- 🆕 **🔥 官方 Java SDK `mcp-core` 自己爆 7.5 DoS CVE（10-14 最强护城河信号）**：**CVE-2026-108395**（Java SDK `mcp-core`，CWE-770，CVSS 7.5）——Servlet SSE/Streamable HTTP/Stateless 无限缓冲请求体、不校验 Content-Length、单请求 chunked 灌爆 OOM；影响 `0.18.x<0.18.4`/`1.x<1.1.4`/`2.0.0`。同源 **CVE-2026-109527 / CVE-2026-59951**（Python SDK 无限缓冲/会话不回收）。**NVD 现 139 条记录点名 MCP（24 Critical / 66 High）** → 本框架 `mcp-core`（治理增强层）的 RateLimit+资源隔离+审计正是对「无界缓冲 DoS」的补偿控制，「升级官方 SDK≠安全」成 10 月最硬安全叙事。安全事件简报（可进标书）→ [`docs/mcp-security-incident-brief-2026-10.md`](docs/mcp-security-incident-brief-2026-10.md)
- 🆕 **Spring AI Alibaba 生态官方化（10-13 更新）**：Nacos 3.0 发布 **MCP Server 自动注册与发现手册**（`spring-ai-alibaba-starter-mcp-registry >= 1.0.0.3`，@Tool 方法启动即注册 Nacos、工具描述/参数运行期热更新、工具运行期启停）；Higress 升级 **CNCF Sandbox v2** 开源 AI 网关——官方只给"注册+路由"，**安全治理（RBAC/审计/HITL/Scope/多租户）仍是空白** → 本框架"治理增强层"定位被生态坐实。GA 示例 → [`examples/spring-ai-alibaba-mcp-server/`](examples/spring-ai-alibaba-mcp-server/) ｜ 今日 SEO 稿 → [`docs/blog-java-mcp-spring-ai-alibaba-ecosystem-2026-10-13.md`](docs/blog-java-mcp-spring-ai-alibaba-ecosystem-2026-10-13.md)
- 🔥 **10-10/10-11 新 CVE 集群再证"认证≠授权"结构性缺陷（治理护城河最强新证据）**：CVE-2026-108585/108586（10-10）**MCP Server Authorization Failures**——argocd-mcp `delete_application` 路径穿越（删 K8s 资源）+ @1mcp/agent 0.20–0.39 越权（confused-deputy，只读 agent 调别人 scope 工具）；核心论断原文"**MCP server implementations are treating client authentication as if it were tool-level authorization**"——正是 `mcp-governance` 的 Scope-ACL/HITL 要解决的结构性弱点类。worldofai 统计在录 **729 个 AI CVE（111 关键 / 352 高 / 5 个 CISA KEV）**，本周新增：JeecgBoot 任意登录用户读 MCP 密钥（CVE-2026-108671 HIGH 7.1）、x64dbg-MCP 任意网络客户端跑调试器命令（CRITICAL 9.3）、**mcp-go through 1.2.1 无界 body 读取 OOM（CVSS 8.7 HIGH，9.1k stars 主导 Go MCP 实现，发布两天即被报）**、Splunk MCP Server 反序列化 RCE（CVE-2026-76404 CVSS 9.1 CRITICAL）、Odoo/phpMyFAQ MCP 绕过既有 ACL——反复印证"成熟产品长出 MCP 面，权限模型默认不带过来"。**本框架 `mcp-governance`(RBAC/Scope/ACL/HITL/审计) + `mcp-auth`(Bearer 强制/jti/吊销) 逐条对症，安全事件简报已扩至可进标书。**
- Java+AI 复合岗 3–5 年 **45K–75K**；二线城市 25-35K·16薪；海外 MCP Expert（Java）€600-900/天。
- 🆕 **🔥 CIS MCP Benchmark 55 点审计基准 + Agentics 六类攻击 taxonomy（10-14 治理护城河最强新证据）**：CIS 已设 **55 点 agent tool governance 审计基准**，监管引用 Five Eyes《Agentic AI Security Guidance》+ NIST AI RMF 把 least-privilege 作基线；Agentics《Enterprise MCP Guide 2026》给出最细 **六类攻击 taxonomy**（Tool/Schema Poisoning、Tool Shadowing、Command Injection、Shadow Servers、Context Oversharing）并披露 **68 个 MCP Server CVE/单月**；EU AI Act / 新加坡 MAS / 韩国正把 MCP 工具层治理写进**强制要求** → 治理从「卖点」升级为「合规刚需」，本框架 `mcp-governance`+`mcp-auth`+`mcp-tenant` 即「合规就绪」资产。
- 🆕 **🔥 新无修复 CVE：CVE-2026-104120（MCP Fetch Server SSRF，CVSS 7.3 High）**：官方 `mcp-server-fetch` 的 `fetch_url` 不做 loopback/link-local/metadata/私网过滤、跟随重定向不重校验目标，**公开利用存在、修复 PR #4890 仍为未合并草稿（截至 10-04）**；叠加 Protocol Pivoting 持续击中 Google/JPMorgan/法国 DINUM/印尼 Tangerang 政府——标准库「注册+路由」天然缺治理，本框架 `mcp-governance`(SSRF 防护/输入校验/Scope) + `mcp-auth`(Bearer 强制/jti/吊销) 逐条对症，**安全事件简报已扩至可进标书**。
- 🆕 **🔥 监管把 MCP 治理写进「强制要求」（10-16 治理护城河最强新证据，叙事升级）**：CIS 已设 **55 点 agent tool governance 审计基线**；Agentics《Enterprise MCP Guide 2026》六类攻击 taxonomy + **68 CVE/月**；**Five Eyes / NIST AI RMF / EU AI Act / 新加坡 MAS / 韩国（起草中）** 均开始把"Agent 工具层治理"引用进监管/准强制预期——治理从「卖点」正式升级为「准入项」。AI Governance Institute 金句："**没有在 MCP 层做技术强制，政策就不算控制**"。本框架 `mcp-governance`+`mcp-auth`+`mcp-tenant` 即把政策变成技术控制的"治理增强层"，**新增监管合规映射（五辖区→模块）** → [`docs/mcp-governance-regulatory-compliance-2026.md`](docs/mcp-governance-regulatory-compliance-2026.md)（可直接进 ZC26G280436 / 湖北疾控 150 万标书作"合规就绪"证明）。
- 🆕 **接单定价梯度（10-14 更新）**：自由职业 MCP 开发 **$75–150/hr**（中级别 $40–80/hr，Upwork/Contra 实测）；MCP 基础设施岗 **€800–1,000/天**（最高溢价，BOVO Digital）；Upwork 官方 Services 三档 **$400 / $800 / $1,500**（交付 5/7/10 天）；项目包 **$1K / $3K–5K / $5K–10K**（单连接器/多工具/全管道）；企业全职（MCP 方向）**$170K–280K 总包**（美国，Inventiple）。组合拳：本框架作交付底座 + Upwork 官方 `mcp.upwork.com` MCP 作获客前端（AI 先按结构化 profile 过滤再进短名单）。
- 🆕🆕 **🔥 Upwork 官方「Hire MCP server developers」标准化定价页（2026-10 实测，最强市场信号）**：MCP 开发已从"小众技能"变成"独立收费品类"——**单连接器 $1.5K–5K / 多工具+auth+scoping $5K–15K / 企业级 MCP 网关 $15K–40K / 定制生产平台 $30K–80K / 运维 $2K–7K / 时薪 $30–150**；且 **$5K 以上档位几乎强制要求 auth+scoping+审计** → 正是本框架 `mcp-governance`+`mcp-auth`+`mcp-audit` 的差异化卖点，作交付底座可平滑跨过溢价门槛。
- 🆕 **接单定价实战（10-17 更新）**：youcanbuildthings 三档 **$1K（单连接器）/ $3-5K（多工具 Server）/ $5-10K（全管道）**，真实案例 $3,500/14h = **$250/hr**；dev.to 数据 **97M SDK 下载/月、<5% 已变现**，Upwork MCP 开发 **$130-185/hr**、项目 **$1K-3K**、月维护 **$500-1,500**；Upwork Services 双目录 **$90/$250/$550** 与 **$400/$800/$1,500** 并存；StackNova **$75-150/hr**。CSDN《AI 副业全景图》列 **MCP 生态开发 5,000–8 万/项目**（"2026 最蓝海赛道"）；bovo-digital **MCP 基础设施 €800–1,000/天**。变现三路径：Marketplace（$19-49/server）+ Gumroad + 定制开发（$1K-3K + 月维护 $500-1.5K）。
- 🆕🆕 **🔥 SAS 官方指南亲口说"MCP 不是安全治理框架"（10-16，治理护城河最强权威背书）**：SAS（银行/保险/公共部门最具话语权的大数据分析厂商）发布 MCP 企业指南，核心论断 **"MCP is not a security governance or compliance framework"**，并明确组织仍需自己负责身份/认证授权/数据治理合规/审计取证/敏感动作 HITL 审批，点名 agentic 风险（目标错位/工具指令滥用/级联信任失败）。**等于官方承认"光有 MCP 不够，还要治理层"** → 本框架 `mcp-governance`+`mcp-auth`+`mcp-tenant` 即 SAS 说的"组织要自己补那一层"，可直接引用进标书/SEO。SAS 原话比喻："The protocol gives you the plug; you design the fuse box（协议给插口，保险丝盒自己设计）"。
- 🆕🆕 **🔥 三连高危漏洞再夯"认证≠授权 / 信任边界"护城河（10-16~10-18）**：**CVE-2026-96620**（Claude Agent SDK Python，**CVSS 9.2 CRITICAL**）注入 `--mcp-config` 定义 stdio MCP server → **绕过 SDK 权限控制任意命令执行**；**CVE-2026-88755**（Z.ai ZCode 桌面端）**workspace 级 MCP 自动连接无授权** → 打开文件夹即 RCE + 工具描述投毒窃密（credentials.json 明文外泄）；**CVE-2026-109382**（WordPress WSP MCP 插件，CVSS 6.5）工具注册**不强制 capability 检查** → 订阅级用户读全部草稿（经典认证≠授权）→ 本框架 `mcp-governance`(工具级 RBAC/Scope/HITL/Fail-closed)+`mcp-auth`(显式授权)+`mcp-core`(RateLimit/资源隔离) 逐条对症，"升级官方 SDK≠安全"叙事再获实锤。
- 🆕🆕 **🔥 HaiND CVE 批次再锤"官方 SDK 也缺治理"（10-11，治理护城河最新证据）**：单一研究者经 VulnCheck 单日披露 **68 个 AI Agent/MCP CVE**，其中 agent 相关 22 个、4 个 CVSS≥7.0——**官方 MCP Kotlin SDK 内存耗尽 DoS（CVE-2026-108714，CVSS 7.5，未授权远程 DoS，0.15.0 仍为最新、无修复命名）**、elvix-sdk MCP SSRF（CVE-2026-108542，公开利用）、Agnai 硬编码凭据（CVE-2026-108753，CVSS 9.4）。结论延续："升级官方 SDK≠安全"，**本框架 `mcp-core`（治理增强层）的 RateLimit + 资源隔离 + 输入校验正是对官方 SDK 治理缺口的补偿控制**，安全事件简报已扩至可进标书。

- 🆕🆕 **🔥 最硬批评者倒戈 + 生态事实标准坐实（10-20 治理护城河最强叙事）**：开源 AI 编程 harness **Pi 1.0（10-14）把原生 MCP 装进核心**（Code Mode + 原生 MCP stable），其创始人曾公开嘲讽 MCP——“连最硬的反对者都倒向 MCP”；**Atlassian 重建 MCP Server GA：220+ 工具 / 1500 万次/日**、AMP 框架给 agent 发“非人类身份 + 审计”；MCP 已成 de facto standard——**78% 企业 AI 栈 / 4 亿+ 月 SDK 下载（Anthropic 7-28）/ 15,930+ 公开服务器**，Google Cloud Agent Gateway 原生支持。Adversa AI 10 月 digest 再补护城河：**OWASP MCP Top 10（beta）10 类攻击** + **MCPTox benchmark 45 个 live server / 353 工具投毒成功率 36.5%（最高 72.8%）** + LiteLLM 仍居 CISA KEV + Grafana CVE-2026-19516 会话伪造 SSRF + Obot 网关 deny-list 绕过 + Langflow CVE-2026-105740 MCP Stdio RCE + 82k 配置硬编码密钥 → 本框架 `mcp-governance`(RBAC/Scope/HITL/审计) + `mcp-auth`(Bearer 强制/jti/吊销) + `mcp-core`(限流/隔离) 逐条对症，“升级官方 SDK≠安全”叙事再实锤。

📊 完整每日收益/招标情报与接单定价梯度 → 见 [`docs/earnings-report-2026-10-28.md`](docs/earnings-report-2026-10-28.md) ｜ 上一份 → [`docs/earnings-report-2026-10-26.md`](docs/earnings-report-2026-10-26.md) ｜ **监管合规映射（新）** → [`docs/mcp-governance-regulatory-compliance-2026.md`](docs/mcp-governance-regulatory-compliance-2026.md) ｜ **安全事件简报** → [`docs/mcp-security-incident-brief-2026-10.md`](docs/mcp-security-incident-brief-2026-10.md) ｜ **ZC26G280436 投标响应包** → [`docs/zc26g280436-bid-package-2026-10-17.md`](docs/zc26g280436-bid-package-2026-10-17.md) ｜ **今日 SEO 稿（官方 SDK 5 连 CVE × 治理增强层 × 接单溢价）** → [`docs/blog-java-mcp-official-sdk-config-gap-2026-10-23.md`](docs/blog-java-mcp-official-sdk-config-gap-2026-10-23.md)

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
| **V1.28** | **治理审批落库（mcp-governance）：ApprovalStore JDBC 化——`approval.store=jdbc` 单表 `mcp_approval_requests`（方言无关 H2/MySQL/PG/SQL Server），多实例审批-消费跨副本可见、重启不丢审批记录（审计合规）；一次性令牌防重放从单机承诺升级为集群承诺 + 无数据源自回退内存（可用性优先）；governance 35 测试全绿 + 治理指南新增持久化章节 + 市场雷达 09-19（NTT DATA Java+MCP+OAuth2 近期最匹配/CloudIngest 保险批量合同/Upwork MCP Expert $60-120/h×50 人）** | ✅ 已完成 |
| **V1.29** | **治理审计落库（mcp-governance）：AuditSink JDBC 化——`audit.store=jdbc` 单表 `mcp_governance_audit`（方言无关），重启/滚动发布不丢审计轨迹、多实例全局可查（合规取证）；只写不删防篡改 + fail-soft 不挂业务 + 近实时双写（落库+SLF4J）+ 值级截断保证合法 JSON；管理 REST API 零改动；governance 43 测试全绿（新增 8 个 H2 集成测试）、全仓 21 模块构建通过；治理指南新增取证章节 + 掘金CSDN稿 + 市场雷达 09-20（OneSeven Java+Spring MCP $4-5K/月/沃尔玛中国 AI·MCP 网关 ¥30-55K/箱箱共用 20-30K·13薪）** | ✅ 已完成 |
| **V1.30** | **治理审计可运营化（mcp-governance）：审计多条件检索（search(Query)：tool/caller/decision/tier/from/to 全字段可空，JDBC 动态 WHERE 全参数绑定方言无关）+ CSV 导出（/audit/export：RFC 4180 + UTF-8 BOM + 注入转义，SIEM/Excel/监管报送）+ 保留策略 TTL（/audit/prune：deleteBefore 方言无关物理清理，GDPR 数据最小化，可配 cron）+ 管理 REST API 升级（旧调用兼容）；governance 49 测试全绿（新增 6 个）、全仓 21 模块构建通过；治理指南新增检索/导出/保留章节 + 掘金CSDN稿 + 市场雷达 09-21（中国移动校招 MCP Tool Server 岗/Singtel MCP governance 岗/Descope $88M MCP 安全岗/micro1 $60-120/hr/沃尔玛中国延续至 10-27）** | ✅ 已完成 |
| **V1.32** | **治理审计实时流导出（mcp-governance）：audit.store=http —— HttpGovernanceAuditSink 用 JDK HttpClient（零外部依赖）把治理判定批量异步推送到 SIEM / Kafka REST Proxy / 任意 webhook（Splunk HEC/ES/Loki），攒批（默认50）+ 定时兜底（默认5s）发送，fail-soft 不挂业务 + 本地队列重试（水位保护），recent/search/deleteBefore 本地视图语义不变，sent/failed/pending 诊断计数，close() 最终 flush；governance 66 测试全绿（新增 4 个）、全仓 21 模块构建通过；治理指南第 12 章 + V1.32 发布说明 + 市场雷达 09-28（Spring AI Alibaba+Nacos 企业级 MCP 分布式部署成热点/Upwork 官方 MCP Server 上线改变接单方式/Mastercard·Autodesk Java-MCP 岗/合肥 MCP 工程师 1.1-1.7万×14薪/深圳 AI Agent Java 20-25K/重庆 MCP+Spring AI 1.5-3万）** | ✅ 已完成 |
| **V1.33** | **市场情报 × 央企"MCP 注册"招标对齐（文档/市场发布）：检索到航空工业西安飞行自动控制研究所「AI 网关、MCP 注册、多模态平台」招标 ZC26G280436（直接点名 MCP 注册，10-20 截标）+ 中科院合肥院 1150 万 / 肿瘤医院 984 万政企大单；Bloomberg Enterprise MCP（15 客户端）/ Miro 1600 万次 MCP 调用 / Stacklok 64% 企业把安全治理列头号障碍（利好 mcp-governance）/ 五巨头 MCP 收敛完成 / 全球市场 $142 亿(+38.5%)·中国 ¥210 亿(18.7%)；新增 earnings-report-2026-10-01.md + 掘金CSDN稿 blog-java-mcp-enterprise-tender-2026-10-01.md + README 企业招标/招聘动态区块** | ✅ 已完成 |
| **V1.34** | **Spring AI Alibaba 1.0 GA 对齐 × 投标资产（生产级就绪）：新增 `examples/spring-ai-alibaba-mcp-server/`（用 GA 把 @Tool Java 方法发布为 MCP 工具并挂 mcp-gateway 获治理层，独立 Maven 工程不进主 reactor）；新增 earnings-report-2026-10-03.md（近3天：长庆油田 130万专题2·MCP服务 10-16截标 + ZC26G280436 10-20截标 / freelancermap·Sumo Logic·OneSeven 点名 MCP Java 岗 / Upwork 官方 MCP Server 已成接单基础设施 / Spring AI Alibaba 1.0 GA=Nacos MCP Registry+Higress 网关与本框架同款定位）；新增 tender-proposal-ZC26G280436-2026-10-03.md（投标技术方案模板：需求↔模块映射+等保对照+交付周期）+ 掘金CSDN稿 blog-java-mcp-spring-ai-alibaba-ga-2026-10-03.md + README 招标/招聘区块更新。无破坏性变更，21 模块构建不受影响** | ✅ 已完成 |
| **V1.35** | **等保合规对照表 × 国庆档投标资产（挣钱路径强化）：新增 `docs/mcp-governance-dengbao-compliance.md`（等保2.0 三级 × mcp-governance 能力逐条对照表，访问控制/审计/入侵防范/数据保密/个人信息/集中管控/高风险审批/限流隔离，每条标注模块+测试证据，可直接进央企标书作合规证明）；新增 `docs/tender-asset-changqing-2026-10-04.md`（长庆油田 130万 10-16截标 投标素材包：需求↔模块映射+业绩证明模板+团队画像+checklist）；新增 `docs/earnings-report-2026-10-04.md`（10-01~10-04 实时情报：移动集采13.41亿/云南智算1.37亿/山东超融合696万、外企德科·阶跃星辰 Agent 岗30-60k·16薪点名MCP+Skills、Upwork AI Engineer 在招+AI automation $19/hr 中位、Spring AI Alibaba 11k stars）；新增 `docs/blog-java-mcp-dengbao-tender-2026-10-04.md`（掘金/CSDN SEO 稿：等保角度抢央企投标流量）；README 招标/招聘/等保区块更新。无破坏性变更，21 模块构建不受影响** | ✅ 已完成 |
| **V1.36** | **安全治理红利窗口（挣钱情报 + 治理价值再强化）：新增 `docs/earnings-report-2026-10-08.md`（10-05~10-08 实时情报：企业 AI 团队采用率 **31%→78%**、500强28%生产跑MCP、>10,000 server 生产；仅 8.5% 实现 OAuth 2.1、43% 命令注入、492 零认证裸奔、18% scoping——治理最大鸿沟验证 mcp-governance；新招标 湖北疾控150万11月采购点名MCP+等保三级+多租户 / GSA政府 Hackathon；招聘 上海30-60k·16薪·西安25-35k·16薪招10人 点名 MCP；接单 Upwork $2.1k / Lytvynov $10k/sprint）；新增 `docs/blog-java-mcp-security-governance-2026-10-08.md`（掘金/CSDN SEO 稿：78%已用 vs 8.5%安全 反差叙事）；新增 `docs/mcp-security-hardening-quickstart.md`（MCP 加固 5 步速查，引流接单/投标）；README 招标/招聘/等保区块更新（新增湖北疾控150万+GSA+78%采用率+8.5%OAuth+43%注入+西安岗）。无破坏性变更，21 模块构建不受影响** | ✅ 已完成 |
| **V1.37** | **招聘/招标情报爆发 × Java+Spring 蓝海验证（挣钱路径再强化）：新增 `docs/earnings-report-2026-10-09.md`（10-06~10-09 实时情报：🔥 ZC26G280436 招标文件获取 10-09 17:00 截止、投标 10-20 截，国产平台+MCP组件+2台智算；沃尔玛 30-55k 点名 AI/MCP 网关+MCP Server+Skill Registry 且 Nacos/Higress/Spring AI 开源优先；Darwin·OneSeven·InAddition·Intellias·Cloudflare 五家国际岗密集点名 Java MCP/Registry/Portals；接单 Upwork $120-300/hr、项目包 $90/$180/$350(进阶$1.5K)、行业实测 $1.5K-10K+/项目；市场数据 97M 月下载/10,000+ server/78% 已用但仅 8.5% OAuth/1,800+ 裸奔/63% 缺传输加密）；新增 `docs/blog-java-mcp-hiring-tender-2026-10-09.md`（掘金/CSDN SEO 稿：招聘招标井喷+Java 蓝海）；README 招标/招聘区块更新（ZC 加"今日截止"紧急标记 + 6 条国际 Java MCP JD + Upwork 最新定价）。无破坏性变更，21 模块构建不受影响** | ✅ 已完成 |

| **V1.38** | **安全治理红利窗口 × 政务标"指名 MCP"时代（挣钱情报 + 治理护城河强化）：新增 `docs/earnings-report-2026-10-10.md`（10-07~10-10 实时情报：宁夏农业农村厅 287.2 万「助农增收智能体 MCP 试点」中标——MCP 首次写进政府采购标的名称；长庆 130 万 10-16 截；ZC 10-20 截；移动 17.53 亿集采；🔥 LiteLLM 成为首个 CISA KEV MCP 漏洞 CVE-2026-59822 + Google CVE-2026-14540 CVSS 8.0「Protocol Pivoting」横跨 Google/JPMorgan/政府 + Grafana CVE-2026-19516 + CoSAI v2.0 + 实习僧/沃尔玛/Satrio 点名 Java MCP + Upwork $120-300/hr）；新增 `docs/mcp-security-incident-brief-2026-10.md`（10 起事件逐条映射到 mcp-governance/mcp-auth 控制，可作投标合规证明/安全审计交付物）；新增 `docs/blog-java-mcp-security-tender-2026-10-10.md`（掘金/CSDN SEO 稿：CISA KEV + 宁夏 287 万双角度）；README 招标/招聘/治理区块更新（宁夏 287 万 + 长庆"仅6天" + CISA KEV 护城河 + 实习僧/Satrio）。无破坏性变更，21 模块构建不受影响** | ✅ 已完成 |

| **V1.39** | **政务指名时代 × 治理分层范式 × Java 蓝海坐实（挣钱情报 + 治理卖点强化）：新增 `docs/earnings-report-2026-10-11.md`（10-09~10-11 实时情报：宁夏 287.2 万**正式中标公告**（MCP 首次进政府采购标的名，6家投标运营商包场前二，门槛硬续约稳）；长庆 130 万 10-16 截/ZC 10-20 截/湖北疾控 150 万；🔥 Atlassian 220+ 工具/1500万次日调用治理分层 + Precisely 全系上 MCP（企业 MCP=治理分层范式）；OX Security 扫描 15,465 公开 Server 零护栏/15.6%海外；新 CVE-2026-104850 官方 SDK OAuth 泄漏；FollowAgents 评 Spring AI Alibaba「缺安全控制」→本框架=治理增强层；招聘 阿里云/实习僧/法本/一览/猎聘 点名 Java+MCP；接单定价 $6K-12K/个·$10K/月订阅·中文 5k-8万/项目）；新增 `docs/blog-java-mcp-governance-tender-2026-10-11.md`（掘金/CSDN SEO 稿：宁夏中标 + Atlassian 治理范式 + Java 蓝海 + 报价梯度）；README 招标/招聘/治理区块更新（Atlassian/Precisely/OX + 5 条国内 Java MCP 岗 + CVE-2026-104850/FollowAgents 护城河）。无破坏性变更，21 模块构建不受影响；并补推 V1.36/37/38 积压 commit** | ✅ 已完成 |

| **V1.40** | **金融智能体"治理前置" + 10-10/11 CVE 集群治理护城河（挣钱情报 + 安全卖点再强化）：新增 `docs/earnings-report-2026-10-12.md`（10-09~10-12 实时情报：🔥 金融智能体采购 9 月招标 33 条/中标约 2109 万、MCP协议+多智能体协同+安全治理进采购文件条目、银行占 57.6%；中国电信襄阳 45.43 万 10-16 截；猎聘/天津 10-12 新出 Java+MCP 岗；Upwork 30 天 2897 软件开发岗 AI 第二大类；🔥 CVE-2026-108585/108586 MCP 授权失败 + worldofai 729 AI CVE + mcp-go 8.7 + Splunk 9.1 RCE 全印证"认证≠授权"）；新增 `docs/blog-java-mcp-security-tender-2026-10-12.md`（掘金/CSDN SEO 稿：治理前置招标 + CVE 集群 + Java 蓝海）；README 招标/招聘/治理区块更新（金融智能体治理前置 + 襄阳 45.43 万 + 宁夏算力 + 猎聘/天津 Java MCP + 10-10/11 CVE 集群）。无破坏性变更，21 模块构建不受影响；并补推积压 V1.39 commit** | ✅ 已完成 |
| **V1.41** | **Spring AI Alibaba 生态官方化 × 治理增强层定位再夯实（挣钱情报 + 生态对齐）：新增 `docs/earnings-report-2026-10-13.md`（10-10~10-13 实时情报：🔥 ZC26G280436 10-20 截仅 7 天 + 长庆 130 万 10-16 截 + 襄阳 45.43 万 10-16 截 + 宁夏 287.2 万已中标 + 湖北疾控 150 万；招聘 Cotality/EPAM/InAddition/沃尔玛30-55K/实习僧18-30K/猎聘多岗 清一色 Java+MCP；Upwork 官方 MCP Server 成 agentic hiring 获客前端 + Procore MCP 在招单；🔥 CVE-2026-104850 TS SDK OAuth issuer 绑定(修复有缺口) + Cycode MCP Python SDK OAuth 账户接管 + CVE-2026-59822 仍居 CISA KEV + CoSAI v2.0 + 两恶意 server 过 5 扫描器零告警；Nacos 3.0 MCP 自动注册手册 + Higress CNCF Sandbox v2 官方化）；新增 `docs/blog-java-mcp-spring-ai-alibaba-ecosystem-2026-10-13.md`（掘金/CSDN SEO 稿：Nacos 自动注册之后谁管安全）；README 招标/招聘/治理区块更新（日期 10-13 + 7 条新 Java MCP JD + Nacos 3.0 自动注册生态护城河 + 情报链接）。无破坏性变更，21 模块构建不受影响** | ✅ 已完成 |
| **V1.42** | **安全治理成合规基线 × 本周最硬 Deadline 冲刺（情报 / 文档 / 市场发布）：新增 `docs/earnings-report-2026-10-14.md`（10-11~10-14 实时情报：🔥 长庆 130 万 / 襄阳 45.43 万 10-16 截仅 2 天 + ZC26G280436 10-20 截仅 6 天；🔥 CIS MCP Benchmark 55 点审计基准 + Agentics 六类攻击 taxonomy(68 CVE/月) + EU AI Act/MAS/韩国走向强制；新无修复 CVE-2026-104120 MCP Fetch SSRF CVSS 7.3(公开利用+修复PR未合并)；招聘 Mindera(罗马尼亚·建MCP Server)/Built In 保险MCP单($75-150/hr)/杭州火石创造(15-30k MCP·Spring AI)/苏州智联(MCP多智能体协作)/青岛中恒信(点名MCP协议)；接单定价 $75-150/hr·€800-1000/天·Upwork三档$400/$800/$1500·项目包$1K/$3-5K/$5-10K·全职$170-280K）；新增 `docs/blog-java-mcp-cis-benchmark-governance-2026-10-14.md`（掘金/CSDN SEO 稿：CIS 55 点 + 六类攻击 + Java 接单护城河）；README 招标/招聘/治理区块更新（日期 10-14 + 5 条新 Java MCP JD + CIS/CVE-2026-104120 护城河 + 10-16/10-20 截标提醒）。无破坏性变更，21 模块构建不受影响** | ✅ 已完成 |
| **V1.43** | **官方 Java mcp-core 爆 7.5 DoS CVE × 治理增强层护城河再夯实（情报 / 文档 / 市场发布）：新增 `docs/earnings-report-2026-10-15.md`（10-11~10-15 实时情报：🔥 CVE-2026-108395 官方 Java SDK mcp-core 7.5 DoS(同名构件直接命中本框架治理层)+CVE-2026-109527/59951(Python)+NVD 139 条 MCP CVE(24C/66H)；长庆130万/襄阳45.43万 10-16 截仅 1 天+ZC 10-20 截仅 5 天；5 条新 Java+MCP 招聘(凯盛数智/物产中大校招/华微/轻快创新/辰海数聚)；Upwork 新费率(0–15%)+upwork-mcp-rs 生态；报价梯度锚点）；新增 `docs/blog-java-mcp-security-cve-java-sdk-2026-10-15.md`（掘金/CSDN SEO 稿：官方 Java mcp-core 漏洞×治理增强层×Java 蓝海）；README 招标/招聘/治理区块更新（长庆/襄阳倒计时改仅1天+ZC仅5天+10-13 AI算力集采放量+5条新Java MCP JD+CVE-2026-108395护城河+NVD 139+Upwork新费率）。无破坏性变更，21 模块构建不受影响** | ✅ 已完成 |
| **V1.44** | **监管把 MCP 治理写进「强制要求」× 治理增强层定位固化（情报 / 文档 / 市场发布）：新增 `docs/earnings-report-2026-10-16.md`（10-14~10-16 实时情报：🔥 监管跨辖区把 Agent 工具层治理写进强制预期——CIS 55 点 + Five Eyes/NIST AI RMF/EU AI Act/MAS/韩国 + Agentics 六类攻击 taxonomy(68 CVE/月)；ZC26G280436 10-20 截仅 4 天 + 长庆/襄阳 10-16 已截 + 湖北疾控 150 万 11月；EPAM/MoOngy/trilogy/Publicis 点名 Java+MCP 招聘(EPAM 字面要"security controls, versioning and observability")；Upwork $75-185/hr + 项目 $1K-10K + GlobalGov 订阅制定价；Protocol Pivoting 复发 SSRF(CVE-2026-75130无修复)+CVE-2026-104120 Fetch SSRF；Spring AI Alibaba v1.0 GA + Higress v2.2.5 + TAF "governed" 命名)；新增 `docs/mcp-governance-regulatory-compliance-2026.md`（核心挣钱资产：CIS 55 点/Five Eyes/NIST/EU AI Act/MAS/韩国/六类攻击 逐条映射到 mcp-governance/mcp-auth/mcp-tenant/mcp-gateway/mcp-monitor，可直接进 ZC26G280436/湖北疾控 150 万标书作合规证明）；新增 `docs/blog-java-mcp-governance-regulation-2026-10-16.md`（掘金/CSDN SEO 稿：监管强制 × Java 接单护城河）；README 招标/招聘/治理区块更新（ZC仅4天+长庆/襄阳10-16已截+EPAM/MoOngy/trilogy/Publicis 新 Java MCP JD+监管合规映射护城河）。无破坏性变更，21 模块构建不受影响** | ✅ 已完成 |
| **V1.45** | **挣钱情报密集日 × ZC26G280436 倒计时 3 天（开源影响力放大）：新增 `docs/earnings-report-2026-10-17.md`（10-14~10-17 实时情报：🔥 ZC26G280436 10-20 截标仅 3 天 + 阿里云 api.aliyun.com/mcp 上线把 2W+ API 一键变 MCP 工具 + 美团 CatPaw 25-50k·15薪点名 MCP Server 接入/沙箱权限管控 + Exerizon/TEKsystems/千问 Java+MCP 岗 + Upwork 实战 $250/hr·项目$1K-10K·月维护$500-1.5K + 安全 CVE 集群 Protocol Pivoting/CVE-2026-104120/104850 + Spring AI Alibaba 生态）；新增 `docs/blog-java-mcp-zc-tender-hiring-2026-10-17.md`（掘金/CSDN SEO 稿：央企 MCP 注册中心标倒计时+Java 蓝海+接单定价+治理护城河）；🆕 新增 `docs/zc26g280436-bid-package-2026-10-17.md`（ZC26G280436 投标响应包索引/Checklist：需求↔模块映射+4 份既有资产打包+10-20 前交付建议，降低投标准备成本）；README 招标/招聘/治理区块更新（ZC 倒计时改仅 3 天+阿里云/美团/千问/Exerizon/TEKsystems 新 JD+10-17 接单实战定价+响应包索引链接）。无破坏性变更，21 模块构建不受影响** | ✅ 已完成 |
| **V1.46** | **SAS 权威背书 × 三连高危漏洞夯护城河（挣钱情报 + 开源影响力）：新增 `docs/earnings-report-2026-10-18.md`（10-16~10-18 实时情报：🔥 SAS 官方指南直言"MCP is not a security governance or compliance framework"——监管最严行业厂商替"治理增强层"定位背书 + NVIDIA 双岗($200-391K)字面点名 MCP/MCP,A2A+多租户OAuth/Vault/审批 + 香港大学/歌尔/西南财大/地大 Spring+Java+MCP 岗 + 三连高危 CVE-2026-96620 9.2分/88755/109382 + HERE 把地理能力作 MCP Server 上 AWS Marketplace + 汇付 AIDA 用 MCP 替代 API + Upwork 0%-15%费率/资深$75-150/hr 接单梯度）；新增 `docs/blog-java-mcp-sas-governance-2026-10-18.md`（掘金/CSDN SEO 稿：SAS 说 MCP 不是治理框架 × 治理增强层 × Java 蓝海）；README 招标/招聘/治理区块更新（SAS 背书+三连CVE+NVIDIA双岗+香港大学+新招标+ZC倒计时改仅2天）+ 版本表 V1.46 行。无破坏性变更，21 模块构建不受影响** | ✅ 已完成 |
| **V1.47** | **挣钱情报密集日 × Upwork 官方把"MCP 开发"明码标价 + 银行 AI 网关+MCP 代理标开标 + 官方 Kotlin SDK 再爆 DoS（情报 / 文档 / 开源影响力）：新增 `docs/earnings-report-2026-10-19.md`（10-17~10-19 实时情报：🔥 Upwork 官方「Hire MCP server developers」标准化定价页——单连接器$1.5K-5K/多工具+auth$5K-15K/企业网关$15K-40K/定制平台$30K-80K/运维$2K-7K/时薪$30-150，MCP 开发成独立收费品类；🔥 中原银行企业级AI网关项目中标候选公示(含AI Agent代理+MCP代理)+上海海关百炼107.4万(含AI网关)+浙江财经大学MCP/CLI接口成交(10-19)；🔥 HaiND CVE批次(10-11)22个agent/MCP CVE含官方MCP Kotlin SDK内存耗尽DoS CVE-2026-108714(CVSS 7.5，未授权远程DoS，无修复)+elvix-sdk MCP SSRF+CVE-2026-108753硬编码凭据9.4；招聘阿里云Agentic Tools SDK团队(api.aliyun.com/mcp 2W+ API)+美团(CatPaw/AI Agent智联)+千问+NVIDIA($200-391K，10-20截)+香港大学+Exerizon+EPAM 清一色 Java+MCP；接单定价Upwork官方+自由职业€800-1000/天+CSDN副业5K-8万/项目）；新增 `docs/blog-java-mcp-upwork-official-pricing-2026-10-19.md`（掘金/CSDN SEO 稿：Upwork 官方定价 × 银行 AI 网关标 × 治理增强层 × Java 蓝海）；README 招标/招聘/治理区块更新（Upwork 官方定价页+中原银行/上海海关/浙财+ HaiND CVE + ZC倒计时改明天最后1天）+ 版本表 V1.47 行。无破坏性变更，21 模块构建不受影响** | ✅ 已完成 |
| **V1.48** | **挣钱情报密集日 × ZC26G280436 截标收官 × MCP 接单报价梯度坐实（情报 / 文档 / 开源影响力）：新增 `docs/earnings-report-2026-10-20.md`（10-17~10-20 实时情报：🔥 ZC26G280436 今日 10-20 09:00 截标收官 + 湖北大数据集团智能体工作台 10-13 开标 + 中原银行 AI 网关中标候选延续 + 阿里云/美团/实习僧/猎聘 清一色 Java+MCP 岗 + Upwork 官方定价页 + Lytvynov $10K/4周 + BeevR/Pixel 报价梯度表(受监管多租户 $48K-288K) + Adversa 10月 CVE digest(LiteLLM KEV/Grafana/Obot/Langflow RCE/OWASP Top10/MCPTox 36.5% 投毒) + Pi 1.0 倒戈 MCP + Atlassian 220+ tools + 4亿月下载事实标准）；新增 `docs/blog-java-mcp-pricing-tender-2026-10-20.md`（掘金/CSDN SEO 稿：接单报价梯度 × ZC 收官 × 治理护城河）；新增 `docs/zc26g280436-bid-response-2026-10-20.md`（ZC26G280436 投标响应正文模板：需求↔模块映射+等保对照+监管合规映射+安全简报+SAS 背书+西安本地交付，即填即用）；README 招标/招聘/治理区块更新（ZC 改“已截标”+湖北大数据集团+4 条新 Java MCP JD+Adversa 10月 CVE/OWASP/MCPTox/Pi 倒戈护城河）。无破坏性变更，21 模块构建不受影响** | ✅ 已完成 |
| **V1.49** | **CSA 双闸门模型 × 治理增强层护城河再夯实（情报 / 文档 / 市场发布）：新增 `docs/earnings-report-2026-10-21.md`（10-19~10-21 实时情报：🔥 CSA 双闸门模型 10-20 云安全联盟——能力准入 vs 执行授权两道独立闸门、运行时 5 因素实时评估、直引 Five Eyes、若被 ISO/NIST 采纳即变审计基线(比 CIS 55 点再进一层，命中 mcp-governance/mcp-auth 运行时授权)；🔥 文件系统能力洗钱 arXiv 2610.22301——21,097 server 扫描 16.4% 非文件系统地带文件能力、150 动态确认可利用、13 公告含 Anthropic 自家 Git MCP Server 2 CVE(CVE-2026-27735)；MCP 实体档案 400M 月连接/78% 渗透/143,000 漏洞跨 73% server 含 CVSS 10.0 RufRoot+LiteLLM CISA KEV；CN-SEC 自动批准投毒成功率 84.2%；Nutanix/DE-CIX 开源 MCP server(治理内置仅限自家/无审批反例)；企业 AI 网关选型 9 厂商(Bifrost 开源竞争 mcp-gateway)；🔥 上交所技术公司(最高规格金融机构点名 MCP 协议及 Skill+RAG+多智能体+信创)/申万宏源(11-20k·MCP 开发)/TOPdesk(荷兰·5年 Java+MCP)/华微软件(广州·MCP) 清一色 Java+MCP；Upwork 官方 mcp.upwork.com(OAuth2.1 免费·write 草稿待确认)+9 个 SaaS 建 MCP Server 商业模式($300-1,000/交付)+2026 费率软件 dev $80-150/hr(AI 高 44%)）；新增 `docs/blog-java-mcp-csa-two-gate-governance-2026-10-21.md`（掘金/CSDN SEO 稿：CSA 双闸门 × Java 接单护城河）；新增 `docs/V1.49-release-notes.md`；README 招标/招聘/治理区块更新（CSA 双闸门/Nutanix/DE-CIX/上交所/申万宏源/TOPdesk/华微 + 版本表 V1.49 行）。无破坏性变更，21 模块构建不受影响** | ✅ 已完成 |
| **V1.50** | **CVE-2026-111770（官方 TS SDK 消息隔离漏洞，CVSS 7.7）× 治理增强层护城河再夯 × 开源影响力放大（情报 / 文档 / 市场发布）：新增 `docs/earnings-report-2026-10-22.md`（10-19~10-22 实时情报：🔥 CVE-2026-111770 官方 TS SDK server 消息隔离(共享单例串客户数据，命中 mcp-tenant+mcp-governance 多租户/会话隔离，同周 CVE-2026-104850 TS OAuth + CVE-2026-108395 Java DoS "升级官方SDK≠安全"三连击)；招聘 Capgemini Polska/Cayuse/Inabia/数字政通(Java+MCP，欧洲大厂把治理写进JD)；招标 ZC26G280436 已截标待公示 + 天津科大342万 + 云南地环180万 + 上海海关107.4万中标 + 金融电子化智算中标候选；Upwork 官方定价页+youcanbuildthings三档实战($250/hr实测)；市场数据 97M月下载/10,000+server/Fortune500 28%/78%企业栈/$10B市场）；新增 `docs/blog-java-mcp-mcp-server-cve-111770-tender-2026-10-22.md`（掘金/CSDN SEO 稿：官方SDK消息隔离漏洞×治理增强层×央企/海关/高校智算标×Java蓝海）；新增 `docs/V1.50-release-notes.md`；README 招标/招聘/治理区块更新（CVE-2026-111770+Capgemini/Cayuse/Inabia/数字政通+天津科大/云南地环/金融电子化智算+上海海关中标+版本表V1.50行）。无破坏性变更，21 模块构建不受影响** | ✅ 已完成 |
| **V1.51** | **官方 TS SDK 5 连 CVE × "升级后还要改配置才生效" 护城河升级 + 运营商/银行新标（情报 / 文档 / 市场发布）：新增 `docs/earnings-report-2026-10-23.md`（10-21~10-23 实时情报：🔥 官方 TS SDK 一次性 5 CVE（CVE-2026-111767~111771），其中 111768 必须设 `maxToolInputElements`、111771 必须设 `expectedAudience`——**升级版本后还要改配置才生效**，"升级官方 SDK ≠ 安全"升级为配置缺口实锤，叠加 CVE-2026-104850 TS OAuth + CVE-2026-108395 Java DoS 成三连击；🔥 中国电信四川直接采购点名 MCP 注册+安全沙箱+监管态势（运营商级新标）；🔥 上海农商银行智能体平台 POC 点名 MCP 插件市场+服务网关动态路由/鉴权/限流/统计（金融新主线）；招聘 EPAM Lead Java AI Native(MCP server ecosystems+security controls+observability)/建信金融科技(成都·Spring AI Alibaba+MCP)/Inabia(纽约金融 $50-55/hr)/Market Cloud(伦敦)/Publicis Sapient($165-205K) 清一色 Java+MCP；Upwork 官方定价页+youcanbuildthings 接单梯度 + Spring AI Alibaba 2.0 MCP+Nacos 注册/网关/集群发现坐实治理增强层）；新增 `docs/blog-java-mcp-official-sdk-config-gap-2026-10-23.md`（掘金/CSDN SEO 稿：官方 SDK 5 连 CVE × 升级后还要改配置 × Java 治理增强层 × 接单溢价）；新增 `docs/V1.51-release-notes.md`；README 招标/招聘/治理区块更新（5 CVE 集群 + 中国电信四川 + 上海农商银行 + EPAM/建信/Inabia/Market Cloud + 版本表 V1.51 行）。无破坏性变更，21 模块构建不受影响** | ✅ 已完成 |
| **V1.52** | **摩根大通点名"维护 MCP server+治理" + 人保资产单列采购"MCP 账号" + ToFu 研究曝 253 个 MCP 工具漏洞（挣钱情报 + 治理护城河再夯）：新增 `docs/earnings-report-2026-10-24.md`（10-21~10-24 实时情报：🔥 JPMC 顶级岗字面"setting up and maintaining MCP servers + secure coding/observability"(Spring AI/Spring ecosystem+金融服务) 直接命中 mcp-governance+mcp-monitor；🔥🔥 人保资产把"MCP 账号"单列采购(10-22公告，10-30截) 金融央企第一次把 MCP 接入做成采购清单，对齐 mcp-registry+mcp-governance；🔥🔥 ToFu "Repeat-After-Me/253 漏洞" 六大框架模型拒绝仍执行、118 server/253 漏洞 最强"模型输出≠授权"证据，mcp-governance HITL+参数校验+JDBC 审计对症；招聘 Accso/牛客网/trilogy/数字政通 Java+MCP；Upwork $5K-15K/企业网关$15K-40K 强制治理；AAIF 150+/MCPA 认证/30%厂商将发自有 server；Spring AI Alibaba 2.0 MCP Gateway 零代码改造存量=治理增强层再坐实）；新增 `docs/blog-java-mcp-jpmc-picc-tender-tofu-2026-10-24.md`（掘金/CSDN SEO 稿：JPMC+人保资产点名 MCP × ToFu 253 漏洞护城河 × Java 治理增强层 × 接单溢价）；🆕 新增 `docs/mcp-tofu-253-vulnerabilities-governance-2026-10-24.md`（核心挣钱资产：ToFu 253 漏洞逐条映射到 mcp-governance/mcp-auth/mcp-tenant/mcp-core/mcp-monitor 控制，可作投标合规证明/安全审计交付物）；README 招标/招聘/治理区块更新（人保资产 MCP 采购 + JPMC/Accso/牛客网/trilogy 新 JD + ToFu 253 漏洞护城河 + AAIF/MCPA 市场数据）。无破坏性变更，21 模块构建不受影响** | ✅ 已完成 |
| **V1.53** | **MCP 安全最差月 × RelayShield 治理产品化 × 金融 MCP 治理投标矩阵（情报 / 文档 / 市场发布）：新增 `docs/earnings-report-2026-10-25.md`（10-22~10-25 实时情报：🔥 10 月 = MCP 安全史上最差月份——Langflow RCE CVE-2026-105697 CVSS 9.9(10-05)/x64dbg 无认证调试器 CVE-2026-107824 CVSS 9.3(10-09)/官方 fetch SSRF CVE-2026-104120 7.3/Python SDK 凭证盗窃(Cycode 7.5)；MCPTox benchmark 45 server 投毒成功率最高 72.8% 且模型越强越脆弱；Koi Security 首个真实恶意 MCP server(postmark-mcp)；🔥 RelayShield MCP Proxy Firewall 以 $49/月把"运行时筛查工具调用与响应"产品化→治理层商业化被第三方验证；🔥🔥 人保资产"MCP 账号"采购报名 10-22~10-26、响应截止 10-30 15:00(上海浦东，陈琦 021-38571848)，金融央企第一次把 MCP 接入做成采购清单；招聘 JPMC/Accso/Capgemini/trilogy/数字政通/牛客网 清一色 Java+MCP；Upwork $5K-15K/企业网关$15K-40K 强制治理；CSDN 流烟默官方文直写"企业级 MCP 治理(鉴权/限流/审计)"再坐实治理增强层）；新增 `docs/blog-java-mcp-attack-surface-relayshield-2026-10-25.md`（掘金/CSDN SEO 稿：MCP 安全最差月 × RelayShield 治理产品化 × Java 治理增强层 × 6 条卖点话术）；新增 `docs/V1.53-release-notes.md`；README 招标/招聘/治理区块更新（RelayShield 治理产品化 + 人保资产采购细节 + CSDN 官方治理文 + 安全最差月护城河）。无破坏性变更，21 模块构建不受影响** | ✅ 已完成 |
| **V1.54** | **金融 MCP 治理成为采购条目 × CVE 回归盲区 × Nacos A2A 信任层（情报 / 文档 / 市场发布）：新增 `docs/earnings-report-2026-10-26.md`（10-23~10-26 实时情报：🔥🔥 **BOFA（美国银行）Senior Agentic AI Engineer JD 字面"design MCP servers + authentication/authorization/auditability/HITL/guardrails"**（顶级银行把治理四件套写成 JD 条目，直击 mcp-governance+mcp-auth+mcp-audit）；🔥🔥 **人保资产 MCP 采购报名今日 10-26 截止、响应 10-30 截仅 4 天**（金融央企第一次把 MCP 账号单列采购）；EPAM Turkey/Cayuse/NetApp 延续 Java+MCP + Job Tracker MCP Server(LobeHub 社区·Java17+Spring Boot+Spring AI 范式验证)；🔥 CVE-2026-111770 是 2 月 CVE-2026-25536 的 **8.5 个月回归** + CVE 修复→NVD 命名 **8–18 天盲区** → mcp-monitor+审计落库价值强化；Nacos 3.1.0 引入 A2A 注册中心+"信任层才是硬仗"再坐实治理增强层；Upwork 官方 MCP Server 46 工具 HITL 默认审批+政府国防 $100-250/hr 溢价区）；新增 `docs/blog-java-mcp-finance-governance-tender-2026-10-26.md`（掘金/CSDN SEO 稿：顶级银行把 MCP 治理写进 JD × 人保资产报名截止 × CVE 回归盲区 × Java 治理增强层）；新增 `docs/V1.54-release-notes.md`；README 招标/招聘/治理区块更新（人保资产紧急窗口 + BOFA/EPAM Turkey/Cayuse/NetApp/Job Tracker + CVE 回归盲区 + Nacos A2A 信任层）。无破坏性变更，21 模块构建不受影响** | ✅ 已完成 |
| **V1.55** | **金融 MCP 生产级落地坐实 + 身份层治理产品化 + 接单真实报价 + Java/Spring 范式迁移（情报 / 文档 / 市场发布）：新增 `docs/earnings-report-2026-10-28.md`（10-25~10-28 实时情报：🔥🔥 **天风证券 MCP Server 调用量破 1800 万、登顶阿里百炼平台 MCP 全品类第一**（头部券商生产级 MCP 落地，金融 MCP 治理需求确定性再升一档）；🔥🔥 **Okta for AI Agents（10-22）CIMD GA + MCP Server Catalog governance** 把治理做成身份层产品（验证 mcp-auth+mcp-registry 治理位）；🔥🔥 **NHI Mgmt Group / Jozu（10-27）"registry+runtime 不够"四问 + 私有 registry + 签名准入**（验证 mcp-registry+mcp-governance）；dev.to vet-a-server（registry 只证 provenance 不证 safety、tool poisoning/credential over-reach）；阿里云百炼周报新增 11 款 MCP 服务 + Tiger OpenAPI Remote MCP；猎聘/阿里云/苏州 Robo 多岗点名 Java+MCP+Skill；**Reflekt Lab / Claude Code Club / HighCircl / PRIMO.ai 真实接单报价梯度**（scoping $2.3K-4.7K / 首 workflow $17.5K+ / 市场 $3K-8K 简单 / $35K-90K 多系统写 / 六位数受监管）；Java+Spring+AI 6 条卖点话术 + 模块↔需求映射）；新增 `docs/blog-java-mcp-tianfeng-okta-nhi-governance-2026-10-28.md`（掘金/CSDN SEO 稿：天风 1800 万 + Okta/Jozu 治理产品化 × Java/Spring 范式迁移 × 接单真实报价 × 治理增强层）；新增 `docs/V1.55-release-notes.md`；README 招标/招聘/治理区块更新（天风证券/Okta/NHI-Jozu/dev.to + 猎聘/阿里云/苏州 Robo + Reflekt Lab 真实报价 + 日期改 10-28）。无破坏性变更，21 模块构建不受影响** | ✅ 已完成 |
| **V1.56** | **ZC26G280436 中标拆解（最高价「MCP 注册中心」央企标 683 万）+ 10-27 命令注入 CVE 波 + 治理三方背书（情报 / 文档 / 市场发布）：新增 `docs/earnings-report-2026-10-29.md`（10-26~10-29 实时情报：🔥🔥 ZC26G280436 **10-27 已开标：西安志诚电子 中标 683 万元**（公开最高价 MCP 注册中心央企标，治理独占 ~20%≈137 万=治理增强层定价权）；🔥 10-27 官方批量 CVE 9+ MCP 工具服务命令注入 CVE-2026-117558~117573（agent 参数拼 shell，prompt-injection→OS 命令执行）→ 命中 mcp-governance 参数校验/沙箱/审计；NHI×Willow/CSA×Akto/TrueFoundry 三方 10-27 同日背书治理=身份/控制平面问题 → 命中 mcp-registry+governance+audit；Moss(€10亿 fintech) / 猎聘 90-120k·16薪 / 字节火山 $128K-256K / 广东广电 点名 Java+MCP 招聘；Upwork 冷提案负期望、invites/repeat 才有效 + Dawloom 乙方自检 7 问；Open Contracting/Brazil Pncp 政府采购 MCP server 上线官方 registry）；新增 `docs/zc26g280436-win-analysis-2026-10-29.md`（核心挣钱资产：683 万拆解+逐模块对标+同类标报价锚定 600-800 万+乙方自检 7 问话术）；新增 `docs/blog-java-mcp-zc-win-security-wave-2026-10-29.md`（掘金/CSDN SEO 稿：683 万中标 × 10-27 命令注入 CVE 波 × Java/Spring 治理增强层双杀）；新增 `docs/V1.56-release-notes.md`；README 招标/招聘/治理区块更新（ZC 改「已开标 683 万」+中标拆解链接 + Moss/猎聘双岗/字节/广电 4 条 Java MCP JD + 10-27 命令注入 CVE 波 + NHI/CSA/TrueFoundry 三方背书）。无破坏性变更，21 模块构建不受影响** | ✅ 已完成 |
| **V1.57** | **官方 EMA 授权 stable + 10-27 命令注入 CVE 波 + Python SDK 双 CVE → 治理增强层护城河再坐实（情报 / 文档 / 市场发布）：新增 `docs/earnings-report-2026-10-30.md`（10-28~10-30 实时情报：🔥🔥 **MCP EMA 扩展正式 stable**（Anthropic/Microsoft/Okta + Asana/Atlassian/Canva/Figma/Linear/Supabase），但只做连接级授权、逐工具权限/审计/限流/HITL/多租户空白 → 本框架"官方方案的治理增强层"定位最干净话术；🔥 10-27 **命令注入波** 9 server（CVE-2026-117558~117573）8 无修复、CVSS 7.8/8.5、allowlist 全被 `$()` 击穿 → 命中 mcp-governance 参数校验/沙箱/审计；🔥 官方 **Python SDK 双 CVE**（CVE-2026-116686 SSRF 6.5 + CVE-2026-116684 audience 6.8）再证"升级官方 SDK ≠ 安全"；Gruve.ai 发文生产级 MCP 授权=逐工具权限+最小特权+OAuth+审计；**长庆油田 MCP 服务已中标候选公示**（北京互时科技）+ **中国计量院「计量+AI」算力 924.62 万中标**（10-29）+ 人保资产 10-30 今日截标 → 金融 MCP 治理投标矩阵成形；**大厂面试题转向"写过 MCP 服务吗"** + dev.to Java Spring AI 高赞范例 → Java+MCP 招聘成必考；PRIMO.ai：MCP+A2A 成顶级架构范式、Upwork 0–15%/Toptal $60–200+/hr/Fiverr 20%/Malt 5% 费率复核；6 条 Java+AI 卖点话术）；新增 `docs/blog-java-mcp-ema-stable-command-injection-2026-10-30.md`（掘金/CSDN SEO 稿：官方授权 stable 但治理空白 × 命令注入波 × Java 治理增强层）；新增 `docs/V1.57-release-notes.md`；README 招标/招聘/治理区块更新（EMA stable + 命令注入波细节 + Python SDK 双 CVE + 长庆中标候选 + 中国计量院 924 万 + 大厂 MCP 面试题 + dev.to 范例 + Upwork/Toptal 费率 + 版本表 V1.57 行）。无破坏性变更，21 模块构建不受影响** | ✅ 已完成 |
| **V1.58** | **ZC26G280436 中标结果正式落地（683 万铁证）+ 移动 116.66 亿算力巨单 + 花旗/Digital Dhara/EPAM 金融 MCP 实弹岗 + SNS Insider 权威市场报告（$1.64B / CAGR 37.22%）+ Upwork 真实在售分层定价（情报 / 文档 / 市场发布）：新增 `docs/earnings-report-2026-10-31.md`（10-29~10-31 实时情报：🔥 ZC26G280436 10-27 中标结果公告正式落地——西安志诚电子 683 万中标、公示期无异议=公开最高价 MCP 注册中心央企标铁证、治理独占 ~20%≈137 万；🔥 中国移动 2026-2027 AI 超节点集采 神州鲲泰第五候选 116.66 亿（不含税）/2048 卡(10-30 公告)；🔥 浙江公安科技研究所 大模型跨域安全硬件采购意向(预计 11 月)含"API 网关一体+智能体跨网会话+阻断非规范智能体协议流量"命 mcp-governance+mcp-gateway；🔥 SNS Insider 权威报告 2026 MCP 市场 $1.64B/2035 $28.36B/CAGR 37.22%(微软/Anthropic 把治理写进官方路线图)；🔥 Citi(CAD 120.8K-170.8K·JD 原话 build MCP servers)/Digital Dhara(Charlotte·auth/authz/auditability/HITL/guardrails)/EPAM(AI Native·MCP server ecosystems+security controls+observability) 金融 MCP 实弹岗逐条命 mcp-governance+mcp-auth+mcp-audit+mcp-monitor；Upwork 真实在售 $149/$349/$799、$400/$800/$1,500、$300/$800/$2,000+UpHunt 指南 $120-300/hr=MCP 实现 $150-300/hr 增长最快品类；Spring AI Alibaba MCP Gateway 长文零代码改造存量 HTTP/Dubbo→MCP 官方只给注册+路由+协议转换 治理空白=治理增强层再坐实）；新增 `docs/blog-java-mcp-zc-award-china-mobile-citi-2026-10-31.md`（掘金/CSDN SEO 稿：683 万落地 × 116 亿算力 × 花旗岗 × Java 治理增强层）；新增 `docs/V1.58-release-notes.md`；README 招标/招聘/治理区块更新（ZC 落地/移动 116 亿/浙江公安/SNS 报告/Citi/Digital Dhara/EPAM/Upwork 真实定价 + 日期改 10-31）。无破坏性变更，21 模块构建不受影响** | ✅ 已完成 |
| **V1.59** | **治理层=最大花钱子段（KS 报告 $575M/40.1%/治理37% + Gravity 53,545 server + Spring AI 2.0 官方 SDK 卡位 + Spring AI Alibaba 停更去耦合 + 公安视频算力标密集 + 5 条 Java MCP JD + Upwork 真实单）** | ✅ 已完成 |
| **V1.60** | **企业级 MCP 网关进入「选型时代」+ 中原银行 AI 网关+MCP 代理活标 + Apple/EPAM/Genesis10/Infotel 点名 Java Spring AI + MCP + Upwork 11 月实时定价 + 安全护城河延续（情报 / 文档 / 市场发布）：新增 `docs/earnings-report-2026-11-02.md`（10-30~11-02 实时情报：🔥 网关选型时代 CData 5 准则/Tray 11 网关对比/Metorial/SumatoSoft/Mintive——买方用治理维度挑网关、每项是 mcp-governance 同义词、开源网关非 Java 栈；🔥 中原银行企业级 AI 网关 DC-2026-05-29-249 活标含 AI 网关+MCP 代理 14 个月；🔥 Apple JD 原话 Java Spring Boot+Spring AI+智能体安全护栏+MCP；EPAM 双 AI Native Java 岗+Genesis10 全球金融机构 12+月合同+Infotel 法国保险 MCP Server；Upwork $149/$349/$799·$400/$800/$1,500·$300/$800/$2,000 + UpHunt $150-300/hr 增长最快品类；eCorpIT 11 CVE 加固/OX 7,000+ 公开 server+NeuralTrust registry-only 可绕过+CVE-2026-103572 trailing-dot+NHI 43% 命令注入+NIST CVE-2026-11624 DNS rebinding；湖北疾控 MCP 插件接入国家平台+多租户+等保三级/厦大 11-11 截/军队大模型安全防护 190 万 11-16 截）；新增 `docs/blog-java-mcp-gateway-buying-guide-apple-tender-2026-11-02.md`（掘金/CSDN SEO 稿：网关选型时代 × 中原银行活标 × Apple Java Spring AI × 治理开源答案）；README 招标/招聘/治理区块更新（日期改 11-02 + 网关选型时代 + 中原银行活标 + 湖北疾控/厦大/军队标 + Apple/EPAM/Genesis10/Infotel JD + Upwork 11 月定价 + 安全护城河 4 条）。无破坏性变更，21 模块构建不受影响** | ✅ 已完成 |
| **V1.61** | **招投标 MCP 品类爆发 + 企业遗留 Java 现代化 + 治理护城河三连击（情报 / 文档 / 市场发布）：新增 `docs/earnings-report-2026-11-03.md`（10-31~11-03 实时情报：🔥 剑鱼标讯 MCP 上线（招投标数据接入 Coze/WorkBuddy/Trae，7 大类 19 工具）+ 中国招投标信息查询 MCP（mcpworld 托管）+ Fiverr/Freelancer/Contra/SynapticRelay 自由市场全面 MCP 化 → MCP 从"开发协议"进化成"企业数据/业务接入标准"、垂直 MCP 成品类；🔥 实习僧 18-30k 北京（截止 11-05）+ Mastercard（Java21+Spring Boot+MCP）+ Digital Dhara（design MCP servers for J2EE + 治理四件套）+ Infosys（MCP mandatory）+ CrossChannel 五条新 Java+Spring+MCP 岗 → 招聘井喷下沉到应届、遗留 J2EE 现代化成赛道；🔥 官方 TS/Py SDK 三连击 CVE-2026-104850（OAuth 凭证泄漏 7.5）/116684（authz 6.8）/116686（SSRF 6.5）+ NHI Mgmt Group 治理规范 → 治理增强层护城河再夯）；新增 `docs/blog-java-mcp-tender-mcp-category-java-hiring-2026-11-03.md`（掘金/CSDN SEO 稿：招投标 MCP 上线 × 企业遗留 Java 现代化 × Java Spring 招聘井喷 × 治理护城河）；新增 `docs/V1.61-release-notes.md`；README 招标/招聘/治理区块更新（日期改 11-03 + 招投标 MCP 品类爆发 + 自由市场 MCP 化 + Apify + 三连击 CVE + NHI 规范 + 实习僧/Mastercard/Digital Dhara/Infosys/CrossChannel 五条 JD）。无破坏性变更，21 模块构建不受影响；V1.52~V1.61 共 10 笔 commit 待 PAT 刷新后补推（GCM `gho_` token 可恢复）** | ✅ 已完成 |
| **V1.62** | **证券业招标首次把「MCP Server 工程能力」写成资质门槛 + 影子AI量化护城河 + 网关自身出洞（情报 / 文档 / 市场发布）：新增 `docs/earnings-report-2026-11-04.md`（11-01~11-04 实时情报：🔥 广发证券《2026 企业微信持续建设项目》征集公告原文要求"MCP Server 设计与开发适配能力（工具定义/参数校验/鉴权限权/调用限流/日志可观测）"+多租户隔离+防篡改留痕 → 证券业首次把 MCP 五件套写成供应商资质门槛、预招标 30~60 天卡位窗口；🔥 TrendAI 影子AI报告——19,000 开源 MCP 仓库 20% 带可利用漏洞/1,467 个无鉴权暴露/2025 年 95 个 MCP CVE/预估 2026 年 AI CVE 2,800~3,600/RTT 攻击范式 → 治理护城河最强量化背书；🔥 CVE-2026-120323 MCPHub 网关把调用者凭据透传给攻击者 server + CVE-2026-120338 沙箱逃逸 10.0 + CVE-2026-120315 路径穿越 8.8 → "网关自身出洞、用了网关≠安全"；太平金科研发工作台把 MCP/Skills 写进保险业采购条目（信创+私有化）+ 京北方半年报"MCP 服务"进上市公司财报；Java+Spring+MCP 招聘国内 4 条（小米 25-30k/Robotaxi 30-60k·16薪/全栈 15-30k/北芯）+ 海外 3 条（纽约 Bench Zero Java+Spring AI 合同 12-18 月/GSK 伦敦 FDE/Elucida build MCP servers）+「写过 MCP 服务吗」进 Java 面试题）；新增 `docs/blog-java-mcp-gf-securities-tender-trendai-2026-11-04.md`（掘金/CSDN SEO 稿：广发招标五件套拆解 × 影子AI × MCPHub CVE × Java 工程师行动清单）；新增 `docs/V1.62-release-notes.md`；README 招标/招聘/治理区块更新（日期改 11-04 + 广发征集/TrendAI/CVE 三连/太平金科/京北方 + 7 条新 JD）。无破坏性变更，21 模块构建不受影响；**V1.52~V1.61 十笔积压 commit 已补推成功**（push 阻塞根因=remote URL 未带 username 致 GCM 交互挂起，已固化 remote 修复）** | ✅ 已完成 |
| **V1.63** | **Uber 800+ 网关生产实证 × 官方 Java SDK 自身中招的全 SDK 级 DoS × 受治理 MCP 同日双上线 × Upwork Now 20 倍通道（情报 / 文档 / 市场发布）：新增 `docs/earnings-report-2026-11-05.md`（11-03~11-05 实时情报：🔥🔥🔥 **Uber 内部 MCP Gateway 生产实证**——800+ servers / 5,000+ tools、默认全部 disabled 待审核启用、按 caller(human/service account/autonomous agent) 差异化授权、授权与 PII 脱敏在请求路径执行、Omni MCP 四元工具增量发现 + Response Projection，结论"Gateway 既是协议路由器也是产品管理平台" → 治理增强层首次拿到超大厂生产级架构对标；🔥🔥🔥 **CVE-2026-108395 命中官方 Java SDK `mcp-core`**——全 SDK 无界请求体 DoS、各家统一修 4 MiB 上限、TS SDK 月下载 2.57 亿/Py 2.35 亿、Red Hat Important 评估 44 产品发 VEX，**两条 SDK CVE 未进 GitHub Advisory Database → pip-audit/osv-scanner/Dependabot 全部漏报**、官方 best-practices 至今无 body 大小指引 → 三层话术「实现层洞 vs 规范空白 vs 治理层」；🔥🔥 **11-05 同日 ISI Markets（Governed access，按订阅/entitlements 授权）+ Agiloft（CLM，写操作强制人工确认）两款受治理 MCP 上线**；🔥🔥 **Upwork Now 公测**——MCP server 上线后周活 ×20、Q3 AI 技能人才 GSV 同比 +55% 至 $112M（总 GSV −10%）→ MCP 是真交易入口；🔥 11 月企业网关横评 Bifrost/Docker/Microsoft/AWS/Kong 清一色非 Java 栈且治理锁在 Enterprise 付费墙；🔥 国内 Java+MCP 明确报价 建发 50–80k(厦门)/上海整车厂 Harness 40–70k·15薪 + 金山办公「现有平台向 MCP Server 标准化封装」/美团「MCP Server 接入管理与工具沙箱权限管控」→ 存量 Java 资产 MCP 化成主线；重庆 AI 大单 3.4412 亿(含智能体应用场景)/中央民大 3712 万(11-25 截)/上外 589.28 万；**新增可直接进标书的 12 项治理合规对照表**）；新增 `docs/blog-java-mcp-uber-gateway-dos-governance-2026-11-05.md`（掘金/CSDN SEO 稿：Uber 架构拆解 × 4MiB CVE × Java/Spring 落地清单 × Upwork/50-80k 钱景）；新增 `docs/V1.63-release-notes.md`；README 招标/招聘/治理区块更新（日期改 11-05 + Uber 生产实证 + 全 SDK DoS + ISI/Agiloft + 网关横评 + 国内预算 + 5 条 Java MCP JD + Upwork Now 官方数字 + 乙方 $5k/$15k 报价锚）+ 版本表 V1.63 行。无破坏性变更，21 模块构建不受影响** | ✅ 已完成 |
| **V1.64** | **Wiz 90 天蜜罐把 AI 网关/MCP 坐实为产业化云入口 × 券商采购双连发 × 「把责任放在能治理的那一层」（情报 / 文档 / 工程示例 / 市场发布）：新增 `docs/earnings-report-2026-11-06.md`（11-04~11-06 实时情报：🔥🔥🔥 Wiz.io 90 天蜜罐 LiteLLM MCP Gateway 一个字符 bearer token（CVE-2026-59822）+ 伪造配置 command 启动矿机且握手正常（CVE-2026-42271）+ host-header 绕过（CVE-2026-48710）链式未认证 RCE 归因 Qilin 勒索 + 从运行进程内存抠 proxy master key + XMRig 藏 .claude/unicorn → 6 条攻击路径逐项映射治理模块；🔥🔥 11-03~11-05 NVD 新 CVE 波 CVE-2026-123272/123271 OAuth resource confusion 8.0 / 111769 TS SDK 跨源重定向泄漏 refresh_token / 121419 SurrealDB 会话指纹按方法名→劫持 8.6 / 121458 Instatic 跳过对象级授权 + IBM ContextForge 网关 7 CVE 含 9.8 默认凭据绕过 → 「MCP 是审核更少的接口」；🔥🔥 广发证券 3 天内第二个 MCP 征集《智能金融资讯数据服务平台》11-13 17:30 截止（MCP 工具协议+Skill+子Agent+混合检索，券商级、能力可外部产品化）+ 山东重工集团级平台二次标 11-20 截止（统一安全管控）+ 重庆 3.4412 亿 11-03 定标；🔥 周大福（中国香港）MCP 共享工具层 JD 逐字 identity propagation/access controls/audit trails + 美团 CatPaw + 猎聘 35-55k·16薪 + 赛舵 20-40k + Disney/Mastercard/PE；💵 Upwork 官方 ×20 周活/+55% GSV/费率 0-15% 口径修正/Work Now 细则/报价公式；🌐 Agentic AI Standards 2026「治理是赢了之后的问题」+ Spring AI 2.0 三条路径金句「把责任放在我能治理的那一层」+ 腾讯云 MCP 广场放量只给连接不给治理；7 条 Java+Spring+AI 卖点）；新增 `docs/blog-java-mcp-wiz-honeypot-gf-securities-governance-2026-11-06.md`（掘金/CSDN SEO 稿：一个字符 bearer × 券商双征集 × Java/Spring 七条控制点 + 过滤器骨架）；🆕 新增 `examples/governance-hardening/`（**参考实现**：McpGatewayGuardFilter + BoundedRequestWrapper + application-hardening.yml + README，覆盖 4MiB 上限/fail-closed/同源重定向/会话按用户/工具默认禁用/按 caller 授权/HITL/审计，不进 Maven reactor 零构建风险）；🆕 `docs/mcp-security-hardening-quickstart.md` 增补第 6 步传输层与注册期硬约束 + `docs/enterprise-rfp-checklist.md` 增补「12 类攻击面 → 治理模块」售前一页纸（含逐条验收方式，可直接进标书）；新增 `docs/V1.64-release-notes.md`；README 招标/招聘/治理区块更新（日期改 11-06 + Wiz 蜜罐 + 广发双征集 + 山东重工 + 重庆定标 + 私有化必选项 + 周大福/美团/猎聘/赛舵/Disney/Mastercard + Upwork 口径修正）+ 版本表 V1.64 行。无破坏性变更，21 模块构建不受影响** | ✅ 已完成 |
| **V1.65** | **官方 SDK 连续两天漏「受众」 × Gartner 首个 AI Agent 管理象限 × 广发 11-13 报名窗口（情报 / **可投标素材** / 工程示例 / 市场发布）：新增 `docs/earnings-report-2026-11-07.md`（11-05~11-07 实时情报：🔥🔥🔥 11-06 三 CVE — `CVE-2026-111771` 官方 TS SDK 不校验 aud/AuthInfo.resource 且**升级后 expectedResource 默认关闭** / `CVE-2026-102610` fast-agent 默认 0.0.0.0+auth=None+quickstart `--shell` 交出 root shell / `CVE-2026-123786` Agenta SSRF 带出 vault token（同库 webhook 已有私网校验、workflow 没有）；🔥🔥 Gartner 首个 AI Agent Management MQ 逐家承认第三方运行时治理缺失（Dify 被点名）+ 78% 渗透/4 亿月连接/382 条 CVE·79 Critical/16,000+ 新 server/KS 市场 $235M→$1,725M·CAGR 49%/OCC 纳管/12–18 月索要 agent inventory；🔥 DigitalOcean Action Gateway 治理规格 + ISI Governed access + Agiloft HITL + Subaio 最小权限 + Peliqan GDPR ROPA 七字段；🔥 广发两份征集公告**官方原文逐条核实**（11-13 17:30 截止 · 13322 把五件套写进资质且指定 Dify/hiagent）；11-05 五条 Java+Spring+MCP JD；Upwork 0–15% 三源复核 + Jobs API；7 条卖点）；🆕 **`docs/gf-securities-tender-response-2026-11-07.md`**（本期最有挣钱价值：12 条资质逐条→模块→仓库真实文件→应答话术 + 3 条诚实边界 + 资质自检 + 联合体分工 + 6 天行动清单 + 提交格式 + 风险）；新增 `docs/blog-java-mcp-cve-111771-gartner-mq-gf-tender-2026-11-07.md`（掘金/CSDN SEO 稿，含 McpResourceBindingFilter Java 骨架与加固 YAML）；🆕 **`examples/monitor-alert-rules/`**（兑现 V1.64 待办⑤：README + Prometheus 4 组 15 条 + McpSecurityAlertRules.java 应用内 6 条 + `mcp_security_*` 埋点契约 + 10 条验收方式，不进 reactor 零构建风险）；`docs/enterprise-rfp-checklist.md` 攻击面 **12→16 类** + GDPR ROPA 采购视角对照；新增 `docs/V1.65-release-notes.md`；README 三区块更新（日期改 11-07）+ 版本表 V1.65 行。**交叉验证**：11-05 ChatCLI 修复公告一次修掉的 7 类传输缺陷与 V1.64 `examples/governance-hardening/` 逐条对应，昨晚参考实现今早被第三方公告背书。无破坏性变更，21 模块构建不受影响** | ✅ 已完成 |
| **V1.66** | **三大云 + iPaaS 把 MCP 治理做成标准层 × 攻击面下沉到 OAuth 连接路径 × 定位复核与两项工程 TODO 闭环（情报 / **售前投标素材** / **可运行工程示例** / 市场发布）：新增 `docs/enterprise-cloud-gateway-comparison-2026-11-08.md`（★ 本期最有挣钱价值：Azure APIM AI Gateway 公开预览 / AWS AgentCore Gateway 五类扩展 / **腾讯云 v3.9.8 产品动态 11-06 18:27** / Celigo 11 月版 + 11 维能力对照 + **5 条买方问答话术** + 4 条销售动作 + 诚实边界）；新增 `docs/earnings-report-2026-11-08.md`（11-06~11-08 全源：`CVE-2026-119025` MCP Inspector 握手期 RCE 五步链 / opencode 无 CVE refactor 修复 / LiteLLM `GHSA-7hp6-4w63-5g45` / Travel Code 11-06 首调永不执行 + 10 分钟一次性码 / Workspace369 自批分离 + 审计只存字段名 / 11-06 五条 Java JD / Upwork WorkMotion $129.7M 与 GSV 口径交叉验证 / 调整后 7 条卖点）；新增 `docs/blog-java-mcp-cloud-gateway-connect-path-2026-11-08.md`（SEO 稿，含 4 段 Java 落地代码）；🆕 `examples/connect-path-guard/`（连接路径防护：字符级 URL 校验 / argv 启动 / 版本下限 / 远程 opt-in，6 攻击用例 + 3 对照组断言）；🆕 `examples/governance-hardening/McpSecurityMetrics.java` + 过滤器接入（**兑现 V1.65 待办 ④**：`mcp_security_*` 10 指标落地、告警规则开箱可用）；🆕 `examples/audit-ropa-export/`（**兑现待办 ⑤**：GDPR ROPA 七字段 JSON/CSV + 完整性自检）；`docs/enterprise-rfp-checklist.md` 攻击面 **16→18 类**；`examples/monitor-alert-rules/README.md` 撤下「契约未落地」标注；README 三区块（日期改 11-08）+ 版本表 V1.66 行。**三个新 Java 示例纯 JDK 零依赖，`javac` 编译 + 三个 `main()` 自检全绿**；无破坏性变更，21 模块构建不受影响** | ✅ 已完成 |
| **V1.67** | **「治理层 = 定价权」× 银行采购「底座期」× 官方参考实现七 CVE × 广发 11-12 最紧窗口（情报 / **售前投标素材** / **可运行工程示例** / 市场发布）：新增 `docs/earnings-report-2026-11-09.md`（11-07~11-09 全源：🔥 银行智能体平台底座期——兴业/北京农商全生命周期+≥50 Skill/上海农商 **MCP 插件市场**/中原安全围栏/浙商 6400 万/9 月 40 标 28 家；🔥 英国五大行封禁外部 Agent + 麦肯锡 5~10% 存款迁移→利润降 1/5、7000 亿英镑 + FCA 立规；🔥 11-08 官方 `modelcontextprotocol/servers` 七 CVE `41901`~`41907`（SELECT 前缀检查被 `VACUUM INTO` 击穿 / 只读 git_status 执行 hook / 漏第 4 条路径 / PyPI 上游归档）；🔥 Anthropic STDIO「Won't Be Patched」12+ CVE·1.5 亿 SDK 下载·唯一缓解是 command allowlist；🔥 广发第三标 11-12 17:30 含「MCP 安全工具管理」；Google Cloud 成第四朵云；朝阳法院 AI 办公判例；3 条新 Java JD（小米 25-30k/智慧树/**医疗 EMPI MCP 化封装**）；接单定价 7 档 $3.7K-72K·生产级 $80K-220K·网关 $250K 起·Upwork 官方时薪；7 条卖点）；★ 新增 `docs/mcp-governance-pricing-anchor-2026-11-09.md`（报价锚：治理层 10 倍定律逐项对位 21 模块 + 三档报价模板 + 加价触发器）、`docs/bank-agent-platform-tender-response-2026-11-09.md`（银行买底座 10 条逐条应答 + 广发三标矩阵 + 买方必问 5 问 + 资质自检 + 6 天行动清单）；新增 `docs/blog-java-mcp-reference-server-cve-pricing-2026-11-09.md`（掘金/CSDN SEO 稿，含 2 段可编译 Java 代码）；🆕 `examples/connect-path-guard/McpResolvedIpGuard.java`（**兑现 V1.66 待办⑤**：解析后真实 IP 校验 + IP 固定防 DNS rebinding，16 攻击 + 5 对照组）；🆕 `examples/tool-input-grammar/`（per-tool statement grammar 对位 `CVE-2026-41901`，8 攻击 + 6 越权 + 7 对照 + 4 边界，含「不得劣于旧基线」断言）；`docs/enterprise-rfp-checklist.md` 攻击面 **18→20 类** + 金融「受控只读接入」验收列；`docs/V1.67-release-notes.md`；README 三区块（日期改 11-09）+ 版本表 V1.67 行。**三个 Java 示例纯 JDK 零依赖，`javac` 编译 + `main()` 自检全绿**；无破坏性变更，21 模块构建不受影响** | ✅ 已完成 |

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
