# MCP Server 挣钱情报日报（2026-11-24 · 近 3 天实时抓取）

> 任务 6 完整输出：企业需求 / 招标 / 招聘 / 开源动态 / 价格 / 用户 Java+Spring+AI 卖点。
> 抓取窗口 11-21~11-24，来源 18 项，全部附链接。⚠️ 报价为公开挂牌价/估算，非成交价。本期核心锚点「出站响应面 / 浏览器中继」三源交叉坐实。

## 一、今日最强信号（TOP 6）

1. ⭐⭐ **MCP Java SDK 官方洞把「浏览器变中继」写成事实** —— `CVE-2026-34237` / `GHSA-hv2w-8mjj-jw22`（11-22，CVSS v3 **6.1**）：`io.modelcontextprotocol.sdk:mcp-core` 在 SSE / Streamable HTTP 传输里下发 `Access-Control-Allow-Origin: *`（影响 `<1.0.0` / `<1.1.1` / `<0.18.3`，修 `1.0.1/1.1.1/0.18.3`）。攻击者页面让受害者浏览器打开 `https://internal-mcp-server/sse`，通配头令页面读到 SSE 首帧 session id，随后带 id 跨域 POST —— **浏览器成了网络位置，攻击者不需要到内网的可达路由**。Python SDK 两种传输都不发通配头 → Java 库替每个 server 做了一个跨域策略决定（[dev.to](https://dev.to/ioneyrezende/wildcard-cors-turns-an-mcp-server-into-a-browser-relay-b1o) / [GHSA](https://github.com/modelcontextprotocol/java-sdk/security/advisories/GHSA-hv2w-8mjj-jw22)）。⚠️ 本项目主仓核心基于**官方 SDK**，此为**官方 SDK 洞**，非本框架洞，须显式披露。
2. ⭐⭐ **OT / 工程工作站侧 MCP CSRF 首次拿到 CVSS 8.8** —— `CVE-2026-112136`（**8.8**）/ `112137`（5.4）（11-23）：Siemens Mendix Studio Pro MCP Server 远程交互 + 浏览器 CSRF，工程工作站任意 tool execution，可投毒其构建/部署的工业应用；官方缓解首条「**只绑 loopback + Origin/Referer allowlist**」——但浏览器 CSRF 路径恰好 rides 合法浏览器会话，loopback 不足（[BreachSpider 112136](https://breachspider.com/intel/2026-11-23-cve-2026-112136-siemens-mendix-studio-pro-mcp-server-exposes) / [112137](https://breachspider.com/intel/2026-11-23-cve-2026-112137-siemens-mendix-studio-pro-mcp-server-exposes)）。
3. ⭐⭐ **「参数以数据之名行控制之实」新靶** —— `CVE-2026-134817`（11-22/23，CVSS **7.1**，CWE-88）：`mcp-server-kubernetes <4.2.0` 把 dash 开头的位置操作数（`--filename=/etc/passwd`）原样交给 kubectl 当 flag 读宿主机文件，SSE / Streamable HTTP 均可达；修 `4.2.0` 加 `assertNotFlagLike()`；Sploitus 已发可复现 lab + PoC（[ThreatCluster](https://threatcluster.io/cluster/cve-2026-134817-kubectl-flag-injection-in-mcp-server-kuberne-87c0b84b)）。
4. ⭐ **企业侧把「MCP = 特权服务」写进采购论证** —— 第三方审计 414 台公网 MCP server **91.8% 无 OAuth**；MCP server 用自己的凭据而非调用者的，动态工具发现意味着安全团队批准部署时未预见的调用（[waseemahmad.dev](https://waseemahmad.dev/blog/mcp-server-business-case-vs-api-key)）。买方话术从「你有 MCP 吗」升级到「你能证明每个出站响应头、每个监听地址、每个工具实参都独立收口吗」。
5. ⭐ **公募基金 AI 进入体系化建设期** —— 南方基金设一级部门「AI 推进中心」、易方达设科技子公司；兴证全球**通过 MCP 整合基金检索/持仓披露**；华安基金 11-19 招标信创算力服务器限价 330 万、11-10 AI 算力物理服务器中标 395.6 万；协会规范「高敏感策略/风控数据只能在私有化隔离环境运行」（[算力之家](https://suanlizhijia.com/forum/ca7e0d3f-a6f6-4448-a7b1-0d637fcc1448)）。金融私有化 + MCP 网关是主战场。
6. ⭐ **Java+Spring+MCP 招聘量价齐稳、AI 编程工具成硬门槛** —— 掘金 11 月三份 JD 拆解：门槛不是 Python 而是「对 AI 产出负责」；智联郑州 Java 高级(AI) 1.5–2 万、猎聘烟台 AI 智能体(Java) 8–11k 逐字「Prompt/RAG/**MCP**/Agent 概念」、猎聘杭州 AI 全栈 15–30k·15薪；云南析数 10-15k 逐字「Agent 工具链(MCP)」+ Spring Cloud Alibaba + 多租户隔离（[掘金](https://juejin.cn/post/7710529903347335168) / [猎聘](https://m.liepin.com/job/1986606949.shtml)）。

## 二、哪些企业在招 MCP 人才 / 用 MCP（本期具体主体）

| 主体 | 事实 | 对位 |
| --- | --- | --- |
| **深圳北芯生命科技（11-22，AI 全栈工程师）** | 逐字「Java 业务系统 + RAG/Agent/Tool Calling，**将业务接口和数据能力封装为智能体 Tool**」，加分项「ChatBI / 数据中台 / 知识图谱本体 / **MCP**」 | 用户 Java+Spring+AI+MCP 全栈精准命中 |
| **云南析数科技（昆明，11-07，10-15k）** | 逐字「Agent 工具链(**MCP**) + LLM 网关 + RAG + NL2SQL」+ **Spring Cloud Alibaba** + 多租户数据隔离 + Docker/K8s + CI/CD | 与用户技术栈 + 多租户框架直接对位 |
| **苏州赤兔/汽车零部件（长沙，15-20k·14薪）** | 逐字加分项「LLM 应用落地：SpringAI/LangChain、RAG、Agent、**MCP**、向量检索，有线上运行案例」+ MES/ANDON/SRM 端到端 | 制造运营系统 + MCP 落地案例 |
| **Tidewell（美远程，EST/CST，8+ 年）** | Java/Spring Boot/Anthropic Claude；逐字「**Claude Code / MCP** 优化工程流程」+ Spring AI 直连 Claude + TDD/CI-CD | 海外 Java+Spring+MCP 远程锚（延续） |
| **兴证全球基金 / 南方基金 / 易方达** | 兴证全球通过 **MCP** 整合基金检索/持仓披露；南方设 AI 推进中心 + 自研智能体；易方达设科技子公司 + AI 投资陪伴团 | 金融买方已用 MCP，钱从「帮你建」转「帮你管」 |

## 三、哪些项目在招标 / 什么价

| 项目 | 金额/截止 | MCP 关联 | 可投性 |
| --- | --- | --- | --- |
| 华安基金信创算力服务器（11-19） | 最高限价 **330 万** | 算力基础设施，未点名 MCP | ⛔ 不计入可投清单，仅作公募 AI 预算锚 |
| 华安基金 AI 算力物理服务器（11-10） | 中标 **395.6 万** | 未点名 MCP | ⛔ 不计入，预算锚 |
| 广发四份征集（13321/13322/13327） | 13321/13322 已截 / 13327 官方正文无截止须核实 | 五件套 + MCP 安全工具管理 + MCP 封装接入 | 预征集≠采购邀请；7 项资质不满足 → **只能联合体技术分包** |
| 中原银行企业级 AI 网关 | 服务期 14 个月，1 家中标 | **AI 网关 + MCP 代理**写进正式公开招标 | 金融 MCP 网关主标位（延续备标） |
| 工信部电子五所 | 173.53 万，12-03 截，广州 | 未点名 MCP | ⛔ 不计入，广州本地联合体可考虑 |
| 中央民大 3712 万（11-25）/ 呼市 900 万（11-23） | 倒计时 | 含智能体应用场景 | 分包与案例背书窗口 |

## 四、什么价 —— 接单定价锚（本期新源：Launch Day Advisors 全量分档）

| 来源 | 价格 | 口径 |
| --- | --- | --- |
| **Launch Day Advisors MCP Server Cost（11 月，本期核心）** | L1 只读单客户端 **$100K–$300K**（~1 季度）；L2 动作单客户端 **$300K–$700K**（~2 季度）；L2 双客户端 **$420K–$1.2M**；L3 agent-resident **$1M+** | partner-built 公开估算；每增一客户端 1.4–1.7× |
| **同源自建 raw spend** | L1 $80K–$240K / L2 $200K–$520K / L2 双 $300K–$900K / L3 $700K+ | in-house 原始投入，不含运维 |
| **L2 单行项拆解** | 工具面 10–25 个 **$65K–160K**；OAuth+scope $45K–100K；safety story $45K–100K；审计+管理面 $30K–70K；server 实现 $40K–90K | **最贵的两项 = 工具面 + 安全故事**，不是 server 代码本身 |
| **持续运维** | MCP server **$5K–$25K/月**；hosting ~$20K/年；首次 SOC 2 Type II $50K–150K | 买方隐性成本，托管服务切入口 |
| **SnipeWork Upwork Market Index** | 周度 ~17,914 job / 均固定价 **$832** / 均时薪 **$24/hr** | 全平台总盘，非 MCP 专项 |
| 历史定价锚 | 生产级 $80K–220K；企业网关 $250K 起；Dawloom 7 档 $3,700–$72,000（19 倍价差） | 「涨的不是代码量，是治理层」 |

→ **报价策略**：Launch Day 的 L1 $100K 底价 = OAuth2.1+PKCE + 5–10 个工具 + 基础审计，正好对位本框架 21 模块；L2 的「写操作需幂等/可撤销/意图预览 + 审计强制」= 本框架决策层 + 结果面 + 身份面。**报价按交付物 / 集成 / 修订轮次拆，别只抬时薪**（OpenAI×Upwork 固定价 +52.4%、时薪仅 +1.5%）。

## 五、用户的 Java + Spring + AI 组合在该赛道的卖点（8 条）

1. **存量 Java 权限复用**：Spring Security / RBAC / 数据归属在 MCP 调用点复用，不重建身份体系——正对云南析数「Spring Cloud Alibaba + 多租户隔离」与公募「私有化隔离环境」。
2. **出站响应面首发能力（本期）**：CP1–CP6 对位 `34237`（Java SDK 通配 CORS）/ Mendix `112136`（CSRF + 非 loopback 绑定）/ k8s `134817`（flag 注入），是同质化 server 里**唯一把「响应头 + 监听地址 + 工具实参」三处独立收口做成可演示样品**的。
3. **官方 SDK 洞的「二次防线」定位**：本框架核心基于官方 SDK；`34237` 证明「升级 SDK 只移除一个 origin 绕过，反向代理仍可能重加通配头」——卖点是**部署后逐路径复查响应头 + 独立收口**，不是「我们没洞」。
4. **确定性执行门**：相对「in-prompt 护栏 100% 失守」，模型外确定性门是可验收边界（十段式），对位 Launch Day「safety story $45K–100K」。
5. **私有化合规工件**：等保/密评/备案 + 审计留痕 ≥180 天 + 字段脱敏 + 第三方 server 沙箱化，Python 系弱项、Java 主场（「不能上云的钱最好赚」）。
6. **Spring AI Alibaba 兼容层**：用户主技术栈；核心基于官方 SDK、Alibaba 可选不进主 reactor，规避生态停更风险。
7. **十段式可演示样品**：`examples/` 下 12 组纯 JDK 守卫，javac + main 自检全绿，售前现场可跑、进标书可当「可演示案例」。
8. **规格变更持续复验 + 定价权锚**：MCP 归 Linux Foundation 中立治理，规格变更无厂商补丁通知 → 把「规格版本当受监控依赖 + 变更后全量复验」做成年度托管；配合 L1 $100K / L2 $300K–700K / $5K–25K 月运维锚，把「写个 server」抬到「交付治理层」。

## 六、明天候选

1. 发 SEO 稿抢「MCP Java SDK 34237 通配 CORS / Mendix 112136 CSRF / k8s 134817 flag 注入 / 浏览器中继」流量。
2. 12 项浏览器/出站响应面验收清单 + 26 断言输出压 1 页 PDF，交广州本地联合体主体。
3. 独立分支评估把 CP1（ACAO allowlist）/ CP3（loopback 绑定）/ CP4（flag 拒绝）/ CP5（CSRF）接进 `mcp-gateway`（先跑通 26 断言）。
4. 备标中原银行 AI 网关（AI 网关 + MCP 代理）；回广发平台核实 13327 报名截止；跟中央民大 11-25 结果。
5. 跨期待办：`exportPrometheus()` 挂 actuator、dataClassification 审批引擎、重定向逐跳 SNI 校验、独立分支评估 Spring AI 2.0 升级。

## 七、来源清单（18 项）

见正文各段超链接，覆盖：dev.to、MCP Java SDK GHSA、BreachSpider（2 篇）、ThreatCluster、waseemahmad.dev、算力之家、掘金、猎聘（2 条）、job1001、Tidewell、 Launch Day Advisors（2 篇）、pdf-book-search、 zdnetinside、SnipeWork、广发智慧数平台、中原银行招标、工信部电子五所采购公告。

> ⚠️ **诚实标注**：本框架**不受**上述第三方 CVE 直接影响；`34237` 为官方 Java SDK 洞（本项目核心依赖），须显式披露并做部署后复查；Launch Day 报价为 partner 公开估算非成交价；招聘薪资为挂牌价；414 台审计 91.8% 为第三方数据非本框架实测；广发四份预征集≠采购邀请、7 项资质不满足只能联合体技术分包；中央民大/呼市/华安基金均未点名 MCP 不计入可投清单；Spring AI 2.0 未升级。
