# 📄 MCP Enterprise Server — API 文档

> 所有接口基于 HTTP REST，默认端口 8081

---

## 基础信息

| 项目 | 值 |
|------|-----|
| Base URL | `http://localhost:8081` |
| 认证方式 | `X-API-Key` Header |
| 内容类型 | `application/json` |
| 版本 | v0.1.0+ |

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
