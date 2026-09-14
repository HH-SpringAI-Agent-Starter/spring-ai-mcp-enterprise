# 用 Java 做一个「联邦 MCP 网关」：把公司里所有的 MCP Server 收编成一个入口

> 标题备选：企业 MCP 落地必读：聚合多个 MCP Server 的联邦网关怎么做（Java/Spring Boot）
> 发布平台：掘金 / CSDN / SegmentFault
> 配套开源项目：https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise （V1.25 新增 mcp-gateway）

## 先说结论

2026 年的企业 AI 落地，MCP（Model Context Protocol）已经从「要不要上」变成「怎么治理」。而当公司里不止一个 MCP Server 时——HR 部门一个、财务一个、被收购的遗留系统还在跑一个——**第一个真正的问题不是写工具，而是怎么把所有这些 Server 收编成一个受治理的入口**。这就是联邦 MCP 网关（MCP Federation Gateway）。

本周美国市场的两则消息可以佐证这个判断：

- **Sumo Logic**（$207K–243K 岗位）JD 原文：「federating with external and third-party MCP servers」——联邦式 MCP Server 托管已经是平台岗的硬性要求；
- **MintMCP**（Cowboy Ventures/Coatue 背书，Coursera/Arlo/Braze 在用）——整家公司就是做 MCP Gateway 的。

而 Java 生态里，这样的网关几乎没有现成实现。本文用一份可运行的 Spring Boot 代码，讲清楚联邦网关要解决的四件事：**聚合、隔离、治理、运维**。

## 一、没有联邦网关，企业 MCP 会变成什么

假设公司有 3 个 MCP Server：

| Server | 提供工具 | 技术栈 |
|---|---|---|
| HR 系统 | `get_employee` / `list_team` | Spring Boot（自家） |
| 财务系统 | `query_invoice` / `run_risk_check` | Python FastMCP |
| 收购的遗留系统 | `legacy_search` | 旧协议，不敢动 |

没有网关时：

- Agent 客户端要**分别配置 3 个连接、3 套凭证、3 套权限**；
- 每个上游都做一遍鉴权、限流、审计——重复建设，且口径不一；
- 遗留系统的工具直接暴露给 Agent，**审计和权限都是空白的**；
- 两个上游都叫 `search`，工具名冲突，谁覆盖谁全看启动顺序。

这就是企业级 AI 架构里最常见的「MCP 熵增」。

## 二、联邦网关的四个设计决策

### 决策 1：聚合——联邦工具就是「一等公民」

网关不做特殊转发层，而是把上游工具**包装成本地工具执行器**，注册进同一个 `McpToolManager`：

```java
public class FederatedToolExecutor implements McpToolExecutor {
    @Override
    public ToolDefinition getDefinition() { /* 本地定义，元数据带 upstream/remoteName */ }
    @Override
    public Mono<Map<String, Object>> execute(Map<String, Object> params) {
        return client.callTool(remoteName, params);  // 委托上游
    }
}
```

关键收益：联邦工具自动继承网关侧**全部治理能力**——RBAC 角色校验、RateLimit、审计日志、工具级 Scope 授权、健康检查、调用统计。对 Agent 来说它就是本地工具，完全透明。

### 决策 2：隔离——命名空间前缀

本地工具名 = `<prefix>__<remoteToolName>`：

```
hr__get_employee      # 来自 HR 上游
finance__query_invoice # 来自财务上游
legacy__search        # 来自遗留系统（可整体下线）
```

一套规则解决冲突、可追溯、可批量管理。

### 决策 3：治理——失败保留、禁用移除

- **同步失败**：保留已注册工具、标记 unhealthy——可用性优先，不因上游抖动清空服务面；
- **禁用上游**：`enabled: false` 或 `DELETE /tools`，一键摘除其全部工具——并购整合的「下线控制面」就是这么简单；
- **重启恢复**：`sync-on-startup` 启动时全量同步。

### 决策 4：运维——管理 API + 状态可见

```
GET    /api/admin/gateway/upstreams               # 上游健康/工具数/上次同步
POST   /api/admin/gateway/sync                    # 全量刷新
POST   /api/admin/gateway/upstreams/{name}/sync   # 单上游刷新
DELETE /api/admin/gateway/upstreams/{name}/tools  # 下线上游
GET    /api/admin/gateway/health                  # 聚合健康
```

配合 Prometheus 指标和 Grafana 看板，一个上游挂没挂、拉没拉到工具、被调了多少次，全部可见。

## 三、零依赖的 Streamable HTTP 客户端

网关连接下游用的是 MCP 2026-07-28 的 **Streamable HTTP** 传输（JSON-RPC over HTTP），纯 JDK `HttpClient` 实现，不引任何额外依赖：

```java
// 每次方法调用前自动完成握手序列
initialize → notifications/initialized → tools/list | tools/call
```

响应处理同时兼容两种形态：

- 单 JSON：`{jsonrpc, id, result}`；
- SSE 流：`text/event-stream` 的 `data:` 行（部分实现 tools/call 用 SSE 返回）。

`tools/call` 结果归一化：`structuredContent` 优先，`isError=true` 映射为 `success=false`，text content 兜底——下游是 Java、Python 还是 SaaS，到网关这一层全部统一。

## 四、配置即联邦

```yaml
mcp:
  enterprise:
    gateway:
      sync-on-startup: true
      upstreams:
        - name: hr          # 工具前缀 = hr
          url: http://hr-mcp:8080/mcp
          api-key: ${HR_MCP_API_KEY:}
        - name: finance
          url: http://finance-mcp:8080/mcp
        - name: legacy
          url: http://legacy:8080/mcp
          enabled: false    # 遗留系统先禁掉
```

重启后：

```
✅ [V1.25] 上游 hr 同步完成: 0 → 3 个工具
✅ [V1.25] 上游 finance 同步完成: 0 → 5 个工具
```

Agent 只连网关一个入口，调用 `hr__get_employee` 和调用本地 `weather` 没有任何区别——但权限、限流、审计全在。

## 五、为什么是 Java 做这件事

MCP 生态 Python 占 80%+，但企业后端的现实是 **90% 是 Java/Spring**。用 Java 实现网关意味着：

1. **无缝嵌入**：不需要给企业引入 Python 运行时，网关就是一个普通 Spring Boot 应用；
2. **一份代码覆盖治理全家桶**：RBAC / 限流 / 审计 / OAuth2 / 多租户 / 可观测性 / 联邦，全部在同一代码库内闭环；
3. **异构上游照单全收**：Java 网关 + Python FastMCP 上游 + 第三方 SaaS MCP，网关层统一治理——这正是「企业统一 Agent 入口」的形态。

## 六、落地路径

1. **起步**：网关 + 1 个自家 Server 上游（30 分钟）；
2. **扩展**：接入 Python FastMCP 上游、第三方 SaaS MCP；
3. **整合**：被收购系统 `enabled: false` 或 `DELETE /tools`；
4. **治理**：配合 Grafana 告警（工具错误率/延迟/流量骤降）形成运营闭环。

## 参考实现

本文代码已开源：**spring-ai-mcp-enterprise**（V1.25 `mcp-gateway` 模块，22 个测试全绿），另有 A2A 双协议网关、Skill Registry、多租户、OAuth2/EMA、Prometheus+Grafana 可观测性等模块：

```
GitHub: https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise
文档:   docs/federation-gateway-guide.md
```

> 作者主页须知：本文为开源项目实践笔记，欢迎 Star / Issue / PR。
> 转载需注明出处与 GitHub 链接。