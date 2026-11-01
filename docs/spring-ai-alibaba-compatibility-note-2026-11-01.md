# 📌 Spring AI Alibaba 兼容性说明（2026-11-01 更新）

> 背景：hqwc.cn 于 2026-10 报道 Spring AI Alibaba 最新稳定版 `v1.1.2.2`（2026-03-10）、2.0.0-M1.1 预览（2026-06-25）近 4 个月无更新，仓库 8–10 月仅 4 次提交。本说明固化本框架的"去耦合"姿态，可直接进售前/投标材料。

## 一、核心结论

**Spring AI MCP Enterprise 的治理增强层不依赖 Spring AI Alibaba，也不依赖任何第三方社区框架的发布节奏。**

- 本框架核心（`mcp-core` / `mcp-governance` / `mcp-auth` / `mcp-audit` / `mcp-tenant` / `mcp-registry` / `mcp-gateway` / `mcp-a2a`）基于 **Spring 团队官方维护的 MCP Java SDK** 构建：
  - Spring AI 2.0.0（2026-06-12 GA）内置 MCP Java SDK 2.0.0；
  - 2026-07-28 无状态规范在 `modern` 模块 **37/37 一致性测试通过**；
  - `@McpTool` / `@McpResource` / `@McpPrompt` 已并入 Spring AI 核心，Streamable HTTP 为默认传输。
- `mcp-alibaba`（Spring AI Alibaba 集成）与 `examples/spring-ai-alibaba-mcp-server/` 是**可选兼容示例层**：
  - 独立 Maven 工程，**不进入主 reactor**；
  - 仅用于"把 Spring AI Alibaba 生态的存量 Bean / Nacos 注册 适配为 MCP 工具"的场景；
  - 上游停更不影响主框架治理能力。

## 二、为什么这样设计

1. **供应链安全**：官方 SDK 由 Spring 团队维护，版本跟进最紧、CVE 修复最及时；社区框架（含 Alibaba）发布节奏不可控。
2. **治理中立**：治理增强层（RBAC / 限流 / 审计 / HITL / 多租户 / Scope）与"用哪家 Agent 框架"正交，可叠加在任何 MCP Server 之上。
3. **可降级**：即便 Alibaba 适配完全停更，用户仍可基于官方 SDK + 本框架治理层独立交付，无单点依赖风险。

## 三、给客户/雇主的标准话术

> "我们的治理增强层构建在 **Spring 官方 MCP Java SDK** 之上，与具体的 Agent/Cloud 框架解耦；Spring AI Alibaba 仅作为可选兼容示例，不参与核心链路，因此不存在上游停更导致生产事故的风险。"

## 四、适用场景对照

| 场景 | 推荐路径 |
|------|----------|
| 新建生产级 MCP Server + 治理 | 官方 SDK + 本框架 `mcp-spring-ai-tools` + 治理模块 |
| 存量 Spring Cloud / Dubbo 转 MCP | 可选 `mcp-alibaba`（Nacos 注册）或 Higress 网关，治理层叠加 |
| 纯 Alibaba 生态团队 | `examples/spring-ai-alibaba-mcp-server` 起步，治理层同款可用 |

---
*参考：hqwc.cn/a/2123698.html、Spring AI 2.0 官方 release notes、juejin.cn/post/7701316033940226082*
