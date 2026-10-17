# 💰 MCP Enterprise 每日收益/招标情报 — 2026-10-17（周六）

> 今日焦点：**ZC26G280436 倒计时仅 3 天（10-20 09:00 截标）**，这是本周最硬、最对标的"MCP 注册中心"央企标的。
> 同时捕获：阿里云 `api.aliyun.com/mcp` 正式上线（MCP 成官方 API 分发基础设施新证据）、美团 CatPaw Managed Agent 平台点名 MCP、一线 Java+MCP JD 井喷、Upwork 接单定价梯度。
> 全量链接见 [README 招标/招聘区块](../../README.md)。

---

## 📑 一、招标 / 采购（挣钱最硬信号）

| 标的 | 金额 | 状态 | 对齐模块 | 紧急度 |
|---|---|---|---|---|
| **AI 网关、MCP 注册、多模态平台**（ZC26G280436） | 国产平台+MCP组件+**2 台智算服务器** | **10-20 09:00 截标（仅 3 天！）** | `mcp-registry`+`mcp-gateway`+`mcp-alibaba` | 🔥🔥🔥 |
| 油气生产管理多智能体（专题2·MCP 服务）长庆油田 | 130 万 | 10-16 已截（结果待公示） | `mcp-registry`+`mcp-governance` | 待结果 |
| 老河口市 AI 大模型赋能平台（襄阳·电信） | 45.43 万 | 10-16 已截（结果待公示） | `mcp-registry`+`mcp-governance` | 待结果 |
| 12316「助农增收智能体 MCP 试点」宁夏农业农村厅 | 287.2 万 **已中标** | 10-08 公示 | 政务"指名 MCP"元年 | ✅ |
| 湖北省疾控「AI+预警预测」 | 150 万 | **2026-11 采购意向**（等保三级+MCP插件+多租户） | `mcp-registry`+`mcp-tenant`+`mcp-governance` | 预研 |

**ZC26G280436 投标响应包（建议 10-20 前打包）**：
- 技术方案模板 → [`docs/tender-proposal-ZC26G280436-2026-10-03.md`](tender-proposal-ZC26G280436-2026-10-03.md)
- 等保 2.0 三级对照表 → [`docs/mcp-governance-dengbao-compliance.md`](mcp-governance-dengbao-compliance.md)
- 监管合规映射（五辖区→模块）→ [`docs/mcp-governance-regulatory-compliance-2026.md`](mcp-governance-regulatory-compliance-2026.md)
- 安全事件简报（合规加分）→ [`docs/mcp-security-incident-brief-2026-10.md`](mcp-security-incident-brief-2026-10.md)
- 🆕 **投标响应包索引/Checklist** → [`docs/zc26g280436-bid-package-2026-10-17.md`](zc26g280436-bid-package-2026-10-17.md)

---

## 🏢 二、点名 MCP 的企业招聘（10-14~10-17 新出，清一色 Java+MCP）

- **🔥 阿里云智能 · Agentic Tools 开发专家（SDK 团队·北京/杭州）** — 团队已上线 **`api.aliyun.com/mcp`（OpenAPI MCP Server，一键接入阿里云 2W+ API，Core 模式）**，自研 CloudSpec IDL 把数十万 API 变语义工具；JD 要 **Java/Golang/Python + MCP + IaC + 分布式 AI 基础设施**。→ 这是 Spring AI Alibaba 生态核心玩家亲自下场做 MCP 工具链，**MCP 成官方 API 分发基础设施**的最强信号，直接命中 `mcp-registry`+`mcp-alibaba`。
- **🔥 美团 · AI Agent 开发工程师（北京，25-50k·15薪，10-09 更新）** — **CatPaw Managed Agent 平台**：MCP Server 接入与管理、工具沙箱与权限管控、Skill 体系、多智能体编排（SubAgent 生命周期/Workflow/RRULE 调度/可观测）。→ **字面命中** `mcp-governance`(沙箱/权限) + `mcp-registry`(接入管理)，是最对口的 JD 模板。
- **阿里 · 千问事业部 助手 Agent 交互技术专家（杭州）** — 精通 Java/Golang，Agent/工具调用/RAG/记忆上下文，5 年，AI 硬件开放体系。
- **Exerizon（Warsaw）· Mid-level Java Engineer（AI Agents, MCP）** — 为全球**保险公司**从零建高可用 MCP Server（Java 17+/Spring Boot/Spring AI MCP，JSON-RPC 2.0+SSE），B2B 远程，时薪制 → 全栈对齐。
- **InAddition Consultants（台北）· Software Engineer（MCP Focus）** — **用 Java 实现 MCP Server** + Docker + 完整测试（单元/集成）。
- **TEKsystems（Toronto）· Senior AI $208-249K CAD/年** — 建 MCP servers 暴露 PHSD 医疗数据（FHIR/R4/SMART on FHIR），Java Spring Boot + Python + Bedrock + OAuth2/OIDC/RBAC。
- **trilogy nextgen（Charlotte, NC）· Junior Java/Spring Boot + AI/LLM/MCP** — 金融服务客户，0–2 年，签证担保，点名 MCP。
- **EPAM（Coimbatore）· Senior/Lead Java Engineer – AI Native** — 建 MCP servers 暴露 Java 服务，字面要 **"security controls, versioning and observability"** → 命中 `mcp-governance`+`mcp-monitor`。
- **MoOngy（里斯本）· AI Engineer（Java/Python）** — "Build and evolve MCP servers"，Spring Boot/FastAPI/K8s。
- **Publicis Sapient（芝加哥）· Principal Java Architect $165-205K** — JD 原话 "ADK & Model Context Protocol (MCP)"。

---

## 💸 三、接单 / Upwork 定价梯度（10-17 最新）

- **youcanbuildthings（实战定价）**：三档 **$1K（单连接器）/ $3-5K（多工具 Server）/ $5-10K（全管道编排）**；真实案例 $3,500/14h = **$250/hr**。
- **dev.to 市场数据**：**97M SDK 下载/月**，**<5% 已变现**；Upwork MCP 开发 **$130-185/hr**；项目 **$1K-3K/个**；月维护 **$500-1,500**。
- **Upwork Services 三档并存**：`$90 / $250 / $550`（交付 3/5/10 天）与 `$400 / $800 / $1,500`（进阶 $1.5K）两种目录均在架。
- **StackNova**：自由职业 **$75-150/hr**（Upwork/Contra 实测，高于普通后端）。
- **变现三路径**：① Marketplace（mcp.so/Glama/PulseMCP，$19-49/server 被动）；② Gumroad（教程→数字产品）；③ 定制开发（$1K-3K + 月维护 $500-1.5K）。
- **组合拳**：本框架作交付底座 + Upwork 官方 `mcp.upwork.com` MCP 作获客前端（AI 先按结构化 profile 过滤再进短名单）。⚠️ 2026-07 费率改革：服务费 **0%–15%**，Connects **$0.15/个**，报价务必算到手价。

---

## 🛡 四、安全 / CVE（治理护城河持续强化，可进标书）

- **AI Governance Institute**：CIS MCP Benchmark **55 点**审计基线 + Agentics 六类攻击 taxonomy（Tool/Schema Poisoning、Tool Shadowing、Command Injection、Shadow Servers、Context Oversharing）+ **68 CVE/月**；**Five Eyes / NIST AI RMF / EU AI Act / 新加坡 MAS / 韩国（起草中）** 均把"Agent 工具层治理"引用进监管/准强制预期 → 治理从卖点升级为准入项。
- **Protocol Pivoting SSRF（CVE-2026-75130，无修复）** 反复击中 Google / JPMorgan / Weaviate / 法国 DINUM / 印尼 Tangerang 政府 → 结构性、跨 5 家无关组织，标准库"注册+路由"天然缺治理。
- **CVE-2026-104120（MCP Fetch SSRF，CVSS 7.3）**：官方 `mcp-server-fetch` 不做 loopback/metadata/私网过滤、重定向不重校验，**公开利用存在、修复 PR #4890 未合并**。
- **CVE-2026-104850（TS SDK OAuth issuer 绑定，CVSS 7.5）**：恶意 server 可指定自己授权服务器窃取 refresh_token；**升级≠安全**，需显式设 `expectedIssuer`（1.x 无 workaround，只能连可信 server）。
- **官方 Java `mcp-core` 7.5 DoS（CVE-2026-108395）**：无限缓冲请求体灌爆 OOM，影响 0.18.x<0.18.4 / 1.x<1.1.4 / 2.0.0 → 本框架 RateLimit+资源隔离+审计是对此的补偿控制，"升级官方 SDK≠安全"。

---

## 🌱 五、生态（Spring AI Alibaba / Nacos / Higress）

- **🔥 阿里云 `api.aliyun.com/mcp` 正式上线**：OpenAPI MCP Server 把 **2W+ 阿里云 API** 一键变 MCP 工具（Core 模式），自研 CloudSpec IDL 统一管理语义与生命周期 → **MCP 从"协议"升级为"官方 API 分发基础设施"**，本框架"治理增强层"定位被官方动作坐实。
- 已确认：Nacos 3.0 MCP 自动注册与发现手册（`starter-mcp-registry >= 1.0.0.3`，@Tool 启动即注册、运行期热更新/启停）、Higress CNCF Sandbox v2 开源 AI 网关、Spring AI Alibaba 1.0 GA。官方只给"注册+路由"，**安全治理（RBAC/审计/HITL/Scope/多租户）仍是空白** → 本框架补位。

---

## 💡 六、为什么 Java + Spring + AI 是 MCP 蓝海（卖点复述）

- **78% 企业 AI 团队已用 MCP，但 91.5% 裸奔**：仅 8.5% 实现 OAuth 2.1、43% 命令注入、492 个 server 零认证、18% 做工具权限 scoping。
- **官方 SDK 自己爆漏洞**（mcp-core 7.5 DoS / TS SDK OAuth 7.5）→ 升级官方≠安全，治理增强层是刚需。
- **监管把 MCP 治理写进强制预期**（CIS 55 点 / Five Eyes / NIST / EU AI Act / MAS / 韩国）→ `mcp-governance`+`mcp-auth`+`mcp-tenant` 即"合规就绪"资产。
- **Java+AI 复合岗 3–5 年 45K–75K**；二线 25-35K·16薪；海外 MCP Expert（Java）€600-1,000/天。

---

## 🎯 七、今日结论与最紧迫动作

1. **🔥 最紧迫（10-20 前）**：把 ZC26G280436 投标响应包（技术方案+等保对照+监管映射+安全简报+今日索引 Checklist）打包，直接对标"AI 网关+MCP 注册中心"。西安岗（阿里云/沃尔玛）可做本地交付叙事。
2. **挣钱支点**：阿里云 `api.aliyun.com/mcp` 上线 = MCP 成官方 API 分发基础设施，本框架"治理增强层"定位获官方侧印证；可用于 SEO 稿抢"MCP 治理/Spring AI Alibaba 治理"流量。
3. **招聘井喷**：美团 CatPaw、阿里云 SDK、千问、Exerizon、TEKsystems 清一色 Java+MCP，仓库即作品集，可直接投递。
4. **接单定价**：Upwork 三档 $90-1.5K 目录 + $1K-10K 项目 + 月维护 $500-1.5K，实战 $250/hr；组合拳=本框架作底座+Upwork MCP 获客。

> 完整每日收益/招标情报归档索引见 [README](../../README.md)。上一日 → [`earnings-report-2026-10-16.md`](earnings-report-2026-10-16.md)
