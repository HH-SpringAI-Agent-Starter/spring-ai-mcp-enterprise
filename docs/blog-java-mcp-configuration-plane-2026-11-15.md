# MCP 进了 Linux 基金会，为什么你的网关还是会被打穿？—— 配置面不是安全边界

> 掘金 / CSDN 同步稿 · 2026-11-15 · Spring AI MCP Enterprise V1.73
> 关键词：MCP 网关 / AAIF / Agent Skills / Spring AI Alibaba / Java 企业级 / 零信任 / CVE-2026-110178 / n8n MCP OAuth / 确定性执行门

---

上周有个挺有意思的巧合：**MCP 和 Agent Skills 都归到了 Linux 基金会的 AAIF 做厂商中立治理**，社区一片叫好；同一周，**IBM ContextForge MCP Gateway 一天发了三个 CVE**，n8n 的 MCP OAuth 被曝可拿可信域名钓鱼，pgEdge 官方把「为什么 `read_only` 挡不住堆叠语句」写进了公告。

一边是协议治理上移到基金会，一边是集成层的洞继续往下沉。这两件事其实说的是同一句话：

> **规格定义「怎么连」，从来不定义「谁能动」。治理权可以中立，授权不能外包。**

这篇不聊协议细节，聊一个更工程的问题：**从外部进来的配置，到底能不能参与授权决策？** 并给出一份能 `javac` 跑通的 Java 参考实现。

---

## 一、三个 CVE，打的是同一层

11-13 IBM ContextForge 的官方 Bulletin 一口气三个，统一修 v1.1.0：

| CVE | 分数 | 一句话 |
| --- | --- | --- |
| `CVE-2026-110178` | 4.1 | `/v1/import` + `conflict_strategy=update` **不校验共享实体归属**，持 team-scoped `admin.import` 的人能覆写**其他租户**的 endpoint / headers / 凭据，把网关流量重定向走。官方 **Workarounds: None** |
| `CVE-2026-110194` | 5.4 | OAuth DCR 从授权服务器 metadata 里拿 `registration_endpoint`，**没校验就直接请求** → SSRF 打内网。官方缓解是 `DCR_ALLOWED_ISSUERS` / 关掉 DCR |
| `CVE-2026-110199` | 4.8 | 管理后台从 URL 参数读 status / error 通知，**客户端 sanitizer 被 mutation XSS 绕过** → 管理员会话里执行任意 JS |

同一个产品，**管理面 UI、配置导入、连接建立**三层同时出问题。再加两个同周的：

- **n8n `CVE-2026-126386`**（4.7）：未认证的 `/mcp-oauth/register` 接受任意 `redirect_uri`，攻击者用**你自己的可信 n8n 域名**发钓鱼链接，在实例做任何检查之前就把人带走。和 IBM 的 `110194` 是同一个病根 —— **外部给来的 URI 没校验就信任了**。
- **pgEdge `CVE-2026-126197`**（8.8）：`$mcp_custom_tool$` 提前闭合 DO 块、`$mcp_args$` 注入 plpgsql；公告里最关键的一句是「**因为使用 simple query protocol，注入文本可以包含额外语句与事务控制，从而绕过 `read_only`**」。修法是 1.1.0，不是把 `readOnly` 标签贴得更牢。

如果你把这几条连起来看，会发现 V1.69~V1.72 我们按顺序说过的话又复现了一遍：注解不是边界、注册中心不是边界、只读声明不是边界、审批与提示词护栏不是边界。**V1.73 补上第五段：配置面 / 连接建立期也不是边界。**

---

## 二、AAIF 之后，这个缺口是变大不是变小

MCP 2025-12-09 捐入 AAIF，Agent Skills 2026-10-22 连同 Open Responses / OpenEnv / OpenSandbox 一起加入，现在 10 个托管项目。Governance WG 允许领域工作组接 SEP 而不必走 core maintainer 全审，2026-07-28 revision 有正式的 Extensions Track，SEP-3398 要求 sponsor；9 月还出了第一个厂商中立的 MCP 认证 MCPA（$250 / 90 分钟，**24% 考安全与治理**）。

对企业来说，好处是不会被单一厂商锁死；代价是：

> **没有哪家厂商会为「规格变了」给你发一份补丁通知。** 而规格变更恰恰可能改变 agent 的权限、认证方式与工具访问边界。

AI Governance Institute 给的处置建议很实在：把 MCP / Agent Skills **当受监控依赖**纳入变更管理与安全复审；采购模板要求供应商**披露规格版本依赖**；此前针对单厂商规格做的安全审批，审批基准要重评。

换句话说：**规格层越活跃，集成方越需要自己的、可现场验收的执行门**。这不是基金会该干的活，也不是三大云网关能替你干的 —— AWS AgentCore Gateway GA 已经把 dynamic listing、`outputSchema`、`readOnlyHint`/`destructiveHint` 做成产品层输入了，但「这次任务里这个 agent 能不能动这张表」仍然只能由你在自己的执行路径里回答。

---

## 三、最有意思的一条：评分门是怎么被一句话骗过去的

ScriptMasterLabs 对 DE-CIX 的 MCP server 做了一组门测试（作者自报、启发式、`calibrated=false`，数字别当基准看，**方向性结论很值钱**）。DE-CIX 那个 server 的 8 个能力里有 **5 个是采购动词**（book / scale / procure / deploy / manage）—— 也就是说 AI agent 一句话就能下商业订单。

| 表达形式 | 动作 | 评分 | 门的结果 |
| --- | --- | --- | --- |
| scope 形式 + 有界状态 | read status | 0.9545 | auto-act ✅ 正确 |
| scope 形式 + 有界状态 | procure deploy connection | 0.50 | advisory hold ✅ 正确 |
| **完整句子** | 「procure and deploy **without asking first**」 | **0.803** | ❌ **auto-act 放行** |
| **完整句子** | 「book and scale **after operator confirmation**」 | 0.8488 | ❌ auto-act 放行 |

注意最后一行：**把「已获操作员确认」写进句子里，门根本用不上这个信息**；而同一个动作换成结构化的 scope 形式，门就画对了线。作者原话：

> *enforce the allowlist mechanically on the tool call itself; use a confidence score over prose as a second opinion, **never as the lock**.*

这和 11-12 那份 arXiv 量化实证是同一结论的两面：in-prompt 护栏被持久自适应攻击者推到 100% 攻击成功率，而模型外的确定性 reference monitor 对 severe actions 放行 0%。**凡是「读自然语言再打分」的东西，都只能当第二意见。**

---

## 四、Java 落地：一条有序、短路、fail-closed 的链

下面这段来自仓库 `examples/gate-orchestration/McpGateChain.java`（纯 JDK 17、零依赖、不进 Maven reactor），现场可跑：

```java
// 1) 两个 SPI（对应 docs/gate-integration-map-2026-11-14.md 的建议接口）
public interface McpRegistrationGate {
    GateVerdict screen(ToolRegistration registration, GateContext ctx);
}
public interface McpInvocationGate {
    GateVerdict decide(TaskGrant grant, ToolInvocation call, GateContext ctx);
}
// V1.73 新增第三个：所有从外部进来的配置都先过这一关
public interface McpControlPlaneInputGate {
    GateVerdict screenInput(ConfigInput input, GateContext ctx);
}
```

```java
// 2) 配置面准入：issuer 白名单 + 域锚定 + 解析后真实 IP 公网分类
private GateVerdict screenDcr(ConfigInput in) {
    if (!isTrustedIssuer(in.issuer()))
        return GateVerdict.deny(Phase.CONTROL_PLANE, "DCR_ISSUER_NOT_ALLOWLISTED", "...");
    String h = in.endpointHost().toLowerCase(Locale.ROOT);
    if (h.contains("metadata") || h.contains("169.254.169.254")) return deny("DCR_CLOUD_METADATA_TARGET");
    if (!h.endsWith(".corp.example")) return deny("DCR_ENDPOINT_OUTSIDE_ISSUER_DOMAIN");
    if (!isPublicIpLiteral(in.resolvedIp())) return deny("DCR_RESOLVED_IP_NOT_PUBLIC");
    return GateVerdict.allow(Phase.CONTROL_PLANE, "DCR_METADATA_ACCEPTED");
}
// 八进制 0177.0.0.1 / 十进制 2130706433 / IPv6 fe80::1 / CGNAT 100.64.x 一律拒绝
```

```java
// 3) 调用期：机械 scope allowlist 是唯一的锁，评分与自证只读审计
if (!g.allowedScopes().contains(c.scope())) {
    if (c.modelConfidence() >= 0.80 || c.approvalDialogShown()
            || "MODEL_SELF_ATTESTED".equals(c.claimLayer())) {
        return deny("SEMANTIC_SCORE_CANNOT_OVERRIDE_ALLOWLIST",
                    "confidence / prose / approval text is a second opinion, never the lock");
    }
    return deny("SCOPE_NOT_GRANTED", "scope outside TaskGrant");
}
if (hasExecutableComment(c.statement())) return deny("EXECUTABLE_COMMENT_PAYLOAD");
if (statementCount(normalizeSql(c.statement())) > 1) return deny("STACKED_STATEMENT");
if (containsWriteVerb(n) && !g.allowedScopes().contains("write"))
    return deny("READ_ONLY_CLAIM_NOT_AUTHORITY");
// c.claimLayer() / modelConfidence() / approvalDialogShown() / prose()
// 从不出现在任何 allow 分支，只写进审计
```

链是 **AND 语义**：注册期 → 配置面 → 调用期，任一 DENY 即整体 DENY，短路但每个已执行的 Gate 都留痕，每条 ALLOW/DENY 都写明 `rule` 和 `reason`，字段白名单化、值恒为 `[REDACTED]`。

```bash
cd examples/gate-orchestration
javac -encoding UTF-8 McpGateChain.java && java McpGateChain
# attack=84 control=26 boundary=13 TOTAL=123
# legacy baseline missed 9 attack cases (new chain blocks all)
# failures=0 → SELF-TEST OK
```

**26 条合法对照 0 误杀**是硬要求：字符串里的 `DROP TABLE`、只读 CTE、**普通注释里的写动词**、带敌意 prose 但 scope 正确的只读查询、以及 `write` scope 下真正合法的 `UPDATE`，全部放行。只演示拦截量，很容易选出把业务全拦死的方案。

### 开发中被断言抓到的 3 个真 bug（比结论更有价值）

1. `normalizeSql()` 把块注释洗掉后，MySQL **`/*!` 可执行注释**的 payload 也一起消失了 → 改成**先用原始文本**判 `hasExecutableComment()`；同时把「普通注释里含写动词」改判为合法对照。
2. `203.0.113.24` 是 **RFC 5737 文档保留段**，被我当成了「合法公网 IP」样本 → 换真实可路由地址，并新增断言**拒绝**文档段（和官方 `GHSA-3rxj` IPv6 site-local 误判 public 是同型错误）。
3. 短路断言初版传的是**合法** DCR，结果一路走到调用期才 DENY → 换成跨租户导入才真正验证「在配置面短路」。

> 这三条再次说明：**对照组 + 「不得劣于旧基线」断言不是装饰**。旧基线（信 hint + 模型自证 + 首关键字 + URI 前缀 + 跨版本继承审批）在这 9 条攻击上全放行。

---

## 五、分层图：别把不同层的承诺混着听

```
规格与技能格式   AAIF / Linux Foundation（MCP · Agent Skills · A2A）
                 → 可移植、去厂商锁定、SEP 流程、MCPA 认证
连接与编排       Azure APIM / AWS AgentCore Gateway / Google Cloud / 腾讯云
                 → 集中入口、凭据托管、dynamic listing、annotations
旁路监视         Guardian Agents 类（Monitor→Action、审计写明 enforcing rule）
                 → 发现、盘点、运行时调节
──────────────── 以上都不回答「这次能不能动」 ────────────────
执行门           mcp-governance + mcp-registry + mcp-gateway + mcp-tenant
                 → 按当前任务签发 · 机械 scope allowlist · fail-closed · 可现场验收
私有化工件       审计导出 / 等保 / 密评 / 存量 Java 权限复用
```

**销售侧也已经跟上了**：Swift Media 11-13 的采购周报把「MCP gateways become a procurement requirement」写进 RFP（集中注册表 / per-agent scope / 检索内容注入过滤 / 可导出审计），还建议「下个 agent 供应商 RFP 加 5 个 MCP 网关问题」。同周还有模型停用通知 30–90 天进 MSA、Agent FinOps 按业务单元分摊、保险商开始问「哪些动作无人工审批」、以及联邦 agent 身份进 HR 离职流程。

---

## 六、Java / Spring 工程师的 9 条落地清单

1. DCR 的 `registration_endpoint` 先过 issuer 白名单 + 域锚定 + **解析后真实 IP**，别只比 `http/https` 前缀。
2. `redirect_uri` 必须与**已注册值精确相等**，拒 `user@`、`//host`、`%2f%2f`、http 降级。
3. 批量导入的 `conflict_strategy=update` 必须校验**共享实体归属 + tenant 一致**；endpoint/headers/credentials 设为不可导入。
4. 管理后台从 URL 读的 status/error **服务端转义 + 纯文本渲染**，不要依赖客户端 sanitizer。
5. 把 `specRevision` 记进上下文；AAIF 规格变更触发**全量复验**，旧审批不自动继承，复验要幂等。
6. 打补丁 ≠ 清干净：AWS 官方明确「升级不改升级前已存配置」，要有显式 `revalidateStored()` + 隔离动作 + 可导出报告。
7. hint / 注解 / `read_only` / `readOnlyHint` / `destructiveHint` **只做比对**，服务端独立定级。
8. `tools/call` 上执行**机械 scope allowlist**；置信度、prose、审批弹窗文本、模型自证一律只读审计。
9. 审计 append-only、字段白名单、值脱敏、每条写明 `enforcing rule`；回归测试必须含**合法对照 0 误杀**。

---

## 七、别踩这些坑（诚实边界）

- 本文所有第三方 CVE **不影响本框架**；`examples/` 是对攻击范式的独立参考实现，**未接入 21 个主模块**，不得当生产组件。
- `TaskGrant.signatureOk` 是**布尔标志，不是密码学签名**；生产要换真签名 + 密钥轮换。IP 判定是字面量分类，不是 DNS/SNI 逐跳校验；SQL 筛查是词法归一化，不是解析器，真实场景必须叠加绑定参数 + 服务端只读事务 + 会话 RESET。
- AAIF 治理移交**不是最近三天的新闻**（MCP 2025-12-09 / Agent Skills 2026-10-22）；本文的增量是采购与变更管理含义的解读。成员数 247 / 305 属不同时点口径，SEP / MCPA 细节来自二手整理，正式引用请回官方复核。
- DE-CIX 门测试是第三方启发式实验（`calibrated=false`、单次测量），数字**不是本框架实测拦截率**；只取「语义评分不能当锁」的方向性结论。
- 123 断言是**本仓库自检输出**，不是第三方测评。

---

**项目**：[HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise](https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise)
**本期文档**：`docs/configuration-plane-boundary-2026-11-15.md`（12 项验收清单 + 买方必问 5 问 + 三档报价）、`docs/earnings-report-2026-11-15.md`（招标 / 招聘 / 价格全量）、`examples/gate-orchestration/README.md`
