# 浏览器 / 出站响应面也不是安全边界 —— 12 项可验收清单（V1.78 · 2026-11-24）

> 十段式收口第十段。前九段管请求端，这一段管**服务器发出去的响应头、监听的网卡、工具的实参**。
> 配套可运行样品：`examples/browser-response-guard/`（纯 JDK 17 零依赖，javac + main 自检全绿）。

## 判词

**CORS 是跨域读取策略、不是认证；loopback 是网络位置、不是授权；session id 是传输句柄、不是身份。**

前九段（注解 → 注册中心 → 只读声明 → 决策层 → 配置面 → 结果面 → 身份传播面 → 会话/传输绑定面 → 执行环境隔离面）全部守的是**入站请求侧**。但 `CVE-2026-34237` 证明：即使请求端的会话、Origin、Host、mTLS 全部正确，只要服务器响应里带一个 `Access-Control-Allow-Origin: *`，攻击者页面就能借受害者浏览器读到 SSE 首帧的 session id、再带 id 跨域 POST——**边界从出站响应头这一侧被拆掉**。Mendix 双 CVE 再补一刀：只绑 loopback 也无法阻挡 rides 合法浏览器会话的 CSRF。

必须在服务端独立收口三处：**出站响应头（CORS）、监听地址（bind）、工具实参（argv）**，默认 fail-closed。

## 一、12 项浏览器 / 出站响应面验收清单（逐条 → 控制点 → 真实文件 → 现场验收）

| # | 验收项 | 控制点 | 仓库真实文件 | 现场验收方式 |
| --- | --- | --- | --- | --- |
| 1 | 通配 `ACAO: *` 拒绝（含 SSE / Streamable HTTP 双传输） | CP1 CORS | `McpBrowserResponseGuard.checkCors()` | `acao("*")` → CORS_WILDCARD；两条 transport 分别测 → 均拦截 |
| 2 | `allow-credentials` 不得配通配 | CP1 CORS | `checkCors()` | `acao("*") + responseAcaCreds=true` → CORS_CREDENTIALS_WITH_WILDCARD |
| 3 | ACAO 非 allowlist 来源拒绝；后缀欺骗拒绝 | CP1 CORS | `checkCors()` `CORS_ALLOWLIST` | `https://evil.com`、`https://app.example.com.evil.com` → CORS_ORIGIN_NOT_ALLOWED |
| 4 | 响应体 session id 必须绑定签发 origin | CP2 SESSION | `checkSession()` | `sid("s-leaked") + issued(null)` → SESSION_ORIGIN_UNBOUND |
| 5 | session 句柄不得暴露给跨域可读来源 | CP2 SESSION | `checkSession()` | `issued(app) + acao(admin)` → SESSION_ID_EXPOSED_CROSS_ORIGIN |
| 6 | 只绑 loopback；`0.0.0.0`/`::`/任意网卡/文档 IP 拒绝 | CP3 BIND | `checkBind()` `LOOPBACK`/`DOC_IPS` | `bind("0.0.0.0")`/`bind("::")`/`bind("192.168.1.10")` → BIND_PUBLIC_INTERFACE |
| 7 | 不可解析绑定地址拒绝（fail-closed） | CP3 BIND | `checkBind()` | `bind("not-a-host.invalid")` → BIND_UNRESOLVABLE |
| 8 | dash 前缀位置操作数拒绝（`--flag`/`--flag=value`/`-x`） | CP4 ARG | `checkArg()` | `arg("--filename=/etc/passwd")`/`arg("-n")` → ARG_FLAG_LIKE |
| 9 | 改动类 POST 缺 Origin 拒绝 | CP5 CSRF | `checkCsrf()` | `origin(null)` → CSRF_ORIGIN_MISSING |
| 10 | Origin 不在 allowlist / 跨源拒绝 | CP5 CSRF | `checkCsrf()` | `origin("https://evil.com")` → CSRF_ORIGIN_MISMATCH |
| 11 | 缺 CSRF token / Referer 跨源拒绝 | CP5 CSRF | `checkCsrf()` | `token(null)` → CSRF_TOKEN_MISSING；`referer("https://evil.com/")` → CSRF_REFERER_MISMATCH |
| 12 | 不得劣于旧基线 + 脱敏审计 | CP6 AUDIT | `appendAudit()` `mask()` `stripQuery()` | 旧基线放行的 20 条全拦；session id 掩码、origin query 删除；`java McpBrowserResponseGuard` → **26 断言 / 0 误杀** |

> 全部 12 项可在 `examples/browser-response-guard/` 现场跑（纯 JDK 17 零依赖，不进 Maven reactor）。
> ⚠️ 参考实现**未接入主模块**；allowlist / token / 绑定地址硬编码、IP 字面量分类、session 绑定字符串标志为演示约束；**本框架不受 `34237` 等第三方 CVE 直接影响，但核心依赖官方 SDK，须部署后逐路径复查响应头**。

## 二、买方必问 5 问（不合格 / 合格回答对照）

| # | 问题 | ❌ 不合格回答 | ✅ 合格回答 |
| --- | --- | --- | --- |
| 1 | 升级官方 Java SDK 修掉通配 CORS 就安全了吗？ | 「是，我们已经升到 1.0.1/1.1.1」 | 「升级只移除 SDK 这一处；Reverse proxy / ingress / servlet container 仍可能重加通配头。我们在**每个实际路径**分别探测 SSE 与 Streamable HTTP 响应头，ACAO 只允许精确 origin allowlist，并在网关层二次剥离。」 |
| 2 | 内网部署、只绑 loopback，还需要管 CSRF / CORS 吗？ | 「内网信任，绑 localhost 就行」 | 「Mendix `112136/112137` 证明浏览器 CSRF rides 合法会话可穿透 loopback；我们强制改动类 POST 带 Origin allowlist + CSRF token + Referer 同源，浏览器永远不是可信网络位置。」 |
| 3 | session id 泄漏的后果是什么？你们怎么防？ | 「session id 不是敏感信息」 | 「34237 里它就是 bearer——页面读到 SSE 首帧 id 即可跨域 POST。我们让 session id 绑定签发 origin，句柄不对跨域可读来源暴露，并按 issuer+subject 双重校验（承接第八段）。」 |
| 4 | 工具参数做了什么校验？ | 「工具函数会做参数类型检查」 | 「类型检查管不住 CWE-88：k8s `134817` 的 `--filename=/etc/passwd` 类型合法但被当 flag。我们对所有位置操作数做 dash 前缀拒绝 + allowlist argv，参数只作数据、绝不作控制。」 |
| 5 | 怎么证明这套不是 PPT？ | 「我们有架构图」 | 「`examples/browser-response-guard/` 纯 JDK 零依赖，javac + main 现场跑 26 断言全绿：20 attack 全拦、4 control 0 误杀、2 boundary（审计脱敏 + 空请求 fail-closed）通过；旧基线放行的 20 条新守卫全拦。」 |

## 三、竞品差异化（为什么是 Java + Spring 的主场）

| 维度 | 通用云网关 / Python 系 | 本框架（Java + Spring + AI） |
| --- | --- | --- |
| 出站响应头治理 | 升级 SDK 即止；通配头常被反向代理重新加回 | 网关层二次剥离 + 逐路径 SSE/Streamable 双传输探测，响应头作为独立控制面 |
| 本地绑定 | 依赖部署文档「建议绑 loopback」 | 启动期 IP 分类强校验，`0.0.0.0`/`::`/网卡/文档 IP fail-closed |
| 存量权限复用 | 需为 MCP 重建身份体系 | Spring Security / RBAC / 数据归属在调用点直接复用，不重建 |
| 工具实参安全 | 靠函数签名类型检查（挡不住 CWE-88） | dash 前缀拒绝 + argv allowlist + 参数只作数据 |
| 可验收工件 | 架构图 + 承诺 | 12 项清单 + 26 断言纯 JDK 自检 + 私有化审计留痕 |

## 四、三档报价（锚定 Launch Day Advisors 2026 全量分档）

| 档 | 范围 | 对位本框架能力 | 参考价（partner-built，非成交价） |
| --- | --- | --- | --- |
| **L1 只读 · 单客户端** | OAuth2.1+PKCE + 5–10 工具 + 基础审计 | 十段式中 CP1/CP3/CP5 出站与绑定治理 + 审计 | **$100K–$300K**（~1 季度） |
| **L2 动作 · 单/双客户端** | 写操作幂等/可撤销/意图预览 + 强制审计 + 多客户端 | 决策层 + 结果面 + 身份面 + 十段式全链 | **$300K–$1.2M**（~2–3 季度） |
| **L3 agent-resident + 年度托管** | 全量治理 + 规格版本变更复验 + $5K–25K/月运维 | 十段式可演示样品 + AAIF 规格受监控依赖 + 私有化合规工件 | **$1M+**（多季度） |

> 报价按交付物 / 集成 / 修订轮次拆，别只抬时薪（OpenAI×Upwork：AI 项目固定价 +52.4%、时薪仅 +1.5%）。

## 五、24 / 48 / 72 小时行动清单

- **24h**：把本文 12 项清单 + `java McpBrowserResponseGuard` 26 断言输出压成 1 页 PDF 售前附件；发 SEO 稿抢「MCP Java SDK 34237 / Mendix 112136 / k8s 134817」流量。
- **48h**：独立分支把 CP1（ACAO allowlist）/ CP3（loopback 绑定）/ CP4（flag 拒绝）/ CP5（CSRF）接进 `mcp-gateway` 过滤器链，先跑通 26 断言；回广发平台核实 13327 报名截止。
- **72h**：备标中原银行 AI 网关（AI 网关 + MCP 代理）；在真实 ingress 对 SSE / Streamable HTTP 两路径做响应头探测取证，形成可交付的「部署后复查报告」模板。

## 六、诚实边界

- 本框架**不受** `34237`（官方 Java SDK）/ Mendix `112136/112137` / k8s `134817` 等第三方 CVE 直接影响；
- 但核心依赖官方 SDK，`34237` 须显式披露：升级只移除一处绕过，反向代理仍可能重加通配头；
- 参考守卫**未接入 21 主模块**，allowlist / token / 绑定地址硬编码、IP 字面量分类、session 绑定字符串标志均为演示约束；
- 报价为公开 partner 估算非成交价；414 台审计 91.8% 无 OAuth 为第三方数据非本框架实测；Spring AI 2.0 未升级。

---

_最后更新: V1.78 (2026-11-24) · 十段式收口第十段 · 攻击面 40 → 42 类_
