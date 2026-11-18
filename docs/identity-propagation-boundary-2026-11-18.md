# 身份传播边界：12 项身份面验收清单（V1.75 · 2026-11-18）

> **本期最有挣钱价值的一页纸。** 可直接摘进标书「技术条款响应 / 验收标准」章节。
> 核心判词：**声明「不接收身份」不是安全边界；必须在每次出站调用点强制裁剪身份字段、校验签名新鲜度、拒绝 header 覆盖、绑定租户，并 fail-closed。**

## 0. 为什么是「身份传播面」——六段式收口的第七段

| 段 | 判词 | 版本 |
| --- | --- | --- |
| 1 注解面 | 注解不是安全边界 | V1.69 |
| 2 注册中心面 | 注册中心自己也不是安全边界 | V1.70 |
| 3 只读声明面 | `read_only` 声明也不是安全边界 | V1.71 |
| 4 决策层 | 审批弹窗 / 提示词护栏也不是安全边界 | V1.72 |
| 5 配置面 | 治理权可中立，授权不能外包 | V1.73 |
| 6 结果面 | 工具返回给模型的结果也不是安全边界 | V1.74 |
| **7 身份传播面（本期）** | **per-server 身份转发声明也不是安全边界** | **V1.75** |

前六段管「谁能动 / 怎么判 / 交出什么」，第七段管**「被调用方真正收到谁的身份」**——
身份令牌在网关→上游 server 的转发途中被错误路由、被覆盖、被重放，则前面六段的授权判定全部落空。

## 1. 证据链（本期实时抓取，11-12~11-18）

| # | 事实 | 来源 | 对位控制点 |
| --- | --- | --- | --- |
| 1 | ⭐⭐ IBM `CVE-2026-110278`（CVSS 7.4，11-12）：ContextForge MCP Gateway 1.0.0–1.1.0 的 per-server 身份转发配置（user id / email / group / team）在 agent 调用工具/读资源时被**忽略**，身份被发给明确配置为「不接收」的上游 server；**静态签名 payload 可被捕获重放**冒充用户。修 1.1.1，无法升级须全局禁用身份传播 | [AI Governance Institute](https://aigovernance.com/news/ibm-mcp-gateway-flaw-sends-user-identity-to-servers-configured-to-block-it) | CP1 / CP3 |
| 2 | IBM `CVE-2026-110279`（11-12）：客户端可通过 header 覆盖网关上游凭据，破坏 per-user 权限与审计归因，交易场景尤其致命 | [AI Agent Store 周报](https://aiagentstore.ai/ai-agent-news/topic/trading/2036-08-26) | CP4 |
| 3 | Impartner MCP（11-17）：**「governed by each user's existing permissions」**，按个人账号鉴权，**敏感字段对 AI 助手隐去**；接入 Claude / ChatGPT / Copilot / Gemini Enterprise | [Impartner 官方](https://impartner.com/resources/blog/impartner-launches-mcp-server-to-connect-ai-assistants-with-live-prm-data) | CP2 / CP5 |
| 4 | Kaseya（11-17）：首发 Autotask/Datto RMM/IT Glue 的 first-party MCP server + 17 skills；自报 48% MSP 视 AI 为头号需求但仅 13% 变现，20% 已在生产跑 MCP → **「MCP as the Governance Layer, not just a technical convenience」** | [Futurum Group](https://futurumgroup.com/insights/kaseya-bets-on-agentic-it-to-close-msps-ai-revenue-gap/) | 商务话术 |
| 5 | 券商 AI 协议战（11-16）：广发明确「依托 MCP 接入生态」，一次封装多处调用；**数据按客户经理归属严格隔离**；恒生研究院「业务本体地图才是壁垒，堆 Skill 无护城河」；第三方内测 Skill 输出准确率最高 95% **仍需人工把关** | [联索科技](https://lexotech.com/news/news-content?did=87674686f0e2f2c8a1a985ff9a27f802) / [观点网](https://www.guandian.hk/article/20261116/10065424.html) | CP5 / HITL |
| 6 | 广发研究 MCP&Skills 已上线智慧数平台（11-13）：21 行业 / 200+ 数据源 / 9 万余指标 / 1000+ 公司盈利预测 / 900+ 产业图谱 → **买方已自建，钱从「帮你建」转到「帮你管」** | [网易](https://dy.163.com/article/LBKQS44F0519D45U.html) | 定价锚 |

> ⚠️ 诚实边界：本框架**不受**上述 IBM CVE 直接影响（不是 ContextForge）；引用为设计依据与买方话术，非「已修复自身漏洞」声明。IBM 修复版本以官方 Bulletin/GHSA 为准。

## 2. 12 项身份面验收清单（可直接进标书）

对位参考实现：[`examples/identity-propagation-guard/`](../examples/identity-propagation-guard/README.md)（`javac` + `main()` 自检 **10 攻击 + 3 合法对照 + 2 边界 = 15 断言全绿 / 0 误杀 / 旧基线漏 3 条全拦**）。

| # | 验收项 | 控制点 | 仓库真实文件 | 现场验收方式 |
| --- | --- | --- | --- | --- |
| 1 | server 声明「不接收身份」的字段，在调用工具/读资源时**确实未收到** | CP1 | `McpIdentityPropagationGuard.java` `declaredBlockedFields` 分支 | 配置 email=block，任务请求 email → 必须 fail-closed 拒绝，且上游抓包无 email |
| 2 | 身份字段按任务按需下发，非必需字段被剥离 | CP2 | 同文件 `stripped` 列表 | 任务只需 userId，构造携带 email/groups 的身份 → 转发仅含 userId，审计记 stripped |
| 3 | 静态/硬编码签名令牌被拒绝 | CP3 | `STATIC_OR_MISSING_SIGNATURE` | 重放固定 token → 拒绝，返回码可核对 |
| 4 | 签名时间戳新鲜度窗口（5 分钟）可配置并生效 | CP3 | `FRESH_WINDOW_SECONDS` / `STALE_SIGNATURE` | 用 901 秒前时间戳签名 → 拒绝；窗口内 → 放行 |
| 5 | nonce 防重放，同一令牌二次使用被拒 | CP3 | `usedNonces` / `REPLAYED_NONCE` | 同一签名连续两次调用 → 第二次拒绝 |
| 6 | HMAC/签名完整性校验，篡改 payload 即拒 | CP3 | `constantEquals` / `BAD_SIGNATURE` | 改 userId 不改签名 → 拒绝；恒定时间比较 |
| 7 | 客户端 header 不得覆盖上游凭据与身份 | CP4 | `FORBIDDEN_HEADERS` | 注入 `X-User-Id` / `Authorization` → 拒绝 |
| 8 | 身份 tenant 与目标 server tenant 绑定，跨租户调用失败 | CP5 | `TENANT_MISMATCH` | tenant-A 身份调 tenant-B server → 拒绝 |
| 9 | 审计 append-only，email 打码、groups 只记数量 | CP6 | `auditTrail()` / `mask()` | 检查审计串不含明文 email，且不可回改历史条目 |
| 10 | 未知/未注册 server 一律拒绝（默认拒绝） | fail-closed | `UNKNOWN_SERVER` | 调用未注册 serverId → 拒绝 |
| 11 | 无认证身份上下文一律拒绝 | fail-closed | `EMPTY_IDENTITY` | identity=null → 拒绝 |
| 12 | 「不得劣于旧基线」：旧实现放行的攻击，新守卫必须拦下 | 回归 | `legacyBaseline()` 对照断言 | 静态签名/header 覆盖/跨租户 3 例：旧基线放行、新守卫全拦 |

## 3. 买方必问 5 问（含不合格 / 合格回答对照）

| # | 问题 | ❌ 不合格回答 | ✅ 合格回答 |
| --- | --- | --- | --- |
| 1 | 我们的 PRM/CRM 声明了「不给 AI 助手邮箱字段」，你们怎么保证真没转发？ | 「配置里设一下就行」 | 「在每次出站调用点按 allowlist 强制裁剪 + block 字段 fail-closed，配置声明只作为审计输入，不作为放行依据」 |
| 2 | 用户身份令牌被截获后能否重复使用？ | 「靠 HTTPS 就安全」 | 「签名带 5 分钟时间戳 + nonce 一次性消费 + HMAC 完整性，捕获后过期或重放均拒绝」 |
| 3 | Agent 能不能通过 header 把自己伪装成另一个用户？ | 「网关注入 header 覆盖」 | 「拒绝调用方传入的身份/凭据类 header，身份只取已认证上下文」 |
| 4 | 多客户经理的数据怎么隔离？ | 「前端做权限」 | 「身份 tenant/归属与目标 server 强绑定，跨归属调用服务端拒绝，不依赖客户端自觉」 |
| 5 | 出了身份泄露怎么追溯？ | 「看网关日志」 | 「append-only 审计 record 谁的身份在何时被转发给哪个 server，字段打码、只记数量，可导出合规工件」 |

## 4. 竞品差异化（本期口径）

- **云网关 / 商业 MCP 网关**（Azure APIM AI Gateway、AWS AgentCore、腾讯云 MCP 网关、IBM ContextForge、SnapLogic Discovery Hub 11-17）：把注册/路由/可观测做成标准层，但 `110278`/`110279` 说明**网关本身也会错转发身份**；本框架是网关**内外都可挂的确定性身份执行门**，不依赖单一厂商实现正确。
- **纯 SDK / 手写 server**：官方 SDK 只给鉴权原语，不给「按任务裁剪字段 + 新鲜度 + 跨租户绑定」的开箱门；`examples/identity-propagation-guard/` 是可运行参考。

## 5. 三档报价（承接 V1.67 治理层定价锚）

| 档 | 范围 | 报价锚 | 说明 |
| --- | --- | --- | --- |
| 售前诊断 | 身份传播面 12 项验收 + 现有网关配置核查 + 一份整改清单 | ¥3–6 万 | 低门槛切入，对应广发/券商「帮你管」需求 |
| 治理增强层交付 | CP1–CP6 落地 + 与主网关集成 + 压测/回归 + 私有化部署 | ¥25–60 万 | 对标 WebsNexus 多系统写+OAuth+审计 **$899 起**、生产级 $80K–220K |
| 年度托管 | 7 段边界持续复验 + CVE 跟踪 + 规格版本变更全量复验 + SLA | ¥15–40 万/年 | 对应 AAIF 规格变更「旧审批不继承、变更后全量复验」 |

> 报价均为公开挂牌价/估算的组合推导，**非成交价**；本项目无企业主体/合同/中标/金融案例/等保测评。

## 6. 24 / 48 / 72 小时行动清单

- **24h**：把本清单 + 15 断言输出 + 云网关对照压成 1 页 PDF，附潜在广州本地联合体主体报名材料；核原厂授权 / 金融案例 / 完税 / 广州本地团队。
- **48h**：发 SEO 稿抢「IBM 110278 身份转发 / 静态签名重放 / 身份传播面 / Kaseya MCP」流量；跟踪广发智慧数平台是否开放第三方治理模块接入。
- **72h**：评估把 `McpIdentityPropagationGuard` 的 CP1/CP3/CP4/CP5 接进 `mcp-gateway`（独立分支先跑通 15 断言）；回广发采购平台核实 13327 报名截止与材料清单。
