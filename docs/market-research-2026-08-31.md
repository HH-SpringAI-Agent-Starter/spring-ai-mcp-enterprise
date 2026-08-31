# 市场雷达 2026-08-31 — MCP/A2A 人才、需求、价格

> 数据源：web_search（yuanbao，freshness=week，中英文检索）| 统计窗口：2026-08-29 ~ 08-31（含窗口内仍有效的中长周期信号）
> 本期重点事件：**2026-08-20 A2A 并入 AAIF**（窗口内多源确认）→ 本框架 V1.15 落地 mcp-a2a 双协议网关

## 一、招聘信号（谁在招、什么价）

### 国内（中文源）

| 公司/来源 | 岗位 | 薪资 | 关键要求（与本框架的对应） |
|-----------|------|-----------|------------------------------|
| **蚂蚁集团**（杭州，延续） | 大模型开发（销售 AI Agent） | 25-50K·15薪 | 「MCP、A2A 研发架构」红线 —— V1.15 已交付双协议网关 |
| **Recruit.net 某大型营销平台**（延续） | 技术专家（AI Agent） | 未公开 | JD 点名「MCP、RAG、Spring AI、**Spring AI Alibaba**、AgentScope」+ 5-8 年 Java + JDK17/21 —— 与本仓库 100% 重合 |
| **四川澜凯信安**（成都，截止 08-07） | AI 应用开发工程师 | 未公开 | **Java + Spring AI + MCP 服务端/客户端工具链 + Dify 对接**（知识库问答/数据分析/流程自动化）—— 正是本框架三层 |
| **淘天集团**（杭州，校招实习） | AI Agent 优化工程师 | 实习 | MCP、Function Calling、Memory、Context 管理、多 Agent 协作 |

### 海外（英文源，按匹配度排序）

| 公司/来源 | 岗位 | 薪资 | 关键要求（与本框架的对应） |
|-----------|------|-----------|------------------------------|
| **Sumo Logic**（美国，08-17 发布） | Staff SWE – Core AI Platform（**MCP & Agent 基础设施**） | Staff 级（未公开） | **Java/Scala/Go/Python** + 自建 MCP Server 基础设施（托管/联邦/编排）+ **多租户执行** + OAuth/密钥 + **可观测性/限流/配额** —— 与 mcp-server/mcp-monitor/mcp-tenant/mcp-auth 逐项对应 |
| **Exerizon**（波兰华沙，B2B 远程，窗口内活跃） | Mid-level Java Engineer（AI Agents, MCP） | B2B 时薪制 | **Java 17+ Spring Boot + Spring AI MCP** 给保险巨头建 MCP Server，JSON-RPC 2.0 + SSE 传输层 —— Java+Spring 栈直接匹配，保险/金融场景 |
| **Recruitment Room**（远程拉美，08-26 发布） | MCP Expert（RL 训练环境） | **$60-120/小时（年化 $124.8K-249.6K），100 个名额** | C++/Python/**Java**/Go/TS/Rust，为 AI 模型训练构建 MCP 工具 RL 环境（bug fix/feature/重构/性能优化）—— 门槛低、量大、可批量接 |
| **CriticalRiver Inc.**（印度海得拉巴） | Senior MCP Developer / AI Agent Engineer | 未公开 | Google Vertex AI Agent Engine / ADK / Gemini / **OAuth2.0 PKCE** / FastMCP / Workato，Python 优先 —— 云侧 MCP 基建岗 |
| **Sigma Software**（克拉科夫/远程，08-18 发布） | AI Solutions Architect（MCP/JS） | 架构师级 | FinTech 企业级 MCP 平台化（SDK/模板/认证/版本化）+ **「在 MCP、A2A、传统 API 间做架构选型」写作岗位职责** —— A2A 已进 JD |
| **MS Services Group**（纽约） | Agentic AI Engineer | **$155K-215K/年** | **Java/Python** + MCP servers + Claude Agent SDK |
| **PIMCO**（加州 Newport Beach） | Software Engineer（短期） | **$125K-240K/年** | Java + REST APIs + 微服务（资产管理场景） |

### 价格带速查（Inventiple《MCP 开发者雇佣指南 2026》）

| 雇佣方式 | 价格 |
|-----------|------|
| 自由职业（Upwork/Toptal） | 高级 $80-180/hr，中级 $40-80/hr |
| 全职（美国） | $170K-280K 总包 |
| 通用外包公司 | 单项目 $50K-200K |

## 二、Upwork 实单（直接可接）

| 项目 | 预算 | 周期 | 语言栈 | 与本框架的对应 |
|------|------|------|--------|----------------|
| **Build MCP Server for AI 内容平台**（08-27 发布，美国客户） | **$500-900** 固定价 | 1-2 周 | Node.js/TS/LangGraph | MCP server + **认证 + 审批门禁** —— 企业安全是卖点 |
| **MCP Gateway & Workflow 自动化平台**（月内发布） | $1,000 固定价 | 里程碑制 | TypeScript | MCP Gateway 测试/加固/连接器 —— 网关经验=本框架 |
| **Senior MCP/API Developer（ChatGPT 内容自动化）** | $22-29/hr 时薪 | 1-3 个月 | Claude/ChatGPT API | 私有模块化内容自动化（WordPress/社媒/邮件）—— B 端整合套路 |
| **Freelancer.com MCP Expert**（RL 环境） | min $50/hr | 持续 | 多语言含 Java | 与 Recruitment Room 同源批量活 |

> ⚠️ 观察：Upwork 上的 MCP 单子**金额普遍偏小（$500-1K）**，但量大、可复购；真正的大钱在**全职岗（$125K-280K）和企业平台项目（$50K-200K/项目）**。用开源框架打口碑 → 接小单练手 → 冲全职/平台项目是当前最优路径。

## 三、平台级事件（改变游戏规则）

1. **Upwork 官方 MCP Server 上线**（08-10 官宣，mcp.upwork.com，OAuth 2.1 动态注册，免费）：AI Agent 可直接在 Claude/Cursor/ChatGPT 里搜人、发单、管合同。→ **freelancer 的 profile 必须结构化**（技能标签/时薪/可用性，否则 Agent 第一轮筛选就漏掉你）。行动项：把「Java + Spring AI + MCP Server」写进 Upwork 技能标签，时薪区间按高级档 $80+ 挂。
2. **A2A 并入 AAIF**（08-20）：协议格局定型，企业采购开始把「支持 MCP+A2A」当作合同条款。→ 本框架 V1.15 正好卡位。

## 四、A2A/协议生态数据（写博客/谈资用）

- A2A 支持组织：50+（2025-04 发布）→ 150+（2026-04）→ AAIF 成员 250+（2026-08）
- A2A SDK：Python/JS/**Java**/Go/.NET 五种官方语言；GitHub 22,000+ stars
- MCP：110M 月下载，官方注册表 9,652 Server，15,000+ GitHub 仓库，78% 企业 AI 团队生产在用；新 SaaS 接入耗时 18h → 4.2h
- Gartner：40% 企业应用 2026 底含 task-specific Agent（2025 <5%）；**>40% agentic 项目 2027 底被砍**（成本/ROI/风控）
- Deloitte：仅 21% 企业有成熟 Agent 治理模型 —— **治理（鉴权/审计/限流/多租户）就是本框架的护城河**
- 安全：A2A 伪造 Agent Card 攻击已有真实 exploit（2025）；A2A v1.2 引入签名卡片；AIUC-1 标准新增 23 项 MCP/A2A 协议安全控制
- 商业化：AP2（Agent 支付协议）60+ 组织；Google Cloud **AI Agent Marketplace** 允许 ISV 卖 A2A Agent 服务 —— 独立 Agent 产品化新渠道

## 五、用户 Java+Spring+AI 组合的卖点（对着 JD 卖）

| 企业要的 | 你的证明 |
|----------|---------|
| MCP Server 生产化经验 | 13 个模块的完整框架：安全（RBAC/API Key/OAuth2/EMA）、限流、审计、多租户三档隔离+生命周期、监控、Docker/K8s/CI |
| **A2A 双协议**（蚂蚁等明确要求） | V1.15 mcp-a2a 网关：Agent Card 自动派生 + JSON-RPC 分派 + 安全继承 |
| Java+Spring 工程化（保险/金融/大厂） | 100% Java 17 + Spring Boot 3.4，JDK17/21 双 CI |
| Spring AI / Spring AI Alibaba | mcp-alibaba：DashScope 通义千问原生集成；mcp-client-spring-ai 示例 |
| Dify/开源生态对接 | mcp-examples/dify 导入模板 + 集成指南 |
| 可观测性/治理 | mcp-monitor + 管理 API + 全链路审计 |
| 开源影响力背书 | GitHub 公开仓库、每日版本发布、中文社区稿（掘金/CSDN） |

## 六、本周行动项（面向挣钱）

1. **Upwork profile 结构化**：技能标签加 `MCP Server`、`Spring AI`、`A2A`；时薪挂 $80-120；availability 填真实值
2. **投 3 类单**：① Java 明确岗（Exerizon 类 B2B、MS Services）② $500-1K 小 MCP 单练手+好评 ③ 批量 RL 环境单（Recruitment Room，Java 可用，量大）
3. **内容卡位**：博客稿已就绪（docs/blog-java-mcp-a2a-2026-08-31.md），发掘金+CSDN；标题带「A2A 并入 AAIF + Java 双协议」
4. **注册表提交**：smithery 已配，补 mcp.so 提交（V1.15 带 A2A 新能力重新提交）