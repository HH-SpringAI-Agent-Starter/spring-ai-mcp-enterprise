# 市场雷达 2026-09-03 —— MCP/A2A 企业需求 · 招聘 · 招标

> 扫描窗口：近一周（2026-08-28 ~ 09-03），聚焦 Java + Spring + MCP/A2A/OAuth2/签名卡片
> 对应动作：V1.18（Signed Agent Card 签名 Agent Card）已按本雷达信号 + 昨日待办落地

---

## 一、本周高价值招聘 / 外包信号

| 公司 / 岗位 | 地点 / 形式 | 薪酬 | 关键要求（与本项目卖点对应） |
| --- | --- | --- | --- |
| **Greelow** — MCP Developer | 拉美 / 远程（US 时区） | **$6,000–9,000/月** | **typed tools + OAuth 2.1 + per-user scoping + rate limits + audit logs**，"not a demo server running on a laptop"——与本项目 RBAC/限流/审计/多租户完全同构 |
| **OneSeven Tech** — Senior Backend (MCP Infra) | 远程（拉美） | **$4,000–5,000/月（Deel）** | Java + Spring Boot + WebFlux，MCP server 组件 + tool-use + agent 编排（持续在招） |
| **Sumsub** — AI/MCP Senior Backend Engineer (Java) | 远程 / 多地 | 未公开（对标市场） | Java 17 + Dropwizard，把 AI/MCP 能力嵌入 KYC/合规产品——**新出现**，合规域 MCP |
| **Sumo Logic** — Staff SE (Core AI Platform, MCP) | Redwood City | **$207K–243K/yr + Equity** | Java/Scala/Go + MCP 平台 + OAuth/token 交换/多租户隔离 + 限流配额 + 可观测 |
| **Exerizon** — Mid-level Java Engineer (AI Agents, MCP) | 华沙 / B2B 15-20h/w | 时薪 Competitive | Java 17 + Spring Boot + **Spring AI MCP 集成** + JSON-RPC 2.0 + SSE 传输层（保险业） |
| **Synechron** — Java Engineer (Spring Boot, AWS & Claude) | 印度 / 全职 | 未公开 | Java + Spring Boot + AWS EKS + **MCP experience preferred**（金融/受监管环境） |
| **火石创造** — 高级Java工程师(MCP / Spring AI方向) | 重庆 / 全职 | 未公开 | **Spring AI 框架实战** + MCP 服务接口设计 + 智能体工作流引擎 + Function Calling 插件管理（8年+，产业大数据） |
| **Photon (Citi)** — Sr Developer (Java API, MCP) | 金融 / 远程(印度) | 未公开（禁利全） | Java/Spring Boot 微服务 + OAuth2(Citi COIN) + OWASP + **MCP client/server 集成** + Kafka + OpenShift |

## 二、行业动态（决定产品方向的信号）

1. **A2A v1.0 正式 GA（Linux Foundation，2026-04）**：支持组织 150+（AWS/Google/Microsoft/IBM/Salesforce/SAP/ServiceNow），官方特性含 **Signed Agent Cards（JWS + JSON Canonicalization）、多租户、OAuth/安全澄清**；5 种官方 SDK（Python/JS/Java/Go/.NET）；GitHub 22,000+ stars。→ **V1.18 的签名卡片正是 A2A v1.0 标准能力，向"规范对齐"再进一步。**
2. **CISO 视角确认签名卡片是强制项**：BeyondScale《A2A Protocol Security: CISO Guide》——"Agent cards are unsigned by default, creating spoofing and prompt injection vectors"被列为五大战术风险之首；mTLS + **signed agent cards** + zero-trust per-request 授权是企业核心控制项。→ **"能讲清为什么声明不够、必须签名"是面试/投标里最打动架构师的点。**
3. **Upwork 发布官方 MCP Server（2026-08-04）**：Claude/ChatGPT/Cursor 可直接通过 MCP 发 job、找 freelancer、发 offer；freelancer 可用 AI agent 扫描匹配 job 并自动起草 proposal。→ **新增一条变现通道：用本项目的能力（安全/限流/审计）做一个 Upwork MCP Server（官方 API + OAuth），实现"雷达自动扫描 + 提案生成"，可作外包接单入口。**
4. **MCP 岗位需求从"想招"变"明确列出"**：本周 Greelow 甚至把岗位 JD 写成 MCP 能力清单（typed tools/OAuth/scoping/rate limits/audit）——市场正在把 V0.1–V1.17 白皮书里的每一项变成招聘硬条件。

## 三、用户（Java + Spring + AI）在该赛道的卖点

- **V1.15→V1.18 完整故事线**：双协议网关（MCP+A2A）→ SSE 流式 → OAuth2 强制鉴权（RFC 6750）→ **Signed Agent Card（JWS 供应链签名）**——正好覆盖 A2A v1.0 GA 的三大企业特性（多租户/安全/签名）
- **与 Greelow JD 逐条对标**：typed tools（工具注册中心+JSON Schema）✓ OAuth/per-user scoping（mcp-auth+多租户隔离）✓ rate limits（Redis 限流）✓ audit logs（审计日志）✓——**"Greelow 要什么人，这个开源项目就是什么"**
- **合规域 MCP 先发**：Sumsub（KYC）、Exerizon/Photon（保险/金融）都在要受监管环境的 MCP——项目的合规工具（finance 合规执行器）+ 审计 + 租户隔离正好讲合规故事
- **落地上云**：Dockerfile + docker-compose + k8s + GitHub Actions 全齐（对标 Synechron 的 AWS EKS、TalentAlly 的 CI/CD 要求）
- **中文市场**：火石创造等 Spring AI 岗位直接点名 Spring AI 实战——`docs/alibaba-integration-guide.md` + mcp-alibaba 模块就是现成面试弹药

## 四、待办（V1.19 候选）

- [ ] **工具级 scope 映射**：OAuth2 token scope → MCP 工具级权限（对标 TalentAlly per-user quota / Cotality token 权限 / Greelow per-user scoping）——**优先级上升**，本周多个 JD 点名
- [ ] **Upwork MCP Server 示例**（官方 API + OAuth）：职位扫描 + 提案生成，作为接单/变现入口
- [ ] 一张 30 秒 pitch 页：面向 Greelow / OneSeven / Sumsub 类 JD 的 Java+MCP+OAuth2 版（含 demo 录屏链接）
- [ ] 将 "A2A + OAuth2 + SSE + Signed Card" 特性提交 mcp.so / smithery 注册表并打标