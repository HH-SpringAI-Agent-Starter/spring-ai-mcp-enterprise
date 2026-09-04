# 市场雷达 2026-09-04 —— MCP/A2A 企业需求 · 招聘 · 招标价目

> 扫描窗口：最近一周（2026-08-29 ~ 09-04），聚焦 Java + Spring + MCP/A2A/OAuth2/scope
> 对应动作：V1.19（工具级 Scope 权限映射 Token Scope → Tool ACL）已按本雷达信号落地

---

## 一、本周高价值招聘 / 外包信号

| 公司 / 岗位 | 地点 / 形式 | 薪酬 | 关键技术要求（与本项目卖点对应） |
| --- | --- | --- | --- |
| **Commerzbank** — Development Java Lead (MCP) | 索非亚 / 全职（德资大行） | 未公开（欧洲行总包） | **MCP gateway + MCP server** 落地银行系统；**OAuth 2.0/OIDC 鉴权**；Kibana/Dynatrace/Prometheus/Grafana 监控；合规与审计指南 ⭐新出现 |
| **NTT DATA** — MCP and Enterprise Integration Engineer | 海得拉巴 / 全职 Senior | 未公开 | MCP server + 工具契约 + JSON Schema + **OAuth2/OIDC/mTLS + 授权检查 + least-privilege** + 工具级可观测性；"auth that survives a security review" ⭐新出现 |
| **Sumo Logic** — Staff SE (Core AI Platform, MCP) | Redwood City | **$207K–243K/yr + Equity** | MCP 平台 + **OAuth/token 交换/多租户隔离** + 限流配额 + 可观测性（持续在招） |
| **Greelow** — MCP Developer | 拉美 / 远程 | **$6,000–9,000/月** | typed tools + **OAuth 2.1 + per-user scoping + rate limits + audit logs**（持续在招，官网 hire/mcp-developers 页面常青） |
| **OneSeven Tech** — Senior Backend (MCP Infra) | 远程（拉美/EST 时区） | **$4,000–5,000/月（Deel）** | Java + Spring Boot + WebFlux + MCP 生产落地 + SQL Server（持续在招） |
| **Empiric Infotech** — MCP Server 外包 | 远程（印度交付） | **$25/小时 或 $2,000/月/开发** | 明码标价：自建 in-house 月成本 $9.2K–13.3K；agency 固定价 **$15K–60K 做 V1**；宣传点= "auth and tenant isolation that passes review" ⭐价格锚点 |
| **Visa** — Software Engineer Sr Consultant | 混合 / 全职 | 未公开 | Java + Spring Boot + **MCP-driven architecture** + Agentic + Kafka（2 月发布仍在挂，说明 MCP 进大厂 JD 常态化） |
| **Gramian Consulting** — SWE (MCP/Agentic AI) | 保加利亚 / 合同 6 个月+ | 未公开 | **MCP Toolbox for Databases** 维护 + 测试套件 + 文档 |
| **builtinottawa** — Senior SWE (MCP & Agentic AI) | 渥太华 / 远程 | 未公开 | MCP servers 实操 + tool registration + Java/Python（2 天前新发） |

## 二、行业动态（决定产品方向）

1. **银行/金融 MCP 岗位集中出现**：Commerzbank（德资大行 MCP gateway 岗）加入本周雷达，与上周 Sumsub/Exerizon/Photon-Citi 呼应——**受监管行业的 MCP 已经从"要不要做"进入"怎么做才合规"阶段**。JD 关键词高度一致：MCP gateway、OAuth2/OIDC、监控、审计。
2. **全球外包盘明确以"安全审查能过"为卖点**：Empiric Infotech 的定价页直接把 *"auth and tenant isolation that passes review"* 写成核心差异——**"能过安全审查的 MCP"本身就是可定价的商品**（V1 固定价 $15K–60K）。
3. **scope/授权成为 JD 标准件**：NTT DATA 明确列出 authorization checks + least-privilege + RBAC/ABAC；Greelow 的 per-user scoping 已固定进职位描述。V1.19 工具级 scope 映射恰好是这句话的**可演示实现**。
4. **外包时薪锚点清晰**：$25/hr（Empiric）~ $125/hr+（高端 MCP 专项）；月度 $2K（外包）~ $9K（Greelow 高端）——本项目定位应打的区间是 **$4K–9K/月的专精档**。

## 三、用户（Java + Spring + AI）在该赛道的卖点

- **全链路自主实现**：从 2026-07 的 V0.1 到现在 V1.19，19 个版本覆盖 RBAC → 限流 → 审计 → OAuth2 → 多租户三档隔离 → MCP+A2A 双协议网关 → SSE → OAuth2 强制鉴权 → Signed Agent Card → **工具级 scope ACL**，几乎逐条命中本周所有 JD 的能力清单
- **双协议（MCP+A2A）**：市面上多数候选只有 MCP；A2A 网关 + Signed Card + JWT 验签是差异化项，直接对应 Commerzbank "MCP gateway" 与 A2A 生态岗位
- **合规叙事完整**：审计日志 + scope 收敛 + 租户隔离 + fail-closed = "能过安全审查"的证据链，对标 Empiric/Greelow 的卖点话术
- **工程完备度**：17 模块全绿测试 + GitHub Actions（JDK 17/21 矩阵）+ Docker/k8s + 三语言客户端示例 + 中文文档

## 四、待办（V1.20 候选）

- [ ] **Upwork MCP Server 示例**（官方 API + OAuth）：职位扫描 + proposal 生成——把 Greelow/Empiric 频道之外的直接变现入口打通
- [ ] **mcp.so / smithery 注册表提交**：把 A2A + OAuth2 + SSE + Signed Card + Scope ACL 特性集打标上线，吃搜索流量
- [ ] **30 秒 pitch 页 / 简历话术包**：面向 Commerzbank / NTT DATA / Greelow 三类 JD 各出一版对照表
- [ ] **README 中文版加"安全审查对照表"**：对 NTT DATA 的 authorization/least-privilege 关键词逐条打勾