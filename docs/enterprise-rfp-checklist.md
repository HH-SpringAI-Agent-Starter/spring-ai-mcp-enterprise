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

## 🛡️ 售前一页纸：攻击面 → 治理模块（V1.69 · 2026-11-11）

> **用途**：投标「威胁建模 / 安全设计」章节、售前白皮书一页纸、安全复测对照表。
> **证据**：Wiz.io 90 天 AI 基础设施蜜罐研究 + 11-03~11-06 NVD 新 CVE 波 + Gartner 首个 AI Agent Management MQ + **11-08 官方参考服务器七 CVE + 11-10 Dynatrace 双 CVE + 11-10 Siemens Mendix 双 CVE + 11-11 INITE 全生态普查**，全部为**公开实证、不含推测**，详见
> [`earnings-report-2026-11-06.md`](earnings-report-2026-11-06.md) 第 1、2 节、[`earnings-report-2026-11-07.md`](earnings-report-2026-11-07.md) 第 1、2 节、[`earnings-report-2026-11-10.md`](earnings-report-2026-11-10.md) 第 1 节与 [`earnings-report-2026-11-11.md`](earnings-report-2026-11-11.md) 第 1、2 节。每条都给出「怎么验」。

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
| 19 | 🆕🆕🆕 **11-08 官方参考服务器七 CVE（`CVE-2026-41901`~`41907`，ShieldPi）—「参考实现自己就是反面教材」**：`mcp-server-sqlite` `write_query` **只拒 SELECT 前缀** → `VACUUM INTO '/tmp/escaped.db'` 自提交任意路径写全库 + DROP/ALTER/PRAGMA/CREATE TRIGGER 全过（**textbook prefix-check failure**）；`mcp-server-git` 带仓库自身 `core.fsmonitor`/hook 调用 Git → 标注 `read-only` 的 `git_status` 主机任意代码执行；`validate_opened_repo` 只校验 3 条路径、漏 `.git/objects` 与 `objects/info/alternates` → 跨仓库泄露；Desktop Commander 默认 blocklist 可绕过。PyPI 上游**已归档**，补丁无处落地 | **逐工具语句文法 allowlist**（不是禁用关键字黑名单）+ 堆叠语句拒绝 + 注释剥离 + **「只读」注解不参与放行判断** + 独立只读/读写 DB 账号 + `SQLITE_DBCONFIG_DEFENSIVE` / `trusted_schema=OFF` + 越界校验覆盖所有底层实际读取路径 | `mcp-governance`（参数 JSON Schema 校验 + 工具契约）+ `mcp-core`（沙箱）+ 🆕 [`examples/tool-input-grammar/`](../examples/tool-input-grammar/README.md) | 提交 `VACUUM INTO '/tmp/x.db'` / `INSERT...; VACUUM INTO...` / `/*x*/ VACUUM INTO...` 必须全拒；正常 `SELECT 'a;b'` 与三条合法 DML 必须通过（**对照组不得误杀**） |
| 20 | 🆕🆕🆕 **Anthropic 对 STDIO 架构缺陷明确「Won't Be Patched」（11 月，OX Security）— 责任被正式下沉到每个集成方**：`CVE-2026-30615`（Windsurf 零点击 RCE，CVSS 8.0）等 **12+ 同类 CVE**；提示注入静默改写 `mcp.json` → 注册攻击者 STDIO server → 零工具调用/零审批/零模型参与即在工具列表出现前执行；根因在官方 SDK `StdioServerParameters` 对 user-controllable `command`/`args` 不中性化直接 spawn，影响 **Python/TS/Java/Rust 官方 SDK 累计 1.5 亿+ 下载**；**Anthropic / Google / Microsoft 定性「spec-conformant / by design」拒绝修复**；唯一已知缓解 = STDIO command allowlist（`npx`/`uvx`/`python`/`node`/`docker`/`deno`）**且非 SDK 默认**。生态量化：AgentSeal 16,000+ server（1808 个中 66% 有安全发现 / 427 critical）、Endor Labs 2,614 实现中 82% 路径穿越 / 67% 代码注入 | STDIO `command`+`args` 允许列表 + 字符中性化 + 子进程最小权限；**配置文件完整性快照 / 工具描述快照比对 + 安装前只读审计**；不信任「升级官方 SDK」；客户端连接路径 argv 启动（V1.66）+ **解析后真实 IP 校验与 IP 固定**（V1.67，防 DNS rebinding / SSRF） | `mcp-core`（沙箱/子进程）+ `mcp-governance`（准入策略/配置基线）+ `mcp-monitor` + [`examples/connect-path-guard/`](../examples/connect-path-guard/README.md)（`McpConnectPathGuard` + 🆕 `McpResolvedIpGuard`） | `mcp.json` 未经审批被改写必须告警并拒绝加载；非 allowlist 的 `command`（如 `powershell` / 绝对路径二进制）必须拒绝；白名单主机解析到 `127.0.0.1`/`169.254.169.254` 必须拒绝且连接必须使用已固定的 IP |
| 21 | 🆕🆕🆕 **`CVE-2026-123811`（11-10，CVSS 7.5）Dynatrace MCP Server `--http` 模式无鉴权 / 无会话校验 / 无 Host+Origin 防护**：网络可达者（含浏览器经 DNS rebinding）直接发 JSON-RPC `tools/call` → `execute_dql` 读走日志、安全事件、用户会话与可能含的 token，`create_dynatrace_notebook` 留带恶意链接的 tenant-visible notebook；官方缓解第一条即**停用 `--http`、改回 stdio 或只绑 127.0.0.1 并前置带 Host/Origin allowlist 的认证反代** | 传输层默认 loopback、非 loopback 必须鉴权、Host/Origin allowlist 强校验、最小 scope 与凭据轮换、`execute_dql` 类工具纳入审计；STDIO 启动命令与参数 allowlist（V1.68） | `mcp-gateway` + `mcp-auth` + `mcp-monitor` + 🆕 [`examples/stdio-command-allowlist/`](../examples/stdio-command-allowlist/README.md) | 未鉴权 `initialize`/`tools/call` 必须 401；伪造 Host 必须 403；带 `--http` 或 `0.0.0.0` 的启动命令必须被拒（14 攻击用例自检可现场跑） |
| 22 | 🆕🆕🆕 **`CVE-2026-123812`（11-10，CVSS 3.7）写工具漏审批门 + 缺 `destructiveHint` 注解**：`create_dynatrace_notebook` 未调 `requestHumanApproval`，间接 prompt injection 静默植入含攻击者 DQL 的 notebook，租户用户打开即执行；**同一 server 的 `send_event` 因有审批门挡住了同一条攻击链** → HITL 第一次拿到「同体内一正一反」实证 | 所有写工具强制 HITL（审批在 server 不在 model、AI 应用不能自批）、注册期校验 `destructiveHint` / `readOnlyHint` 元数据、工具描述变更须重新审批、审计只存字段名 | `mcp-registry`（注册期准入）+ `mcp-governance`（HITL）+ `mcp-audit` | 无 `approvalId` 的写工具调用必须 DENY；缺 `destructiveHint` 的副作用工具不予注册；审批记录须覆盖工具描述每一版 |
| 23 | 🆕🆕🆕 **11-11 INITE Atlas 全生态普查 + Microsoft Agent Framework 1.25.0：注解谎报与「目录漂移使旧审批失效」**：[INITE 11-11 实测 11,393 个 server](https://inite.ai/en/atlas/mcp/report) —— **25%（2,222）缺 read-only/destructive hint**、**4%（326）删除/发送/支付却标非破坏性**、**23%（2,051）免登录仍会改数据**、47% 参数无描述；[OpenAI 要求三 hint 显式 boolean、Anthropic 用 hint 决定 auto-permissions 且拒 catch-all](https://dawloom.com/blog/submit-mcp-server-to-chatgpt-and-claude)；[MS Agent Framework Python 1.25.0（11-09）](https://www.cloudproinc.com.au/index.php/2026/11/11/microsoft-agent-framework-mcp-approval-policies-governance-guide) 上线 callable discovery approval policies，并明确「**metadata ... an input to your policy rather than the final security decision**」、要求 **catalogue 刷新后复验审批** → hint 只是策略输入，**上游改了描述而旧审批仍有效 = 静默越权** | 注册期三 hint 显式 / 矛盾 / 谎报只读 / openWorld 谎报 / catch-all 五类硬校验 + **服务端独立定级不采信声明** + 审批流水号绑定 `tool + 元数据指纹 + subject` + catalogue 变更即作废 + READ 才免确认、WRITE/DESTRUCTIVE **fail-closed 审批门** + override 留痕且 DESTRUCTIVE 禁 override + append-only 脱敏审计 | `mcp-registry` + `mcp-governance` + `mcp-auth` + `mcp-audit` + 🆕 [`examples/tool-annotation-gate/`](../examples/tool-annotation-gate/README.md) | 把 `delete_customer` 三个 hint 全改成只读友好值仍须定级 DESTRUCTIVE；审批后改写工具描述、用旧流水号调用必须 DENY；无 approver / approver 抛异常必须 DENY（**32 断言自检可现场跑**） |
| 24 | 🆕🆕🆕 **Siemens SSA-710379（11-10）Mendix Studio Pro 11 内置 MCP Server 双 CVE —「绑 localhost 就安全」被官方否掉**：[`CVE-2026-112136`](https://severitydaily.com/siemens-mendix-studio-pro-mcp-server-cve-2026-112136-8-8-cvss-3-1-ui-r-vs-9-2-cvss-4-0-ui-n-at-p-cwe-1327-loopback-port-7782-ssa-710379-v11-16-0-v11-12-7-cve-2026-112137-csrf-origin-content-type) CWE-1327 未限制 HTTP listener 到 loopback（CVSS 3.1 **8.8** / 4.0 **9.2**，端口 7782）；`CVE-2026-112137` CWE-352 未校验 `Content-Type` 与 `Origin`（5.4 / 5.1）→ 恶意网页让开发者浏览器向本地 server 发请求；与 `CVE-2026-123811`（Dynatrace `--http` 无鉴权 / Host+Origin）、[Adversa 11 月榜](https://adversa.ai/blog/top-mcp-security-resources-november-2026)（server-everything wildcard CORS + `get-env` 整包环境变量、Basic Memory DNS rebinding、FastMCP `HostOriginGuardMiddleware` **默认关闭**）**同构** | 启动期只绑 loopback、拒 `0.0.0.0` / `::`、非 loopback 必须显式 opt-in **且**强制鉴权；请求期 Content-Type 严格 `application/json`、Origin / Host 白名单 + `X-Forwarded-Host` 一致、禁 wildcard CORS、未鉴权 401、禁 `get-env` 类工具 | `mcp-gateway` + `mcp-auth` + 🆕 [`examples/local-listener-guard/`](../examples/local-listener-guard/README.md)（与 [`examples/connect-path-guard/`](../examples/connect-path-guard/README.md)、[`examples/stdio-command-allowlist/`](../examples/stdio-command-allowlist/README.md) 构成出站 / 入站 / 子进程三层） | 提交 `text/plain` / 缺 Origin / 伪造 Host / `Access-Control-Allow-Origin: *` 必须全拒；正常 `application/json; charset=utf-8` 与本地 CLI 无 Origin 必须通过（**对照组不得误杀**，30 断言自检可现场跑） |

**可直接引用的签字结论**：

> 本方案**不假设上游 SDK / 代理 / 工具声明是安全的**。上述 **24 类**攻击面全部在 **网关 / 注册中心 / 治理 / 租户 / 监控** 五层设有**相互独立**的控制点，任一层失效不会导致全局放行，且每一类都提供可复现的验收方式。参考实现见 [`examples/governance-hardening/`](../examples/governance-hardening/README.md)、告警规则见 [`examples/monitor-alert-rules/`](../examples/monitor-alert-rules/README.md)、工具入参文法见 [`examples/tool-input-grammar/`](../examples/tool-input-grammar/README.md)、连接路径与解析后 IP 校验见 [`examples/connect-path-guard/`](../examples/connect-path-guard/README.md)、STDIO 启动命令 allowlist 见 [`examples/stdio-command-allowlist/`](../examples/stdio-command-allowlist/README.md)、🆕 **注册期注解完整性与审批门**见 [`examples/tool-annotation-gate/`](../examples/tool-annotation-gate/README.md)、🆕 **本地监听器准入**见 [`examples/local-listener-guard/`](../examples/local-listener-guard/README.md)，加固配置见 [`mcp-security-hardening-quickstart.md`](mcp-security-hardening-quickstart.md) 第 6 步。
>
> **2026-11-09 更新依据**：官方 `modelcontextprotocol/servers` 参考实现被一次性披露 7 个 CVE，且 Anthropic 对 STDIO 架构缺陷明确不修 —— 「升级官方依赖」不再是可用的缓解路径，**责任在集成方**。
>
> **2026-11-10 更新依据**：Dynatrace MCP Server 双 CVE 用**同一个 server 的一正一反**证明审批门真实生效（有门挡住 / 漏门成入口），且 `--http` 无 Host+Origin 防护再次把攻击面下沉到传输层；UiPath×Google Cloud 把「治理」写成受监管行业允许 MCP 接入的**前提**，浙商证券把 7 项法规写成**强制测评依据清单** → 治理从「定价权」升级为「**准入权 / 法律责任分界线**」。
>
> **2026-11-11 更新依据**：INITE Atlas 当日 11,393 个 server 普查把「注解谎报/缺失」从个案变成**可量化的生态基线**（25% 缺 hint / 4% 谎报 / 23% 免登录改数据），Microsoft Agent Framework 1.25.0 与 Google Cloud Agent Registry 把「发现期逐工具审批 / 逐工具 IAM」做成产品层并明确**元数据不可信、目录刷新须复验** → 治理的起点被推回「**工具注册进来的那一刻**」；Siemens Mendix 双 CVE 则从商业 IDE 侧证明「绑 localhost」不是安全边界。

### 📋 采购方视角补充：合规文档也成了评估项（V1.67）

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

**配套采购窗口（应答时引用，11-09 刷新）**：🆕 **广发证券第三份《2026 态势感知及安全分析平台扩容项目》11-12 17:30 截止（最紧）** —— 含「**MCP 安全工具管理** + 安全技能库 + 6 个安全智能体 + AI 安全数据底座（统一日志模型/实体识别与关联）」；广发《智能金融资讯数据服务平台》**11-13 17:30 截止（13321 延长）**与《2026 企业微信持续建设项目》**11-15 17:30 截止**（⚠️ **V1.68 口径修正**：此前误记为 11-13，官方原文为 11-15；三份分别为 11-12 / 11-13 / 11-15；后者把「工具定义与描述 / 参数校验 / 鉴权与权限控制 / 调用限流 / 日志与可观测性」写进资质门槛且指定 Dify/hiagent）；🆕 **银行智能体平台「底座期」** —— 兴业（全生命周期纳管）、北京农商（开发/纳管/调用/评测/观测/运营 + ≥50 个 Skill）、上海农商（**MCP 插件市场** + 权限管理）、中原（安全围栏 + 企业级 AI 网关）；山东重工集团级大模型平台二次招标 **11-20 截止**（统一安全管控）；厦大 11-11 / 军队 190 万 11-16 / 中山 399 万 11-17 / 呼市 900 万 11-23 / 中央民大 3712 万 11-25。
⚠️ 全部为**预征集 / POC 征集（非采购邀请）**，投标前须核官方原文与自身资质；逐条应答见 [`bank-agent-platform-tender-response-2026-11-09.md`](bank-agent-platform-tender-response-2026-11-09.md) 与 [`gf-securities-tender-response-2026-11-07.md`](gf-securities-tender-response-2026-11-07.md)。

### 🆕 V1.67 补充：金融机构「受控只读接入」成为采购基线（11-08~11-09）

| 公开事实 | 可直接写进验收列的做法 |
| --- | --- |
| [The Telegraph 11-08：英国五大行（汇丰/Nationwide/NatWest/巴克莱/劳埃德）集体封禁外部 AI Agent](http://mp.weixin.qq.com/s?__biz=MzY4MzQ2MDgxMA==&mid=2247483715&idx=1&sn=a81636ba0d1e9736e600b18ce06906d2) + 麦肯锡测算「5~10% 存款迁移 → 存款利润降至少 1/5，极端 7000 亿英镑」+ FCA 制定规则 + 嘉信每周主动拦截 10~20 客户 | **外部 Agent 默认只读**：读工具默认放行、写工具默认 HITL、交易类工具默认禁用；强认证在**服务端**校验，不接受模型自称身份；强认证不可被 Agent 绕过；逐工具 scope 与数据归属隔离；每笔调用留痕可回溯到「哪个员工、用了哪个工具、处理了什么文件」 |
| [北京青年报 11-09：朝阳法院 AI 办公劳动争议判例](http://mp.weixin.qq.com/s?__biz=MzY4MzQ2MDgxMA==&mid=2247483715&idx=1&sn=a81636ba0d1e9736e600b18ce06906d2) | 治理颗粒度下沉到**个人**：提供「哪些能用外部工具、哪些绝不能出内网」的工具级清单；AI 生成内容纳入留痕与复核范围（幻觉 / 泄密 / 版权三类隐患） |
| [腾讯云开发者社区《MCP 认证与安全：授权是可选的，责任不是》](https://cloud.tencent.com/developer/article/2779058) | 授权在 MCP 规范里是 **OPTIONAL**：必须显式实现 token `aud`/`resource` 校验、discovery 元数据 URL 不得直达内网、**工具描述变更须重新审批**（审批记录不能只有最初那一版） |

> **选型会必答（V1.66 升级版）**：Azure / AWS / 腾讯云 / **Google Cloud（11-08 文档更新，中央注册表 + Agent Gateway 强制 IAM + 不接受 API Key + 为 MCP 工具建独立身份）** 已把注册、动态列工具、鉴权、限流、HITL、可观测做成平台标配 —— 本方案**不与云网关抢流量入口**，采用「云网关管流量与成本 / 本层管责任认定 / 客户端管连接准入 / 工具实现管入参文法」**四层分工**。Strac 8 家横评金句可直接引用：「**如果 enterprise 意味着适配既有采购合同，云巨头赢；如果意味着通过数据保护审查，候选名单短得多**」。能力对照与 5 条买方问答见 [`enterprise-cloud-gateway-comparison-2026-11-08.md`](enterprise-cloud-gateway-comparison-2026-11-08.md)，报价锚见 [`mcp-governance-pricing-anchor-2026-11-09.md`](mcp-governance-pricing-anchor-2026-11-09.md)。

### 🆕 V1.68 补充：金融强制测评依据写进「验收列」（11-10）

[零壹智库 10 月金融智能体招标复盘](https://new.qq.com/rain/a/20261110A0DO1L00)：**浙商证券**请第三方对已部署智能体做合规安全测评，招标文件**附强制依据清单**；同期 10 月中标 **15 条 / 12 条披露金额合计 1652.8 万元**（下半年新低）、国投证券把一个需求**拆成四单**分别定标、12 家具名中标方仅 2 家拿到两单。→ **测评依据从「建议参考」变成「强制清单」，且细分件可独立比价**。

| 强制依据 | 本清单对应验收列 | 可验收工件 |
| --- | --- | --- |
| `GB/T 45654-2025` 生成式 AI 服务安全基本要求 | 第 7 类（4 MiB / fail-closed） | `examples/governance-hardening/BoundedRequestWrapper.java` |
| 《人工智能安全治理框架 2.0》 | 第 2、22 类（默认 disabled / 写工具 HITL） | `application-hardening.yml` |
| 《网络安全法》 | 第 3、17、21 类（Host/Origin / 连接路径 / 解析后 IP） | `examples/connect-path-guard/` |
| 《数据安全法》 | 第 9、13 类（aud/resource 强绑定、token 不转发） | `mcp-governance-regulatory-compliance-2026.md` |
| 《个人信息保护法》 | 第 5 类（脱敏）+ ROPA 七字段 | `examples/audit-ropa-export/RopaExportService.java` |
| 《生成式人工智能服务管理暂行办法》 | 第 6 类 + 审计只写不删 / traceparent | `mcp-audit` + `mcp-monitor` |
| 《证券期货业网络和信息安全管理办法》 | 第 19、20、21 类（入参文法 / STDIO allowlist / 传输准入） | `examples/tool-input-grammar/`、`examples/stdio-command-allowlist/`、`examples/monitor-alert-rules/` |

> ⚠️ **诚实边界**：本表是**技术控制点映射**，不构成「已通过第三方测评」声明；本项目无等保测评报告、无算法备案、无金融行业验收案例。逐条应答包见 [`financial-agent-tender-compliance-2026-11-10.md`](financial-agent-tender-compliance-2026-11-10.md)。

---

### 🆕 V1.69 补充：注册期注解完整性验收列（11-11）

[INITE Atlas 11-11 全生态普查](https://inite.ai/en/atlas/mcp/report) 给出可引用的现状风险数字：**11,393** 个 server 中 **25% 工具缺 read-only/destructive hint**、**4% 删除/发送/支付却标非破坏性**、**23% 免登录仍改数据**、47% 参数无描述、52% 免登录即响应。[PowerDuck](https://www.powerduck.com/blog/mcp-tool-annotations-readonly-destructive) 与 [CSDN 信任模型](https://devpress.csdn.net/aibjcy/6af303c1e5f4ca4eff8fa677.html) 同时写明「**hints, not enforcement**」→ 采购文件应把「工具注册期」而非「客户端确认框」写成验收位。

| # | 注册期验收项 | 控制点 | 现场验收方式 |
| --- | --- | --- | --- |
| 1 | 三 hint 显式 boolean | 缺一拒收；缺 hint 按最坏档推断 | 提交缺 `destructiveHint` 的工具定义必须 REJECT |
| 2 | 声明自相矛盾 | `readOnlyHint && destructiveHint` 拒收 | 提交矛盾声明必须 REJECT |
| 3 | **谎报只读** | 副作用动词（中英文）与只读声明冲突即拒 | `delete_customer` 标 `readOnlyHint:true` 必须 REJECT |
| 4 | openWorld 谎报 | 只读 + `openWorldHint:true` 即拒 | 「读远程发票」标只读必须 REJECT |
| 5 | catch-all 工具 | `api_request` 类 + `method` 参数拒收（Anthropic 目录规则） | 通用请求工具必须 REJECT |
| 6 | 整包环境变量导出 | `get-env` 默认禁用 | 调用必须 DENY |
| 7 | **独立风险定级** | `classify()` 不采信声明 hint | 三个 hint 全改成只读友好值，删除类仍须 DESTRUCTIVE |
| 8 | 服务端身份 | 不接受模型自称 `role=admin` | 无 `subject` 调用必须 DENY |
| 9 | 写工具审批流水号 | 绑定 `tool + 指纹 + subject` | 无 / 伪造流水号必须 DENY |
| 10 | **审批器 fail-closed** | 缺失 / 异常 / 返回 false 一律拒 | 注入抛异常的 approver 必须 DENY |
| 11 | **目录漂移复验** | 元数据任一变更即作废旧审批（MS 1.25.0） | 审批后改描述、用旧流水号调用必须 DENY |
| 12 | override 管控 | DESTRUCTIVE 禁 override；WRITE 须带理由并记「谁 / 绕过哪条策略 / 理由 / 结果」 | 无理由或破坏性 override 必须 DENY |
| 13 | 审计完整性 | append-only + 密钥值脱敏 + **拒绝分支同样留痕** | 传 `api_key=sk-live-...` 后审计不得出现明文 |
| 14 | 传输层配套 | 见第 24 类 | `examples/local-listener-guard/` 17 攻击用例现场跑 |

> 完整矩阵（四家规则对位 / 竞品差异化 / 买方必问 5 问 / 24-48-96 小时行动清单 / 三档报价）见 [`tool-annotation-integrity-governance-2026-11-11.md`](tool-annotation-integrity-governance-2026-11-11.md)。
> ⚠️ **诚实边界**：14 项对应的是 `examples/` 下**参考实现**，尚未接入 `mcp-registry` 主模块；副作用动词表为启发式，fingerprint 只覆盖元数据不覆盖实现代码，基于数据分类的差异化策略本版只留字段、未实现引擎。INITE 百分比为描述与 hint 的公开自动比对，**不代表已被利用**。

---

_本清单随项目版本持续更新。最后更新: V1.69 (2026-11-11) · 攻击面 22 → 24 类_
