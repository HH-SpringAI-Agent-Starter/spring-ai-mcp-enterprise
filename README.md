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

### 📑 最新央企/军工/国企/卫健 MCP 与算力招标（可对标交付，更新 10-14）
- 🔥🔥 **AI 网关、MCP 注册、多模态平台** — 中国航空工业集团西安飞行自动控制研究所（编号 **ZC26G280436**，招标文件获取已截止 10-09，投标 **10-20 09:00** 截标，国产商业平台 + MCP 组件 + 2 台智算服务器，3 个月到货，不接受联合体）→ 对应 `mcp-registry` + `mcp-gateway` + `mcp-alibaba`。**投标技术方案模板** → [`docs/tender-proposal-ZC26G280436-2026-10-03.md`](docs/tender-proposal-ZC26G280436-2026-10-03.md) ｜ **等保对照表** → [`docs/mcp-governance-dengbao-compliance.md`](docs/mcp-governance-dengbao-compliance.md) ｜ **安全事件简报（合规加分）** → [`docs/mcp-security-incident-brief-2026-10.md`](docs/mcp-security-incident-brief-2026-10.md)
- 🔥🔥 **油气生产管理多智能体架构（专题2：MCP 服务）** — 中石油·长庆油田（**130 万**，**10-16 14:00 截标（仅 2 天！本周最硬 Deadline）**）→ 对应 `mcp-registry` + `mcp-governance` + `mcp-tools/tool-database`。**投标素材包** → [`docs/tender-asset-changqing-2026-10-04.md`](docs/tender-asset-changqing-2026-10-04.md) ｜ **安全事件简报（合规加分）** → [`docs/mcp-security-incident-brief-2026-10.md`](docs/mcp-security-incident-brief-2026-10.md)
- 🔥 **12316 平台「助农增收智能体 MCP 试点」** — 宁夏农业农村厅（**287.2 万已中标**，10-08 公示，中国电信宁夏 82.52 分第一，6 家投标）：**MCP 第一次写进政府采购标的名称**，标志政务 AI 进入"指名 MCP"时代；运营型+试点型是政务 AI 主流打法（周期长/续约高/数据积累强）→ 对齐 `mcp-registry` + `mcp-governance` + 中间量验收方案（首次响应时长/一次解决率/自动处理占比/采纳率）。
- 🆕 **湖北省疾控中心「人工智能+预警预测场景建设」** — **150 万**（2026-11 采购意向，10-08 发布）：明确 **MCP 插件接入国家平台 + 多租户适配 + 等保三级 + 数据安全** → 完美对齐 `mcp-registry` + `mcp-tenant` + `mcp-governance`。首个点名 MCP+等保三级的**卫健系统**标，行业从能源/军工扩到公共卫生。
- 🆕 **GSA 美国政府「MCP Server + AI Agent 政府 Hackathon」**（9-11月，Databricks/OpenAI/IBM 合作，联邦 open data → MCP server 原型）→ 政府级 MCP 需求信号，未来 6-12 月 gov 标将放量。
- 💡 **算力/AI 基础设施集采持续放量**：中国移动 2026-2027 PC 服务器集采框架上限 **13.41 亿**、云南智算 **约 1.37 亿**、山东超融合 **696 万**；央企 AI 平台标普遍把 **MCP 注册中心+网关+治理（等保）** 作子项。
- 🔥 **企业侧治理分层成标配（强验证 mcp-governance）**：**Atlassian 重建 MCP Server 至 220+ 工具 / 1500 万次/日调用**，明确做 **read/write/destructive 风险分层 + OAuth 2.1 PKCE + Guard DLP + 审计**（正是本框架 T0–T4 + HITL + JDBC 审计范式）；**Precisely 把数据清洗/SAP 自动化/地址校验等全系产品发布为 MCP Server**——每个厂商上 MCP 都需要治理层，本框架即"官方方案的治理增强层"。
- 🔥 **OX Security 扫描 15,465 个公开 MCP Server**：市场**零安全护栏**、15.6% 部署在海外（19 中国 / 18 俄罗斯）、2.3% 挂在过期域名（可 $4–12 劫持）、0.45% 走 ngrok 个人隧道 → 企业**自托管 + 治理**是唯一解，`mcp-governance`+`mcp-auth`+`mcp-tenant` 直接命中。
- 🆕 **金融智能体采购进入"治理前置"阶段（10-12 腾讯新闻）**：9 月金融智能体**招标 33 条、中标 14 条披露金额约 2109 万**（单笔最高光大银行 452.9 万），银行占 **57.6%**（建行上海分行智慧医院科研经费管理智能体、天津分行财务共享智能合规审核、中行上海分行智能体安全管理平台、中国进出口银行一月三条、中国信达/长城 AMC 再现身）；**MCP 协议、多智能体协同、可观测性与安全治理**已从技术话题变成采购文件具体条目 → 本框架 `mcp-governance`(HITL/审计/Scope/限流) + `mcp-monitor`(可观测) + `mcp-registry` 直接命中"治理前置"硬需求。
- 🔥🔥 **中国电信襄阳分公司·老河口市人工智能大模型赋能平台（询比，45.43 万，10-09 发、10-16 14:00 截，仅 2 天！本周最硬 Deadline）**：含**知识库管理 + 工单分派智能体 + 政务办公智能体 + 社会治理智能体**调测；延续湖北疾控 150 万线索，华中政务 AI 标密集 → 对齐 `mcp-registry`+`mcp-governance`+中间量验收。
- 💡 **宁夏算力/AI 集采持续放量**：宁夏建材**智算服务器**采购（10-09）、宁夏电投煤电、宁夏煤业 DEEPSEEK 算力服务器租赁（9-30）——叠加 12316「助农增收智能体 MCP 试点」287.2 万中标，宁夏已成政务 MCP + 算力双热点，适合本地化交付团队深耕。

### 🏢 点名 MCP 的企业招聘 / 接单（近 3 天，10-10~10-13）
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
- **Upwork 官方 MCP Server 已成接单基础设施**（`mcp.upwork.com`）；AI Agent/MCP 类 **$120–300/hr**（资深 $200+ 标配）；项目包 MCP Server $90/$180/$350（进阶 $1,500），+$75/工具，+$200/系统；行业实测常态 **$1,500–5,000/项目**、复杂 **$10,000+**；SecurityScorecard 招 Senior MCP Engineer（生产级 OAuth/租户隔离/审计，ChatGPT/Claude/Copilot/Codex 客户端，$10–250/hr，50+ 提案）。组合拳：本框架作交付底座 + Upwork MCP 作获客前端。
- 🆕 **猎聘 · 高级 Java 开发工程师（上市公司核心岗 25K·武汉·5-8年）**（今日 10-12 更新，52 分钟前在线）— JD 原话"**MCP 协议接入、AI Agent 系统开发、LangChain4j 应用开发、RAG 知识库**"，要求 Spring Cloud 微服务 + 高并发治理 → 与本框架技术栈 100% 重合，仓库即作品集。
- 🆕 **天津 · AI 开发工程师（25-35k·石化·3-5年）**（10-12 在招）— 要求 **Spring AI / Agent Scope / RAG / 企业级智能体平台或 AI 中台 / 多租户隔离** → 直接命中 `mcp-governance`(多租户) + `mcp-spring-ai-tools`。
- 🆕 **Cotality · Senior Software Engineer (MCP Servers)**（达拉斯，截至 10-11）— **MCP Server + Apigee X 网关 + OAuth 2.1 + MCP Inspector 测试**，要求 Python/JS/**Java** 5+ 年 → 命中 `mcp-gateway`+`mcp-auth`。
- 🆕 **EPAM · Senior Java Engineer – AI Native**（印度 Coimbatore）— **用 Spring Boot 构建并部署 MCP Server** 把 Java 服务/库暴露给 LLM Agent，5–10 年 → 全栈对齐。
- 🆕 **InAddition · Software Engineer (AI Solution / MCP Focus)**（台北，1.1M–1.3M TWD/年）— **用 Java 实现 MCP Server** + Docker + 完整测试 → 全栈对齐。
- 🆕 **猎聘 · 箱箱共用 Java 高级工程师（20-30K·13薪·上海）** — JD 原话"**MCP 协议、Skills、SubAgent 及 Agent 设计模式**，构建可扩展智能体编排框架(Harness)" → 全栈对齐。
- 🆕 **猎聘 · 全栈(Java+AI应用) 9-13K·上海浦东** — 要求 **Spring AI / MCP / LangChain** 大模型接入、Agent、知识库 → `mcp-spring-ai-tools`。
- 🆕 **鱼泡直聘 · AI 开发工程师 8-12K·北京丰台** — 职责"遵循 **MCP 开发规范**，采用 Java 语言参与 AI 功能模块开发" → 全栈对齐。
- 🆕 **Mindera · Senior Engineer AI Specialist（罗马尼亚 Sibiu，远程友好）**（10-14 在招）— JD 原话"**building MCP servers**"：Java 17/21+ Spring Boot + Spring AI + LangGraph/LangChain4j，要求 RAG/MCP 实战深度（非年限堆叠）→ 全栈对齐 `mcp-spring-ai-tools`。
- 🆕 **Built In · Mid-level Java Engineer (AI Agents, MCP)**（全球远程，波兰语）— 为**全球保险公司**从零建高可用 MCP Server（Java 17+ Spring Boot，Spring AI MCP / 核心 SDK，JSON-RPC 2.0 + SSE），REST/gRPC/Kafka 连接器，**$75–150/hr·3-5 年** → 全栈对齐。
- 🆕 **杭州火石创造 · 高级 Java 工程师（MCP / Spring AI 方向）15–30k·5-10 年**（10-14 在招）— JD 原话"基于 Spring AI 框架开发企业级智能体平台核心服务""负责 **MCP 服务接口设计**，实现智能体与业务连接" → 全栈对齐。
- 🆕 **智联 · 高级后端工程师（LLM 应用工程化 / MCP 多智能体协作）苏州·5-10 年**（10-14 在招）— JD 原话"设计并实现 **MCP 协议下的 Client/Server 服务框架**，支持多 Agent 工具调用、跨系统协作""权限与沙箱""多租户与可扩展部署" → 命中 `mcp-governance`+`mcp-tenant`。
- 🆕 **中恒信 · AI 集成与平台开发工程师（Java/AI）青岛·应界可投**（10-14 在招）— JD 明确"探索并集成如 **MCP 等协议**，标准化 LLM 与内部/第三方工具的交互能力" → 全栈对齐。

### 💰 为什么 Java + Spring + AI 是 MCP 蓝海（78% 已用，但 91.5% 裸奔）
- 🔥 **MCP 已成企业标配**：企业 AI 团队采用率一年 **31% → 78%**，500 强 **28%** 已生产运行 MCP server，>10,000 server 在生产；250+ AI 工程师企业 **89%** 生产采用。
- 🔥 **安全治理是最大鸿沟（最强验证本框架）**：仅 **8.5%** 实现强制 OAuth 2.1、**43%** 命令注入漏洞、**492** 个 server 零认证裸奔、**18%** 任何工具权限 scoping；NSA+CISA 联合公告要求治理由部署方自建 → 本框架 `mcp-governance`（RBAC/审计/HITL/限流/网关集中鉴权/EMA）**直接命中痛点**。
- 🔥 **CISA KEV 首个 MCP 漏洞 + Protocol Pivoting（10 月最新，治理护城河最强证据）**：LiteLLM MCP 端点接受**任何无效 bearer token** → **CVE-2026-59822 成为第一个登上 CISA 已知被利用漏洞清单（KEV）的 MCP 漏洞**；Google `mcp-toolbox` SSRF **CVE-2026-14540（CVSS 8.0）**；同款信任边界缺陷横跨 Google/JPMorgan/Weaviate/法国政府/印尼政府（Ars Technica 10-05 头条「Protocol Pivoting」）；Grafana MCP 会话伪造 CVE-2026-19516；CoSAI 发布 **MCP 安全模型 v2.0** → 本框架 `mcp-auth`（V1.9 Bearer 强制校验 + jti 防碰撞 + 吊销端点）+ `mcp-governance`（HITL/审计/Scope/SSRF 防护）**直接命中全部缓解项**；**安全事件简报（可进标书/审计报告）** → [`docs/mcp-security-incident-brief-2026-10.md`](docs/mcp-security-incident-brief-2026-10.md)
- **Spring AI Alibaba 1.0 GA（2026-10）**：企业级 MCP = **Nacos MCP Registry + Higress AI 网关**，与本框架 `mcp-registry`+`mcp-gateway` 定位同款；但评测机构 FollowAgents 直指其 **"1 safety controls not found"（缺安全控制）** → 本框架补上最缺的**安全治理层**，定位"Spring AI Alibaba 的治理增强层"。当前稳定 1.0.0.2 GA、starter 实跑 1.1.2.0、11k stars（+399/月）、275 contributors。GA 示例 → [`examples/spring-ai-alibaba-mcp-server/`](examples/spring-ai-alibaba-mcp-server/) ｜ SEO 稿（治理角度）→ [`docs/blog-java-mcp-security-governance-2026-10-08.md`](docs/blog-java-mcp-security-governance-2026-10-08.md) ｜ **MCP 安全加固 5 步速查** → [`docs/mcp-security-hardening-quickstart.md`](docs/mcp-security-hardening-quickstart.md)
- 🛡️ **等保 2.0 三级合规对照表**：`mcp-governance` 逐条映射等保三级，每条有模块+测试证据 → [`docs/mcp-governance-dengbao-compliance.md`](docs/mcp-governance-dengbao-compliance.md)。**可直接进央企/军工/卫健标书作"合规就绪"证明。**
- 🆕 **Spring AI Alibaba 生态官方化（10-13 更新）**：Nacos 3.0 发布 **MCP Server 自动注册与发现手册**（`spring-ai-alibaba-starter-mcp-registry >= 1.0.0.3`，@Tool 方法启动即注册 Nacos、工具描述/参数运行期热更新、工具运行期启停）；Higress 升级 **CNCF Sandbox v2** 开源 AI 网关——官方只给"注册+路由"，**安全治理（RBAC/审计/HITL/Scope/多租户）仍是空白** → 本框架"治理增强层"定位被生态坐实。GA 示例 → [`examples/spring-ai-alibaba-mcp-server/`](examples/spring-ai-alibaba-mcp-server/) ｜ 今日 SEO 稿 → [`docs/blog-java-mcp-spring-ai-alibaba-ecosystem-2026-10-13.md`](docs/blog-java-mcp-spring-ai-alibaba-ecosystem-2026-10-13.md)
- 🔥 **10-10/10-11 新 CVE 集群再证"认证≠授权"结构性缺陷（治理护城河最强新证据）**：CVE-2026-108585/108586（10-10）**MCP Server Authorization Failures**——argocd-mcp `delete_application` 路径穿越（删 K8s 资源）+ @1mcp/agent 0.20–0.39 越权（confused-deputy，只读 agent 调别人 scope 工具）；核心论断原文"**MCP server implementations are treating client authentication as if it were tool-level authorization**"——正是 `mcp-governance` 的 Scope-ACL/HITL 要解决的结构性弱点类。worldofai 统计在录 **729 个 AI CVE（111 关键 / 352 高 / 5 个 CISA KEV）**，本周新增：JeecgBoot 任意登录用户读 MCP 密钥（CVE-2026-108671 HIGH 7.1）、x64dbg-MCP 任意网络客户端跑调试器命令（CRITICAL 9.3）、**mcp-go through 1.2.1 无界 body 读取 OOM（CVSS 8.7 HIGH，9.1k stars 主导 Go MCP 实现，发布两天即被报）**、Splunk MCP Server 反序列化 RCE（CVE-2026-76404 CVSS 9.1 CRITICAL）、Odoo/phpMyFAQ MCP 绕过既有 ACL——反复印证"成熟产品长出 MCP 面，权限模型默认不带过来"。**本框架 `mcp-governance`(RBAC/Scope/ACL/HITL/审计) + `mcp-auth`(Bearer 强制/jti/吊销) 逐条对症，安全事件简报已扩至可进标书。**
- Java+AI 复合岗 3–5 年 **45K–75K**；二线城市 25-35K·16薪；海外 MCP Expert（Java）€600-900/天。
- 🆕 **🔥 CIS MCP Benchmark 55 点审计基准 + Agentics 六类攻击 taxonomy（10-14 治理护城河最强新证据）**：CIS 已设 **55 点 agent tool governance 审计基准**，监管引用 Five Eyes《Agentic AI Security Guidance》+ NIST AI RMF 把 least-privilege 作基线；Agentics《Enterprise MCP Guide 2026》给出最细 **六类攻击 taxonomy**（Tool/Schema Poisoning、Tool Shadowing、Command Injection、Shadow Servers、Context Oversharing）并披露 **68 个 MCP Server CVE/单月**；EU AI Act / 新加坡 MAS / 韩国正把 MCP 工具层治理写进**强制要求** → 治理从「卖点」升级为「合规刚需」，本框架 `mcp-governance`+`mcp-auth`+`mcp-tenant` 即「合规就绪」资产。
- 🆕 **🔥 新无修复 CVE：CVE-2026-104120（MCP Fetch Server SSRF，CVSS 7.3 High）**：官方 `mcp-server-fetch` 的 `fetch_url` 不做 loopback/link-local/metadata/私网过滤、跟随重定向不重校验目标，**公开利用存在、修复 PR #4890 仍为未合并草稿（截至 10-04）**；叠加 Protocol Pivoting 持续击中 Google/JPMorgan/法国 DINUM/印尼 Tangerang 政府——标准库「注册+路由」天然缺治理，本框架 `mcp-governance`(SSRF 防护/输入校验/Scope) + `mcp-auth`(Bearer 强制/jti/吊销) 逐条对症，**安全事件简报已扩至可进标书**。
- 🆕 **接单定价梯度（10-14 更新）**：自由职业 MCP 开发 **$75–150/hr**（中级别 $40–80/hr，Upwork/Contra 实测）；MCP 基础设施岗 **€800–1,000/天**（最高溢价，BOVO Digital）；Upwork 官方 Services 三档 **$400 / $800 / $1,500**（交付 5/7/10 天）；项目包 **$1K / $3K–5K / $5K–10K**（单连接器/多工具/全管道）；企业全职（MCP 方向）**$170K–280K 总包**（美国，Inventiple）。组合拳：本框架作交付底座 + Upwork 官方 `mcp.upwork.com` MCP 作获客前端（AI 先按结构化 profile 过滤再进短名单）。

📊 完整每日收益/招标情报与接单定价梯度 → 见 [`docs/earnings-report-2026-10-14.md`](docs/earnings-report-2026-10-14.md) ｜ 上一份 → [`docs/earnings-report-2026-10-13.md`](docs/earnings-report-2026-10-13.md) ｜ **安全事件简报** → [`docs/mcp-security-incident-brief-2026-10.md`](docs/mcp-security-incident-brief-2026-10.md) ｜ **今日 SEO 稿** → [`docs/blog-java-mcp-cis-benchmark-governance-2026-10-14.md`](docs/blog-java-mcp-cis-benchmark-governance-2026-10-14.md)

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
