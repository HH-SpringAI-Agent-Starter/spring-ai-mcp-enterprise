# 财富机会雷达 · 2026-11-12（周四 21:30 CST）

> 抓取窗口 **11-10 ~ 11-12**（实时 web_search，含 11-12 当天带日期戳数据）；项目版本 **V1.70**。
> 本期一句话：**负责审批工具的注册中心自己成了提权入口；与此同时，券商把「MCP 封装接入」正式写进了采购目标。治理层的买方从「要不要」变成「谁能证明自己管得住」。**

---

## 0. 今日结论（先看这个）

| # | 结论 | 硬证据 | 对「挣钱」的含义 |
| --- | --- | --- | --- |
| 1 | **注册中心自己被提权** | AWS 11-11 两份 Important 公告 `2026-179-AWS` / `2026-180-AWS`：`CVE-2026-100307`(8.8) + `CVE-2026-125926`(8.5)，**第一个洞发出的正是第二个洞所需的权限** | 售前新增一整章「管理面自防护」，可直接现场演示 |
| 2 | **打补丁 ≠ 清干净** | 官方修复说明：1.35.0 只在注册 / 修改时校验上游，**升级前已存配置不变**；升级不撤销已植入 group 映射、不使已泄露凭据失效 | 卖「**存量全量复验**」这项可验收服务，不只卖组件 |
| 3 | **广发第四份征集出现，MCP 写进项目目标第 7 条** | [13327《2026 年面向稳定性保障的 AI 智能运维项目》](http://gfjc.gf.com.cn/gyszjgg/13327.jhtml)：「推进可观测、自动化、日志等关联平台的 **MCP 封装接入**」 | 本期最值钱的国内直接机会，应答包见 [`gf-securities-aiops-tender-response-2026-11-12.md`](gf-securities-aiops-tender-response-2026-11-12.md) |
| 4 | **券商 AI 从「试点」进入「能力集合 / 规模计量」** | 中国证券报 11-12：华泰「行知」上线**多维度 MCP 能力集合**；国泰海通半年报累计智能体 **1,314** 个 / 技能模块 **404** 个；广发在 WorkBuddy 上线 12 项自研技能 + 9 项专家能力；中证协 34 篇案例集 | 买方已能数清自己有多少工具 → 纳管 / 审批 / 观测成为刚需 |
| 5 | **MCP 成为产品的定价分层维度** | OnePlan 11-12 发布 MCP Server / 天眼查 MCP 11-12 上架 ModelScope / 企查查风险信息 MCP 11-12 上架腾讯云 / Agorapulse·OneUp·Smart Post Studio 11-12 定价横评（$8 / $15 / per-seat） | 「MCP 能力是否可写、是否含在基础套餐」直接决定价格档 |
| 6 | **海外金融业出现 MCP 实弹岗** | PNC 银行 11-10 发布 Software Engineer – AI Agents / GenAI，明确「Implement and support **Model Context Protocol (MCP) based solutions and integrations**」 | Java+Spring+AI 组合的海外受监管行业切口 |

---

## 1. 🔥🔥🔥 注册中心信任边界崩塌（本期最强新叙事）

### 1.1 AWS `mcp-gateway-registry` 双 CVE（公告 2026-11-11）

| 项 | `CVE-2026-100307`（GHSA-rxf3-qgpv-wxvr） | `CVE-2026-125926`（GHSA-fc39-fwh3-wv5r） |
| --- | --- | --- |
| 评分 | CVSS 3.1 **8.8 High** | CVSS 3.1 **8.5 High** |
| 公告 | `2026-179-AWS`（11-11 12:30 PST） | `2026-180-AWS`（11-11 13:00 PST） |
| 根因 | `/api/servers/*` 管理端点**只认证、不做角色 / 权限校验** | 注册 / 修改 API 未校验上游目标 |
| 利用 | `POST /api/servers/groups/import` 把自己 IdP group 映射到 `mcp-registry-admin`；**管理员身份按请求派生**，下次请求即生效、无需重新认证 | 有注册 / 修改权限者把网关上游指向内网服务与**云实例元数据端点**，部分场景可读回响应 |
| 后果 | 全部已注册资产控制权 + 读时解密的 server 凭据 / 自定义 header + 访问控制配置本身 | SSRF → 云凭据泄露 |

**链式关系**：提权洞给出的正是 SSRF 洞所需的「注册 / 修改」权限。这不是两个独立漏洞，是一条完整攻击链。

**修复的三个「不」**（本期最有工程价值的判词，来自 [AL-ICE 11-11 分析](https://al-ice.ai/posts/2026/11/aws-mcp-gateway-registry-cve-2026-100307-125926-privesc-ssrf-november-2026) 与 [xcademia 复述](https://www.xcademia.com/news/aws-fixes-mcp-gateway-registry-flaw-that-could-give-low-privilege-users-administrator-access)）：

1. **不清理存量**：1.35.0 只在注册 / 修改时校验上游地址，**升级前已存储的 server 配置保持不变**。
2. **不撤销映射**：升级只在写入路径加授权，不修改升级前创建的 group 定义 → 恶意管理员映射升级后仍可能生效。
3. **不失效凭据**：攻击者具备管理员权限期间读过的凭据不会因升级自动失效。

→ 官方开出的处置是「**逐个复查所有已注册 server 的上游地址** + 回溯受影响版本窗口内的异常注册与变更日志」。这就是一个**必须为买方做的验收动作**，而不是一句「升级到最新版」。

> ⚠️ **口径说明**：公开来源对修复版本表述不一致（一说 privesc 影响 `1.0.9–1.24.7`、修于 `1.25.0`／6-27；另一说受影响至 `1.34.0`、SSRF 修于 `1.35.0`／10-29，GHSA 均 11-11 才公布）。可自洽的读法是两洞分别修于 1.25.0 与 1.35.0、GHSA 延迟披露；**版本级符合性一律以 AWS 官方公告 / GHSA 原文为准**，本期不做版本断言。

### 1.2 同周另外三条，把「网关 / 注册表自身」坐实为新攻击面

- **`CVE-2026-109492`**（[OffSeq 11-10 / 数据截至 11-11](https://radar.offseq.com/threat/cve-2026-109492-cwe-639-authorization-bypass-through-user-controlled-key-in-microsoft-mcp-gateway-792138926698577a)）：Microsoft `mcp-gateway` IDOR / CWE-639，CVSS 3.1 **8.8**，修于 1.0.0，CNA=microsoft，`Not known to be exploited`。
- **`CVE-2026-125271`**：AWS API MCP Server **安全策略绕过（操作名归一化错误）** —— 策略匹配前不做规范化，变形名字即可绕过。
- **`CVE-2026-102610` 11-11 实验室复测**（[AgentOpsGuide](https://agentopsguide.com/fast-agent-vulnerability-cve-2026-102610)）：fast-agent 0.7.21 / 0.10.54 默认绑 `127.0.0.1` 了，但**同机免鉴权仍可执行 shell**，`--host 0.0.0.0` 只打印警告就恢复全量远程访问 → 「绑 localhost 就安全」第二次被实测否掉，延续 V1.69 Siemens 双 CVE 的结论。
- 生态横评（[dev.to 企业注册表五强](https://dev.to/kuldeep_paul/top-5-mcp-registry-tools-for-enterprise-ai-governance-2026-52ap)）：**Official MCP Registry 的 Access Control Model 标注为 `None (open discovery layer)`**，Bifrost / JFrog / Docker MCP Catalog / Stacklok ToolHive 各自补上 RBAC；[Ajelix 指南](https://ajelix.com/ai/mcp-in-agentic-ai) 引注册表普查：**40.6%** 多版本 server 在版本间无通知改变声明、**9.57%** 被扫 server 无鉴权网络暴露；Pinterest 生产做法是「每个 MCP server 都在中央注册表后面，注册表外的任何 agent 都调不到」，66,000 月调用 / 844 活跃用户 / 约省 7,000 工程小时。

**结论**：V1.69「注解不是安全边界」→ V1.70「**注册中心自己也不是安全边界**」。治理层必须先管住自己的管理面，且必须能证明存量是干净的。今晚新增可运行参考实现 [`examples/registry-admin-guard/`](../examples/registry-admin-guard/README.md)（45 断言全绿）。

---

## 2. 💰 哪些项目在招标（本期可投清单）

### 2.1 广发证券第四份征集（11-12 新增，最优先）

| 项 | 内容 |
| --- | --- |
| 公告 | [13327《2026 年面向稳定性保障的 AI 智能运维项目供应商征集公告》](http://gfjc.gf.com.cn/gyszjgg/13327.jhtml) |
| 与 MCP 的直接关系 | 项目目标**第 7 条**逐字：「推进可观测、自动化、日志等关联平台的 **MCP 封装接入**」 |
| 其余六条目标 | ①运维智能体管控框架（模型范围 / 分阶段调用 / 输入输出敏感信息含代码分级 / 异常行为自动拦截）②代码级故障排障 ③运维知识图谱 ④「机器人常态观测、人工例外介入」人机协同闭环 ⑤通用技能与智能体 ⑥按个人 / 群组 / 智能体 / 技能 / 工具维度的 DAU·WAU 度量 |
| 资质硬门槛 | 独立法人；**成立 3 年以上**、**注册资金 ≥500 万**；**原厂或授权代理**；3 年以上运维平台代理销售经验且**≥2 个同类成功案例**；**广州本地服务团队**；纳税信用 / 无欠税证明；与其它报名服务商无关联 |
| 截止 | ⚠️ 检索到的公告正文**未呈现报名截止时间**，须回采购平台原文核实；V1.68 已核实的另三份为 **11-12 / 11-13 / 11-15 17:30** |

> **口径**：13327 编号大于此前 13321 / 13322，按公告序列判断为本期新增的**第四份**；但「第四份」为序列推断，非官方编号说明。四份均为**供应商预征集 ≠ 采购邀请**。逐条应答见 [`gf-securities-aiops-tender-response-2026-11-12.md`](gf-securities-aiops-tender-response-2026-11-12.md)。

### 2.2 存量倒计时（沿用 V1.68/V1.69 已核口径，本期未复核到新公告）

| 标的 | 截止 | 状态 |
| --- | --- | --- |
| 广发 13321《智能金融资讯数据服务平台》 | 11-13 17:30 | 剩 1 天 |
| 广发 13322《企业微信持续建设》（MCP 五件套写进资质门槛） | 11-15 17:30 | 剩 3 天 |
| 广发第三份《态势感知及安全分析平台扩容》 | 11-12 17:30 | **今日截止** |
| 军队大模型安全防护 190 万 | 11-16 | 剩 4 天 |
| 中山 399 万 | 11-17 | 剩 5 天 |
| 山东重工集团级平台二次标 | 11-20 | 剩 8 天 |
| 呼市 900 万 / 中国林科院 300 万 / 中央民大 3712 万 | 11-23 / 11-24 / 11-25 | 备标 |

### 2.3 行业侧信号（11-12 中国证券报 / 证券时报）

[《AI 加速渗透证券行业 数字化转型进入应用阶段》](https://field.10jqka.com.cn/20261112/c681724624.shtml)：

- **华泰证券**半年报：「AI 涨乐」升级核心交易服务智能体，机构平台「行知」上线研究技能模块与**多维度 MCP 能力集合**。
- **国泰海通**：上半年累计发布智能体 **1,314** 个、技能模块 **404** 个。
- **招商证券**：「小研 Work」把研究资料 / 指标 / 框架整理为**可调用的技能模块**；招商国际的 AI 原生投研执行系统由 AI 智能体完成任务，**最终结论与评级仍由持牌分析师签署担责**（HITL 责任分界的中国实证）。
- **中信证券**数字员工体系覆盖投资 / 投行 / 研究 / 营销 / 运营 / 合规风控；**广发**在 WorkBuddy 上线首批 12 项自研技能模块 + 9 项专家能力。
- **中证协**「智汇证券·共创未来」案例集 34 篇，覆盖运营提效 / 体验升级 / 决策支持 / 模式创新 / 研发效能 / 风险合规。
- 东吴证券孙婷判词：**「AI 金融应用的竞争重点正聚焦于专业数据、工具调用、场景渗透和持续使用」**、**「金融数据和研究框架能否以标准化方式被智能体调用，将影响产品的专业性和使用体验」** → 这句就是 MCP 的钱。

> ⚠️ 本节为行业趋势与买方成熟度佐证，**不是可投标的**；本期新检索到的国内公告未全部复核，未点名 MCP 的一律未计入可投清单。

---

## 3. 👔 哪些企业在招 MCP 人才（Task 6 逐条输出）

| 企业 / 来源 | 岗位 | 薪资 / 地点 | 日期 | MCP 明确度 |
| --- | --- | --- | --- | --- |
| **PNC 银行** [官方 JD](https://careers.pnc.com/global/en/job/PNC1GLOBALR240298) | Software Engineer – AI Agents / Generative AI | 未披露；Pittsburgh / Dallas / Strongsville / Birmingham / Denver 五地，**不提供签证担保** | **11-10 Posted** | ⭐ **逐字**「Implement and support **Model Context Protocol (MCP) based solutions and integrations**」+「Knowledge of MCP concepts」；Python / LangGraph / REST / 生产可观测；金融服务受监管环境 |
| **美团** [官方 JD](https://zhaopin.meituan.com/web/position/detail?jobUnionId=4119654480) | AI Agent 开发工程师 | 未披露 | 未标注 | ⭐ 明确 Agent Loop / Tool Use / **MCP** / RAG / Harness Engineering / **安全管控与可观测性体系**；Java 为可接受语言之一；「参与或维护开源项目、有可验证框架类技术资产」加分 |
| **贵州数据宝（词元宝）** [校招转发](https://jy.bsu.edu.cn/front/zpxx.jspa?tid=133109) | 开发工程师（AI 平台方向） | **7–10K/月**，贵州 | **11-10 发布** | 间接：MaaS 平台 / 工具调用 / RAG / SpringBoot / 限流缓存异步化；**未点名 MCP** |
| 猎聘 [高级 Java 开发工程师（AI 方向）](https://m.liepin.com/a/79976171.shtml) | 金融 AI 工程化 | **30–40k**，杭州 | 11-03 更新（**非近 3 天**，仅作价位参考） | 未点名 MCP；Spring Boot/Cloud + **Spring AI / LangChain4j** + ReAct / Function Calling / SSE / RAG |

**读数**：本期最值钱的是 **PNC 11-10** —— 受监管金融业把 MCP 写成岗位职责而非加分项，与 11-11 中信「Agent+Skill+MCP」、广发四份征集构成中美同步。国内明确报价仍以「Java + Spring AI + 智能体」为主、**MCP 多为隐含能力而非岗位名**，说明国内买方还处在「把 MCP 当实现细节」阶段 → 这正是把治理层做成可验收工程口径的窗口期。

> ⚠️ 网上流传的「智能体开发岗位同比 +217% / +455%、初级 40–60 万年薪」来自自媒体聚合页，**口径不明、未采信**，未计入上表。

---

## 4. 💵 什么价（本期报价锚，全部为公开价非成交价）

| 品类 | 数字 | 来源 / 口径 |
| --- | --- | --- |
| **MCP 作为产品定价分层** | Smart Post Studio 把 MCP 做成 **$8/月** 套餐的头条功能；OneUp 在最低 **$15/月** 档即含 MCP + API；Agorapulse 订阅免费含 MCP，但**要求 BYO AI 订阅**（Claude Pro / ChatGPT Go / Copilot Studio） | [11-12 三家横评](https://futuretweets.com/agorapulse-vs-oneup-vs-smart-post-studio-2026)，**读 vs 写能力差异**（至少一家的 MCP 不能 publish）是档位分界 |
| **生产级 MCP 项目** | $80K–$220K 含 audit / SSO / RBAC / versioning；企业网关 $250K 起；自建 $15K–$220K + 年运维 10–30% | 沿用 `mcp-governance-pricing-anchor-2026-11-09.md`，本期无新成交价 |
| **Upwork 费率**（10-22 三源复核，口径不变） | 卖方 **0–15%** / 客户端 Basic 3–5%、Business Plus 8–10% / 额外 Connects **$0.15** / Freelancer Plus $19.99/月 | [Earninger](https://earninger.com/store/upwork-freelance-marketplace)；旧 20/10/5% 口径弃用 |
| **Upwork 大盘**（11-02 Q3 财报） | 市场收入 **−9%**、MCP server 上线、**近 5,000 万次 agent requests**、AI 技能人才 GSV +55% | [GlobeNewswire 11-02](https://www.globenewswire.com/news-release/2026/11/02/3393727/0/en/upwork-reports-third-quarter-2026-financial-results.html)；⚠️ **agent requests ≠ MCP 通道收入**，不可混用 |
| **自由市场 AI 技能时薪** | AI/ML 工程 $120–$250/hr、云架构 $65–$130/hr、全美自由职业均值 $47.71/hr | 聚合站二手数据，**仅作区间参考，不作报价依据** |
| **国内 Java 岗** | 杭州金融 AI 30–40k；贵州 AI 平台 7–10K | 见第 3 节，跨度反映城市与业务差异 |

**本期定价新读法**：MCP 从「功能」变成「**套餐档位**」——能否写、是否含在基础价、要不要另买 AI 订阅，决定了收多少钱。对应到本项目的话术：**治理层决定这个 MCP 能不能被允许写**，因此治理层天然处在定价最高那一档。

---

## 5. 🧩 产品与市场侧（11-10 ~ 11-12 新上架）

| 事件 | 日期 | 对本项目的含义 |
| --- | --- | --- |
| [OnePlan November 2026 Release](https://www.saltwire.com/press-releases/pr-newswire/oneplans-november-2026-release-brings-portfolio-data-into-chatgpt-claude-and-copilot-and-adds-outcome-tracking-for-strategic-investments)：MCP Server 让 ChatGPT / Claude / Copilot 读写企业项目组合数据 | **11-12** | 官方逐字「**Every interaction respects existing OnePlan permissions**」→ **存量权限复用**正是本项目核心卖点；同时含「authorized actions」写操作 → 必须配审批门 |
| [天眼查 MCP](https://www.modelscope.cn/mcp/servers/TianYanCha/tyc-mcp) 上架 ModelScope（Hosted） | **11-12** | 官方数据商把**招投标 / 工商 / 风险查询**做成 MCP 工具 → 连「找招标」这个动作本身都在 MCP 化 |
| [企查查-风险信息 MCP](https://cloud.tencent.com/developer/mcp/server/11794) 上架腾讯云开发者社区 | **11-12** | 支持 StreamableHTTP / SSE，带 key 接入；国内数据厂商把 MCP 当**发行渠道** |
| [MS Agent Framework Python 1.25.0 治理指南](https://www.cloudproinc.com.au/index.php/2026/11/11/microsoft-agent-framework-mcp-approval-policies-governance-guide) | 11-11 | 四条判词延续可用：「metadata ... an input to your policy」「read-only does not always mean harmless」「overrides need their own controls」、approval fatigue |
| [Claude MCP connector 现状](https://penchan.co/en/ai/claude/mcp)（文档截至 11-09） | 11-09 | Managed Agents 的 MCP 工具**默认每次调用都要用户批准**；connector 仍 beta 且只能连公网 server → 私有化 MCP 仍需自建 |

---

## 6. 🎯 Java + Spring + AI 组合在本赛道的卖点（本期 8 条）

| # | 卖点 | 11-12 新证据 | 对应仓库真实资产 |
| --- | --- | --- | --- |
| 1 | **管理面自防护**：认证≠授权、授权源不可由注册数据派生 | AWS `100307` | 🆕 `examples/registry-admin-guard/` |
| 2 | **存量全量复验**：能证明「升级后注册表是干净的」 | AWS 修复三个「不」 | 🆕 `McpRegistryAdminGuard.reverifyAll()`（幂等 + 指纹漂移） |
| 3 | **上游 SSRF 准入 + 重定向逐跳 + Host/SNI 一致性** | `125926` / `109492` | 🆕 `validateUpstream()` / `validateRedirectHop()` + `connect-path-guard/` |
| 4 | **操作名归一化策略** | `125271` | 🆕 `canonicalizeOperation()` |
| 5 | **注册期注解完整性 + 元数据漂移复验** | V1.69 INITE 普查 / MS 1.25.0 | `examples/tool-annotation-gate/` |
| 6 | **存量 Java 权限复用**（不重写权限体系） | OnePlan 11-12「respects existing permissions」 | `mcp-auth` / `mcp-tenant` / 21 模块 |
| 7 | **HITL 责任分界与人工审核留痕** | 招商国际「持牌分析师签署担责」/ 广发 13327「机器人常态观测、人工例外介入」 | `mcp-governance` / `enterprise-rfp-checklist.md` |
| 8 | **可现场跑的验收证据**（不是 PPT） | 买方已能数清自己有 1,314 个智能体 / 404 个技能 | 8 组 `examples/` 纯 JDK 自检，累计 45+32+30+… 断言 |

**一句话定位（可直接用于标书 / Upwork profile）**：
> 我们不卖「又一个 MCP Server」，我们卖**让注册中心自己也管得住、且能向审计证明存量干净**的 Java/Spring 治理层；打补丁只修写入路径，我们补的是「复验 + 举证」。

---

## 7. ⚠️ 诚实标注（沿用项目口径）

- AWS / Microsoft 相关 CVE **本框架均不受直接影响**（未依赖其 registry / gateway 组件）；`100307`/`125926` 的 affected range 在不同二手来源表述不一致，**版本级判断以官方公告 / GHSA 为准**。
- 广发四份均为**供应商预征集 ≠ 采购邀请**；13327 截止时间**未在检索到的正文中呈现**，须回官网核实；「第四份」为编号序列推断。本项目**无商业合同、无中标、无金融案例、无等保测评 / 算法备案，且不成立 3 年、不满足「原厂或授权代理 + 2 个运维平台案例 + 广州本地团队 + 注册金 500 万」门槛** → 只能作**联合体技术分包**，禁虚构材料。
- 全部报价为**卖方销售页公开价或 JD 标价，非成交价**；Upwork「近 5,000 万 agent requests」是平台请求量，**不等于 MCP 通道收入**。
- 本节行业数据（1,314 智能体 / 404 技能 / 34 篇案例）来自 11-12 媒体报道引用的半年报，未逐份回查原始财报。
- 新示例仍是 `examples/` 参考实现，**未接入 `mcp-registry` 主模块**；主机判定为字面量检查，需与 DNS 解析后 IP 校验叠加；跨主机重定向默认拒绝是保守策略选择。
- Spring AI 2.0 **未执行升级**；`exportPrometheus()` 挂 actuator、基于 `dataClassification` 的差异化审批引擎仍为跨期待办。

---

## 8. 来源清单（本期 18 条，均带可访问链接）

1. [AL-ICE · AWS 双 CVE 深度分析（11-11）](https://al-ice.ai/posts/2026/11/aws-mcp-gateway-registry-cve-2026-100307-125926-privesc-ssrf-november-2026)
2. [xcademia · AWS privesc 修复与升级局限](https://www.xcademia.com/news/aws-fixes-mcp-gateway-registry-flaw-that-could-give-low-privilege-users-administrator-access)
3. [OffSeq · CVE-2026-109492 Microsoft mcp-gateway IDOR](https://radar.offseq.com/threat/cve-2026-109492-cwe-639-authorization-bypass-through-user-controlled-key-in-microsoft-mcp-gateway-792138926698577a)
4. [Vulnios · 11-11 威胁汇总（含 125271 / 125926 / 100307）](https://vulnios.com/threats/antifox-ai-pentesting-platform-linked-to-attacks-that-accessed-aws-production-se-2026-11-12)
5. [AgentOpsGuide · fast-agent CVE-2026-102610 11-11 实测](https://agentopsguide.com/fast-agent-vulnerability-cve-2026-102610)
6. [i10X · CVE-2026-123811 Dynatrace 三层修复](https://i10x.ai/news/cve-2026-123811-dynatrace-mcp-server-unauthenticated-http)
7. [dev.to · 五大企业 MCP Registry 横评](https://dev.to/kuldeep_paul/top-5-mcp-registry-tools-for-enterprise-ai-governance-2026-52ap)
8. [Ajelix · MCP 治理指南（40.6% / 9.57% / Pinterest）](https://ajelix.com/ai/mcp-in-agentic-ai)
9. [广发采购平台 · 13327 AI 智能运维征集](http://gfjc.gf.com.cn/gyszjgg/13327.jhtml)
10. [同花顺 · AI 加速渗透证券行业（11-12）](https://field.10jqka.com.cn/20261112/c681724624.shtml)
11. [同花顺财经 · 中证协 34 篇案例集](https://m.10jqka.com.cn/20261112/c681724624.shtml)
12. [CPI · MS Agent Framework 1.25.0 治理指南（11-11）](https://www.cloudproinc.com.au/index.php/2026/11/11/microsoft-agent-framework-mcp-approval-policies-governance-guide)
13. [PNC Careers · AI Agents / GenAI JD（11-10）](https://careers.pnc.com/global/en/job/PNC1GLOBALR240298)
14. [美团招聘 · AI Agent 开发工程师](https://zhaopin.meituan.com/web/position/detail?jobUnionId=4119654480)
15. [北京体育大学就业网 · 贵州数据宝招聘（11-10）](https://jy.bsu.edu.cn/front/zpxx.jspa?tid=133109)
16. [猎聘 · 高级 Java 开发工程师（AI 方向）](https://m.liepin.com/a/79976171.shtml)
17. [OnePlan November 2026 Release（11-12）](https://www.saltwire.com/press-releases/pr-newswire/oneplans-november-2026-release-brings-portfolio-data-into-chatgpt-claude-and-copilot-and-adds-outcome-tracking-for-strategic-investments)
18. [天眼查 MCP（11-12）](https://www.modelscope.cn/mcp/servers/TianYanCha/tyc-mcp) · [企查查风险信息 MCP（11-12）](https://cloud.tencent.com/developer/mcp/server/11794) · [11-12 MCP 定价横评](https://futuretweets.com/agorapulse-vs-oneup-vs-smart-post-studio-2026) · [Earninger Upwork 费率](https://earninger.com/store/upwork-freelance-marketplace) · [Upwork Q3 财报](https://www.globenewswire.com/news-release/2026/11/02/3393727/0/en/upwork-reports-third-quarter-2026-financial-results.html) · [Upwork MCP Server 官方页](https://www.upwork.com/ai/mcp) · [Claude MCP 现状](https://penchan.co/en/ai/claude/mcp)
