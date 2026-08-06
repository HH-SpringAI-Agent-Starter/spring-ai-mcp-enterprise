#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
MCP Enterprise Server — Streamable HTTP 客户端示例 (Python)

演示 2026-07-28 无状态协议 + Streamable HTTP 双通道调用：
  POST /api/mcp/v2/message — JSON-RPC 请求（initialize / tools/list / tools/call）
  GET  /api/mcp/v2/stream  — server→client 事件流（endpoint 事件 + listChanged 通知）
  POST /api/mcp/v2/notify  — 触发 tools/listChanged 广播

零依赖，仅用标准库 urllib。

使用方式:
    python mcp_streamable_client.py
    或
    MCP_API_KEY=my-key python mcp_streamable_client.py
"""

import json
import os
import sys
import time
import urllib.error
import urllib.request

BASE_URL = os.environ.get("MCP_BASE_URL", "http://localhost:8081").rstrip("/")
API_KEY = os.environ.get("MCP_API_KEY", "default-admin-key")
PROTOCOL_VERSION = "2026-07-28"
STREAM_READ_SECONDS = 3  # 事件流读取时长（秒），收到 listChanged 后提前结束


def _request(method: str, path: str, body: dict = None) -> dict:
    """通用 HTTP JSON 请求"""
    url = f"{BASE_URL}{path}"
    headers = {"Content-Type": "application/json", "X-API-Key": API_KEY}
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return {"error": f"HTTP {e.code}: {e.read().decode()}"}
    except Exception as e:  # noqa: BLE001
        return {"error": str(e)}


def rpc(msg_id: str, method: str, params: dict) -> dict:
    """发送 MCP JSON-RPC 消息"""
    return _request("POST", "/api/mcp/v2/message",
                    {"jsonrpc": "2.0", "id": msg_id, "method": method, "params": params})


def demo_event_stream() -> None:
    """事件流演示：打开 GET /stream，触发 notify，观察 listChanged 通知"""
    print("   打开事件流 (GET /api/mcp/v2/stream)...")
    url = f"{BASE_URL}/api/mcp/v2/stream"
    req = urllib.request.Request(url, headers={"Accept": "text/event-stream", "X-API-Key": API_KEY})

    try:
        resp = urllib.request.urlopen(req, timeout=30)
        print(f"   ✅ 事件流已连接 (HTTP {resp.status})")

        # 触发 notify 广播（流已建立，server 会投递给本连接）
        print("   触发 notify 广播...")
        notify = _request("POST", "/api/mcp/v2/notify", {})
        print(f"   广播投递: {notify.get('delivered')} 个连接")

        # 读取 SSE 行，直到收到 listChanged 或超时
        current_event = ""
        deadline = time.time() + STREAM_READ_SECONDS
        got_list_changed = False
        while time.time() < deadline:
            line = resp.readline().decode("utf-8", errors="replace").strip()
            if not line:
                continue
            if line.startswith("event:"):
                current_event = line[6:].strip()
            elif line.startswith("data:"):
                data = line[5:].strip()
                print(f"   事件 [{current_event}]: {data}")
                if "list_changed" in current_event:
                    got_list_changed = True
                    break
        if not got_list_changed:
            print("   ⏰ 超时未收到 listChanged 事件（可能无连接投递）")
        resp.close()
        print("   📴 事件流已关闭")
    except Exception as e:  # noqa: BLE001
        print(f"   ⚠️ 事件流异常: {e}")


def main() -> None:
    print("🚀 MCP Enterprise Streamable HTTP Client (Python)")
    print("=" * 50)

    # 1. 协议能力声明
    print("\n📡 1. 拉取协议能力声明 (GET /api/mcp/v2)...")
    caps = _request("GET", "/api/mcp/v2")
    print(f"   protocolVersion: {caps.get('protocolVersion')}")
    transports = caps.get("transport", {}).get("supportedTransports", [])
    print(f"   transports: {', '.join(transports)}")
    print(f"   streamableHttp 通道: {caps.get('transport', {}).get('streamableHttp', {}).get('message')}")

    # 2. initialize（无状态，携带协议版本）
    print("\n🔌 2. initialize (POST /api/mcp/v2/message)...")
    init = rpc("init-1", "initialize", {
        "protocolVersion": PROTOCOL_VERSION,
        "clientInfo": {"name": "python-streamable-demo", "version": "1.0.0"},
    })
    server_info = init.get("result", {}).get("serverInfo", {})
    print(f"   server: {server_info.get('name')} v{server_info.get('version')}")

    # 3. tools/list
    print("\n🔧 3. tools/list...")
    tools = rpc("tools-1", "tools/list", {}).get("result", {}).get("tools", [])
    print(f"   工具数: {len(tools)}")
    for t in tools:
        print(f"   - {t.get('name')} ({t.get('description')})")

    # 4. tools/call — 调用计算器
    print("\n⚡ 4. tools/call calculator...")
    call = rpc("call-1", "tools/call", {
        "name": "calculator",
        "arguments": {"expression": "1 + 2 * 3"},
    })
    content = call.get("result", {}).get("content", [])
    if content:
        print(f"   计算结果: {content[0].get('text')}")
    else:
        print(f"   响应: {json.dumps(call, ensure_ascii=False)}")

    # 5. 事件流演示
    print("\n🔔 5. Streamable HTTP 事件流演示...")
    demo_event_stream()

    print("\n✅ Python Streamable HTTP 示例运行完成!")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        sys.exit(130)
