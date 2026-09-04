
# ==================================================
# V1.19 工具级 Scope 权限映射演示（Token Scope → Tool ACL，RFC 6750 insufficient_scope）
# 前置：mcp.enterprise.security.scope.enabled=true + enforce-bearer=true
# ==================================================
echo ""
echo "🔐 24. 查看全局 scope 授权矩阵（GET /api/mcp/scope/policy）"
curl -s "${BASE_URL}/api/mcp/scope/policy" | python3 -m json.tool

echo ""
echo "🔐 25. 申请受限令牌（仅 tools:finance:read）"
SCOPE_TOKEN=$(curl -s -X POST "${BASE_URL}/api/auth/oauth2/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=mcp-service&client_secret=change-me-client-secret&scope=tools:finance:read" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")
echo "已取得受限令牌（scope=tools:finance:read）"

echo ""
echo "✅ 26. 调 finance_indicator（scope 命中 tools:finance:read）→ 应成功"
curl -s -X POST "${BASE_URL}/api/mcp/tools/finance_indicator/invoke" \
  -H "Authorization: Bearer ${SCOPE_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"indicator":"cagr","params":{"beginValue":100,"endValue":200,"years":3}}' | python3 -m json.tool

echo ""
echo "⛔ 27. 调 db_query（需 tools:database:*，令牌无）→ 应 HTTP 403 insufficient_scope"
curl -s -i -X POST "${BASE_URL}/api/mcp/tools/db_query/invoke" \
  -H "Authorization: Bearer ${SCOPE_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"sql":"SELECT 1"}' | head -20

echo ""
echo "⛔ 28. Streamable HTTP tools/call 越权 → JSON-RPC -32090 + HTTP 403"
curl -s -i -X POST "${BASE_URL}/api/mcp/v2/tools/call" \
  -H "Authorization: Bearer ${SCOPE_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"name":"db_query","arguments":{"sql":"SELECT 1"}}' | head -20

echo ""
echo "✅ 29. tools/list 暴露 requiredScopes（客户端可在令牌签发前就申请正确 scope）"
curl -s "${BASE_URL}/api/mcp/v2/tools" -H "Authorization: Bearer ${SCOPE_TOKEN}" | python3 -c "
import sys, json
data = json.load(sys.stdin)
for t in data.get('result', {}).get('tools', []):
    print(t.get('name'), '→', t.get('requiredScopes', []))"

echo ""
echo "✔️  V1.19 工具级 Scope 权限映射 演示完成（403 + WWW-Authenticate: insufficient_scope）"
