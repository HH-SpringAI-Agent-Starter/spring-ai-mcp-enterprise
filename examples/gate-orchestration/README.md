# 四 Gate 装配链 · 可运行参考实现（V1.73）

> **兑现 `docs/gate-integration-map-2026-11-14.md` 分期①**：把 V1.69~V1.72 四个 Gate 的判定契约装配成一条**有序、短路、fail-closed、全量留痕**的链，并补齐 V1.73 新增的**配置面准入**关卡。
> 纯 JDK 17、零依赖、**不进 Maven reactor**（对 21 模块 CI 零风险）。

---

## 1. 一句话判词

> **治理权可以中立，授权不能外包。**
> 注解、注册中心元数据、只读声明、审批弹窗与提示词护栏、以及**从外部进来的配置**（授权服务器 metadata / redirect_uri / 批量导入 / 管理后台 URL 参数 / 规格版本），**一律不参与授权决策**。
> 唯一可验收的边界是：**按当前任务签发、在工具调用本身机械执行、fail-closed 的确定性执行门**。

这是五段式收口的第五段：V1.69 注解 → V1.70 注册中心 → V1.71 只读声明 → V1.72 决策层 → **V1.73 配置面**。

---

## 2. 链结构

```
ToolRegistration ──▶ [A] AnnotationIntegrityGate ──┐
ConfigInput      ──▶ [B] ControlPlaneInputGuard  ──┼──▶ Chain.evaluate() ──▶ ALLOW / DENY
TaskGrant + Call ──▶ [C] TaskBoundInvocationGate ──┘        │
                                                             └──▶ append-only audit (rule + reason + redacted fields)
```

| 阶段 | SPI | 对应既有 Gate / 文档 | 关键控制点 |
| --- | --- | --- | --- |
| **A 注册期** | `McpRegistrationGate` | `examples/tool-annotation-gate/`、`examples/registry-admin-guard/` | 双 hint 必填、默认禁用、指纹漂移复验、**服务端独立定级**（声明层不参与授权）、谎报须重新声明 |
| **B 配置面** 🆕 | `McpControlPlaneInputGate` | 本文 V1.73 新增 | DCR metadata 准入、redirect_uri 精确匹配、跨租户导入归属、管理后台 mXSS、**规格版本变更不继承旧审批** |
| **C 调用期** | `McpInvocationGate` | `examples/read-only-enforcement/`、`examples/task-bound-monitor/`、`examples/connect-path-guard/` | CONTROL_PLANE 签发 + 任务/租户绑定 + **机械 scope allowlist** + 堆叠语句/写动词 + 评分与自证只读审计 |

**设计约束（沿用 `gate-integration-map` 第 3 节 6 条）**：
1. `GateContext` 携带 `tenant / taskId / grantIssuedBy`，V1.73 增加 `specRevision`；
2. 声明层字段**不进入** `GateVerdict` 的允许分支；
3. 任一 DENY 即整体 DENY（AND 不是 OR），短路但每个已执行 Gate 都留痕；
4. 必须保留对照组回归；
5. 指纹漂移 / 存量复验是幂等读操作 + 显式隔离动作；
6. 审计字段名白名单化、值脱敏、每条决策写明 `rule`。

---

## 3. 控制点 → 真实披露映射

| 控制点 | 对应 2026-11-13/14 披露 | 自检用例 |
| --- | --- | --- |
| B1 `DCR_ISSUER_NOT_ALLOWLISTED` / `DCR_RESOLVED_IP_NOT_PUBLIC` | **IBM ContextForge `CVE-2026-110194`**（5.4，OAuth DCR 从 metadata 拿 registration endpoint 未校验即请求 → SSRF；官方缓解 `DCR_ALLOWED_ISSUERS` / `DCR_ENABLED=false`） | B01–B15（含八进制 IP、十进制 IP、IPv6 site-local、云元数据、CGNAT） |
| B2 `REDIRECT_URI_NOT_EXACT_MATCH` / `REDIRECT_URI_EXTERNAL_HOST` | **n8n `CVE-2026-126386`**（4.7，未认证 `/mcp-oauth/register` 接受任意 `redirect_uri` → 可信域钓鱼） | B16–B23 |
| B3 `IMPORT_CROSS_TENANT_OWNERSHIP` / `IMPORT_PROTECTED_FIELD_OVERWRITE` | **IBM ContextForge `CVE-2026-110178`**（4.1，`/v1/import` + `conflict_strategy=update` 覆写他租户 endpoint/headers/凭据，**官方 Workarounds: None**） | B24–B27 |
| B4 `ADMIN_STATUS_ACTIVE_MARKUP` | **IBM ContextForge `CVE-2026-110199`**（4.8，管理后台 URL 参数经客户端 sanitizer 的 mutation XSS → 管理员会话任意 JS） | B28.0–B28.10、B29、B30 |
| B5 `SPEC_REVISION_CHANGED_REVALIDATE_ALL` | **MCP / Agent Skills 归入 Linux Foundation · AAIF 厂商中立治理**：规格变更不再有单厂商补丁通知，须作为受监控依赖进变更管理 | B31、B32 |
| C `SEMANTIC_SCORE_CANNOT_OVERRIDE_ALLOWLIST` | **DE-CIX MCP server 门测试（ScriptMasterLabs 10-19 实测）**：完整句子「procure and deploy without asking first」拿到 **0.803 auto-act** 放行，「operator confirmation」也只到 0.8488；**scope 形式**下同一动作为 0.50 advisory hold → 原文结论「enforce the allowlist mechanically on the tool call itself; use a confidence score over prose as a second opinion, **never as the lock**」 | D05–D08 |
| C `READ_ONLY_CLAIM_NOT_AUTHORITY` / `STACKED_STATEMENT` | **pgEdge `CVE-2026-126197`**（8.8，`$mcp_custom_tool$` 提前闭合 DO 块；**simple query protocol 使注入可含额外语句与事务控制，绕过 `read_only`**） | D09–D13 |
| A 服务端独立定级 | **Amazon Bedrock AgentCore Gateway** 把 `outputSchema` / `readOnlyHint` / `destructiveHint` 做成产品层输入 → 注解越标准化，越不能当授权依据 | A05–A11 |

---

## 4. 运行

```bash
cd examples/gate-orchestration
javac -encoding UTF-8 McpGateChain.java
java McpGateChain
```

**V1.73 实测输出**（Windows / JDK 17.0.8）：

```
attack=84 control=26 boundary=13 TOTAL=123
legacy baseline missed 9 attack cases (new chain blocks all)
failures=0
SELF-TEST OK (pure JDK, zero dependency, all groups green)
```

- **84 条攻击**全部 DENY；**26 条合法对照**全部 ALLOW（**0 误杀**）；**13 条边界**。
- 合法对照刻意包含：字符串里的 `DROP TABLE`、只读 CTE、内联注释里的分号与写动词、带敌意 prose 但 scope 正确的合法只读、以及 `write` scope 下真正合法的 `UPDATE`。
- **「不得劣于旧基线」断言**：旧基线（信 hint + 模型自证 + 首关键字 + URI 前缀 + 跨版本继承审批）漏掉 9 条攻击，新链全部拦下；若任一合法对照被误杀，该组断言会直接失败。

### 开发中被断言抓到并修正的 3 个真 bug

1. **`/*!` 可执行注释被归一化洗掉**：`normalizeSql()` 移除块注释后，MySQL 可执行注释的 payload 消失 → 改用**原始文本**先判 `hasExecutableComment()`；同时把「普通注释里含写动词」改判为**合法对照**（注释里的 SQL 不执行），避免为了拦截量误杀业务。
2. **RFC 5737 文档专用 IP 被当成公网地址**：`203.0.113.24` 是保留段，不能当 `resolvedIp` 通过样本 → 改用真实可路由地址，并新增一条断言**拒绝**文档段（与 `GHSA-3rxj` IPv6 site-local 误判 public 同型）。
3. **短路断言用了会通过的配置**：初版 `evaluate()` 的阻断用例传的是合法 DCR，实际会走到调用期才 DENY → 换成跨租户导入后，才真正验证「在配置面短路、不进入调用期」。

> 这三条再次印证：**只写顺风测试会选错方案**。对照组与「不得劣于基线」是硬要求，不是装饰。

---

## 5. 接入主模块的分期建议（本期只给可运行装配，未实施）

| 分期 | 动作 | 状态 |
| --- | --- | --- |
| ① 最小可用 | 把 `McpRegistrationGate` 接进 `mcp-registry` 注册回调、`McpInvocationGate` 接进 `mcp-governance` 调用链；`McpControlPlaneInputGate` 挂在 `mcp-gateway` 的配置/建连入口 | ⏳ 本目录已给出接口 + 装配 + 123 断言回归，**主模块未改** |
| ② 管理面 | `IMPORT_*` / `SPEC_REVISION_*` 接 `mcp-tenant` 租户上下文与升级后存量全量复验 | 未做 |
| ③ 连接路径 | DCR 准入叠加真实 DNS 解析后 IP、重定向逐跳 SNI 证书校验 | 未做 |
| ④ 执行层 | 绑定参数 + 服务端只读事务 + 会话 RESET 接 `mcp-tools` 执行器（需真实 DB 回放） | 未做 |

---

## 6. ⚠️ 诚实边界（必读）

- **本框架不受** IBM / n8n / pgEdge / AWS / VS Code 等第三方 CVE 的直接影响；本目录是对其**攻击范式的独立参考实现**，不是补丁、不是受影响声明。
- `TaskGrant.signatureOk` 是**布尔标志，不是密码学签名**；生产必须换成真正的签名/验签与密钥轮换。
- `isPublicIpLiteral()` 是**字面量 + 分类检查**，不是 DNS 解析链 / SNI / 重定向逐跳校验；跨主机重定向默认拒绝是保守策略。
- SQL 筛查是**词法归一化**，不是 SQL 解析器；真实场景必须叠加绑定参数、服务端只读事务、会话状态复位。
- **全部代码仍位于 `examples/`，未接入 21 个主模块**，不得当生产组件；`mvn` CI 不受影响。
- **AAIF 治理移交不是最近 3 天发生的新事件**：MCP 于 2025-12-09 捐入 AAIF，Agent Skills 于 2026-10-22 随四项目加入；本期的增量是**采购/变更管理含义**的解读与可验收控制点，不是新闻首发。成员数在不同来源为 247 / 305，属不同时点口径，不做版本断言。
- DE-CIX 门测试为第三方启发式评分实验（`calibrated=false`，作者自报、单次测量），**不是本框架实测拦截率**；引用只取「语义评分不能当锁」这一方向性结论。
