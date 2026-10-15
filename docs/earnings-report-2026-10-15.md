# 收益 / 招标 / 招聘 / 接单 情报日报 — 2026-10-15（V1.43）

> 数据来源：WebSearch 实时抓取（2026-10-11 ~ 10-15），覆盖招聘平台、中国政府采购网、NVD/CVE 公告、Upwork 生态、开源社区。
> 目标：把「MCP 赛道企业需求」转化为本框架（`spring-ai-mcp-enterprise`）的**开源影响力 + 接单/投标弹药**。

---

## 🔥 今日最强信号：官方 Java SDK 的 `mcp-core` 自己爆了 7.5 DoS CVE

**CVE-2026-108395（Java SDK `mcp-core`，CWE-770，CVSS 7.5，2026-10-14 进入 NVD）**
- 漏洞位置：官方 Java MCP SDK 的 `mcp-core` 构件——**Servlet 版 SSE / Streamable HTTP / Stateless 传输**缓冲整个请求体且**不校验 Content-Length**，用 chunked 分块编码可流式灌入任意大 body，直到 `OutOfMemoryError` 把进程打崩。**单条请求即可**触发。
- 影响版本：`0.18.x < 0.18.4`、`1.x < 1.1.4`、`2.0.0`。
- 同源两发（Python SDK）：**CVE-2026-109527**（CVSS 7.5，OAuth 端点/Streamable HTTP/SSE 无限缓冲 body）、**CVE-2026-59951**（CVSS 6.9，HTTP session state 默认永不回收 → DoS）。

**为什么这对本框架是「黄金护城河」信号：**
1. 漏洞命中的构件名就叫 **`mcp-core`**——与本框架核心模块同名。官方参考实现连「资源上限」都没做，而**本框架的 `mcp-core` 是治理增强层**：`mcp-governance` 的 **RateLimit（限流）+ 请求体上限 + 审计 + HITL** 正是对「无界缓冲 DoS」的补偿控制。
2. 可直接写进话术：「**别只升级官方 SDK——它的 mcp-core 刚被 7.5 DoS CVE 点名。把 MCP Server 挂到治理网关后，限流/资源隔离/审计天然兜底。**」
3. 关联资产：[`docs/mcp-security-incident-brief-2026-10.md`](docs/mcp-security-incident-brief-2026-10.md)（事件→控制映射表，可进标书/审计报告）。

**NVD 全量计数（10-12 查询）：139 条记录点名 MCP 协议 —— 24 Critical / 66 High / 44 Medium / 5 Low。** 治理鸿沟持续扩大，卖点持续走强。

---

## 📑 招标 / 采购动态（10-11 ~ 10-15）

### 🚨 本周最硬 Deadline（倒计时）
| 标 | 金额 | 截标 | 剩余 | 对齐模块 |
| --- | --- | --- | --- | --- |
| 中石油·长庆油田（专题2·MCP 服务） | **130 万** | 10-16 14:00 | **仅 1 天** | `mcp-registry`+`mcp-governance`+`mcp-tools` |
| 中国电信襄阳·老河口市 AI 大模型平台 | **45.43 万** | 10-16 14:00 | **仅 1 天** | `mcp-registry`+`mcp-governance` |
| 航空工业西安所 ZC26G280436（AI网关+MCP注册+多模态） | 含 2 台智算 | 10-20 09:00 | **仅 5 天** | `mcp-registry`+`mcp-gateway`+`mcp-alibaba` |

> ⚠️ 长庆 / 襄阳 **明天（10-16）就截标**——这是最后一次提醒。投标素材：
> - 长庆 → [`docs/tender-asset-changqing-2026-10-04.md`](docs/tender-asset-changqing-2026-10-04.md)
> - ZC 方案模板 → [`docs/tender-proposal-ZC26G280436-2026-10-03.md`](docs/tender-proposal-ZC26G280436-2026-10-03.md)
> - 等保对照表 → [`docs/mcp-governance-dengbao-compliance.md`](docs/mcp-governance-dengbao-compliance.md)

### 新放量（AI 算力基建，10-13 集中公示，喂生态非指名 MCP）
- 某研究所 AI 服务器 **366.7 万**（ccgp，10-13 中标，H3C R4930 G7×2）
- 杭州市第七人民医院智能算力服务器 **179.65 万**（10-13 中标）
- 国产化高性能通用算力服务器 **225.34 万** 中标候选（北京龙讯旷腾，10-12 公示）
- 国家海洋环境预报中心服务器 **7.975 万**；嘉兴公安服务器采购合同落地
- 解读：算力集采持续放量，央企 AI 平台标普遍把「MCP 注册中心+网关+治理（等保）」当子项 → 本框架是这些大单的治理拼图。

### 持续活跃（指名 MCP / 等保）
- 宁夏 12316「助农增收智能体 MCP 试点」**287.2 万已中标**（政务 MCP 第一标）
- 湖北疾控「人工智能+预警预测」**150 万**（11 月采购意向，MCP+等保三级）
- GSA 美国政府 MCP+AI Agent Hackathon（9–11 月，联邦级需求预演）

---

## 🏢 招聘（清一色 Java + MCP / Spring AI，10-11 ~ 10-15 新出）

| 公司 / 岗位 | 薪资 | 地点 | 命中模块 |
| --- | --- | --- | --- |
| 广州轻快创新 · Java 后端（AI 全栈） | 15–20k·13薪 | 广州 | JD 原话「AI Agent、MCP 能力建设」，Spring Boot/Cloud |
| 物产中大云商（世界500强）· 27届校招 AI 应用 | 校招 | 浙江 | 「Java+Spring AI…RAG、MCP、Skills」 |
| 华微软件 · AI 应用工程师 | 在招 | 广州 | 「熟悉 MCP 等 AI 应用技术」 |
| 四川辰海数聚 / 宜宾人才 · Java 高级工程师（AIoT） | 面议 | 宜宾 | 大模型流式交互、Agent 对话（10-11 截止） |
| 凯盛数智（中国建材集团）· 大模型应用开发（AI Agent） | 面议·硕士 | 上海 | 「MCP(Model Context Protocol)、Workflow、Memory、Multi-Agent」（10-10 截止） |

> 延续信号：沃尔玛 30–55K / 阿里云（西安岗呼应 ZC）/ 实习僧 18–30K / 猎聘武汉 25K / Mindera（罗马尼亚·建 MCP Server）/ Built In（保险 MCP $75–150/hr）/ 杭州火石创造 15–30k / 苏州智联（MCP 多智能体协作）/ 青岛中恒信 —— **Java+MCP 已成国企/外企/独角兽校招社招的共同硬指标**，仓库即作品集。

---

## 💰 接单 / Upwork 定价（2026-10 实测）

- **Upwork 费率改革（2026-07-20 生效）**：服务费改为**按合同 0%–15%**（旧 20%/10%/5% 阶梯已作废）；Connects **$0.15/个**。报价时务必按新费率算到手价。
- **Upwork MCP 生态成熟**：官方 `upwork-mcp-rs`（Rust，高保障 MCP Server）解决 2 小时 token 失效 / ID 错配 / 提示注入 / 上下文爆炸，带 SHA-256 二进制飞行记录器 —— 说明「MCP Server 接 Upwork」已成接单基础设施，也侧面印证**接单场景最吃治理/安全**（token 保管、审计、防注入）。
- 社区 Upwork MCP Server 清单（多数为社区桥，部分走 CDP 浏览器自动化有封号风险；只有官方 `upwork/upwork-agent-plugin` 标安全）—— 接单前置知识，避免踩 ToS 雷。
- 国内兼职平台指南（2026）：Upwork 时薪 **30–80 美金**（AI/全栈溢价高）、Toptal 精英 80–200 美金（0 抽成）、Fiverr 固定 20% 抽成。

**报价梯度（给客户/标书的锚点）：**
| 档位 | 价格 |
| --- | --- |
| 接单时薪（中级别） | $75–150/hr（资深 $200+） |
| MCP 基础设施日费 | €800–1000/天 |
| Upwork 项目包 | $400 / $800 / $1,500 三档 |
| 企业级 MCP 项目 | $1.5K–5K（复杂 $10K+） |
| 企业全职（MCP 安全治理） | $170–280K/年 |

---

## 💡 卖点对照（Java + Spring + AI 在 MCP 赛道的护城河）

1. **官方 SDK 自己有洞**：CVE-2026-108395（Java mcp-core 7.5 DoS）+ CVE-2026-109527/59951（Python）。→ 框架补「限流/资源隔离/审计」补偿控制。
2. **治理缺口是鸿沟**：仅 8.5% 强制 OAuth 2.1、43% 命令注入、492 个裸奔、18% 无 scope；NVD 139 条 MCP CVE（24 Critical）。
3. **合规走向强制**：CIS MCP Benchmark 55 点、EU AI Act / 新加坡 MAS / 韩国将工具层治理写进强制要求。
4. **标准已赢，治理空白**：10,000+ 生产 Server、9700 万月 SDK 下载；Spring AI Alibaba（Nacos 自动注册 + Higress v2）只给「注册+路由」，**安全治理（RBAC/审计/HITL/Scope/多租户）仍是空白** → 本框架 = 「官方方案的治理增强层」。

---

## 📌 今日结论
- **最紧迫**：长庆 130 万 / 襄阳 45.43 万 **10-16 截（明天）**，ZC 10-20 截（5 天）——把既有投标素材 + 等保表 + 安全事件简报打包送出。
- **最强新弹药**：CVE-2026-108395 让「官方 Java mcp-core 有 DoS 洞」成为 10 月最硬安全叙事，直接绑定本框架治理层。
- **接单价格锚点**已更新为 Upwork 新费率（0–15%）。

> 下一步见 [`docs/V1.43-release-notes.md`](docs/V1.43-release-notes.md) 与 README 版本表。
