# Earnings Report 2026-11-04 — 证券业招标首次把「MCP Server 工程能力」写成资质门槛 × 趋势科技影子AI报告 × 网关自身出洞

> 抓取窗口：2026-11-01 ~ 2026-11-04（web_search 实时全源）｜ 产出侧：spring-ai-mcp-enterprise V1.62

## 一、今日最强信号总览

| 等级 | 信号 | 命中模块 | 挣钱含义 |
| --- | --- | --- | --- |
| 🔥🔥🔥 | **广发证券供应商征集把 MCP Server 能力逐条写成资质门槛** | mcp-core/governance/gateway/monitor | 证券业预招标=提前 30~60 天卡位窗口 |
| 🔥🔥🔥 | **趋势科技 TrendAI「影子AI」报告：19,000 个开源 MCP 仓库 20% 带洞、1,467 个无鉴权暴露** | mcp-governance/auth | 治理增强层最强量化证据（可进标书） |
| 🔥🔥 | **MCPHub 网关自身 CVE：把调用者凭据透传给攻击者注册的 server** | mcp-gateway/governance | "自建网关≠安全"叙事再实锤 |
| 🔥🔥 | **太平金科研发工作台采购：MCP/Skills 写进保险业采购条目（腾讯云中）** | mcp-governance/registry | 保险业 MCP 成采购标配 |
| 🔥🔥 | **京北方半年报：MCP 服务写进上市银行 IT 商财报** | 全栈 | MCP 成为财报级业务条目 |
| 🔥 | **Java+Spring+MCP 招聘 4 条国内 + 3 条海外** | mcp-spring-ai-tools | 接单/求职双通道 |

## 二、招投标（预招标卡位优先）

### 1. 广发证券「2026 年企业微信持续建设项目」供应商征集公告（gf.com.cn 采购平台）🔥🔥🔥
- 建设范围原文：**「智能体(Agent)+技能(Skill)+MCP 工具接入」三层体系**——6 个场景智能体（微管家业务专家/智能问数×2/打新/服务日志/服务查询总结）+ 可复用技能（客群圈选/达标测算/渗透率/收益归因/批量触客/会话总结/OCR…）+ **MCP 工具接入**（客户信息、资产/持仓/交易/盈亏、业务准入规则、高级筛选引擎、留痕中心、企微会话）。
- **资质要求原文（逐条命中本框架）**："MCP Server 设计与开发适配能力（**工具定义与描述、参数校验、鉴权与权限控制、调用限流、日志与可观测性**）" → 五项对应 `mcp-core`(注册/限流) + `mcp-governance`(鉴权/Scope/审计) + `mcp-monitor`(可观测)；另要求：金融行业大模型案例 ≥2 个、技能工程化（定义/注册/编排/复用/版本管理/效果评测）、**须用采购方 Dify/hiagent 平台开发**、数据权限按客户经理归属严格隔离、**服务行为自动留痕且日志不可篡改** → `mcp-tenant`(数据隔离)+`mcp-audit`(防篡改日志)。
- **意义**：这是可核验的证券业招标文本首次把 MCP Server 工程能力写成**供应商资质门槛**（不再是"加分项"）。征集公告=正式招标前 30~60 天窗口，面向集成商报名——本框架可作为集成商的"治理底座"技术方案素材。
- 定性：**预招标卡位信号**，命中 `mcp-governance`+`mcp-core`+`mcp-tenant`+`mcp-audit`+`mcp-monitor` 全家桶。

### 2. 太平金科「智能化研发工作台」单一来源采购（11-01 立项，腾讯云中标）🔥🔥
- 采购功能清单：基础工作台（自主规划/**Skills 扩展/MCP**/专家及专家团）+ 代码开发（插件/IDE/CLI、**MCP**、Rules、记忆、SKILL 管理）+ 知识治理（权限管控/全生命周期）。
- 硬约束：**私有化 + 信创**（海光 x86/鲲鹏 ARM + 银河麒麟 + 国产 GPU≤8 卡）+ 双基座模型可切换 + 统一底座/统一权限。
- 意义：MCP+Skiils 已从"技术名词"变成保险业**采购验收条目**；信创+私有化约束=Java 栈治理底座天然优势区，命中 `mcp-governance`+`mcp-tenant`。

### 3. 京北方（002987）2026 半年报 🔥
- 原话："作为核心服务商深度参与某银行金融大模型三期建设，提供人工智能开发、运营及 **MCP 服务**等全链条支持" → MCP 服务进入**上市公司财报披露级业务条目**，银行 MCP 外包服务已有成熟买单方（乙方模式）。

### 4. 延续主线
- 剑鱼标讯 MCP（4.1 亿条招标数据接入 Coze/WorkBuddy/Trae）热度延续，垂直数据 MCP 品类确认（11-03 已入档）。
- 中国石油大学(北京) 具身智能机器人平台 98.8 万中标（11-04 公示）——高校 AI 平台采购继续放量，与厦大 11-11 截标同梯队。

## 三、安全治理护城河（今日最强量化证据）🔥🔥🔥

### 1. 趋势科技 TrendAI《代理式AI的影子工作流程》报告（11-03，工商时报）
- **19,000 个开源 MCP 服务器仓库中约 20% 存在可被利用的漏洞**；
- **1,467 个对外暴露、完全没有鉴权机制的 MCP 服务器**；
- 2025 年出现 **95 个 MCP 相关 CVE**；预估 **2026 年 AI 相关 CVE 达 2,800~3,600 个**；
- 新攻击范式 **Return-to-Tool (RTT)**：恶意指令藏进客服案件/文档/数据库记录/图片 → 代理 AI 当工作要求执行 → 动用自身合法权限取验证权杖/加密客户资料；
- "企业导入 AI 代理的速度已超过资安能见度与治理能力"=「影子AI」。
- **→ 治理增强层的最强第三方量化背书**：20% 漏洞率 + 1467 无鉴权暴露 两个数字可直接进标书/SEO，命中 `mcp-governance`(资产清册/访问控制) + `mcp-auth`(强制鉴权) + `mcp-core`(注入防护/出网隔离)。

### 2. 本周 CVE 波（11-01/11-02 发布）
| CVE | 产品 | 类型 | CVSS | 治理含义 |
| --- | --- | --- | --- | --- |
| CVE-2026-120338 | Prefab UI（FastMCP Generative UI 沙箱） | 沙箱逃逸 RCE（Pyodide→Deno 全权） | **10.0** | `mcp-core` 沙箱/最小权限 |
| CVE-2026-120315 | LobeHub | 路径穿越覆写启动脚本 | 8.8 | `mcp-core` 参数校验 |
| **CVE-2026-120323** | **MCPHub（自建 MCP 网关/控制面）** | **非特权用户注册恶意 OpenAPI server，passthroughHeaders 把调用者 Authorization/Cookie/X-Api-Key 原样透传给攻击者** | 4.3~7.3 | **网关自身出洞** → `mcp-gateway` 的 egress 白名单 + server 准入分级正是对症缓解 |

- **MCPHub 这条最关键**：市面上"自建网关"把「透传调用者凭据」当默认特性，等价于把管理员的 bearer key 拱手让人 → "用了网关≠安全，网关需要治理增强层（server 准入分级/egress 控制/凭据隔离）"叙事新增实证，直接对标 `mcp-gateway`+`mcp-governance` 设计。

## 四、招聘 / 接单（Java+Spring+MCP）

### 国内（4 条新）
| 公司/岗位 | 价码 | MCP 相关要求 | 更新 |
| --- | --- | --- | --- |
| 小米 · 高级AI系统开发工程师（大模型与RAG） | 25-30k | Agent 工作流编排框架、**工具调用（MCP 协议）**，Java/Spring 全家桶硬性 | 11-04 在招 |
| Robotaxi/RoboX · Java 研发工程师（苏州，招 3 人） | **30-60k·16薪** | **AI Agent 开发、MCP 集成实践**为基本条件 | 猎聘 11-01 更新 |
| 全栈开发工程师（SaaS ERP+AI，深圳） | 15-30k | RAG/Agent/**MCP 工具集成**/Prompt 工程至少一项 | 猎聘今日更新 |
| 深圳北芯生命科技 · Java+AI | — | 业务接口封装为智能体 Tool，ChatBI/**MCP 优先** | 在招 |

### 海外（3 条新）
| 公司 | 岗位 | 关键点 |
| --- | --- | --- |
| **Bench Zero（纽约 Bryant Park）** | Senior/Enterprise Java Developer with AI（Contract 12-18+ 月） | **10+ 年 Java + Spring 生态，Spring AI 为 strong plus** → 与用户技术栈逐字吻合的海外合同岗 |
| GSK（伦敦） | Forward Deployed Engineer（Agentic Automation，截止 11-10） | **MCP + 治理（Responsible AI/Accountability）+ 受监管行业 GxP/GDPR/SOX** → "MCP+合规"复合岗 |
| Elucida Labs（San Jose） | Founding Staff AI Engineer | 职责含 **build MCP servers**（科学计算场景） |

### 生态佐证
- 「**有写过 MCP 服务吗**」已进入 Java 后端大厂面试题体系（Spring Boot 最小 MCP Server 教程类内容在中文社区批量出现）→ 面试内容需求=SEO 引流新入口，本框架 examples/ 即"我搭过、跑通过"的现成答案。

## 五、用户 Java+Spring+AI 卖点定位（本期更新）

1. **唯一 Java/Spring 开源企业级 MCP 治理栈**：广发证券资质门槛五项（工具定义/参数校验/鉴权限权/限流/可观测）= 本框架开箱能力清单，可逐条应答。
2. **"影子AI"治理答案**：TrendAI 20% 漏洞率/1,467 无鉴权暴露 → `mcp-governance` 的 RBAC/Scope/HITL/审计 + `mcp-auth` 强制鉴权 = 直接缓解项。
3. **网关治理增强层**：MCPHub CVE-2026-120323 证明自建网关需要 server 准入分级 + egress 控制 + 凭据隔离，本框架原生具备。
4. **信创+私有化友好**：太平金科硬约束（国产芯片/OS/双基座）= Java 栈优势区，`mcp-tenant` 数据隔离对齐"权限按归属隔离"。
5. **接单兜底**：国内 30-60k·16 薪 Java MCP 岗 + 纽约 12-18 月 Java+Spring AI 合同岗，招聘密度持续。

## 六、明天做什么（候选）

1. 发 SEO 稿抢「广发证券 MCP 招标 / 证券业 MCP 资质门槛 / 影子AI MCP 漏洞 / MCPHub CVE / Java Spring MCP 招聘」流量。
2. 把 TrendAI 两个数字（20%/1,467）+ RTT 攻击范式补进 `docs/mcp-governance-regulatory-compliance-2026.md` 与 `docs/mcp-security-incident-brief-2026-10.md`（标书素材）。
3. 跟踪广发证券正式招标挂网（征集→招标通常 30~60 天），预组"治理底座"技术方案页。
4. 厦大 11-11 截标倒计时 7 天，军队大模型安全防护 190 万 11-16 截 → 备齐"治理+网关+等保"素材。
5. 面试题引流：基于 mcp-examples 写一篇「Java 工程师 MCP Server 面试通关」长文。

## 七、模块映射速查

| 市场信号 | 命中模块 |
| --- | --- |
| 广发证券资质门槛五项 | `mcp-core`(注册/限流) + `mcp-governance`(鉴权/Scope) + `mcp-monitor`(可观测) + `mcp-audit`(留痕) |
| 广发证券数据权限隔离/留痕不可篡改 | `mcp-tenant` + `mcp-audit` |
| TrendAI 影子AI（20% 漏洞率/无鉴权暴露/RTT） | `mcp-governance` + `mcp-auth` + `mcp-core` |
| CVE-2026-120338 沙箱逃逸 | `mcp-core`（沙箱/最小权限） |
| CVE-2026-120323 MCPHub 凭据透传 | `mcp-gateway`（egress 白名单/准入分级）+ `mcp-governance` |
| 太平金科信创+私有化+统一权限 | `mcp-tenant` + `mcp-governance` |
| Java+Spring+MCP 招聘 7 条 | `mcp-spring-ai-tools` + `mcp-examples`（作品集） |
