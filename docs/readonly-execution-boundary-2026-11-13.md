# 「只读」不是安全边界：执行层强制的 12 项验收清单

> 2026-11-13 · V1.71 · 技术售前核心 · 面向 Agent / MCP 平台采购方与投标应答
> 证据基线：pgEdge Postgres MCP Server 7 CVE（11-10~11-12）+ 官方
> `modelcontextprotocol/servers` 5 GHSA（11-11）+ 嘉信理财 11-12 agentic MCP Server。

---

## 0. 判词

**`read_only` 是声明，不是边界。**

一个工具在注册时写 `readOnlyHint=true`、描述里写「只读查询」、连接串上带
`read_only`，这三样都只是**元数据**。它们告诉调用方「我期望只读」，但**不产生任何
强制**。pgEdge Postgres MCP Server 在 4 天内被连续报出 7 个 CVE，没有一个是靠
「把标签贴得更牢」修好的：全部是在**语句真正执行之前**补上了执行层控制点。

同一周，嘉信理财（$13.33 万亿托管资产）把 MCP Server 从只读摘要推进到可下单，
**但每一笔交易仍强制人工确认**。这两件事指向同一个结论：

> **治理层不是阻碍 agentic 上线的成本，而是让 agentic 敢上线的条件。**
> 而治理层能不能被采购方接受，取决于你能不能**现场证明**它，而不是宣称它。

### 三段式递进（本项目版本脉络）

| 版本 | 判词 | 被证伪的「信任」 | 交付 |
| --- | --- | --- | --- |
| V1.69 | 注解不是安全边界 | 工具元数据 / hint | `examples/tool-annotation-gate/` 14 项注册期验收 |
| V1.70 | 注册中心自己也不是安全边界 | 治理层自己的管理面 | `examples/registry-admin-guard/` 12 项管理面验收 + 存量复验 |
| **V1.71** | **只读声明也不是安全边界** | **连接标签 / read_only / 首关键字分类** | 🆕 `examples/read-only-enforcement/` **12 项执行层验收** |

---

## 1. 12 项执行层验收清单（逐条 → 控制点 → 真实文件 → 现场验收方式）

| # | 验收项 | 控制点 | 本方案真实文件 | 现场验收方式 | 对位实证 |
| --- | --- | --- | --- | --- | --- |
| 1 | **声明层不得参与授权**：`readOnlyHint` / 工具描述 / 连接标签只是展示字段 | 声明/执行解耦 | [`examples/read-only-enforcement/McpReadOnlyEnforcementGate.java`](../examples/read-only-enforcement/McpReadOnlyEnforcementGate.java) 的 `ReadOnlyClaim` 与 `enforce()` | 把 `readOnlyAnnotation=true`、`connectionLabel="read_only"` 配到一条 `SELECT ... INTO` 上 → 必须 **DENY**；再翻转所有声明字段，合法只读查询结果**不变** | `CVE-2026-126196` |
| 2 | **词法归一化后再分类**：先剥离行/块注释、单引号字符串、双引号标识符、dollar-quote 块，再对**全文**扫描动词 | CP1 | 同上 `classify()` | 提交 `/*x*/ VACUUM INTO ...`、`-- c\nDELETE ...`、`SELECT 'DROP TABLE t; DELETE FROM t'` → 前两条拒绝、第三条**必须放行**（不得误杀字符串常量） | `126199` / `126198` / V1.67 `41901` |
| 3 | **禁止多语句 / 简单查询协议**：分号堆叠、事务与会话状态改写一律拒 | CP1 | 同上 `statementCount > 1` | `SELECT 1; UPDATE accounts SET balance=0`、`SELECT 1; COMMIT` → DENY；`select * from orders limit 10;`（尾分号）→ ALLOW | `126196` |
| 4 | **dollar-quote 标签逃逸检测**：保留模板标签不得出现在客户端语句中 | CP1 | 同上 `RESERVED_TEMPLATE_TAGS` | `SELECT $mcp_custom_tool$; DROP TABLE t; -- $mcp_custom_tool$` → DENY；合法的 `$$a; b$$` 字符串 → ALLOW | `126197` |
| 5 | **修复完整性复验**：不能只验「本轮补丁」，要对历史绕过形态做回归 | CP1 | 同上 8 条攻击载荷 + [`examples/registry-admin-guard/`](../examples/registry-admin-guard/README.md) 存量复验 | 跑主自检输出必须含 `22 attack ... 20 incremental blocks over legacy baseline` | `GHSA-q74p`（对 `w766` 的修复不完整） |
| 6 | **强制参数绑定 + 服务端只读事务**：`SET TRANSACTION READ ONLY` 由服务端下发，不信任客户端声明 | CP2 | 同上 CP2；加固配置 [`examples/governance-hardening/application-hardening.yml`](../examples/governance-hardening/application-hardening.yml) | 关闭 `paramsBound` 或 `readOnlyTransaction` → DENY；数据库侧同时用**最小权限只读角色**（双闸） | `126196` |
| 7 | **`where` 参数作用域**：不得引用声明表之外的表 / 子查询 | CP2 | 同上 `WHERE_SUBQUERY_OUT_OF_DECLARED_SCOPE` | 构造 `count_rows(where="id IN (SELECT id FROM other_table)")` → DENY | `125322` |
| 8 | **池化会话状态隔离**：连接归还前 RESET / DISCARD ALL，禁止残留事务状态让后续调用变可写 | CP3 | 同上 `POOLED_SESSION_STATE_NOT_RESET` | 制造「前一条改写 `session_replication_role`、后一条复用同一连接」的序列 → 第二条必须被拦或状态已复位 | `126196` 后半 |
| 9 | **调用方身份与 Origin**：身份不得取 XFF 最左项；Origin 白名单 + Host/Origin 一致性 | CP4 | 同上 CP4；[`examples/local-listener-guard/`](../examples/local-listener-guard/README.md) | 伪造 `X-Forwarded-For: 127.0.0.1, attacker`、缺失/跨域 Origin、Host 与 Origin 不一致 → 三种全部 DENY | `125321` / `125320` |
| 10 | **只读工具执行环境净化**：禁止加载仓库 / 工作区自带可执行配置（`core.fsmonitor`、hooks） | CP5 | 同上 `READONLY_TOOL_LOADS_UNTRUSTED_REPO_CONFIG`；[`examples/stdio-command-allowlist/`](../examples/stdio-command-allowlist/README.md) | 在只读工具处理的仓库里放恶意 `.git/config` `core.fsmonitor` → 必须既不执行也不加载 | `GHSA-6hqx-gqrx-c5r2` (7.8) |
| 11 | **校验路径与实际执行路径同源 + 解析后 IP 分类完整** | CP6 | 同上 CP6；[`examples/connect-path-guard/McpResolvedIpGuard.java`](../examples/connect-path-guard/McpResolvedIpGuard.java) | `Disallow: /private` 下抓 `//private` → DENY；`fec0::/10`、`64:ff9b::/96`、十进制/八进制 IP 一律不得判为 public | `GHSA-f9hh` / `3rxj` / `66qx` / `v38p` |
| 12 | **fail-closed + 可举证**：任一控制点失效不全局放行，拒绝原因码可导出、可审计 | 全局 | 同上 `Verdict.reasonCode`；[`examples/audit-ropa-export/`](../examples/audit-ropa-export/README.md)、[`examples/monitor-alert-rules/`](../examples/monitor-alert-rules/README.md) | 逐一关闭 6 个 CP，每次都必须 DENY 且 `reasonCode` 精确对应；导出审计含工具名、原因码、调用方、时间，不含明文凭据 | 买方 RFP「可导出的审计日志」(Swift Media 11-13) |

### 现场演示脚本（一条命令）

```bash
cd <repo>
javac -encoding UTF-8 -d out examples/read-only-enforcement/McpReadOnlyEnforcementGate.java
java -Dfile.encoding=UTF-8 -cp out com.example.mcp.readonly.McpReadOnlyEnforcementGate
# 预期：[McpReadOnlyEnforcementGate] self-test OK
#       (22 attack + 9 control + 8 boundary = 79 assertions; 20 incremental blocks over legacy baseline)
```

> **演示要点**：一定要同时展示那 **9 条合法只读对照**。只演示「拦住了多少攻击」的
> 供应商不可信——能拦攻击很容易（全拒绝即可），**不误杀正常业务**才是工程能力。

---

## 2. 买方必问 5 问（把清单变成采购对话）

| # | 买方应该问 | 不合格回答 ❌ | 合格回答 ✅ |
| --- | --- | --- | --- |
| 1 | 「你说这个工具只读，是**谁**保证的？工具作者、模型、还是数据库？」 | 「工具描述里写了 readOnlyHint」 | 「**数据库最小权限只读角色**为主闸 + **服务端下发 `SET TRANSACTION READ ONLY`** 为副闸 + 网关执行层文法为第三闸；工具声明只是展示字段，不参与授权」 |
| 2 | 「模型能不能通过 `SELECT 1; UPDATE ...` 或 CTE 里的写操作绕过？」 | 「我们做了 SELECT 前缀检查」 | 「先做**词法归一化**（剥离注释/字符串/dollar-quote），再对**全文**扫描动词 + 堆叠语句计数；CTE 内 `UPDATE` 同样命中；附 8 条真实载荷的回归输出」 |
| 3 | 「连接池复用会不会把上一次的事务状态带给下一次调用？」 | 「我们每次新建连接」 | 「归还前 `RESET / DISCARD ALL` 并对会话状态做指纹复验；给出『前一条改 session、后一条复用』的拦截面演示」 |
| 4 | 「补丁修完了，**之前已经写进去的配置 / 已经泄露的凭据**怎么办？」 | 「升级到最新版即可」 | 「升级不清理存量（AWS 双 CVE 官方明确）→ 提供**存量全量复验 + 指纹漂移检测 + 凭据轮换清单**；现场可跑 [`registry-admin-guard`](../examples/registry-admin-guard/README.md) 45 断言」 |
| 5 | 「能不能给我**可导出的审计日志**和逐条验收方式，而不是一份 PPT？」 | 「我们有 dashboard」 | 「28 类攻击面逐条对应控制点 + 真实文件路径 + 现场验收命令；审计含原因码/调用方/时间且脱敏；提供纯 JDK 可编译示例自行复核」 |

---

## 3. 竞品差异化（2026-11 口径）

| 层 | 别人在做 | 空白 / 本方案位置 |
| --- | --- | --- |
| 协议层 | Linux 基金会 AAIF 无状态规范（2026-07-28）、OAuth 2.0/OIDC + RFC 9207 | **不做**。标准已冻结，无增量故事 |
| 传输 / 网关 | Azure APIM AI Gateway、AWS AgentCore Gateway、腾讯云 MCP 网关、Google Agent Registry、Cloudflare/Akamai 身份治理 | **不硬碰**。三大云已把治理做成标准产品层（V1.66） |
| SaaS 接入 | Salesforce substrate / SAP broker / OnePlan「respects existing permissions」/ Stripe 收紧密钥 | 复用其权限模型，**不重造** |
| **执行层可验证性** | 各家给架构图与合规声明；**公开 ROI 数字几乎全线缺位**（CSDN 11-12） | ⭐ **本方案位置**：把「只读 / 审批 / 审计」变成**可现场编译运行、带攻击与对照断言**的工件。21 模块 + 28 类攻击面 + 20 组纯 JDK 示例，采购方可自行复核 |
| **存量 Java 资产** | 云网关多为 Go/Python/Node，治理锁在 Enterprise 付费墙（V1.63 横评） | ⭐ **Java + Spring + Spring AI Alibaba 原生**，存量权限/审计直接复用，私有化 + 等保工件 |

**一句话差异化**：
> 三大云卖的是「网关」这一层，我们卖的是**「你能不能当场证明这一层真的生效」**。

---

## 4. 三档报价模板（沿用 V1.67 报价锚，按本期验收项重算）

| 档 | 范围 | 报价区间 | 交付物 | 加价触发器 |
| --- | --- | --- | --- | --- |
| **A · 评估** | 12 项执行层验收清单对照 + 现有 MCP 部署只读面审计 + 一次现场演示 | **$8K–$15K** / ¥6–11 万 | 差距报告（逐条通过/不通过）+ 79 断言演示 + 整改优先级 | 需出正式盖章报告、需渗透测试授权 |
| **B · 落地** | 6 个执行层控制点接入 + 审批门 + 脱敏审计导出 + 28 类攻击面验收 | **$80K–$220K** / ¥58–158 万 | 可运行组件 + 验收脚本 + 运维手册 + 2 轮回归 | 私有化/信创/等保三级、多租户隔离、遗留 Java 权限映射 |
| **C · 平台** | 跨运行时统一治理平面 + 存量全量复验 + 持续合规工件 | **$250K 起** / ¥180 万起 | 控制平面 + 季度复验服务 + 买方 RFP 应答包 | 金融监管现场检查、跨云/跨集群、SLA 99.99% |

> ⚠️ 全部为**卖方销售页公开价 / 估算区间，非成交价**（V1.67 报价锚：
> Dawloom 7 档 $3,700–$72,000 达 19 倍价差，生产级含 audit/SSO/RBAC/versioning
> $80K–$220K，企业网关 $250K 起）。本项目**无商业合同 / 无中标 / 无金融案例**，
> 报价仅作联合体技术分包时的成本参照。

---

## 5. 24 / 48 / 72 小时行动清单

| 窗口 | 动作 | 产出 |
| --- | --- | --- |
| **24h** | ① 回广发采购平台**核实 13327 报名截止与材料清单**（官方正文未呈现）；② 把本清单 + 79 断言输出 + 云网关对照压成 **1 页 PDF**，交潜在广州本地联合体主体；③ 发 SEO 稿 | 核实记录、1 页 PDF、掘金/CSDN 稿 |
| **48h** | ④ 13322（11-15 17:30）资质自检：原厂授权 / 2 个金融案例 / 完税 / 广州本地团队——**不足则明确只做技术分包**；⑤ 把 12 项验收列并入 `enterprise-rfp-checklist.md` 的应答矩阵 | 资质自检表、RFP v1.71 |
| **72h** | ⑥ 把 `McpReadOnlyEnforcementGate` 与 `McpToolAnnotationGate`、`McpRegistryAdminGuard` 三者的**接入点**画成一张图并评估接入 `mcp-registry` 的工作量；⑦ 军队 190 万 11-16 / 中山 399 万 11-17 / 山东重工 11-20 备标 | 接入方案、三标素材 |

---

## 6. 诚实边界

- 本框架**不受** pgEdge 7 CVE / 官方 5 GHSA 直接影响（不内置这些实现）；引用为行业级失效模式证据。
- 12 项验收清单是**参考实现的验收口径**，其中 CP2 where 作用域 / CP3 会话状态 / CP4 身份来源 /
  CP5 环境净化 / CP6 路径 IP 在示例中以**执行上下文标志**表达，用于演示 fail-closed；
  真实落地需接 JDBC/`pgx` 元数据、连接池 hook、可信代理头、DNS 解析后 IP 分类器。
  **不得把示例当生产组件交付或宣称已内置主模块。**
- 参考实现未接入 `mcp-core` / `mcp-registry`（跨期待办）；`mcp-monitor` `exportPrometheus()`
  挂 actuator、dataClassification 差异化审批引擎、Spring AI 2.0 升级均**未完成**。
- 广发四份为**预征集 ≠ 采购邀请**；本项目 7 项资质门槛全不满足，只能联合体技术分包，
  禁虚构企业主体 / 合同 / 案例 / 等保证书。
- 嘉信理财 MCP 当前仅接 Claude、仅零售客户；「打破独占」为分析师判断非既成事实。
- 全部报价为公开挂牌 / 销售页价，非成交价。

---

_配套：全量情报 [`earnings-report-2026-11-13.md`](earnings-report-2026-11-13.md) ·
SEO 稿 [`blog-java-mcp-readonly-boundary-schwab-2026-11-13.md`](blog-java-mcp-readonly-boundary-schwab-2026-11-13.md) ·
攻击面清单 [`enterprise-rfp-checklist.md`](enterprise-rfp-checklist.md) ·
发布说明 [`V1.71-release-notes.md`](V1.71-release-notes.md)_
