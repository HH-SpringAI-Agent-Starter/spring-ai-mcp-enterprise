# Proposal 模板库 2026-09-06 — 定向投递弹药（5 份）

> 依据市场雷达 09-06 新信号生成；每份 200 字左右 + 仓库锚点
> 仓库：https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise（V0.1→V1.21，21 版，18 模块全绿）

---

## 1. 禾蛙/迅致 · MCP平台开发工程师（上海，¥80-120万/年）— 中文

> 投递渠道：禾蛙职位页（佣金 166K 岗位）；语言：中文；重点：Skill Registry 代码证据 + 企微/Claude Enterprise 方案思路

**Proposal：**

我在开源项目 `HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise` 中从零实现了企业级 MCP Server 框架（21 个版本迭代、18 模块全绿测试），其中与你 JD 直接对应的能力已经落地为代码：

- **Skill 标准化运行环境**：`mcp-registry` 模块——Skill 注册表 + 语义化版本管理 + 显式激活 + **故障回滚（一键退回上一已部署版本）+ 灰度路由（权重制发布）**，管理 REST API（/api/admin/skills）与客户端发现端点（/api/mcp/skills）齐备，12 个单元测试守护；这正是你要求的"Skill 注册、版本管理、灰度发布、故障回滚"。
- **平台底座**：OAuth2 全生命周期（Client Credentials/Refresh/吊销/jti）、RBAC、限流、审计、多租户三档隔离、MCP+A2A 双协议网关、工具级 Scope ACL——"给别人用的系统"该有的鉴权、向后兼容、API 品味都按这个标准做。
- **Claude Enterprise 打点思路**：我有 A2A（Agent2Agent）网关 + Signed Agent Card 签名实战（供应链安全基线），可以快速迁移到"企微 × Claude Enterprise"的身份鉴权/消息路由/沙箱隔离链路设计。

能独立从需求到原型，深度使用 AI Coding 工具，读得懂 MCP 开源生态源码。

---

## 2. NTT DATA · MCP and Enterprise Integration Engineer（Hyderabad）— English

> 重点：逐条拆 Recruiter 红旗（"只会消费 MCP 工具" / "identity/authorization 弱" / "无生产 API 所有权"）

**Proposal：**

I built, from scratch, an enterprise MCP server framework in Java/Spring Boot (21 releases, 18 modules, all tests green): `HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise`.

Your red flags read like a checklist of what I've already shipped in production-shaped code: server-side MCP implementation (not just consuming tools) with Streamable HTTP + SSE; OAuth2/OIDC-grade authn (client credentials, refresh rotation per RFC 9700, revocation per RFC 7009, jti); mTLS-ready service identity model; tool-level scope ACL with RFC 6750 insufficient_scope; per-tool audit trails and monitoring; contract-style tests across modules; and a Skill Registry with versioning, gray release and fault rollback (12 tests). I own the full API surface end-to-end — design, security, observability, docs.

Happy to walk through the security review checklist I maintain for every endpoint.

---

## 3. Cognizant · Agentic AI MCP Integration Specialist（Boise，$98-115K，截止 09-09）— English

> 重点：企业系统集成（ServiceNow/Jira/SharePoint）+ OAuth/Entra + 可观测性；强调 Java 型企业集成经验

**Proposal：**

I bring production MCP server experience plus deep enterprise-integration fundamentals from Java/Spring: I built `HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise`, an enterprise MCP framework covering exactly your stack — OAuth2/OIDC-style authn (including Microsoft Entra-friendly flows), RBAC, per-tool scoping, rate limits, audit logging and Prometheus observability over every tool call.

On the integration side I've implemented connectors to SQL databases (read-only tooling with injection protection), generic HTTP tools with SSRF allow-lists, finance/compliance toolkits, and a multi-tenant isolation layer (row/schema/instance) — the same discipline required to expose ServiceNow/Jira/SharePoint/Snowflake safely to agents. I also contribute to open source, which your team values. Available for immediate start; happy to cover PST overlap.

---

## 4. OneSeven Tech · Sr Backend Engineer — MCP Infrastructure（LATAM，$4-5K/月）— English

> 重点：Java + Spring Boot/WebFlux + SQL Server + on-prem 桥接；强调独立架构决策与 peer 协作

**Proposal：**

Your project brief is almost a mirror of what I've been building: Java + Spring (Boot 3.4, reactive-ready), MCP server components, bridging on-prem systems into an agent layer. In `HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise` I architected the server-side implementation of MCP (Streamable HTTP/SSE transports, JSON-RPC tooling, tool registry, security, multi-tenancy, A2A gateway) as the primary backend owner — pattern-defining work in a small team, exactly your "peer, not ticket queue" setup.

I design and defend architecture before writing code, and I've shipped against SQL Server-class relational workloads (JDBC tooling with schema-safe queries). Onboarding in ~1 week; EST overlap is fine from my timezone. Long-term contract preferred.

---

## 5. CareerFlow · AI SWE (MCP Development / AI R&D)（Remote NA/LATAM，$50-70/hr）— English

> 重点：开源 MCP 工具开发 + 技术博客转示例代码 + 测试套件

**Proposal：**

I maintain an open-source enterprise MCP framework (`HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise`) — 21 releases, 18 modules, 300+ tests — so turning technical blogs into sample code and keeping high-quality test suites is my daily workflow, not a side task. I've built MCP tools across domains (database, search, HTTP with SSRF protection, finance/compliance, weather) with typed JSON-Schema contracts and deterministic error handling — the "small, single-purpose tools" style your Frontier LLM Lab client expects.

I also keep a bilingual (EN/zh) documentation practice, which helps bridge your community docs. 40h/week with 6h PST overlap is workable; 6-month engagement fits how I operate (I iterate in weekly release cycles).

---

## 附：Upwork 200 字通用模板（微调版）

> 适配 Greelow / OneSeven / 任意 Java MCP 单

**Upwork Proposal：**

I build enterprise-grade MCP servers in Java + Spring Boot — with auth that survives a security review. Open-source proof: `HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise` (21 releases, 18 modules, all tests green) implements OAuth2 token lifecycle (client credentials, refresh rotation, revocation), tool-level scoping (RFC 6750 insufficient_scope), per-tenant isolation, rate limiting, audit trails, A2A gateway with signed agent cards, and a Skill Registry with versioning/gray-release/rollback. If your agents need to reach your APIs/databases safely and you need per-user scoping + audit logging from the first commit, that's what I ship. I deliver typed tool contracts, eval-friendly test suites, and docs in English and Chinese. Available for build engagements (4-8 weeks) and ongoing fractional work.

## 投递优先级（09-06 时点）

1. **Cognizant**（截止 09-09，$98-115K，remote US，Java 无关但 MCP 集成 + OAuth 强相关——模板 #3）
2. **NTT DATA**（Java 栈强匹配，模板 #2，持续在招）
3. **OneSeven**（Java 栈，$4-5K/月保底/练手，模板 #4）
4. **CareerFlow**（$50-70/hr，开源贡献叙事最强，模板 #5）
5. **禾蛙**（¥80-120万，需 985/211 + 上海 onsite，模板 #1 备用于叙事复制）