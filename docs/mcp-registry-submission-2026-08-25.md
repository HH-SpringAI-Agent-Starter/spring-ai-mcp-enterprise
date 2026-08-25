# MCP Registry 收录行动 — Spring AI MCP Enterprise

> 版本：V1.10 候选 | 日期：2026-08-25 | 状态：已核验缺口，待提交

## 一、核验结果（2026-08-25）

| Registry | 收录状态 | 说明 |
| --- | --- | --- |
| mcp.so | ❌ 未收录（搜索 0 结果） | 社区最大目录（~19,000 servers），提交表单即可 |
| Smithery | ❌ 未收录（有 smithery.yaml 但未见收录） | 需 CLI publish 或 dashboard 提交 |
| 官方 Registry (registry.modelcontextprotocol.io) | ❌ 未收录 | 需向 modelcontextprotocol/registry 仓库提 PR |
| Glama | ❌ 未收录 | 可自动爬取 GitHub 发现（等收录或主动提交） |
| MCPFind | ❌ 未收录 | 表单提交 |

**结论：项目具备完整 server.json + smithery.yaml 元数据，但未进入任何公开目录——这是可见性最大缺口。**

## 二、提交清单（按 ROI 排序）

### 1. mcp.so（最快，10 分钟）
- 地址：https://mcp.so/submit
- 填写：Server 名称 `spring-ai-mcp-enterprise`、描述（从 server.json description 复制）、GitHub 链接、transport（streamable-http + `http://localhost:8080/api/mcp/message`）；
- 优点：无需审核等待，社区驱动。

### 2. Smithery（CLI 方式）
```bash
npm install -g @smithery/cli
smithery mcp publish "https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise" -n hh-springai/spring-ai-mcp-enterprise
```
- 注意：仓库已有 smithery.yaml（版本号 0.11.0 需更新为 1.1.0，待确认是否自动读取）；
- 远程 Server 需可公开访问的 HTTPS 端点（本地 localhost 不可用）→ **若暂无公网端点，先以「自托管/源码」形式收录**。

### 3. 官方 Registry（GitHub PR）
- 仓库：https://github.com/modelcontextprotocol/registry
- 在 `servers/` 目录新增 JSON（参考现有条目格式），字段：name、description、homepage、repository；
- 合并后自动进入官方目录，被所有 MCP client 自动发现。

### 4. Glama（零操作）
- 放置 `glama.json` 到仓库根目录即可自动发现（可选加分）。

## 三、公网端点问题（前置依赖）

本地 localhost 端点无法被 Smithery/官方 Registry 验证。两个选择：

| 方案 | 成本 | 说明 |
| --- | --- | --- |
| **免费内网穿透**（ngrok / cpolar） | 免费额度 | 演示用，不稳定 |
| **云服务器 + Docker Compose**（推荐） | ~¥50-100/月 | 按 [production-deployment.md](production-deployment.md) 部署，长期可用，可挂 OAuth2 回调 |

## 四、执行状态追踪

- [ ] mcp.so 提交（表单）
- [ ] Smithery CLI publish
- [ ] 官方 Registry PR（需先定公网端点）
- [ ] Glama glama.json 添加
- [ ] README 添加 registry badge（收录后）

## 相关文档

- [SEO 增长计划](star-growth-plan-zh.md)
- [市场部署](mcp-marketplace-deploy.md)
