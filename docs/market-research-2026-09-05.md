# 市场雷达 2026-09-05 — MCP/A2A 企业需求 · 招聘 · 招标价目

> 扫描窗口：2026-09-03 ~ 09-05，聚焦 Java + Spring + MCP/A2A/OAuth2/scope
> 对应动作：V1.20（开发变现通道：Upwork 官方 MCP Server 接入指南 + 安全审查对照表 + JD 话术包）已按本雷达信号落地

---

## 一、近 3 天高价值招聘 / 外包信号

| 公司 / 岗位 | 地点 / 形式 | 薪资 | 关键技术要求（与本项目卖点对应）|
| --- | --- | --- | --- |
| **Photon（Citi COIN 平台）** — Sr Developer - Java API, MCP | 印度钦奈 / 远程（Agency）| 未公开（数字代理大厂，服务 40% 财富 100 强）| Java/Spring Boot 微服务 + **MCP 客户端与 Server/Registry 集成** + Kafka/MQ + **OAuth2** + OpenShift/Helm + Grafana/日志；Citi 私有云 ｜ 新出现（6 天前发布）|
| **沃尔玛中国** — 高级AI平台/应用开发工程师 | 中国（猎聘）| **¥30,000–55,000/月**（约 $4.2K–7.7K/月）| **AI/MCP 网关 + MCP Server** + 多 MaaS 统一接入路由 + Skill/Spec Registry + **鉴权/限流/熔断/灰度** + RAG/工具调用/Agent 编排 + Nacos/etcd；加分：Higress/APISIX/Kong/Envoy、OPA 策略引擎、Token 计费成本归集 ｜ 新出现 |
| **WF Next（SethAI 筛选）** — 印度 MCP 开发者池 | 印度全栈外包 | **$7,000–12,000/月**（senior 全包给客户）；build engagement 4–8 周固定价 | 面试筛选标准：**token scoping、per-tenant quotas、audit trails**、eval 套件、**prompt injection / trust boundaries**；"auth and tenancy thinking" 为首要考核 ｜ 新出现 |
| **MintMCP** — Software Engineer（企业 MCP 治理平台）| 远程 / 全职（10 人初创）| 竞争性薪资 + 股权 | 自建 MCP Gateway + Agent Monitor（连接器、凭据管理、RBAC、审计、策略护栏）；要求 **security/identity/permission 背景 + 开源 MCP server 贡献** ｜ 新增 ×2 |
| **RubyLabs** — MCP Engineer / AI Backend Engineer | 100% 远程 | 竞争性 | MCP 基础设施 + AI 后端 + Cloudflare Workers + payments/fintech 加分 ｜ 新出现 |
| **Frontier LLM Lab（Data Cloud）** — AI Software Engineer (MCP Development) | 北美/LATAM 远程（外包）| 未公开 | **MCP Toolbox for Databases** 维护 + 测试套件 + 技术博客转示例代码；6 个月合同，40h/周，PST 重叠 6 小时 ｜ 持续在招 |
| **Sumo Logic** — Staff SWE (Core AI Platform, MCP) | Redwood City / 全职 | **$207K–243K/yr + Equity** | MCP 服务器托管框架 + 联邦 + 多租户隔离 + OAuth/token 交换 + 可观测性 ｜ 持续在招（已连续 5 天雷达）|
| **Greelow** — MCP Developer | 拉美 / 远程 | **$6,000–9,000/月** | typed tools + **OAuth 2.1 + per-user scoping + rate limits + audit logs** ｜ 持续在招（官网 hire/mcp-developers 常青）|

## 二、行业动态（决定产品方向）

1. **Upwork 官方 MCP Server 已正式上线（2026-08-10 发布）**：OAuth 2.1 鉴权，支持 Claude（Web/Desktop/Code）、ChatGPT、Cursor、Codex；三类角色（client/freelancer/agency）；**draft-confirm 机制**——所有写操作先出草稿再确认，绑定动作（offer/escrow）必须在 upwork.com 完成。第三方生态同步爆发：Getmany（免费 50 次查询/天，Pro $29/月）、UpworkBridge（Glama 可装）、upwork-mcp（TypeScript，npm 全局可用，18 个工具，GraphQL API）。
2. **"能过安全审查的 MCP"成为可定价商品**：WF Next 的招聘页把 **token scoping / per-tenant quotas / audit trails** 写成首轮筛选标准；Photon-Citi 岗要求 OAuth2 + MCP 双栈；沃尔玛中国把 **鉴权/限流/熔断/灰度** 写成网关核心能力。→ 本项目 V1.17-OAuth2 强制鉴权 / V1.19-scope ACL / V1.11-13-多租户 / V1.9-限流审计，几乎逐条命中。
3. **中国区 MCP 网关岗位价格锚定**：沃尔玛中国 ¥30-55K/月证明了**国内大企业也在建 AI/MCP 网关**，且要求 Nacos/OPA/Higress 等本地生态——本项目 README 中文版 + 中文 RFC 文档（6750、7009、9700）是差异化优势。
4. **Upwork 本体就是变现渠道**：官方 MCP Server 让 freelancer 的 AI agent 能扫岗 + 起草 proposal——本项目用户可以用同样的方式在 Upwork 上接 Greelow/OneSeven/Empiric 类 Java MCP 单，形成"开源影响力 → 岗位/外包 → 复利"闭环。

## 三、用户（Java + Spring + AI）在该赛道的卖点

- **全链路自主实现**：V0.1 → V1.20，20 个版本覆盖 RBAC → 限流 → 审计 → OAuth2(Client Credentials/Refresh/吊销/jti) → 多租户三档隔离 → MCP+A2A 双协议网关 → SSE → Bearer 强制鉴权 → Signed Agent Card → 工具级 Scope ACL → 变现通道；几乎逐条命中本周所有 JD 的能力清单。
- **安全审查叙事完整**：`docs/security-review-checklist.md` 把 NTT DATA "auth that survives a security review" 变成逐条可演示的对照表（凭证/传输/授权/租户/限流/审计/供应链签名）。
- **双协议（MCP+A2A）**：市场多数候选人只会 MCP；A2A 网关 + Signed Card + JWT 签名是差异化项，直接对应 Commerzbank "MCP gateway" 与 A2A 生态岗位。
- **工程完备度**：17 模块全绿测试 + GitHub Actions（JDK 17/21 矩阵）+ Docker/k8s + 三语言客户端示例 + 中文文档。
- **可演示的变现闭环**：`docs/upwork-mcp-guide.md` 给出官方 MCP Server 接入 + 用本项目做内部工具网关的两层落地路径。

## 四、待办（V1.21 候选）

- [ ] **手写 proposal 模板库**：基于本项目的 5 个信号岗位（Greelow/Empiric/OneSeven/Photon/Walmart），各出一版 200 字 proposal + 项目链接指路
- [ ] **mcp.so 注册表提交**：把 A2A + OAuth2 + SSE + Signed Card + Scope ACL 特性集打标上线，吃搜索流量
- [ ] **Upwork 官方 MCP Server 实操接入验证**：申请 OAuth 2.1 凭据后，用本仓库示例把"扫岗 → 起草 proposal"跑通并截图进 docs
- [ ] **国内 CSDN/掘金矩阵同步**：本周 blog 已产 5 篇，下周转微信公众号 + 知乎专栏，配合沃尔玛类 JD 的"国内网关需求"叙事