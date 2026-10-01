@echo off
REM ============================================================
REM MCP Enterprise Server — Windows curl 客户端调用示例
REM ============================================================
REM
REM 使用前提：
REM   MCP Enterprise Server 已在 localhost:8081 启动
REM
REM 使用方式：
REM   mcp-curl-examples.bat
REM ============================================================

setlocal

set BASE_URL=%MCP_SERVER_URL%
if "%BASE_URL%"=="" set BASE_URL=http://localhost:8081

set API_KEY=%MCP_API_KEY%
if "%API_KEY%"=="" set API_KEY=default-admin-key

echo ============================================
echo   MCP Enterprise curl 客户端调用示例
echo   Server: %BASE_URL%
echo ============================================

echo.
echo [1] 健康检查
curl -s "%BASE_URL%/api/mcp/health"

echo.
echo.
echo [2] API 信息
curl -s "%BASE_URL%/api/mcp/info"

echo.
echo.
echo [3] 列出所有 MCP 工具
curl -s -H "X-API-Key: %API_KEY%" "%BASE_URL%/api/mcp/tools"

echo.
echo.
echo [4] 调用计算器工具
curl -s -X POST -H "Content-Type: application/json" -H "X-API-Key: %API_KEY%" "%BASE_URL%/api/mcp/tools/call" -d "{\"toolName\":\"calculator\",\"arguments\":{\"expression\":\"2 + 3 * 4\"}}"

echo.
echo.
echo [5] 调用天气查询工具
curl -s -X POST -H "Content-Type: application/json" -H "X-API-Key: %API_KEY%" "%BASE_URL%/api/mcp/tools/call" -d "{\"toolName\":\"weather\",\"arguments\":{\"city\":\"上海\"}}"

echo.
echo.
echo [6] JSON-RPC 协议调用（MCP 标准）
curl -s -X POST -H "Content-Type: application/json" -H "X-API-Key: %API_KEY%" "%BASE_URL%/mcp/message" -d "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}"

echo.
echo.
echo ✅ curl 示例完成！
echo 更多 API 端点请参考: docs/api-docs.md

endlocal