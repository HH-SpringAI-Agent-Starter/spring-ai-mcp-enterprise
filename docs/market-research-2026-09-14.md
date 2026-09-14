# MCP 市场雷达 · 2026-09-14

> 数据来源：web_search（freshness=week，覆盖 2026-09-10 ~ 09-14）。聚焦：企业招人、自由市场报价、平台/资本动态。
> 当晚产品对应：**V1.25 MCP Federation Gateway（联邦 MCP 网关）** —— 正好命中本周最强需求线「聚合/联邦多个 MCP Server」。

## 一、企业在招 MCP 人才（近一周活跃 JD 与重复信号）

| 公司 | 岗位 | 地点/形式 | 薪酬 | 与本项目契合点 |
|---|---|---|---|---|
| **MintMCP** | Software Engineer（MCP Gateway 产品） | 美国 SF/远程 | 未公开（融资机构背书） | **本周最强信号**：产品本身就是 **MCP Gateway**——"governed access to company data and tools；把 agent 的权限 scope 到任务所需最小集 + 审计"。50+ 付费客户（Coursera/Arlo/Braze），Cowboy Ventures + Coatue 背书，天使含 **Andrej Karpathy / Jeff Dean**。JD 明确要求"做过 MCP Server / agentic framework 开源贡献优先"。**V1.25 联邦网关 = 同类能力的企业级实现** |
| **Sumo Logic** | Staff SWE – Core AI Platform（MCP & Agent Infra） | 美国 | **$207K–243K/年** | JD 原文要求 "**federating with external and third-party MCP servers**" —— 联邦托管 + 容错/重试/幂等 + rate limits/backpressure + 多租户 + OAuth + OpenTelemetry。**与 V1.25 联邦网关 + V1.24 可观测性 + 既有 auth/tenant 模块一一对应** |
| **NTT DATA** | MCP and Enterprise Integration Engineer（Senior IC） | 印度 Hyderabad/Noida | 高级档 | Java/Python/TS；OAuth2/OIDC/mTLS/服务身份/最小权限；red flags 明确写"只消费过 MCP 工具、没写过 server 的不要"——**本仓库就是「写过 server」的可验证证据** |
| **OneSeven Tech** | Senior Backend Engineer – MCP Infrastructure | 远程（拉美），美东时区 | **$4,000–5,000/月**（长期合同，Deel 付款） | **Java + Spring Boot + WebFlux + SQL Server**，给美国中量级软件公司建 MCP 层。要求提供 GitHub 仓库证明。**这就是用户 Java+Spring+AI 组合的可直接投标标的** |
| **Cotality（CoreLogic）** | Senior SWE（MCP Servers）+ Apigee X | 美国 Irvine CA | **$10,725–13,333/月**（$128.7K–160K/年），截止 **2026-09-16** | MCP server 暴露内部 API/数据集 + API 网关（Apigee）治理 + OAuth/JWT/API Key/数据掩码。**「MCP Server + 网关 + 安全策略」与我们全套模块重合** |
| **Descope** | Senior SWE, MCP | 以色列 Tel Aviv（混合） | 未公开（$88M 种子） | MCP server/client 集成 + OAuth/JWT + SOC2/GDPR 合规，要求**开源 MCP 贡献优先** |
| **adidas** | Senior AI Platform Engineer – MCP & Agentic Infra | 西班牙 Zaragoza | 欧盟资深档 | MCP + A2A + RAG + Entra ID；"building and governing enterprise AI capabilities based on MCP"（09-04 发布，仍在招） |
| **GuidePoint Security** | Innovation Engineer（MCP client/server 架构） | 美国远程 | 未公开 | 安全咨询公司也要 MCP 架构师——**安全合规赛道对 MCP Server 有增量需求** |
| **前沿 LLM Lab（匿名）** | AI Software Engineer（MCP Development / AI R&D） | 北美+LATAM 远程 | **$50–70/小时**（合同，40h/周） | 给大模型公司建 MCP 工具 + "MCP Toolbox for Databases" 开源维护 |
| **Upwork 官方** | MCP Expert（RL 环境构建） | 全球远程 | **$60–120/小时** | 用 Java/Python/C++/Go/TS/Rust 训练 agent 用 MCP 工具的评测环境，招 50 人，1–3 个月 |

**结构性观察（本周新增）：**

1. **「联邦/聚合 MCP」第一次出现在 JD 原文里**：Sumo Logic 明确要 "federating with external and third-party MCP servers"；MintMCP 整家公司就是做 MCP 网关的。**“一个入口、聚合所有上游 MCP” 已是平台公司的明确需求** → 今晚 V1.25 联邦网关是对这条需求线的直接工程回应。
2. **资本进场**：MintMCP（Cowboy Ventures/Coatue + Karpathy/Jeff Dean 天使）验证 MCP 网关是 2026 下半年的融资热点方向；Snowflake 上一周收购 Natoma（MCP 控制面）同向。
3. **Java MCP 岗位持续增多**：OneSeven（Java+Spring+WebFlux）、NTT DATA（Java 首选）、Cotality（Java 列表内）、Upwork MCP Expert（Java 在列）——Java 生态缺口不是孤例，已成批次。
4. **放宽地理限制的远程合同价**：OneSeven $4–5K/月（拉美远程）、前沿 LLM Lab $50–70/h（北美+LATAM）——**中国远程开发者可用同等技能、以竞价的腰斩价切入**。

## 二、自由职业 / 外包报价（可直接对标的价位）

| 渠道 / 供应商 | 报价 | 说明 |
|---|---|---|
| **OneSeven Tech（美资代理）** | **$4,000–5,000/月** 长期合同 | Java+Spring+WebFlux 建 MCP 基础设施；明确要 GitHub 仓库证明——**竞争力公式 = 仓库即简历** |
| **前沿 LLM Lab（Upwork 转包）** | **$50–70/小时**（40h/周） | MCP 工具开发 + 开源库维护，N.A./LATAM 远程 |
| **Upwork MCP Expert** | **$60–120/小时** | 构建 MCP RL 评测环境，Java 允许，1–3 个月 |
| **Owlab（Upwork 店铺）** | 固定价 **$300 / $1,500 / $3,000** | 企业 MCP 集成三档（2/5/10 天交付）——**固定价最低档已被打到 $300，纯 Python 集成卷价；Java 企业级仍是蓝海** |
| **印度市场基线（上周）** | $2,000–12,000/月 | 维持不变 |

**结论：Java+Spring 的 MCP 基础设施合同（$4–5K/月档）比纯 Python 集成（$300 起）单价高一个数量级，且竞争者少。用户应主攻「企业 MCP 网关/联邦」型订单，避开「拼 Python 脚本」型红海。**

## 三、招标 / 采购 / 平台动态

| 事件 | 规模 | 启示 |
|---|---|---|
| **MintMCP 融资曝光** | 50+ 付费客户，Coursera/Arlo/Braze | MCP 网关赛道已被资本验证，**Open Source 网关实现 = 名片** |
| **Cotality（CoreLogic）MCP Server 岗截止 09-16** | $128.7K–160K/年 | 地产数据巨头也要 MCP Server + API 网关（Apigee）组合 |
| **Sumo Logic 明确「联邦式 MCP 托管」** | $207K–243K/年 | 联邦/聚合成为平台岗硬性技能 |
| **Gartner：2026 年 75% API 网关集成 MCP**（上周） | 持续 | 网关是 MCP 落地中枢，V1.25 站位正确 |

## 四、用户 Java + Spring + AI 组合的卖点（本周更新版）

1. **「Java MCP」正在从蓝海变成刚需**：OneSeven/Cotality/NTT DATA 三家同时要 Java 系 MCP 工程师，且都要求「GitHub 仓库证明」。本仓库（含联邦网关、A2A、Skill Registry、可观测性）是**市面上少见的 Java 系 MCP 企业级全家桶**，简历首行即可用。
2. **「联邦网关」= 平台岗的入场券**：Sumo Logic 要 "federating external MCP servers"，MintMCP 产品即网关。V1.25 让仓库具备**聚合多个上游、统一治理**的演示能力——POC 现场用 docker compose 起两个上游 + 网关，视觉与说服力远超 PPT。
3. **可报价交付形态（更新）**：
   - 对标 OneSeven：**$3–5K/月** 承接 Java MCP 基础设施合同（我们成本结构更低，有 30%+ 议价空间）；
   - 对标固定价：**$8K–20K** 交付「联邦网关 + 2–3 个上游接入 + 治理配置」build engagement（取 $15K–60K 市场价的低段，靠开源框架摊薄成本）；
   - 只做 Python 集成红海（$300–3K）不碰。
4. **MintMCP 类公司是潜在「被聘 or 被收购技术参考」**：若走雇员路线，JD 明确偏好 MCP 开源贡献者；本仓库的 mcp-gateway 提交记录就是最硬的材料。

## 五、行动建议（本周）

1. **本周内**把 V1.25 联邦网关 + V1.24 Grafana 看板做成一段 **3 分钟 POC 录屏**：两个上游（如本地 tool-weather + 一个 Python FastMCP）+ 网关统一入口 + 管理 API 刷新演示；
2. **对标 OneSeven 投递**（远程、要 GitHub）：用本仓库 + 「联邦网关」commit 作为主材料；
3. 关注 **Cotality 09-16 截止** 与 Sumo Logic 岗位，准备英文能力映射表（已有 `docs/enterprise-rfp-checklist.md` 可复用）；
4. 把「联邦网关」写进提案模板与掘金/CSDN 稿件（本日晚间稿件已交付 `docs/blog-java-mcp-federation-gateway-2026-09-14.md`）。

---

_生成：2026-09-14 · 来源：web_search（week）_