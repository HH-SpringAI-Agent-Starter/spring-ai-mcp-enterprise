# 财富机会雷达 · MCP 企业化挣钱情报（2026-11-13）

> 采集窗口 **2026-11-11 ~ 11-13**（近 3 天），Task 6 全量：**招标 / 招聘 / 价格 / 卖点 / 来源**。
> 口径延续：只计「明确点名 MCP / MCP Server / 智能体平台且需 MCP 化」的机会；未点名的
> 通用 AI 大单一律标注「非 MCP 直接机会」，不计入可投清单。全部报价为**公开挂牌/销售页价**，
> 非成交价；本项目**无商业合同 / 无中标 / 无金融行业案例 / 无等保测评**。

---

## 0. 今日一句话（最高信号）

**「read_only 是声明，不是边界」被一个产品 4 天 7 个 CVE 钉死，而同一周最大的
金融机构（嘉信理财 $13.3 万亿）刚把 MCP Server 从只读推进到可下单——但每一笔
交易仍强制人工确认。** 治理层的卖点从「我有 RBAC」升级为「我能在**执行层**证明只读
是真的只读」；买方语言同步变成 Swift Media 11-13 周报那句：
**「MCP 网关成为采购清单必选项」**。这是本项目 21 模块 + 28 类攻击面 + 纯 JDK
可演示示例最值钱的一次外部背书。

三段式递进（V1.69 → V1.70 → V1.71）：

| 版本 | 判词 | 被证伪的「信任」 |
| --- | --- | --- |
| V1.69 | 注解不是安全边界 | 工具元数据（hint / destructiveHint） |
| V1.70 | 注册中心自己也不是安全边界 | 治理层自己的管理面 |
| **V1.71** | **只读声明也不是安全边界** | **连接标签 / read_only / 首关键字分类** |

---

## 1. 技术面：11-10~11-12 的 12 条披露（本期护城河来源）

来源：[FixTheVuln AI IDE & MCP 披露追踪](https://fixthevuln.com/ai-ide-mcp-disclosures.html)
（截至 11-13 共 **187** 条：Critical 20 / High 94 / Medium 61 / Low 12；本窗口 12 条，
High 3 / Medium 6 / Low 3）。

### 1.1 pgEdge Postgres MCP Server —— 7 个 CVE，一个根因

| CVE | 发布 | CVSS | 一句话根因 | 对应执行层控制点 |
| --- | --- | --- | --- | --- |
| `CVE-2026-126196` | 11-12 | **8.1 High** | `query_database` 在 read_only 连接上把未识别语句经 `pgx Exec` 以**简单查询协议**发出，可分号堆叠多语句 + 事务/会话状态改写，池化会话残留让后续调用变可写 | CP1 堆叠 / CP2 绑定+只读事务 / CP3 会话复位 |
| `CVE-2026-126197` | 11-12 | **8.8 High** | custom `pl-do` 工具把 JSON 参数插值进固定 dollar-quote 标签分隔的 `DO` 块，`$mcp_custom_tool$` / `$mcp_args$` 可提前闭合并注入任意 SQL / plpgsql | CP1 dollar-quote 逃逸 |
| `CVE-2026-126199` | 11-12 | 6.8 Med | bundled CLI/web client **只按语句首个关键字**分类就决定安全策略 | CP1 词法归一化全文扫描 |
| `CVE-2026-126198` | 11-12 | 3.1 Low | 写确认依赖可被绕过的浅层语句检查 | CP1 + 审批门 |
| `CVE-2026-125322` | 11-10 | 6.5 Med | `count_rows` 的 `where` 参数接受针对**声明表以外**的子查询 | CP2 where 作用域 |
| `CVE-2026-125321` | 11-10 | 6.5 Med | 启用认证的 HTTP transport 取**最左调用方可控值**（XFF 最左项）派生身份 | CP4 身份来源 |
| `CVE-2026-125320` | 11-10 | 5.3 Med | HTTP transport 不校验 `Origin`，网页可 DNS rebinding 读库 | CP4 Origin |

> 官方修复：统一在 **1.1.0**。参考 [NVD 126196](https://nvd.nist.gov/vuln/detail/CVE-2026-126196)、
> [126197](https://nvd.nist.gov/vuln/detail/CVE-2026-126197)。
> **关键判词**：7 个洞没有一个靠「把 read_only 标签贴得更牢」修好，全部靠**执行层**
> 补控制点。这与 V1.67 `VACUUM INTO` 击穿 SELECT 前缀是同一失效模式，只是这次
> 一个产品一次暴露 7 次 ⇒ 可写进标书当「为什么需要独立执行层」的实证。

### 1.2 官方 `modelcontextprotocol/servers` —— 5 个 GHSA（11-11）

| GHSA | 分数 | 根因 | 控制点 |
| --- | --- | --- | --- |
| `GHSA-6hqx-gqrx-c5r2` | **7.8 High** | `mcp-server-git` **只读工具**处理仓库时信任仓库自身 Git 配置，`core.fsmonitor` 触发任意命令执行 | CP5 执行环境净化 |
| `GHSA-q74p-4wrc-q7w6` | 5.3 Med | 上一轮 `validate_opened_repo()` **修复不完整**，`objects/info/alternates` + linked-worktree `common_dir` 混淆绕过 `--repository` | CP1 修复完整性复验 |
| `GHSA-p9cv-8g75-4xcg` | 4.4 Med | 验证后才替换 symlink（TOCTOU），`read_text_file` 返回允许目录外文件 | CP6 校验/执行同源 |
| `GHSA-f9hh-cmx9-2f23` | 3.1 Low | `robots.txt` 路径规范化与实际抓取路径不一致，`//private` 绕过 `Disallow: /private` | CP6 路径同源 |
| `GHSA-3rxj-6jhw-qfv7` | 3.1 Low | SSRF 分类器把 IPv6 site-local `fec0::/10` 误判 public（CWE-918） | CP6 IP 分类完整性 |

> 补充（窗口外、同源）：`GHSA-w766-5x43-whvw`（10-28 nested gitdir）、
> `GHSA-v38p-wf49-hx2j`（10-25 IDNA 解析不一致）、`GHSA-66qx-2266-pmj6`（10-25
> NAT64 `64:ff9b::/96` 误判 public）、`GHSA-3jqg-r5pp-rmp2`（10-25 重定向不复核 robots）。
> **「修复不完整」再次出现** ⇒ 与 V1.70「打补丁 ≠ 清干净」形成连续证据链。

### 1.3 参考实现（本期已交付，可现场跑）

🆕 [`examples/read-only-enforcement/`](../examples/read-only-enforcement/README.md)：
`McpReadOnlyEnforcementGate.java` 纯 JDK 零依赖，6 控制点、声明层完全不参与授权，
`javac -encoding UTF-8` 编译 + `main()` 自检
**`22 attack + 9 control + 8 boundary = 79 断言`全绿，其中 20 条为旧基线漏掉的增量拦截**。

---

## 2. 挣钱 · 招标与采购（可投清单）

### 2.1 广发证券四份征集（持续跟踪，⭐ 本期核实进展）

| 编号 | 项目 | 截止 | MCP 相关要求 | 状态 |
| --- | --- | --- | --- | --- |
| 13321 | [智能金融资讯数据服务平台](https://gfjc.gf.com.cn/gyszjgg/13321.jhtml) | **11-13 17:30（今日已截止）** | MCP 工具协议 + Skill + 子Agent + 长期/会话记忆 + 混合检索；能力可外部产品化 | 窗口关闭，转跟踪正式招标 |
| 13322 | [2026 企业微信持续建设](https://www.gf.com.cn/web/article/detail?columnId=4&articleId=01a1a56c47c77f2cabc5083dbccf44eb) | **11-15 17:30（剩 2 天）** | Agent+Skill+**MCP 五件套**（工具定义/参数校验/鉴权限权/限流/可观测）写进**供应商资质**；指定 Dify/hiagent；数据按客户经理归属隔离；日志不可篡改；广州本地团队 | ⭐ 最紧窗口 |
| 13327 | [2026 面向稳定性保障的 AI 智能运维](http://gfjc.gf.com.cn/gyszjgg/13327.jhtml) | **⚠️ 正文未呈现截止** | 目标第 7 条逐字「推进可观测、自动化、日志等关联平台的 **MCP 封装接入**」；另有模型范围/分阶段调用/输入输出敏感信息分级自动拦截 | 待回采购平台核实 |
| 1332x | AI 安全数据底座 / MCP 安全工具管理 | 11-12 17:30（历史） | MCP 安全工具管理 + 安全技能库 | 已过期 |

> ⚠️ **兑现 V1.70 待办①**：本次已回读 13327 官方原文，确认了 7 条目标与资质清单，但
> **公告正文确实没有报名截止时间**（采购平台页面结构与 13321/13322 不同）。
> 结论：**必须电话/邮件向广发采购平台核实**，不得凭编号序列推断。
> 资质硬门槛（13327）：独立法人 / 成立 3 年以上 / 注册资金 ≥ 500 万 / 原厂或授权代理 /
> **3 年以上运维平台代理销售经验 + ≥ 2 个运维平台成功案例** / 广州本地服务团队 / 完税凭证。
> **本项目 7 项全不满足 → 只能作为联合体技术分包**，与 V1.70 结论一致。

### 2.2 本期新增（近 3 天）

| 项目 | 金额 | 时间 | MCP 相关度 | 判读 |
| --- | --- | --- | --- | --- |
| [工信部电子五所 · 智能体开发管理平台建设](https://www.365trade.com.cn/zfwzb/896160.jhtml) | **173.53 万**（含税最高限价） | 11-13 公告，**12-03 09:30 截标** | 智能体开发管理平台（未逐字点名 MCP） | ⚠️ **邻近赛道**，专门面向中小企业、**不接受联合体**、广州、2 个月交付。可作为「治理层能力」素材，但**不计入 MCP 直接机会** |
| [成都市中西医结合医院 · AI 智能体平台建设（更正二次）](https://www.ccgp.gov.cn/cggg/dfgg/gzgg/202611/t20261111_27659980.htm) | 未披露 | 11-11 更正：投标截止由 11-13 **延至 11-27 09:30** | AI 智能体平台（未逐字点名 MCP） | ⚠️ 邻近赛道；延期本身说明采购方在补需求，可跟踪更正函附件 |
| 兴业银行 智能体平台一期（软件许可买断 + 研发实施） | 未披露 | 10-21 公告，持续 | 主流大模型 / 灵活插件集成与调用 / 知识库 / 可视化编排 | 底座期，跟踪正式招标文件 |
| 中信银行「一基八体」 | — | 三季报 | 43 个智能知识应用场景，按业务条线铺开 | 非招标，买方意向信号 |
| 建行浙江省分行 数币营销服务 | 900 万 | 11-25 开标 | ❌ 与 MCP 无关 | **非 MCP 直接机会，不计入** |

> 存量倒计时（延续口径，本期未新增官方信息）：军队大模型安全防护 190 万 **11-16** /
> 中山大学 399 万 **11-17** / 山东重工集团级平台二次标 **11-20** /
> 呼市 900 万 **11-23** / 中央民大 3712 万 **11-25**。

### 2.3 买方语言的转折（⭐ 本期最有商务价值）

[Swift Media · AI headlines for business this week (Nov 13, 2026)](https://swiftmedia.ca/blog/ai-headlines-for-business-november-13-2026)
本周采购对话发生了位移：

> **「MCP gateways become a procurement requirement」** — 企业买家把 MCP 网关写进
> Agent RFP：**集中式工具注册表 / 按 agent 的 scope / 对检索内容做提示注入过滤 /
> 可导出的审计日志**。Vendors position gateways as the control plane between chat UI
> and production APIs.

同周另外四条同期进入合同的治理条款：`模型停用通知期 30–90 天 + 回归测试权`、
`Agent FinOps 按业务单元分摊 token 成本`、`保险商询问哪些动作无需人工批准 / kill switch`
、`联邦 agent 身份（一 agent 一 client ID + 短时令牌 + human sponsor + 离职即禁用）`。

**对位**：这四项几乎逐条命中本项目 `mcp-gateway` / `mcp-registry` / `mcp-governance` /
`mcp-monitor` / `mcp-tenant` / `mcp-auth`。建议把「买方必问 5 问」直接改成 RFP 应答模板。

---

## 3. 挣钱 · 谁在招 MCP 人才（近 3 天 + 本期新增）

| 企业 / 平台 | 岗位 | 地点 / 时间 | 明确要求 | 价格 |
| --- | --- | --- | --- | --- |
| **海康威视** | [机器人·高级应用软件开发工程师 JAVA](https://talent.hikvision.com/home/socity/position?postId=2350F2E18D457BE3987BA4AD9EFAD237) | 西安 / 11-06，5 年+ | ⭐ **「MCP Server 与 Skills 设计开发，将业务 API/数据库/知识库封装为工具，并设计权限控制、安全护栏及高风险操作的人工确认(HITL)」**；**熟悉 Spring AI / LangChain4j / Spring AI Alibaba**；RAG + Agent 评测可观测 | 未披露 |
| **法本信息**（深圳，万人级，金融/通信客户） | Java（AI 工程化方向） | 杭州，5-10 年 | ⭐ **「必须深刻理解并熟悉 MCP 协议，具备基于 Java 扩展和开发 MCP Server」**；SpringAI / SpringCloud / 支付系统 | 未披露 |
| **智慧树**（港股 02687.HK） | 高级 Java 开发 | 上海，3-5 年 | **基于 Spring AI 构建 Text-to-SQL、RAG、MCP Server**；多数据源/语义模型 | 未披露 |
| **北京中软环球** | 云原生后端开发工程师 | 北京石景山，5-10 年 | Java SpringBoot + 大模型网关管理后台 + **开发 MCP 系列服务，适配内网 AI 编码工具**；Docker/K8s/Harbor/MinIO | 未披露 |
| **International Logic Systems (ILS)** | [AI Engineer – LLMs, Agentic Workflows & MCP](https://jobsearcher.com/jobs/86db907a4cfe2a593061f30b/ai-engineer-llms-agentic-workflows-mcp-at-international-logic-systems-in-benton-twp-mi) | Fairfax VA / 11-04，5-9 年，混合 | **Java (Spring Boot) + Spring AI + MCP integrations + RAG + 监管行业经验** | 未披露 |
| **Upwork 真实单** | [AI Engineer, Knowledge & Retrieval Systems](https://www.upwork.com/freelance-jobs/apply/Engineer-Knowledge-Retrieval-Systems_~022119160413600550735/) | 全球 / **11-12 发布** | **通过 API 与 MCP 把 agent 连到知识库**；RAG / 评测框架 / 多租户 / 访问控制；MCP servers 为 nice-to-have | **$500 固定价**（Intermediate，20–50 proposals，8 面试中，6 邀请） |
| **OnePlan** | [November 2026 Release](https://oneplan.ai/oneplan-november-2026-release)（MCP Server preview） | 11-12 发布 | 「Every interaction **respects existing OnePlan permissions**；users can see and change only what they are authorized to」+ 「**reviewing changes before saving**」 | 产品发布，非招聘 |

### 3.1 薪资锚（本期新增权威数据）

[eSEOspace · MCP Engineer Salary and Jobs in 2026](https://eseospace.com/blog/mcp-engineer-salary-and-jobs)
（数据核于 2026-11-02）：

| 指标 | 数值 | 口径 |
| --- | --- | --- |
| 美国 BLS 软件开发者中位年薪 | **$135,980**（2025-05） | 官方大类，**不是 MCP 溢价** |
| 英国 ITJobsWatch 半年内提及 MCP 的永久岗 | **123 个**（去年同期 **11 个**，≈ 11 倍） | 6 个月至 11-02 |
| 英国 MCP 岗位挂牌中位薪资 | **£85,855**（63 个挂牌报价）；25 分位 £72,500 / 75 分位 £110,000 | 小样本，**谨慎** |
| 英国非伦敦中位 | £82,500 | 同上 |
| 岗位真实名称 | AI / Platform / Integrations Engineer，**「MCP engineer」极少作为独立 title** | 重要选品口径 |

> 结论：**MCP 是技能不是岗位**。简历/提案要写「Java 平台工程 + AI Agent 治理」，
> 而不是「MCP 工程师」——这与本项目「治理层是独立控制平面」的定位完全一致。
> ⚠️ 该来源明确警告：小样本 + 关键词混淆（MCP 是多义缩写），**不可当成 MCP 薪资溢价**。

---

## 4. 挣钱 · 价格与接单口径

| 项 | 数值 | 来源 / 口径 |
| --- | --- | --- |
| Upwork 时薪分层 | 入门 **$10–25/h**；中级 **$25–75/h**；高级开发/AI/战略咨询 **$75–150+/h** | [Upwork 官方 2026 时薪指南](https://www.upwork.com/guides/hourly-rates)（公开挂牌价，非成交） |
| 平台费率（三源复核） | **Upwork 0–15%**（签约时锁定）/ Fiverr **20%** / Freelancer **10% 或 $5** | [独立开发者横评](https://zhangdapeng.dev/p/fiverr-upwork-freelancer-for-indie-hackers)；延续 V1.68 口径 |
| 报价公式 | `Phase1 = 工时 × 底线时薪 × 1.25 ÷ (1 − 费率)`；里程碑须先 **Funded** 再开工 | [Digital Solo Hub](https://digitalsolohub.com/ai-automation-jobs-on-upwork) |
| 真实单 | **$500 固定价** MCP+RAG（11-12）；历史 $1,500 单（V1.68） | 见上表 |
| 项目级报价锚（历史） | 生产级含 audit/SSO/RBAC/versioning **$80K–$220K**；企业网关 **$250K 起**；Dawloom 7 档 **$3,700–$72,000**（19 倍价差） | `docs/mcp-governance-pricing-anchor-2026-11-09.md` |
| Upwork 基础设施 | 官方 MCP Server（2026-08-10）+ **Upwork Now 公测（11-02）**；proposal 保持草稿直到人工确认（draft-and-confirm）；5 分钟无响应会话结束 | 可直接用于选品与 HITL 话术 |

> **反直觉但最值钱的接单话术**（Digital Solo Hub 原文）：
> **「don't promise a fully autonomous system. Saying where a human approves a step
> makes you sound more trustworthy.」** —— 与嘉信理财「每笔交易强制人工确认」、
> T-Investments「法律责任转移」、本项目审批门完全同构。**卖 HITL 比卖全自动更好卖。**

---

## 5. 生态与竞争面（决定卖点怎么讲）

### 5.1 CSDN 11-12 万字长文：免费 SEO 顺风

[《企业落地智能体，MCP 统一了连接，治理真空却同步扩大》](https://blog.csdn.net/summerliyang/article/details/170562593)
（原创，11-12 09:05）。核心判词与本项目话术几乎逐字一致：

> **「MCP 赢下的是『连接』这一层，输掉的是『治理』这一层」**
> **「企业把 MCP 当接口，忘了它是传输」**（Forrester：MCP 更接近 RPC/消息系统，
> 身份、访问控制、策略执行全都不在里面）
> **「标准赢了，治理输了，中间的差价就是 2026 年剩下两个月所有 MCP 安全创业公司的订单」**
> 收口建议原文：**「先把身份、最小权限、工具级审批、不可篡改日志这四件事做在接入前面」**

可引用量化（该文汇总）：MCP 2024-11-25 开源 → 2025-03-26 OpenAI 采纳 →
2025-12-09 捐给 Linux 基金会 AAIF（成员 49 → 2026-08 **247**）→ 2026-07-28 第五版
规范改**无状态**（移除握手与 `Mcp-Session-Id`、强制 `server/discover`、MRTR、OAuth2.0/OIDC
+ RFC 9207 issuer 校验）；**28% 财富 500 强生产环境在用**；官方注册表 2026-06 快照
**10,856** 个 server；SDK 月下载 2026-03 **9,700 万** → 年中 **4 亿+**；
**Gartner 预测 2026 年底 75% 的 API 网关厂商会带 MCP 能力**。
风险侧：GitGuardian **24,008 个密钥躺在 GitHub MCP 配置**、AI 服务凭据泄露同比 **+81%**、
CSA 七类风险 + 「敏感数据 + 不可信内容 + 对外通信」**致命三重组合**。
收缩侧：微软 2026-10-01 下线 Dynamics 365 ERP 静态 MCP server、Stripe 10-31 起拒无
Agent 标签全权限密钥、Workday MCP 转付费 SKU 按调用计费（**「数据过路费」**）。
⚠️ 该文明确：公开 ROI 数字几乎全线缺位，唯一 98.7% token 削减是厂商自报；
Atlassian 1,500 万次/日调用为**单一来源未证实**（本项目不采信为事实）。

### 5.2 嘉信理财 Schwab 11-12（⭐ 金融级 HITL 活广告）

[RIABiz 11-13](https://riabiz.com/a/2026/11/13/schwab-surprises-analysts-with-extra-rapid-launch-of-own-mcp-servers-also-leaping-from-read-only-to-agentic-get-stuff-done-ai-it-leaves-rias-out-for-now-and-makes-anthropics-days-numbered-as-schwabs-exclusive-llm) /
[CyberSentinel 11-13](https://cybersentinelnews.com/charles-schwab-launches-agentic-mcp-servers-sidestepping-rias)：

- $13.33 万亿托管资产的券商 11-12 上线**自有 MCP Server**，AI（Charley）从
  **read-only 摘要**跃迁到 **agentic 下单**（准备/下单/管理交易）。
- **但每一笔交易仍强制 client confirmation**；脚注风险提示「Charley may provide
  inaccurate or incomplete results，用户须自行核验」。
- CEO Rick Wurster 10-16 才公开强调 AI 代理交易需 **rules of the road / human
  oversight / pre-defined criteria**；此次上线被分析师称「比预期快得多」。
- MCP 作为**中立协议层**打破 Anthropic 独占（9 月签的独家，20 席 $4,800 企业门槛
  把 11,000+ 中小 RIA 挡在门外）→ 未来可插多家 LLM。
- 首批只对零售客户，RIA 暂不含。

**销售用法**：这是「金融机构用 MCP 但坚决不放弃人工确认」的最强第三方实证，
直接支撑广发 13322「日志不可篡改 / 数据按客户经理隔离」与 13327「敏感信息分级
自动拦截」的应答话术，也证明**治理层不是阻碍创新的成本，而是让 agentic 敢上线的条件**。

### 5.3 采购与生态的结构性判断

- 治理层已被三大云 + iPaaS 做成**标准产品层**（V1.66），因此差异化锚点必须换到
  **「跨运行时签字 / 私有化合规工件 / 存量 Java 权限复用 / 执行层可验证性」**——本期
  V1.71 的 79 断言自检就是这个「可验证性」。
- [OnePlan 11-12](https://oneplan.ai/oneplan-november-2026-release) 逐字
  「respects existing permissions」+「reviewing changes before saving」＝
  **复用存量权限 + 写前复核**，与本项目 `mcp-auth` / `mcp-tenant` / 审批门同构。
- 中国路线（CSDN）：平台层支持 MCP、C 端 App 普遍不内置 MCP 客户端；MCPBench
  「简单封装 MCP 并未显著提升工具调用性能」→ **别卖封装，卖治理**。

---

## 6. 用户的 Java + Spring + AI 组合，在这条赛道的卖点（8 条）

| # | 卖点 | 对位证据（本期） | 可演示工件 |
| --- | --- | --- | --- |
| 1 | **Spring AI Alibaba 原生兼容** —— 用户技术栈直接可用，不用换栈 | 海康威视 JD 逐字列 Spring AI / LangChain4j / **Spring AI Alibaba**；法本「基于 Java 扩展开发 MCP Server」 | [`examples/spring-ai-alibaba-mcp-server/`](../examples/spring-ai-alibaba-mcp-server/README.md) |
| 2 | **执行层可验证的只读**（不是标签、不是注解） | pgEdge 7 CVE + 官方 5 GHSA；Swift Media「网关成采购必选项」 | 🆕 [`examples/read-only-enforcement/`](../examples/read-only-enforcement/README.md) **79 断言现场跑** |
| 3 | **21 模块覆盖买方 RFP 五件套** —— 集中注册表 / per-agent scope / 注入过滤 / 可导出审计 | Swift Media 11-13 采购清单；广发 13322 资质五件套 | `mcp-registry` / `mcp-tenant` / `mcp-governance` / `mcp-monitor` / `mcp-auth` |
| 4 | **金融级 HITL 与法律责任分界** | 嘉信理财每笔强制人工确认；Upwork draft-and-confirm；反直觉话术「卖 HITL 比卖全自动好卖」 | `examples/tool-annotation-gate/` 审批门 + `docs/financial-agent-tender-compliance-2026-11-10.md` |
| 5 | **存量 Java 资产 MCP 化**（不是重写） | 广发 13327「可观测/自动化/日志平台的 MCP 封装接入」；中软环球「内网 AI 编码工具适配」；OnePlan「respects existing permissions」 | `mcp-integrations` + 四语言 Client SDK 示例 |
| 6 | **等保 / 私有化 / 信创合规工件** | 工信部电子五所 173.53 万（广州、2 个月交付）；军队 190 万；财富雷达「不能上云的钱最好赚」 | `docs/enterprise-rfp-checklist.md`（28 类攻击面逐条验收） |
| 7 | **多租户隔离 + 不可篡改审计** | 广发 13322「数据按客户经理归属严格隔离 / 服务行为自动留痕 / 日志不可篡改」；Upwork 单要求多租户 | `mcp-tenant` + `examples/audit-ropa-export/` |
| 8 | **开源影响力 = 售前信任状** | GitHub 组织仓库 + 每日 V1.x 迭代 + 20 组可编译示例；CSDN 万字长文免费放大「治理真空」叙事 | README 版本表 V1.71 + 掘金/CSDN SEO 稿 |

**一句话定位**（可直接放进 Upwork/LinkedIn 简介）：
> 我不帮你「接上 MCP」——那层已经标准化了。我帮你在**执行层证明**：只读是真的只读、
> 每一笔写操作有人签字、每一次调用可回溯、存量 Java 权限不用推倒重来。

---

## 7. 诚实边界（必须随任何对外材料一起给出）

1. **本框架不受** pgEdge 7 CVE / 官方 servers 5 GHSA / Mendix 双 CVE **直接影响**
   （本框架不内置这些实现）；引用它们是说明**行业级失效模式**与买方验收口径。
2. 广发四份为**预征集 ≠ 采购邀请 ≠ 正式招标**；13327 截止**未在官方正文呈现，须回
   采购平台核实**，不得凭编号推断；本项目 7 项资质门槛**全不满足**，只能联合体技术分包，
   **禁虚构**企业主体 / 合同 / 案例 / 等保证书。
3. 工信部电子五所、成都中西医结合医院两个新标**未逐字点名 MCP**，判为邻近赛道，
   **不计入 MCP 可投清单**；建行 900 万与 MCP 无关。
4. 全部报价为**公开挂牌 / 卖方销售页价，非成交价**；eSEOspace 薪资为小样本岗位挂牌，
   **不代表 MCP 溢价**；CSDN 文中 Atlassian 1,500 万次/日调用为单一来源未证实，未采信。
5. 嘉信理财 MCP 目前**只接 Claude（尽管协议设计中立）**、仅零售客户、RIA 不含；
   「打破 Anthropic 独占」是分析师判断而非既成事实。
6. 🆕 参考实现未接入 `mcp-core` / `mcp-registry` 主模块：CP2 where 作用域 / CP3 会话
   状态 / CP4 身份来源 / CP5 环境净化 / CP6 路径 IP **以执行上下文标志表达**，
   用于演示 fail-closed，真实落地需接 JDBC/pgx 元数据、连接池 hook、可信代理头、
   DNS 解析后 IP 分类器。**别把示例当生产组件卖**。
7. Spring AI 2.0 **未执行升级**（见 `docs/spring-ai-2.0-migration-notes-2026-11-10.md`）；
   `mcp-monitor` `exportPrometheus()` 挂 actuator、dataClassification 差异化审批引擎
   仍为跨期待办。

---

## 8. 来源清单（18 源，采集 2026-11-13）

1. FixTheVuln — https://fixthevuln.com/ai-ide-mcp-disclosures.html
2. NVD CVE-2026-126196 — https://nvd.nist.gov/vuln/detail/CVE-2026-126196
3. HackerFeeds CVE-2026-126197 — https://hackerfeeds.com/cve/CVE-2026-126197
4. ThreatMonitor 126196 — https://threatmonitor.io/cve/CVE-2026-126196
5. theworldofai CWE-346 — https://theworldofai.org/ai-news/cwes/CWE-346/
6. 广发证券 13321 — https://gfjc.gf.com.cn/gyszjgg/13321.jhtml
7. 广发证券 13327 — http://gfjc.gf.com.cn/gyszjgg/13327.jhtml
8. 广发证券官网 企业微信征集 — https://www.gf.com.cn/web/article/detail?columnId=4&articleId=01a1a56c47c77f2cabc5083dbccf44eb
9. 机电设备采购平台 13322 — https://www.chinamae.com/partner/7c2b37352c0f32ddf1890ba8c6b63c52.html
10. 中招联合 工信部电子五所 — https://www.365trade.com.cn/zfwzb/896160.jhtml
11. 中国政府采购网 成都医院更正 — https://www.ccgp.gov.cn/cggg/dfgg/gzgg/202611/t20261111_27659980.htm
12. Swift Media 11-13 — https://swiftmedia.ca/blog/ai-headlines-for-business-november-13-2026
13. RIABiz Schwab — https://riabiz.com/a/2026/11/13/schwab-surprises-analysts-with-extra-rapid-launch-of-own-mcp-servers-also-leaping-from-read-only-to-agentic-get-stuff-done-ai-it-leaves-rias-out-for-now-and-makes-anthropics-days-numbered-as-schwabs-exclusive-llm
14. CyberSentinel Schwab — https://cybersentinelnews.com/charles-schwab-launches-agentic-mcp-servers-sidestepping-rias
15. OnePlan 11-12 — https://oneplan.ai/oneplan-november-2026-release
16. CSDN 治理真空 — https://blog.csdn.net/summerliyang/article/details/170562593
17. 海康威视 JD — https://talent.hikvision.com/home/socity/position?postId=2350F2E18D457BE3987BA4AD9EFAD237
18. ILS / Upwork / eSEOspace / 智慧树 / 法本 / 中软环球 / Upwork 时薪 / 费率横评 /
    Digital Solo Hub（招聘与价格，见第 3、4 节内联链接）

---

_本报告随项目版本持续更新。V1.71 · 2026-11-13 · 攻击面 26 → 28 类_
