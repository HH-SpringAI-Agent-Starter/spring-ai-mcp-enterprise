# MCP Federation Gateway 使用指南（V1.25）

> 联邦 MCP 网关：**一个入口，聚合任意多个下游 MCP Server**，工具自动命名空间隔离，
> 并复用本框架全部企业治理能力（RBAC / RateLimit / 审计 / 工具级 Scope / 健康检查 / 统计）。

## 为什么需要联邦网关

| 场景 | 没有联邦网关 | 有联邦网关（V1.25） |
|---|---|---|
| 3 个部门各有 MCP Server | Agent 要配 3 个 client 连接、3 套凭证、3 种权限 | Agent 只连本网关一个入口 |
| 收购遗留系统（还在跑 MCP） | 无法统一治理，风险敞口 | `enabled: false` 一键下线其工具 |
| 要接 Python FastMCP 生态 | 跨语言接入 + 无鉴权/审计 | 网关统一鉴权后透传，审计全记录 |
| 工具名冲突（两个上游都有 `search`） | 冲突/覆盖 | `hr__search` vs `erp__search` 命名空间隔离 |

## 快速开始

### 1. 加依赖

```xml
<dependency>
    <groupId>com.mcp.enterprise</groupId>
    <artifactId>mcp-gateway</artifactId>
    <version>1.1.0</version>
</dependency>
```

### 2. 配置上游

```yaml
mcp:
  enterprise:
    gateway:
      sync-on-startup: true        # 应用启动后自动同步（默认 true）
      upstreams:
        - name: hr                 # 必填，唯一；兼作工具前缀
          url: http://hr-mcp:8080/mcp   # 必填，Streamable HTTP 端点
          api-key: ${HR_MCP_API_KEY:}   # 可选，Bearer 透传
          enabled: true
          request-timeout-ms: 30000
        - name: finance
          url: http://finance-mcp:8080/mcp
        - name: legacy
          url: http://legacy:8080/mcp
          enabled: false           # 被收购系统，先禁掉
```

任何符合 **MCP 2026-07-28 Streamable HTTP** 的服务都可作上游：
- 本项目的 `mcp-server`；
- Python FastMCP (`uvx fastmcp ...` / `fastmcp run server.py --transport streamable-http`)；
- Spring AI 官方 MCP Server 实现；
- 各大 SaaS 的托管 MCP 端点。

### 3. 启动

应用启动后（`sync-on-startup=true`）日志出现：

```
🌐 [V1.25] 启动联邦同步完成: {synced=2, ...}
✅ [V1.25] 上游 hr 同步完成: 0 → 3 个工具
```

此时 `GET /api/mcp/v2/tools`（或标准 `tools/list` 端点）已包含联邦工具：

```json
{ "tools": [ { "name": "hr__get_employee", "category": "federated", ... } ] }
```

Agent 像调用本地工具一样调用 `hr__get_employee`——**鉴权、限流、审计、Scope 全部自动生效**。

### 4. 管理 API

```bash
# 查看所有上游状态（健康/工具数/上次同步）
curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/admin/gateway/upstreams

# 全量刷新（上游发布新工具后手动拉取）
curl -X POST -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/admin/gateway/sync

# 刷新单个上游
curl -X POST http://localhost:8081/api/admin/gateway/upstreams/hr/sync

# 下线上游（移除其全部联邦工具）
curl -X DELETE http://localhost:8081/api/admin/gateway/upstreams/legacy/tools

# 聚合健康视图
curl http://localhost:8081/api/admin/gateway/health
```

> ⚠️ `/api/admin/gateway/*` 与其它 admin 端点一样，必须置于 mcp-auth / 网关鉴权之后。

### 5. 工具调用结果形态

`tools/call` 归一化为与本地工具一致的形态：

```json
// 成功（structuredContent 优先）
{ "success": true, "result": { "empId": 42, "name": "张三" }, "upstream": "hr", "tool": "hr__get_employee" }

// 上游 isError
{ "success": false, "error": "业务校验失败", "upstream": "hr", "tool": "hr__get_employee" }
```

## 架构

```
                    ┌────────────────────────────────────────────┐
  Agent / Client ──►│         本 MCP Server（治理平面）            │
                    │  RBAC · RateLimit · 审计 · Scope · 统计     │
                    │         ┌──────────────────────┐           │
                    │         │   McpToolManager     │           │
                    │         │  (本地 + 联邦工具)     │           │
                    │         └──────────┬───────────┘           │
                    └────────────────────┼───────────────────────┘
                                         │ McpFederationManager
              ┌──────────────────────────┼──────────────────────────┐
              ▼                          ▼                          ▼
     StreamableHttpUpstreamClient  StreamableHttpUpstreamClient  (自定义 Factory)
     (上游: hr)                    (上游: finance)               (私有协议)
              │                          │
       http://hr-mcp:8080/mcp     http://finance-mcp:8080/mcp
       (Java / Python / 任意)      (第三方 SaaS MCP)
```

## 自定义上游协议

实现 SPI 即可替换/扩展传输：

```java
@Component
public class MyFactory implements McpUpstreamClientFactory {
    @Override
    public UpstreamMcpClient create(McpGatewayProperties.Upstream config) {
        return new MyPrivateClient(config);   // 实现 ping/listTools/callTool
    }
}
```

自动配置 `@ConditionalOnMissingBean` 保证你的 Bean 优先于默认 `StreamableHttpUpstreamClient.Factory`。

## 生产建议

1. **prefix 全局唯一**：两个上游用同一个 prefix 会产生工具互相覆盖（`registerExecutor` 语义为覆盖）。建议 prefix = 上游 name，且团队内约定 `xx__` 前缀保留给联邦工具；
2. **上游加 `api-key`**：内部上游也建议启用鉴权，网关凭据用环境变量注入（`${HR_MCP_API_KEY:}`）；
3. **同步失败不摘工具**：V1.25 语义为「失败保留、标记 unhealthy」，配合 V1.24 Grafana 看板的 `McpTrafficDrop` 告警使用；
4. **下线流程**：先 `POST .../sync` 确认新版本健康，再 `DELETE .../tools` 下线退役上游；
5. **超时对齐**：`request-timeout-ms` 会写入联邦工具定义的 `timeoutMs`，下游慢工具请调大，避免网关侧超时截断。

## 完整示例（docker compose 双上游）

1. 起一个 Python FastMCP 上游：`fastmcp run echo_server.py --transport streamable-http --port 9000`
2. 起本项目 `mcp-server`（自身也是合法上游）
3. 在 application.yml 配置两个 `upstreams`（`echo` 指向 :9000，`self` 指向本服务）
4. 重启后 `GET /api/admin/gateway/upstreams` 即可看到两个上游聚合