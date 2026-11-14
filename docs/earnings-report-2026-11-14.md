# MCP 赛道挣钱情报日报 · 2026-11-14（周六）

> 任务「🌙 MCP Server 企业化每日开发·21:30」Task 6 · 抓取窗口 **11-12 ~ 11-14**
> 目标：哪些企业在招 MCP 人才、哪些项目在招标、什么价、Java+Spring+AI 组合的卖点。
> 口径：所有金额为**公开挂牌价 / 公开中标公示 / 公开薪资区间**，非本项目成交价；本项目无商业合同/中标/金融案例。

---

## 0. 今日判词

**「买方已经自己把 MCP 建起来了，钱不在『帮你建』，在『帮你管住已经上线的』。」**

三条同日证据：
1. **广发研究 MCP & Skills 11-13 正式上线智慧数平台**（21 行业 / 9 万余指标 / 1000+ 公司预测）——券商不再只是发征集公告，而是自家研究资产已通过 MCP 生产化。
2. **Swift Media 11-13 采购周报**：「**MCP gateways become a procurement requirement**」——买方把集中注册表、per-agent scope、注入过滤、可导出审计写进 RFP 必选项。
3. **IBM ContextForge 双 CVE（11-13 官方首发）**：连「网关自己」的批量导入都能跨租户覆写凭据，**官方 Workarounds: None** —— 治理不是加分项，是上线前必须过的门。

叠加 11-12 arXiv 量化实证（in-prompt 护栏 100% 失守 / 模型外确定性 monitor severe actions 0%），本框架「**治理增强层 = 模型之外、任务绑定、可现场跑断言的执行门**」定位拿到本期最完整的一条证据链。

---

## 1. 招标 / 采购（哪些项目在招标）

| # | 项目 | 金额 / 时间 | MCP 相关度 | 判断 |
| --- | --- | --- | --- | --- |
| 1 | **广发研究 MCP & Skills 上线智慧数平台**（11-13 官方） | 未披露；5 项 MCP 能力、21 行业、200+ 数据源、9 万余指标、1000+ A股/港股/美股预测、900+ 产业图谱 | ⭐⭐⭐ **已生产上线** | **不是招标，是买方能力已落地的信号**。供应商窗口从「建 MCP」转向「治理/运维/安全/可观测」 |
| 2 | **广发 13327《2026 面向稳定性保障的 AI 智能运维项目》供应商征集** | 8 项资质（独立法人 / 成立 3 年+ / 注册资金 ≥500 万 / 原厂或授权代理 / 3 年以上运维平台经验 + 2 个成功案例 / 无重大违法 / **广州本地服务团队** / 税务信用评级或无欠税）；**正文确无报名截止** | ⭐⭐ 目标第 7 条逐字「推进可观测、自动化、日志等关联平台的 **MCP 封装接入**」 | ⚠️ **预征集 ≠ 采购邀请**；7 项资质本项目全不满足 → **只能联合体技术分包**；截止须向采购平台核实，**不得凭编号推断** |
| 3 | **广发 13322**（MCP 五件套写进资质） | **11-15 17:30 截止（剩 1 天）** | ⭐⭐ 工具定义/参数校验/鉴权限权/调用限流/日志可观测 | 硬门槛同上；建议把 12 项验收清单 + 51 断言输出压成 1 页 PDF 交潜在广州本地联合体主体 |
| 4 | **广发 13321** | 11-13 17:30 **已截止** | ⭐ | 已过窗口，仅作复盘 |
| 5 | **大模型应用场景建设技术服务中标候选公示**（11-11，新浪财经 11-12 报道） | **南天 218 万中标第一候选**、科大讯飞 268 万、百度 300 万；含企业级智能体平台 + 智能信贷助手 + 员工问答 + 客户经理助手 + 智能办公助手 | ⚠️ **未点名 MCP** | **不计入可投 MCP 清单**，仅作「企业级智能体平台」真实预算锚（200~300 万级） |
| 6 | 既有倒计时（沿用） | 军队大模型安全防护 190 万 **11-16** / 中山 399 万 **11-17** / 山东重工 **11-20** / 呼市 900 万 11-23 / 中央民大 3712 万 11-25 / 工信部电子五所 173.53 万 12-03（**未点名 MCP**） | 混合 | 前三项备「治理 + 网关 + 等保」素材；未点名 MCP 的**不计入可投清单** |

**⭐ 本期最重要的商务判断**：广发自家 MCP 已上线 → 明天起不要再以「我们能帮你建 MCP Server」为主话术，改成「**你的 MCP 已经上线了，我们来回答『谁在什么时候被允许调哪个工具、出事能不能导出证据、升级后存量配置还算不算数』**」。这也是 13327 第 7 条「MCP 封装接入」真正的付费点。

---

## 2. 招聘（哪些企业在招 MCP 人才）

| # | 企业 / 岗位 | 薪资 / 地点 | JD 关键原文 | 命中模块 |
| --- | --- | --- | --- | --- |
| 1 | **Jobgether — Software Engineer, Project, AI Platforms**（11-12 发布，US Remote） | **$139.7K–176.6K/yr** + 8% target bonus；8 年+ 经验 | 「Develop and maintain **MCP servers and tool interfaces, providing AI agents with secure and governed access** to product environments, build systems, testing infrastructure, and telemetry」+ OT/ICS 合规、二进制签名、数据边界 | ⭐⭐⭐ `mcp-governance` / `mcp-monitor` / `mcp-auth` 的英文同义词逐字命中 |
| 2 | **上海凯世通半导体**（万业企业 600641 子公司、大基金入股）— 应届 AI 工程师 | **10–18k·14 薪**，上海浦东，硕士 | 「**MCP 工具服务开发**：封装离子注入机数据接口（SECS/GEM、内部 API、MES/FDC）为标准 MCP 工具，**使 Agent 能安全调用**」+「理解 MCP 协议，能独立开发 MCP Server 封装工具接口」 | ⭐⭐⭐ 半导体设备 / OT 场景 MCP 化 + 字面「安全调用」 |
| 3 | 平湖中级软件开发工程师 | 7000–13000 元/月，嘉兴平湖 | Java/Spring Boot + Vue + AI Agent/RAG + Codex/Cursor/Claude Code | ⚠️ **未点名 MCP**，不计入 MCP 直接岗 |
| 4 | 华钦科技 CLPS 初级全栈（广州天河） | 10–13k | Spring Boot + 银行/金融科技优先 | ⚠️ 未点名 MCP，不计入 |
| 5 | 大连宜顺精密（11-12 校招） | 8–15k | Java/Go/Python + Spring Boot + 微服务 | ⚠️ 未点名 MCP，不计入 |

**延续口径**：MCP 正在变成**技能而非独立岗位**（V1.71 英国半年 123 个提及 MCP 的永久岗、BLS 中位 $135,980）。今天两条最精准的 JD 都在「**工业软件 / 半导体设备**」而非互联网，说明**存量重型 Java/OT 系统 MCP 化**是主线。

---

## 3. 价格（什么价）

### 3.1 自由职业费率（11-12 三源复核，旧口径弃用）

| 平台 | 卖方费率 | 备注 |
| --- | --- | --- |
| **Upwork** | **0–15%**（按合同浮动，2025-05 从固定 10% 改浮动） | Connects **$0.15/个**；旧「10%/20%」口径已过时；客户侧 Basic 5% + 每合同 $0.99–14.99 启动费 |
| **Fiverr** | **20%** | 完成订单才入账 |
| **Freelancer** | **10% 或 $5 取大** | 免费会员每月 6 次投标，需 $20 余额 |

### 3.2 时薪 / 项目预算

| 口径 | 数字 | 来源 |
| --- | --- | --- |
| 美国自由职业者全类目均值 | **$47.71/hr** | Upwork 2025 |
| 软件开发 | $52–60/hr | 2026 费率横评 |
| **AI / ML 工程** | **$120–250/hr**（另一源 $100–250） | 2026 费率横评 |
| 高级全栈 | $70–140/hr | 同上 |
| 域深度 + AI 熟练度组合 | 相对同经验传统服务 **+30–60%** | 同上 |
| 生产级含 audit/SSO/RBAC/versioning 的 MCP 交付 | **$80K–$220K**；企业网关 **$250K 起** | V1.67 沿用的公开销售页报价 |
| Upwork 官方时薪分层（V1.71） | $10–25 / $25–75 / **$75–150+** | Upwork 官方 |

### 3.3 ⭐ 本期新增：OpenAI × Upwork 11-10 联合研究（11-12 报道）

| 指标 | 数值 | 商务含义 |
| --- | --- | --- |
| AI 相关项目**固定价预算**（按子类目与月份调整后） | **+52.4%** | 预算确实变大 |
| **挂牌时薪** | **仅 +1.5%** | **但时薪几乎没涨** |
| 雇佣后前 90 天**记录工时** | **+40.6%** | 钱在工时与范围 |
| 任务簇数量 | 3.35（AI） vs 3.19（其他） | 差异不在任务数量 |
| 历史上罕见的任务组合占比 | **8.4%（AI） vs 2.4%** | **差异在「组合复杂度」** |

> **结论：AI 项目是「变大了」不是「变贵了」。** 报价必须按**交付物 / 工具成本 / 修订轮次 / 集成工作 / 预期工时**拆解，不能简单抬时薪。研究明确说明这是相关性分析（按子类目与月份调整），**不等于「加 AI 导致预算上涨」的因果结论**；且固定价预算 ≠ 最终成交、≠ 到手收入。

---

## 4. 治理 / 安全护城河（为什么我们的东西有人买）

| 日期 | 事件 | 数字 / 判词 |
| --- | --- | --- |
| 11-12 | **arXiv 任务绑定 reference monitor 论文** | in-prompt 护栏被持久自适应攻击者推到 **100%** 攻击成功率；模型外确定性 monitor 只放行 **1.3%/7.7%**、severe actions **0%** |
| 11-13 | **IBM ContextForge 双 CVE**（官方首发，v1.0.0~1.0.11，修 v1.1.0） | `110178` 跨租户导入覆写 endpoint/headers/凭据（**Workarounds: None**）+ `110194` DCR SSRF 5.4 |
| 11-13/14 | **AWS `125926` 版本区间坐实** | v1.0.3~1.34.0 中招、**1.35.0 修**；1.25.0 只修管理 API 洞 → 「跑 1.25.0+ 仍中」；**升级不改存量配置** |
| 11-10 | VS Code `GHSA-66hq-jw93-462h` | 8.3 / NVD 9.6，elicitation 文本变命令链接；1.145.3 改纯文本渲染 |
| 11-11 | 官方参考 git server `GHSA-6hqx-gqrx-c5r2` | 只读 `git_status` 执行 `core.fsmonitor`；修法强制 `GIT_CONFIG_COUNT` |
| 11-12/13 | pgEdge `CVE-2026-126197` | 8.8，dollar-quote 逃逸绕过 `read_only`；NVD 时间戳确认 |
| 11-13 | Permit.io 控制面授权三判据 | `adapter:create` 独立权限 / 内部 ID 不由用户名派生 / 每个决策带 tenant |
| 11-12 | **Sitenna MCP Connector** | 「**AI never has more access than the person using it**」+ OAuth + SOC 2 + 每动作记名 + AI 提议工作流、人审批后才建 |
| 11-12 | **嘉信理财 Schwab** | $13.33T / 日均 1070 万笔 / 只读→agentic 下单**但每笔 client in-the-loop** / 仅 Claude pilot / 脚注自认可能不准确 |
| 11-13 | **Swift Media 采购周报** | 「MCP gateways become a procurement requirement」+ 模型停用 30–90 天通知 + Agent FinOps + 保险商问自主动作 + 联邦身份每 agent 一个 client ID |
| 11-10 | **Nacos 3.3.0 / Higress 2.2.6** | 统一 MCP 生命周期（draft/review/publish）+ **运维可控的 MCP 工具导入出站策略** + 默认客户端鉴权 + Spring Boot 4 |
| 近期 | **Amazon Bedrock AgentCore Gateway** | 服务端 tool loop + 全 MCP 原语 + 按调用者身份 dynamic listing + IAM 接管权限 |

**口径修正**：V1.59（11-01）曾记录「Spring AI Alibaba 停更风险、2.0.0-M1.1 近 4 个月无更新」→ 现在 Nacos 3.3 与 Higress 2.2.6 在 11 月密集发版，且**主动把 MCP 注册/导入/权限收进平台层**。修正为：**主仓节奏与周边生态分化；本框架「核心治理层基于官方 SDK、Alibaba 仅可选兼容层（不进主 reactor）」的去耦合定位仍成立，且平台层越强，执行门与私有化合规工件的空隙越清晰。**

---

## 5. Java + Spring + AI 组合的 8 条卖点（可直接进提案）

| # | 卖点 | 支撑证据（本期） |
| --- | --- | --- |
| 1 | **模型之外、任务绑定、fail-closed 的确定性执行门** | arXiv 100% vs 0%；`examples/task-bound-monitor` 51 断言现场可跑 |
| 2 | **升级后存量配置全量复验 + 指纹漂移隔离（可导出报告）** | AWS 官方「升级不改存量」、IBM「Workarounds: None」 |
| 3 | **21 模块逐条对位 RFP 五件套**（工具定义/参数校验/鉴权限权/限流/可观测） | 广发 13322 资质条款 |
| 4 | **多租户：每个决策携带 tenant；导入不越权；受保护字段不可改写** | `CVE-2026-110178` + Permit.io 三判据 |
| 5 | **金融级 HITL 与可导出审计**（人工确认是流程，执行门才是边界） | Schwab 每笔 in-the-loop、Sitenna 每动作记名、Swift Media 可导出审计 |
| 6 | **存量 Java / Spring / OT 资产 MCP 化**（SECS/GEM、MES/FDC、遗留 J2EE） | 凯世通半导体 JD、Jobgether OT/ICS 岗、Nacos 零代码 HTTP→MCP |
| 7 | **私有化 / 等保 / 密评工件 + 全 Java 栈**（可离线、可审计、不被云厂商锁定） | 云网关把治理锁在 Enterprise 付费墙；广发要求广州本地团队 |
| 8 | **开源影响力即信任状**：四期连续可编译、200+ 断言、Apache-2.0、每期实测记录 | 本仓库 V1.69~V1.72 的 `examples/` 与 release notes |

---

## 6. 行动清单（明天 / 下一交易日）

| 优先级 | 动作 |
| --- | --- |
| 🔴 最紧 | **13322 11-15 17:30 剩 1 天**：12 项验收清单 + 51 断言输出 + 四 Gate 接入点图 → 1 页 PDF，交潜在广州本地联合体主体；核资质，不足则只做技术分包 |
| 🔴 | **回广发采购平台核实 13327 报名截止与材料清单**（官方正文无截止，不得凭编号推断） |
| 🟠 | 发 V1.72 SEO 稿抢「in-prompt 护栏 100% 失守 / IBM ContextForge 双 CVE / 广发研究 MCP 上线 / 确定性执行门」流量 |
| 🟠 | 按 `gate-integration-map` 分期①把 Gate A + D2 接进 `mcp-registry` / `mcp-governance`（独立分支，先跑通 83 断言） |
| 🟡 | 军队 190 万 11-16 / 中山 399 万 11-17 / 山东重工 11-20 备「治理+网关+等保」素材 |
| 🟡 | 跨期待办：`exportPrometheus()` 挂 actuator、`dataClassification` 差异化审批、重定向逐跳 SNI 证书校验、Spring AI 2.0 独立分支评估 |
| 🟡 | 跟踪兴业 / 北京农商 / 上海农商正式招标文件；跟踪广发 13321/13322 结果公示与 13327 截止 |

---

## 7. ⚠️ 诚实边界（每期必写）

1. **本框架不受** IBM ContextForge / AWS mcp-gateway-registry / VS Code / git server / pgEdge / llm 等第三方 CVE 直接影响；它们是论证控制点必要性的公开实证。
2. 四个 Gate 全在 `examples/` 层，**未接入主模块**；`TaskGrant` 的 `CONTROL_PLANE` 是字符串标志非密码学签名；连接路径只做字面量主机检查。**不得当生产组件对外宣称**。
3. arXiv 数字是**学术模拟实验**（Qwen2.5 小模型、13 任务 12 工具），证明控制点位置的重要性，**不是本框架实测拦截率**。
4. 广发四份为**预征集 ≠ 采购邀请**；本项目 7 项资质全不满足、无企业主体 / 商业合同 / 中标 / 金融案例 / 等保测评 → 建议联合体技术分包，**禁虚构材料**。
5. 全部报价为**公开挂牌价 / 公开薪资区间 / 中标候选公示**，非成交价、非本项目收入；「大模型大单 218 万」等**未点名 MCP，不计入可投清单**。
6. Schwab 当前仅接 Claude、仅零售客户、20 席 $4,800 门槛排除小 RIA；「打破 Anthropic 独占」为分析师判断非官方结论。
7. OpenAI×Upwork 研究是**相关性分析**，不构成「加 AI 导致预算上涨」的因果结论；固定价预算 ≠ 到手收入。
8. Spring AI 2.0 **未执行升级**；Nacos/Higress 版本信息来自二手站点整理，投标前须回官方 Release Notes 复核。

---

## 8. 来源（18 源）

1. arXiv / MindPattern 11-12：https://mindpattern.ai/s/2026-11-12-approval-prompts-and-repo-config-never-kept-anyone-safe-and-three-mcp-patches-prove-i
2. IBM `CVE-2026-110194`：https://www.ibm.com/support/pages/node/7296696
3. IBM `CVE-2026-110178`：https://www.ibm.com/support/pages/node/7296701
4. AWS `CVE-2026-125926`：https://cybersecurity-see.com/aws-issues-warning-about-mcp-gateway-registry-vulnerability-that-could-enable-attackers-to-access-internal-network-services
5. AWS NVD 时间线 / 版本复核：https://intel.threadlinqs.com/threat/TL-2026-5928
6. Permit.io 控制面授权：https://www.authorizon.com/blog/mcp-gateway-security
7. 广发研究 MCP & Skills 上线：https://finance.sina.com.cn/stock/estate/integration/2026-11-13/doc-iniyqsna7995492.shtml
8. 广发 13327 官方原文：http://gfjc.gf.com.cn/gyszjgg/13327.jhtml
9. 大模型大单中标候选（11-12）：https://finance.sina.com.cn/roll/2026-11-12/doc-iniymsif1275758.shtml
10. Schwab 官方（Yahoo Finance / Business Wire）：https://finance.yahoo.com/technology/ai/articles/schwab-announces-platform-enhancements-including-133000925.html
11. Schwab RIA 侧细节：https://www.privatewealthdaily.com/story/schwab-starts-mcp-server-pilot-with-claude-only
12. Sitenna MCP Connector：https://www.einpresswire.com/article/956026427/sitenna-connects-the-world-s-leading-ai-tools-to-infrastructure-project-and-asset-management
13. Swift Media 采购周报：https://swiftmedia.ca/blog/ai-headlines-for-business-november-13-2026
14. Jobgether AI Platforms JD：https://rolesprint.io/jobs/bGV2ZXI6am9iZ2V0aGVyOmJkNzYzMjY1LTc1OWMtNGY4OC1iZjBlLTgxOWM0OTA4NDJiNQ
15. 凯世通半导体 JD：https://m.liepin.com/lptjob/86549425
16. OpenAI×Upwork 联合研究（QUASA）：https://quasa.io/stories/ai-freelance-projects-carry-bigger-budgets-but-barely-higher-hourly-rates
17. Upwork / Fiverr / Freelancer 费率三源（11-12）：https://technorious.com/upwork-fees ｜ https://jobgader.com/blog/remote-freelancing-jobs-2026 ｜ https://www.teamdigiworks.com/blog/comparisons/upwork-virtual-assistant
18. Nacos 3.3 / Higress 2.2.6 / AgentCore / pgEdge CVE：https://www.besthub.dev/articles/nacos-3-3-ai-agent-registry-mcp-lifecycle-dns-discovery-spring-boot-4-support-08692e1dda4c ｜ https://git.hiwiki.org/zh-CN/repo/higress-group/higress ｜ https://theagenttimes.com/agents/article/amazon-bedrock-agentcore-gateway-expands-into-full-enterpris-8e190323 ｜ https://threatmonitor.io/cve/CVE-2026-126197

---

_V1.72 · 2026-11-14 · 情报仅供商务判断，不构成投资建议或商业承诺。_
