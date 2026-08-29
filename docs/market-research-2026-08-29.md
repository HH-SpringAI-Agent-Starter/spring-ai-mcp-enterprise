# 市场雷达 2026-08-29 — MCP 人才/需求/价格

> 数据源：web_search（yuanbao，freshness=week，中英文检索）| 统计窗口：2026-08-26 ~ 08-29（含少量窗口内仍有效的长周期信号）

## 一、招聘信号（谁在招、什么价）

### 海外（英文源，按匹配度排序）

| 公司/来源 | 岗位 | 薪资 | 关键要求（与本框架的对应） |
|-----------|------|-----------|------------------------------|
| **Anthropic**（SF/NYC，Enterprise 团队） | MCP Engineer | **$300K-320K** | 评审伙伴 MCP Server 架构的 security/scalability/enterprise compliance；OAuth 2.0/SAML/OIDC；远程/本地部署模式；金融/生命科学/医疗垂直 **——逐条命中 mcp-core（RBAC+OAuth2+审计）+ mcp-server** |
| **NTT DATA**（Hyderabad，Senior，7+ 年） | MCP and Enterprise Integration Engineer | B2B 未公开（大厂惯例中高） | **JD 红线：『Only consumed MCP tools; no server implementation』直接刷掉**；要求 OAuth2/OIDC/mTLS、工具契约、审计、可观测；Java/Python/TS **——『生产级 MCP Server 实现经验』就是我们交付物本身** |
| **Cotality**（Irvine/Dallas，Hybrid） | Senior Software Engineer (MCP Servers) | **$129K-160K/年** | **Java + Python + Node 三栈**；MCP Server 暴露内部 API/数据；Apigee X；OAuth/JWT/API key（数据脱敏）**——Java 明确在列，国内 Java 工程师可直接投** |
| **Sigma Software**（欧洲 FinTech） | Principal JavaScript/Node.js MCP Engineer | 未公开（Principal 级欧洲偏上） | 企业级 MCP 平台化：共享 SDK、模板、认证、版本化——「MCP 平台工程化」与本框架定位一致 |
| **Hirify**（SF onsite） | Staff SWE (MCP & Developer Platform) | 未公开 | 直接贡献 MCP 规范 + 开源 MCP Server；服务 40 万企业客户 —— **开源影响力变现岗** |
| **Banyan Software**（远程） | Senior Cloud/AI Integrator (MCP/A2A) | 未公开 | Keycloak 网关鉴权 + MCP/A2A + AWS EKS；C#/Python/Node/Go/**Java** |
| **SKM Group**（欧洲远程） | Senior Backend SWE (MCP & AI Agents) | **€54K-120K** | FastMCP + OAuth2/SSO/JWT + Azure Entra；Redis 分布式状态 |
| **EPAM**（Katowice，截止 10-02） | Senior Python MCP Engineer | 未公开 | 企业 Agent 开发平台：MCP server 模板、OpenTelemetry、AWS Lambda |
| **CriticalRiver**（Hyderabad） | Senior MCP Developer / AI Agent Engineer | 未公开 | Google Vertex AI + OAuth2 PKCE + MCP Server（FastMCP/Workato） |
| **Akvelon**（欧洲远程） | Middle+ SDE with MCP Experience | B2B | C# 或 Python + MCP Server + 企业级 Web 服务 |
| **CAI**（印度远程 Contract） | Python MCP Developer | 未公开 | FastMCP + OAuth2/API key + AgentCore Gateway |

### 薪资锚点（llmhire “The MCP Economy”，08-13，仍为窗口内最全综述）

- **MCP Server Developer**：$150K-250K（企业）/ $120K-200K（创业）/ $200K-280K（MCP-native 公司，要求上过生产）
- **AI Integration Engineer (MCP 专项)**：$170K-290K（含安全背景溢价：金融 Goldman/JPM、医疗 Epic、SaaS Salesforce/ServiceNow/HubSpot 均在扩招）
- **MCP Product Engineer**：$200K-360K（AI 原生公司）
- **印度外包价（WFNext）**：全职高级 MCP 开发 **$7K-12K/月**；Build engagement 4-8 周固定价；**面试明确考察『多租户 MCP Server 设计』（token scoping / per-tenant quotas / audit trails）——正是 V1.11-13 的能力**

## 二、Upwork/外包即时需求（3 天内新信号 ★）

| 项目（时间） | 要求 | 价格/形态 |
|---|---|---|
| ★ **MCP Developer for Two Custom Integrations（Mindbody + Attentive）**（2 天前，Upwork） | 为 Claude Cowork 建 2 个自定义 MCP Server（门店管理 + 营销短信） | Hourly / Expert / <1 个月，<30h/周 |
| ★ **AI Infrastructure Engineer - MCP, LLM Providers, Guardrails, Auth**（3 周，Upwork，Gurgaon 优先） | MCP transports/tool schemas/tool-level authorisation；OAuth 2.1 PKCE；Docker Compose 排障 | $10-15/h，hourly，3-6 个月 —— **价低、可当作练手+背书的入口** |
| AI Engineer - MCP Gateway & Workflow Automation（4 周） | 已有 MCP Gateway 的测试/修复/新 connector；Claude Code 等 AI 工具重度使用 | $1,000 fixed 起步里程碑，50+ 提案（红海） |
| Sports Betting 数据管道 + MCP Server + OpenClaw Agents（3 周） | 30 家竞对赔率采集 → 归一化 → MCP 暴露 → Agent 定价 | 未公开（生产级，长期） |
| Senior MCP/API Developer for ChatGPT 内容自动化（活跃） | Phase0 架构验证 + Phase1A SEO 博客原型，分阶段签约 | **$22-29/h**，30+h/周，可转全职 |
| 行业基准（Empiric Infotech） | 专职 MCP 开发者 | $25/h 或 $2,000/月；v1 MCP Server 固定价 **$15K-60K**（改单另计） |

## 三、中国企业侧信号（供应链名单 + 入选参考）

今日头条 08-03《模型上下文协议(MCP)核心供应链》列出 14 家 A 股公司已官宣 MCP 布局，可作为**国内客户名单与方案对标**：

| 公司 | MCP 动作 | 对本项目的含义 |
|---|---|---|
| 金山办公 | WPS AI 全面兼容 MCP，超百类第三方工具接入 | 办公生态 MCP 化标杆 |
| 用友网络 | BIP 大型企业 ERP 原生适配 MCP + A2A | 企业软件 MCP 化的头部样板 |
| 恒生电子 | 金融 AI 底座搭载 MCP 订阅服务，对接上百家券商/基金 | **金融 MCP 招标路径：跟着恒生生态做券商交付** |
| 宇信科技 | 自研 MCP Server 重构金融 AI 底层，服务超百家银行 | **银行 MCP 集成商，是潜在甲方/集成伙伴** |
| 南天信息 | WebAE 银行开发平台试点多家股份制银行 | 银行 AI 改造全链路 |
| 山石网科/启明星辰 | 安全大模型 + MCP 工具封装（50+/30+ 原子能力） | 安全赛道 MCP 工具化 |
| 鼎捷数智 | 工业 MCP 标准化组件 | 制造场景 |
| 赛意信息 | 善谋 GPT 企业 AI 中台支持 MCP，打通 ERP/MES | 珠三角制造集群交付通道 |

**结论**：国内 MCP 已从「概念」进入「上市公司财报布局清单」；金融（宇信/恒生/南天）、安全（山石/启明）、工业（鼎捷/赛意）三条线是最现实的 B 端交付通道。另外，Gartner 预期 2026 年底 40% 企业应用内嵌 task-specific AI agent，Forrester 预期 30% 企业应用厂商自研 MCP Server——「Does it speak MCP?」已进入采购评估清单（vertexagility）。企业 MCP 网关类需求（SSO + RBAC + 每工具限流 + 全量审计 + 用量分析，见 Carmatec/Patoliya 服务页）与本框架 mcp-core/mcp-server/mcp-monitor 能力一一对应。

## 四、用户（Java + Spring + AI）在本赛道的卖点

1. **稀缺供给**：MCP 生态 80%+ 是 Python/TS，Java 生产级实现几乎空白；而 JD 满天都在要 Java（Cotality 三栈含 Java、NTT DATA Java/Python/TS、Banyan 含 Java）——**供给错配就是溢价来源**；
2. **框架即简历**：spring-ai-mcp-enterprise 已覆盖 JD 高频词：RBAC（NTT DATA：authorization）、OAuth2/EMA（Anthropic：OAuth）、限流（SKM：performance）、审计（Patoliya 采购清单：audit trails）、多租户三档（WFNext 面试题）、可观测（EPAM：OpenTelemetry）——投递时直接附架构图 + GitHub 链接，对「Only consumed MCP tools」的红线形成降维打击；
3. **可演示交付物**：实例级多租户（V1.13）+ 即将落地的生命周期管理 API（V1.14）组合，正好对上外包报价单「Enterprise multi-tenant MCP server $40K-80K」档；
4. **两条变现路径**：(a) Upwork 单子（$22-29/h 起步、Mindbody 双集成单 <1 个月）作为现金流 + 案例背书；(b) 国内金融/安全/工业集成商生态（宇信、恒生、山石等）做技术合作/劳务输出。

## 五、行动清单（本周）

- [ ] 完善 Upwork 简介与作品集（V1.13 架构图 + 三档隔离博客）→ 投 Mindbody+Attentive 双集成单
- [ ] 把「多租户三档隔离」整理成英文 README 亮点段 + 录 1 分钟演示 demo 链接
- [ ] 跟踪 NTT DATA / Cotality（Java 明确在列）JD 并准备针对性简历
- [ ] V1.14 租户生命周期 REST API 开发启动（消费 TenantInstanceRegistry）