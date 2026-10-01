# MCP Enterprise curl 客户端示例

## 概述

本目录提供 `curl` 命令行调用 MCP Enterprise Server 的完整示例，适用于：
- 快速验证 Server 是否正常运行
- CI/CD 流水线中的健康检查
- 不使用 Java SDK 的轻量级集成场景

## 前提条件

MCP Enterprise Server 已启动：
```bash
# 方式一：直接运行
java -jar mcp-server/target/mcp-server-1.1.0.jar

# 方式二：Docker
docker compose up -d mcp-server
```

## 使用方法

### Linux / macOS / Git Bash
```bash
chmod +x mcp-curl-examples.sh
./mcp-curl-examples.sh
```

### Windows CMD
```cmd
mcp-curl-examples.bat
```

### 自定义 Server 地址和 API Key
```bash
export MCP_SERVER_URL=http://your-server:8081
export MCP_API_KEY=your-api-key
./mcp-curl-examples.sh
```

## API 端点一览

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/mcp/health` | GET | 健康检查 |
| `/api/mcp/info` | GET | Server 信息 |
| `/api/mcp/tools` | GET | 列出所有工具 |
| `/api/mcp/tools/call` | POST | 调用指定工具 |
| `/sse` | GET | SSE 流式连接 |
| `/mcp/message` | POST | JSON-RPC 协议调用 |

## 请求头

| Header | 必填 | 说明 |
|--------|------|------|
| `X-API-Key` | 是 | API Key 认证 |
| `Content-Type` | POST 请求 | `application/json` |