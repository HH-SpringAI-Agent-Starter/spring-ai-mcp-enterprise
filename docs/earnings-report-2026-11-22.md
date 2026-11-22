# MCP Server 挣钱情报日报（2026-11-22 · 近 3 天实时抓取）

> 任务 6 完整输出：企业需求 / 招标 / 招聘 / 开源动态 / 价格 / 用户 Java+Spring+AI 卖点。
> 抓取窗口 11-19~11-22，来源 18 项，全部附链接。⚠️ 报价为公开挂牌价/估算，非成交价。

## 一、今日最强信号（TOP 6）

1. ⭐⭐ **执行环境 / 沙箱成为本周 CVE 最高危面** —— MCPHub `CVE-2026-79748`（**CVSS 9.9**，非管理员注册 `command=/bin/sh` 的 server 立即执行、edit 端点无归属校验，Docker 常以 root 跑）+ Hermes Agent `GHSA-p22p-9ph6-gmrh`（11-19 High，从可写目录读回 env 注入 `LD_PRELOAD` 穿透沙箱触达 provider keys）→ 直接坐实本期第九段「执行环境隔离面」（[DugganUSA](https://www.dugganusa.com/post/vulncheck-canaries-mcp-python-sdk-mcphub-ispconfig) / [MindPattern](https://mindpattern.ai/s/2026-11-20-mcp-tools-guard-the-path-their-authors-pictured-and-leave-the-next-one-open)）。
2. ⭐⭐ **「读路径有 guard、写路径跳过」成pattern** —— NVD 11-18 三 CVE：Gortex `62964`（7.8，read 查 confinement、write 收绝对路径可写 `~/.zshrc`）、yt-mcp `75612`（7.5，`add_attachment` 读任意文件外发）、DOMShell `75518`（标 `readOnlyHint:true` 却跑 CDP `Runtime.evaluate`，客户端审批 UI 被骗）→ 审计 MCP server 必须读写两条路径都查（[MindPattern](https://mindpattern.ai/f/35322)）。
3. ⭐⭐ **MCP Python SDK 官方洞进在野目录** —— `CVE-2026-52869`（7.1，VulnCheck 11-20 canaries）：SSE / stateful Streamable HTTP 仅凭 session_id 路由、不校验同一认证客户端；月下载 3.31 亿次；多人共享 OAuth client 不填 `AccessToken.subject` 则只按 client 不按 user 分离 → 升级须到 1.30.0 / 2.2.0（1.27.2 只修本洞、9 月 OAuth 洞仍在）→ **升级不等于修好**（[DugganUSA](https://www.dugganusa.com/post/vulncheck-canaries-mcp-python-sdk-mcphub-ispconfig)）。
4. ⭐ **DNS rebinding / Host 校验持续爆发** —— Jupyter MCP Server（9.3，`/api/connect` 无保护）、Todoist `CVE-2026-76807`（未校验 Host/Origin + 通配 CORS，恶意网页驱动 agent SaaS 账户）、obsidian-sync-mcp（无 `MCP_AUTH_TOKEN` 暴露 vault）、BadHost `CVE-2026-48710`（Starlette Host 注入问号绕过鉴权，周下载 3.25 亿，MCP 免鉴权 discovery 端点放大）→ 传输边界与执行环境必须联动（[eyeon.ai](https://eyeon.ai/f/3918) / [The Agent Times](https://theagenttimes.com/agents/article/badhost-vulnerability-bypasses-authentication-across-our-mcp-879a13b7)）。
5. ⭐ **企业侧「MCP + Skills + Sandbox」成高薪岗位标配** —— 外企德科上海 AI 大模型应用开发 **2.5–5 万·16薪** 逐字「MCP + Skills 企业级建设 + Sandbox 安全隔离 + 企业数据安全治理加分」；广州自我游 AI 智能体岗 12-22k 逐字「Spring Boot + Spring AI + MCP Server + Agent Skills 工程化落地」（[智联招聘](https://m.zhaopin.com/jobs/CC138117190J40902547605.htm) / [猎聘](https://m.liepin.com/job/1986214177.shtml)）。
6. ⭐ **买方开始按「scope 项目」 hiring 而非招人** —— DEV Community《Hiring a Custom MCP Server Developer》：单系统 1–2 周、多系统+auth+远程 2–4 周、多租户/合规 4–8 周，明确「writes need confirmation patterns, validation and audit logging」「prompt injection：server 返回的文本不能未经检查就驱动写工具」→ 与本期六道闸逐条对位（[DEV](https://dev.to/modernstackmac/hiring-a-custom-mcp-server-developer-how-to-scope-the-project-4ejk)）。

## 二、哪些企业在招 MCP 人才 / 用 MCP（本期具体主体）

| 主体 | 事实 | 对位 |
| --- | --- | --- |
| **外企德科（上海，11-20）** | AI 大模型应用开发 **2.5–5 万·16薪**：逐字「**MCP + Skills：企业级 MCP/Skill 建设**」+ Sandbox 安全隔离 + 熟悉沙箱隔离/代码安全扫描/企业数据安全治理加分 | 高薪 + 本期执行环境面双命中 |
| **广州自我游网络科技（天河，11-17）** | AI 智能体开发工程师 12-22k：逐字「Spring Boot + **Spring AI** + RAG + 工具调用 + **MCP Server** + 多 Agent 工程化落地」+ 沉淀 MCP Server/Agent Skills 资产 | 用户 Java+Spring+AI 技术栈精准命中 |
| **上海凯世通半导体（浦东，应届）** | AI 工程师 10-18k·14薪：封装离子注入机 SECS/GEM/MES 接口为**标准 MCP 工具**、「使 Agent 能安全调用」 | 工业设备 MCP 封装 + 安全调用 |
| **Tidewell（美远程，EST/CST）** | Java/SpringBoot/Anthropic Claude Developer，8+ 年：Spring AI + Claude + **Claude Code/MCP 优化工程流程** + TDD/CI-CD | 海外 Java+Spring+MCP 远程锚 |
| **美团（AI Agent 开发）** | 逐字「Harness Engineering、**MCP**、Agent Loop、多 Agent 协同工程化，能设计评测基准、灰度发布、**安全管控与可观测性体系**」 | 大厂把安全管控 + 可观测写进 JD |

## 三、哪些项目在招标 / 什么价

| 项目 | 金额/截止 | MCP 关联 | 可投性 |
| --- | --- | --- | --- |
| 湖北工程学院数字经济教学实验室（智能体应用算力平台） | **120 万**，11-30 09:00 响应，不接受联合体/分包 | 智能体应用与数据治理一体化平台（公告未点名 MCP） | ⛔ 未点名 MCP，**不计入可投清单**，仅作高校智能体预算锚 |
| 广发四份征集（13321/13322/13327） | 13321 11-13 截 / 13322 11-15 截 / 13327 官方正文无截止须核实 | 五件套 + MCP 安全工具管理 + MCP 封装接入 | 预征集≠采购邀请；7 项资质不满足 → **只能联合体技术分包** |
| 中原银行企业级 AI 网关 | 服务期 14 个月，1 家中标 | **AI 网关 + MCP 代理**写进正式公开招标 | 金融 MCP 网关主标位（本期核心备标） |
| 工信部电子五所 | 173.53 万，12-03 截，广州，面向中小企业 | 未点名 MCP | ⛔ 不计入可投清单，广州本地联合体可考虑 |
| 中央民大 3712 万（11-25）/ 呼市 900 万（11-23） | 倒计时 | 含智能体应用场景 | 分包与案例背书窗口 |

## 四、什么价 —— 接单定价锚（本期延续 + 新源）

| 来源 | 价格 | 口径 |
| --- | --- | --- |
| **SnipeWork Upwork Market Index（11-16~11-22）** | 本周扫描 **17,914** 个 job，平均固定预算 **$832**、平均时薪 **$24/hr** | 官方 API 周度指数，公开可引用 |
| **Upwork 官方（Q3 2026）** | MCP server WAU 自 8 月上线 **涨 20 倍**、近 **5000 万**请求；客户数 −10%、take rate 19.8% | 平台方数据：MCP 通道爆发但买方池收缩 → 报价按固定价而非时薪 |
| **DEV Community scope 指南（本期新）** | 单系统 1–2 周 / 多系统+auth+远程 2–4 周 / 多租户+合规 4–8 周 | 按交付物定价的直接依据 |
| **外企德科（上海，11-20）** | AI 大模型应用 2.5–5 万·16薪 | 国内高薪锚（含 MCP+Skills+Sandbox） |
| **WebsNexus / Innovation Insight** | $299 起（单系统只读）/ $899 起（多系统写+OAuth+审计）；mid $3.6–4.8K/月、senior $4.8–6.6K/月 | 公开挂牌 fixed quote / 全包月费 |
| 历史定价锚（V1.67） | 生产级 $80K–220K；企业网关 $250K 起；Dawloom 7 档 $3,700–$72,000（19 倍价差） | 「涨的不是代码量，是治理层」 |

→ **报价策略**：按交付物 / 集成 / 修订轮次 / 工时拆（OpenAI×Upwork 固定价预算 +52.4%、时薪仅 +1.5%）；10 倍定律对位 21 模块治理层。

## 五、用户的 Java + Spring + AI 组合在该赛道的卖点（8 条）

1. **存量 Java 权限复用**：企业已有 Spring Security / RBAC / 数据归属体系，本框架在 MCP 调用点复用，不需重建身份体系——正对 Tidewell「8+ 年 Java/Spring」与美团「安全管控与可观测性体系」。
2. **确定性执行门**：相对「提示词护栏 100% 失守 / in-prompt 100% 被打穿」，模型外确定性门是可验收边界（九段式）。
3. **执行环境隔离面首发能力（本期）**：CP1–CP6 对位 MCPHub 79748（9.9）/ Hermes p22p / Gortex 62964 / Python SDK 52869，是同质化 MCP server 里唯一把「沙箱反向穿透 + 读写路径同查 + 会话 subject 绑定」做成可演示样品的。
4. **私有化合规工件**：等保/密评/备案 + 审计留痕 ≥180 天 + 字段脱敏 + 第三方 server 沙箱化，Python 系弱项、Java 主场（「不能上云的钱最好赚」）。
5. **Spring AI Alibaba 兼容层**：用户主技术栈，广州自我游 JD 逐字「Spring AI + MCP Server」，核心基于官方 SDK、Alibaba 可选不进主 reactor。
6. **九段式可演示样品**：`examples/` 下 11 组纯 JDK 守卫，javac + main 自检全绿，售前可现场跑、进标书可当「可演示案例」。
7. **定价权锚**：治理层 10 倍价差 + DEV scope 指南 1–8 周分档 + WebsNexus $299/$899 + 生产级 $80K–220K，把「写个 server」抬到「交付治理层」。
8. **AAIF 规格变更持续复验**：MCP/Agent Skills 归 Linux Foundation 中立治理，规格变更无厂商补丁通知——本框架把「规格版本当受监控依赖 + 变更后全量复验」做成年度托管服务。

## 六、明天候选

1. 发 SEO 稿抢「MCPHub 79748 / Hermes 沙箱穿透 / Gortex 写路径 / Python SDK 52869」流量。
2. 12 项执行环境面验收清单 + 26 断言输出压 1 页 PDF，交广州本地联合体主体。
3. 评估把 CP2 / CP3 / CP4 / CP5 接进 `mcp-gateway`（独立分支先跑通 26 断言）。
4. 备标中原银行 AI 网关；回广发平台核实 13327 报名截止。
5. 跨期待办：`exportPrometheus()` 挂 actuator、dataClassification 审批引擎、重定向逐跳 SNI 校验、独立分支评估 Spring AI 2.0 升级。

## 七、来源清单（18 项）

见正文各段超链接，覆盖：DugganUSA、MindPattern（2 篇）、eyeon.ai、The Agent Times、智联招聘、猎聘（2 条）、Tidewell、美团招聘、DEV Community、SnipeWork、Upwork 官方、beingguru、WebsNexus、Innovation Insight、广发智慧数平台、湖北工程学院采购公告。
