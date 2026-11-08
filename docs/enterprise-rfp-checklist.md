# MCP Enterprise Server — 企业 RFP/招标应答清单

> 本清单帮助企业采购团队评估 MCP Server 方案，也帮助开发者准备应答材料。

---

## ✅ 功能完整性检查表

### 核心协议支持
- [ ] MCP 协议版本支持（2024-11-05 / 2025-03-26 / 2026-07-28）
- [ ] JSON-RPC 2.0 消息格式
- [ ] SSE (Server-Sent Events) 传输
- [ ] Streamable HTTP 传输（无状态模式）
- [ ] stdio 传输（本地模式）
- [ ] 工具发现 (tools/list)
- [ ] 工具调用 (tools/call)
- [ ] 资源暴露 (resources)
- [ ] Prompt 模板 (prompts)

### 安全与合规
- [ ] API Key 认证
- [ ] OAuth 2.1 Client Credentials
- [ ] OAuth 2.1 Authorization Code
- [ ] Bearer Token 认证
- [ ] RBAC 角色权限控制
- [ ] Scope 级别授权（工具级 ACL）
- [ ] Rate Limiting（令牌桶/滑动窗口）
- [ ] 审计日志（谁调了什么、什么参数、什么结果）
- [ ] Refresh Token 轮换 + 重用检测
- [ ] Token 吊销 (RFC 7009)
- [ ] 令牌内省 (RFC 7662)
- [ ] CORS 配置
- [ ] SQL 注入防护

### 多租户
- [ ] 租户隔离（Row-level Security）
- [ ] 租户感知数据源
- [ ] 租户级 API Key 管理
- [ ] 租户级 Rate Limit
- [ ] 租户级审计日志

### 集成能力
- [ ] Spring AI Alibaba 集成（通义千问）
- [ ] Spring AI 标准 ToolCallback 集成
- [ ] A2A (Agent-to-Agent) 协议支持
- [ ] Dify 工作流集成
- [ ] Federation Gateway（多 MCP Server 聚合）
- [ ] 自定义工具注册（热插拔）

### 可观测性
- [ ] Prometheus 指标暴露
- [ ] Grafana 仪表盘
- [ ] 健康检查端点 (Actuator)
- [ ] 结构化日志
- [ ] 分布式追踪（OpenTelemetry）

### 部署
- [ ] Docker 镜像
- [ ] Docker Compose 编排
- [ ] Kubernetes Helm Chart
- [ ] CI/CD Pipeline (GitHub Actions)
- [ ] 多环境配置（dev/staging/prod）

---

## 🏗️ 架构评估问题（面试/评审用）

### 基础
1. MCP 协议支持哪些传输方式？如何选择？
2. 如何实现工具级别的访问控制？
3. Rate Limit 策略有哪些？如何防止单个 Agent 耗尽配额？

### 安全
4. OAuth2 的 Refresh Token 如何防止重放攻击？
5. 如何防止 MCP 工具被恶意 Agent 用作攻击跳板（Tool Poisoning）？
6. 审计日志保留多久？如何满足 GDPR/SOC2 要求？

### 扩展
7. 如何实现多 MCP Server 聚合（Federation）？
8. 多租户场景下如何保证数据隔离？
9. 工具热更新如何实现零停机？

### 生产
10. 监控告警阈值如何设置？
11. 故障恢复策略是什么？
12. 如何评估 MCP Server 的性能瓶颈？

---

## 📊 竞品对比（供应商评估用）

| 特性 | MCP Enterprise (本项目) | Python FastMCP | TypeScript SDK | Go MCP |
|------|------------------------|----------------|----------------|--------|
| 语言 | Java 17+ | Python | TypeScript | Go |
| 框架 | Spring Boot 3.4 | FastAPI | Express | net/http |
| OAuth2 | ✅ 完整 | ❌ 需自建 | ❌ 需自建 | ❌ 需自建 |
| RBAC | ✅ 内置 | ❌ | ❌ | ❌ |
| 多租户 | ✅ Row-level | ❌ | ❌ | ❌ |
| Rate Limit | ✅ 令牌桶 | ❌ | ❌ | ❌ |
| 审计日志 | ✅ 内置 | ❌ | ❌ | ❌ |
| Federation | ✅ Gateway | ❌ | ❌ | ❌ |
| Spring AI 集成 | ✅ 原生 | ❌ | ❌ | ❌ |
| Alibaba 集成 | ✅ DashScope | ❌ | ❌ | ❌ |
| Grafana 仪表盘 | ✅ 内置 | ❌ | ❌ | ❌ |
| Docker 部署 | ✅ 多阶段构建 | 手动 | 手动 | 手动 |
| 企业就绪度 | ⭐⭐⭐⭐⭐ | ⭐⭐ | ⭐⭐ | ⭐⭐⭐ |

---

## 💼 适用场景

### 场景 1: 企业内部 AI Agent 工具平台
- 需求：RBAC + 审计 + Rate Limit + 多部门隔离
- 方案：MCP Enterprise + mcp-tenant + OAuth2
- 交付周期：2-4 周

### 场景 2: SaaS 产品 MCP Server 对外发布
- 需求：OAuth2 + 多租户 + Federation + 文档
- 方案：MCP Enterprise + mcp-gateway + 自定义工具
- 交付周期：4-8 周

### 场景 3: 金融/保险行业合规 MCP 平台
- 需求：审计日志 + RBAC + 数据脱敏 + 加密
- 方案：MCP Enterprise + mcp-auth + mcp-tenant
- 交付周期：6-12 周

### 场景 4: 多模型统一接入网关
- 需求：DashScope/OpenAI/本地模型统一接入
- 方案：MCP Enterprise + Federation Gateway + Spring AI
- 交付周期：3-6 周

---

---

## 🛡️ 售前一页纸：攻击面 → 治理模块（V1.66 · 2026-11-08）

> **用途**：投标「威胁建模 / 安全设计」章节、售前白皮书一页纸、安全复测对照表。
> **证据**：Wiz.io 90 天 AI 基础设施蜜罐研究 + 11-03~11-06 NVD 新 CVE 波 + Gartner 首个 AI Agent Management MQ，全部为**公开实证、不含推测**，详见
> [`earnings-report-2026-11-06.md`](earnings-report-2026-11-06.md) 第 1、2 节与 [`earnings-report-2026-11-07.md`](earnings-report-2026-11-07.md) 第 1、2 节。每条都给出「怎么验」。

| # | 真实攻击面（公开实证） | 本方案控制点 | 模块 | 验收方式 |
| --- | --- | --- | --- | --- |
| 1 | `CVE-2026-59822` LiteLLM MCP Gateway 接受**一个字符** bearer token | 签名/aud/iss/exp/scope 全校验，异常 fail-closed 401 | `mcp-gateway` + `mcp-auth` | 发送 `Authorization: Bearer x` 必须 401 且落审计 |
| 2 | `CVE-2026-42271` 伪造 MCP server 配置 `command` 启动矿机，**握手仍正常** | 注册默认 disabled、`command`/shell 字段拒绝、测试端点沙箱 | `mcp-registry` + `mcp-governance` | 提交含 `command` 的注册请求必须拒绝并告警 |
| 3 | `CVE-2026-48710` host-header 绕过 → 链式未认证 RCE（归因 Qilin 勒索） | Host / X-Forwarded-Host 一致性 + 同源策略 | `mcp-gateway` | 伪造 `X-Forwarded-Host` 必须 403 |
| 4 | 从**运行进程内存**取回 proxy master key（磁盘无此文件） | 短期令牌、密钥不明文长驻、凭据访问审计 | `mcp-auth` + `mcp-monitor` | 凭据访问事件必须入审计流并可检索 |
| 5 | 盲提示注入拿 shell（DNS 带外确认、Base64 + Pastebin） | 工具入参校验 + 出站白名单 + 写操作 HITL | `mcp-governance` + `mcp-monitor` | 非常规出站告警；写操作返回 202 待审批 |
| 6 | XMRig 藏 `.claude/unicorn` 伪装 AI 开发工具 | shell 派生 / 异常子进程 / 压缩下载监控 | `mcp-monitor` | 非白名单子进程触发告警 |
| 7 | `CVE-2026-108395` 全 SDK 无界请求体 DoS（官方统一修 4 MiB） | 声明值 + 流式双限，超限 413 | `mcp-gateway` | 发送 >4 MiB 请求体必须 413 |
| 8 | `CVE-2026-111769` 官方 TS SDK 跨源重定向带出 `refresh_token` / `client_secret` | 禁跟随跨源 307/308、自定义头不跨源 | `mcp-gateway` | 跨源重定向请求必须拒绝 |
| 9 | `CVE-2026-123272/123271` OAuth resource confusion（RFC 9728 §3.3 校验缺失） | aud/issuer 强绑定，元数据声明 resource 必须等于所访问 server | `mcp-auth` | 伪造 resource 元数据必须 401 |
| 10 | `CVE-2026-121419` 会话指纹按 access method 名 → 同方法用户会话劫持 | fingerprint = tenant + subject + clientId | `mcp-tenant` | 同方法不同用户会话不得互访 |
| 11 | `CVE-2026-121458` MCP 工具**跳过**主 API 的对象级授权（低权读他人草稿） | 工具级 ACL 复用主 API 同一套对象授权 | `mcp-governance` | 低权用户调用他人资源必须拒绝 |
| 12 | IBM ContextForge MCP Gateway 7 CVE（含默认凭据绕过 **9.8 critical**） | 网关自身纳入加固基线：无默认凭据、最小权限、审计 | `mcp-gateway` | 默认凭据登录必须失败 |
| 13 | 🆕 `CVE-2026-111771`（11-06）官方 TS SDK **不校验 `aud` / `AuthInfo.resource`** → 别的服务签的 token 被本服务接受；**升级后新校验默认关闭** | aud / issuer / resource 三者与 canonical server URL 强绑定，显式启用 `expectedResource` | `mcp-auth` + `mcp-gateway` | 用另一服务签发的有效 token 调本服务必须 401，且拒绝落审计 |
| 14 | 🆕 `CVE-2026-102610`（11-06）`fast-agent serve` 默认绑 `0.0.0.0:8000` + `auth=None` 且无警告，README quickstart 的 `--shell` 让未认证调用方拿到 root shell | 默认只绑 loopback；绑非 loopback 且无鉴权时强告警；生产 profile 禁止 `auth=None`；shell 类工具默认禁用 | `mcp-gateway` + `mcp-governance` | 未配置鉴权时启动必须失败或显著告警；未认证 `initialize` 必须 401 |
| 15 | 🆕 `CVE-2026-123786`（11-06，8.5）Agenta workflow `url` 只校验 scheme/netloc + `follow_redirects=True` 带 Secret token → SSRF 打云元数据 + 重定向跳外带凭据；**同库 webhook 路径已有私网校验、workflow 路径没有** | 出站白名单（含 DNS 解析后私网/元数据地址阻断）、禁跟随跨源重定向、自定义头不跨源、**所有取 URL 的路径共用同一校验器** | `mcp-gateway` + `mcp-auth` + `mcp-monitor` | 提交 `http://169.254.169.254/` 必须拒绝；审计能证明 webhook 与 workflow 走同一校验器 |
| 16 | 🆕 Gartner 首个 AI Agent Management MQ：**各家最深的治理只覆盖自有 runtime，第三方运行时管不住**（Dify 被点名「最强控制只针对内建 agent」、AvePoint「缺实时运行时控制」、Gravitee「只有流量过它的网关才生效」） | 治理层协议/平台无关，与买方指定的 Dify / hiagent 并存，工具接入层统一受同一套策略约束 | `mcp-gateway` + `mcp-governance` + `mcp-registry` | 在买方指定平台上发起的跨运行时工具调用，仍能被本层拒绝并留痕 |

| 17 | 🆕🆕 **`CVE-2026-119025`（11-05 公开完整链）连接路径 RCE**：远程 MCP server 在 OAuth discovery 的 `authorization_endpoint` 返回 `https://evil.test/$(...)`，官方 MCP Inspector 自动打开时 `open` 10.x 在 Windows/WSL 塞进 PowerShell 双引号求值 `$(...)`；**零工具调用、零审批、零模型参与，代码在工具列表出现前执行**；合法 https URL 使「只校验 scheme」失效。兄弟洞 `GHSA-4fc4-wcmh-4g86` **9.6** 可把远程 session 翻转成本地 stdio 执行（不限 OS） | 客户端侧连接路径准入：URL 字符级校验先于 scheme、host allowlist、私网/元数据字面阻断、发现文档大小上限、**浏览器用 argv 不经 shell**、远程目标默认 opt-in | `examples/connect-path-guard/`（客户端）+ `mcp-gateway`（服务端互补） | 提交 `https://evil.test/$([IO.File]::WriteAllText(...))` 必须被拒；正常 `https://auth.example.com/o?a=b&c=d` 必须通过；`ProcessBuilder` argv 中不得出现 `powershell` |
| 18 | 🆕🆕 **「无 CVE 的修复」使扫描器集体失明**：opencode 1.18.33 同类 RCE 以 `refactor(core): share a browser opener` 悄悄修掉，无 CVE / 无公告 → GitHub Advisory DB、Dependabot、SBOM 全部静默；Adversa AI 统计 10 起 Claude Code 相关攻击仅 2 起有 CVE；叠加 LiteLLM `GHSA-7hp6-4w63-5g45`（复用 salt 伪造 proxy-admin → MCP stdio 主机 RCE，与 `59822` 同组件两周连打） | 客户端工具**资产清单 + 自动更新通道 + 版本下限**（Inspector ≥2.12.0 / 导入 `server.json` ≥2.14.0），不把 agent CLI 当库等 CVE；网关凭据签发 salt 隔离、MCP stdio 端点最小暴露 | `mcp-governance`（准入策略）+ `mcp-monitor`（版本基线） + `examples/connect-path-guard/` | 低于版本下限的客户端连接必须拒绝并落审计；伪造 `internal_user` 提权到 proxy-admin 必须失败 |

**可直接引用的签字结论**：

> 本方案**不假设上游 SDK / 代理是安全的**。上述 18 类攻击面全部在 **网关 / 注册中心 / 治理 / 租户 / 监控** 五层设有**相互独立**的控制点，任一层失效不会导致全局放行，且每一类都提供可复现的验收方式。参考实现见 [`examples/governance-hardening/`](../examples/governance-hardening/README.md)、告警规则见 [`examples/monitor-alert-rules/`](../examples/monitor-alert-rules/README.md)，加固配置见 [`mcp-security-hardening-quickstart.md`](mcp-security-hardening-quickstart.md) 第 6 步。

### 📋 采购方视角补充：合规文档也成了评估项（V1.66）

[Peliqan《GDPR-Compliant MCP Servers: The 2026 Procurement Reference》](https://peliqan.io/blog/gdpr-compliant-mcp-servers/) 指出 **ROPA（处理活动记录）** 是 DPA 问询、网络保险承保、SOC 2 审计都会索要的唯一工件，要求 MCP server 自动产出七类元数据：per-tool purpose tag、per-connector 数据分类、数据主体类别、接收方（LLM 供应商 / 下游 SaaS / 审计日志目的地）、第三国转移 SCC、保留期、安全措施（静态与传输加密 / RBAC / 脱敏 / MFA / 审计日志）。自动化 ROPA 每年为 DPO 省 **40–80 小时**。

| ROPA 字段 | 本方案数据来源 | 对应广发 13322 号公告关键指标 |
| --- | --- | --- |
| 安全措施（审计日志） | `JdbcGovernanceAuditSink` / `McpAuditLogger` append-only | 「服务行为自动留痕、**日志不可篡改**」 |
| 数据主体类别 / 隔离 | `McpTenantFilter` / `TenantContext` / `TenantAwareJdbcTemplate` | 「数据权限**按客户经理归属严格隔离**」 |
| 接收方 | `mcp-gateway` 联邦上游 + `mcp-integrations` 出站记录 | 「与券商核心业务系统（客户/账户/交易/留痕）对接」 |
| per-tool purpose tag | `ToolDefinition` + `SkillSpec` 元数据 | 「工具定义与描述」「技能可复用、可编排」 |

> 采购应答提示：**写「符合等保」不如写「这七类元数据由哪个类产生、怎么导出」**。逐条应答对照见 [`gf-securities-tender-response-2026-11-07.md`](gf-securities-tender-response-2026-11-07.md)。


### 🆕 V1.66 补充：HITL 公开规格与云网关分层（应答时引用）

| 公开规格 | 可直接写进验收列的做法 |
| --- | --- |
| [Travel Code MCP 11-06 写操作开放](https://travel-code.com/news/travel-code-mcp-connectors) | 下单/改单/取消/联系旅客**首次调用永不执行**；确认在 server 不在 model；elicitation 由 server 发 Confirm/Cancel 使**模型不能代答**；无 elicitation 时返回绑定 session 与精确参数、**10 分钟一次性码**；命名 OAuth scope；策略在创建订单时校验；外部工具 Always/Ask/Off、工具列表变更即暂停连接待重批、外部返回只当数据不当指令（诚实边界：**无 submit-for-approval**） |
| [Workspace369 CRM MCP（厂商文档 11-07 核）](https://workspace369.com/blog/best-crm-mcp-servers) | 12 类动作默认等待应用内审批、60 分钟过期；**AI 应用不能批准自己的请求**（自批分离）；审计日志**只存字段名不存值**；120 calls/min/connection |
| [Celigo 11 月版](https://www.celigo.com/builders-hub/celigo-november-2026-release) / [AgentCore Gateway](https://dev.to/om_shree_0709/amazon-bedrock-agentcore-gateway-just-became-the-enterprise-mcp-infrastructure-layer-heres-what-m4o) | 从已认证请求解析 caller 身份、无身份 fail-closed 返回空；dynamic listing 按身份实时列工具；elicitation 三模式 |

> **选型会必答**：Azure / AWS / 腾讯云 / Celigo 本周已把注册、动态列工具、鉴权、限流、HITL、可观测做成平台标配 —— 本方案**不与云网关抢流量入口**，采用「云网关管流量与成本 / 本层管责任认定 / 客户端管连接准入」三层分工，能力对照与 5 条买方问答见 [`enterprise-cloud-gateway-comparison-2026-11-08.md`](enterprise-cloud-gateway-comparison-2026-11-08.md)。ROPA 七字段的可运行导出见 [`../examples/audit-ropa-export/README.md`](../examples/audit-ropa-export/README.md)。

**配套采购窗口（应答时引用）**：广发证券《智能金融资讯数据服务平台》供应商征集 **11-13 17:30 截止**（MCP 工具协议 + Skill + 子 Agent + 混合检索，剩 5 天）；同券商《2026 企业微信持续建设项目》把「工具定义与描述 / 参数校验 / 鉴权与权限控制 / 调用限流 / 日志与可观测性」写进资质门槛且指定 Dify/hiagent；山东重工集团级大模型平台二次招标 **11-20 截止**（统一安全管控）；厦大 11-11 / 军队 190 万 11-16 / 中山 399 万 11-17 / 呼市 900 万 11-23 / 中央民大 3712 万 11-25。

---

_本清单随项目版本持续更新。最后更新: V1.66 (2026-11-08)_
