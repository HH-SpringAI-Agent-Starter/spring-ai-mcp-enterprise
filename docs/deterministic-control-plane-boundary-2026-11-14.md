# 决策层验收清单：为什么「审批了」不等于「被管住了」

> V1.72 · 2026-11-14 · 技术售前核心文档，可直接进标书 / RFP 应答 / 现场 POC 验收
> 适用：金融、券商、保险、政务、能源、运营商等需要「智能体能做事，但出事能说清」的采购方。
> 配套可运行参考实现：[`examples/task-bound-monitor/`](../examples/task-bound-monitor/README.md)

---

## 0. 一句话判词

**「我们加了审批弹窗 / 系统提示词里写了不要执行危险操作 / 工具标了 readOnlyHint」——这三句话在 2026 年 11 月的公开实证面前，都不构成安全边界。**

四段式收口（本框架连续四期的核心叙事）：

| 期次 | 判词 | 被证伪的「声明层」 |
| --- | --- | --- |
| V1.69 | 注解不是安全边界 | `destructiveHint` / 工具描述 |
| V1.70 | 注册中心自己也不是 | 官方 Registry 的访问控制 |
| V1.71 | 只读声明也不是 | 连接串 `read_only` / `readOnlyHint` |
| **V1.72** | **审批与提示词护栏本身也不是** | **in-prompt guardrail / HITL 弹窗 / 模型自证** |

**唯一可验收的边界：模型之外、按当前任务签发、确定性执行、默认拒绝（fail-closed）的参考监视器。** 这不是我们的主张，是 11-12 公开的量化实验结果。

### 决定性数字（arXiv 论文，MindPattern 11-12 复盘）

| 指标 | in-prompt 护栏 / 模型自决 | 任务绑定的模型外确定性 monitor |
| --- | --- | --- |
| 模型提出攻击者工具调用 | 64%（Qwen2.5-1.5B）/ 84%（7B） | — |
| 实际被放行 | — | **1.3% / 7.7%** |
| 持久自适应攻击者下攻击成功率 | **100%** | severe actions **0%** |

实验设置：13 个任务、12 个工具、每防御每模型 156 个攻击 episode。**「monitor 是模型之外、知道当前任务允许哪些动作的确定性检查」**——与本框架四个 Gate 的设计原则逐字一致。

> ⚠️ 口径：该论文为学术模拟实验（Qwen2.5 小模型、13 任务 12 工具），证明的是**控制点位置**的重要性，不等于任何商用产品的实测拦截率。投标时不得把 1.3%/7.7%/0% 说成本框架的实测指标。

---

## 1. 本期新增公开实证（11-12 ~ 11-14）

| # | 事件 | 关键事实 | 命中本框架 |
| --- | --- | --- | --- |
| 1 | **IBM ContextForge MCP Gateway 双 CVE**（11-13 官方 Bulletin 首发，影响 v1.0.0~v1.0.11，统一修 v1.1.0） | `CVE-2026-110178`(4.1)：`/v1/import` 的 `conflict_strategy=update` **不校验共享实体归属**，持 team-scoped `admin.import` 的远程认证者可覆写**其他租户**公共 tool/resource/prompt 的 `endpoint URL`、`request headers`、`authentication credentials`，把后续 AI 网关调用重定向到攻击者主机；**官方 Workarounds: None**（只能升级 + 不把 `admin.import` 授给 team-scoped 角色）。`CVE-2026-110194`(5.4)：OAuth DCR 从 authorization server metadata 拿到的 registration endpoint **未校验即请求**，认证用户可让网关打内网；缓解 `DCR_ALLOWED_ISSUERS` 白名单 / `DCR_ENABLED=false` / 限制网关创建权限 | `mcp-tenant` + `mcp-registry` + `mcp-governance` + **CP3/CP4** |
| 2 | **AWS `CVE-2026-125926` 版本区间坐实**（11-13/14 NVD 更新） | 影响 **v1.0.3~v1.34.0**，**1.35.0 修复**；管理 API 洞 `CVE-2026-100307` 在 1.25.0 修 → **「跑 1.25.0+ 仍中 SSRF」**；官方明确 **升级不会修改升级前已存配置**，必须人工复核 upstream 地址、查日志、必要时轮换云角色凭据；CVSS 3.1 = 8.5，NVD 4.0 次要分 6.3；无 PoC、无在野利用 | **CP5 存量复验**（兑现 V1.70 遗留待办「跟踪 AWS GHSA 原文版本区间」） |
| 3 | **Permit.io / Authorizon 控制面授权长文**（11-13） | 三条通用判据：① `adapter:create` 是独立权限、rename 单独校验、ownership 显式记录且 transfer 须授权；② 内部 ID **不由用户可控名称派生**，按 tenant 前缀/命名空间，**409 Conflict 是安全信号不得吞**；③ 每个决策都带 tenant（"may user X update adapter Y" → "may user X **in tenant T** update adapter Y **in tenant T**"） | **CP2/CP3** 可直接引用为第三方规范背书 |
| 4 | **MindPattern 11-12 三连补丁复盘** | VS Code `GHSA-66hq-jw93-462h`（8.3/NVD 9.6）：server 控制的 elicitation 文本被解释成交互式 command link，用户点通知即命令执行，1.145.3 改为纯文本渲染；另两洞：`vscode:mcp/install` 链接携带 command-backed input definitions、legacy SSE server 可让 server 指定后续请求端点。官方参考 git server `GHSA-6hqx-gqrx-c5r2`：`core.fsmonitor` 让只读 `git_status` 执行代码（修法 `GIT_CONFIG_COUNT` 强制 false + 清 filter/merge drivers + 拒 Git<2.31）。`llm` 0.37.2 两处 OpenAI key 泄漏 | 共同判词「**客户端信任了服务端发来的东西**」→ **CP1 授权源隔离** |
| 5 | **广发研究 MCP & Skills 正式上线智慧数平台**（11-13 官方） | 5 项能力（研报库 MCP / 宏观数据库 MCP / 精品行业数据库 MCP / 股票盈利预测数据 MCP / 产业洞察 Skill），**21 个行业 / 200+ 数据源 / 9 万余核心指标 / 1000+ A股港股美股盈利预测 / 900+ 主题产业图谱** | 券商 MCP 从「征集/预研」转入**真实生产上线** → 卖点应从「帮你建 MCP」转向「**帮你治理已上线的 MCP**」 |
| 6 | **嘉信理财 Schwab 官方发布**（11-12 Business Wire / Yahoo Finance） | $13.33T 客户资产、Q3 日均 **1070 万笔**交易；MCP Server 从只读扩展到 **prepare / place / manage trades，但 all with the client in-the-loop**；当前仅 Claude pilot、20 席 $4,800 门槛排除 11,000+ 小 RIA；脚注自认「may provide inaccurate or incomplete results」；CEO 10-16 才强调要 "rules of the road" | 金融级 HITL 活广告；**流程上有人工确认，但确认本身不是执行边界** |
| 7 | **Sitenna MCP Connector**（11-12） | 逐字「**AI never has more access than the person using it**」+ 每请求走用户自己账号 + 每个动作记到用户名 + OAuth + 数据留客户 region + SOC 2 + 管理员即时撤销 + **AI 提议工作流、团队 review and approve 后才建** | 第三方商业产品已把「身份传播/最小权限/HITL/审计」写成默认卖点 |
| 8 | **Swift Media 11-13 采购周报** | 「**MCP gateways become a procurement requirement**」：集中工具注册表 / per-agent scopes / 检索内容注入过滤 / 可导出审计日志；模型停用通知期 30–90 天、Agent FinOps 按业务单元计费、保险商问自主动作、联邦身份每 agent 一个 client ID + HR 离职禁用其 agent | 买方语言已把治理写进 RFP 必选项 |
| 9 | **Spring AI Alibaba 生态侧密集发版** | Nacos **3.3.0**：统一 MCP 生命周期（draft/review/publish/online/offline + 可见性 + Java SDK）、AI Agent Registry、**运维可控的 MCP 工具导入出站策略**、默认开启客户端鉴权、把 deprecated Pipeline/MCP import APIs 默认禁用、Spring Boot 4 / Jackson 3；Higress **v2.2.6**（11-10）统一 LLM API + MCP API 管理 | **口径修正**：V1.59「停更风险」判断针对主仓，周边 Nacos/Higress 仍在快速迭代并把注册/导入/权限收进平台层 → 去耦合话术仍成立，但空隙更清晰 |
| 10 | **Amazon Bedrock AgentCore Gateway** 扩展为完整企业 MCP 基础设施层 | 服务端 tool loop、全 MCP 原语、`outputSchema` + `readOnly/destructive` annotations、**按调用者身份的 dynamic listing**、IAM 策略接管权限 | 云厂商继续把「注册表/发现/身份」做成标准层；本框架差异化仍在**模型外确定性执行门 + 私有化合规工件 + 存量 Java 权限复用** |

---

## 2. 12 项决策层验收清单（可直接抄进标书技术规格）

> 每项都给出「控制点 → 仓库真实文件 → 现场验收方式」。**验收方式是可复现的命令或断言，不接受 PPT 口头承诺。**

| # | 验收项 | 控制点 | 仓库真实文件 | 现场验收方式 |
| --- | --- | --- | --- | --- |
| 1 | 授权决策**不读取**模型输出中的自证字段（"已批准"/"用户同意"/"这是只读"） | CP1 | `examples/task-bound-monitor/McpTaskBoundReferenceMonitor.java` `decide()` | 构造 `modelAttestation="HITL-approved"` + `verb=DELETE` → 必须 DENY，审计里出现 `IGNORE` 记录 |
| 2 | 工具注解 `readOnlyHint` / `destructiveHint` **不参与**授权，只参与审计 | CP1 | 同上 + `examples/tool-annotation-gate/` | `readOnlyHint=true` + `verb=DROP` → DENY；合法 `SELECT` 且注解为 false 仍按 grant 放行 |
| 3 | 授权源必须是控制平面签发，模型/客户端通道签发的 grant 一律拒 | CP1 | `TaskGrant.issuedBy` 校验 | `issuedBy="MODEL_CHANNEL"` → DENY@CP1 |
| 4 | 任务绑定：`taskId` 不匹配 / 授权过期 / 跨租户 → 默认拒绝 | CP2 | `decide()` 时间窗 + 租户断言 | 重放 `T-999`、过期、tenant 改 `evil` → 三种全部 DENY |
| 5 | 对象级越权（`account_id=123→124`）被拦，对应 `CVE-2026-109492` IDOR | CP2 | `allowedObjectIds` | 合法 grant 只允许 `acct-123`，传 `acct-124` → DENY |
| 6 | 工具名/动词归一化绕过、堆叠语句、控制字符 → 拒绝 | CP2 | `canon()` + 空白/控制字符校验 | `" Query "`、`"SELECT; DROP TABLE t"`、`"que\try"` → 全部 DENY |
| 7 | 批量导入**跨租户覆盖**一律拒；`endpoint/headers/credentials` 为受保护字段 | CP3 | `screenImport()` | 对位 `CVE-2026-110178` 的 8 个用例（含 `authorization` / `request_headers` / `owner_tenant`）→ 全部 DENY |
| 8 | 内部 ID 不由用户可控名称派生；同名冲突 409 不得被 `skip`/`update` 吞掉 | CP3 | `screenImport()` 冲突分支 | `infraObjectExistsWithSameName=true` + `skip` → DENY；`error` → 放行 |
| 9 | DCR 默认关闭 + issuer 白名单；十进制/八进制/IPv6 site-local/云元数据上游被拒 | CP4 | `screenUpstream()` + `isNumericIpLiteral()` | 11 个上游用例（含 `2852039166`、`0177.0.0.1`、`[fec0::1]`、`169.254.169.254`）→ 按预期判定 |
| 10 | 解析后真实 IP 落在私网 → 拒（DNS rebinding 防护） | CP4 | `screenUpstream(..., resolvedPrivateIp=true)`；叠加 `examples/connect-path-guard/McpResolvedIpGuard.java` | 传入 `resolvedPrivateIp=true` → DENY |
| 11 | 升级后**存量配置全量复验**，指纹漂移即隔离，且幂等 | CP5 | `revalidateStored()` + `fingerprint()`；叠加 `examples/registry-admin-guard/` | 干净/投毒/漂移/畸形四条 → 1 放行 3 隔离；复验两次结果一致 |
| 12 | 审计 append-only + 脱敏；**存在「不得劣于既有基线」的对照组断言** | CP6 | `auditTrail()`、`legacyInPromptGuardAllows()`、`main()` 自检 | 跑 `main()` → `self-test: OK \| 51 断言 \| failures=0`；把 `Bearer SUPERSECRET123` 传进导入请求，审计串中不得出现该明文 |

**现场一句话**：`cd examples/task-bound-monitor && javac -encoding UTF-8 -d /tmp/mcpv172 McpTaskBoundReferenceMonitor.java && java -cp /tmp/mcpv172 McpTaskBoundReferenceMonitor` —— **不需要联网、不需要数据库、不需要 Spring 容器**，51 条断言当场出结果。

### ⭐ 8 个合法对照为什么比 36 个攻击更重要

参数里含 `DROP TABLE`、`ignore previous instructions`、`/* ; DROP TABLE t */` 的合法只读查询**全部放行**。**只看拦截量会选出一个把业务全拦死的方案。** 本框架四期 Gate 全部内置「不得劣于既有基线」断言；11-14 开发期还靠这个断言抓到 `request_headers` 归一化不一致导致保护被绕过的真 bug。

---

## 3. 买方必问 5 问（含不合格 / 合格回答对照）

| # | 问题 | ❌ 不合格回答（常见） | ✅ 合格回答 |
| --- | --- | --- | --- |
| 1 | 「你们怎么保证智能体不会执行任务之外的工具？」 | 「系统提示词里写了不允许」「模型会自己判断」「有审批弹窗」 | 「控制平面按**当前任务**签发 `TaskGrant`，执行门只按 `(taskId, tenant, tool, verb, scope, objectId)` 精确匹配，**默认拒绝**；模型输出、注解、弹窗结果都不参与授权。现场可跑 16 个越权用例」 |
| 2 | 「升级到修复版本之后，之前被植入的配置怎么办？」 | 「升级后就安全了」 | 「**打补丁 ≠ 清干净**。AWS `125926` 官方明确升级不改存量配置、IBM `110178` 官方 Workarounds: None。我们提供 `revalidateStored()` 全量复验 + 指纹漂移隔离 + 幂等，可导出复验报告」 |
| 3 | 「多租户下，A 租户能改到 B 租户的工具配置吗？」 | 「有角色权限控制」 | 「**每个决策都携带 tenant**；导入路径单独校验实体归属，`endpoint/headers/credentials` 是受保护字段不允许导入改写；内部 ID 由平台生成、不由用户名称派生；同名冲突返回 409 且不吞。对位 `CVE-2026-110178` 可现场演示」 |
| 4 | 「你们和云厂商的 MCP 网关（AgentCore / Azure APIM / Higress / Nacos）什么关系？」 | 「我们功能更全」「我们替代它们」 | 「它们把**注册表/发现/身份/路由**做成标准层，这是好事；我们补的是**模型之外、任务绑定、fail-closed 的执行门**与**私有化合规工件**（可导出审计、等保/密评材料、存量 Java 权限复用）。我们是被集成方，不与平台抢控制面」 |
| 5 | 「能不能证明你们的拦截不是靠把正常业务也拦掉换来的？」 | 只给拦截数量截图 | 「跑 `main()`：`36 attack + 8 control + 7 boundary = 51 断言`，其中 8 个合法调用（含参数里带 `DROP`/注入文本）全部放行，且有 `legacyInPromptGuardAllows()` 旧基线做『不得劣于基线』断言。**11 条为旧基线漏掉的增量拦截，0 误杀**」 |

---

## 4. 竞品差异化（诚实版）

| 维度 | 云厂商网关（AgentCore / Azure APIM / 腾讯云 / Higress / Nacos） | 开源非 Java 网关（IBM ContextForge / Bifrost / Obot） | 本框架 |
| --- | --- | --- | --- |
| 注册 / 发现 / 身份 / 路由 | ✅ 标准层，成熟 | ✅ 有 | ⚠️ 不抢，复用上游 |
| **任务绑定确定性执行门** | ⚠️ 以 IAM / scope 为主，决策仍依赖调用链声明 | ❌ 普遍缺失 | ✅ 四个独立 Gate，纯 JDK 可跑断言 |
| **存量配置全量复验 / 指纹漂移** | ⚠️ 官方文档要求客户自己做（AWS 明确「升级不改存量」） | ❌ 少见 | ✅ `revalidateStored()` 幂等 + 可导出报告 |
| 私有化 / 等保 / 密评工件 | ⚠️ 托管服务形态受限 | ✅ 可私有化 | ✅ 全 Java 栈、可离线、可导出审计与 ROPA |
| 存量 Java / Spring 权限复用 | ⚠️ 需改造接入 | ❌ 非 Java 栈 | ✅ 原生 Spring Security / RBAC 对齐 |
| 治理锁在付费墙 | ⚠️ 多数 Enterprise 版 | — | ✅ Apache-2.0 全开源 |

**⚠️ 诚实边界**：本框架**没有**生产级大流量网关的性能验证、**没有**商业合同 / 中标 / 金融案例 / 等保测评；四个 Gate 都在 `examples/` 层、**未接入主模块**（接入工作量评估见 [`gate-integration-map-2026-11-14.md`](gate-integration-map-2026-11-14.md)）。投标时建议作**联合体技术分包**，禁虚构资质与案例。

---

## 5. 三档报价模板（沿用 V1.67 定价锚，全部为公开挂牌价非成交价）

| 档位 | 交付物 | 参考区间 | 加价触发器 |
| --- | --- | --- | --- |
| **A · 评估** | 12 项验收清单逐条差距分析 + 51 断言现场 POC + 一页整改建议 | 公开市场 AI/ML 自由职业 **$120–250/hr**；国内 Java+Spring+AI 岗 20–55k·13~16 薪 | 需出具书面差距报告、需对接等保条款 |
| **B · 落地** | 四个 Gate 接入 `mcp-registry` / `mcp-governance` + 审计导出 + 告警规则 + 回归测试 | 生产级含 audit/SSO/RBAC/versioning 的公开报价 **$80K–$220K**；自建 $15K–$220K + 年运维 10–30% | 多租户、私有化、存量配置复验、与既有 IAM 对接 |
| **C · 合规** | 私有化部署 + 等保/密评材料包 + 可导出审计与 ROPA + 运维交接 | 企业网关公开挂牌 **$250K 起**；受监管行业六位数 | 金融/政务强监管、需联合体资质、需驻场 |

**⚠️ 口径**：以上均为**卖方销售页公开挂牌价 / 公开薪资区间的估算**，**不是本项目成交价，也不是本项目已有收入**。OpenAI×Upwork 11-10 联合研究（11-12 报道）显示 AI 项目固定价预算调整后 **+52.4%**，但挂牌时薪仅 **+1.5%**、前 90 天记录工时 **+40.6%** → **AI 项目是「变大了」不是「变贵了」**：报价应按**交付物 / 集成 / 修订轮次 / 预期工时**拆解，而不是简单抬时薪。

---

## 6. 24 / 48 / 72 小时行动清单

| 时限 | 动作 | 产出 |
| --- | --- | --- |
| 24h | 把本清单 + 51 断言输出 + 四 Gate 接入点图压成 **1 页 PDF**；发 SEO 稿抢「in-prompt 护栏 100% 失守 / IBM ContextForge 双 CVE / 广发研究 MCP 上线」流量 | 报名附件、掘金/CSDN 稿 |
| 48h | 回广发采购平台核实 **13327 报名截止与材料清单**（官方正文确无截止，**不得凭编号推断**）；核 7 项资质自检、确认联合体技术分包路径 | 资质自检表、联合体分工方案 |
| 72h | 按 `gate-integration-map` 分期 ① 把 `McpTaskBoundReferenceMonitor` 与 `McpToolAnnotationGate` 接进 `mcp-registry` 注册期回调；补 `exportPrometheus()` 挂 actuator | 可编译分支 + 接入回归测试 |

---

## 7. 来源

1. arXiv 论文量化对照（MindPattern 11-12 复盘）：https://mindpattern.ai/s/2026-11-12-approval-prompts-and-repo-config-never-kept-anyone-safe-and-three-mcp-patches-prove-i
2. IBM ContextForge SSRF Bulletin（11-13 首发，`CVE-2026-110194`）：https://www.ibm.com/support/pages/node/7296696
3. IBM ContextForge 导入越权 Bulletin（11-13 首发，`CVE-2026-110178`）：https://www.ibm.com/support/pages/node/7296701
4. AWS `CVE-2026-125926` 版本区间与升级边界：https://cybersecurity-see.com/aws-issues-warning-about-mcp-gateway-registry-vulnerability-that-could-enable-attackers-to-access-internal-network-services
5. Permit.io 控制面授权三判据：https://www.authorizon.com/blog/mcp-gateway-security
6. 广发研究 MCP & Skills 上线（11-13）：https://finance.sina.com.cn/stock/estate/integration/2026-11-13/doc-iniyqsna7995492.shtml
7. Schwab 官方发布（11-12）：https://finance.yahoo.com/technology/ai/articles/schwab-announces-platform-enhancements-including-133000925.html
8. Sitenna MCP Connector（11-12）：https://www.einpresswire.com/article/956026427/sitenna-connects-the-world-s-leading-ai-tools-to-infrastructure-project-and-asset-management
9. Swift Media 采购周报（11-13）：https://swiftmedia.ca/blog/ai-headlines-for-business-november-13-2026
10. 广发 13327 官方原文：http://gfjc.gf.com.cn/gyszjgg/13327.jhtml
11. Nacos 3.3.0 特性：https://www.besthub.dev/articles/nacos-3-3-ai-agent-registry-mcp-lifecycle-dns-discovery-spring-boot-4-support-08692e1dda4c
12. Amazon Bedrock AgentCore Gateway：https://theagenttimes.com/agents/article/amazon-bedrock-agentcore-gateway-expands-into-full-enterpris-8e190323
13. OpenAI×Upwork 联合研究（QUASA 11-12）：https://quasa.io/stories/ai-freelance-projects-carry-bigger-budgets-but-barely-higher-hourly-rates
14. Upwork 费率三源复核（11-12）：https://technorious.com/upwork-fees ｜ https://jobgader.com/blog/remote-freelancing-jobs-2026
15. pgEdge `CVE-2026-126197`（NVD 11-12/13 时间戳）：https://threatmonitor.io/cve/CVE-2026-126197

---

_本文档随项目版本持续更新 · V1.72 · 2026-11-14 · 所有外部数字均标注来源与口径，不作为本项目实测指标或商业承诺。_
