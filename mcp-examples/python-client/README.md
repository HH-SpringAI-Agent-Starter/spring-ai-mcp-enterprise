# MCP Enterprise Python 客户端示例

## 概述

Python 客户端示例，方便非 Java 用户快速接入 MCP Enterprise Server。

适用于：
- 数据科学家 / AI 工程师用 Python 调用 MCP 工具
- 自动化脚本和 Agent 编排
- 快速验证 Server 功能

## 安装依赖

```bash
pip install requests

# 可选：SSE 流式连接支持
pip install sseclient-py
```

## 使用方法

### 基本使用
```bash
# 确保 MCP Enterprise Server 已启动
python mcp_client.py
```

### 自定义配置
```bash
export MCP_SERVER_URL=http://your-server:8081
export MCP_API_KEY=your-api-key
python mcp_client.py
```

### 启用 SSE 流式连接
```bash
python mcp_client.py --sse
```

### 在代码中使用

```python
from mcp_client import McpEnterpriseClient

# 创建客户端
client = McpEnterpriseClient(
    base_url="http://localhost:8081",
    api_key="your-api-key"
)

# 健康检查
health = client.health()
print(health)

# 列出所有工具
tools = client.list_tools()

# 调用工具
result = client.call_tool("calculator", {"expression": "1 + 1"})
print(result)

# JSON-RPC 协议调用
result = client.jsonrpc_call("tools/list")
print(result)
```

## API 说明

| 方法 | 说明 |
|------|------|
| `health()` | 健康检查 |
| `info()` | 获取 Server 信息 |
| `list_tools()` | 列出所有已注册工具 |
| `call_tool(name, args)` | 调用指定工具 |
| `jsonrpc_call(method, params)` | JSON-RPC 协议调用 |
| `sse_connect(duration)` | SSE 流式连接 |

## 与 LangChain / LlamaIndex 集成

```python
# LangChain 示例：将 MCP 工具包装为 LangChain Tool
from langchain.tools import StructuredTool
from mcp_client import McpEnterpriseClient

client = McpEnterpriseClient()

def create_mcp_tool(tool_name: str, description: str):
    def _run(**kwargs):
        result = client.call_tool(tool_name, kwargs)
        return str(result)
    return StructuredTool.from_function(
        coroutine=_run,
        name=tool_name,
        description=description,
    )

# 注册为 LangChain 工具
tools = client.list_tools()
lc_tools = [
    create_mcp_tool(t["name"], t.get("description", ""))
    for t in tools
]
```