# MCP 平台部署密钥（2026-07-12）

> ⚠️ 敏感信息！勿提交到公开仓库！

## MCPize（首选）
- API Key: `sk_global_88d4b22aa1a34b36a8f177746daf6455`
- 登录方式: CLI (`mcpize login`) 或 API
- 发布方式: GitHub App / `mcpize deploy`

## AgenticMarket（备选）
- API Key: `am_live_ae415fdc498849cc96f61c243902dfbf`（2026-07-12 重新生成）
- 旧 Key（403 失效）: `am_live_68a4783a083b4d56962b870de76d81d6`
- 状态: Vercel Security Checkpoint 导致 CLI 403（CLI 直连 API 被 Vercel 安全验证拦截）
- 注意: Edge 浏览器登录正常可用，但 CLI 认证也返回 403，说明是 API 层面安全拦截
- 发布方式: 需通过浏览器操作 Submit New Server

## Apify（长期留用）
- API Key: `apify_api_uxaewXKubKl7OGZrOhXmgcJ9QGyMEH2BtDl0`
- 发布方式: GitHub Actions / `apify push`
