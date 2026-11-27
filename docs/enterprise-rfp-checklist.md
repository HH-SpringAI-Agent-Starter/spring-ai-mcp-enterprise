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

## 🛡️ 售前一页纸：攻击面 → 治理模块（V1.73 · 2026-11-15）

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
| 25 | 🆕🆕🆕 **11-11 AWS `mcp-gateway-registry` 双 CVE 链式提权 —— 注册中心自己成了攻击入口**：[公告 `2026-179-AWS` / `2026-180-AWS`](https://al-ice.ai/posts/2026/11/aws-mcp-gateway-registry-cve-2026-100307-125926-privesc-ssrf-november-2026) —— `CVE-2026-100307`（8.8）`/api/servers/*` 管理端点**只认证不授权**，低权限用户经 `POST /api/servers/groups/import` 把自己的 IdP group 映射到 `mcp-registry-admin`，**管理员身份按请求派生**、下次请求即生效、无需重新认证，随后可读走注册表中**读时解密**的 server 凭据与自定义 header；`CVE-2026-125926`（8.5）注册/修改 API 可把网关上游指向内网与**云实例元数据端点**并读回响应。**第一个洞发出的正是第二个洞所需的权限**。叠加 [`CVE-2026-109492`（Microsoft `mcp-gateway` IDOR / CWE-639，8.8）](https://radar.offseq.com/threat/cve-2026-109492-cwe-639-authorization-bypass-through-user-controlled-key-in-microsoft-mcp-gateway-792138926698577a)、`CVE-2026-125271`（操作名归一化错误致策略绕过）、[dev.to 横评](https://dev.to/kuldeep_paul/top-5-mcp-registry-tools-for-enterprise-ai-governance-2026-52ap)「Official MCP Registry 的 Access Control Model = **None**」 | **认证 ≠ 授权**：每个管理操作显式判 required permission；**组导入禁止写入任何特权 scope**（归一化后判 admin/root/superuser/policy）；**授权源独立、只读、不可变，绝不由注册表可写数据派生**；管理员身份**不隐含** `CREDENTIAL_READ`；凭据读取审计只记字段名；策略匹配前做操作名归一化 | `mcp-registry` + `mcp-auth` + `mcp-governance` + 🆕 [`examples/registry-admin-guard/`](../examples/registry-admin-guard/README.md) | 低权限主体调 `registerServer` 必须 DENY；`mcp-registry-admin` / `MCP_REGISTRY_ADMIN` / `mcp.registry.admin` 三种变形全 DENY；导入后授权集合引用与内容不变且不可变；bootstrapAdmin 主体读凭据必须 DENY；`get_user`/`GetUser`/`Get__User` 必须落到同一策略键（**45 断言自检可现场跑**） |
| 26 | 🆕🆕🆕 **上游 SSRF 到内网 / 云元数据 + 「升级后存量未复验」**：`CVE-2026-125926` 允许把网关上游指向 `169.254.169.254` 等云元数据与内网服务；**官方修复边界更致命** —— 1.35.0 **只在注册 / 修改时校验上游，升级前已存储的 server 配置保持不变**，升级也不撤销已植入的 group 映射、不使已泄露凭据失效 → **打补丁 ≠ 清干净**，官方处置要求「逐个复查所有已注册 server 上游 + 回溯异常注册与变更日志」。对运维场景尤其危险：可观测 / 日志 / 自动化平台**本来就在内网**（对位 [广发 13327 目标第 7 条](http://gfjc.gf.com.cn/gyszjgg/13327.jhtml)「MCP 封装接入」）。同周 [fast-agent `102610` 11-11 复测](https://agentopsguide.com/fast-agent-vulnerability-cve-2026-102610) 再证「绑 localhost 就安全」不成立 | 上游仅 https、禁 userinfo / 反斜杠歧义，主机准入覆盖 loopback·私网·link-local·`169.254.169.254`·metadata 域·IPv6 ULA·十进制与八进制 IP 字面量；**重定向逐跳重校验 + Host/SNI 一致性**（兑现 V1.69 待办）；修改上游 = 重新注册并重签指纹；★ **升级 / 策略变更后全量复验存量**：重跑准入 + 配置指纹漂移比对 + 不通过即隔离 + **幂等** + append-only 留痕 | `mcp-gateway` + `mcp-registry` + `mcp-monitor` + 🆕 [`examples/registry-admin-guard/`](../examples/registry-admin-guard/README.md)（与 [`connect-path-guard/`](../examples/connect-path-guard/README.md) 的 DNS 解析后真实 IP 校验叠加） | 14 个攻击 URL 全 DENY 且 `203.0.113.10` / 公网显式端口放行（**对照组不得误杀**）；同域相对路径重定向 ALLOW、跳到 `169.254.169.254` 或跨主机 DENY；带外污染一个已注册项 → `reverifyAll()` 第一次抓到并隔离、第二次不再重复报；⚠️ 主机判定为字面量检查，须与 DNS 解析后 IP 校验叠加 |
| 27 | 🆕🆕🆕 **11-10~11-12 pgEdge Postgres MCP Server 7 CVE —— 「read_only 声明」被「执行层」整批击穿**：`CVE-2026-126196`(8.1) `query_database` 在 read_only 连接上以**简单查询协议**经 `pgx Exec` 发出未识别语句，`SELECT 1; UPDATE ...` 分号堆叠 + 事务/会话状态改写，**池化会话残留让后续调用变可写**；`126197`(8.8) `pl-do` 把 JSON 参数插值进固定 dollar-quote 标签的 `DO` 块，`$mcp_custom_tool$`/`$mcp_args$` 提前闭合注入任意 SQL；`126199`(6.8)/`126198`(3.1) 只按**首个关键字**分类语句与写确认；`125322`(6.5) `count_rows` 的 `where` 接受声明表之外的子查询；`125321`(6.5) HTTP 身份取**最左 XFF**；`125320`(5.3) 不校验 `Origin` 可 DNS rebinding 读库。统一修 1.1.0，**7 个洞没有一个靠把 read_only 标签贴牢修好** | **声明层不得参与授权**：注解 / 工具描述 / 连接标签只作展示；执行层 6 控制点 —— 词法归一化后**全文**动词扫描 + 堆叠计数 + dollar-quote 逃逸 + 强制参数绑定 + 服务端 `SET TRANSACTION READ ONLY` + where 作用域 + 池化会话 RESET + Origin/Host/身份 + 执行环境净化 + 校验/实际路径同源 | `mcp-governance` + `mcp-gateway` + `mcp-auth` + 🆕 [`examples/read-only-enforcement/`](../examples/read-only-enforcement/README.md) | 把 `readOnlyHint=true` + `read_only` 连接标签配到 `SELECT * INTO stolen` 上必须 **DENY**；`SELECT 1; UPDATE ...`、`SELECT $mcp_custom_tool$; DROP...`、CTE 内 `UPDATE` 全 DENY；`SELECT 'DROP TABLE t; DELETE FROM t'`、`SELECT 'a;b'`、只读 CTE、dollar-quote 字符串必须 **ALLOW**（**对照组不得误杀**）。`79 断言自检可现场跑`，其中 20 条为旧基线漏掉的增量拦截 |
| 28 | 🆕🆕🆕 **11-11 官方 `modelcontextprotocol/servers` 5 GHSA —— 「只读工具」照样执行代码 / 修复不完整 / 校验与执行不同源**：`GHSA-6hqx-gqrx-c5r2`(7.8) `mcp-server-git` **只读工具**信任仓库自身 `.git/config` 的 `core.fsmonitor` → 任意命令执行；`GHSA-q74p-4wrc-q7w6`(5.3) 对 10 月 `w766` 的 `validate_opened_repo()` **修复不完整**，`objects/info/alternates` + linked-worktree `common_dir` 混淆再次绕过 `--repository`；`GHSA-p9cv`(4.4) 验证后才替换 symlink（TOCTOU）读越界文件；`GHSA-f9hh`(3.1) `robots.txt` 校验路径 `//private` 与实际抓取 `/private` 规范化不一致；`GHSA-3rxj`(3.1) SSRF 分类器把 IPv6 site-local `fec0::/10` 误判 public（同源还有 `66qx` NAT64 前缀、`v38p` IDNA 解析不一致、`3jqg` 重定向不复核 robots） | 只读工具**执行环境净化**（禁加载目标仓库/工作区自带可执行配置与 hook）；修复须对历史绕过形态做**回归复验**而非只验本轮补丁；校验路径与实际执行路径**同源**且校验后不得重新解析；解析后真实 IP 分类覆盖 IPv6 site-local / NAT64 / 十进制·八进制字面量 | `mcp-core`（沙箱）+ `mcp-governance`（准入/基线）+ `mcp-registry`（存量复验）+ 🆕 [`examples/read-only-enforcement/`](../examples/read-only-enforcement/README.md)（CP5/CP6）+ [`examples/registry-admin-guard/`](../examples/registry-admin-guard/README.md) + [`examples/connect-path-guard/`](../examples/connect-path-guard/README.md) | 只读工具处理的仓库内放恶意 `core.fsmonitor` 必须既不执行也不加载；`Disallow: /private` 下抓 `//private` 必须 DENY；`fec0::/10`、`64:ff9b::/96` 一律不得判为 public；跑一次 79 断言自检须含 8 条堆叠/逃逸载荷与 9 条合法只读对照 |
| 29 | 🆕🆕🆕 **11-12 arXiv 量化实证 + VS Code / 官方 git server / llm 三连补丁 —— 「审批与提示词护栏本身也不是安全边界」**：[MindPattern 11-12 复盘](https://mindpattern.ai/s/2026-11-12-approval-prompts-and-repo-config-never-kept-anyone-safe-and-three-mcp-patches-prove-i) —— Qwen2.5-1.5B/7B、13 任务 12 工具、每防御每模型 156 攻击 episode：模型提出攻击者工具调用 **64% / 84%**；**in-prompt guardrail 栈被持久自适应攻击者推到 100% 攻击成功率**；而「任务绑定的模型外确定性 reference monitor」只放行 **1.3% / 7.7%**、severe actions 保持 **0%**。同期 [VS Code `GHSA-66hq-jw93-462h`（8.3 / NVD 9.6）](https://mindpattern.ai/s/2026-11-12-approval-prompts-and-repo-config-never-kept-anyone-safe-and-three-mcp-patches-prove-i) MCP server 返回的 elicitation 文本被解释成交互式 command link、用户点通知即命令执行（1.145.3 改纯文本渲染 + 校验 payload）；官方参考 git server `GHSA-6hqx-gqrx-c5r2` 只读 `git_status` 因仓库自带 `core.fsmonitor` 执行代码（修法 `GIT_CONFIG_COUNT` 强制 false + 清 filter/merge drivers + 拒 Git<2.31）；`llm` 0.37.2 两处把配置的 OpenAI key 发给兼容端点 → **共同判词「客户端信任了服务端发来的东西」** | **CP1 授权源隔离 + CP2 任务绑定 fail-closed**：决策**只读**控制平面按当前任务签发的 `TaskGrant`，模型自证（`modelAttestation`）/ 工具注解（`readOnlyHint`）/ 审批弹窗结果**全程只记审计、不参与授权**；非控制平面签发的 grant 直接拒；`(taskId, tenant, tool, verb, scope, objectId)` 精确匹配、默认拒绝、时间窗与动词白名单 | `mcp-governance` + `mcp-core` + 🆕 [`examples/task-bound-monitor/`](../examples/task-bound-monitor/README.md) | `modelAttestation="HITL-approved"` + `verb=DELETE` 必须 DENY 且审计出现 `IGNORE`；`readOnlyHint=true` + `verb=DROP` 必须 DENY；跨任务重放 `T-999` / 过期 / tenant 改 `evil` / `account_id` 改写全 DENY；**8 个合法调用（含参数带 `DROP TABLE`、`ignore previous instructions`）必须全放行**；`51 断言自检可现场跑`，含「不得劣于既有基线」对照组断言 |
| 30 | 🆕🆕🆕 **11-13 IBM ContextForge MCP Gateway 双 CVE（官方 Bulletin 首发）+ AWS `125926` 版本区间坐实 —— 「网关自己的配置导入也不是安全边界」**：[`CVE-2026-110178`（4.1）](https://www.ibm.com/support/pages/node/7296696) `/v1/import` 的 `conflict_strategy=update` **不校验共享实体归属**，持 team-scoped `admin.import` 的远程认证者可覆写**其他租户**公共 tool/resource/prompt 的 `endpoint URL`、`request headers`、`authentication credentials`，把后续 AI 网关调用重定向到攻击者主机，**官方 Workarounds: None**；[`CVE-2026-110194`（5.4）](https://www.ibm.com/support/pages/node/7296701) OAuth DCR 从 authorization server metadata 拿到的 registration endpoint **未校验即请求** → SSRF 打内网（缓解 `DCR_ALLOWED_ISSUERS` 白名单 / `DCR_ENABLED=false`）。[AWS `CVE-2026-125926`](https://cybersecurity-see.com/aws-issues-warning-about-mcp-gateway-registry-vulnerability-that-could-enable-attackers-to-access-internal-network-services) 影响 **v1.0.3~v1.34.0**、**1.35.0 修复**（1.25.0 只修管理 API 洞 → 「跑 1.25.0+ 仍中」），且官方明确**升级不改升级前已存配置** → 必须人工全量复验 + 轮换云角色凭据。[Permit.io 11-13](https://www.authorizon.com/blog/mcp-gateway-security) 补三条规范判据：`adapter:create` 独立权限 / 内部 ID 不由用户可控名称派生（409 是安全信号不得吞）/ 每个决策都带 tenant | **CP3 导入归属 + CP4 上游准入 + CP5 存量复验**：跨租户覆盖一律拒；`endpoint/headers/credentials` 为受保护字段、导入路径不得改写；owner 变更走显式 transfer；同名冲突不得被 `skip`/`update` 吞；DCR 默认关闭 + issuer 白名单 + 上游字面量校验（十进制/八进制/IPv6 site-local/云元数据）+ 解析后真实 IP 复检；★ 升级后对已存配置**全量复验、指纹漂移即隔离、幂等、可导出报告** | `mcp-registry` + `mcp-tenant` + `mcp-gateway` + 🆕 [`examples/task-bound-monitor/`](../examples/task-bound-monitor/README.md)（与 [`registry-admin-guard/`](../examples/registry-admin-guard/README.md)、[`connect-path-guard/`](../examples/connect-path-guard/README.md) 叠加） | 8 个导入用例（跨租户 / `authorization` / `request_headers` / `owner_tenant` / 同名冲突）按预期判定；11 个上游用例含 `2852039166`、`0177.0.0.1`、`[fec0::1]`、`169.254.169.254` 按预期判定；干净/投毒/漂移/畸形四条存量 → 1 放行 3 隔离且复验两次一致；把 `Bearer SUPERSECRET123` 传进导入请求，审计串中**不得出现明文** |
| 31 | 🆕🆕🆕 **11-13~11-15 配置面 / 连接建立期三层同破 —— 「治理权可以中立，授权不能外包」**：[IBM ContextForge 第三个 CVE `CVE-2026-110199`（4.8，11-13）](https://junglewise.ai/threats/cve-2026-110199-ibm-contextforge-mcp-gateway-1-0-0-through-1-0-11-is-vulnerable) 管理后台从 URL 参数读 status/error 通知，客户端 HTML sanitizer 被 **mutation XSS（mXSS）** 绕过 → 管理员会话任意 JS / 凭据窃取；与同日 `110178`（跨租户导入覆写凭据，官方 Workarounds: None）、`110194`（DCR metadata SSRF）合起来正好打穿**管理面 UI / 配置导入 / 连接建立**三层，统一修 v1.1.0。[n8n `CVE-2026-126386`（4.7）](https://aithreatalert.com/cve/CVE-2026-126386) 未认证 `/mcp-oauth/register` 接受任意 `redirect_uri` → 用**可信 n8n 域名**钓鱼，与 `110194` 同源（外部 URI 未校验即信任）。同期 [pgEdge `CVE-2026-126197`（8.8）官方描述](https://threatmonitor.io/cve/CVE-2026-126197) 坐实「**simple query protocol 让注入携带额外语句与事务控制，绕过 `read_only`**」 | 🆕 **配置面准入 SPI**（`McpControlPlaneInputGate`）：issuer 白名单 + 域锚定 + **解析后真实 IP** 公网分类（拒 `169.254.169.254` / 私网 / 八进制·十进制字面量 / IPv6 site-local / CGNAT）；`redirect_uri` 与已注册值精确相等且拒 `user@` / `//host` / `%2f%2f` / http 降级；导入校验共享实体归属 + 受保护字段（endpoint/headers/credentials）不可写；管理后台 status/error **服务端转义 + 纯文本渲染**、长度上限；规格版本变更触发全量复验、旧审批不继承；调用期机械 scope allowlist 拒堆叠语句 / 写动词 / `/*!` 可执行注释 | `mcp-gateway` + `mcp-tenant` + `mcp-registry` + 🆕 [`examples/gate-orchestration/`](../examples/gate-orchestration/README.md) | 提交 11 组 mXSS 载荷、8 类 SSRF IP、8 类 redirect 混淆、跨租户导入各一次，全部 DENY 且审计写明 `rule`；合法中英文纯文本 / 精确匹配 redirect / 同租户导入全部 ALLOW（**26 条合法对照 0 误杀**）；`java McpGateChain` → **123 断言全绿** |
| 32 | 🆕🆕🆕 **AAIF 厂商中立治理 + DE-CIX 门测试 A/B —— 「规格与语义评分都不是安全边界」**：MCP（2025-12-09）与 Agent Skills（2026-10-22）归入 [Linux Foundation · AAIF](https://aigovernance.com/news/mcp-and-agent-skills-move-to-vendor-neutral-governance-under-linux-foundation)，规格变更**不再有单厂商补丁通知**且可能改变 agent 权限 / 认证方式 / 工具访问边界 → 既有安全审批基准须重评、采购模板须要求披露 MCP / Agent Skills 规格版本依赖；[ScriptMasterLabs 对 DE-CIX MCP server 的门测试](https://bizarro.dev.to/scriptmasterlabs01/can-ai-agents-safely-order-network-connections-8-capabilities-counted-5-of-them-procurement-35gj) （8 能力中 **5 个采购动词**）：完整句子「procure and deploy **without asking first**」拿 **0.803 auto-act 放行**，「book and scale after operator confirmation」0.8488 仍放行，而 **scope 形式**同一动作 0.50 advisory hold → 原文「**enforce the allowlist mechanically on the tool call itself; use a confidence score over prose as a second opinion, never as the lock**」；AWS AgentCore Gateway GA 把 `outputSchema` / `readOnlyHint` / `destructiveHint` 做成产品层输入，进一步证明**注解越标准化越不能当授权依据** | `specRevision` 进 `GateContext`、变更即 `SPEC_REVISION_CHANGED_REVALIDATE_ALL`（幂等读 + 显式隔离）；**机械 scope allowlist 是唯一的锁**，`modelConfidence` / prose / 审批弹窗文本 / 模型自证 / hint **一律只读审计、不进允许分支**；AND 语义短路 + 全量留痕 + 字段白名单值脱敏 + 每条决策写明 `enforcing rule` | `mcp-governance` + `mcp-registry` + `mcp-monitor` + 🆕 [`examples/gate-orchestration/McpGateChain.java`](../examples/gate-orchestration/McpGateChain.java) | 把 `specRevision` 改成任意新值 → 所有已审批工具回到待复验；用 DE-CIX 同型用例（0.803 置信度 + `without asking first` + 写 scope）必须 DENY `SEMANTIC_SCORE_CANNOT_OVERRIDE_ALLOWLIST`；「不得劣于旧基线」断言：旧基线（信 hint + 模型自证 + 首关键字 + URI 前缀 + 跨版本继承审批）漏 9 条，新链全拦 |


| 33 | 🆕🆕🆕 **11-16 AWS 双 MCP Server + 官方 Python SDK + code-review-mcp-server —— 「工具返回给模型的结果 / 输出面也不是安全边界」**：[REBUILD `CVE-2026-129875`](https://eyeon.ai/f/3815) MCP QueryRecords 把 `User.password` 当可过滤字段，count 响应成**盲注 oracle**，低权限用户逐字符恢复管理员 SHA-256 口令哈希并登录管理员（CWE-202）；[AWS `CVE-2026-126816`](https://updates.securityinsights.io/2026/11/cloud-digest-17-nov-2026.html)（aws-pricing-mcp-server < 1.0.31）cost report 路径穿越任意文件写且**工具被错误标记 read-only 致 MCP 客户端自动批准**；[AWS `CVE-2026-126681`](https://updates.securityinsights.io/2026/11/cloud-digest-17-nov-2026.html)（Timestream for InfluxDB MCP < 0.0.19）不限制端点，prompt injection 泄漏 API token 到攻击者端点；[MCP Python SDK `CVE-2026-115563`](https://theworldofai.org/ai-news/cves/archive/)（7.5）向不可信 auth 端点泄漏 OAuth 密钥；[code-review-mcp-server `CVE-2026-68934`](https://theworldofai.org/ai-news/cves/CVE-2026-68934)（8.7/CWE-78）`folderPath`/`baseBranch` 拼进 `execSync` 跑 shell | **结果面六道闸**：CP1 回传字段归一化敏感 token 拦截 + CP2 敏感字段前缀枚举达阈值判 oracle + CP6 `readOnlyHint` 声明层不参与授权（有副作用即拒）+ CP3 输出路径 confinement（允许根目录 + 无 `..`）+ CP4 外发端点 allowlist（拒 IP 字面量 / 云元数据 / 非白名单）+ CP5 shell 元字符拒拼接；append-only 审计字段名白名单 + 值脱敏 + 每条写明 rule | `mcp-governance` + `mcp-audit` + `mcp-auth` + 🆕 [`examples/result-field-guard/`](../examples/result-field-guard/README.md) | `java McpResultFieldGuard` → **27 attack + 12 control + 4 boundary = 43 断言 / 0 误杀 / 旧基线漏 16 条全拦**；`User.password_hash`、`PassWord`、`user-pwd` 全 DENY；`customer_name` 前缀 50 次 / `password` 等值单次 / 允许根目录写文件 / 白名单端点 / `git status` 全 ALLOW（**对照组不得误杀**）；明文 `supersecret` 不进审计 |
| 34 | 🆕🆕 **11-12 IBM ContextForge `CVE-2026-110279`（8.1，修 1.1.1）+ 企业 AI 控制面五原语 —— 「身份覆盖与动作级授权」**：[`110279`](https://echeloncyber.com/intelligence/entry/cyber-intelligence-weekly-november-15-2026-our-take-on-three-things-you-need-to-know) 特制 HTTP header 覆盖网关发往上游的凭据，破坏用户/应用/agent 身份隔离与审计准确性，官方临时缓解=在反代剥离 `X-Upstream-Authorization`；[企业 AI 控制面五原语](https://agenticreview.co/posts/enterprise-ai-control-plane-defined-for-agentic-workforces-running-mcp-servers-a)（11-16）：scoped credentials / 每次工具调用运行时策略 / 单一治理注册表 / 可审计归因 / **动作级 RBAC**（「权限给到动作，不是给到工具」）；[Schwab $13.33T 受控门复盘](https://twarx.com/research/schwab-mcp-server-agentic-write-access)（机构自有 IdP 发 token、读写分 scope、prepare→带外确认、记动作不记聊天） | 上游凭据由网关指定、禁请求头覆盖（反代剥离覆盖头）+ 身份来源不用最左 XFF；运行时策略在**每次工具调用**执行、动作级 scope（`GITHUB_MERGE_PR` 与 `GITHUB_DELETE_REPO` 分权）；机构自有授权服务器 + 工具目录 + 审计轨迹，读写工具分离、带外确认、记动作不记聊天 | `mcp-auth` + `mcp-gateway` + `mcp-governance` + `mcp-audit` + [`examples/gate-orchestration/`](../examples/gate-orchestration/README.md) | 注入特制 upstream auth header → 上游身份仍为网关指定值、审计身份不被改写；动作级 scope：允许 `GITHUB_CREATE_PR`/`MERGE_PR` 但拒 `DELETE_REPO`（组级一刀切会误杀）；运行时改 scope 下一次调用即生效、旧审批不继承 |
| 43 | **🆕 11-23~11-25 日志 / 遥测输出面（可观测性出口）：日志落盘 / 指标标签 / 诊断响应泄漏凭据** —— [`CVE-2026-135747`](https://nvd.nist.gov/vuln/detail/CVE-2026-135747)（Apache IoTDB MCP Server ≤0.2.0 启动明文口令日志，修 0.3.0，CWE-532）+ [AWS `2026-200-AWS` / `CVE-2026-135614`](https://aws.amazon.com/security/security-bulletins/)（CVSS 5.5，troubleshooting 工具脱敏不当，prompt injection 让 agent 拿到 task-def 明文 env 与 secret 引用，修 0.1.36 **且轮换凭据**）| **出口内容独立定级脱敏**（CP1 凭据模式 password=/token=/Bearer/AKIA/PRIVATE KEY + CP2 `redactSensitive` 声明只进审计不进允许分支 + CP3 `${ENV}`/`process.env`/secretRef 拒绝 + CP6 append-only 脱敏审计、掩码幂等）| `mcp-monitor` + `mcp-audit` + 🆕 [`examples/observability-redaction-guard/`](../examples/observability-redaction-guard/README.md) | `java McpObservabilityRedactionGuard` → **20 attack + 8 control + 2 boundary = 30 断言全绿 / 0 误杀 / 旧基线漏 18 条**；C8 已脱敏文本 0 误杀 |
| 44 | **🆕 出口外发端点与地址族分类不完整（对位 `CVE-2026-134492`）** —— [`CVE-2026-134492`](https://nvd.nist.gov/vuln/detail/CVE-2026-134492)（Anthropic mcp-server-fetch SSRF guard 把 IPv6 site-local `fec0::/10` 当公网放行，修 2026.11.11，CVSS 2.3）| CP4 日志/遥测外发端点**精确 allowlist**（禁通配/后缀欺骗）+ IP **全地址族**分类（IPv4 私有/回环/链路本地/RFC5737 文档段 + IPv6 site-local `fec0::/10` / ULA `fc00::/7` / `::1` / `fe80::`）+ CP5 堆栈只记 error code、请求参数不入错误路径 | `mcp-gateway` + `mcp-monitor` + 🆕 [`examples/observability-redaction-guard/`](../examples/observability-redaction-guard/README.md) | attack #15/#16 未授权端点与 `169.254.169.254` 元数据伪装 → DENY；attack #17/#18 参数入堆栈 → DENY；control C6/C7 error-code-only 堆栈与 allowlist 端点 → ALLOW |

| 45 | **🆕 11-24~11-26 持久化记忆 / 学习存储面：一次合法写入把投毒变成跨会话状态** —— [`CVE-2026-59726`](https://nvd.nist.gov/vuln/detail/CVE-2026-59726)（CVSS **10.0**，ruflo < 3.16.3，默认 compose 绑 `0.0.0.0`、`POST /mcp` 无鉴权，blocklist 只在 autopilot 强制被 `/mcp/:group` 绕过 → `terminal_execute` RCE + 读全部 provider key + 投毒 AgentDB 影响未来所有用户，官方「**重新部署不能撤销投毒**」）+ NIST SP 2400-102e 草案（记忆投毒注入与利用解耦）| **写路径治理**（CP1 来源自报无 host 背书不进批准区 + CP2 agent 写入先落隔离区、未人类确认不得晋升 + CP4 指令式注入/secret 载荷扫描 + CP5 低信任记录召回进高权限前重定级）| `mcp-tools` + `mcp-audit` + 🆕 [`examples/persistent-memory-guard/`](../examples/persistent-memory-guard/README.md) | `java McpPersistentMemoryGuard` → **20 attack + 8 control + 2 boundary = 30 断言全绿 / 0 误杀 / 旧基线漏 20 条**；control C8 清理投毒 DELETE 0 误杀 |
| 46 | **🆕 全局 / 永久写入未升级审批 + 投毒不可回滚（对位 ruflo 官方处置「补丁≠清零」）** —— Nodeflare 13 次写入实测（50.46% 写入成功率 / 41.05% 跨会话改行为率 / plain store 7/7 vs 完整写门 0/7，OWASP ASI06）+ 加密无法阻止语义污染（NIST） | CP3 **scope/TTL**（全局 scope 或 `ttl=0` 须升级审批、按 token `sub` 租户隔离）+ CP6 **append-only 脱敏审计 + hash 定位回滚**（投毒可清除、可回到时间点快照）+ 记忆不存 secret | `mcp-audit` + 🆕 [`examples/persistent-memory-guard/`](../examples/persistent-memory-guard/README.md) | attack #2/#8/#9/#17 全局/永久未升级 → SCOPE_TTL_ESCALATION；boundary B1 审计零明文残留、B2 投毒 hash 可定位回滚；合法全局升级审批 control C7 → ALLOW |

**可直接引用的签字结论**：

> 本方案**不假设上游 SDK / 代理 / 工具声明是安全的**。上述 **46 类**攻击面全部在 **网关 / 注册中心 / 治理 / 租户 / 监控 / 可观测性出口 / 记忆存储写入** 七层设有**相互独立**的控制点，任一层失效不会导致全局放行，且每一类都提供可复现的验收方式。参考实现见 [`examples/governance-hardening/`](../examples/governance-hardening/README.md)、告警规则见 [`examples/monitor-alert-rules/`](../examples/monitor-alert-rules/README.md)、工具入参文法见 [`examples/tool-input-grammar/`](../examples/tool-input-grammar/README.md)、连接路径与解析后 IP 校验见 [`examples/connect-path-guard/`](../examples/connect-path-guard/README.md)、STDIO 启动命令 allowlist 见 [`examples/stdio-command-allowlist/`](../examples/stdio-command-allowlist/README.md)、🆕 **注册期注解完整性与审批门**见 [`examples/tool-annotation-gate/`](../examples/tool-annotation-gate/README.md)、🆕 **本地监听器准入**见 [`examples/local-listener-guard/`](../examples/local-listener-guard/README.md)、🆕 **注册中心管理面自防护与存量复验**见 [`examples/registry-admin-guard/`](../examples/registry-admin-guard/README.md)、🆕 **只读执行层强制（声明层不参与授权）**见 [`examples/read-only-enforcement/`](../examples/read-only-enforcement/README.md)、🆕 **任务绑定确定性监视器（模型外执行门）**见 [`examples/task-bound-monitor/`](../examples/task-bound-monitor/README.md)、🆕 **结果 / 输出面守卫**见 [`examples/result-field-guard/`](../examples/result-field-guard/README.md)、🆕 **会话 / 传输绑定面守卫**见 [`examples/session-transport-binding-guard/`](../examples/session-transport-binding-guard/README.md)、🆕 **执行环境隔离面守卫**见 [`examples/execution-environment-guard/`](../examples/execution-environment-guard/README.md)、🆕 **日志 / 遥测输出面守卫**见 [`examples/observability-redaction-guard/`](../examples/observability-redaction-guard/README.md)、🆕 **持久化记忆 / 学习存储面守卫**见 [`examples/persistent-memory-guard/`](../examples/persistent-memory-guard/README.md)，加固配置见 [`mcp-security-hardening-quickstart.md`](mcp-security-hardening-quickstart.md) 第 6 步。
>
> **2026-11-09 更新依据**：官方 `modelcontextprotocol/servers` 参考实现被一次性披露 7 个 CVE，且 Anthropic 对 STDIO 架构缺陷明确不修 —— 「升级官方依赖」不再是可用的缓解路径，**责任在集成方**。
>
> **2026-11-10 更新依据**：Dynatrace MCP Server 双 CVE 用**同一个 server 的一正一反**证明审批门真实生效（有门挡住 / 漏门成入口），且 `--http` 无 Host+Origin 防护再次把攻击面下沉到传输层；UiPath×Google Cloud 把「治理」写成受监管行业允许 MCP 接入的**前提**，浙商证券把 7 项法规写成**强制测评依据清单** → 治理从「定价权」升级为「**准入权 / 法律责任分界线**」。
>
> **2026-11-11 更新依据**：INITE Atlas 当日 11,393 个 server 普查把「注解谎报/缺失」从个案变成**可量化的生态基线**（25% 缺 hint / 4% 谎报 / 23% 免登录改数据），Microsoft Agent Framework 1.25.0 与 Google Cloud Agent Registry 把「发现期逐工具审批 / 逐工具 IAM」做成产品层并明确**元数据不可信、目录刷新须复验** → 治理的起点被推回「**工具注册进来的那一刻**」；Siemens Mendix 双 CVE 则从商业 IDE 侧证明「绑 localhost」不是安全边界。
>
> **2026-11-12 更新依据**：AWS 11-11 两份 Important 公告把「负责审批工具的注册中心」自己变成提权入口（`100307` 只认证不授权自授予 registry-admin + `125926` 上游 SSRF，**两洞构成一条链**），且官方明确**升级不清理存量配置 / 不撤销已植入映射 / 不使已泄露凭据失效** → 治理层必须先管住**自己的管理面**，并把「**存量全量复验 + 举证**」做成可验收服务；同期 Microsoft `mcp-gateway` IDOR 8.8 与操作名归一化策略绕过，把攻击面从「工具层」下沉到「注册表与网关自身」。完整 12 项验收清单见 [`registry-control-plane-self-defense-2026-11-12.md`](registry-control-plane-self-defense-2026-11-12.md)。
>
> **2026-11-13 更新依据**：pgEdge Postgres MCP Server 4 天内 7 个 CVE 把「**read_only 是声明不是边界**」钉死（简单查询协议堆叠 / dollar-quote 逃逸 / 首关键字分类 / where 跨表 / 最左 XFF / 缺 Origin），官方 `modelcontextprotocol/servers` 5 个 GHSA 同期证明「**只读工具照样执行代码**」（`core.fsmonitor`）与「**修复不完整**」（`q74p`）→ 三段式收口：注解不是安全边界（V1.69）→ 注册中心自己也不是（V1.70）→ **只读声明也不是（V1.71）**。买方侧 Swift Media 11-13 已把「MCP 网关 + 集中注册表 + per-agent scope + 注入过滤 + 可导出审计」写成 RFP 必选项，嘉信理财 11-12 上线 agentic MCP 却坚持**每笔交易人工确认** → 治理的验收口径从「有没有」变成「**能不能当场证明**」。完整 12 项执行层验收清单见 [`readonly-execution-boundary-2026-11-13.md`](readonly-execution-boundary-2026-11-13.md)。
>
> **2026-11-14 更新依据**：arXiv 量化对照实验把四段式收口到第四段 —— 注解（V1.69）→ 注册中心（V1.70）→ 只读声明（V1.71）→ **「审批与提示词护栏本身也不是」（V1.72）**：in-prompt guardrail 在持久自适应攻击下 **100% 失守**，而任务绑定的**模型外确定性** reference monitor 只放行 1.3%/7.7%、severe actions **0%** → 唯一可验收的边界是「按当前任务签发、fail-closed、不读模型自证」的执行门。IBM 11-13 双 CVE 把攻击面下沉到**网关自身的批量导入通道**（跨租户覆写凭据、官方 Workarounds: None），AWS `125926` 版本区间坐实且官方明确「升级不改存量配置」→ **打补丁 ≠ 清干净**成为可写进运维 SOP 的验收项。买方侧广发研究 MCP & Skills 11-13 **正式生产上线**（21 行业 / 9 万余指标），说明机会已从「帮你建 MCP」转向「**帮你治理已上线的 MCP**」。完整 12 项决策层验收清单见 [`deterministic-control-plane-boundary-2026-11-14.md`](deterministic-control-plane-boundary-2026-11-14.md)，四 Gate 接入点图与工作量评估见 [`gate-integration-map-2026-11-14.md`](gate-integration-map-2026-11-14.md)。
>
> **2026-11-15 更新依据**：五段式收口到第五段 —— 注解（V1.69）→ 注册中心（V1.70）→ 只读声明（V1.71）→ 审批与提示词护栏（V1.72）→ **配置面 / 连接建立期（V1.73）**。IBM ContextForge 11-13 一天三 CVE 把管理面 UI（mXSS）/ 配置导入（跨租户覆写凭据）/ OAuth 连接建立（DCR metadata SSRF）同时打穿，n8n `126386` 与 pgEdge `126197` 官方细节从旁佐证；同期 MCP / Agent Skills 完成 AAIF 厂商中立治理 → **规格进基金会，责任还在集成方**：规格版本须作受监控依赖、变更后全量复验、旧审批不继承。DE-CIX 门测试 A/B 给出「语义评分 0.803 放行采购动作、机械 allowlist 正确拦住」的独立实证 → 唯一可验收边界仍是「按当前任务签发、在工具调用本身机械执行、fail-closed」的确定性执行门。买方侧 Swift Media 11-13 已把 MCP 网关写进 RFP 并建议「加 5 个 MCP 网关问题」。完整 12 项配置面验收清单 + 买方必问 5 问见 [`configuration-plane-boundary-2026-11-15.md`](configuration-plane-boundary-2026-11-15.md)，四 Gate 装配链（兑现 V1.72 待办④分期①）见 [`examples/gate-orchestration/README.md`](../examples/gate-orchestration/README.md)。


> **2026-11-17 更新依据**：REBUILD `CVE-2026-129875` 用**只读查询的 count 响应当盲注 oracle**逐字符恢复管理员口令哈希，AWS `126816` 因**工具被错误标记 read-only 而自动批准**路径穿越写文件，AWS `126681` / 官方 Python SDK `115563` 把 token / OAuth 密钥泄漏到攻击者端点，code-review `68934` 把参数拼进 shell —— 共同把边界推进到**工具返回给模型的结果 / 输出面**（第六段）。IBM `110279` 与「企业 AI 控制面五原语」「Schwab 受控门」进一步把治理落到**身份不被覆盖 + 动作级授权**。完整 12 项结果面验收清单见 [`result-field-boundary-2026-11-17.md`](result-field-boundary-2026-11-17.md)。
### 📋 管理面 12 项验收列（V1.70，可直接进标书）

| # | 验收项 | 现场验收方式 |
| --- | --- | --- |
| 1 | 管理 API 认证 ≠ 授权，逐操作显式判权限 | 低权限主体调 `registerServer` → DENY |
| 2 | 组导入禁止特权 scope（含大小写 / 分隔符变形） | 三种 `mcp-registry-admin` 变形全 DENY |
| 3 | 授权源独立只读、不可由注册数据派生 | 导入后授权集合引用与内容不变、不可变 |
| 4 | 管理员身份不隐含凭据读取 | bootstrapAdmin 读凭据 → DENY；`CREDENTIAL_READ` 主体 → ALLOW |
| 5 | 凭据读取审计脱敏 | 审计快照搜索明文值 → 0 命中 |
| 6 | 上游 scheme 与 userinfo 准入 | `https://api.example.com@169.254.169.254/` → DENY |
| 7 | 上游主机准入（私网 / 元数据 / IPv6 / 进制伪装） | 14 攻击 URL 全 DENY，2 个合法公网对照放行 |
| 8 | 重定向逐跳重校验 | 同域相对路径 ALLOW、跳内网 DENY |
| 9 | Host / SNI 一致性 | 跨主机重定向 → DENY |
| 10 | 修改上游 = 重新批准并重签指纹 | 改后指纹变化、旧批准不复用 |
| 11 | ★ 存量全量复验（幂等 + 漂移 + 隔离） | 带外污染 → 首次抓到并隔离、二次不重复报 |
| 12 | 操作名归一化 | `get_user` / `GetUser` / `Get__User` 同一策略键；`Export__Credentials` 命中 deny list |

> 全部 12 项可在 [`examples/registry-admin-guard/`](../examples/registry-admin-guard/README.md) 现场跑：**27 攻击 + 10 对照 + 8 边界 = 45 断言**，其中 14 个是「只查 http/https 前缀」旧基线全漏的增量拦截。⚠️ 当前为 `examples/` 参考实现、**未接入 `mcp-registry` 主模块**；主机判定为字面量检查，须与 DNS 解析后真实 IP 校验叠加；**本框架不受上述 CVE 直接影响**。

### 📋 执行层 12 项验收列（V1.71，可直接进标书）

> 完整矩阵（买方必问 5 问 / 竞品差异化 / 三档报价 / 24-48-72 小时行动清单）见
> [`readonly-execution-boundary-2026-11-13.md`](readonly-execution-boundary-2026-11-13.md)。

| # | 验收项 | 现场验收方式 |
| --- | --- | --- |
| 1 | 声明层不参与授权（`readOnlyHint` / 描述 / 连接标签仅展示） | 全声明只读 + `SELECT ... INTO` → DENY；翻转声明字段，合法查询结果不变 |
| 2 | 词法归一化后再分类（剥离注释/字符串/标识符/dollar-quote） | `/*x*/ VACUUM INTO` DENY；`SELECT 'DROP TABLE t; DELETE FROM t'` **ALLOW** |
| 3 | 禁止多语句 / 简单查询协议 | `SELECT 1; UPDATE ...` DENY；尾分号 `select ... limit 10;` ALLOW |
| 4 | dollar-quote 保留标签逃逸检测 | `SELECT $mcp_custom_tool$; DROP...` DENY；`$$a; b$$` ALLOW |
| 5 | 修复完整性回归（对历史绕过形态复验） | 自检输出须含 `22 attack ... 20 incremental blocks over legacy baseline` |
| 6 | 强制参数绑定 + 服务端只读事务（数据库侧另有最小权限只读角色） | 关闭 `paramsBound` 或 `readOnlyTransaction` → DENY |
| 7 | `where` 参数不得引用声明表之外 | `count_rows(where="id IN (SELECT ... FROM other_table)")` → DENY |
| 8 | 池化会话归还前 RESET / DISCARD ALL | 「前一条改 session、后一条复用连接」序列 → 拦下或状态已复位 |
| 9 | 身份不取最左 XFF + Origin 白名单 + Host/Origin 一致 | 三种伪造/缺失场景全部 DENY |
| 10 | 只读工具执行环境净化（禁加载仓库自带 `core.fsmonitor`/hook） | 恶意 `.git/config` 必须既不执行也不加载 |
| 11 | 校验路径与实际路径同源 + 解析后 IP 分类完整 | `//private`、`fec0::/10`、`64:ff9b::/96`、进制伪装全 DENY |
| 12 | fail-closed + 可举证（原因码可导出、审计脱敏） | 逐一关闭 6 个 CP，每次 DENY 且 `reasonCode` 精确对应 |

> 全部 12 项可在 [`examples/read-only-enforcement/`](../examples/read-only-enforcement/README.md) 现场跑：
> **22 攻击 + 9 对照 + 8 边界 = 79 断言**，其中 **20 条**是旧基线「看 `readOnlyAnnotation` +
> 首关键字 SELECT/WITH 就放行」全漏的增量拦截。⚠️ 当前为 `examples/` **参考实现、未接入主模块**；
> CP2 where 作用域 / CP3 会话状态 / CP4 身份来源 / CP5 环境净化 / CP6 路径 IP 以**执行上下文标志**
> 表达，真实落地需接 JDBC/pgx 元数据、连接池 hook、可信代理头、DNS 解析后 IP 分类器；
> **不得把示例当生产组件交付**；**本框架不受上述 CVE 直接影响**。


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


### 📋 决策层 12 项验收列（V1.72，可直接进标书）

> 完整矩阵（买方必问 5 问含不合格/合格回答对照 / 竞品差异化 / 三档报价 / 24-48-72h）见
> [`deterministic-control-plane-boundary-2026-11-14.md`](deterministic-control-plane-boundary-2026-11-14.md)。

| # | 验收项 | 现场验收方式 |
| --- | --- | --- |
| 1 | 模型自证字段不参与授权 | `modelAttestation="HITL-approved"` + `verb=DELETE` → DENY，审计含 `IGNORE` |
| 2 | 工具注解不参与授权，只进审计 | `readOnlyHint=true` + `verb=DROP` → DENY；合法 `SELECT` 且注解 false 仍按 grant 放行 |
| 3 | 授权源必须是控制平面签发 | `issuedBy="MODEL_CHANNEL"` → DENY@CP1 |
| 4 | 任务绑定 + fail-closed | 重放 `T-999` / 过期 / tenant 改 `evil` → 三种全 DENY |
| 5 | 对象级越权（`CVE-2026-109492` IDOR） | grant 只允许 `acct-123`，传 `acct-124` → DENY |
| 6 | 归一化绕过 / 堆叠语句 / 控制字符 | `" Query "`、`"SELECT; DROP TABLE t"`、`"que\try"` → 全 DENY |
| 7 | 导入跨租户覆盖 + 受保护字段（`CVE-2026-110178`） | 含 `authorization` / `request_headers` / `owner_tenant` 的 8 个导入用例按预期判定 |
| 8 | 内部 ID 不由用户名派生；409 不得吞 | 同名冲突 + `skip` → DENY；`error` → 放行 |
| 9 | DCR 默认关闭 + issuer 白名单 + 上游字面量校验（`110194` / `125926`） | 11 个上游用例含 `2852039166`、`0177.0.0.1`、`[fec0::1]`、`169.254.169.254` 按预期判定 |
| 10 | 解析后真实 IP 落私网 → 拒 | `resolvedPrivateIp=true` → DENY（叠加 `McpResolvedIpGuard`） |
| 11 | ★ 升级后存量全量复验（幂等 + 漂移隔离） | 干净/投毒/漂移/畸形四条 → 1 放行 3 隔离；复验两次结果一致 |
| 12 | 审计 append-only 脱敏 + 「不得劣于基线」对照断言 | `51 断言` 全绿；明文 `Bearer SUPERSECRET123` 在审计串中 0 命中 |

> 全部 12 项可在 [`examples/task-bound-monitor/`](../examples/task-bound-monitor/README.md) 现场跑：
> **36 攻击 + 8 合法对照 + 7 边界 = 51 断言**，`11 条为旧基线漏掉的增量拦截`、`0 误杀`。
> ⚠️ 当前为 `examples/` 参考实现、**未接入主模块**；`CONTROL_PLANE` 是字符串标志非密码学签名；
> 连接路径只做字面量主机检查；arXiv 数字是学术模拟实验**不是本框架实测拦截率**；**本框架不受上述 CVE 直接影响**。


### 📋 配置面 / 连接建立期 12 项验收列（V1.73，可直接进标书）

> 完整矩阵（12 项逐条 → 控制点 → 仓库真实文件 → 现场验收 / 买方必问 5 问含不合格·合格回答对照 / 生态分层差异 / 三档报价 / 24-48-72h）见
> [`configuration-plane-boundary-2026-11-15.md`](configuration-plane-boundary-2026-11-15.md)。

| # | 验收项 | 现场验收方式 |
| --- | --- | --- |
| 1 | DCR 授权服务器 metadata 准入（issuer 白名单 + 域锚定 + 解析后真实 IP） | 8 类 payload（`169.254.169.254` / `127.0.0.1` / `10.x` / `172.16~31.x` / `0177.0.0.1` / `2130706433` / `fe80::1` / `metadata.*`）全 DENY |
| 2 | `redirect_uri` 与已注册值精确匹配 | `https://app.corp.example@evil.example/cb` 与 `//evil.example/cb` 全 DENY；合法精确匹配 ALLOW |
| 3 | 批量导入共享实体归属 + 受保护字段 | tenant-b 身份改 tenant-a endpoint → DENY；同租户且未碰受保护字段 → ALLOW |
| 4 | 管理后台 URL 参数纯文本渲染 | 11 组 mXSS / 活跃标记载荷全 DENY；中英文纯文本放行 |
| 5 | 规格版本依赖登记 + 变更后全量复验 | 改 `specRevision` → 已审批工具回到待复验，复验幂等 |
| 6 | 升级不改存量 → 显式 `revalidateStored()` | 模拟升级前已植入映射 / 凭据，升级后仍须报出并隔离 |
| 7 | 授权源与控制面输入隔离 | 「导入请求自带 admin scope」不得获得额外权限 |
| 8 | 每决策带 tenant / taskId / grantIssuedBy / specRevision | 抽查审计四字段齐全；跨 tenant 调用直接 DENY |
| 9 | 机械 scope allowlist 是唯一的锁 | DE-CIX 同型用例（0.803 + `without asking first` + 写 scope）→ DENY `SEMANTIC_SCORE_CANNOT_OVERRIDE_ALLOWLIST` |
| 10 | 声明层完全不参与授权，服务端独立定级 | `readOnlyHint=true` + `DELETE` / `VACUUM INTO` / `/*!...*/` → 注册期与调用期均 DENY |
| 11 | 审计 append-only + 字段白名单 + 值脱敏 + 写明规则 | 导出审计：无明文值 / 无未知字段 / 无空 `rule` |
| 12 | AND 语义 + 对照组回归 + 不得劣于旧基线 | `java McpGateChain` → **84 攻击 + 26 对照 + 13 边界 = 123 断言 / 0 误杀 / 旧基线漏 9 条全拦** |

> 全部 12 项可在 [`examples/gate-orchestration/`](../examples/gate-orchestration/README.md) 现场跑（纯 JDK 17 零依赖，不进 Maven reactor）。
> ⚠️ 参考实现**未接入主模块**；`TaskGrant.signatureOk` 是布尔标志**非密码学签名**；IP 判定是字面量分类非 DNS/SNI 逐跳校验；SQL 筛查是词法归一化非解析器；
> **DE-CIX 门测试为第三方启发式实验（`calibrated=false`、单次测量），非本框架实测拦截率**；AAIF 治理移交不是近 3 天事件；**本框架不受上述 CVE 直接影响**。


### 📋 结果 / 输出面 12 项验收列（V1.74，可直接进标书）

> 完整矩阵（12 项逐条 → 控制点 → 仓库真实文件 → 现场验收 / 买方必问 5 问 / 竞品差异化 / 三档报价 / 24-48-72h）见
> [`result-field-boundary-2026-11-17.md`](result-field-boundary-2026-11-17.md)。

| # | 验收项 | 现场验收方式 |
| --- | --- | --- |
| 1 | 回传字段敏感列拦截（CP1） | `User.password_hash` / `PassWord` / `user-pwd` 全 DENY；`name/balance/status` ALLOW |
| 2 | 盲注 oracle 抑制（CP2） | `password` 前缀探测 12 次 DENY；`customer_name` 50 次 / `password` 等值单次 ALLOW |
| 3 | 只读声明不参与授权（CP6） | 声明只读 + 写文件 / 外发 / shell 三种全 DENY；声明只读且无副作用 ALLOW |
| 4 | 输出路径 confinement（CP3） | `../../etc/passwd` / `C:\Windows\...` DENY；允许根目录下路径 ALLOW |
| 5 | 外发端点 allowlist（CP4） | `169.254.169.254` / `10.0.0.5` / 非白名单 DENY；白名单域名 ALLOW |
| 6 | token / OAuth 密钥不外泄 | `access_token=sk-abc` 被拒后审计无明文 |
| 7 | shell 参数元字符拦截（CP5） | `git status; curl evil|x` / `$(whoami)` / `` `id` `` DENY；`git status` ALLOW |
| 8 | 身份覆盖防护 | 特制 upstream auth header 不改变上游身份、审计身份不被改写 |
| 9 | 写权限三档分级 + 强制留痕回滚 | 只读 / 低风险写 / 高风险动作分档验收，高风险须人工确认 + 可回滚 |
| 10 | 协议接入不替代授权 | 售前明确「MCP 是连接手段，协议接入不替代业务授权」 |
| 11 | 每次调用运行时策略 + 动作级 RBAC | 改 scope 下一次即生效；`MERGE_PR` 允许但 `DELETE_REPO` 拒绝 |
| 12 | append-only 归因审计 + 不得劣于基线 | 审计无明文 / 无空 rule；`java McpResultFieldGuard` → **43 断言 / 0 误杀 / 旧基线漏 16 条全拦** |

> 全部 12 项可在 [`examples/result-field-guard/`](../examples/result-field-guard/README.md) 现场跑（纯 JDK 17 零依赖，不进 Maven reactor）。
> ⚠️ 参考实现**未接入主模块**；敏感字段检测是词法归一化非语义解析、阈值是演示常量；**本框架不受上述 CVE 直接影响**。

> **2026-11-18 更新依据**：IBM `CVE-2026-110278`（CVSS 7.4）per-server 身份转发配置被忽略 + 静态签名可重放，`CVE-2026-110279` 客户端 header 覆盖上游凭据，Impartner MCP「governed by existing permissions」+ 敏感字段隐去 → 边界推进到**身份传播面**（第七段）。完整 12 项身份面验收清单见 [`identity-propagation-boundary-2026-11-18.md`](identity-propagation-boundary-2026-11-18.md)。

### 📋 身份传播面 12 项验收列（V1.75，可直接进标书）

> 完整矩阵（12 项逐条 → 控制点 → 仓库真实文件 → 现场验收 / 买方必问 5 问 / 竞品差异化 / 三档报价 / 24-48-72h）见
> [`identity-propagation-boundary-2026-11-18.md`](identity-propagation-boundary-2026-11-18.md)。

| # | 验收项 | 现场验收方式 |
| --- | --- | --- |
| 1 | 声明 block 字段确实未转发（CP1） | 配 email=block、任务请求 email → fail-closed 拒绝，上游抓包无 email |
| 2 | 字段最小化（CP2） | 只需 userId 却携带 email/groups → 仅下发 userId，审计记 stripped |
| 3 | 拒绝静态/缺失签名（CP3） | 固化 token → 拒绝，返回 STATIC_OR_MISSING_SIGNATURE |
| 4 | 签名新鲜度窗口（CP3） | 901 秒前时间戳 → 拒绝；窗口内 → 放行 |
| 5 | nonce 防重放（CP3） | 同一签名二次调用 → 第二次 REPLAYED_NONCE |
| 6 | HMAC 完整性（CP3） | 改 userId 不改签名 → BAD_SIGNATURE |
| 7 | header 覆盖防护（CP4） | 注入 X-User-Id / Authorization → HEADER_OVERRIDE |
| 8 | 租户绑定（CP5） | tenant-A 身份调 tenant-B server → TENANT_MISMATCH |
| 9 | 脱敏 append-only 审计（CP6） | 审计无明文 email、groups 只记数量、历史不可回改 |
| 10 | 未知 server 默认拒绝 | ghost-server → UNKNOWN_SERVER |
| 11 | 无认证身份默认拒绝 | identity=null → EMPTY_IDENTITY |
| 12 | 不得劣于旧基线 | legacyBaseline 放行的 3 条攻击全拦；`java McpIdentityPropagationGuard` → **15 断言 / 0 误杀** |

> 全部 12 项可在 [`examples/identity-propagation-guard/`](../examples/identity-propagation-guard/README.md) 现场跑（纯 JDK 17 零依赖，不进 Maven reactor）。
> ⚠️ 参考实现**未接入主模块**；示例密钥硬编码、进程内 nonce 去重、词法白名单为演示约束；**本框架不受上述 CVE 直接影响**。

---

> **2026-11-20 更新依据**：`CVE-2026-132121`（Supergateway stdio↔WS 无 Origin 校验，恶意网页跨站 WebSocket 劫持本机 MCP 网关，PoC 12/12 runs 外传本地文件）+ `CVE-2026-131857`（prototype-inherited sessionId 打崩网关）+ `CVE-2026-130007`（11-19 NVD，k8s-mcp-server `POST /message` 无 authorization/Origin 即派发工具、read-only 默认关闭，修 1.1.0）+ `CVE-2026-48710` BadHost（Host 注入 `?` 使 URL 重解析边界漂移，MCP 免鉴权 OAuth discovery 端点放大）+ `CVE-2026-115563`（Python SDK OAuth 凭据发给 server 自选 AS，升级后须显式 `issuer=`）→ 边界推进到**会话 / 传输绑定面**（第八段）。完整 12 项会话面验收清单见 [`session-transport-binding-boundary-2026-11-20.md`](session-transport-binding-boundary-2026-11-20.md)。

### 📋 会话 / 传输绑定面 12 项验收列（V1.76，可直接进标书）

> 完整矩阵（12 项逐条 → 控制点 → 仓库真实文件 → 现场验收 / 买方必问 5 问 / 竞品差异化 / 三档报价 / 24-48-72h）见
> [`session-transport-binding-boundary-2026-11-20.md`](session-transport-binding-boundary-2026-11-20.md)。

| # | 验收项 | 现场验收方式 |
| --- | --- | --- |
| 1 | 未认证派发第一道门拒绝（CP5a） | principal=null 非握手调用 → UNBOUND_LISTENER |
| 2 | prototype-inherited sessionId 拒绝（CP1） | `__proto__.sessionId` / `constructor.prototype.sid` → INHERITED_SESSION |
| 3 | sessionId 服务端签发 + 篡改/未注册拒绝（CP1） | `s-deadbeef-1` 或改签名 → FORGED_SESSION |
| 4 | 会话绑定 principal（CP1） | alice 会话配 mallory 主体 → SESSION_PRINCIPAL_MISMATCH |
| 5 | 握手必带 Origin，缺/`null` 拒绝（CP2） | 无 Origin / `Origin: null` → DENY |
| 6 | Origin 白名单且与会话绑定一致（CP2） | 跨站 `https://malicious.page` WS → ORIGIN_NOT_ALLOWED |
| 7 | Host 逐字符 RFC 校验（CP3） | `Host: foo?/admin` → BAD_HOST；合法域名:端口 → ALLOW |
| 8 | IPv6 Host 不误杀（CP3 对照） | `[::1]:8080` → ALLOW |
| 9 | 凭据无 issuer 拒绝（CP4） | 有凭据无 issuer → ISSUER_UNBOUND |
| 10 | issuer 非预期授权服务器拒绝（CP4） | `issuer=https://attacker.as` → ISSUER_MISMATCH |
| 11 | 只读会话禁写工具（CP5） | 只读 session 调写工具 → WRITE_NOT_PERMITTED |
| 12 | 不得劣于旧基线 + 脱敏审计（CP6） | legacyBaseline 放行的 6 条全拦；`java McpSessionTransportBindingGuard` → **26 断言 / 0 误杀** |

> 全部 12 项可在 [`examples/session-transport-binding-guard/`](../examples/session-transport-binding-guard/README.md) 现场跑（纯 JDK 17 零依赖，不进 Maven reactor）。
> ⚠️ 参考实现**未接入主模块**；示例密钥硬编码、会话表进程内、Host/Origin 字面量校验为演示约束；**本框架不受上述 CVE 直接影响**。

---

> **2026-11-22 更新依据**：MCPHub `CVE-2026-79748`（CVSS 9.9，非管理员注册 `command=/bin/sh` 立即执行 + edit 端点无归属校验）+ Hermes Agent `GHSA-p22p-9ph6-gmrh`（沙箱 env 文件反向穿透注入 `LD_PRELOAD` 触达 provider keys）+ Gortex `62964`/yt-mcp `75612`/DOMShell `75518`（读写路径不一致 + `readOnlyHint` 说谎）+ MCP Python SDK `CVE-2026-52869`（11-20 VulnCheck 在野，仅凭 session_id 路由不校验同一认证客户端、共享 OAuth client 无 subject）→ 边界推进到**执行环境隔离面**（第九段）。完整 12 项执行环境面验收清单见 [`execution-environment-boundary-2026-11-22.md`](execution-environment-boundary-2026-11-22.md)。

### 📋 执行环境隔离面 12 项验收列（V1.77，可直接进标书）

> 完整矩阵（12 项逐条 → 控制点 → 仓库真实文件 → 现场验收 / 买方必问 5 问 / 竞品差异化 / 三档报价 / 24-48-72h）见
> [`execution-environment-boundary-2026-11-22.md`](execution-environment-boundary-2026-11-22.md)。

| # | 验收项 | 现场验收方式 |
| --- | --- | --- |
| 1 | 绝对路径逃逸 confinement root 拒绝（读写同查，CP1） | `/etc/passwd` → ESCAPE_ABSOLUTE_PATH |
| 2 | `..` 路径遍历拒绝（CP1） | `../secrets.txt` → ESCAPE_PATH_TRAVERSAL |
| 3 | 符号链接逃逸拒绝（CP1） | `link/config` + symlink 集合 → ESCAPE_SYMLINK |
| 4 | 路径控制字符拒绝（CP1） | `report.csv .png` → ESCAPE_CONTROL_CHAR |
| 5 | 命令 allowlist + 解释器直执拒绝（CP2） | `/bin/sh`/`bash` → CMD_INTERPRETER；`curl` → CMD_NOT_ALLOWLISTED |
| 6 | argv shell 元字符拒绝（CP2） | argv `x; rm -rf /` → CMD_SHELL_META |
| 7 | env 危险键拒绝（CP3） | `LD_PRELOAD`/`DYLD_INSERT_LIBRARIES` → ENV_DANGEROUS_KEY |
| 8 | env 源头白名单 + 值中命令替换拒绝（CP3） | 源 `/tmp/agent.env` → ENV_UNTRUSTED_SOURCE；`$(curl evil)` → ENV_INJECTION_VALUE |
| 9 | 服务端独立定级：只读声明不参与授权（CP4） | 写动词标 `readOnlyHint:true` → READ_ONLY_LIE |
| 10 | 写动词必须有审批令牌（CP4） | 写动词无令牌 → WRITE_TIER_NEEDS_APPROVAL |
| 11 | 会话按 issuer+subject 绑定（CP5） | 无 subject → SUBJECT_UNBOUND；共享 client 用 clientId 当 subject → SHARED_CLIENT_NO_SUBJECT |
| 12 | 不得劣于旧基线 + 脱敏审计（CP6） | 旧基线放行的 20 条全拦；`s3cr3t-value`/原始 subject 不落审计；`java McpExecutionEnvironmentGuard` → **26 断言 / 0 误杀** |

> 全部 12 项可在 [`examples/execution-environment-guard/`](../examples/execution-environment-guard/README.md) 现场跑（纯 JDK 17 零依赖，不进 Maven reactor）。
> ⚠️ 参考实现**未接入主模块**；策略硬编码、符号链接集合模拟、单进程审计表为演示约束；**本框架不受上述 CVE 直接影响**。

---

---

> **2026-11-24 更新依据**：MCP **Java SDK 官方洞** `CVE-2026-34237` / `GHSA-hv2w-8mjj-jw22`（CVSS 6.1，`mcp-core` SSE / Streamable HTTP 下发 `Access-Control-Allow-Origin: *`，浏览器成中继读 SSE 首帧 session id 再跨域 POST；修 1.0.1/1.1.1/0.18.3，⚠️ 本项目核心依赖官方 SDK 须显式披露）+ Siemens Mendix `CVE-2026-112136`（8.8）/ `112137`（5.4）（远程交互 + 浏览器 CSRF 穿透 loopback）+ mcp-server-kubernetes `CVE-2026-134817`（7.1，CWE-88，dash 前缀位置操作数被当 kubectl flag）→ 边界推进到**浏览器 / 出站响应面 + 本地绑定面**（第十段，首次守出站方向）。完整 12 项验收清单见 [`browser-response-boundary-2026-11-24.md`](browser-response-boundary-2026-11-24.md)。

> **2026-11-25 更新依据**：IoTDB `135747` 把连接口令明文写进日志（读日志是运维日常动作）、AWS `135614` 证明 `sensitiveData` 声明被 prompt injection 绕过且**升级后仍须轮换凭据**、mcp-server-fetch `134492` 证明出口 allowlist 漏掉 IPv6 site-local `fec0::/10` —— 十一段式收口到第十一段：**日志 / 遥测输出面（可观测性出口）也不是安全边界**。中国经营报 11-22 / IDC MarketScape 2026（28 家厂商）把采购从「造智能体」转向「管智能体」，治理合规 58.7% 居首，验收四问末问「出偏差能不能追溯」直指出口可审计 → 出口必须按内容独立定级脱敏、append-only 审计、默认 fail-closed。完整 12 项输出面验收清单 + 买方必问 5 问见 [`observability-redaction-boundary-2026-11-25.md`](observability-redaction-boundary-2026-11-25.md)。

> **2026-11-27 更新依据**：`CVE-2026-131688`（CVSS 3.1 8.1，11-26）受限 scope bearer token 提权至 WordPress 管理员 —— scope 声明不是权限边界；`CVE-2026-136620`（CVSS 3.1 7.1，11-25 入库）`confirmation_token` 被原路返回给同一调用方、输出路径无 confinement —— 确认令牌是流程句柄不是独立审批人；官方 MCP servers v2.5.0（11-18 八洞）只读 `git_diff` 经构造 `.git/index` 读目录外文件、`git_merge` 触发仓库自带 hook 以 root 执行、filesystem 符号链接 TOCTOU（Windows #5600 未修）、npm 语义版 2.4.0 不提示升级 —— 只读入口的内部委托动作必须逐段重判，升级 ≠ 修复。十三段式收口到第十三段：**审批确认 / 委托链面也不是安全边界**。完整 12 项审批链验收清单 + 买方必问 5 问见 [`approval-chain-boundary-2026-11-27.md`](approval-chain-boundary-2026-11-27.md)。

> **2026-11-26 更新依据**：`CVE-2026-59726`（CVSS 10.0，ruflo < 3.16.3）证明投毒可持久化进 AgentDB 学习存储、影响未来所有用户且**重新部署不能撤销投毒**；NIST SP 2400-102e 草案把长期记忆投毒列入 Agentic SI 并指出检索阶段是独立控制点；Nodeflare 13 次写入实测证明写门把非期望写入从 7/7 降到 0/7、provenance 命中模式扫描漏掉的那一条 —— 十二段式收口到第十二段：**持久化记忆 / 学习存储面（跨会话状态）也不是安全边界**。修复是写路径不是过滤器、补丁 ≠ 清干净、加密 ≠ 语义可信 → 写入须默认隔离、带 host provenance 与 scope/TTL、全局永久须升级审批、召回进高权限前重定级、留 hash 可回滚。完整 12 项记忆面验收清单 + 买方必问 5 问见 [`persistent-memory-boundary-2026-11-26.md`](persistent-memory-boundary-2026-11-26.md)。

### 📋 浏览器 / 出站响应面 12 项验收列（V1.78，可直接进标书）

> 完整矩阵（12 项逐条 → 控制点 → 仓库真实文件 → 现场验收 / 买方必问 5 问 / 竞品差异化 / 三档报价 / 24-48-72h）见
> [`browser-response-boundary-2026-11-24.md`](browser-response-boundary-2026-11-24.md)。

| # | 验收项 | 现场验收方式 |
| --- | --- | --- |
| 1 | 通配 `ACAO: *` 拒绝，SSE/Streamable 双传输（CP1） | `acao("*")` → CORS_WILDCARD |
| 2 | allow-credentials 不得配通配（CP1） | `acao("*")+creds` → CORS_CREDENTIALS_WITH_WILDCARD |
| 3 | ACAO 非 allowlist / 后缀欺骗拒绝（CP1） | `evil.com`、`app.example.com.evil.com` → CORS_ORIGIN_NOT_ALLOWED |
| 4 | session id 必须绑定签发 origin（CP2） | `sid+issued(null)` → SESSION_ORIGIN_UNBOUND |
| 5 | session 句柄不得跨域可读（CP2） | `issued(app)+acao(admin)` → SESSION_ID_EXPOSED_CROSS_ORIGIN |
| 6 | 只绑 loopback，非 loopback/文档 IP 拒绝（CP3） | `0.0.0.0`/`::`/`192.168.1.10` → BIND_PUBLIC_INTERFACE |
| 7 | 不可解析绑定地址 fail-closed（CP3） | `not-a-host.invalid` → BIND_UNRESOLVABLE |
| 8 | dash 前缀位置操作数拒绝（CP4） | `--filename=/etc/passwd`/`-n` → ARG_FLAG_LIKE |
| 9 | 改动类 POST 缺 Origin 拒绝（CP5） | `origin(null)` → CSRF_ORIGIN_MISSING |
| 10 | Origin 跨源拒绝（CP5） | `origin("https://evil.com")` → CSRF_ORIGIN_MISMATCH |
| 11 | 缺 CSRF token / Referer 跨源拒绝（CP5） | `token(null)` → CSRF_TOKEN_MISSING；跨源 Referer → CSRF_REFERER_MISMATCH |
| 12 | 不得劣于旧基线 + 脱敏审计（CP6） | 旧基线放行的 20 条全拦；session id 掩码 + origin query 删除；`java McpBrowserResponseGuard` → **26 断言 / 0 误杀** |

> 全部 12 项可在 [`examples/browser-response-guard/`](../examples/browser-response-guard/README.md) 现场跑（纯 JDK 17 零依赖，不进 Maven reactor）。
> ⚠️ 参考实现**未接入主模块**；allowlist/token/绑定地址硬编码、IP 字面量分类、session 绑定字符串标志为演示约束；**本框架不受 `34237` 等第三方 CVE 直接影响，但核心依赖官方 SDK，须部署后逐路径复查响应头**。

---

### 🔐 审批确认 / 委托链面 12 项验收列（V1.81，可直接进标书）

> 完整矩阵（12 项逐条 → 控制点 → 仓库真实文件 → 现场验收 / 买方必问 5 问 / 竞品差异化 / 三档报价 / 24-48-72h）见 [`approval-chain-boundary-2026-11-27.md`](approval-chain-boundary-2026-11-27.md)。

| # | 验收项 | 现场验收方式 |
| --- | --- | --- |
| 1 | 审批人须与请求人隔离，自批拒绝（CP1） | `tokenSubject=principal` → DENY@CP1 |
| 2 | 确认令牌不得由调用方自签发/自返回（CP1） | `tokenIssuer=SELF` → DENY@CP1 |
| 3 | 审批人须在独立 allowlist（CP1） | 任意用户名签发 → DENY@CP1 |
| 4 | 状态变更强制独立审批（CP1） | 空令牌 PAYMENT → DENY@CP1 |
| 5 | 服务端独立定级 scope（CP2） | claimed=admin/effective=read → DENY@CP2 |
| 6 | 声明只进审计不进允许分支（CP2/CP6） | `readOnlyHint` 不影响判定，仅落审计 |
| 7 | 令牌 TTL + 一次性 + payload 绑定（CP5） | 过期/未来/篡改/重放全 DENY@CP5 |
| 8 | 只读入口内部委托动作逐段重判（CP3） | `git_diff` 委托 `gc.recentObjectsHook`/`promisorfetch` → DENY@CP3；`blame_read` 放行 |
| 9 | 真实路径 confinement（CP4） | 绝对路径 / `../outside/secret.xml` → DENY@CP4 |
| 10 | 后缀白名单 + 禁覆盖 + 全链软链拒绝（CP4） | `.sh`/已存在文件/软链目录 → DENY@CP4 |
| 11 | append-only 脱敏审计（CP6） | 令牌原文不入盘、仅记 `sha256:` 前 12 位 |
| 12 | 合法调用 0 误杀 + 不得劣于旧基线 | 8 条合法对照全 ALLOW；旧基线漏 15/20；`java ApprovalChainGuardSelfTest` → **31 断言 / 0 误杀** |

> 全部 12 项可在 [`examples/approval-chain-guard/`](../examples/approval-chain-guard/) 现场跑（纯 JDK 17 零依赖，不进 Maven reactor）。
> ⚠️ 守卫**未接入主模块**；审批人为字符串 allowlist、令牌为进程内 hash 绑定，**非密码学签名 / 非独立身份系统**；confinement 是路径判定非竞态防护。**本框架不受 131688/136620/v2.5.0 等第三方 CVE 直接影响。**

---

_本清单随项目版本持续更新。最后更新: V1.81 (2026-11-27) · 攻击面 46 → 48 类_
