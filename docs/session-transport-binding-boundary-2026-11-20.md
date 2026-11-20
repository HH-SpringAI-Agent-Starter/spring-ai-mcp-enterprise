# 会话 / 传输绑定边界：12 项会话面验收清单（V1.76 · 2026-11-20）

> **本期最有挣钱价值的一页纸。** 可直接摘进标书「技术条款响应 / 验收标准」章节。
> 核心判词：**sessionId 不是安全边界；Host / Origin 头不是装饰；OAuth 凭据必须绑定 issuer——
> 必须在每次调用点强制校验会话主体绑定、Origin 白名单与会话一致、Host RFC 合法性、凭据 issuer 绑定，默认只读且 fail-closed。**

## 0. 为什么是「会话 / 传输绑定面」——八段式收口的第八段

| 段 | 判词 | 版本 |
| --- | --- | --- |
| 1 注解面 | 注解不是安全边界 | V1.69 |
| 2 注册中心面 | 注册中心自己也不是安全边界 | V1.70 |
| 3 只读声明面 | `read_only` 声明也不是安全边界 | V1.71 |
| 4 决策层 | 审批弹窗 / 提示词护栏也不是安全边界 | V1.72 |
| 5 配置面 | 治理权可中立，授权不能外包 | V1.73 |
| 6 结果面 | 工具返回给模型的结果也不是安全边界 | V1.74 |
| 7 身份传播面 | per-server 身份转发声明也不是安全边界 | V1.75 |
| **8 会话 / 传输绑定面（本期）** | **sessionId / Host / Origin 头也不是安全边界** | **V1.76** |

前七段管「谁能动 / 怎么判 / 交出什么 / 收到谁的身份」，第八段管**「这条调用是从哪个会话、哪个 Origin、哪个 Host 进来的」**——
传输绑定是最后一道入口关：会话被冒用、Origin 被伪造、Host 被注入，前面七段的授权判定全部落在错误的入口上。

## 1. 证据链（本期实时抓取，11-17~11-19）

| # | 事实 | 来源 | 对位控制点 |
| --- | --- | --- | --- |
| 1 | ⭐⭐ `CVE-2026-132121`（Supergateway 2.4.0–4.2.2）：stdio-to-WebSocket 模式无 Origin 校验，恶意网页可开 `/message` WebSocket（含 localhost 绑定网关）驱动桥接 MCP server；PoC 11-17 跨 4 源 **12/12 runs 外传本地文件**；`--apiKey` 可拒未认证升级 | [AI Security Intelligence](https://eyeon.ai/f/3883) | CP2 |
| 2 | ⭐ `CVE-2026-131857`（SGW-2026-001）：未认证攻击者可用 prototype-inherited `sessionId` 值在 stdio-to-SSE 模式打崩 Supergateway | [AI Security Intelligence](https://eyeon.ai/f/3883) | CP1 |
| 3 | ⭐⭐ `CVE-2026-130007`（11-19 NVD）：k8s-mcp-server < 1.1.0，`SERVER_MODE` 默认 SSE 监听 0.0.0.0:8080 无 auth option；`GET /sse` 的 sessionId 仅为 correlation value，`POST /message` 接受可加载 sessionId 无 authorization / Origin 校验即派发 `tools/call`；**read-only 默认关闭**，写工具以服务端集群权限运行（CWE-306） | [ThreatMonitor](https://threatmonitor.io/cve/CVE-2026-130007) | CP1 / CP5 |
| 4 | ⭐ `CVE-2026-48710` BadHost（Starlette，CVSS 6.5 被批低估）：Host 含 `/ ? #` 使 URL 重解析边界漂移，`Host: foo?` 令 `/admin` 被当 query、`request.url.path` 返回 `/` 绕过鉴权中间件 200 OK；Starlette 周下载 3.25 亿，影响 FastAPI / vLLM / LiteLLM；MCP 规范强制免鉴权 OAuth discovery 端点成可靠利用路径，修 1.0.1 | [The Agent Times](https://theagenttimes.com/agents/article/badhost-vulnerability-bypasses-authentication-across-our-mcp-879a13b7) | CP3 |
| 5 | ⭐ `CVE-2026-115563`（MCP Python SDK OAuth）：OAuth client 把凭据发给 server 自选 AS——issuer 未在每条 discovery path 校验、凭据未绑定 AS；修 2.2.0/1.30.0，且用 ClientCredentials/PrivateKeyJWT **必须显式传 `issuer=` 才生效** | [agentbeam](https://agentbeam.com/cve/cve-2026-115563) | CP4 |
| 6 | OWASP MCP Top 10 / 2026 best practices：静态 API key 跨服务共享是最常见失败模式；workload identity + mTLS + **每次调用经网关做 per-call 授权**；永不裸奔暴露公网 MCP server | [Unosecur](https://www.unosecur.com/security-101/mcp-security-best-practices) / [NexusVoid](https://nexusvoidai.com/research-analysis/what-is-mcp-security-model-context-protocol) | 全局 |

> ⚠️ 诚实边界：本框架**不受**上述 Supergateway / k8s-mcp-server / Starlette / Python SDK CVE 直接影响（非同类产品）；引用为设计依据与买方话术，非「已修复自身漏洞」声明。`CVE-2026-48710` 曾于 V1.64 蜜罐链出现，本期为其 Host 注入机制及 MCP discovery 端点放大的独立复验。修复版本以官方公告 / GHSA 为准。

## 2. 12 项会话面验收清单（可直接进标书）

对位参考实现：[`examples/session-transport-binding-guard/`](../examples/session-transport-binding-guard/README.md)（`javac -encoding UTF-8` + `main()` 自检 **20 攻击 + 4 合法对照 + 2 边界 = 26 断言全绿 / 0 误杀 / 旧基线漏 6 条全拦**）。

| # | 验收项 | 控制点 | 仓库真实文件 | 现场验收方式 |
| --- | --- | --- | --- | --- |
| 1 | 未认证主体的工具派发被第一道门拒绝 | CP5a | `McpSessionTransportBindingGuard.java` `decide()` 开头 `UNBOUND_LISTENER` | `principal=null` 发非握手调用 → 拒绝；不能靠「有 sessionId 就算数」 |
| 2 | prototype-inherited / constructor 形态 sessionId 被拒 | CP1 | `INHERITED_SESSION` 分支 | 传 `__proto__.sessionId` / `constructor.prototype.sid` → 拒绝（对位 131857） |
| 3 | sessionId 必须服务端 HMAC 签发，伪造/未注册/篡改签名被拒 | CP1 | `sign()` / `FORGED_SESSION` | 传 `s-deadbeef-1` 或篡改签名 → 拒绝；合法签发 → 放行 |
| 4 | session 绑定 principal，换主体调用失败 | CP1 | `SESSION_PRINCIPAL_MISMATCH` | alice 的 session 配 mallory 主体 → 拒绝 |
| 5 | WS 升级 / SSE 握手必须带 Origin，缺失或 `null` 被拒 | CP2 | `MISSING_ORIGIN` / `NULL_ORIGIN` | 握手无 Origin / `Origin: null` → 拒绝 |
| 6 | Origin 须在白名单，且与会话绑定 Origin 一致 | CP2 | `ORIGIN_NOT_ALLOWED` / `ORIGIN_SESSION_MISMATCH` | `https://malicious.page` 跨站 WS → 拒绝；绑定 Origin 不一致 → 拒绝（对位 132121） |
| 7 | Host 含问号 / 斜杠 / 井号 / @ / 控制字符被拒 | CP3 | `validHost()` / `BAD_HOST` | `Host: foo?/admin` → 拒绝；`mcp.example.com:443` 放行（对位 48710） |
| 8 | IPv6 字面量 Host 正常放行（不过度拦截） | CP3 对照 | `validHost()` `[::1]:8080` | IPv6 Host 合法调用 → 放行，0 误杀 |
| 9 | 携带 OAuth 凭据但未声明 issuer 被拒 | CP4 | `ISSUER_UNBOUND` | 有凭据无 issuer → 拒绝（对位 115563 升级后须传 `issuer=`） |
| 10 | issuer 非预期授权服务器被拒 | CP4 | `ISSUER_MISMATCH` | `issuer=https://attacker.as` → 拒绝 |
| 11 | 只读会话禁止写工具；写工具以显式授权开启 | CP5 | `WRITE_NOT_PERMITTED` / `readOnlyOnly` | 只读 session 调写工具 → 拒绝，默认不开放集群级写权限（对位 130007） |
| 12 | 「不得劣于旧基线」+ append-only 脱敏审计 | 回归 / CP6 | `legacyBaseline()` 对照 / `appendAudit()` | 旧基线放行的 6 例全拦；审计串不含完整 sessionId、不可回改 |

## 3. 买方必问 5 问（含不合格 / 合格回答对照）

| # | 问题 | ❌ 不合格回答 | ✅ 合格回答 |
| --- | --- | --- | --- |
| 1 | 我们的 MCP 网关暴露在内网，别人知道 sessionId 就能调工具吗？ | 「sessionId 是随机生成的，猜不到」 | 「sessionId 由服务端 HMAC 签发并绑定 principal+Origin；每次调用点校验签名、主体、新鲜度，未认证派发第一道门拒绝——随机性不是授权」 |
| 2 | 浏览器里的恶意网页能不能借我们的 WebSocket 网关调本地工具？ | 「网关绑了 localhost，外面访问不到」 | 「WS 升级强制 Origin 白名单且与会话绑定 Origin 一致，跨站 Origin 一律拒绝；localhost 绑定挡不住恶意网页的跨站 WebSocket」 |
| 3 | Host 头被改写会不会绕过我们的路径鉴权？ | 「鉴权看 path，Host 无所谓」 | 「按 RFC 9112/3986 逐字符校验 Host，拒绝分隔符/问号/井号/@/控制字符，鉴权判定不依赖重解析后的 URL path」 |
| 4 | OAuth 凭据会不会被 MCP server 引到它自己的授权服务器？ | 「用的官方 SDK，升级到最新版就行」 | 「客户端凭据显式绑定预期 issuer，每条 discovery path 校验，未绑定/不匹配 fail-closed；升级后不传 `issuer=` 仍会跟随 server 广告」 |
| 5 | 默认权限是什么？会不会一装上来就能写？ | 「默认全开，用的时候再关」 | 「默认只读 + 未认证拒绝，写工具与写权限必须显式授权；决策在服务端执行不依赖客户端自觉」 |

## 4. 竞品差异化（本期口径）

- **三大云 + iPaaS 网关**（Azure APIM AI Gateway / AWS AgentCore / 腾讯云 MCP 网关 3.9.8 / IBM ContextForge / SnapLogic Discovery Hub）：把注册 / 路由 / 可观测做成标准层，但 `132121`/`130007` 说明**桥接层与自建 server 的会话 + Origin + Host 仍由集成方负责**，云网关不覆盖本地 stdio↔WS 桥。
- **纯 SDK / 手写 server**：官方 SDK 只给鉴权原语，不给「会话主体绑定 + Origin 会话一致 + Host RFC 校验 + issuer 绑定 + 默认只读」的开箱门；`examples/session-transport-binding-guard/` 是可运行参考。

## 5. 三档报价（承接 V1.67 治理层定价锚）

| 档 | 范围 | 报价锚 | 说明 |
| --- | --- | --- | --- |
| 售前诊断 | 会话 / 传输面 12 项验收 + 现有网关会话与 Origin 配置核查 + 整改清单 | ¥3–6 万 | 低门槛切入，对应「帮你管」需求 |
| 治理增强层交付 | CP1–CP6 落地 + 与主网关集成 + 压测 / 回归 + 私有化部署 | ¥25–60 万 | 对标生产级 $80K–220K、企业网关 $250K 起 |
| 年度托管 | 八段边界持续复验 + CVE 跟踪 + 规格版本变更全量复验 + SLA | ¥15–40 万/年 | 对应 AAIF 规格变更「旧审批不继承、变更后全量复验」 |

> 报价均为公开挂牌价 / 估算的组合推导，**非成交价**；本项目无企业主体 / 合同 / 中标 / 金融案例 / 等保测评。

## 6. 24 / 48 / 72 小时行动清单

- **24h**：把本清单 + 26 断言输出 + 云网关对照压成 1 页 PDF，附潜在广州本地联合体主体报名材料；核原厂授权 / 金融案例 / 完税 / 广州本地团队。
- **48h**：发 SEO 稿抢「Supergateway 132121 / k8s-mcp 130007 / BadHost 48710 / 会话绑定面」流量；跟踪广发智慧数平台是否开放第三方治理模块接入。
- **72h**：评估把 `McpSessionTransportBindingGuard` 的 CP1 / CP2 / CP3 / CP5 接进 `mcp-gateway`（独立分支先跑通 26 断言）；回广发采购平台核实 13327 报名截止与材料清单。
