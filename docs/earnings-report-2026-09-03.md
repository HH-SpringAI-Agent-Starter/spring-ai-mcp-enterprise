# Daily Earnings Report 2026-09-03

> 目标：推进 spring-ai-mcp-enterprise 至下一版本，扩大开源影响力 + 挣钱
> 今日动作：**V1.18 Signed Agent Card（A2A v1.2 供应链安全基线）** + 市场雷达 09-03

---

## 一、今日完成

### 代码（V1.18，mcp-integrations/mcp-a2a）

| 交付物 | 说明 |
| --- | --- |
| `A2aAgentCardSigner` | JWS HS256 签名器/校验器：规范化 JSON（sorted keys）、密钥派生与 mcp-auth 一致（<32 字节补齐）、防 alg=none 算法混淆、常量时间比较、静态 `verify(jws, secret)` 客户端入口 |
| `SignedAgentCard` | 信封 record：agentCard + signature + algorithm + keyId + signedAt |
| `McpA2aProperties` | 新增 `card-signing-key` / `card-key-id` / `isSignedCardEnabled()` |
| `McpA2aAutoConfiguration` | 条件注册签名器 bean（未配置不注册，向后兼容） |
| `A2aRpcController` | agent-card 双端点返回签名信封 + `X-Agent-Card-Signature` 头；新增 `GET /a2a/agent-card/verify` 自验证端点；health 暴露 signedCard/cardKeyId |
| `A2aAgentCardSignerTest` | 9 个新用例（结构/往返/篡改/错钥/静态/确定性/补齐/畸形/alg=none） |

**测试：mcp-a2a 43 用例全绿；BUILD SUCCESS。**

### 配置与文档

- `mcp-server/application.yml`：`card-signing-key` / `card-key-id`（环境变量注入）
- `docs/V1.18-release-notes.md`：发布说明（含攻击场景、pitch 素材）
- `docs/blog-java-mcp-a2a-signed-card-2026-09-03.md`：掘金/CSDN 稿件《A2A 安全三部曲终章》
- `docs/a2a-integration-guide.md`：新增第十章（配置/信封/JWS 结构/客户端校验/curl 流程/三层安全模型）
- `docs/market-research-2026-09-03.md`：市场雷达
- `docs/earnings-report-2026-09-03.md`：本文件

## 二、市场雷达要点（09-03）

**谁在招 / 什么价：**

| 机会 | 价格/形式 | 对齐点 |
| --- | --- | --- |
| Greelow MCP Developer | $6,000–9,000/月（拉美远程） | typed tools + OAuth + per-user scoping + rate limits + audit logs —— 与本项目逐条同构 |
| OneSeven Tech | $4,000–5,000/月（Deel） | Java+Spring Boot+WebFlux MCP infra（持续在招） |
| Sumo Logic Staff SE | $207K–243K/yr + Equity | MCP 平台 + OAuth/token + 多租户 + 限流 + 可观测 |
| Sumsub（新） | 未公开 | Java + MCP/KYC 合规域 |
| 火石创造（重庆） | 未公开 | 点名 Spring AI 实战 + MCP 接口设计 |
| Synechron | 未公开 | Java+Spring Boot+AWS，MCP preferred |

**行业信号：** A2A v1.0 GA 官方含 Signed Agent Cards；CISO 指南把"未签名卡片"列为头号风险——V1.18 正中靶心；Upwork 发布官方 MCP Server（08-04）→ 新的接单/变现通道。

## 三、挣钱路径推演（本周更新）

1. **直接投递**：围绕 Greelow/OneSeven 类 JD 准备 30 秒 pitch（Java+MCP+OAuth2+签名卡片），开源项目即作品集
2. **外包单**：企业 MCP 集成单（认证/多连接器/生产部署）通常数周至数月，单价 $6-9K/月量级——用本项目做交付底座，可压缩 60%+ 工期
3. **Upwork MCP Server 变现通道**（新发现 09-03）：官方 API + OAuth 的职位扫描+提案生成 Server 可作为自由职业接单入口
4. **开源影响力**：博客稿已积累 20+ 篇（docs/blog-*），发布到掘金/CSDN 引流 star；注册表提交（mcp.so/smithery）待办

## 四、明日（09-04）待办

- [ ] **工具级 scope 映射**（V1.19 主方向）：OAuth2 token scope → MCP 工具级权限（多个 JD 点名 per-user scoping，优先级上升）
- [ ] 30 秒 pitch 页（针对 Greelow/OneSeven/Sumsub JD）+ demo 录屏
- [ ] Upwork MCP Server 示例模块（官方 API + OAuth，职位扫描 + 提案生成）
- [ ] mcp.so / smithery 注册表提交（打标 A2A+OAuth2+SSE+Signed Card）
- [ ] 博客稿发布到掘金/CSDN（今日稿件 + 系列前作）

## 五、指标

- 测试：mcp-a2a 43（+9 今日）/ 全仓绿灯
- 版本：V1.18（主线：V1.15 网关 → V1.16 SSE → V1.17 OAuth2 → V1.18 签名卡片）
- 文档：docs/ 累计 150+ 文件；今日 +5