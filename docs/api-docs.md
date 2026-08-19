# 📡 MCP Enterprise Server — API 文档

> 所有接口基于 HTTP REST，默认端口 8081

---

## 基础信息

| 项目 | 值 |
|------|-----|
| Base URL | `http://localhost:8081` |
| 认证方式 | `X-API-Key` Header |
| 内容类型 | `application/json` |
| 版本 | v1.1.0+ |

---

## 1. 健康检查
```
GET /api/mcp/health
```

不需要 API Key。
**Response:**
```json
{
  "status": "UP",
  "toolCount": 3,
  "activeSessions": 0,
  "uptime": 1744387200000
}
```

---

## 2. 连接服务

```
POST /api/mcp/connect
```

建立新会话。需要 API Key。
**Headers:**
| Header | 值 | 必填 |
|--------|-----|------|
| X-API-Key | 你的 API Key | 是 |
| Content-Type | application/json | 是 |

**Body:**
```json
{
  "clientName": "my-ai-agent"
}
```

**Response:**
```json
{
  "success": true,
  "sessionId": "uuid-string",
  "serverVersion": "0.0.1",
  "supportedProtocols": ["mcp-v1", "streaming-v1"]
}
```

---

## 3. 断开连接

```
POST /api/mcp/disconnect
```

**Body:**
```json
{
  "sessionId": "uuid-string"
}
```

**Response:**
```json
{
  "success": true
}
```

---

## 4. 列出工具

```
GET /api/mcp/tools
```

返回所有已注册工具。

**Response:**
```json
{
  "success": true,
  "total": 3,
  "tools": [
    {
      "name": "database-query",
      "displayName": "数据库查询",
      "description": "执行 SQL 查询并返回结果",
      "category": "database",
      "version": "1.0.0",
      "enabled": true,
      "requiredRoles": "user",
      "timeoutMs": 30000,
      "rateLimitPerSecond": 10
    }
  ]
}
```

---

## 5. 获取工具详情

```
GET /api/mcp/tools/{name}
```

**Path Parameters:**
| 参数 | 类型 | 说明 |
|------|------|------|
| name | string | 工具名称 |

**Response:**
```json
{
  "success": true,
  "tool": {
    "name": "database-query",
    "displayName": "数据库查询",
    "description": "执行 SQL 查询并返回结果",
    ...
  }
}
```

---

## 6. 调用工具

```
POST /api/mcp/tools/{name}/invoke
```

**Headers:**
| Header | 值 | 必填 |
|--------|-----|------|
| X-API-Key | 你的 API Key | 是 |
| Content-Type | application/json | 是 |

**Body (工具相关参数):**
```json
{
  "query": "SELECT COUNT(*) FROM users",
  "maxRows": 100
}
```

**Response:**
```json
{
  "success": true,
  "tool": "database-query",
  "status": "invokable",
  "sdkEndpoint": "/api/mcp/sdk/database-query"
}
```

---

## 7. 服务统计

```
GET /api/mcp/stats
```

**Response:**
```json
{
  "tools": {
    "total": 3
  },
  "sessions": {
    "active": 2
  },
  "audit": {
    "recentEntries": 42
  }
}
```

---

## 8. Actuator 管理端点

MCP Enterprise Actuator Endpoint 提供框架内部状态查看：

| 端点 | 说明 |
|------|------|
| `GET /actuator/mcp-enterprise` | 概要信息（工具数、分类统计） |
| `GET /actuator/mcp-enterprise/tools` | 工具完整列表 |
| `GET /actuator/mcp-enterprise/security` | 安全状态 |
| `GET /actuator/mcp-enterprise/audit` | 审计日志 |

---

## 9. Java SDK 示例

参见 `examples/client-java/McpEnterpriseClient.java`

```java
McpEnterpriseClient client = new McpEnterpriseClient();
client.health();        // 健康检查
client.connect("demo"); // 连接
client.listTools();     // 列出工具
client.invokeTool("database-query", params); // 调用
```

## 10. Python SDK 示例

参见 `examples/client-python/mcp_client.py`

```python
with McpEnterpriseClient() as client:
    client.health()
    client.connect()
    client.list_tools()
    client.invoke_tool("database-query", {"query": "SELECT 1"})
```

## 11. curl 示例

参见 `examples/curl-examples.sh`

```bash
# 一键完成全部流程
bash examples/curl-examples.sh
```

---

## 12. V1.6 网关路由指标端点

```
GET  /api/monitor/metrics/gateway    — 网关路由指标（Mcp-Method/Mcp-Name 维度）
DELETE /api/monitor/metrics/gateway  — 重置网关指标
```

**Response (GET):**
```json
{
  "operations": [
    {
      "method": "tools/call",
      "name": "greet",
      "totalInvocations": 1283,
      "errors": 12,
      "errorRate": "0.94%",
      "avgLatencyMs": 42.5
    }
  ],
  "total": 1,
  "totalInvocations": 1283,
  "timestamp": "2026-08-19T13:30:00Z"
}
```

---

## 13. V1.7 网关限流路由表管理端点

> 无需重启，运行时按 Mcp-Method / Mcp-Name 调整各操作 QPS

### 13.1 查看限流规则
```
GET /api/mcp/v2/ratelimit/rules
```
**Response:**
```json
{
  "enabled": true,
  "rules": [
    {"method": "tools/call", "name": "*", "maxPerSecond": 100},
    {"method": "tools/list", "name": "*", "maxPerSecond": 5}
  ],
  "total": 2
}
```

### 13.2 新增/更新限流规则
```
POST /api/mcp/v2/ratelimit/rules
Content-Type: application/json

{"method": "tools/call", "name": "greet", "maxPerSecond": 10}
```
- `method`: Mcp-Method 匹配模式（tools/call、tools/list、*）
- `name`: Mcp-Name 匹配模式（greet、finance_*、*；空串表示无 name 操作）
- 匹配优先级：精确 > 前缀通配 > 全通配
- 超限返回 JSON-RPC error code `-32029`（Rate limit exceeded）

### 13.3 删除限流规则
```
DELETE /api/mcp/v2/ratelimit/rules?method=tools/call&name=greet
```

### 13.4 清空所有规则（恢复全放行，谨慎操作）
```
DELETE /api/mcp/v2/ratelimit/rules/all
```

### 13.5 限流开关
```
POST /api/mcp/v2/ratelimit/toggle
{"enabled": false}
```

---

## 14. V1.7 Prometheus 指标导出

```
GET /api/monitor/metrics/prometheus
```
输出 OpenMetrics 文本格式（`text/plain; version=0.0.4`），Prometheus 可直接抓取：

| 指标 | 类型 | 说明 |
|------|------|------|
| mcp_tool_invocations_total{tool} | counter | 工具调用总数 |
| mcp_tool_errors_total{tool} | counter | 工具错误数 |
| mcp_tool_latency_ms{tool} | gauge | 工具平均延迟(ms) |
| mcp_gateway_invocations_total{method,name} | counter | 网关操作调用数 |
| mcp_gateway_errors_total{method,name} | counter | 网关操作错误数 |
| mcp_gateway_latency_ms{method,name} | gauge | 网关操作平均延迟 |
| mcp_build_info{version} | gauge | 版本信息 |

Prometheus scrape 配置：
```yaml
scrape_configs:
  - job_name: mcp-enterprise
    metrics_path: /api/monitor/metrics/prometheus
    static_configs:
      - targets: ['mcp-server:8081']
```
