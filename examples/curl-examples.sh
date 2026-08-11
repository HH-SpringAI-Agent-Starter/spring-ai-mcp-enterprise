#!/bin/bash
# ==================================================
# MCP Enterprise Server — curl 调用示例
# ==================================================
# 前提：服务已启动 (mvn spring-boot:run)
# 默认 API Key 在启动日志中，或使用默认值
# ==================================================

BASE_URL="http://localhost:8081"
API_KEY="${MCP_API_KEY:-default-admin-key}"

echo "🚀 MCP Enterprise Server curl 示例"
echo "===================================="
echo ""

# 1️⃣ 健康检查
echo "📡 1. 健康检查"
curl -s "${BASE_URL}/api/mcp/health" | python3 -m json.tool
echo ""

# 2️⃣ 连接服务
echo "🔗 2. 连接服务"
SESSION_RESP=$(curl -s -X POST "${BASE_URL}/api/mcp/connect" \
  -H "X-API-Key: ${API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{"clientName":"curl-demo"}')
echo "${SESSION_RESP}" | python3 -m json.tool
SESSION_ID=$(echo "${SESSION_RESP}" | python3 -c "import sys,json; print(json.load(sys.stdin)['sessionId'])")
echo ""

# 3️⃣ 列出可用工具
echo "🔧 3. 列出可用工具"
curl -s "${BASE_URL}/api/mcp/tools" \
  -H "X-API-Key: ${API_KEY}" | python3 -m json.tool
echo ""

# 4️⃣ 查看工具详情（示例：第一个工具）
echo "📋 4. 查看工具详情"
curl -s "${BASE_URL}/api/mcp/tools/example-tool" \
  -H "X-API-Key: ${API_KEY}" | python3 -m json.tool
echo ""

# 5️⃣ 调用工具
echo "⚡ 5. 调用工具"
curl -s -X POST "${BASE_URL}/api/mcp/tools/example/invoke" \
  -H "X-API-Key: ${API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{"query":"查询用户数量"}' | python3 -m json.tool
echo ""

# 6️⃣ 服务统计
echo "📊 6. 服务统计"
curl -s "${BASE_URL}/api/mcp/stats" \
  -H "X-API-Key: ${API_KEY}" | python3 -m json.tool
echo ""

# 7️⃣ 断开连接
echo "👋 7. 断开连接"
curl -s -X POST "${BASE_URL}/api/mcp/disconnect" \
  -H "X-API-Key: ${API_KEY}" \
  -H "Content-Type: application/json" \
  -d "{\"sessionId\":\"${SESSION_ID}\"}" | python3 -m json.tool
echo ""

echo "✅ curl 示例运行完成!"

echo ""
echo "============================================"
echo "🆕 Streamable HTTP (2026-07-28 规范) 示例"
echo "============================================"
echo ""

# 8️⃣ 能力声明 (v2 无状态)
echo "📡 8. v2 能力声明"
curl -s "${BASE_URL}/api/mcp/v2" | python3 -m json.tool
echo ""

# 9️⃣ v2 健康检查（含 streamable-http 传输信息）
echo "🩺 9. v2 健康检查"
curl -s "${BASE_URL}/api/mcp/v2/health" | python3 -m json.tool
echo ""

# 🔟 初始化 (无状态，无需 session)
echo "🚀 10. 无状态 initialize"
curl -s -X POST "${BASE_URL}/api/mcp/v2/initialize" \
  -H "Content-Type: application/json" \
  -d '{"protocolVersion":"2026-07-28","clientInfo":{"name":"curl-demo"}}' | python3 -m json.tool
echo ""

# 1️⃣1️⃣ 无状态 tools/list
echo "🔧 11. 无状态 tools/list"
curl -s "${BASE_URL}/api/mcp/v2/tools" | python3 -m json.tool
echo ""

# 1️⃣2️⃣ 无状态 tools/call
echo "⚡ 12. 无状态 tools/call"
curl -s -X POST "${BASE_URL}/api/mcp/v2/tools/call" \
  -H "Content-Type: application/json" \
  -d '{"name":"system_info","arguments":{}}' | python3 -m json.tool
echo ""

# 1️⃣3️⃣ Streamable HTTP — GET 事件流（server→client 通知通道）
# 连接后保持 15s 心跳，收到 tools/listChanged 通知后重新拉取 tools/list
echo "📡 13. Streamable HTTP 事件流 (Ctrl+C 退出，建议单独终端运行)"
echo "     curl -N ${BASE_URL}/api/mcp/v2/stream"
echo ""

# 1️⃣4️⃣ 触发 tools/listChanged 广播（需先有流连接）
echo "📢 14. 触发 tools/listChanged 广播"
curl -s -X POST "${BASE_URL}/api/mcp/v2/notify" | python3 -m json.tool
echo ""

echo "✅ Streamable HTTP 示例完成!"

# 15. OAuth2 Client Credentials (machine-to-machine) token
echo "馃攼 15. OAuth2 client-credentials token"
TOKEN_RESP=$(curl -s -X POST "${BASE_URL}/api/auth/oauth2/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=mcp-service&client_secret=change-me-client-secret&scope=tools:read")
echo "$TOKEN_RESP" | python3 -m json.tool
echo ""

# 16. Call MCP tool with Bearer token
echo "馃摗 16. tools/call with Bearer token"
ACCESS_TOKEN=$(echo "$TOKEN_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)[\"access_token\"])" 2>/dev/null)
curl -s -X POST "${BASE_URL}/api/mcp/v2/tools/call" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -d '{"name":"system_info","arguments":{}}' | python3 -m json.tool
echo ""
echo "鉁?Streamable HTTP 绀轰緥瀹屾垚!"