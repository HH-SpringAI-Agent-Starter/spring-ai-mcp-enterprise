# 💰 MCP Enterprise 每日收益 / 招标 / 招聘 / 接单情报 — 2026-10-10

> 扫描窗口：2026-10-07 ~ 2026-10-10（近 3 天实时 web_search）
> 项目版本基线：V1.37（21 模块，治理/审计/多租户/联邦网关全绿）。
> 本期核心结论：**安全治理从"加分项"变成"生死线"**——Google/JPMorgan/Weaviate/法国政府同款 SSRF 信任边界漏洞被 Ars Technica 头条报道；**LiteLLM MCP 端点成为第一个登上 CISA KEV 的 MCP 漏洞**；政务标首次把"MCP"直接写进采购标的名称（宁夏农业农村厅 287.2 万中标）。本框架 `mcp-governance` + `mcp-auth` 直接命中所有痛点。

---

## 一、🔥 招标 / 中标情报（挣钱主战场）

### 1. 宁夏农业农村厅 12316「助农增收智能体 MCP 试点」**287.2 万中标**（10-08 公示）
- **里程碑意义**：这是 **MCP 第一次写进政府采购"标的名称"**（此前只写"系统运维/平台升级"）。中国电信宁夏分公司 82.52 分中标，中国移动宁夏 80.79 第二，6 家投标。
- **结构信号**：287.2 万含 12316 热线运营，真正留给智能体试点的钱不多——**运营型 + 试点型**是政务 AI 主流打法（周期长、续约高、数据积累强）。
- **可复用打法**：验收指标必须拆到**可观测中间量**（首次响应时长 / 一次解决率 / 自动处理占比 / 采纳率），别写"带动农户增收 X%"这种终局指标（易卡验收）。
- 来源：https://so.html5.qq.com/page/real/search_news?docid=70000021_0386ac852ba17652

### 2. 🔥 长庆油田 专题2·MCP 服务 **130 万 — 10-16 截标（仅 6 天！）**
- 国家油气专项配套，130 万（不含税），西安，服务期至 2027-11-15。
- **硬性门槛**：① 2023-01-01 起**实时/时序数据 MCP 服务业绩 1 项**；② 项目负责人 + 技术负责人均需**中级及以上职称**；③ 开发工程师 ≥4 人；④ 不接受联合体。
- 本框架对标：`mcp-registry` + `mcp-governance` + `mcp-tools/tool-database`（时序数据 MCP 暴露）。
- 投标素材包 → `docs/tender-asset-changqing-2026-10-04.md`
- 来源：https://www.cnhh.com/zhaobiao/show-6112413.html ｜ https://m.ccement.com/xm/bcon-689451836791139.html

### 3. 航空工业西安所 ZC26G280436「AI 网关、MCP 注册、多模态平台」— 10-20 截标
- 招标文件获取已于 10-09 17:00 截止；投标 **10-20 09:00** 截，国产平台 + MCP 组件 + 2 台智算，3 个月到货，不接受联合体。
- 对标：`mcp-registry` + `mcp-gateway` + `mcp-alibaba`。投标技术方案模板 → `docs/tender-proposal-ZC26G280436-2026-10-03.md` ｜ 等保对照表 → `docs/mcp-governance-dengbao-compliance.md`

### 4. 算力 / AI 基础设施集采持续放量（MCP 注册中心常被作子项）
- 中国移动 2026–2027 PC 服务器集采：**软通计算机中标超 17.53 亿元**（10-10 财新）。
- 中国人民大学高瓴 AI 云计算服务 **284.4 万**（10-10 中标）；协和医院云平台扩容 **307 万**（10-08）。
- 来源：https://www.yicai.com/news/103190136.html ｜ https://www.ccgp.gov.cn/cggg/zygg/zbgg/202610/t20261010_27464146.htm

### 5. 湖北疾控 150 万（2026-11 采购意向，10-08 发布）
- 明确 **MCP 插件接入国家平台 + 多租户适配 + 等保三级 + 数据安全** → 对齐 `mcp-registry`+`mcp-tenant`+`mcp-governance`，首个卫健系统点名 MCP+等保三级。

---

## 二、🛡️ 安全治理：本月最强卖点（直接验证 mcp-governance / mcp-auth）

### 🔥 "Protocol Pivoting"——同款 SSRF 信任边界漏洞横跨 Google / JPMorgan / 政府（Ars Technica 10-05/06 头条）
独立研究员 Syed Anas Mohiuddin 在 5 个月内，把**同一类 MCP 信任边界缺陷**送到了 5 家互不相关的组织并全部确认修复：
- **Google**：`googleapis/mcp-toolbox` 缺 redirect 策略 + 目标 IP 校验 → **CVE-2026-14540，CVSS 8.0（High）**，影响 0.3.0–1.4.0，1.5.0 修复（新增 SSRFGuard、IP allow/block 名单、启动期校验 BaseURL）。
- **JPMorgan Chase**：docs MCP server 的 `related()` 工具无 allowlist 服务端取 URL，已修复（中危）。
- **Weaviate / 法国 DINUM(data.gouv.fr) / 印尼 Tangerang 市政府**：同款 SSRF，均已修复。
- **5 个美国政府（含 VA 福利）MCP server 仍处于 triage 未修复**。另 Rapid7 CVE-2026-97228（CVSS 2.7）已修。
- 攻击本质：一个 agent 读到的恶意文本被当成"委派任务"传给受信任的对等 agent，对等 agent 以内部网络身份发起 SSRF——Mohiuddin 称"protocol pivoting"，X41 D-Sec 归为间接提示注入子类。
- 来源：https://arstechnica.com/security/2026/10/vulnerability-in-agents-from-google-and-others-exposes-structural-flaw-in-mcp/ ｜ https://threatcluster.io/article/researcher-discloses-same-mcp-flaw-at-google-jpmorgan-two-go-bba90fcd

### 🔥 LiteLLM MCP 端点成为**第一个登上 CISA KEV 的 MCP 漏洞**
- **CVE-2026-59822**：LiteLLM 的 MCP 端点**接受任何无效 bearer token**（认证绕过 → 云接管），已进 CISA 已知被利用漏洞清单（KEV）。
- **CVE-2026-59821**：自定义 guardrail 允许管理员代码执行；pass-through 端点可达云元数据。
- **9.6% 暴露的代理仍使用默认 `sk-1234` 主密钥** → 预认证链直达云接管。
- **Grafana MCP**：会话 ID 仅按格式校验 → 可伪造并以服务账号调工具；`X-Grafana-URL` 头可伪造 → 完整 SSRF 代理（**CVE-2026-19516**）。
- **CoSAI 发布 MCP 安全模型 v2.0**（Adversa AI 任工作组 lead）。
- 扫描盲区：2 个恶意 MCP server 干净通过 5 款扫描器；约 **82,000 份公开 MCP 配置文件含硬编码密钥**（多数形态不为传统 secret scanner 识别）。
- 来源：https://adversa.ai/blog/top-mcp-security-resources-october-2026

### ✅ 本框架命中映射（可直接进标书 / 安全审计报告）
| 披露的缺陷 | 对应 CVE | 本框架缓解控制 |
|---|---|---|
| 接受任何无效 bearer token | CVE-2026-59822（CISA KEV） | `mcp-auth` OAuth2 Bearer 强制校验（V1.9 RFC 6750）+ `jti` 防碰撞 + 吊销端点 |
| 服务端 SSRF / 不受控出站 | CVE-2026-14540（CVSS 8.0） | `mcp-tools/tool-http` SSRF 防护（V1.1）+ `mcp-governance` 出站 allowlist 治理开关 |
| 会话伪造 / 越权调工具 | CVE-2026-19516 | `mcp-governance` RBAC + Scope→Tool ACL（V1.19）+ Signed Agent Card（V1.18） |
| 无审计 / 无追溯 | 行业通病 | `mcp-governance` 审计 JDBC 落库（V1.29）+ SIEM 实时流导出（V1.32）+ traceId 链路追踪（V1.31） |
| 高风险操作无人工审批 | 间接提示注入 | HITL 人类在环审批（V1.26）+ deny-tiers 硬闸门 + fail-closed |
| 零信任缺失 | Protocol Pivoting | 多租户实例级隔离（V1.13）+ 网关集中鉴权 + 等保 2.0 三级对照表（`docs/mcp-governance-dengbao-compliance.md`） |

> 今日新增高价值资产：`docs/mcp-security-incident-brief-2026-10.md`——把上述 6 起事件逐条映射到模块控制，可作为**投标合规证明 / 安全审计交付物**复用。

---

## 三、🏢 招聘情报（点名 MCP / Java / Spring）

- **实习僧 · AI 应用开发工程师（Java）18–30k/月 · 北京**：JD 原话"围绕业务场景构建业务 Agent、**MCP 工具与 Skills 体系**"；要求熟练 Java + Spring Boot，27 届，截止 11-05。来源：https://www.shixiseng.com/intern/inn_4rnvnkaab6sj
- **Satrio · MCP Expert $60–120/hr · 全球远程**：用 MCP 工具设计强化学习环境训练下一代 AI，10-07 发布，10-23 截。要求 C++/Python/**Java**/Go/TS/Rust 之一。来源：https://apply.satriocs.com/jobs/453
- **freelancermap · MCP Expert (Java API Integration) · 阿姆斯特丹 12 月合约**：生产级 MCP Server（Java）连接企业 API，费率可谈（已关闭但仍是需求信号）。来源：https://www.freelancermap.com/project/mcp-expert-java-api-integration-3048551
- **Darwin Recruitment · MCP Expert (Java API Integration) · 阿姆斯特丹**（10-03 上架，确认于招聘板）：生产级 MCP Server（Java）+ Azure。来源：https://alion.io/job/darwin-recruitment-mcp-expert-java-api-integration-2
- 延续在招：沃尔玛中国 30–55k（AI/MCP 网关 + Skill Registry，Nacos/Higress/Spring AI 开源优先，10-27 截）；Intellias Tech Lead·MCP Server Registry；Cloudflare MCP Portals €66–91K。

---

## 四、💸 接单 / 自由职业定价（Upwork + 中文副业）

- **Upwork 2026 增长最快开发者类目 = AI Agent Engineering / LLM 集成**：MCP server 实现 **$120–300/hr**（资深 **$200+** 标配）；多 agent 设计 $180–350/hr；RAG 管线 $130–250/hr。来源：https://uphunt.io/blog/developer-guide-niche-selection-upwork
- **Upwork Project Catalog MCP 成品**：Starter $250 / Standard $750 / Advanced $1,500（连接 Claude/ChatGPT 到 API/DB/CRM）。来源：https://upwork.com/services/product/development-it-a-custom-mcp-server-to-chat-with-your-system-with-natural-human-language-2089561627151831754
- **SecurityScorecard 招 Senior MCP Engineer**（<30hr/wk，$10–250/hr，50+ 提案）：要求**生产级 MCP server + OAuth + 租户隔离 + 审计日志**，支持 ChatGPT/Claude/Copilot/Codex 客户端。来源：https://www.upwork.com/freelance-jobs/apply/Build-MCP-server-for-some-of-my-data-sets_~022102156808568493227
- **中文副业盘点（2026 版）**：MCP 生态开发单项目 **5,000–8 万**，标注"2026 最蓝海赛道，会做的人极少"，收款走 Wise/PayPal，零启动成本。来源：http://www.hqwc.cn/a/1591985.html

---

## 五、🌱 生态 / 技术栈（Spring AI Alibaba × MCP）

- **Spring AI Alibaba extensions 1.1.2.0 发布**：新增 **MCP Registry**（`spring-ai-alibaba-starter-mcp-registry`）+ **MCP Router**（`spring-ai-alibaba-starter-mcp-router`）+ **40+ 预置工具**（搜索/翻译/地图/新闻/协作/数据等）。来源：https://github.com/spring-ai-alibaba/spring-ai-extensions
- **Spring AI 2.0 GA**（6-12 发布，Boot 4.1，MCP SDK 2.0.0，合规 2025-11-25 MCP 规范）：工具调用统一进 ChatClient + advisor 链，移除 `internalToolExecutionEnabled`，把 tool loop 收归框架——**Java agents 终于有"够稳的应用栈"**。来源：https://data-today.net/spring-ai-java-agent-stack
- 本框架定位再确认：**官方 Spring AI Alibaba / Nacos / Higress 方案的"安全治理增强层"**（RBAC/审计/HITL/限流/多租户/等保），GA 示例 → `examples/spring-ai-alibaba-mcp-server/`。

---

## 六、📌 今日结论（挣钱视角）

1. **治理 = 生死线，不是选项**：CISA KEV 首个 MCP 漏洞 + Google/JPMorgan 头条 = 客户现在会因"裸奔"被监管/媒体点名。`mcp-governance`+`mcp-auth` 是本月最强差异化卖点，所有投标/接单话术应前置"合规就绪"。
2. **政务标进入"指名 MCP"时代**：宁夏 287.2 万把 MCP 写进标的名，长庆 130 万 10-16 截、ZC 10-20 截——**本周必须完成长庆投标素材冲刺**（业绩证明 + 团队画像 + 等保对照表）。
3. **Java+Spring+AI 是稀缺蓝海**：实习僧/沃尔玛/Intellias 密集点名 Java MCP；Upwork MCP $120–300/hr，中文副业单项目 5k–8 万。仓库（GitHub 作品集）+ 等保对照表 + 安全事件简报 = 可直接投递的"合规能力证明"。

> 上一份情报 → `docs/earnings-report-2026-10-09.md` ｜ 安全事件简报 → `docs/mcp-security-incident-brief-2026-10.md` ｜ SEO 稿 → `docs/blog-java-mcp-security-tender-2026-10-10.md`
