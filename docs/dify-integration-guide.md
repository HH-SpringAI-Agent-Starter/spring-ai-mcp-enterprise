# Dify 集成指南 — Spring AI MCP Enterprise

> 版本：V1.10 候选 | 日期：2026-08-25
> 目标：把本框架的 MCP Server 接入 [Dify](https://dify.ai) 工作流，让 Dify 编排的 Agent 直接调用企业数据库查询 / 搜索 / 系统监控等工具。

## 为什么集成 Dify

- Dify 是国内最流行的 LLMOps 平台（GitHub 90k+ stars），企业 Agent 编排事实标准之一；
- Dify 原生支持 **MCP Server 工具市场**（Agent → 工具 → MCP），可在可视化工作流里直接挂载远程 MCP Server；
- 本项目 = 企业级 MCP Server（RBAC/OAuth2/审计/限流），Dify = 可视化 Agent 编排层，**两者互补**，是「企业 MCP 平台」报价单（$40-90K 档）的标准交付组合。

## 前置条件

1. 已按 [快速上手指南](quickstart.md) 启动 MCP Enterprise Server（默认端口 8081，MCP 端点 `/api/mcp/message`）；
2. Dify 版本 ≥ 1.2（支持 Streamable HTTP 类型 MCP 工具）；
3. 已创建 API Key 或 OAuth2 客户端（参考 [oauth2-guide.md](oauth2-guide.md)）。

## 方式一：Dify 工作流挂载 MCP Server（推荐）

### 步骤 1：获取 Server 地址与凭证

```bash
# Server 启动后验证 MCP 端点
curl -s http://localhost:8081/actuator/health
# {"status":"UP",...}

# 创建 API Key（如无）
curl -s -X POST http://localhost:8081/api/v1/keys \
  -H "Authorization: Bearer <admin-token>" \
  -d '{"name":"dify-agent"}'
```

### 步骤 2：Dify 中添加 MCP 工具

1. 进入 Dify → **工具** → **自定义工具** → **添加 MCP 工具**；
2. 类型选择 **Streamable HTTP**；
3. 填写：
   - **Server URL**: `http://<your-host>:8081/api/mcp/message`
   - **Headers**:
     ```json
     {
       "Authorization": "Bearer <你的 MCP_API_KEY 或 OAuth2 access_token>",
       "X-MCP-Enterprise-Client": "dify-workflow"
     }
     ```
   - **Transport**: `streamable-http`（若 Server 配置了 SSE 兼容端点，也可选 SSE）
4. 点击「获取工具列表」，框架会通过 MCP `tools/list` 自动返回已注册工具（`database_query` / `search_web` / `execute_command` / `get_weather` / finance 等）；
5. 勾选需要的工具，保存。

### 步骤 3：在 Agent 节点中使用

```
[开始] → [Agent(工具=数据库查询+网络搜索)] → [结束]
```

Agent 节点的 System Prompt 示例：

```text
你是一个企业数据分析助手。当用户询问数据库相关问题时，调用 database_query 工具；
当需要最新资讯时，调用 search_web 工具。所有查询必须只读。
```

## 方式二：Dify 插件市场直接安装（企业私有化）

Dify 支持通过 Plugin 方式安装自定义 MCP 供应商。将本框架打包为 Dify Plugin：

1. 在 Dify 后台 → **插件** → **自定义插件** → **创建**；
2. 插件类型选择 `mcp`，填写 Server URL 与鉴权头（同上）；
3. 发布到团队插件市场，供多个工作流复用。

## 生产环境注意事项

| 事项 | 说明 |
| --- | --- |
| 网络连通 | Dify 与 MCP Server 需网络可达；跨 K8s 命名空间建议走 Service DNS |
| 凭证管理 | 推荐 OAuth2 Client Credentials + 短期 access_token（参考 oauth2-guide.md），避免长期 API Key 泄露 |
| 审计 | 所有 Dify 发起的工具调用都会写入审计日志，`mcp.enterprise.audit.include-body=true` 可记录请求体 |
| 限流 | 为 Dify 的 client_id 配置独立限流策略，避免 Agent 循环调用打爆后端 |
| 只读约束 | database_query 默认仅允许 SELECT/WITH，Dify 工作流无法绕过（服务端强制） |

## 配置样例（mcp-examples/dify）

在 `mcp-examples/dify/` 提供：

- `dify-mcp-tool.json` — Dify 自定义工具导入模板
- `workflow-export.yml` — 示例工作流（Agent + 数据库查询 + 搜索）

## 相关文档

- [快速上手指南](quickstart.md)
- [OAuth2 企业授权指南](oauth2-guide.md)
- [架构说明](architecture.md)
- [API 文档](api-docs.md)
