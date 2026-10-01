#!/bin/bash
# ============================================================
# MCP Enterprise Server — curl 客户端调用示例
# 适用于 Linux / macOS / Windows Git Bash
# ============================================================
#
# 使用前提：
#   MCP Enterprise Server 已在 localhost:8081 启动
#
# 使用方式：
#   chmod +x mcp-curl-examples.sh
#   ./mcp-curl-examples.sh
# ============================================================

set -euo pipefail

BASE_URL="${MCP_SERVER_URL:-http://localhost:8081}"
API_KEY="${MCP_API_KEY:-default-admin-key}"

echo "============================================"
echo "  MCP Enterprise curl 客户端调用示例"
echo "  Server: $BASE_URL"
echo "============================================"

# ---- 1. 健康检查 ----
echo -e "\n📡 1. 健康检查"
curl -s "$BASE_URL/api/mcp/health" | python3 -m json.tool 2>/dev/null || \
  curl -s "$BASE_URL/api/mcp/health"

# ---- 2. 获取 API 信息 ----
echo -e "\n\n📋 2. API 信息"
curl -s "$BASE_URL/api/mcp/info" | python3 -m json.tool 2>/dev/null || \
  curl -s "$BASE_URL/api/mcp/info"

# ---- 3. 列出所有已注册工具 ----
echo -e "\n\n🔧 3. 列出所有 MCP 工具"
curl -s -H "X-API-Key: $API_KEY" \
     "$BASE_URL/api/mcp/tools" | python3 -m json.tool 2>/dev/null || \
  curl -s -H "X-API-Key: $API_KEY" "$BASE_URL/api/mcp/tools"

# ---- 4. 调用工具示例：计算器 ----
echo -e "\n\n🧮 4. 调用计算器工具"
curl -s -X POST \
     -H "Content-Type: application/json" \
     -H "X-API-Key: $API_KEY" \
     "$BASE_URL/api/mcp/tools/call" \
     -d '{
       "toolName": "calculator",
       "arguments": {
         "expression": "2 + 3 * 4"
       }
     }' | python3 -m json.tool 2>/dev/null || \
  curl -s -X POST \
     -H "Content-Type: application/json" \
     -H "X-API-Key: $API_KEY" \
     "$BASE_URL/api/mcp/tools/call" \
     -d '{"toolName":"calculator","arguments":{"expression":"2 + 3 * 4"}}'

# ---- 5. 调用工具示例：天气查询 ----
echo -e "\n\n🌤️  5. 调用天气查询工具"
curl -s -X POST \
     -H "Content-Type: application/json" \
     -H "X-API-Key: $API_KEY" \
     "$BASE_URL/api/mcp/tools/call" \
     -d '{
       "toolName": "weather",
       "arguments": {
         "city": "上海"
       }
     }' | python3 -m json.tool 2>/dev/null || \
  curl -s -X POST \
     -H "Content-Type: application/json" \
     -H "X-API-Key: $API_KEY" \
     "$BASE_URL/api/mcp/tools/call" \
     -d '{"toolName":"weather","arguments":{"city":"上海"}}'

# ---- 6. SSE 流式连接（MCP 标准协议） ----
echo -e "\n\n🌊 6. SSE 流式连接（Ctrl+C 退出）"
echo "    连接到: $BASE_URL/sse"
echo "    按 Ctrl+C 断开"
echo ""
curl -s -N -H "X-API-Key: $API_KEY" \
     "$BASE_URL/sse" &
SSE_PID=$!
sleep 5
kill $SSE_PID 2>/dev/null || true
echo "    [SSE 连接已演示]"

# ---- 7. JSON-RPC 调用（MCP 标准协议） ----
echo -e "\n\n📨 7. JSON-RPC 协议调用（MCP 标准）"
curl -s -X POST \
     -H "Content-Type: application/json" \
     -H "X-API-Key: $API_KEY" \
     "$BASE_URL/mcp/message" \
     -d '{
       "jsonrpc": "2.0",
       "id": 1,
       "method": "tools/list",
       "params": {}
     }' | python3 -m json.tool 2>/dev/null || \
  curl -s -X POST \
     -H "Content-Type: application/json" \
     -H "X-API-Key: $API_KEY" \
     "$BASE_URL/mcp/message" \
     -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'

echo -e "\n\n✅ curl 示例完成！"
echo "更多 API 端点请参考: docs/api-docs.md"