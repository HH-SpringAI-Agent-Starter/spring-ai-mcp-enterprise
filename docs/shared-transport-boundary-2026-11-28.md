# 共享传输 / 跨客户端隔离面也不是安全边界（V1.82，2026-11-28）

> 十四段式收口第十四段：注解 → 注册中心 → 只读声明 → 决策层 → 配置面 → 结果面 → 身份传播面 → 会话/传输绑定面 → 执行环境隔离面 → 浏览器出站响应面 → 日志/遥测输出面 → 持久化记忆面 → 审批确认/委托链面 → **共享传输 / 跨客户端隔离面**。
>
> 判词：**传输实例、JSON-RPC id、scope 声明与绑定地址是路由事实，不是「这条消息属于谁、这笔账该谁付」的证明。共享一处实例，多个客户端的响应与通知就会串线；只按 id 关联，从 0 开始的请求号必然碰撞。**

---

## 0. 本期证据链（近 3 天，均为第三方，本框架不受直接影响）

| # | 证据 | 事实 | 对本期论断的意义 |
| --- | --- | --- | --- |
| E1 | **CVE-2026-111770**（官方 MCP TypeScript SDK，CVSS 3.1 **7.7**，NVD 11-05 / 阿里云 AVD 11-22 再分析，CWE-362 竞态） | `@modelcontextprotocol/server` 2.0.0–2.3.0：应用在多个客户端间**共享同一个无状态 HTTP transport 或同一个 McpServer/Server 实例**时，`WebStandardStreamableHTTPServerTransport` 仅按 JSON-RPC id 写入共享 `_requestToStreamMapping`；SDK 客户端**请求 id 从 0 开始**，正常操作即碰撞，`send()` 按 id 选连接 → **一个客户端的响应/通知投递给另一个客户端**。修 2.3.0（无 sessionIdGenerator 的 transport 拒绝第二次 handleRequest + `connect()` 已连接实例抛 `ALREADY_CONNECTED`），**升级会导致复用实例失败，须改应用代码**；stdio 与工厂每调用新建实例不受影响。无在野利用、未入 KEV。 | 传输实例复用 + id-only 关联 = 跨客户端串线；这是**官方 SDK 自身**的洞，本框架核心依赖官方 SDK，必须显式披露 |
| E2 | **CVE-2026-138026**（VulnCheck 11-27，CVSS 7.3，单研究者 Syed Anas Mohiuddin） | Crypto APIs **14 个 `@cryptoapis-io/mcp-*` npm 包**：HTTP 模式绑定 `0.0.0.0` 且 `/mcp` 无任何鉴权，任何可达端口者可用**运营者预置 API key** 调全部工具——花付费额度、读账户数据、改区块链事件 webhook、删/重同步 HD 钱包（key 本身不回传、资金不可转）。修包 + 绑 `127.0.0.1` 或设 `MCP_AUTH_TOKEN`；官方要求**端口曾可达者轮换 API key 并复查账户/订阅/钱包**。stdio 模式与无 key 启动的 HTTP 不受影响。 | 「绑定位置」不是授权；「谁付费」必须绑定到调用方，不是绑定到服务器进程 |
| E3 | **11-27 同批 8 个 MCP CVE**（VulnCheck，同研究者，覆盖 Crypto APIs / bigcommerce-api-mcp / Wazuh-MCP-Server / bagel-mcp / WhoDB / mcp-video-analyzer / SylphxAI pdf-reader-mcp） | 三类成因：**网络绑定 HTTP 传输缺鉴权** / URL 处理 SSRF / **SQL 闸门漏掉写文件语句**。一天 8 个同源 CVE 说明这是一整类工程模式问题，不是单品纰漏。 | 单点修补不够；需要「绑定 + 路由 + 管理端点 + 调用方凭证」成套执行门 |
| E4 | **CVE-2026-77318 / 77359**（Jupyter MCP Server，11-19，CVSS 7.3 / 9.3） | standalone streamable-http 模式对 `/mcp` 强制 `MCP_TOKEN`，但**状态变更的 `/api/connect` 与 `/api/stop` 管理路由无鉴权**；浏览器 DNS rebinding 可经管理路由劫持 Jupyter 后端。 | 数据面有 token ≠ 管理面有 token；管理路由必须独立鉴权 |
| E5 | **Azure AI Gateway 11-28 正式 GA**（独立服务，17 区域） | 每网关每月 1 万免费请求；2027-01-01 起计费 **$500/网关/月含 1000 万请求，超出 $20/百万**；联邦 MCP servers、把 OpenAPI/连接器转成 MCP 工具；把**调用方 key 换成自己的 managed identity token** 再转发；按 user/team/app/agent/provider 追踪成本与预算。实测者指出：关联 Foundry 并**不阻止直连 Foundry**，须私有端点 + RBAC 才能强制走网关。 | 正面标杆：调用方 key 在网关处被替换/隔离、成本按调用方归属；同时证明「接入了网关」不等于「流量都过了网关」 |
| E6 | **Archive Intel MCP Server 11-25 上线**（金融合规，Claude Compliance API） | 面向 RIA/券商查询归档通信，**WORM 只读归档**上做服务端 RBAC（数据在到达 AI 前先按来源权限过滤）、每条回答引用源消息、所有 prompt/query 记录审计。MCP 定价 **$100/月或 $1200/年/机构**，年合同 ≥$12K 免费包含。 | 又一个「帮你管/帮你审」的付费样本：读路径也要按调用方隔离 + 可审计 |

---

## 1. 十二项共享传输面现场验收清单

> 逐条给出：**验收项 → 控制点 → 真实文件 → 现场验收方式**。本清单是买方 POC 的提问脚本，不是营销话术。

| # | 验收项 | 控制点 | 真实文件 | 现场验收 |
| --- | --- | --- | --- | --- |
| 1 | 传输/服务实例是否禁止跨客户端复用 | CP1 | `McpSharedTransportGuard.java` CP1 `ATTACHED` | 先让 alice attach `t1`，再让 bob 用 `t1` → DENY@CP1（A1–A6） |
| 2 | 同一 principal 复用自己实例是否不误杀 | CP1 | 同上 `putIfAbsent` | alice 二次用自己的 `t-c9` → ALLOW（C9） |
| 3 | STDIO 单客户端是否豁免 | CP1 | 同上 `mode != STDIO` | STDIO 无 session → ALLOW（C1） |
| 4 | 消息是否强制会话归属，而非只靠 JSON-RPC id | CP2 | 同上 CP2 `sessionId` / `sessionPrincipal` | id 从 0 开始但无 session / session 绑他人 → DENY@CP2（A7–A11） |
| 5 | scope 声明 / readOnlyHint 是否只进审计不进允许分支 | CP2/CP6 | 同上 `claimedScope` / `appendAudit()` | 声明 ADMIN、hint=true 但会话正确 → 仍 ALLOW（C6） |
| 6 | 管理路由是否独立鉴权 | CP3 | 同上 CP3 `MGMT_ROUTES` / `adminToken` | `/api/connect`、`/api/stop` 无独立 admin token → DENY@CP3（A12–A15） |
| 7 | 非 loopback 绑定是否 fail-closed | CP4 | 同上 CP4 `LOOPBACK` / `authToken` | `0.0.0.0`、`192.168.1.100` 无 token → DENY@CP4（A16–A18） |
| 8 | 调用方凭证是否按 owner 隔离 | CP5 | 同上 CP5 `credentialOwner` vs `principal` | operator key 被 alice/bob 消费 → DENY@CP5（A19/A20） |
| 9 | 凭证原文是否不落审计 | CP6 | 同上 `sha256(...).substring(0,12)` | `SUPER_SECRET_CRED_VALUE` 不入盘、仅记 `sha256:` 前 12 位（B1） |
| 10 | 审计是否 append-only | CP6 | 同上 `AUDIT` | 条目只增不减，无 mutator 删除（B1） |
| 11 | 不完整请求是否 fail-closed | CP0 | 同上 CP0 | null / 缺字段 → DENY（B3） |
| 12 | 是否保留合法调用对照组（0 误杀）且不得劣于旧基线 | 全链 | `SharedTransportGuardSelfTest.java` C1–C9 | 9 条合法调用全 ALLOW；旧基线（transport + token）漏 17/20；`java SharedTransportGuardSelfTest` → **32 断言 / 0 误杀** |

**自检实测**：`javac -encoding UTF-8 -d .` + `java` → **20 attack + 9 control + 3 boundary = 32 断言全绿，0 合法调用误杀，旧基线（「transport 非空 + token 非空」）放行的攻击 17/20 被本期全拦**。
⚠️ 本期对照组设计阶段即规避 V1.81 两次踩坑：① 管理路由常量预存 `norm()` 小写形式、比对也走 `norm()`（避免「常量原始拼写 vs 归一化比对」误杀）；② CP4/CP3 放在 CP1/CP2 之前判定，确保每个攻击命中**目标控制点**而非被前置门抢先拦成不可达分支（避免 V1.76 CP5a 类问题）。**「只写顺风测试会选错方案」继续作为硬约束。**

---

## 2. 买方必问 5 问（不合格 / 合格回答对照）

| # | 问题 | ❌ 不合格回答 | ✅ 合格回答 |
| --- | --- | --- | --- |
| 1 | 多个客户端连同一个 MCP server，响应会不会串？ | 「用 JSON-RPC id 区分就行」 | 「实例不跨客户端复用；消息必须带会话归属并绑定调用方；官方 SDK CVE-2026-111770 正是只按 id 关联、从 0 开始碰撞导致串线，修 2.3.0 且须改应用代码」 |
| 2 | 服务绑在内网地址、加个 token 是不是就够了？ | 「绑 0.0.0.0 配个 token 就行」 | 「非 loopback 绑定无 token 一律 fail-closed；管理路由 `/api/*` 另设独立 admin token；Crypto APIs 138026 就是 0.0.0.0 无鉴权被花掉运营者 key，端口曾可达还须轮换凭据」 |
| 3 | 认证的 key 谁付费、能不能一个 key 大家用？ | 「用一个服务级 key 统一调」 | 「凭证按 owner 隔离，调用方只能花自己的 key；网关侧把调用方 key 换成 managed identity 再转发，成本按 user/team/agent 归属（Azure AI Gateway 11-28 GA 做法）」 |
| 4 | 接了网关是不是流量就都受控了？ | 「接了网关就都管住了」 | 「不：关联上游并不阻止直连（Azure 实测者明确指出），须私有端点 + RBAC 强制过网关；本期 CP1 也拦「同一实例被第二个客户端复用」的绕过」 |
| 5 | 出事以后能不能定位到是哪个客户端、哪笔账？ | 「有访问日志」 | 「append-only 审计记传输实例、会话、路由、声明、调用方与凭证摘要（原文不入盘）；32 断言可现场复现，逐条对应真实 CVE」 |

---

## 3. 竞品差异化

| 维度 | 通用 Agent 平台 / 网关 | 官方 MCP Server 现状 | 本框架 V1.82 |
| --- | --- | --- | --- |
| 实例复用 | 单实例服务多客户端（性能优先） | 无状态 transport 共享 → 111770 串线 | CP1 禁跨客户端复用，同 principal 放行，STDIO 豁免 |
| 消息关联 | 按 JSON-RPC id | id 从 0 开始，共享 `_requestToStreamMapping` | CP2 强制会话归属 + principal 绑定 |
| 管理端点 | 与数据面同 token 或无 | Jupyter 77318：`/mcp` 有 token、`/api/*` 无 | CP3 管理路由独立 admin token |
| 网络绑定 | 默认 0.0.0.0 便于接入 | Crypto APIs 138026 无鉴权 | CP4 非 loopback 无 token fail-closed |
| 凭证归属 | 服务级 key 统一 | 运营者 key 被任意调用方消费 | CP5 owner==caller + 审计只记摘要 |
| 版本治理 | 「升级即可」 | 111770 升级会破坏复用实例、须改代码 | 升级 + 应用层消除共享实例 + 32 断言复验 |
| 可审计性 | 访问日志 | 因 server 而异 | append-only、凭证原文不入盘、32 断言可现场复现 |

**边界**：本框架**不声称**替代上述任何第三方产品的安全责任；Guard 仍在 `examples/` 层，**未接入 21 个主 reactor 模块**；`ATTACHED` 是进程内 Map、会话/令牌绑定是字符串标志，**非分布式会话存储 / 非密码学签名 / 非真实传输层实现**；不是生产组件。官方 MCP **Java** SDK 仍为核心依赖，须单独披露版本；CVE-2026-111770 影响的是 **TypeScript** SDK，须按依赖语言区分。

---

## 4. 三档报价（公开挂牌价口径，非成交价）

| 档位 | 交付物 | 周期 | 参考价位 |
| --- | --- | --- | --- |
| **L1 共享传输审计包** | 12 项清单现场验收 + 32 断言复现 + 旧基线漏检对照 + 一页加固建议 | 2 周 | **$15K–30K** |
| **L2 集成加固** | CP1/CP2/CP3/CP5 接进 `mcp-gateway`，含按会话实例工厂、管理路由独立鉴权、调用方凭证隔离 | 4–8 周 | **$80K–220K** |
| **L3 生产级私有化** | CP1–CP6 全链接入 + 与既有 IdP/网关对接 + 私有端点强制过网关 + 逐路径复验 + 运维审计包 | 3–6 个月 | **$300K–700K**，运维 **$5K–25K/月** |

> 对标公开挂牌：Azure AI Gateway 11-28 GA **$500/网关/月（含 1000 万请求）+ 超量 $20/百万**、每网关 1 万免费请求/月；Archive Intel MCP **$100/月或 $1200/年/机构**；Launch Day 2026 L1 $100K–300K / L2 $300K–700K / L3 $1M+；可信审计独立成项 $15K 起。以上均为挂牌价，非成交价，不构成报价承诺。

---

## 5. 24 / 48 / 72 小时交付节奏

| 时点 | 交付 |
| --- | --- |
| **24h** | 12 项清单 + 32 断言输出 + 旧基线 17/20 漏检对照压成 1 页 PDF；对位小米 A18742 / 中邮消费金融 25-35k·16薪 JD 逐条写应答 |
| **48h** | 在客户给定 PoC 环境复现 A1–A20（跨客户端串线、id 从 0 碰撞、管理路由裸奔、0.0.0.0 无鉴权、operator key 被消费），出逐条 PASS/FAIL 表 |
| **72h** | 出 L2 集成方案与工作量拆分，标明「进程内 Map / 字符串标志 / 按语言区分 SDK 洞」等演示约束与生产替换清单 |

---

## 6. 诚实标注

- 本框架**不受** CVE-2026-111770 / 138026 / 77318 / 77359 等第三方漏洞直接影响；**CVE-2026-111770 影响的是官方 TypeScript SDK，本框架核心依赖的是官方 Java SDK**，须按依赖语言区分、不可混称「本框架有此洞」。
- CVE-2026-111770：无在野利用、未入 KEV；CVSS 3.1 7.7（阿里云 AVD 6.4 为另一口径）；修复 2.3.0 **会破坏已有复用实例、须改应用代码**，不是无缝升级。
- CVE-2026-138026：14 包同源、VulnCheck 11-27 发布，官方 09-25 已修；**轮换 key 不能撤销攻击者已做的改动**（webhook/钱包已被改须复查）。
- 11-27 同批 8 CVE 为单一研究者发现、经 VulnCheck 发布；具体每包版本区间须回各自 GHSA 复核。
- Azure AI Gateway $500/月 pricing **2027-01-01 起计费**，当前每网关 1 万免费请求/月；findings 来自实测者博客（一手部署经验，非厂商承诺）。
- Archive Intel $100/月、$1200/年、节省数字为厂商公开定价/自述；WORM 只读归档、服务端 RBAC 为其架构声明。
- 招标存量项（中科院大连化物所 270 万智能体服务器、天津渤海职院 190 万、太原理工 200 万、眉山彭山中医院 4.98 万）**均未点名 MCP，不计入可投清单**；广发四份预征集≠采购邀请、13327 正文无截止须回平台核实。
- 全部报价为公开挂牌价、非成交价；Spring AI 2.0 未升级。
