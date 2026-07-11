"""
MCP Enterprise Server — Python 客户端示例

演示 Python 应用如何通过 REST API 调用 MCP Enterprise Server。
无需额外依赖，使用标准库 urllib。

使用方式：
    python mcp_client.py
    或
    MCP_API_KEY=my-key python mcp_client.py
"""

import json
import os
import sys
import urllib.request
import urllib.error

try:
    import ssl
    ctx = ssl.create_default_context()
except ImportError:
    ctx = None


class McpEnterpriseClient:
    """MCP Enterprise Server Python 客户端"""

    def __init__(self, base_url: str = "http://localhost:8081", api_key: str = None):
        self.base_url = base_url.rstrip("/")
        self.api_key = api_key or os.environ.get("MCP_API_KEY", "default-admin-key")
        self.session_id = None

    def _request(self, method: str, path: str, body: dict = None,
                  with_api_key: bool = True) -> dict:
        """通用 HTTP 请求"""
        url = f"{self.base_url}{path}"
        headers = {"Content-Type": "application/json"}
        if with_api_key:
            headers["X-API-Key"] = self.api_key

        data = json.dumps(body).encode("utf-8") if body else None
        req = urllib.request.Request(url, data=data, headers=headers, method=method)

        try:
            with urllib.request.urlopen(req, context=ctx, timeout=30) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            return {"error": f"HTTP {e.code}: {e.read().decode()}"}
        except Exception as e:
            return {"error": str(e)}

    def health(self) -> dict:
        """健康检查"""
        return self._request("GET", "/api/mcp/health")

    def connect(self, client_name: str = "python-demo") -> dict:
        """连接 MCP Server"""
        result = self._request("POST", "/api/mcp/connect",
                                body={"clientName": client_name})
        if result.get("success"):
            self.session_id = result.get("sessionId")
        return result

    def disconnect(self) -> dict:
        """断开连接"""
        if not self.session_id:
            return {"error": "No active session"}
        result = self._request("POST", "/api/mcp/disconnect",
                                body={"sessionId": self.session_id})
        if result.get("success"):
            self.session_id = None
        return result

    def list_tools(self) -> dict:
        """列出所有可用工具"""
        return self._request("GET", "/api/mcp/tools")

    def get_tool(self, name: str) -> dict:
        """获取工具详情"""
        return self._request("GET", f"/api/mcp/tools/{name}")

    def invoke_tool(self, name: str, params: dict) -> dict:
        """调用工具"""
        return self._request("POST", f"/api/mcp/tools/{name}/invoke",
                              body=params)

    def stats(self) -> dict:
        """服务统计"""
        return self._request("GET", "/api/mcp/stats")

    def __enter__(self):
        return self

    def __exit__(self, *args):
        if self.session_id:
            self.disconnect()


def main():
    print("🚀 MCP Enterprise Python Client Demo")
    print("=" * 45)

    with McpEnterpriseClient() as client:
        # 1. 健康检查
        print("\n📡 1. 健康检查...")
        health = client.health()
        print(f"   状态: {health.get('status')}")
        print(f"   工具数: {health.get('toolCount')}")
        print(f"   活跃会话: {health.get('activeSessions')}")

        # 2. 连接服务
        print("\n🔗 2. 连接服务...")
        conn = client.connect()
        print(f"   Session ID: {conn.get('sessionId')}")
        print(f"   服务版本: {conn.get('serverVersion')}")

        # 3. 列出工具
        print("\n🔧 3. 可用工具列表...")
        tools = client.list_tools()
        print(f"   总工具数: {tools.get('total', 0)}")
        for tool in tools.get("tools", []):
            print(f"   - {tool.get('name')} ({tool.get('displayName')}) "
                  f"[{tool.get('category')}]")

        # 4. 查看统计
        print("\n📊 4. 服务统计...")
        stats = client.stats()
        print(f"   工具总数: {stats.get('tools', {}).get('total')}")
        print(f"   活跃会话: {stats.get('sessions', {}).get('active')}")

        # 5. 断开连接
        print("\n👋 5. 断开连接...")
        disc = client.disconnect()
        print(f"   断开成功: {disc.get('success')}")

    print("\n✅ Python 示例运行完成!")


if __name__ == "__main__":
    main()
