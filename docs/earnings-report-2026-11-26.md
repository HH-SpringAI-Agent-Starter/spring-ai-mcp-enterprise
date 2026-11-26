# 财富机会雷达 · MCP 企业化（2026-11-26 · V1.80 · Task 6）

> 检索窗口：近 3 天（11-23~11-26）+ 必要历史锚；全部价格/薪资为公开挂牌价或预算带，**非成交价**；未点名 MCP 的标不计入可投清单。

## 1. 本期六大信号

| # | 信号 | 内容 | 对我的含义 |
| --- | --- | --- | --- |
| S1 ⭐⭐ | **MCP 生态规模坐实**：[Anthropic 11-25 数据（新浪财经 11-25 转述）](https://cj.sina.com.cn/articles/view/8330648469/1f08b9b9507201dkg4) | MCP 相关工具包**月下载量超 7 亿次**、Claude 可用 MCP 连接器**破 1 万个**；2025-12 捐给 Linux 基金会 Agentic AI 基金会（Google/微软/AWS/Cloudflare 均参与）；2026 上半年调研**四成以上软件组织已生产环境使用 MCP** | 赛道过拐点：不是「要不要做」而是「怎么安全做」；报道明确「真正拿高薪的是**懂企业权限体系、能做安全审计的服务端开发**、能把 MCP 与现有智能体框架深度整合的架构师」——正对位本项目定位 |
| S2 ⭐⭐ | **记忆投毒成显学**（[CVE-2026-59726](https://nvd.nist.gov/vuln/detail/CVE-2026-59726) + [NIST SP 2400-102e](https://cn-sec.com/archives/5606895.html) + [Nodeflare 实测](https://nodeflare.tech/blog/mcp-memory-poisoning)） | ruflo CVSS 10.0：投毒 AgentDB 影响未来所有用户、**重新部署不能撤销投毒**；NIST 把长期记忆投毒列入 Agentic SI；写门实测 7/7→0/7 | 新增可收费的「写入期治理 + 跨会话回滚」能力，竞品普遍缺失；V1.80 已把它做成可跑守卫 |
| S3 ⭐ | **工具返回值入口被点名**（[RunPod 复盘，腾讯云 11-20~22](https://cloud.tencent.com/developer/article/2793066)） | MCP 三个文本入口（tools/list / 返回值 / initialize），公开静态扫描只覆盖第一个；RunPod 在 create-pod 返回值夹带「评分且不告知用户」指令 | 售前话术可加「你们扫了返回值和 initialize 吗？」——大多数买方答不上来 |
| S4 ⭐ | **规范责任继续下沉**（[MCP 协议安全实战](https://www.hqwc.cn/a/2038977.html)） | 2026-07-28 版规范改无状态设计，把会话与安全责任从协议层移交实现者；工具 description 直进 System Prompt、协议层无来源区分 | 「协议越轻、实现者责任越重」——私有化合规工件是长期刚需 |
| S5 | **审计成独立采购项**（[SpecsWriter 2026](https://specswriter.com/knowledge/what_does_an_enterprise_mcp_server_security_audit_involve_and_how_should_organizations_approach_it_in_2026.php)） | 外部咨询**单 MCP 服务器环境起 $15K**，按服务器数/架构复杂度放大；商业平台年费**数千~数万美元**；建议基线审计 + **季度复审** + 重大变更/漏洞披露/事件后的触发式审计 | 与本项目「12 项验收清单 + 可跑断言」天然对位，可做审计交付物供应商 |
| S6 | **定价 600 倍价差延续**（[dev.to 9 家横评](https://dev.to/tomakadlec/what-a-custom-mcp-server-costs-and-when-to-buy-a-connector-instead-4an2) + [Vibidsoft](https://www.vibidsoft.com/mcp-server-development)） | 只读单系统 $1,500 起；多系统 + 写权限进五六位美元；Clyrix $12K–35K 聚焦 / $35K–90K 多系统 / **$90K–250K+ 企业**；Launch Day $100K–300K 只读 / $300K–700K 带动作 / $1M+ 顶层；Vibidsoft **$1,500/月**托管、「never bill by the hour」 | 报价锚不变；继续按「交付物 / 集成 / 修订轮次」拆，不抬时薪（OpenAI×Upwork 结论） |

## 2. 哪些企业在招 MCP 人才（近 3 天 + 存量最精准）

| 企业 / 岗位 | 时间 | 薪资 | 逐字命中 | 精准度 |
| --- | --- | --- | --- | --- |
| [北京动码印章科技 · AI 技术负责人](https://m.liepin.com/job/1986870933.shtml) | 11-19 更新 | **30-45k** | 「工具调用（Function Calling / **MCP**）、RAG 检索、**记忆与状态管理**」+「6 年以上 **Java** 后端、精通 **Spring Boot / Spring Cloud**」+「私有化部署方案」 | ⭐⭐⭐ **本期最精准**：Java + Spring + MCP + **记忆/状态管理** + 私有化，与 V1.80 主题逐字重合 |
| [小米 · 高级 AI 系统开发工程师（大模型与 RAG，A18742）](https://www.jrzp.com/jianzhi3926174.shtml) | 11-25 | **25-30k**（武汉） | 「设计并实现 Agent 工作流编排框架，支持**工具调用（MCP 协议）**」+「扎实的 **Java** 基础…**Spring、SpringBoot、Spring Cloud**」+「向量数据库集成」 | ⭐⭐⭐ 与 11-15 小米岗同口径复现，需求稳定 |
| [郑州某企 · Java 开发经理](https://m.liepin.com/a/82157629.shtml) | 11-24 更新 | **20-21k** | 「参与 **Agent、RAG、企业知识库、Tool Calling、MCP** 等 AI 应用后端开发」+「Spring Boot/Cloud、8 年 Java」+「Docker / CI/CD」 | ⭐⭐ 传统 Java 团队转 MCP 的典型样本 |
| [河南兜圈子网络 · AI 应用开发工程师全栈](https://xcdqzy.goworkla.cn/module/position_details/id-471361/nid-5074) | 11-24 | 8-10k（校招 30 人） | 「围绕业务场景构建业务 Agent、**MCP 工具与 Skills 体系**」+「Java/Python/Go…Spring Boot」 | ⭐ 校招批量岗，薪资低但说明 MCP 已进入基础教学 |
| [Bybit · AI Agent Architect](https://jobs.codemind.site/jobs/ai-agent-architect-bybit-6b0590c617acd208ed7922e5) | 11-24 | 全球远程（未挂牌） | 「**Expose internal systems as MCP servers**, sub-agents, and agent skills」+「ADLC: Spec → Eval」+ P7/P7+ 6~8 年 | ⭐⭐ 海外远程、强调 MCP server 化内部系统 |
| 存量最精准（延续） | — | 10-18k·14薪 | [上海凯世通半导体](https://m.liepin.com/lptjob/86549425)「封装离子注入机 SECS/GEM、MES/FDC 为**标准 MCP 工具**」 | ⭐⭐⭐ 工业软件 + 半导体设备，最贴合执行环境面 |

> ⚠️ 薪资为招聘挂牌区间非成交价；北京动码印章岗 11-19 更新（非近 3 天新发），但主题与本期高度重合故列入并标注。

## 3. 招标 / 商机（存量跟踪，未点名 MCP 不计入可投清单）

| 项目 | 时间 | 金额 | 状态 / 边界 |
| --- | --- | --- | --- |
| 广发研究 MCP & Skills 智慧数平台 | 11-13 正式上线 | — | 买方**已自建**（5 项 MCP 能力/21 行业/9 万指标），钱从「帮你建」转「帮你管」；13321/13322 已截、13327 正文无截止须向采购平台核实、四份均为**预征集 ≠ 采购邀请** |
| 中原银行 AI 网关 | 存量跟踪 | — | 含 MCP 代理，未正式招标 |
| 工信部五所 | 12-03 | — | 备标窗口 |
| 军队 / 中山 / 山东重工 / 呼市 | 11-16~11-23 | 190 万 / 399 万 / — / 900 万 | 11-20~23 窗口已过或临近，**均未点名 MCP，不计入可投清单**，仅作智能体预算锚 |
| 上海银行绿色金融智能体 85 万 / 会话式 AI 技改 224.97 万中标 | 11-24 存量 | 85 万 / 224.97 万 | 未点名 MCP，预算锚非可投清单 |

> 本期近 3 天**未检索到新的明确点名 MCP 的国内招标公告**；不虚构、不合并认定（延续 V1.74 口径）。

## 4. 价格锚（近 3 天复核）

| 来源 | 价格 | 口径 |
| --- | --- | --- |
| dev.to 9 家横评 | **$1,500 → $1M+（600 倍价差）** | 只读单系统 vs 企业级带动作 |
| Clyrix Digital | $12K–35K 聚焦 / $35K–90K 多系统 / $90K–250K+ 企业 | 预算带非报价 |
| Launch Day Advisors | $100K–300K 只读 / $300K–700K 带动作 / $1M+ 顶层 | 大厂合作 |
| Synchronized Codelab | $15K–120K+ 生产级 | — |
| SpecsWriter（审计） | 外部咨询单环境起 **$15K**，商业平台年费数千~数万美元 | 审计独立采购项 |
| Vibidsoft | 评估 $1,500 起 / 托管 **$1,500/月** / **never bill by the hour** | 固定价里程碑 |
| 存量锚（Upwork / Arc / Lemon.io） | Upwork ML $50–200/hr、Arc $15–110+、Lemon.io LLM 中位 $35/48/55；费率 Upwork 0–15% / Fiverr 20% / Freelancer 10% 或 $5 | 11-24 复核，挂牌价 |

## 5. 我的 Java + Spring + AI 组合卖点（本期更新为 8 条）

| # | 卖点 | 对位证据 |
| --- | --- | --- |
| ⭐1 | **付费点已从「帮你建」移到「帮你管 + 帮你审」**——买方自己已上线 MCP 平台（广发智慧数），采购转治理合规（58.7% 居首），审计独立成项（$15K 起） | S1/S5 |
| ⭐2 | **十二段式收口，每段可现场跑断言**：注解→注册中心→只读→决策层→配置面→结果面→身份→会话→执行环境→出站响应→日志出口→**记忆写入**，30 断言全绿/0 误杀 | V1.80 交付物 |
| ⭐3 | **写入期 + 跨会话回滚是竞品空白**：网关管「谁能调」，本守卫管「写进去的东西明天算不算数」；对位 ruflo「补丁≠清零」 | S2 / E1 |
| ⭐4 | **host provenance + 隔离区 > 正则扫描**：7/7→0/7 实测支撑，「provenance caught what patterns could not」 | S2 / E3 |
| ⭐5 | **Java/Spring 私有化是稀缺组合**：近 3 天最精准 JD（动码印章 30-45k、小米 25-30k）逐字 Java + Spring + MCP + 私有化；国内竞品多为 Go/TS SaaS | 招聘表 |
| ⭐6 | **工业 / 半导体设备场景卡位**：SECS/GEM、MES/FDC 标准 MCP 工具（凯世通），执行环境面守卫可复用 | 存量 JD |
| ⭐7 | **合规工件 + 可审计出口**：12 项验收清单 + 买方必问 5 问 + 三档报价 + 24/48/72h，直接可做审计交付物 | S5 |
| ⭐8 | **报价按交付物拆而非抬时薪**：OpenAI×Upwork「固定价 +52.4% / 时薪仅 +1.5%」+ Vibidsoft「never bill by the hour」双源支撑 | S6 |

## 6. 明天做什么（候选，按优先级）

1. **发 SEO 稿**抢「CVE-2026-59726 补丁不清毒 / NIST 记忆投毒 / Nodeflare 7/7→0/7 / RunPod 返回值指令」流量（稿件已就位：`docs/blog-java-mcp-persistent-memory-2026-11-26.md`）。
2. 把 **12 项记忆面验收清单 + 30 断言输出 + 写门前后对照**压成 1 页 PDF 进售前包；对位动码印章 30-45k JD 逐条写应答。
3. **回广发采购平台核实 13327 报名截止与材料清单**（跨期未决项）；工信部五所 12-03 备标。
4. 独立分支评估把 **CP1 provenance / CP2 quarantine / CP6 rollback** 接进 `mcp-tools` / 记忆服务写路径（先跑通 30 断言再评估合入）。
5. 跨期待办：`exportPrometheus()` 挂 actuator、dataClassification 差异化审批引擎、重定向逐跳 SNI 校验、独立分支评估 Spring AI 2.0。
6. 跟踪：MCP 7 亿下载官方原文（新浪为二手转述须回 Anthropic 核实）、小米/动码印章 JD 是否持续、CVE-2026-59726 修复版 v3.16.3 复验。

## 7. 诚实边界

- 全部价格 / 薪资为公开挂牌价或预算带**非成交价**；招标存量项未点名 MCP 不计入可投清单；广发四份为预征集 ≠ 采购邀请、7 项资质不满足只能联合体技术分包、无企业主体/合同/中标/金融案例/等保测评。
- MCP 7 亿下载为新浪财经转述 Anthropic 11-25 数据，**二手来源须回官方核实**；dev.to / Clyrix / Launch Day 为公开挂牌价。
- OpenAI×Upwork 为相关性分析非因果；Spring AI 2.0 未升级；本框架不受 ruflo CVE 直接影响。
