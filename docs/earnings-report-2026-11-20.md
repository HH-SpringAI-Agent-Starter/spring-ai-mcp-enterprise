# MCP Server 挣钱情报日报（2026-11-20 · 近 3 天实时抓取）

> 任务 6 完整输出：企业需求 / 招标 / 招聘 / 开源动态 / 价格 / 用户 Java+Spring+AI 卖点。
> 抓取窗口 11-17~11-20，来源 18 项，全部附链接。⚠️ 报价为公开挂牌价/估算，非成交价。

## 一、今日最强信号（TOP 6）

1. ⭐⭐ **会话 / 传输层成为本周 CVE 主战场** —— `CVE-2026-132121`（Supergateway stdio↔WS 无 Origin 校验，恶意网页跨站 WebSocket 劫持本机 MCP 网关，PoC 11-17 **12/12 runs 外传本地文件**）+ `CVE-2026-131857`（prototype-inherited sessionId 打崩网关）+ `CVE-2026-130007`（k8s-mcp-server 11-19 NVD：sessionId 仅 correlation value，`POST /message` 无 authorization/Origin 即派发工具，**read-only 默认关闭**，修 1.1.0）→ 直接坐实本期第八段「会话 / 传输绑定面」（[eyeon.ai](https://eyeon.ai/f/3883) / [ThreatMonitor](https://threatmonitor.io/cve/CVE-2026-130007)）。
2. ⭐⭐ **`CVE-2026-48710` BadHost 机制复验**（Starlette，CVSS 6.5 被批低估）：Host 注入 `?` 使 URL 重解析边界漂移，`/admin` 被当 query 绕过鉴权中间件；周下载 3.25 亿，影响 FastAPI / vLLM / LiteLLM；**MCP 规范强制的免鉴权 OAuth discovery 端点使其成为可靠利用路径**（[The Agent Times](https://theagenttimes.com/agents/article/badhost-vulnerability-bypasses-authentication-across-our-mcp-879a13b7)）。
3. ⭐ **`CVE-2026-115563` MCP Python SDK OAuth 凭据错投**：client 把凭据发给 server 自选 AS，issuer 未逐 path 校验、凭据未绑定 AS；修 2.2.0/1.30.0，**且必须显式传 `issuer=` 才生效，升级不等于修好**（[agentbeam](https://agentbeam.com/cve/cve-2026-115563)）。
4. ⭐ **企业侧「MCP + 流程软件 = 确定性执行」成共识**：稀土掘金 11-19《多个采购智能体如何协同工作》——智能体经 API / MCP 调用业务服务，**由流程软件承接规则校验、审批和记录**，让智能分析与确定性执行配合；自动下单 / 异常处置仍遵循企业采购规则（[掘金](https://juejin.cn/post/7709228455338491939)）→ 与本项目「模型外确定性执行门」论断同构。
5. ⭐ **天眼查 MCP 11-19 上线 ModelScope**：企业工商 / 股权穿透 / 风险诉讼 / 招投标数据接入智能体，面向 KYB / 尽调 / 风控 / 供应商评估；同日标讯平台把「101 个数据接口经 MCP 输出 + 私有化部署」写成产品卖点（[ModelScope](https://www.modelscope.cn/mcp/servers/TianYanCha/tyc-mcp) / [网易](https://www.163.com/dy/article/LC1C7VA80519CSH4_pdya11y.html)）。
6. **治理成为标准采购语言**：Unosecur / SpecsWriter / NexusVoid 三份 2026 best-practices 一致要求 —— mTLS + 短时令牌 + **每次调用经网关 per-call 授权**、审计留存 ≥180 天、第三方 MCP server 沙箱化、绝不裸奔公网（[Unosecur](https://www.unosecur.com/security-101/mcp-security-best-practices) / [SpecsWriter](https://specswriter.com/knowledge/what_is_the_definitive_mcp_security_best_practices_checklist_for_enterprise_ai_deployments_in_2026.php)）。

## 二、哪些企业在招 MCP 人才 / 用 MCP（本期具体主体）

| 主体 | 事实 | 对位 |
| --- | --- | --- |
| **Mirantis**（K8s 原生 AI 基建，美远程） | Senior SWE Integrations & AI Agents：逐字「**develop and maintain MCP servers and connectors providing agents secure access to internal systems**」+ Java/Spring 生态 + OAuth2 scopes / least-privilege / PII / audit logging + LLM gateways 成本监控 | Java+Spring+MCP 最精准海外 JD |
| **外企德科（上海，11-18）** | AI 大模型应用开发 **2.5–5 万·16薪**：逐字「**MCP + Skills：负责企业级 MCP/Skill 建设**」+ Sandbox 安全隔离 + 熟悉 MCP/Skills/多 Agent 协作 + 熟悉沙箱隔离/代码安全扫描/企业数据安全治理加分 | 高薪 + 安全治理双命中 |
| **猎聘研发工具链岗（上海嘉定，12-20k）** | Java 外派岗，逐字「研发工具链 **MCP 集成**、Skill 开发、智能助手」+ Jira/Codebeamer 二开优先 | 存量 Java 工具链 MCP 化 |
| **苏州 Robotaxi Java 岗（50-70k·14薪）** | 逐字「**AI Agent 开发、MCP 集成与工程化**」+ 高并发分布式 + 车云一体化 | 高薪岗位已把 MCP 写进核心职责 |
| **长沙全栈（20-26k·16薪，今日更新）** | Java 服务端 + RAG/Agent/内容生成/智能审核，Spring/Spring Boot 实战 | AI 工程化下沉二线城市 |
| **天眼查 / 标讯平台（11-19）** | 企业数据 MCP 化 + 「MCP 协议输出 101 数据接口 + 私有化部署」 | 数据即服务 + 私有化 |

## 三、哪些项目在招标 / 什么价

| 项目 | 金额/截止 | MCP 关联 | 可投性 |
| --- | --- | --- | --- |
| 浙江工商大学智能体基础服务架构设备采购（11-18） | **130 万**，12-10 开标，不接受联合体 | 智能体基础服务架构（公告正文未点名 MCP） | ⛔ 未点名 MCP，**不计入可投清单**，仅作高校智能体预算锚 |
| 广发四份征集（态势感知/资讯数据/企业微信/AI 运维） | 13321 11-13 截 / 13322 11-15 截 / 13327 官方正文无截止须核实 | 五件套 + MCP 安全工具管理 + MCP 封装接入 | 预征集≠采购邀请；7 项资质不满足 → **只能联合体技术分包** |
| 山东重工集团级大模型平台（二次） | 11-20 截，潍坊，接受联合体 | 统一入口/服务/管理/**安全管控** + 智能体平台 + 大模型安全护栏 | 制造业集团级治理卡位（今日截止，核对是否已过） |
| 中原银行企业级 AI 网关 | 服务期 14 个月，1 家中标 | **AI 网关 + MCP 代理**写进正式公开招标 | 金融 MCP 网关主标位 |
| 国家管网 4089 万 / 河北政采 48.9 万 | 11 月 | **未点名 MCP** | ⛔ 不计入可投清单，仅作智能体预算锚 |
| 重庆 3.4412 亿 / 中央民大 3712 万（11-25）/ 呼市 900 万（11-23） | 已定标 / 倒计时 | 含智能体应用场景 | 分包与案例背书窗口 |

## 四、什么价 —— 接单定价锚（本期延续 + 口径复核）

| 来源 | 价格 | 口径 |
| --- | --- | --- |
| **外企德科（上海，11-18）** | AI 大模型应用 2.5–5 万·16薪 | 国内高薪锚（含 MCP+Skills + 安全治理） |
| **猎聘（本期）** | 研发工具链 Java 12-20k / 苏州 Robotaxi Java 50-70k·14薪 / 长沙全栈 20-26k·16薪 | Java+MCP 岗位带宽 12k–70k |
| **Mirantis（美远程）** | 未标薪（Senior SWE，Fortune 500/Global 2000 客户） | 海外 JD 锚，secure agent infrastructure 职责 |
| **WebsNexus**（MCP server 定制） | 单系统只读 **$299 起**；多系统写+OAuth+审计 **$899 起** | 公开挂牌 fixed quote |
| **Innovation Insight**（AI agent 工程师外包） | mid $3,600–4,800/月、senior $4,800–6,600/月、lead $6,600–7,800/月 | 全包月费 |
| 历史定价锚（V1.67/11-09） | 生产级 $80K–220K；企业网关 $250K 起；Dawloom 7 档 $3,700–$72,000（19 倍价差） | 「涨的不是代码量，是治理层」 |

→ **报价策略**：按交付物/集成/修订轮次/工时拆（OpenAI×Upwork 固定价预算 +52.4%、时薪仅 +1.5%），别只抬时薪；报价 10 倍定律对位 21 模块治理层。

## 五、用户的 Java + Spring + AI 组合在该赛道的卖点（8 条）

1. **存量 Java 权限复用**：企业已有 Spring Security / RBAC / 数据归属体系，本框架在 MCP 调用点复用，不需重建身份体系——正对 Mirantis「OAuth2 scopes / least-privilege」与掘金「流程软件承接审批」。
2. **确定性执行门**：相对「提示词护栏 100% 失守 / Skill 准确率 95% 仍需人工把关」，模型外确定性门是可验收边界（八段式）。
3. **会话 / 传输绑定面首发能力**（本期）：CP1–CP6 对位 Supergateway 132121 / k8s 130007 / BadHost 48710 / Python SDK 115563，是同质化 MCP server 里的差异化卖点。
4. **私有化合规工件**：等保/密评/备案 + 审计留痕 ≥180 天 + 字段脱敏 + 第三方 server 沙箱化，Python 系弱项、Java 主场（「不能上云的钱最好赚」）。
5. **Spring AI Alibaba 兼容层**：用户主技术栈，Nacos 3.3.0 MCP 生命周期 / Higress v2.2.6 周边生态密集发版，核心基于官方 SDK、Alibaba 可选不进主 reactor。
6. **八段式可演示样品**：`examples/` 下 10 组纯 JDK 守卫，javac + main 自检全绿，售前可现场跑、进标书可当「可演示案例」。
7. **定价权锚**：治理层 10 倍价差 + WebsNexus $299/$899 + 生产级 $80K–220K，把「写个 server」抬到「交付治理层」。
8. **AAIF 规格变更持续复验**：MCP/Agent Skills 归 Linux Foundation 中立治理，规格变更无厂商补丁通知——本框架把「规格版本当受监控依赖 + 变更后全量复验」做成年度托管服务。

## 六、明天候选

1. 发 SEO 稿抢「Supergateway 132121 / k8s-mcp 130007 / BadHost 48710 / 会话绑定面」流量。
2. 12 项会话面验收清单 + 26 断言输出压 1 页 PDF，交广州本地联合体主体。
3. 评估把 CP1 / CP2 / CP3 / CP5 接进 `mcp-gateway`（独立分支先跑通 26 断言）。
4. 备标中原银行 AI 网关；回广发平台核实 13327 报名截止。
5. 跨期待办：`exportPrometheus()` 挂 actuator、dataClassification 审批引擎、重定向逐跳 SNI 校验、独立分支评估 Spring AI 2.0 升级。

## 七、来源清单（18 项）

见正文各段超链接，覆盖：eyeon.ai、ThreatMonitor、The Agent Times、agentbeam、稀土掘金、ModelScope、网易、Unosecur、SpecsWriter、NexusVoid、kickstartremote（Mirantis JD）、智联招聘（外企德科）、猎聘（三条 JD）、WebsNexus、Innovation Insight、Datamata、Upwork 官方、广发智慧数平台。
