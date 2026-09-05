# Upwork 官方 MCP Server 接入指南（变现通道）

> 适用版本：V1.20+
> 目标读者：希望把「开源 MCP Server 影响力」转化成「Upwork 外包收入」的 Java + Spring 开发者
> 关联资料：官方页面 https://upwork.com/ai/mcp ｜ 本仓库 [examples/upwork-client-config.json](../examples/upwork-client-config.json)

---

## 一、为什么这是变现通道

2026-08-10 Upwork 官方发布 MCP Server（OAuth 2.1），把全球最大自由职业市场接入 Claude / ChatGPT / Cursor / Codex 等 AI 客户端。对 Java + Spring AI 开发者意味着：

1. **岗位直达**：可以让 AI agent 在聊天里直接扫「Java + MCP」「Spring AI」类岗位，不用每天刷网页。
2. **proposal 起草自动化**：官方 MCP 提供"基于 job 上下文生成 proposal 草稿"能力，配合本项目的工程叙事（安全审查对照表、20 个版本 roadmap），proposal 命中率更高。
3. **需求侧在涨**：本周雷达确认 Greelow（$6-9K/月）、OneSeven（$4-5K/月）、Photon-Citi（MCP 岗）、沃尔玛中国（¥30-55K/月）都在招 MCP 能力——Upwork 官方通道 + 本项目开源背书 = 双重信用。

## 二、Upwork 官方 MCP Server 能力摘要

| 角色 | 核心能力 |
| --- | --- |
| 通用 | 账户列表、文件附件、draft-confirm 草稿确认机制 |
| Client（发单方）| 找人才、发岗位、邀请、offer/合同、milestone、消息 |
| Freelancer（接单方）| 搜岗、推荐、**起草并提交 proposal（消耗 Connects）**、接受邀请、交付、收益/资料 |
| Agency | 团队视角聚合以上全部 |

**安全设计（与本项目理念一致）**：
- 全部走 OAuth 2.1 授权 + Upwork 账户体系（身份验证、escrow 托管、里程碑担保）
- 每个**写操作**都是草稿，需用户在 AI 客户端里二次确认；绑定动作（打款 escrow、接受 offer）必须回 upwork.com 完成
- 权限随时可在 Upwork 账户设置里吊销

## 三、接入步骤

### 1. 在 Claude Code / Cursor 中安装

官方支持多种客户端：

```bash
# Claude Code 示例：指向官方安装说明后按提示授权（OAuth 2.1 登录）
```

安装后先用一句话建立工作流：

```
Show me this week's jobs that match my skills (Java, Spring Boot, MCP, Spring AI)
and fit my budget and availability.
```

### 2. 用本仓库的配置模板（离线/自托管客户端）

见 [examples/upwork-client-config.json](../examples/upwork-client-config.json)——把官方 MCP Server 声明进 `.mcp.json` / `claude_desktop_config.json`，并把本仓库的 `mcp-server`（localhost:8081）作为**内部工具网关**同时挂载：

```json
{
  "mcpServers": {
    "mcp-enterprise": {
      "type": "http",
      "url": "http://localhost:8081/mcp",
      "headers": { "Authorization": "Bearer ${MCP_API_KEY}" }
    },
    "upwork": {
      "type": "http",
      "url": "https://developer-mcp.upwork.com/mcp",
      "oauth": { "scopes": ["jobs_read", "proposals_write"] }
    }
  }
}
```

> 架构说明：你的 AI 客户端通过 `mcp-enterprise` 调用本项目工具（走本项目的 API Key / OAuth2 / scope ACL / 限流 / 审计），需要真人协助时通过 `upwork` 直达市场。两个通道职责分离，符合"内部能力网关 + 外部人力市场"的企业 AI 编排模式。

### 3. 与本项目能力对照（为什么这套组合能过安全审查）

| 维度 | Upwork 官方 MCP | 本项目（spring-ai-mcp-enterprise）|
| --- | --- | --- |
| 认证 | OAuth 2.1 + Upwork 账户 | X-API-Key / OAuth2 Client Credentials / Authorization Code（mcp-auth）|
| 授权粒度 | 角色（client/freelancer/agency）| RBAC + 工具级 Scope ACL（V1.19）+ 多租户三档隔离（V1.11-13）|
| 写操作安全 | draft-confirm + escrow | draft 语义可由工具层 `requiredScopes` + 审计日志组合实现 |
| 限流 | 平台配额（Connects）| 网关限流 + 按租户配额（V1.9/V1.7）|
| 审计 | 平台操作记录 | 全链路审计日志（mcp-core）|
| 供应链 | 官方托管 | Signed Agent Card（V1.18，A2A 供应链签名）|

## 四、实操建议（三步走）

1. **第一步：扫岗**：用官方 MCP 搜索关键词组合 `Java Spring Boot MCP`、`Spring AI`、`Model Context Protocol`，预算过滤 `$4,000–$9,000/月`。
2. **第二步：差异化 proposal**：在 proposal 开头直接给仓库链接 + 一句话叙事：「20 版本 MCP 企业级框架，OAuth2/scope ACL/多租户/审计全链路开源，可过安全审查（见 security-review-checklist.md）」。
3. **第三步：把开源当简历**：本仓库的 GitHub Actions、Docker、三语言客户端示例、中文文档就是面试官/发包方最先看到的东西——持续每日提交本身就是信用资产。

## 五、注意事项

- 每个写操作（提交 proposal、发消息）默认是草稿，**务必自己 review 后再确认**；绑定资金动作只能在 upwork.com 完成。
- Connects 是消耗品，proposal 提交前先用 `get_job_details` 看完整需求，避免盲目投递。
- 不要用本指南绕过 Upwork 平台规则；所有交易走官方 API 与 escrow。