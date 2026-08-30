# 市场雷达 2026-08-30 — MCP 人才/需求/价格

> 数据源：web_search（yuanbao，freshness=week，中英文检索）| 统计窗口：2026-08-27 ~ 08-30（含窗口内仍有效的中长周期信号）

## 一、招聘信号（谁在招、什么价）

### 国内（中文源，重点标注 Java 栈）

| 公司/来源 | 岗位 | 薪资 | 关键要求（与本框架的对应） |
|-----------|------|-----------|------------------------------|
| **蚂蚁集团**（杭州，急聘） | 大模型开发（销售 AI Agent） | **25-50K·15薪** | **红线：「熟练掌握 LangChain、MCP、A2A 研发架构」+ 精通 Java/Spring** —— MCP+A2A 双协议明确写进 JD，本框架的 Streamable HTTP + 多租户即交付物 |
| **某大型互联网（猎头代招）**（上海） | AI 研发工程师（风控大模型） | **40-65K·16薪** | Java/Python + Agent/Copilot 模式下的 **Workflow/RAG/Tools 基建** + 模型推理系统 —— Tools 体系 = mcp-tools |
| **阿里巴巴集团**（杭州） | AI Agent 研发专家 | 未公开（大厂惯例高位） | Java 工程化 + **Spring AI** + Agent 工具体系/技能机制可插拔 —— 正是 mcp-spring-boot-starter + SPI 注册中心 |
| **小鹏汽车**（广州/北上深） | 后端研发（AI 应用方向） | **20-40K·15薪** | Java + Spring + MySQL/Redis，**LLM/RAG/Agent 优先** |
| **东易日盛科技**（北京） | AI Agent 开发工程师 | 20-40K/月 | 1-5 年，Coze/Dify/LangChain/LangGraph，工具调用/记忆/多智能体 |
| **步云科技**（厦门） | Java 开发（AI 方向） | 10-15K | Java + Spring + Docker + AI 场景挖掘 |
| **Recruit.net 收录某营销平台** | 技术专家（AI Agent 方向） | 未公开 | **JD 原话点名「MCP、RAG、上下文工程、LangChain、LangGraph、Spring AI、Spring AI Alibaba、AgentScope」** —— 技术栈与本仓库 100% 重合 |

### 海外（英文源，按匹配度排序）

| 公司/来源 | 岗位 | 薪资 | 关键要求（与本框架的对应） |
|-----------|------|-----------|------------------------------|
| **Glama（MCP 基础设施公司）**（远程） | 首个全职工程师（Full-Stack TS） | 未公开（创始团队早期股权） | **运营 30,000+ 用户的私有 MCP Server 托管 + FastMCP/mcp-proxy/mcp-client 开源框架 + MCP 目录/网关/托管/认证/可观测性** —— 「MCP 网关 + 托管」与 mcp-server/mcp-monitor 高度同构 |
| **Jobgether（代招，西班牙）** | MCP Engineer / AI Backend Engineer | 未公开（100% 远程） | MCP 服务器 + TS/Node + Cloudflare Workers，生产级后端工程素养 |
| **Ruby Labs**（EU/法国，远程） | MCP Engineer / AI Backend Engineer | 未公开 | MCP Server/Client 经验为 Strong Plus；重工程化（接口清晰/可观测性/错误处理）——正是企业化框架的卖点 |
| **Cotality**（Irvine/Dallas） | Senior SWE（MCP Servers） | $129K-160K/年（窗口内多次重发） | **Java + Python + Node 三栈** + MCP Server + Apigee X + OAuth/JWT/API key —— Java 明确在列 |
| **Sigma Software**（欧洲 FinTech） | Principal JS/Node.js MCP Engineer | Principal 级（未公开） | MCP 平台化：共享 SDK/模板/认证/版本化 —— 本框架定位一致 |
| **EPAM**（华沙，远程） | Senior Python MCP Engineer | 未公开 | 企业 Agent 平台：MCP server 模板 + OpenTelemetry + Lambda |
| **Anthropic**（SF/NYC） | MCP Engineer（Enterprise 团队） | $300K-320K | MCP Server 安全/可扩展性/合规审查，OAuth/SAML/OIDC（窗口内持续有效） |

### 薪资锚点（llmhire "The MCP Economy"，08-13，窗口内最全综述）

- **MCP Server Developer**：$150K-250K（企业）/$120K-200K（创业）/$200K-280K（MCP-native，要求上过生产）；
- **AI Integration Engineer (MCP 专精)**：$170K-290K（含安全背景溢价：金融 Goldman/JPM、医疗 Epic、SaaS Salesforce/ServiceNow/HubSpot 均在扩招）；
- **MCP Product Engineer**：$200K-360K（AI 原生公司）。

---

## 二、Upwork/外包即时需求（⭐ 新信号）

| 项目（时间） | 要求 | 价格/形态 |
|---|---|---|
| ⭐ **Upwork 官方 MCP Server 上线（08-10 发布）** | Upwork 发布官方 MCP Server（mcp.upwork.com/mcp，OAuth 免费），Claude/ChatGPT/Cursor 内直接发布职位/匹配人才/管理合同 —— **人才市场接入 AI 工作流成为正式产品** | 生态级事件：MCP 商业化通道更宽 |
| ⭐ **MCP Gateway & Workflow Automation 平台**（持续活跃） | 测试/修复已有 MCP Gateway（TS/Node），OAuth/webhooks/MCP servers 经验，用 Claude Code/Codex 开发 | **$1,000 fixed 起步里程碑**，50+ 提案红海 |
| ⭐ **Senior MCP/API Developer for ChatGPT 内容自动化**（活跃） | Phase0 架构验证 + Phase1A SEO 博客原型，分阶段签约，可转全职 | **$22-29/h，30+h/周** |
| MCP Developer for Custom Integrations（SaaS 类） | 自定义 MCP Server 连接 API/数据库，产出 89 单服务商定价参考 | $50（入门）/ $900（标准）/ $3,200（高级），1-10 天 |
| **Empiric Infotech（行业基准价目）** | 专职 MCP 开发者 | **$25/h 或 $2,000/月**；v1 MCP Server 固定价 **$15K-60K**（改单另计） |
| **Inventiple 雇主指南（$ 锚点）** | 高级自由职业者 $80-180/h，中级 $40-80/h；in-house $170K-280K 总包；通用 agency $50K-200K | 需求端定价体系成型 |

---

## 三、本周观察结论

1. **需求端从「会不会 MCP」升级为「有没有上过生产」**：NTT DATA 式「只消费过工具直接刷掉」的 JD 在扩散，Glama/EPAM/Sigma 都要「server 实现 + 平台化」经验 —— 本仓库的贡献记录 = 可验证的生产级履历；
2. **国内 Java 大厂 JD 密集点名 MCP/A2A/Spring AI Alibaba**：蚂蚁 25-50K·15薪、阿里、Recruit.net 收录岗，**Java + Spring AI 组合在国内企业 AI 基建投标中成硬通货**；
3. **MCP 网关/托管/可观测成为独立岗位品类**（Glama、Jobgether、Ruby Labs）—— 与 mcp-server + mcp-monitor + mcp-auth 的「企业化三件套」完全对位，是下一个可报价的交付单元；
4. **Upwork 官方 MCP Server 上线**（08-10）意味着：AI 代理直接求职/接单成为平台级能力，自由职业者应该把「MCP 作品集」做成标准简历项。

## 四、用户的 Java+Spring+AI 组合卖点（该赛道差异化）

| 卖点 | 证据/对应 |
|---|---|
| **生产级 Java MCP Server 落地经验**（非 TS/Python 转译） | 80%+ 竞品是 Python/TS；Java 生产级实现稀缺，而国内大厂 JD 恰好要 Java |
| **企业安全闭环**（RBAC/OAuth2/SSO/JWT/API Key + 审计 + 限流） | 直击 NTT DATA/Cotality/Anthropic JD 的 security/enterprise compliance 要求 |
| **多租户三档隔离 + 生命周期管理**（Row/Schema/Instance + 管理 API） | WFNext 面试题「多租户 MCP Server 设计」；$40K-80K 外包档完整演示能力 |
| **Spring AI Alibaba 原生兼容** | 蚂蚁/阿里/国内企业 AI 基建投标的技术栈对齐 |
| **开源可验证**（V1.0→V1.14，100+ 测试，CI 全绿，Maven Central 就绪） | 简历/提案里直接贴 GitHub 即可，减少信任成本 |