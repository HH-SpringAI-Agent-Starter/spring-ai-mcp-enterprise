# MCP 市场雷达 · 2026-09-13

> 数据来源：web_search（freshness=week，覆盖 2026-09-10 ~ 09-13）+ 长期趋势库。聚焦：企业招人、项目招标、自由市场报价、平台动态。

## 一、企业在招 MCP 人才（近一周活跃 JD）

| 公司 | 岗位 | 地点/形式 | 薪酬 | 与本项目契合点 |
|---|---|---|---|---|
| **Caterpillar（卡特彼勒）** | Senior Java Developer – **Agentic AI Squad Lead** | 美国 Irving, TX | 资深档 | ⚠️ **申请截止日 = 2026-09-13（今天）**。JD 要求：Advanced Java + **Spring/Spring Boot** 微服务、RESTful API、把 AI Agent 当作交付团队一员、编排 agentic AI 能力。**Java + Spring + Agent 编排** 完全对口 |
| **沃尔玛（中国）** | 高级 AI 平台/应用开发工程师（AI/MCP 网关） | 中国 | **￥30,000–55,000/月** | JD 简直为本项目画像：AI/MCP 网关、MCP Server、Skill/Spec Registry、鉴权/限流/熔断/灰度、Nacos 注册中心；加分项「开源社区贡献（Nacos/Higress/**Spring AI 生态优先**）」 |
| **Sumo Logic** | Staff Software Engineer – Core AI Platform（**MCP & Agent Infra**） | 美国 | **$207K–243K/年** | MCP-first 平台、**联邦式 MCP Server 托管**、工具调用容错/重试/幂等、**rate limits/quotas/backpressure**、多租户隔离、OAuth、可观测性（OpenTelemetry）——与 mcp-core/mcp-tenant/mcp-monitor/mcp-auth 一一对应 |
| **adidas** | Senior AI Platform Engineer – **MCP & Agentic Infrastructure** | 西班牙 Zaragoza | 欧盟资深档 | 发布 2026-09-04。要求「创建并**加固 MCP Server（用网关）**」、企业 Agent 平台、可观测性与安全护栏、AWS Bedrock AgentCore。**「MCP Server + 网关 + 安全」正是我们的卖点** |
| **Descope** | Senior Software Engineer, MCP | 以色列 Tel Aviv（混合） | 未公开 | 做 **MCP 认证授权产品**：MCP Server/Client 集成、OAuth/JWT、企业安全合规（SOC2/GDPR）——对应 mcp-auth / OAuth2+EMA |
| **Lifted（Upwork 旗下）** | Software Engineer（MCP 服务） | 远程（拉美，美中时区） | 全职合同 ~40h/周，至 2027-03 | **Java 优先**，构建/运营 MCP 服务、企业上下文只读暴露 |
| **Intellias** | Python MCP Engineer（Core Architecture） | 波兰（远程） | 欧盟承包价 | 用 FastMCP + **Streamable HTTP** + OpenTelemetry 建企业 MCP Server；AWS Lambda/API Gateway——验证「**Streamable HTTP + 可观测性**」是当前企业标准诉求（我们已具备） |
| **CAI（$1.3B 全球服务商）** | Python MCP Developer | 远程（印度/全球） | 承包 | FastMCP + Streamable HTTP + OAuth2/OIDC + AWS；把企业 API 包成 MCP 工具、注册到 **AgentCore Gateway** |
| **MintMCP** | Software Engineer | 美国（远程） | 未公开 | 企业 MCP 治理平台（**MCP Gateway + Agent Monitor**：托管连接器、集中凭证、RBAC、实时可观测、审计、策略护栏）——**我们 mcp-registry + mcp-auth + mcp-monitor 的对标产品** |

**结构性观察（本周新增）：**

1. **「MCP 网关 + 治理」成为岗位主旋律**：沃尔玛（中国）、Sumo Logic、adidas、MintMCP 四家 JD 同时指向「MCP Server/网关 + 鉴权 + 可观测 + 多租户」。这正是本项目的模块编排（core/auth/tenant/registry/monitor）。
2. **Java 岗位开始明确出现**：Caterpillar（Java+Spring+Agent Lead）、Lifted（Java 优先）、Tavant（Java MCP），本周「Java + Agent/MCP」同时出现在多个 JD，说明 Java 生态的 MCP 缺口正在被企业用工需求「证伪」。
3. **企业把「可观测性」写进硬性要求**：Sumo Logic/Intellias/adidas 都把 tracing/metrics/alerting 列为必须。→ **今晚 V1.24 交付的 Prometheus 告警 + Grafana 看板，正对着这条需求线。**
4. **印度市场持续放量**：Shine.com 显示 9 月 **1,158 个 MCP 相关职位**；WF Next 印度资深 MCP 开发者 **$7,000–12,000/月**（全包）。

## 二、自由职业 / 外包报价（可直接对标的价位）

| 渠道 / 供应商 | 报价 | 说明 |
|---|---|---|
| **WF Next（印度）** | 资深 MCP 开发者 **$7,000–12,000/月** | Build engagement 4–8 周（固定价交付 v1 MCP Server + eval + 部署） |
| **Empiric Infotech（印度）** | **$2,000/月**（160–172h） 或 **$25/小时** | 专职 MCP 开发者；对比美国自聘 $9.2K–13.3K/月 |
| **固定价 AI 代理商** | **$15,000–60,000** 交付 v1 MCP Server | 「第一个改造单之前」的价格，之后另收维护费 |
| **Mercor（印度 Nashik）** | 最高 **$500/任务** | AI Agent 框架 + MCP 工具编排评估任务 |
| **Upwork 官方（Lifted）** | 全职合同 ~40h/周 | **Java 优先**，MCP 服务方向 |

**结论：MCP Server 交付的「市场公允价」≈ $2,000–12,000/月/人；固定价一次性交付 ≈ $15K–60K。** 若以国内远程/兼职形式承接，可在 $2,000–5,000/月区间取得明显性价比优势。

## 三、招标 / 采购 / 平台动态

| 事件 | 金额/规模 | 启示 |
|---|---|---|
| **火山引擎**中标某公司 AI Coding 大单 | **￥37.74 万**（12 个月模型调用额度） | 企业统一模型网关 + 用量统计 + 安全审计是标配 |
| **Snowflake 收购 Natoma** | 未披露 | MCP 控制面（verified server registry + identity + access policy）成采购战场 → 对应 mcp-registry/mcp-auth |
| **Microsoft Agent 365 / Anthropic MCP Tunnel** | — | 三大巨头 30 天内齐推 MCP 治理，标准未定先抢控制权 |
| **美国 VA（退伍军人部）** MCP Server 临床摘要合同 | ~18 个月 | 政府也在采购 MCP Server 工程，中小企业可参与分包 |
| Gartner 预测 | 2026 年 **75% API 网关厂商集成 MCP** | 网关是 MCP 落地的中枢 |

## 四、用户的 Java + Spring + AI 组合，在该赛道的卖点

1. **「企业后端 90% 是 Java/Spring」**：MCP 生态 Python 占 80%+，Java 几乎空白。用 Java 交付 MCP Server，能**无缝嵌入企业既有 Spring Boot 体系**，不需要企业引入 Python 运行时、不需要跨语言运维。
2. **一套代码覆盖企业治理全家桶**：本项目已把 **RBAC / 限流 / 审计 / OAuth2+EMA / 多租户 / 注册中心 / 可观测性（本版补齐告警+看板）** 做进框架——这些正是 JD 里被反复点名的能力，**POC 现场可 `docker compose up` 直接演示**。
3. **Spring AI 生态的稀缺贡献者身份**：沃尔玛 JD 明确偏好「Spring AI 生态开源贡献」。本仓库（含 Spring AI Alibaba 集成 + Spring AI Tool Bridge）就是最硬的敲门砖，可写进简历首行。
4. **Agent 时代的「基础设施派」**：Caterpillar 要的是「把 Agent 当团队一员」的 Java Lead，本项目的「工具注册中心 + 治理平面」正是让 Agent 在企业里**安全可控地调用工具**的地基，与岗位语境高度一致。
5. **可报价的交付形态**：以本框架为底座，可快速交付「4–8 周 build engagement」（对标 $15K–60K 固定价）或「$2K–5K/月 维护与扩展」，边际成本极低。

## 五、行动建议（本周）

1. 用 V1.24 的 Grafana 看板截图，作为 **POC 演示素材**（视觉冲击力远胜 README）；
2. 针对沃尔玛中国 JD 逐条对标，写一份「能力映射表」放进 `docs/`（下轮任务候选）；
3. 关注 Caterpillar 之后是否再放 Java+Agent 岗，作为「企业在买 Java Agent 能力」的持续证据；
4. 把「$2,000–12,000/月」的价位区间写进报价话术（已有 `docs/proposal-templates-2026-09-06.md`）。

---

_生成：2026-09-13 · 来源：web_search（week）_
