# 市场雷达 2026-09-02 —— MCP/A2A 企业需求 · 招聘 · 招标

> 扫描窗口：近一周（2026-08-27 ~ 09-02）
> 聚焦：Java + Spring + MCP / A2A / OAuth2 / SSE 流式
> 对应动作：V1.17（A2A OAuth2 Bearer 强制鉴权）已按本雷达信号落地

---

## 一、本周高价值招聘 / 外包信号

| 公司 / 岗位 | 地点 / 形式 | 薪酬 | 关键要求（与项目卖点） |
| --- | --- | --- | --- |
| **Photon (Citi)** — Sr Developer (Java API, MCP) | 金奈 / 远程(印度) | 未公开（福利全） | Java/Spring Boot 微服务 + **OAuth2(Citi COIN)** + OWASP + **MCP client/server 集成** + Kafka + OpenShift |
| **Exerizon** — Mid-level Java Engineer (AI Agents, MCP) | 华沙，B2B 15-20h/w | 时薪 Competitive | Java 17+ + Spring Boot + **Spring AI MCP 集成** + **JSON-RPC 2.0 + SSE 传输层** + 保险业 |
| **OneSeven Tech** — Senior Backend Engineer (MCP Infra) | 远程（拉美） | **$4000–5000/月（Deel）** | Java + Spring Boot + **WebFlux**，MCP server 组件 + tool-use + agent 编排（持续在招） |
| **Sumo Logic** — Staff SE (Core AI Platform, MCP) | Redwood City | **$207K–243K/yr + Equity** | Java/Scala/Go + **MCP 平台** + **OAuth/token 交换/多租户隔离** + 限流配额 + 可观测（发布 08-29） |
| **Cotality** — Senior SE (MCP Servers) | Dallas | 未公开 | **MCP 服务器** + **OAuth/JWT/API key/令牌** + Apigee X + 测试（招到 10-11） |
| **Intellias** — Tech Lead (MCP Server Registry) | 远程（多国） | 未公开 | **MCP 注册/发现/治理** + **OAuth2.0/JWT/企业 IdP** + AWS AgentCore 生态 |
| **MintMCP** — Software Engineer | 旧金山 | Competitive + Equity | **MCP gateway** + 权限收敛 + 审计 + 安全边界（Coursera/Arlo/Braze 客户，Cowboy/Coatue 投资） |
| **TalentAlly** — Senior Platform Eng (AI Gateway MCP) | 远程 | 未公开 | **AI Gateway MCP server** + Entra OIDC + **token 生命周期/滑动 TTL/每用户配额/分布式限流** + 负载测试 1000+ 并发 |

## 二、行业动态（决定产品方向的关键信号）

1. **A2A 进入 AAIF（Agentic AI Foundation）**：2026-08-20 A2A 与 MCP 同归 Linux Foundation AAIF 治理，会员 250+（AWS/Google/Microsoft/OpenAI/Anthropic/Cloudflare 全在）。**RFP 语言正从"支持自定义工具目录"转向"支持 MCP + A2A + 可测的合规性"** —— 双协议网关的窗口期正式打开。
2. **A2A v1.2 Signed Agent Cards**：签名 Agent Card 已成企业部署的供应链安全底线（"不签卡 = 供应链漏洞"）。→ 已列入 V1.18 候选。
3. **"假安全"不可接受**：A2A 认证信任模型 = **HTTPS 基线 + OAuth2.0 + 签名卡三层**；本周 JD（Photon/Citi、TalentAlly、Cotality）全部点名 OAuth2/JWT/token 管理。→ V1.17 的 Bearer 强制鉴权正命中此信号。
4. **MCP 质量两极分化**：公开注册表 17,468 个 MCP server 中仅 12.9% 质量评分 >70——**"能做规范 + 质量可信"的 Java MCP 供应商是稀缺品**，本项目的开源质量背书（全仓测试、CI、文档）正是差异点。

## 三、用户（Java + Spring + AI）在该赛道的卖点

- **OAuth2 闭环可演示**（V1.17）：mcp-auth Client Credentials 发证 → A2A 网关 Bearer 强制校验（RFC 6750），一条命令演示"发证-验证-拒绝"三段式。
- **双协议一次建**：MCP（安全/限流/审计/多租户/RBAC）+ A2A（同步/流式/鉴权）双协议网关，直接匹配 AAIF 双栈趋势与 RFP 新语言。
- **供应链安全叙事**：V1.16→V1.17 是"声明→强制"的升级路径，讲故事时能讲透**为什么声明不够**——这是面试/投标里最能打动架构师的点。
- **能落地云原生**：Dockerfile + docker-compose + k8s + GitHub Actions 全齐（Photon 的 OpenShift/Helm、TalentAlly 的 CI/CD 要求可直接对标）。

## 四、待办动作（V1.18 附近）

- [ ] **Signed Agent Card**（A2A v1.2）落地 -> 可在 pitch 里宣称"签名 Agent Card + OAuth2"双安全特性
- [ ] **工具级 scope 映射**：OAuth2 token scope → MCP 工具级权限（对位 TalentAlly "per-user quota"/Cotality "token 权限"）
- [ ] 一页 pitch：面向 Sumo Logic / OneSeven / Photon 类 JD 的 Java+MCP+OAuth2 版（含 demo 录屏链接）
- [ ] 把 "A2A + OAuth2 + SSE" 特性提交 mcp.so / smithery 注册表标注