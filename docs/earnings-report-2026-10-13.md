# 💰 每日收益 / 招标 / 招聘情报 — 2026-10-13

> 自动任务「🌙 MCP Enterprise 每日开发·21:30」产出。口径：近 3 天（10-10 → 10-13）实时 web_search 抓取，全来源带链接。
> 项目状态：**V1.40 稳（21 模块）**，本日核心增量 = 挣钱情报（Task 6，高优先级）；代码侧 1–5 早已交付并通过 CI，**本期无破坏性代码变更**。

---

## 🔥 TL;DR（今天最该盯的 5 件事）

1. **ZC26G280436「AI网关、MCP注册、多模态平台」10-20 09:00 截标仅剩 7 天**——最强「MCP 注册中心」对标信号仍活跃，本框架 `mcp-registry`+`mcp-gateway`+`mcp-alibaba` 全套命中，投标模板/等保对照表/安全简报已就绪。
2. **Spring AI Alibaba 生态本周官方化落地**：Nacos 3.0 发布 **MCP Server 自动注册与发现手册**（`starter-mcp-registry >= 1.0.0.3`，@Tool 方法启动即注册 Nacos，工具描述/参数运行期热更新、工具运行期启停），Higress 升级 CNCF Sandbox v2——**官方只给"注册+路由"，本框架的 RBAC/审计/HITL/Scope/多租户治理层仍是空白，定位"官方方案的治理增强层"更稳**。
3. **招聘清一色点名 Java + MCP**：Cotality（达拉斯）MCP Servers 资深工程师、EPAM（印度）AI Native Java 工程师、InAddition（台北）Java 实现 MCP Server、沃尔玛中国 30–55K、实习僧 18–30K、猎聘多岗位——**仓库即作品集，投这些岗直接贴本框架链接**。
4. **CVE 集群在 10 月持续放量，认证≠授权护城河再次被验证**：CVE-2026-104850（官方 TS SDK OAuth issuer 绑定，7.5）修复有缺口（升级不等于安全）、Cycode 曝 MCP Python SDK OAuth 账户接管、CVE-2026-59822 仍居 CISA KEV——安全事件简报可作标书合规加分。
5. **Upwork 官方 MCP Server 已成接单基础设施**：agentic hiring 让 AI 先读你的 profile 再决定短名单，**结构化 profile（技能标签/费率/类目/履历）比三段自我描述更重要**——本框架 repo 就是最好的结构化作品集。

---

## 📑 招标 / 采购（本周活跃截标，对齐交付）

| 标包 | 金额 | 截标 | 对齐模块 | 状态 |
|---|---|---|---|---|
| 🔥 航空工业西安所 **ZC26G280436**「AI网关、MCP注册、多模态平台」 | 国产平台+MCP组件+**2台智算** | **10-20 09:00** | `mcp-registry`+`mcp-gateway`+`mcp-alibaba` | 购标已截止，投标准备中 |
| 🔥 中石油·长庆油田「专题2·MCP 服务」 | 130 万 | **10-16 截（仅 3 天！）** | `mcp-registry`+`mcp-governance`+`mcp-tools` | 紧急冲刺 |
| 中国电信襄阳·老河口「AI 大模型赋能平台」 | 45.43 万 | **10-16 14:00 截** | `mcp-registry`+`mcp-governance` | 政务智能体 |
| 宁夏农业农村厅「助农增收智能体 MCP 试点」 | 287.2 万（**已中标**） | 10-08 公示 | `mcp-registry`+`mcp-governance` | 电信宁夏中标，MCP 首进标的名 |
| 湖北省疾控中心「人工智能+预警预测」 | 150 万（11月采购意向） | 10-08 发布 | `mcp-registry`+`mcp-tenant`+`mcp-governance` | 卫健首单，等保三级 |
| 上海海关「百炼大模型平台升级及运维」 | 110 万 | 10-08 已截 | AI网关+智算+运维 | 参考：AI网关独立采购成常态 |

> **本周窗口**：长庆(10-16) + 襄阳(10-16) 双截标仅剩 3 天，ZC(10-20) 7 天。投标素材包/等保对照表/安全简报均已就绪 → 见 `docs/tender-asset-changqing-2026-10-04.md`、`docs/mcp-governance-dengbao-compliance.md`、`docs/mcp-security-incident-brief-2026-10.md`。

**来源**
- ZC26G280436 招标公告（中招联合 365trade）：https://365trade.com.cn/zhwzb/877891.jhtml
- 宁夏 287.2 万中标公告（中国政府采购网）：https://www.ccgp.gov.cn/cggg/dfgg/zbgg/202610/t20261008_27444436.htm
- 上海海关百炼大模型平台（中国政府采购网）：https://www.ccgp.gov.cn/cggg/zygg/gkzb/202609/t20260915_27326324.htm

---

## 🏢 招聘（清一色 Java + MCP，10-10~10-13 新出）

| 公司 / 岗位 | 地点 | 薪资 / 费率 | 点名技术 | 对齐 |
|---|---|---|---|---|
| **Cotality · Senior Software Engineer (MCP Servers)** | 达拉斯 | 5+ yr，Python/JS/**Java** | **MCP Server + Apigee X 网关 + OAuth** | `mcp-gateway`+`mcp-auth`（截至 10-11） |
| **EPAM · Senior Java Engineer – AI Native** | 印度 Coimbatore | 5–10 yr | **Spring Boot 构建并部署 MCP Server** 暴露 Java 服务/库给 LLM Agent | 全栈对齐 |
| **InAddition · Software Engineer (AI Solution / MCP Focus)** | 台北 | 1.1M–1.3M TWD/年 | **Java 实现 MCP Server** + Docker + 完整测试 | 全栈对齐 |
| **沃尔玛(中国) · 高级 AI 平台** | 中国 | **30–55K/月**（10-27 截） | AI/MCP 网关、MCP Server、Skill/Spec Registry；加分 Nacos/Higress/Spring AI | `mcp-gateway`+`mcp-alibaba` |
| **实习僧 · AI 应用开发(Java)** | 北京 | 18–30K/月（11-05 截） | MCP 工具与 Skills 体系 | `mcp-tools`+Skills |
| **猎聘 · 箱箱共用 Java 高级工程师** | 上海徐汇 | 20–30K·13薪 | MCP 协议/Skills/SubAgent/Harness | 全栈对齐 |
| **猎聘 · 全栈(Java+AI)** | 上海浦东 | 9–13K | Spring AI/MCP/LangChain | `mcp-spring-ai-tools` |
| **鱼泡直聘 · AI 开发工程师** | 北京丰台 | 8–12K | 遵循 **MCP 开发规范** 用 Java 开发 | 全栈对齐 |

> **结论**：Java+MCP 岗已下沉到二线/应届/9–13K 档，但**大厂（沃尔玛/EPAM/Cotality）与高薪档（30–55K）全部点名「MCP Server 治理、网关、注册中心、OAuth」**——这正是本框架的主场卖点。投递话术：**"我维护一个生产级 Spring MCP 治理框架（RBAC/审计/HITL/限流/多租户），代码开源在 GitHub"**，比空谈简历压中率高一个数量级。

**来源**
- Cotality MCP Servers：https://bebee.com/us/jobs/senior-software-engineer-mcp-servers-cotality-dallas--lifeworq-2a7a9e5a-5dab-4dff-97dc-f66f86f781ad
- EPAM Senior Java Engineer AI Native：https://www.glassdoor.com.hk/partner/jobListing.htm?jobListingId=1010196725652
- InAddition MCP Focus (Cake)：https://www.cake.me/jobs/MCP?locale=vi&seniority_levels=mid_senior_level
- 沃尔玛中国 AI 平台：https://bebee.com/cn/jobs/ai--techmap_cn_4455831440
- 实习僧 AI 应用(Java)：https://www.shixiseng.com/intern/inn_4rnvnkaab6sj
- 猎聘 箱箱共用：https://m.liepin.com/job/1977050753.shtml
- 猎聘 全栈 Java+AI：https://m.liepin.com/job/1985518269.shtml

---

## 🤝 接单 / Upwork（官方 MCP Server 已成获客前端）

- **Upwork 官方 MCP Server（`mcp.upwork.com`）**：2026-08-10 上线，支持 Claude/ChatGPT/Cursor/Codex 任意 MCP 客户端；客户端侧可搜人才/发职位/管合同，自由职业者侧可收匹配岗位、起草并提交提案。**agentic hiring 改变规则**：AI 先按结构化字段（技能标签/费率/类目/履历）过滤你，再决定进不进短名单——**三段式自我描述不再决定首轮**。`beingguru.com` 建议本周必改 5 个 profile 字段：可用性、时薪、技能标签（对齐 Upwork 词表）、类目、履历条目。
- **在招真实单**：Secure Procore + ChatGPT MCP Integration（Upwork 固定价 pilot，要求 MCP server 开发 + OAuth 2.0 + Node/TS/Python + 私有云部署 + 访问控制与审计日志，澳洲中型公司）。
- **定价梯度（沿用，仍有效）**：AI Agent/MCP **$120–300/hr**（资深 $200+ 标配）；项目包 MCP Server $90/$180/$350（进阶 $1,500），+$75/工具，+$200/系统；行业实测 **$1,500–5,000/项目**、复杂 **$10,000+**；中文副业生态 **5K–8 万/项目**。
- **组合拳**：本框架作交付底座（RBAC/审计/HITL 直接满足客户"访问控制与审计日志"硬性要求）+ Upwork MCP 作获客前端。**客户要"secure MCP integration with audit logging"，本框架开箱即答。**

**来源**
- Upwork MCP Server 官方页：https://upwork.com/ai/mcp
- Upwork 新闻稿（agentic hiring）：https://upwork.com/press/releases/upwork-talent-is-now-everywhere-ai-works
- agentic hiring 对自由职业者影响：https://beingguru.com/upwork-mcp-server-what-agentic-hiring-changes-for-freelancers
- Procore + ChatGPT MCP 在招单：https://www.upwork.com/freelance-jobs/apply/Secure-Procore-and-ChatGPT-MCP-Integration_~022098750919935049864

---

## 🛡 安全 / CVE（10 月集群，治理护城河再证）

| CVE / 事件 | 严重度 | 核心缺陷 | 本框架对症模块 |
|---|---|---|---|
| **CVE-2026-104850**（MCP TS SDK OAuth） | CVSS 7.5 | OAuth client 让远端 MCP server 决定凭证发往哪个授权服务器（issuer 未绑定）；**修复有缺口：升级不等于安全**，需对 bundled provider 设 `expectedIssuer`、给旧凭证补 issuer 或清登重登 | `mcp-auth`（Bearer 强制/jti/吊销）+ `mcp-governance`（Scope/ACL） |
| **Cycode 曝 MCP Python SDK OAuth 账户接管** | 高 | discovery 返回 404 → SDK OAuth client 退回 legacy fallback 跳过 issuer 校验，泄露 client_secret/code/PKCE；修复 1.30.0/2.2.0 | `mcp-auth` issuer 绑定范式 |
| **CVE-2026-59822**（LiteLLM MCP） | KEV | 接受任何无效 bearer token → 云接管；叠加 9.6% 代理仍用默认 `sk-1234` | `mcp-auth` 强制校验 + `mcp-governance` 网关集中鉴权 |
| **CVE-2026-19516**（Grafana MCP） | 高 | session ID 仅校验格式 → 伪造 + `X-Grafana-URL` SSRF | `mcp-auth` + SSRF 防护 |
| **CVE-2026-108585/108586**（argocd-mcp / 1mcp） | CVSS 5.4 | "认证≠授权"结构性缺陷：把认证当工具级授权 | `mcp-governance` Scope-ACL/HITL |

**新行业信号**
- **CoSAI 发布 MCP 安全模型 v2.0**；Adversa AI 列「Top 25 MCP vulnerabilities」并提供缓解映射。
- **两个恶意 MCP server 通过 5 款扫描器零告警**；扫描约 **82,000 个公开 MCP 配置文件** 发现 1/8 槽位硬编码密钥（多数扫描器识别不了的形状）。
- **"Five gates before an MCP tool reaches production"**（trace human/agent/backend 凭证、scope 可见工具、限流防循环、破坏/出站动作人工审批、审计留痕）——与本框架 T0–T4 + HITL + JDBC 审计范式逐条重合。

> **护城河叙事（投标/接单可直接用）**：官方生态（Nacos MCP Registry + Higress）只解决"注册+路由"，**安全治理是空白**；本框架补上最缺的 RBAC/审计/HITL/Scope/多租户。完整事件→控制映射见 [`docs/mcp-security-incident-brief-2026-10.md`](docs/mcp-security-incident-brief-2026-10.md)。

**来源**
- CVE-2026-104850（Cyber-Defence，含 EPSS/修复版本）：https://www.cyber-defence.io/tools/cve/CVE-2026-104850
- TS SDK OAuth issuer 绑定缺口（AL-ICE）：https://al-ice.ai/posts/2026/10/mcp-typescript-sdk-cve-2026-104850-issuer-binding-python-twin-no-cve
- MCP Security October 2026 digest（Adversa AI）：https://adversa.ai/blog/top-mcp-security-resources-october-2026
- CVE-2026-108585/108586 结构性弱点类（ThreatClaw）：https://www.threatclaw.ai/discussion/v7axqf5wmv2pt5ol

---

## 🌱 生态（Spring AI Alibaba × Nacos × Higress 本周官方化）

- **Nacos 3.0 发布 MCP Server 自动注册与发现手册**：用 Spring AI Alibaba 或 Nacos MCP Wrapper(Python) 开发 MCP Server，启动即动态注册 Nacos，支持**工具描述/参数运行期热更新、工具运行期启停、配置中心+服务发现全栈打通**；`@Tool` 方法 + `spring.ai.mcp...alibaba.mcp.nacos.registry.enabled=true` 即开。依赖 `spring-ai-alibaba-starter-mcp-registry >= 1.0.0.3` + `spring-ai-starter-mcp-server-webmvc >= 1.0.0`。
- **Higress 升级 CNCF Sandbox v2**：开源 AI 网关，统一承载 LLM/Agent/MCP，5 分钟 Docker 本地起；提供免费开源版 + 阿里云企业版（99.95% SLA）。
- **Spring AI Alibaba 定位再确认**：企业级 MCP = Nacos MCP Registry（分布式注册+负载均衡）+ Higress AI 网关（协议转换/路由）。JManus（Java 版 Manus，基于 Spring AI Alibaba）成通用智能体平台标杆。11k stars、275 contributors。
- **本框架重定位（不变）**：官方给"注册+路由"，**本框架补"治理增强层"**——RBAC/审计/HITL/Scope/限流/多租户。GA 示例 → [`examples/spring-ai-alibaba-mcp-server/`](examples/spring-ai-alibaba-mcp-server/)。

**来源**
- Nacos MCP 自动注册手册：https://nacos-group.github.io/en/docs/v3.0/manual/user/ai/mcp-auto-register/
- Higress 官网：https://higress.io/
- Spring AI Alibaba 概览（AIBARS）：https://aibars.net/en/projects/782930675329273856
- Nacos + Higress 连 Agent 与 MCP 实战：https://www.sheratonhq.com/news/121570

---

## 🎯 卖点小结（Java + Spring + AI 在 MCP 赛道的差异化）

| 维度 | 官方生态（Nacos/Higress/Spring AI Alibaba） | 本框架（spring-ai-mcp-enterprise） |
|---|---|---|
| 注册 / 发现 | ✅ Nacos MCP Registry 自动注册 | ✅ `mcp-registry` 兼容并补 RBAC 命名空间隔离 |
| 路由 / 网关 | ✅ Higress AI 网关 | ✅ `mcp-gateway` 联邦 `<prefix>__<tool>` + 治理平面 |
| **RBAC / 鉴权** | ⚠️ 基础 | ✅ `mcp-auth`(Bearer/jti/吊销) + `mcp-governance` |
| **审计 / HITL** | ❌ 缺 | ✅ JDBC 审计 + T0–T4 + 人工审批 + 链路追踪 |
| **多租户** | ⚠️ 部分 | ✅ `mcp-tenant` 实例级隔离 |
| **等保合规** | ❌ 缺 | ✅ 等保三级逐条对照表（可直接进标书） |
| **安全事件简报** | ❌ 无 | ✅ 10+ CVE 逐条映射（投标合规加分） |

> **一句话卖点**："Spring AI Alibaba 帮企业把 MCP 跑起来，本框架帮企业把 MCP **安全地**跑进生产——这是央企/金融/卫健招标现在明文要求的部分。"

---

## 📌 明日（10-14）行动候选

1. **长庆 130 万（10-16 截）冲刺**：用 `tender-asset-changqing` + 等保对照表 + 安全简报，备齐业绩证明 + 团队画像 + 中间量验收方案。
2. **襄阳 45.43 万（10-16 截）政务智能体方案**：对齐 `mcp-registry`+`mcp-governance`+中间量验收。
3. **ZC26G280436（10-20 截）扩正式标书**：融合安全事件简报作合规加分 + 西安本地交付叙事。
4. **发本 SEO 稿** `blog-java-mcp-spring-ai-alibaba-ecosystem-2026-10-13.md` 抢「Nacos MCP 自动注册 / Spring AI Alibaba MCP / MCP 治理」搜索流量。
5. **强化 GitHub Topics**：`MCP 注册中心 / MCP 治理 / 等保 / Spring AI Alibaba / 多租户 / 接单`。
6. **补推积压 commit**：V1.39+V1.40+V1.41 三笔已就绪本地，待 GitHub PAT 刷新后立即 `git push origin main`。
7. 评估主 `mcp-alibaba` 模块升 Spring AI Alibaba `starter-mcp-registry 1.0.0.3` 坐标（待验证后再动主代码）。
