#!/usr/bin/env python3
"""
MCP Enterprise Server — Python 客户端示例

适用于：
- 非 Java 用户快速接入 MCP Enterprise Server
- 数据科学家 / AI 工程师用 Python 调用 MCP 工具
- 自动化脚本和 Agent 编排

使用前提：
  MCP Enterprise Server 已在 localhost:8081 启动

安装依赖：
  pip install requests sseclient-py

使用方式：
  python mcp_client.py
"""

import json
import os
import sys
from typing import Any, Optional

try:
    import requests
except ImportError:
    print("❌ 请先安装 requests: pip install requests")
    sys.exit(1)


class McpEnterpriseClient:
    """MCP Enterprise Server Python 客户端"""

    def __init__(
        self,
        base_url: str = "http://localhost:8081",
        api_key: str = "default-admin-key",
        timeout: int = 30,
    ):
        self.base_url = base_url.rstrip("/")
        self.api_key = api_key
        self.timeout = timeout
        self.session = requests.Session()
        self.session.headers.update(
            {
                "X-API-Key": self.api_key,
                "Content-Type": "application/json",
                "Accept": "application/json",
            }
        )

    def health(self) -> dict:
        """健康检查"""
        resp = self.session.get(
            f"{self.base_url}/api/mcp/health", timeout=self.timeout
        )
        resp.raise_for_status()
        return resp.json()

    def info(self) -> dict:
        """获取 Server 信息"""
        resp = self.session.get(
            f"{self.base_url}/api/mcp/info", timeout=self.timeout
        )
        resp.raise_for_status()
        return resp.json()

    def list_tools(self) -> list[dict]:
        """列出所有已注册的 MCP 工具"""
        resp = self.session.get(
            f"{self.base_url}/api/mcp/tools", timeout=self.timeout
        )
        resp.raise_for_status()
        return resp.json()

    def call_tool(
        self, tool_name: str, arguments: Optional[dict[str, Any]] = None
    ) -> dict:
        """
        调用指定的 MCP 工具

        Args:
            tool_name: 工具名称（如 "calculator", "weather"）
            arguments: 工具参数字典

        Returns:
            工具调用结果
        """
        payload = {"toolName": tool_name, "arguments": arguments or {}}
        resp = self.session.post(
            f"{self.base_url}/api/mcp/tools/call",
            json=payload,
            timeout=self.timeout,
        )
        resp.raise_for_status()
        return resp.json()

    def jsonrpc_call(self, method: str, params: Optional[dict] = None) -> dict:
        """
        通过 JSON-RPC 2.0 协议调用（MCP 标准协议）

        Args:
            method: MCP 方法名（如 "tools/list", "tools/call"）
            params: 方法参数

        Returns:
            JSON-RPC 响应
        """
        payload = {
            "jsonrpc": "2.0",
            "id": 1,
            "method": method,
            "params": params or {},
        }
        resp = self.session.post(
            f"{self.base_url}/mcp/message",
            json=payload,
            timeout=self.timeout,
        )
        resp.raise_for_status()
        return resp.json()

    def sse_connect(self, duration: int = 10) -> None:
        """
        连接 SSE 流式端点（MCP 标准传输层）

        Args:
            duration: 监听时长（秒）
        """
        try:
            import sseclient
        except ImportError:
            print("❌ SSE 需要 sseclient-py: pip install sseclient-py")
            return

        print(f"🌊 连接 SSE 端点: {self.base_url}/sse")
        print(f"   监听 {duration} 秒...")
        resp = self.session.get(
            f"{self.base_url}/sse", stream=True, timeout=duration + 5
        )
        client = sseclient.SSEClient(resp)
        import time

        start = time.time()
        for event in client.events():
            elapsed = time.time() - start
            if elapsed > duration:
                break
            print(f"   [{event.event}] {event.data}")
        print("   [SSE 连接结束]")


def main():
    """演示完整调用流程"""
    # 从环境变量读取配置
    base_url = os.environ.get("MCP_SERVER_URL", "http://localhost:8081")
    api_key = os.environ.get("MCP_API_KEY", "default-admin-key")

    print("=" * 50)
    print("  MCP Enterprise Python 客户端示例")
    print(f"  Server: {base_url}")
    print("=" * 50)

    client = McpEnterpriseClient(base_url=base_url, api_key=api_key)

    # 1. 健康检查
    print("\n📡 Step 1: 健康检查")
    try:
        health = client.health()
        print(f"   状态: {health.get('status')}")
        print(f"   工具数: {health.get('toolCount')}")
        print(f"   活跃会话: {health.get('activeSessions')}")
    except Exception as e:
        print(f"   ❌ 无法连接到 MCP Server: {e}")
        print("   请确保 MCP Enterprise Server 已在 localhost:8081 启动")
        return

    # 2. 获取 Server 信息
    print("\n📋 Step 2: Server 信息")
    try:
        info = client.info()
        print(f"   名称: {info.get('name')}")
        print(f"   版本: {info.get('version')}")
    except Exception as e:
        print(f"   ⚠️ 获取信息失败: {e}")

    # 3. 列出所有工具
    print("\n🔧 Step 3: 列出所有 MCP 工具")
    try:
        tools = client.list_tools()
        for tool in tools:
            name = tool.get("name", "unknown")
            desc = tool.get("description", "")[:60]
            print(f"   • {name}: {desc}")
        print(f"   共 {len(tools)} 个工具")
    except Exception as e:
        print(f"   ⚠️ 获取工具列表失败: {e}")

    # 4. 调用计算器工具
    print("\n🧮 Step 4: 调用计算器工具")
    try:
        result = client.call_tool("calculator", {"expression": "2 + 3 * 4"})
        print(f"   表达式: 2 + 3 * 4")
        print(f"   结果: {result}")
    except Exception as e:
        print(f"   ⚠️ 调用失败: {e}")

    # 5. 调用天气查询工具
    print("\n🌤️  Step 5: 调用天气查询工具")
    try:
        result = client.call_tool("weather", {"city": "上海"})
        print(f"   城市: 上海")
        print(f"   结果: {json.dumps(result, ensure_ascii=False, indent=2)}")
    except Exception as e:
        print(f"   ⚠️ 调用失败: {e}")

    # 6. JSON-RPC 协议调用
    print("\n📨 Step 6: JSON-RPC 协议调用")
    try:
        result = client.jsonrpc_call("tools/list")
        tool_count = len(result.get("result", {}).get("tools", []))
        print(f"   JSON-RPC 返回 {tool_count} 个工具")
    except Exception as e:
        print(f"   ⚠️ 调用失败: {e}")

    # 7. SSE 流式连接（可选）
    if "--sse" in sys.argv:
        print("\n🌊 Step 7: SSE 流式连接")
        client.sse_connect(duration=5)

    print("\n✅ Python 客户端示例完成！")
    print("更多 API 端点请参考: docs/api-docs.md")


if __name__ == "__main__":
    main()