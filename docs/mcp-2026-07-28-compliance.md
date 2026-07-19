# MCP 2026-07-28 规范适配清单

> 本文档追踪 MCP 2026-07-28 规范相比 2025-03-26 的所有变更，及 MCP Enterprise 的适配状态。

## 规范发布路线

| 里程碑 | 日期 | 状态 |
|--------|------|------|
| 候选版发布 | 2026-05 | ✅ 已完成 |
| Schema vending | 2026-06-27 | ✅ python-sdk 已置入 |
| 正式发布 | 2026-07-28 | ⏳ 待发布 |

## 核心变化（来自企查查工程实践文章 & 官方 schema）

### 一、无状态核心 → 从"会话绑定"走向"请求自包含"

| 旧版（2025-03-26） | 新版（2026-07-28） |
|-------------------|-------------------|
| 需要 initialize/initialized 握手 | ❌ 取消握手，请求自包含 |
| 维护 Mcp-Session-Id | ❌ 取消 Session |
| 单实例绑定 | ✅ 任意实例可处理 |

**MCP Enterprise 适配**：✅ `McpStatelessEndpoint` 已实现无状态处理

### 二、能力发现 → 从"列出来"走向"管起来"

| 变更 | 说明 |
|------|------|
| `tools/discover` | 单工具深度能力查询 |
| `server/discover` | Server 全量能力清单 |
| `Mcp-Method` / `Mcp-Name` | 网关识别调用的方法和工具 |
| `ttlMs` / `cacheScope` | 工具清单和资源的缓存时间 |

**MCP Enterprise 适配**：✅ 已实现 `tools/discover` + `server/discover`

### 三、完整 JSON Schema 2020-12

| 旧版 | 新版 |
|------|------|
| JSON Schema Draft-07 | JSON Schema 2020-12 |
| 简单类型描述 | 支持 `$ref`/`oneOf`/`anyOf`/条件/引用 |

**MCP Enterprise 适配**：✅ 所有工具 inputSchema 已升级

### 四、W3C Trace Context → 标准化链路追踪

| 变更 | 说明 |
|------|------|
| `traceparent` | 标准 trace-id + span-id |
| `tracestate` | 厂商扩展字段 |

**MCP Enterprise 适配**：✅ 已支持 `traceparent`/`tracestate`

### 五、Extensions → 一等公民

**MCP Enterprise 适配**：✅ 声明 `mcp-enterprise` + `custom` 命名空间

### 六、Tasks → 长任务支持

**MCP Enterprise 适配**：✅ `tasks/create` + 异步执行 + taskId 轮询

### 七、企业授权加固

| 变更 | 说明 |
|------|------|
| OAuth 2.0 | 标准授权流程 |
| 身份提供商（IdP） | Okta / 钉钉 / 飞书统一管控 |
| 企业托管授权 | 管理员集中配置，员工无需手动 OAuth |

**MCP Enterprise 适配**：✅ RBAC + JWT + OAuth2/SSO + API Key（已在 V0.8 完成）

### 八、MCP Apps → 交互 UI

| 变更 | 说明 |
|------|------|
| MCP Apps | Agent 前端交互界面标准 |

**MCP Enterprise 适配**：📋 V0.12 计划

## 适配进度总览

| # | 特性 | 状态 |
|---|------|------|
| 1 | 无状态核心 | ✅ V0.11 |
| 2 | 能力发现 | ✅ V0.11 |
| 3 | JSON Schema 2020-12 | ✅ V0.11 |
| 4 | W3C Trace Context | ✅ V0.11 |
| 5 | Extensions | ✅ V0.11 |
| 6 | Tasks | ✅ V0.11 |
| 7 | 企业授权 | ✅ V0.8 |
| 8 | MCP Apps | 📋 V0.12 |

8 项中 **7 项已完成**，适配进度 87.5%。
