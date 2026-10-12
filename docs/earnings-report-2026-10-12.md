# 收益 / 招标 / 招聘 情报日报 — 2026-10-12（近 3 天：10-09 → 10-12）

> 项目：spring-ai-mcp-enterprise（Spring AI MCP Enterprise 企业级 MCP Server 框架）
> 目标：扩大开源影响力 + 挣钱。本报告为 Task 6「挣钱部分」每日产出，配套 SEO 稿见 `blog-java-mcp-security-tender-2026-10-12.md`，版本说明见 `V1.40-release-notes.md`。
> 立场：代码侧 1–5（Alibaba 集成 / 多语言 Client SDK / CI-CD / Docker / 中文博客）早已交付并通过 CI，本期**无破坏性代码变更**，专注市场情报与治理护城河资产。

---

## 一、执行摘要（3 句话）

1. **政务与金融标全面进入"指名 MCP + 治理前置"阶段**：金融智能体 9 月招标 33 条、中标披露约 2109 万，银行占 57.6%，**MCP 协议、多智能体协同、可观测性与安全治理**已从技术话题变成采购文件具体条目——本框架 `mcp-governance`+`mcp-monitor`+`mcp-registry` 直接命中。
2. **安全治理护城河再获最强新证据**：10-10/10-11 集中爆发 CVE-2026-108585/108586（MCP 授权失败）、worldofai 在录 729 个 AI CVE、mcp-go 8.7、Splunk 9.1 RCE，反复印证"**认证 ≠ 授权**"结构性缺陷——正是 `mcp-governance` 的 Scope-ACL/HITL 对症面。
3. **Java + Spring + AI 仍是蓝海且需求在下沉**：猎聘/天津 10-12 当天新出 Java+MCP 岗，Upwork 30 天 2897 个软件开发岗中 AI/LLM 第二大类（597 个），"把模型接到业务工具/做 agent 跑工作流"成常态需求。

---

## 二、挣钱情报总览表

| 维度 | 近 3 天核心信号（10-09→10-12） | 对齐模块 | 挣钱动作 |
|------|--------------------------------|----------|----------|
| 🏛 政务/金融招标 | 金融智能体 9 月招标 33 条/中标约 2109 万；银行占 57.6%；MCP/多智能体/治理进采购条目；襄阳 45.43 万 10-16 截 | registry+governance+monitor | 投标 长庆10-16 / ZC10-20 / 襄阳10-16 / 湖北疾控11月 |
| 💼 企业招聘 | 猎聘 Java 25K·武汉·今日更新点名 MCP；天津 25-35k 石化点名 Spring AI/Agent Scope/多租户；阿里云/实习僧/法本延续 | gateway+alibaba+governance | 仓库即作品集直接投 |
| 🌐 接单/Upwork | Upwork 30 天 2897 开发岗（中位固定价 $200、时薪 $15-35）；AI/LLM 第二大类 597；upwork-mcp-rs 成接单基础设施 | 框架作交付底座 | 本框架 + Upwork MCP 获客 |
| 🛡 安全治理 | CVE-2026-108585/108586 + 729 AI CVE + mcp-go 8.7 + Splunk 9.1 RCE | governance+auth | 安全事件简报进标书/审计 |
| 🌱 生态 | Spring AI Alibaba 11k stars、Nacos+Higress MCP 架构范式、企业级 Multi-Agent McpRegistry 实战 | registry+gateway | "官方方案治理增强层"定位 |

---

## 三、招标 / 采购线索（可直接对标交付）

| 标讯 | 金额 | 截标 | 内容要点 | 对齐模块 | 紧迫度 |
|------|------|------|----------|----------|--------|
| 🔥 金融智能体采购（9 月汇总，10-12 腾讯新闻报道） | 中标约 **2109 万**（14 条披露） | 持续放量 | 银行占 **57.6%**；建行上海（科研经费管理智能体）、天津分行（财务共享智能合规审核）、中行上海（智能体安全管理平台）、中国进出口银行（一月 3 条：知识管理平台/AI 人力池/信贷评审智能体）、中国信达/长城 AMC 再现身；**MCP 协议+多智能体协同+可观测性+安全治理**成采购文件条目 | registry+governance+monitor | ★★★ 长期主线 |
| 🆕 中国电信襄阳·老河口市人工智能大模型赋能平台 | **45.43 万**（不含税） | **10-16 14:00** | 知识库管理 + 工单分派智能体 + 政务办公智能体 + 社会治理智能体调测；60 日交付+1年运维 | registry+governance | ★★ 本周 |
| 🔥 长庆油田「专题2·MCP 服务」 | **130 万** | **10-16（仅 4 天！）** | 油气生产管理多智能体，需时序数据 MCP 业绩 | registry+governance+tools | ★★★ 紧急 |
| 🔥 ZC26G280436「AI 网关、MCP 注册、多模态平台」 | 含 2 台智算 | **10-20 09:00** | 国产平台+MCP 组件+智算，不接受联合体 | registry+gateway+alibaba | ★★★ 紧急 |
| 宁夏 12316「助农增收智能体 MCP 试点」 | **287.2 万（已中标）** | 已公示 | MCP 首次写进政府采购标的名 | registry+governance | 续约/运营 |
| 湖北疾控「人工智能+预警预测」 | **150 万**（11 月采购意向） | 11 月 | MCP 插件接入国家平台+多租户+等保三级 | registry+tenant+governance | ★★ 预研 |
| 宁夏算力/AI 集采（宁夏建材智算服务器/宁夏电投/宁夏煤业 DEEPSEEK 租赁） | 未披露 | 滚动 | 政务 MCP + 算力双热点 | 交付生态 | ★★ 区域深耕 |

> **挣钱判断**：金融 + 政务"治理前置"是 10 月最强主线。银行系智能体安全管理平台、合规审核类需求，本框架 `mcp-governance`（HITL/审计/Scope/限流）+ `mcp-monitor`（可观测）是**现成卖点**。

---

## 四、招聘信号（Java + Spring + MCP / Agent，蓝海持续）

| 公司/平台 | 岗位 | 薪资 | 地点 | 点名技术 | 匹配度 |
|-----------|------|------|------|----------|--------|
| 🆕 猎聘·上市公司核心岗 | 高级 Java 开发工程师 | **25K** | 武汉·5-8年 | JD 原话"**MCP 协议接入、AI Agent、LangChain4j、RAG**"+Spring Cloud 高并发治理 | 100% |
| 🆕 天津·石化 | AI 开发工程师 | **25-35k** | 天津·3-5年 | **Spring AI / Agent Scope / RAG / 企业级智能体平台/AI中台 / 多租户隔离** | 95% |
| 沃尔玛(中国) | 高级 AI 平台 | 30-55k | 多城 | AI/MCP 网关+MCP Server+Skill Registry，Nacos/Higress/Spring AI 优先 | 100% |
| 阿里云 | AI 应用中间件 | 面议 | 含西安 | AI 网关、MCP 代理、RAG | 95% |
| 实习僧 | AI 应用开发(Java) | 18-30k | 北京 | MCP 工具与 Skills 体系 | 90% |
| 法本/一览/猎聘 | Java AI 方向 | 9k-30k | 重庆/深圳/广州 | MCP/Tool Calling/Agent/多模态 | 85% |
| Satrio/Darwin/OneSeven/Intellias/Cloudflare | MCP Expert / Registry / Portals | $60-120/hr·€66-91K | 远程/欧洲 | 生产级 MCP Server(Java)+OAuth+Vault | 100% |

> **结论**：Java+AI 复合岗 3–5 年 **45K–75K**，二线 25-35K·16薪，海外 MCP Expert（Java）€600-900/天。本框架 repo 即**作品集**，JD 技术栈 100% 重合，可直接投递。

---

## 五、接单 / Upwork 定价与趋势

| 指标 | 数据（10-09~10-12） | 出处 |
|------|---------------------|------|
| Upwork 开发岗总量 | 30 天 **2897** 个软件开发岗（≈97/天） | Vibeworker 6Sep–5Oct |
| 中位固定价 | **$200**（Upwork 中位 2 倍），75 分位 $670 | Vibeworker |
| 时薪中位 | **$15–35** | Vibeworker |
| AI/LLM 类 | **597** 个，第二大类别 | Vibeworker |
| 最热技能 | API 集成 726 > AI/LLM 597 > Shopify 425 | Vibeworker |
| 平台抽成 | 阶梯：前 $500 抽 20% / $500-10000 抽 10% / >$10000 抽 5% | mtrt.cn 实战指南 |
| 接单常态价 | MCP Server **$1,500–5,000/项目**、复杂 **$10,000+**；$120-300/hr（资深 $200+） | 历史+本期 |
| 基础设施化 | **upwork-mcp-rs**（Rust）成接单前端：双密钥见证人防幻觉提交、固定价里程碑、执行级高杠杆哨兵 | LobeHub |

> **挣钱动作**：本框架作**交付底座** + Upwork 官方 MCP（`mcp.upwork.com`）作**获客前端**。Vibeworker 明确指出"AI 工作多为：把模型接到业务工具、做客服 bot、做跑工作流的 agent"——正是 MCP Server 的甜区。

---

## 六、安全治理红利（最强护城河，10-10/10-11 新 CVE 集群）

| CVE / 事件 | 日期 | 严重度 | 缺陷本质 | 本框架对症模块 |
|------------|------|--------|----------|----------------|
| CVE-2026-108585（argocd-mcp `delete_application` 路径穿越） | 10-10 | 5.4 | MCP 工具绕过底层 RBAC，以 server 服务账户删 K8s 资源 | mcp-governance Scope-ACL + 身份透传 |
| CVE-2026-108586（@1mcp/agent 0.20–0.39 越权） | 10-10 | 5.4 | 低权客户端调他人 scope 工具（confused-deputy） | mcp-governance Scope + fail-closed |
| **核心论断** | — | — | "MCP server 把**客户端认证当成工具级授权**" | mcp-governance 全部能力 |
| JeecgBoot 任意登录用户读 MCP 密钥 CVE-2026-108671 | 10-10 | 7.1 HIGH | 权限检查被注释掉 | mcp-auth + 密钥隔离 |
| x64dbg-MCP 任意网络客户端跑调试器命令 | 10-09 | 9.3 CRITICAL | 无认证暴露全功能 | mcp-auth Bearer 强制 |
| **mcp-go through 1.2.1** 无界 body 读取 OOM | 10-11 | 8.7 HIGH | 9.1k stars 主导 Go MCP 实现，发布两天即被报 | mcp-core 传输层防护范式 |
| Splunk MCP Server 反序列化 RCE CVE-2026-76404 | 10-08 | 9.1 CRITICAL | 凭据管理组件反序列化未校验 | mcp-governance 输入校验 |
| Odoo MCP / phpMyFAQ MCP 绕过既有 ACL | 10-11 | 5.3 | 第二道门不带原权限模型 | mcp-governance ACL 兜底 |
| worldofai 在录 AI CVE 总量 | 10-11 | — | **729**（111 关键 / 352 高 / 5 个 CISA KEV） | 治理层全覆盖 |

> **护城河叙事**：反复出现"成熟产品长出 MCP 面，权限模型默认不带过来""认证 ≠ 授权"——本框架 `mcp-governance`（RBAC/Scope/ACL/HITL/审计）+ `mcp-auth`（Bearer 强制/jti/吊销）逐条对症。安全事件简报 `mcp-security-incident-brief-2026-10.md` 已可**直接进标书/安全审计报告**。

---

## 七、生态：Spring AI Alibaba 最新状态 + Nacos/Higress 架构范式

| 项目 | 状态（10-12） | 对本框架含义 |
|------|---------------|--------------|
| Spring AI Alibaba | **11k stars**（+399/月，近周 +28）、275 contributors、bus factor 4、Apache-2.0、需 JDK17 | Java Agentic 事实标准，但增速放缓（understory 负） |
| 内置能力 | SequentialAgent/ParallelAgent/RoutingAgent/LoopAgent + graph runtime + Nacos A2A + MCP 管理 + 多模态语音 | 框架可补其最缺的**安全治理层** |
| Nacos + Higress MCP 架构 | 注册（metadata: mcp-protocol/tenant/tools）+ 配置（统一 Key 热更新）+ 网关（key-auth 校验）+ MCP Server 端点 | 与本框架 `mcp-registry`+`mcp-gateway` **同款定位** |
| 企业级 Multi-Agent 实战 | 基于 Redis 的分布式 `McpRegistry`（心跳续约/能力快照/路由权重）+ `McpSecurityFilter` 自定义校验 | Java 侧治理范式成熟，本框架即"官方方案的治理增强层" |

---

## 八、Java + Spring + AI 赛道卖点（用户定位）

1. **不是玩具，是对齐大厂 JD 与央企招标的生产级方案**：JD 技术栈（MCP 网关/Server、Registry、Scope、多租户、Spring AI）与 repo 100% 重合。
2. **治理增强层定位**：Spring AI Alibaba 解决"能不能跑"，本框架解决"稳不稳、值不值、合不合规"——FollowAgents 评其"1 safety controls not found"正好由本框架补齐。
3. **可进标书的合规资产**：等保 2.0 三级对照表 + 安全事件简报 + 投标模板（ZC/长庆/襄阳），形成"框架 + 文档 + 标书"三位一体交付包。
4. **接单即获客**：Upwork 官方 MCP + 本框架底座，把"写一次 MCP Server 三端复用"的卖点直接变现。

---

## 九、明天做什么（候选行动）

1. **紧急投标冲刺**：长庆 130 万 **10-16 截**（仅 4 天）——用 `tender-asset-changqing` + 等保对照表 + 安全事件简报，备齐业绩证明+团队画像+中间量验收。
2. **襄阳 45.43 万 10-16 截**：草拟政务智能体（工单分派/社会治理）技术方案，复用宁夏中标叙事。
3. **ZC 10-20 扩正式标书**：融合安全事件简报作合规加分 + 西安岗本地交付叙事。
4. **发 SEO 稿抢流量**：`blog-java-mcp-security-tender-2026-10-12.md` 抢"MCP 安全/CVE/金融智能体治理/Java MCP"搜索词。
5. **强化 GitHub Topics**：MCP治理/等保/CISA KEV/Spring AI Alibaba/多租户/接单。
6. **评估主 mcp-alibaba 升 1.1.2.0**：GA 适配仍以独立示例呈现，待验证后再升级主模块（安全约束不变）。
