# 财富机会雷达 · MCP 赛道（2026-11-17，窗口 11-15~11-17）

> **Task 6 全量输出**：哪些企业在招 MCP 人才 / 哪些项目在招标 / 什么价 / Java+Spring+AI 组合的卖点。
> 采集方式：实时 web 检索，所有金额、日期、JD 原话均标注来源；**未点名 MCP 的一律不计入可投清单**。

---

## 1. 执行摘要 · 今日最强 6 个信号

| # | 信号 | 为什么值钱 |
| --- | --- | --- |
| 1 | ⭐⭐ **结果/输出面成为新攻击前沿**：REBUILD `CVE-2026-129875`（MCP QueryRecords 把 `User.password` 当可过滤字段，count 响应当盲注 oracle，**逐字符恢复管理员 SHA-256 口令哈希并登录管理员**） | 「只读查询的结果也可能泄密」→ 本期 V1.74 核心判词：**工具返回给模型的结果也不是安全边界** |
| 2 | ⭐⭐ **AWS 11-16 两 MCP Server 同日暴雷**：`CVE-2026-126816`（aws-pricing-mcp-server 路径穿越任意文件写，**工具被错误标记 read-only 致 MCP 客户端自动批准**）/ `CVE-2026-126681`（Timestream for InfluxDB MCP 不限制连接端点，prompt injection 泄漏 API token） | 「误标只读 = 自动批准」直接坐实 V1.71+V1.74：**声明层零信任**；`uvx/pip` 装完从不更新的存量风险可进标书 |
| 3 | ⭐⭐ **官方 MCP Python SDK `CVE-2026-115563`（7.5）**：向不可信 auth 端点泄漏 OAuth 密钥（11-16）；code-review-mcp-server `CVE-2026-68934`（8.7/CWE-78）工具调用者经拼接参数跑 shell | 「升级官方 SDK ≠ 安全」再实锤；输出/参数面成为护城河新增量 |
| 4 | ⭐⭐ ⭐ **SnapLogic 11-17（今天）发布 Discovery Hub**：MCP 中央注册表 + 网关治理 + Monitor 可观测 + SnapGPT pipeline 治理，客户含 AstraZeneca/Adobe/Verizon/Sony | 又一 iPaaS 把「MCP 注册+治理」做成标准产品层 → 差异化换锚到**跨运行时签字 / 私有化合规工件 / 存量 Java 权限复用** |
| 5 | ⭐ **企业 AI 控制面五原语被系统定义**（agenticreview 11-16）：scoped credentials / 每次工具调用的运行时策略 / 单一治理注册表 / 可审计可归因 / 动作级 RBAC（**权限给到动作，不是给到工具**） | 五原语逐条命中本框架 `mcp-auth`/`mcp-governance`/`mcp-registry`/`mcp-audit`，可直接做售前映射 |
| 6 | ⭐ **Schwab 的「受控门」架构深度复盘（twarx 11-13）**：$13.33T 券商 MCP Server 从只读跨到下单，**机构自己当控制面**（自有 IdP 发 token、读写工具分 scope、prepare→带外确认、记动作而非聊天记录） | 「不是让银行信模型，是让银行拥有授权服务器/工具目录/审计轨迹」—— 这是可直接进标书的可复用论据 |

> **一句话**：协议治理权上移到基金会和云厂商，**但「回传什么、写到哪、连到哪、拼成什么命令」这一层没人替你管** —— 这一层只能由集成方在自己的执行路径里落地并现场验收。

---

## 2. 招标 / 采购（国内）

### 2.1 广发证券四份征集 · 状态更新

| 编号 | 项目 | 截止（官方原文） | MCP 相关要求 | 状态 |
| --- | --- | --- | --- | --- |
| **13321** | 智能金融资讯数据服务平台 | 2026-11-13 17:30 | MCP 工具协议 + Skill + 子 Agent + 混合检索 | ✅ 已截止 |
| **13322** | 2026 年企业微信持续建设项目 | 2026-11-15 17:30 | MCP 工具调用稳定且权限可控 / 日志不可篡改 / Dify·hiagent / 广州本地团队 | ✅ 已截止 |
| **13327** | AI 智能运维（稳定性保障） | **官方正文确无报名截止** | 目标第 7 条逐字「推进可观测、自动化、日志等关联平台的 **MCP 封装接入**」+ 智能体运行观测 + 模型调用管控 + 敏感信息分级 | ⚠️ **须向采购平台核实，不得凭编号推断** |

> **诚实口径**：四份均为**供应商预征集 ≠ 采购邀请**；本项目 7 项资质（独立法人 / 成立 3 年+ / 注册 500 万+ / 原厂或授权代理 / 金融成功案例 / 完税 / 广州本地团队）**全不满足** → 只能作**联合体技术分包**，**禁虚构材料**。

### 2.2 本期新抓 / 跟踪窗口

| 项目 | 预算 / 时间 | MCP 关联 | 结论 |
| --- | --- | --- | --- |
| 国家管网集团智能体平台升级（讯飞/联想中标候选，11-06 公示） | 标段一 **2686 万**（讯飞）+ 标段二 **1402 万**（联想），合计约 4089 万 | 逐字「工具调用」+ 平台安全防护组件开发，**未点名 MCP** | ❌ 不计入可投清单，仅作智能体平台预算锚 |
| 河北省数据和政务服务局垂直大模型/智能体研究（11-13 中标，中关村科学城城市大脑） | **48.9 万** | 未点名 MCP | ❌ 不计入可投清单（延续 V1.73 口径） |
| 用友《2026 中国 AI 智能体产业应用标杆案例蓝皮书》（11 月） | — | 非招标；买方口径实证「写操作强制留痕与回滚」 | ✅ 执行层三档（只读/低风险写/高风险动作）+ 治理层记录身份·模型版本·工具调用·审批 + 开放层「**MCP 是连接手段，协议接入不替代业务授权**」 |
| 军队大模型安全防护 / 中山 / 山东重工 / 呼市 / 工信部五所 | 190 万·11-16 / 399 万·11-17 / 11-20 / 900 万·11-23 / 12-03 | 延续跟踪，需治理+网关+等保素材 | ⏰ 倒计时，MCP 直接关联待核 |

---

## 3. 招聘（本期新抓，均来自实时 JD）

| 岗位 | 公司 / 地点 | 薪资 | JD 中的 MCP 原话 |
| --- | --- | --- | --- |
| 高级 AI 系统开发工程师（大模型与 RAG，A18742） | 小米科技（北碚人才网 11-11） | **25–30k** | 「设计 Agent 工作流编排框架，支持**工具调用（MCP 协议）**、任务规划与自动化执行」+ Java/Spring/SpringBoot + 高并发分布式 |
| AIOT 云平台工程师 | 深圳（猎聘，11-09 更新） | **20–30k·16薪** | 「将底层硬件与系统服务封装为标准化 API 与 **MCP 接口**，供上层 AI Agent 调度」+ 5 年 Java/Spring Boot + MCP/OpenAPI 实践 |
| Senior AI Engineer / Forward-Deployed | Sagl Consulting（新加坡，regulated：银行/资本市场/医疗/保险） | **$9.3k–126k/yr** | 「RAG pipelines、healthcare AI assistants、Claude API 和 **Model Context Protocol (MCP) servers**」+ Python/Java/Spring Boot + MAS/MoH 合规 |
| Frontier / Fullstack AI Engineer | Synapse Business Systems（Phoenix AZ，W2 合同，11-11） | **$50–55/hr** | 「Architect enterprise-scale agent deployments, creating **trust layers and cross-agent governance frameworks**」+ 「Expertise in **MCP**, RAG-at-scale, knowledge graph」+ Java/Spring Boot |
| Senior Software Engineer (Agentic SDLC) | Ford（Dearborn MI，hybrid，11-12） | **$115k–192.9k** | 「Evaluate emerging AI tools... **MCP integrations**, agentic workflows」+ Java/Spring Boot/Spring Cloud + GCP |
| Senior AI Engineer（补充） | 金融/医疗强监管行业（多岗合集） | 见源 | 「每笔 agent 操作强制用户确认 + 可撤销 scope + 可审计身份」= HITL/责任分界共识 |

> **口径**：薪资为 JD 挂牌区间，**非成交价**；Sagl「20+ 年经验」与「10+ 年」在 JD 内并存，属描述不一致，未做断言。

---

## 4. 接单价格与市场（Upwork / 自由市场）

| 指标 | 数值 | 来源 / 口径 |
| --- | --- | --- |
| ⭐ AI 相关固定价项目预算溢价 | **+52%**（控制子类别与月份后；原始 +70%） | OpenAI×Upwork 联合研究《The Rebundling of Freelance Work in the AI Age》（11-10），36,000+ 对话样本 |
| 同等工作挂牌时薪溢价 | 仅 **+1.5%**（原始 +9%） | 同上 → **「变大不是变贵」：卖交付物/范围，不是抬时薪** |
| 前 90 天工时 | **+40.6%**；罕见任务组合 8.4% vs 2.4% | 同上 |
| Upwork 官方 MCP server | 周活 **×20**、AI agent 近 **5000 万次请求**、Connects 年化收入 **>$300 万** | Upwork Q3 2026（11-12 8-K/A） |
| AI 技能自由职业 GSV | 年化 **~$4.5 亿**，同比 **+55%** | 同上（**全平台 AI 技能盘，非 MCP 通道收入**） |
| Upwork 费率 | 卖方 **0–15%**（三源复核）/ Fiverr 20% / Freelancer 10% 或 $5 | 11 月三源 |
| 企业报价带（延续） | 生产级 **$80K–220K** / 企业网关 **$250K 起** / 7 档公开报价 $3.7K–72K | 卖方公开页，**非成交价** |
| MCP 接入形态（新） | 默认禁用 + 显式激活 + 只读 + 可撤销（Masttro 11-16）；准备付款但须本人签名（Tochka Bank 11-16） | 两家受监管金融机构的 MCP 上线形态 → 售前模板 |

---

## 5. 攻防生态（供护城河叙事，均非本框架受影响）

| 披露 | 要点 | 对应模块 |
| --- | --- | --- |
| REBUILD `CVE-2026-129875` | MCP QueryRecords 字段过滤不设敏感列 → 低权限用户借 count 盲注恢复管理员哈希 | `mcp-governance` + `mcp-audit` + 本期 `examples/result-field-guard/` |
| AWS `126816` / `126681`（11-16） | 路径穿越任意写（误标只读）+ 端点不限制泄漏 token | 同上 + `mcp-core` |
| MCP Python SDK `115563`（7.5）/ code-review `68934`（8.7） | OAuth 密钥泄漏到不可信端点 / 参数拼 shell | `mcp-auth` + `examples/stdio-command-allowlist/` |
| IBM ContextForge `110279`（8.1，11-12） | 特制 HTTP header 覆盖网关上游凭据，破坏身份隔离与审计准确性，修 1.1.1；临时缓解=在反代剥离 `X-Upstream-Authorization` | `mcp-gateway` + `mcp-auth` |
| AI CVE tracker（theworldofai，11-17 核） | 累计 **1437** 条 AI 相关 CVE；11-16 单日 MCP 相关 4 条 | 护城河量化数字 |

---

## 6. Java + Spring + AI 组合的 8 条卖点

1. **唯一 Java/Spring 开源企业级 MCP 治理栈**：横评 Bifrost/Docker MS/AWS/Kong/SnapLogic/IBM ContextForge 清一色非 Java 栈（Python/Go/平台锁定）→ 存量 J2EE 权限、SSO、审计可直接复用。
2. **结果面护城河（本期新增）**：`examples/result-field-guard/` 把 REBUILD/AWS/官方 SDK 的回传字段、输出路径、外发端点、shell 拼接四类泄漏做成可现场演示的 43 断言守卫。
3. **六段式「X 不是安全边界」方法论**：注解→注册中心→只读声明→决策层→配置面→**结果面**，每一段都有第三方 CVE 背书 + 可运行对照组，售前可直接讲。
4. **误标只读 = 自动批准**：AWS `126816` 证明 `readOnlyHint` 不能进允许分支；本框架「声明层零参与授权」有 V1.71~V1.74 四代工程实证。
5. **Schwab「受控门」同构**：读写工具分 scope + prepare→带外确认 + 记动作不记聊天 + 机构自有 IdP → 本框架 `mcp-auth`/`mcp-audit`/`mcp-governance` 逐条对位，解决「谁担责」的采购死结。
6. **企业控制面五原语逐条命中**：scoped credentials / 每次调用运行时策略 / 单一治理注册表 / 可审计归因 / 动作级 RBAC —— 逐条映射 21 模块。
7. **打补丁 ≠ 清干净**：AWS `125926` 官方明确升级不改存量配置 + IBM `110279` 修 1.1.1 → 「存量全量复验 + 举证」是可写进 SOP 的收费服务。
8. **存量 Java 资产 MCP 化**：小米/深圳 AIOT/Ford/Sagl 的 JD 逐字 Java/Spring Boot + MCP → 用 MCP 把历史 Java 系统安全接入 agentic 工作流，命中 `mcp-core`+`mcp-registry` 主场。

---

## 7. 来源（18 条）

1. echeloncyber 11-15 周报（IBM `110279`）—— https://echeloncyber.com/intelligence/entry/cyber-intelligence-weekly-november-15-2026-our-take-on-three-things-you-need-to-know
2. cyberpress AWS `125926` —— https://cyberpress.org/aws-mcp-gateway-ssrf-flaw
3. eyeon.ai REBUILD `129875` —— https://eyeon.ai/f/3815
4. theworldofai AI CVE archive（11-17 核）—— https://theworldofai.org/ai-news/cves/archive/
5. theworldofai `CVE-2026-68934` —— https://theworldofai.org/ai-news/cves/CVE-2026-68934
6. securityinsights Cloud Digest 11-17（AWS `126816`/`126681`）—— https://updates.securityinsights.io/2026/11/cloud-digest-17-nov-2026.html
7. junglewise `CVE-2026-126816` —— https://junglewise.ai/threats/cve-2026-126816-path-traversal-and-arbitrary-file-write-in-the-cost-report
8. markets.businessinsider SnapLogic Discovery Hub 11-17 —— https://markets.businessinsider.com/news/stocks/snaplogic-launches-mcp-discovery-hub-adds-new-ai-agent-building-and-governance-capabilities-1036773291
9. agenticreview 企业 AI 控制面五原语 —— https://agenticreview.co/posts/enterprise-ai-control-plane-defined-for-agentic-workforces-running-mcp-servers-a
10. MIT Technology Review 11-16（Infosys 48% 无治理 + SAP Agent Hub）—— https://www.technologyreview.com/2026/11/16/1146070/building-ai-that-can-work-across-the-enterprise/
11. twarx Schwab 受控门复盘 —— https://twarx.com/research/schwab-mcp-server-agentic-write-access
12. cybersentinelnews Schwab 上线 —— https://cybersentinelnews.com/charles-schwab-launches-agentic-mcp-servers-sidestepping-rias
13. crofirst Masttro MCP 11-16 —— https://crofirst.com/news/masttro-mcp-server-launches-for-family-office-ai-connectivity
14. ai-digest Tochka Bank MCP —— https://www.ai-digest-1.com/news/tochka-bank-opens-its-services-to-external-ai-agents-via-an-mcp-server-23611
15. 北碚人才网 小米 A18742 —— https://www.beibeirc.com/job2055843563.shtml
16. 猎聘 AIOT 云平台工程师 —— https://www.liepin.com/a/81689383.shtml
17. alion Sagl Consulting JD —— https://alion.io/job/sagl-consulting-senior-ai-engineer-forward-deployed-solutions-engineer
18. datafirstjobs / mccoy Ford JD —— https://datafirstjobs.com/frontier-fullstack-ai-engineer-w2-only-synapse-business-systems-98eaa2c232 / https://tracker.mccoy.io/postings/ford%3A72881
19. youmake OpenAI×Upwork 联合研究解读 —— https://blog.youmake.dev/articles/ai-freelance-projects
20. teklent Upwork 37% 裁员 + MCP 数据 —— https://www.teklent.com/blog/upwork-expands-layoffs-37-percent-workforce-what-workers-get
21. 广发 13327 原文 —— http://gfjc.gf.com.cn/gyszjgg/13327.jhtml
22. 新浪财经 国家管网 4089 万 —— https://finance.sina.com.cn/roll/2026-11-11/doc-iniyixxy2012894.shtml
23. 用友 企业级 AI 平台选型四层 —— https://www.yonyou.com/subject/zcy/news/8151

---

## 8. 诚实边界

- 全部第三方 CVE（REBUILD / AWS / IBM / Python SDK / code-review）**本框架不受直接影响**；`examples/result-field-guard/` 是对其攻击范式的独立参考实现。
- 广发四份为预征集 ≠ 采购邀请；本项目无企业主体 / 商业合同 / 中标 / 金融案例 / 等保测评；7 项资质全不满足只能联合体技术分包。
- 国家管网 4089 万 / 河北政采 48.9 万**未点名 MCP，不计入可投清单**；用友蓝皮书为买方口径非招标。
- 全部报价 / JD 薪资 / 预算为公开挂牌或预算上限，**非成交价**；OpenAI×Upwork 为相关性分析非因果。
- Upwork Q3 数据为 11-12 披露（非近 3 天发生），作背景锚；MCP `+20 周活 / 5000 万请求 / $300 万 Connects` 为官方口径，**≠ MCP 通道直接收入**。
- 本期 New Zealand/印度等区域 JD 未采信（口径不明）；SnapLogic/Masttro/Tochka 为产品发布稿，功能以厂商自述为准。
