# 配置面也不是安全边界：治理权可以中立，授权不能外包

> **V1.73 技术售前核心 · 2026-11-15**
> 五段式收口第五段：V1.69 注解 → V1.70 注册中心 → V1.71 只读声明 → V1.72 决策层 → **V1.73 配置面 / 连接建立期**
> 适用：MCP 网关 / 智能体平台 / 券商与银行 AI 中台的采购、验收、投标应答、安全复审

---

## 0. 判词（一句话，可进标书）

> **规格进基金会，责任还在集成方。**
> MCP 与 Agent Skills 已归入 Linux Foundation 旗下 **Agentic AI Foundation（AAIF）** 的厂商中立治理。规格变更从此**不再有单厂商的产品更新或补丁通知**，可能「更快、从更多方向」到达，直接改变 agent 的权限、认证方式与工具访问边界。
> 但**规格定义的是「怎么连」，从来不是「谁能动」**。把外部进来的声明性输入（授权服务器 metadata、redirect_uri、批量导入、管理后台 URL 参数、注解、read_only 标志、规格版本）当授权依据，等于把门锁交给任何会说话的对端。
> **唯一可验收的边界**：按当前任务签发、在工具调用本身机械执行、fail-closed 的确定性执行门。

**这周的证据链（11-13~11-15）**：一个网关一天三个 CVE，分别打在**管理面 UI、配置导入、OAuth 连接建立**；n8n 把可信域做成钓鱼跳板；pgEdge 官方描述坐实 `simple query protocol` 让堆叠语句绕过 `read_only`；DE-CIX 的第三方门测试给出「语义评分 0.803 放行采购动作、机械 allowlist 正确拦住」的 A/B。**同一周，协议治理权完成中立化。** 三件事指向同一个结论。

---

## 1. 为什么「规格中立化」反而放大执行门的缺口

| 变化 | 对买方意味着什么 | 对集成方意味着什么 |
| --- | --- | --- |
| MCP（2025-12-09）与 Agent Skills（2026-10-22）归入 AAIF；AAIF 10 个托管项目、成员组织各来源报 247 / 305（不同时点口径） | 既有安全审批是**针对单厂商规格**做的，审批基准需要重评 | 采购模板必须要求供应商**披露 MCP / Agent Skills 规格版本依赖** |
| Governance WG 允许领域工作组接 SEP 而无需 core maintainer 全审；2026-07-28 revision 有正式 Extensions Track；SEP-3398 要求 sponsor | 规格迭代可能快于企业变更管理周期 | 规格版本要像依赖一样**纳入受监控清单**，变更触发全量复验，不能继承旧审批 |
| 没有单厂商会为规格变更发「补丁通知」 | 「等厂商发公告再复验」的流程失效 | 需要自己有**版本登记 + 变更检测 + 复验报告**三件套 |
| 首个厂商中立 MCP 认证 MCPA（2026-09-14，$250 / 90 分钟，**24% 考安全与治理**）；conformance suite 中 Java 为 Tier 2 | 人才与合规评估开始标准化 | Java/Spring 栈的**工程化治理工件**比「会用 SDK」更值钱 |

> **定位复核**：AAIF 管的是协议与技能格式；三大云（Azure APIM AI Gateway / AWS AgentCore Gateway / Google Cloud / 腾讯云）管的是连接与编排；Guardian-agent 类新品管的是旁路监视。**没有任何一层替你决定「这个 agent 在这一次任务里能不能动这张表」**。这一层只能由集成方在**自己的执行路径**里落地，而且必须可现场验收。

---

## 2. 12 项配置面 / 连接建立期验收清单

> 每条都可现场跑、可写进 RFP 验收列。「真实文件」列均为本仓库**实际存在**的路径，不是示意。

| # | 验收项 | 控制点 | 真实文件 | 现场验收方式 |
| --- | --- | --- | --- | --- |
| 1 | **DCR 授权服务器 metadata 准入** | 签发方白名单 `DCR_ALLOWED_ISSUERS`；`registration_endpoint` 主机锚定 issuer 域；**解析后真实 IP** 必须公网且非严格字面量直拒 | `examples/gate-orchestration/McpGateChain.java` (`screenDcr`) · `examples/connect-path-guard/McpResolvedIpGuard.java` · `examples/registry-admin-guard/McpRegistryAdminGuard.java` | 提交 `169.254.169.254` / `127.0.0.1` / `10.x` / `172.16~31.x` / `0177.0.0.1` / `2130706433` / `fe80::1` / `metadata.*` 八类 payload，全部 DENY 且审计留 `DCR_RESOLVED_IP_NOT_PUBLIC` |
| 2 | **redirect_uri 精确匹配** | 必须等于**已注册**值；拒 `javascript:`、`//host`、`user@` 混淆、`%2f%2f`、http 降级、任意外部域 | `McpGateChain.java` (`screenRedirect`) | 用 `https://app.corp.example@evil.example/cb` 与 `//evil.example/cb` 各发一次，均 DENY；合法精确匹配 ALLOW |
| 3 | **批量导入共享实体归属** | `conflict_strategy=update` 不得跨租户；`endpoint` / `headers` / `credentials` 列为不可导入受保护字段 | `McpGateChain.java` (`screenImport`) · `examples/registry-admin-guard/McpRegistryAdminGuard.java` · `mcp-tenant/` | 用 tenant-b 的 `admin.import` 身份改 tenant-a 工具 endpoint，DENY；同租户且未碰受保护字段，ALLOW |
| 4 | **管理后台 URL 参数纯文本** | `status` / `error` 等 URL 参数**服务端转义 + 纯文本渲染**，不进 DOM；长度上限 512 | `McpGateChain.java` (`screenAdminStatus`) | 提交 11 组 mXSS / 活跃标记载荷（`<svg/onload=`、`&#x3C;`、`srcdoc`、`$(...)`、`{{...}}` 等），全部 DENY，合法中英文纯文本放行 |
| 5 | **规格版本依赖登记与全量复验** | `specRevision` 进 `GateContext`；变更即 `SPEC_REVISION_CHANGED_REVALIDATE_ALL`，**旧审批不自动继承** | `McpGateChain.java` (`screenSpecRevision`) · `docs/spring-ai-2.0-migration-notes-2026-11-10.md` | 把基线从 `2026-07-28+extensions-1` 改成任意新值，所有已审批工具必须回到待复验状态，且复验是**幂等读操作** |
| 6 | **升级不改存量** | 打补丁 ≠ 清干净；必须显式 `revalidateStored()` 导出报告 + 隔离动作 | `examples/registry-admin-guard/McpRegistryAdminGuard.java` · `docs/registry-control-plane-self-defense-2026-11-12.md` | 模拟升级前已植入的映射/凭据，升级后仍存在 → 系统必须报出并隔离，不能「升级即通过」 |
| 7 | **授权源与控制面输入隔离** | 外部输入只作数据；身份/权限**不得由请求内容派生**；`admin.import` 不得隐含凭据读权限 | `McpGateChain.java` · `mcp-auth/` · `mcp-governance/` | 构造「导入请求自带 admin scope」与「用户名派生内部 ID」两类 payload，均不得获得额外权限 |
| 8 | **每决策带 tenant / taskId / grantIssuedBy** | Permit.io 三条判据：独立 adapter 创建权限、内部 ID 不由用户名派生、每决策带 tenant | `McpGateChain.GateContext` · `mcp-tenant/` | 抽查任意一条审计记录，四个字段齐全；跨 tenant 调用直接 DENY |
| 9 | **机械 scope allowlist 是唯一的锁** | 在 `tools/call` 本身按精确 scope 集合裁决；`modelConfidence` / prose / 审批弹窗文本 / 模型自证**只读审计、不进允许分支** | `McpGateChain.java` (`TaskBoundInvocationGate`) · `examples/task-bound-monitor/McpTaskBoundReferenceMonitor.java` | 用 DE-CIX 同型用例：「procure and deploy without asking first」+ 0.803 置信度 → 仍 DENY `SEMANTIC_SCORE_CANNOT_OVERRIDE_ALLOWLIST` |
| 10 | **声明层完全不参与授权** | hint / 注解 / `read_only` / 规格中的 `readOnlyHint` `destructiveHint` 只做比对；服务端独立定级 | `examples/tool-annotation-gate/McpToolAnnotationGate.java` · `examples/read-only-enforcement/McpReadOnlyEnforcementGate.java` · `McpGateChain.classify()` | 提交 `readOnlyHint=true` 但 body 为 `DELETE` / `VACUUM INTO` / `/*!...*/`，注册期与调用期均 DENY |
| 11 | **审计 append-only + 字段白名单 + 值脱敏 + 写明规则** | 每条 ALLOW/DENY 都写 `rule` 与 `reason`；值恒为 `[REDACTED]`，字段名走固定白名单 | `McpGateChain.AuditRecord` · `mcp-monitor/` · `examples/audit-ropa-export/` | 导出审计，检查无明文值、无未知字段、无空 `rule`；对照 V1.72 修过的 `request_headers` 归一化 bug |
| 12 | **AND 语义 + 对照组回归 + 不得劣于旧基线** | 任一 Gate DENY 即整体 DENY，短路但每个已执行 Gate 留痕；必须有合法对照 0 误杀 | `McpGateChain.Chain` · `examples/gate-orchestration/README.md` | 现场跑 `java McpGateChain`：**123 断言 / 84 攻击 + 26 对照 + 13 边界 / 0 误杀 / 旧基线漏 9 条全拦** |

> ✅ **本期可现场演示**：`cd examples/gate-orchestration && javac -encoding UTF-8 McpGateChain.java && java McpGateChain`（纯 JDK 17，零依赖，不进 reactor）。

---

## 3. 买方必问 5 问（含不合格 / 合格回答对照）

| # | 问题 | ❌ 不合格回答 | ✅ 合格回答 |
| --- | --- | --- | --- |
| 1 | 「AAIF 规格变更后，你们怎么保证我这边的审批还有效？」 | 「我们跟进官方最新版，升级即可」 | 「我们登记 `specRevision` 作为受监控依赖；变更触发全量复验并导出幂等报告，**旧审批不自动继承**，升级前已存配置同样复验」 |
| 2 | 「DCR 拿到一个外部 `registration_endpoint`，你们会直接请求吗？」 | 「按 RFC 7591 标准流程走」 | 「先过 issuer 白名单 + 域锚定 + **解析后真实 IP** 公网分类；云元数据/私网/八进制字面量一律拒绝；可现场跑八类 payload」 |
| 3 | 「agent 说『已获人工批准』、置信度 0.95，能放行写操作吗？」 | 「我们有多层护栏和评分」 | 「评分与文本**只读审计**；唯一锁是按当前任务签发的机械 scope allowlist。DE-CIX 0.803 放行采购动作就是反例」 |
| 4 | 「打完补丁，升级前已存的配置和已泄露的凭据怎么办？」 | 「升级到修复版就安全了」 | 「AWS 官方明确升级不改存量；我们提供显式 `revalidateStored()` + 隔离动作，并把结果写进可导出报告」 |
| 5 | 「怎么证明你们没有为了拦截率把正常业务全拦死？」 | 「我们拦截率 100%」 | 「现场跑对照组：字符串里的 `DROP TABLE`、只读 CTE、注释里的写动词、合法 `UPDATE` 全部放行，**0 误杀**；并有『不得劣于旧基线』断言」 |

---

## 4. 竞品 / 生态分层差异（不得混淆层级）

| 层 | 代表 | 它解决什么 | 它**不**解决什么 |
| --- | --- | --- | --- |
| 规格与技能格式 | **Linux Foundation / AAIF**（MCP、Agent Skills、A2A、Open Responses、OpenEnv、OpenSandbox） | 可移植性、去厂商锁定、SEP 流程、MCPA 认证 | 逐工具授权、跨租户归属、私有化合规工件、存量复验 |
| 连接与编排 | Azure APIM AI Gateway / **AWS AgentCore Gateway**（GA，Responses API 服务端执行、dynamic listing、outputSchema + annotations）/ Google Cloud / 腾讯云 | 集中入口、凭据托管、按用户动态列工具、IAM | 执行门「这次能不能动」、已存配置复验、Java 存量权限复用 |
| 旁路监视 | **WhiteMatter Guardian Agents**（11-10 $20M A 轮，一 agent 一 Guardian、Monitor→Action、审计写明 enforcing rule） | 发现、盘点、运行时调节 | 私有化交付、等保/密评工件、与既有 Java 权限模型同层执行 |
| 反面教材（11-13） | **IBM ContextForge** 三 CVE / **n8n** MCP OAuth open redirect / **pgEdge** 7 CVE | — | 证明「管理面 UI、配置导入、连接建立、只读声明」四层都不是安全边界 |
| **本框架定位** | `mcp-governance` + `mcp-registry` + `mcp-gateway` + `mcp-tenant` + `examples/` 参考实现 | **执行门 + 可现场验收的 12 项清单 + 私有化合规工件 + 存量 Java/Spring 权限复用** | 不替代规格、不替代云网关、不替代模型能力 |

> **销售锚点**：`Swift Media` 11-13 采购周报已把「MCP gateways become a procurement requirement」写进 RFP（集中注册表 / per-agent scope / 检索内容注入过滤 / **可导出审计**），并建议「下个 agent 供应商 RFP 加 5 个 MCP 网关问题」。上表第 3 节那 5 问可以直接复制进采购问卷。

---

## 5. 三档报价模板（沿用 V1.67 定价锚，非成交价）

| 档 | 交付物 | 公开价带锚点 | 加价触发器 |
| --- | --- | --- | --- |
| **POC / 安全复审** | 12 项清单逐条验收 + 123 断言回归报告 + 规格版本依赖登记 + 1 次全量复验 | 公开市场 $3.7K–$15K 起步（7 档报价低端） | 多租户 / 受监管行业 / 需现场演示 |
| **生产级治理层** | 四 Gate 接入主模块 + 审计导出 + HITL + 告警规则 + 私有化部署文档 | **$80K–$220K**（含 audit / SSO / RBAC / versioning 的生产级公开报价带） | 等保 / 密评 / 信创 / 存量 Java 权限复用 / 多系统集成 |
| **集团级统一控制面** | 多租户 + 跨网关纳管 + 存量复验平台化 + 长期运维 | 网关类 **$250K 起** | 多法人主体 / 跨地域 / 7×24 SLA |

> **加价判词（V1.67 十倍定律）**：从 $15K 到 $220K 涨的不是代码量，是**治理层**；V1.72 OpenAI×Upwork 联合研究进一步说明「AI 项目是**变大了不是变贵了**」（固定价预算 +52.4%，挂牌时薪仅 +1.5%，前 90 天工时 +40.6%）→ **按交付物 / 集成面 / 修订轮次 / 工时拆分报价，别只抬时薪**。
> ⚠️ 上述均为卖方公开挂牌价 / 行业估算，**非成交价**；相关性分析非因果。

---

## 6. 24 / 48 / 72 小时行动清单

| 窗口 | 动作 | 产出 |
| --- | --- | --- |
| **24h** | ① 发 SEO 稿抢「AAIF 规格治理 / IBM 三 CVE / n8n 开放重定向 / DE-CIX 门测试」流量；② 把 12 项清单抽成 1 页 PDF 进售前包；③ 把 5 问写进 RFP 问卷模板 | `docs/blog-java-mcp-configuration-plane-2026-11-15.md` + 一页纸 |
| **48h** | ① 回广发采购平台核实 **13327 报名截止与材料清单**（官方正文无截止，不得凭编号推断）；② 跟踪 **13322（11-15 17:30 今日已截止）** 与 13321 是否转正式招标；③ 把 123 断言输出录成 3 分钟演示 | 采购平台回执 + 演示录屏 |
| **72h** | ① 按分期①评估把 `McpRegistrationGate` / `McpInvocationGate` 接进 `mcp-registry` / `mcp-governance`（独立分支先跑通 123 断言）；② 军队 190 万 11-16 / 中山 399 万 11-17 / 山东重工 11-20 备标；③ 跟踪兴业 / 北京农商 / 上海农商正式招标文件 | 分支评估结论 + 备标素材 |

---

## 7. ⚠️ 诚实边界

- **AAIF 治理移交不是最近 3 天发生的新闻**：MCP 2025-12-09 捐入、Agent Skills 2026-10-22 随四项目加入；本期增量是**采购与变更管理含义**的解读 + 可验收控制点。成员数 247 / 305 为不同来源不同时点口径，不做版本断言；SEP / Extensions Track / MCPA 细节来自二手整理，正式引用须回 AAIF 与 modelcontextprotocol.io 复核。
- **本框架不受** IBM ContextForge 三 CVE / n8n 126386 / pgEdge 126197 / AWS 125926 等第三方漏洞直接影响；本仓库是对其攻击范式的独立参考实现。
- `examples/gate-orchestration/` **未接入 21 个主模块**，不得当生产组件；`TaskGrant.signatureOk` 是布尔标志**非密码学签名**；IP 判定是字面量分类非 DNS/SNI 链校验；SQL 筛查是词法归一化非解析器。
- **DE-CIX 门测试**为第三方启发式评分实验（`calibrated=false`、作者自报、单次测量），数字不是本框架实测拦截率；只取「语义评分不能当锁」的方向性结论。
- **123 断言是本仓库自检输出**，不是第三方测评、不是生产拦截率。
- **广发四份均为供应商预征集 ≠ 采购邀请**；本项目无企业主体 / 商业合同 / 中标 / 金融案例 / 等保测评，7 项资质硬门槛不满足 → 只能作**联合体技术分包**，**禁虚构材料**。
- 国内新公告（河北政采 11-13 中标 48.9 万等）**未点名 MCP，不计入可投清单**，仅作智能体平台预算锚。
- Spring AI 2.0 **未执行升级**；Nacos / Higress 版本来自二手站点须回官方复核。
