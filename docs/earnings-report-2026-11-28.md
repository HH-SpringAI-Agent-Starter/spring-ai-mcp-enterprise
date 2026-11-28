# 财富机会雷达 · 2026-11-28（V1.82）

> 检索窗口：近 3 天为主（11-26 ~ 11-28），价格/挂牌锚放宽到 7 天并标注口径。全部为公开信息，报价为挂牌价非成交价；未点名 MCP 的标不计入可投清单。

## 一、六个市场信号

| # | 信号 | 事实 | 对我方的含义 |
| --- | --- | --- | --- |
| S1 | **官方 SDK 自己出跨客户端串线洞** | CVE-2026-111770（`typescript-sdk` @modelcontextprotocol/server 2.0.0–2.3.0，CVSS 7.7，NVD 11-05 / 阿里云 AVD 11-22）：共享无状态 transport 时仅按 JSON-RPC id 关联、客户端 id 从 0 开始必然碰撞 → 响应/通知投错客户端；修 2.3.0 且**升级会破坏复用实例须改代码**。 | 买方必问从「有没有鉴权」升级到「几个客户端共用实例、消息按什么归属、升级要不要改代码」；这是可验收执行门的新卖点 |
| S2 | **一天 8 个同源 MCP CVE** | VulnCheck 11-27 由同一研究者发布 8 个 MCP CVE（Crypto APIs 14 包 / bigcommerce / Wazuh / bagel / WhoDB / mcp-video-analyzer / pdf-reader-mcp）：网络绑定缺鉴权 + SSRF + SQL 闸门漏写文件语句。 | 从单品漏洞升级为**整类工程模式问题**；成套执行门（绑定/路由/管理端点/凭证）比单点补丁更有价值 |
| S3 | **管理面与主面鉴权分离成硬要求** | Crypto APIs 138026（11-27）：HTTP 模式 0.0.0.0 无鉴权、运营者 key 被任意调用方消费，须轮换 key；Jupyter 77318/77359（11-19）：`/mcp` 有 token 但 `/api/connect` `/api/stop` 管理路由无鉴权、DNS rebinding 劫持。 | 「绑定位置不是授权、数据面 token ≠ 管理面 token」进入买方 checklist |
| S4 | **网关正式商品化、价格公开** | **Azure AI Gateway 11-28 GA**（独立服务、17 区域）：每网关每月 1 万免费请求，2027-01-01 起 **$500/网关/月含 1000 万请求 + $20/百万超量**；调用方 key 换成 managed identity 转发、按 user/team/agent 归属成本。实测者指出「接了网关不阻止直连上游，须私有端点+RBAC」。 | 「帮你管/帮你审」有了明确对标价；我方三档报价可锚在网关价之上、以执行门差异化 |
| S5 | **金融合规继续把 MCP 变付费项** | Archive Intel 11-25 上线 MCP Server（RIA/券商归档通信，WORM 只读 + 服务端 RBAC + 逐条引用 + 全量审计），定价 **$100/月或 $1200/年/机构**，年合同 ≥$12K 免费包含。 | 又一个「读路径也要按调用方隔离 + 可审计」的付费样本；与 Duco 11-25 GA 同一方向 |
| S6 | **生态规模与招聘双升** | Skillful.sh 11-28 生态报告：追踪 **702,822 tools**，其中 MCP server **248,930（35.4%）**，近 30 天 +69,174（约 2,306/天）；阿里云智能招 Agentic Tools 开发专家（OpenAPI MCP Server 接入 2W+ API）。 | 供给爆炸 → 治理/隔离/可审计成为稀缺能力；大厂自建 MCP 基础设施，分包与咨询空间在「管」不在「建」 |

## 二、招标 / 采购存量（未点名 MCP 不计入可投清单）

| 项目 | 金额 | 状态 | 判定 |
| --- | --- | --- | --- |
| 中科院大连化物所智能体训练与部署服务器（HWZB2026-1106） | **270 万**（A 包 120 / B 包 150） | 11-27 公告，12-21 开标 | ⛔ 货物类服务器采购，未点名 MCP，不计入可投清单 |
| 天津渤海职院化工类专业智能体建设（TGPC-2026-A-0517） | **190 万** | 11-26 公告，12-18 开标 | ⛔ 未点名 MCP，不计入 |
| 太原理工 AI 赋能学生综合服务平台（1499002026CGK04429） | **200 万** | 11-27 公告，12-19 开标 | ⛔ 面向中小企业、未点名 MCP，不计入 |
| 眉山市彭山区中医医院科研智能体平台 | **4.98 万**（Token 计价） | 11-27 公告，12-02 截标 | ⛔ 不接受联合体/分包，未点名 MCP，不计入 |
| 广发智慧数平台 MCP & Skills | — | 已 11-13 正式生产上线 | ⛔ 买方自建完成；13321/13322 已截，13327 正文无截止须回平台核实 |
| 工信部五所 / 中原银行 AI 网关 + MCP 代理 | — | 12-03 备标 / 跟踪中 | ⏳ 待正式文件核实 |

## 三、招聘（近 3 天，含逐字 JD 关键词）

| 企业 / 岗位 | 薪资 | 关键逐字 | 匹配度 |
| --- | --- | --- | --- |
| **中邮消费金融 · 架构开发岗（AI方向）**（广州天河，11-19 更新） | **25–35k·16薪** | 「**MCP Server 生态构建与工具集成**」「多模型网关」「**运行时 Sandbox 安全隔离机制**」「Prompt 注入防护、数据脱敏、权限控制」+ Java/Python + 金融行业 AI 项目 | ⭐⭐ 本期最精准：MCP 生态+网关+Sandbox+权限，与本期「共享传输/隔离面」逐字命中 |
| **猎聘 · Senior Software Engineer (JAVA API)**（上海，11-27 更新） | **22–26k·13薪** | 「TrueFoundry AI Gateway 与 **MCP Gateway**」「API key 生命周期/轮换/撤销/scoped access」「**Never expose credentials in logs**」「MCP source onboarding：注册、发现、鉴权边界、生命周期」「guardrails & policy」 | ⭐⭐ 极精准：API key 隔离 + 凭证不入日志 + MCP 注册鉴权边界，与 CP5/CP6 一一对应 |
| 阿里云智能 · Agentic Tools 开发专家（SDK 团队，北京/杭州） | 面议（大厂 P 序列） | 「**OpenAPI MCP Server** 接入 2W+ API」「Core 模式统一接入与规模化治理」「多语言 SDK 生成引擎」 | ⭐ 大厂平台岗：强调「规模化治理」，与执行门方向一致 |
| 小米科技 · 高级AI系统开发工程师 A18742（武汉洪山，11-26） | **25–30k** | 「Agent 工作流编排框架，支持**工具调用（MCP协议）**、任务规划」+「Java/Spring/SpringBoot」+「高并发分布式」 | ⭐ 持续在招（11-26 更新），Java+Spring+MCP+Agent 编排逐字齐全 |
| 河南兜圈子 · AI 应用开发工程师全栈（校招，11-26） | 8–10k | 「业务 Agent、**MCP 工具与 Skills 体系**」「Java/Python/Go」「Claude Code/Codex/Cursor」 | 中：校招价，Skills 体系关键词值得盯 |

## 四、价格锚（挂牌价，非成交价）

| 项 | 数值 | 来源/口径 |
| --- | --- | --- |
| **Azure AI Gateway GA**（11-28） | 每网关 **1 万免费请求/月**；2027-01-01 起 **$500/月含 1000 万请求 + $20/百万超量** | 官方 GA 公告，17 区域 |
| **Archive Intel MCP Server**（11-25） | **$100/月 或 $1200/年/机构**；年合同 ≥$12K 免费包含 | 官方新闻稿，11-25 |
| MCP 网关订阅（对照） | NS Pros Free/$9/$24/$49 月；Glama $9/$26/$80；Composio Pro $29 | dev.to 11-23 实测对比 |
| MCP 工具按次计费（对照） | APIbase **$0.0005–$29.99/次**（USDC、免账户）；NS Pros 订阅+BYOK 无单次加价 | dev.to 11-23 实测 |
| Upwork 自由职业者服务费 | **0–15%**（按合同、投标前显示） | 多源复核，旧 10/20% 口径弃用 |
| 生产级 MCP Server 建置 | L1 只读 **$100K–300K** / L2 带动作 **$300K–700K** / Agent 驻留 **$1M+** | Launch Day Advisors（判断口径，非调查） |
| L2 安全分项 | OAuth $45–100K / 审计日志 $30–70K / 第三方渗透 $30K / 首次 SOC2 Type II $50–150K | 同上 |
| 运维 | **$5K–25K/月** + ~$20K/年 hosting | 同上 |
| 工资对照 | 中邮消费金融 25–35k·16薪 / 猎聘 JAVA API 22–26k·13薪 / 小米 25–30k | 11-19~11-27 JD |

## 五、用户「Java + Spring + AI」在该赛道的 8 条卖点

1. **跨客户端串线可现场证伪**：共享实例被第二个客户端复用、id 从 0 碰撞、会话归属缺失三类攻击可现场复现并被拦；32 断言全绿、旧基线漏 17/20。
2. **官方 SDK 洞也能讲清**：CVE-2026-111770 是官方 TypeScript SDK，须按依赖语言区分；本框架核心依赖官方 Java SDK，给出「升级 + 消除共享实例 + 复验」完整话术，不混称、不夸大。
3. **不靠声明自证**：claimed scope、readOnlyHint、绑定地址一律只进审计不进允许分支，对齐 11-27 VulnCheck 一批 8 CVE 的「工程模式问题」判词。
4. **调用方凭证隔离**：credential owner == caller、网关侧换成 managed identity、成本按 user/team/agent 归属，对位猎聘 JAVA API JD 的「Never expose credentials in logs」逐字要求。
5. **私有化合规工件**：RBAC/RateLimit/审计/API Key/注册中心/REST API 已成型，适合金融与工业软件私有部署，中邮消费金融 JD 的 Sandbox/权限控制方向可直接应答。
6. **十四段式攻击面清单**：注解→注册中心→只读→决策→配置→结果→身份→会话→执行环境→出站→日志→记忆→审批链→**共享传输/隔离面**，48→50 类，可按买方行业裁剪成 POC 脚本。
7. **价格结构而非抬时薪**：Azure 网关 $500/月、Archive Intel $100/月给出对标地板；我方按「审计包 / 集成加固 / 运维」拆分交付物报价，对位 L1 $15–30K → L2 $80–220K → L3 $300–700K。
8. **精准 JD 逐字命中**：中邮消费金融 25–35k·16薪的「MCP Server 生态构建 + 多模型网关 + Sandbox 隔离 + 权限控制」、猎聘 22–26k·13薪的「MCP Gateway + API key 生命周期 + 凭证不入日志」与本期能力一一对应，可直接写逐条应答。

## 六、明天动作

1. 12 项共享传输清单 + 32 断言 + 旧基线对照压 1 页 PDF，对位中邮消费金融 25-35k·16薪 JD 与猎聘 JAVA API 22-26k·13薪逐条写应答。
2. 独立分支把 CP1 实例工厂 / CP3 管理路由独立鉴权 / CP5 凭证隔离接进 `mcp-gateway`（先跑通 32 断言）。
3. 核实官方 MCP Java SDK 是否已存在同类 transport 共享问题（TypeScript 洞不可直接外推到 Java，须按依赖语言区分、单独披露版本）。
4. 发 SEO 稿抢「CVE-2026-111770 跨客户端串线 / Crypto APIs 138026 0.0.0.0 无鉴权 / 一天 8 个 MCP CVE / Azure AI Gateway GA 价格」长尾流量。
5. 跨期待办：`exportPrometheus()` 挂 actuator、dataClassification 审批引擎、重定向逐跳 SNI 校验、Spring AI 2.0 评估。

## 七、来源

CVE-2026-111770（NVD 11-05 / 阿里云 AVD 11-22 / kitploit / cvefeed / verisq，CVSS 7.7、CWE-362）、CVE-2026-138026（theinjection.dev 11-27 引 VulnCheck + Crypto APIs 官方 advisory + installplango 11-28）、11-27 同批 8 CVE（installplango / verno index）、CVE-2026-77318/77359（verno index 11-19）、Azure AI Gateway GA（lavx news 11-28 引微软官方）、Archive Intel MCP Server（gateex.org 11-25）、Skillful.sh 生态报告 11-28、中科院大连化物所/天津渤海职院/太原理工/眉山彭山中医院招标公告（ccgp.gov.cn 等 11-26~27）、中邮消费金融 JD（猎聘 11-19）、Senior Software Engineer JAVA API JD（猎聘 11-27）、阿里云 Agentic Tools JD（careers.aliyun.com）、小米 A18742 JD（今日招聘网 11-26）、dev.to MCP 计费对比 11-23、Launch Day Advisors MCP cost guide。
