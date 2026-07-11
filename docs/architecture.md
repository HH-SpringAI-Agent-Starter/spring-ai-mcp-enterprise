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

| 层次 | 组件 | 可配置 |
|------|------|--------|
| L1 传输 | API Key header 校验 | mcp.enterprise.security.api-key-enabled |
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
