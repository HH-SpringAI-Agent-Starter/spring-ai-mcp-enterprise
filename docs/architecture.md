# 🏗️ MCP Enterprise Server — 架构说明

> 一个为 Java/Spring Boot 生态设计的企业级 MCP (Model Context Protocol) Server 框架

---

## 设计哲学

```
┌──────────────────────────────────────────────────────────┐
│                  为什么需要 MCP Enterprise？              │
├──────────────────────────────────────────────────────────┤
│                                                          │
│  问题：企业系统（DB/ERP/CRM/文档）需要暴露给 AI Agent，  │
│        但缺少安全、审计、管理框架。                       │
│                                                          │
│  答案：MCP Enterprise =                                   │
│        Spring Boot 天然亲和 + 企业安全开箱即用             │
│        + 工具注册/发现/管理 + 监控告警                    │
│                                                          │
│  对比市场方案：                                           │
│        agentic-trust  → 只有 Python 版                   │
│        open-mcp       → 纯 Python                        │
│        mcp-rs         → Rust 版                          │
│        ★ MCP Enterprise → 唯一 Java/Spring Boot 企业版   │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

---

## 整体架构

```
                      AI Agent / LLM
                    (Claude / GPT / 通义千问)
                           │
                           ▼  MCP Protocol
              ┌──────────────────────────┐
              │   MCP Enterprise Server   │
              │                           │
              │  ┌─────────────────────┐  │
              │  │    REST API Layer   │  │  ← HTTP/SSE 端点
              │  │  /api/mcp/*        │  │
              │  └────────┬────────────┘  │
              │           ▼               │
              │  ┌─────────────────────┐  │
              │  │   Tool Registry     │  │  ← 注册/发现/版本/热加载
              │  │   (注册中心)         │  │
              │  └────────┬────────────┘  │
              │           ▼               │
              │  ┌────┬────┬────┬──────┐  │
              │  │ DB │搜索│系统│ 自定义│  │  ← 工具实现
              │  └────┴────┴────┴──────┘  │
              │                           │
              │  ┌─────────────────────┐  │
              │  │    Enterprise 层    │  │
              │  │  RBAC  RateLimit    │  │
              │  │  审计日志 监控      │  │
              │  └─────────────────────┘  │
              └──────────────────────────┘
```

---

## 模块职责

| 模块 | 职责 | 依赖 |
|------|------|------|
| **mcp-core** | 核心模型(ToolDefinition)、工具注册中心(ToolRegistry)、安全框架(McpSecurityManager：API Key/RBAC/RateLimit/审计/IP白名单) | Spring AI MCP SDK |
| **mcp-spring-boot-starter** | 自动配置：Spring Boot 导入后自动注册核心 Bean + Actuator Endpoint | mcp-core |
| **mcp-server** | 可运行服务：入口+Controller+REST API+初始器(创建默认API Key) | starter + web + actuator |
| **mcp-tools/tool-database** | 数据库查询工具(JDBC) | mcp-core + spring-jdbc |
| **mcp-tools/tool-search** | 搜索查询工具 | mcp-core |
| **mcp-tools/tool-system** | 系统命令工具 | mcp-core |
| **mcp-monitor** | 监控中心：调用统计、审计查看、告警 | mcp-core + web |

---

## 核心流程

### 1. 工具注册流程

```
工具开发者 → 实现 Tool 接口 → 放入 scan-packages
         ↓
  McpServerInitializer @PostConstruct
         ↓
  ToolRegistry.register(name, definition, instance)
         ↓
  REST API / 其他人发现
```

### 2. 请求处理流程

```
Client → POST /api/mcp/tools/{name}/invoke
         ↓
  McpServerController
         ↓
  McpSecurityManager.validateApiKey()   ← API Key 认证
  McpSecurityManager.checkPermission()  ← RBAC 鉴权
  McpSecurityManager.checkRateLimit()   ← 频率限制
         ↓
  ToolRegistry.getDefinition(name)      ← 查找工具
         ↓
  McpSecurityManager.audit()            ← 审计记录
         ↓
  返回工具定义 / 转发到 MCP SDK 调用
```

### 3. 安全层次

| **mcp-auth** | 🔐 企业认证层：OAuth2/SSO/JWT + API Key 兼容 + 多 IdP 支持 | spring-security + oauth2-client |

> 新增！mcp-auth 模块支持 MCP Enterprise Auth 规范（2026-07-13 稳定版）

---

## 企业认证架构（mcp-auth）

```
                    ┌──────────────────────┐
                    │    Identity Provider   │
                    │  Keycloak/Okta/Azure AD│
                    └──────────┬───────────┘
                               │ OIDC / OAuth2
                               ▼
              ┌──────────────────────────┐
              │     mcp-auth 模块        │
              │                          │
              │  ┌────────────────────┐  │
              │  │ OAuth2 Login/SSO  │  │  ← 用户通过浏览器 SSO 登录
              │  └────────┬───────────┘  │
              │           ▼              │
              │  ┌────────────────────┐  │
              │  │ JWT Token Provider │  │  ← 生成/验证 MCP 会话令牌
              │  └────────┬───────────┘  │
              │           ▼              │
              │  ┌────────────────────┐  │
              │  │ API Key 兼容层    │  │  ← 支持旧的 X-API-Key 请求
              │  └────────┬───────────┘  │
              │           ▼              │
              │  ┌────────────────────┐  │
              │  │ RBAC 角色映射     │  │  ← IdP 角色 → MCP 角色
              │  └────────────────────┘  │
              └──────────────────────────┘
                         │
                         ▼ MCP 请求（已认证）
              ┌──────────────────────────┐
              │     mcp-core 安全层      │
              │  RateLimit + 审计 + IP   │
              └──────────────────────────┘
```

### 三种认证模式

| 模式 | 使用场景 | 配置 |
|------|---------|------|
| `none` | 开发/测试 | `mcp.auth.mode=none` |
| `api-key` | 默认/向后兼容 | `mcp.auth.mode=api-key`（默认） |
| `oauth2` | 企业 SSO 生产环境 | `mcp.auth.mode=oauth2` + 配置 IdP |

### 支持的 IdP

| 身份提供商 | 类型 | 配置示例 |
|-----------|------|---------|
| Keycloak | 开源自建 | `application-auth.yml#keycloak-dev` |
| Okta | 云 SaaS | `issuer-uri: https://dev-xxxxxx.okta.com` |
| Azure AD | 微软云 | `issuer-uri: https://login.microsoftonline.com/{tenant}/v2.0` |
| Auth0 | 云 SaaS | `issuer-uri: https://your-tenant.auth0.com` |

| 层次 | 组件 | 可配置 |
|------|------|--------|
| L0 身份 | OAuth2/OIDC 企业 SSO | mcp.auth.mode=oauth2 |
| L0 令牌 | JWT 会话令牌 | mcp.auth.jwt-secret |
| L1 传输 | API Key / Bearer Token | mcp.auth.mode=api-key / oauth2 |
| L2 鉴权 | RBAC 角色权限 | createApiKey 时指定 roles |
| L3 限流 | Token Bucket 频率限制 | ToolDefinition.rateLimitPerSecond |
| L4 审计 | 全调用链路记录 | mcp.enterprise.security.audit-log-enabled |
| L5 网络 | IP 白名单 | McpSecurityManager.addIpToWhitelist |

---

## 与 Spring AI Alibaba 集成

MCP Enterprise 天然兼容 Spring AI Alibaba 生态。

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
```

完整配置见 `application-alibaba.yml`

---

## 扩展性设计

### 1. 工具 SPI
实现 `McpTool` 接口 → SPI 自动发现 → 无需修改框架代码

### 2. 自定义安全
覆盖 `McpSecurityManager` Bean → 对接企业 LDAP/OAuth2/SSO

### 3. 自定义传输
默认 HTTP/REST → 可扩展 SSE/WebSocket 传输层

### 4. 集群部署
mcp-monitor → Prometheus 采集 → Grafana 面板 → 横向扩展支持

---

## 技术栈

| 组件 | 版本/选择 | 原因 |
|------|-----------|------|
| Java | 17+ | LTS，主流企业版本 |
| Spring Boot | 3.4.x | 最新稳定版 |
| Spring AI MCP | 1.0.0-M6 | Spring 官方 MCP 支持 |
| Reactor | 3.6+ | 响应式编程，处理并发连接 |
| Prometheus | 最新 | 企业监控标准 |
