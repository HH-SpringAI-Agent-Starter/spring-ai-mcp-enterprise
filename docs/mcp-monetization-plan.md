# MCP Monetization Plan — 发布变现路线图

## 目标平台（V0.9）

| 平台 | 分成 | 特点 | 注册方式 |
|------|------|------|---------|
| **AgenticMarket** | 80-90% | 代理层，自动计费，Wise提现 | 官网注册 |
| **MCP Marketplace** | 85% | 类似应用商店，Stripe自动结算 | 官网注册 |
| **MCPize** | 85% | MCP服务器货币化专用 | 官网注册 |
| **Apify** | 80% | 3.6万开发者社区，月付50万+ | 官网注册 |
| **x402** | 自持 | HTTP微支付，USDC结算 | 官网注册 |

## 发布流程

1. 注册各平台账号
2. 配置 MCP Enterprise Server 作为基础框架
3. 打包工具模块 → 发布到平台
4. 设置定价（$0.03-$0.25/次调用）
5. 每日更新 + 监控收入

## 当前框架资产

- **mcp-tools/database** — SQL查询工具（已测试通过）
- **mcp-tools/search** — Web搜索工具（已含Mock）
- **mcp-tools/system** — 系统信息工具（已测试通过）
- **mcp-core** — SPI机制 + 安全管理 + 审计日志
- **mcp-server** — SSE端点 + MCP协议 + 管理API
- **mcp-monitor** — 监控模块
