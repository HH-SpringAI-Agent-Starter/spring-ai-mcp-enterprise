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
