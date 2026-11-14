# 四个 Gate 的接入点图与 `mcp-registry` 接入工作量评估

> V1.72 · 2026-11-14 · **兑现 V1.71 待办③**（「画三个 Gate 的接入点图，评估接入 mcp-registry 工作量」）
> 本文只做**评估与分期建议，不改主代码**。四个 Gate 目前全部位于 `examples/`，**均未接入主模块**。

---

## 0. 结论先行

| 结论 | 说明 |
| --- | --- |
| **四个 Gate 不是四个产品，是同一条调用链上四个不同时机的检查** | 注册期 → 管理面 → 连接路径 → 执行期；**任一层失效不应导致全局放行** |
| **建议接入顺序**：`McpToolAnnotationGate` → `McpTaskBoundReferenceMonitor` → `McpRegistryAdminGuard` → `McpReadOnlyEnforcementGate` | 前两个是「注册期/调用期」同步门，改动面小、收益最大；后两个涉及存量与执行上下文，需独立分支 |
| **最小可用接入（分期①）≈ 2 个新接口 + 1 个配置类 + 1 组回归测试** | 不需要改 `mcp-core` 的鉴权模型，不需要引入新依赖 |
| ⛔ **不建议一次性把四个 Gate 塞进 reactor** | 会同时改动注册、管理、传输、执行四条路径，回归面过大；且与 21 模块现有 CI 的兼容验证成本高 |

---

## 1. 调用链与接入点全景

```
                         ┌───────────────────── 控制平面（模型之外） ─────────────────────┐
                         │                                                              │
   [管理员/平台] ──①──▶ │  GATE A: 注册期注解完整性门 (V1.69)                          │
   注册工具 / 导入配置   │  examples/tool-annotation-gate/                              │
                         │  · 六条硬校验 · 服务端独立定级 · 元数据指纹漂移复验           │
                         │  · fail-closed 审批门 · override 留痕                        │
                         │            │                                               │
                         │            ▼ 写入注册表                                      │
   [管理员/平台] ──②──▶ │  GATE B: 管理面自防护门 (V1.70)                              │
   组导入 / 改上游 /     │  examples/registry-admin-guard/                              │
   凭据 / 升级后复验     │  · 管理面授权 · 禁特权 scope 自授予 · 上游 SSRF 准入          │
                         │  · 重定向逐跳重校验 · 存量全量复验 + 指纹漂移                │
                         │            │                                               │
                         └────────────┼──────────────────────────────────────────────┘
                                      │ 注册表 / 策略快照（只读下发给执行侧）
                                      ▼
   [Agent / LLM] ──▶ MCP Client ──▶ mcp-gateway ──▶ ③ GATE C: 连接路径准入 (V1.66/67/70)
   提议一次工具调用       (不可信)      │              examples/connect-path-guard/
                                      │              + local-listener-guard/
                                      │              · 字符级 URL · 解析后真实 IP
                                      │              · Host/SNI 一致性 · 绑定/Origin/CORS
                                      ▼
                         ④ GATE D: 执行期确定性门
                            ├─ V1.71 只读执行层强制 examples/read-only-enforcement/
                            │    （声明层完全不参与授权，6 控制点，79 断言）
                            └─ ★ V1.72 任务绑定监视器 examples/task-bound-monitor/
                                 （CP1 授权源隔离 / CP2 任务绑定 fail-closed
                                   CP3 导入归属 / CP4 上游准入 / CP5 存量复验 / CP6 审计对照）
                                      │
                                      ▼
                          ┌───────────────────────────┐
                          │  真实工具 / DB / API / 文件  │
                          └───────────────────────────┘
                                      │
                                      ▼
                         append-only 脱敏审计 → mcp-audit / mcp-monitor（Prometheus）
```

**关键判据**：**授权决策的数据来源必须在图的左上方（控制平面），不能来自左侧的 `[Agent / LLM]` 通道。** V1.72 的 arXiv 实证说明，任何来自模型通道的「已批准 / 只读 / 用户同意」都不能当输入。

---

## 2. 四个 Gate 对照表

| Gate | 期次 | 触发时机 | 决策输入（可信） | 明确忽略（不可信） | 现有断言 | 对应主模块 |
| --- | --- | --- | --- | --- | --- | --- |
| **A** 注解完整性门 | V1.69 | 工具注册 / 元数据刷新 | 服务端独立定级、注册策略 | 工具自带 `destructiveHint` / 描述 | 32 | `mcp-registry` |
| **B** 管理面自防护门 | V1.70 | 组导入 / 改上游 / 升级后复验 | 管理面授权、issuer 白名单、存量指纹 | 注册数据派生的身份、请求体里的 scope | 45 | `mcp-registry` + `mcp-tenant` |
| **C** 连接路径准入 | V1.66/67/70 | 建连 / 重定向 / 监听启动 | 解析后真实 IP、Host/SNI、绑定配置 | 字面量 `http/https` 前缀、最左 XFF | 16+5 / 30 | `mcp-gateway` + `mcp-core` |
| **D1** 只读执行层 | V1.71 | SQL/命令实际执行前 | 词法全文扫描、绑定参数、服务端只读事务 | `read_only` 连接串、`readOnlyHint` | 79 | `mcp-tools` + `mcp-governance` |
| **D2** 任务绑定监视器 ★ | **V1.72** | 每次工具调用裁决 | 控制平面签发的 `TaskGrant` | `modelAttestation`、`readOnlyHint`、审批弹窗结果 | **51** | `mcp-governance` + `mcp-core` |

> 注：C 与 D1 的断言数分别来自 V1.66/67 与 V1.71 记录；四个 Gate 合计 **200+ 断言**，全部为纯 JDK 零依赖、`javac` 可编译、`main()` 自检。

---

## 3. 接入 `mcp-registry` 的接口设计（建议，未实施）

```java
// 建议新增于 mcp-registry（示意，非现有代码）
public interface McpRegistrationGate {
    /** 注册 / 元数据刷新时调用；返回 DENY 即 fail-closed，不写注册表 */
    GateVerdict screen(ToolRegistration registration, GateContext ctx);
}

public interface McpInvocationGate {
    /** 每次 tools/call 裁决；只读控制平面签发的 TaskGrant */
    GateVerdict decide(TaskGrant grant, ToolInvocation call, GateContext ctx);
}

// 组合链（有序、短路、全量留痕）
// registry:  McpToolAnnotationGate → McpRegistryAdminGuard(管理面动作)
// invocation: McpConnectPathGuard → McpTaskBoundReferenceMonitor → McpReadOnlyEnforcementGate
```

**设计约束（从四期踩坑里固化）**：

| # | 约束 | 原因 |
| --- | --- | --- |
| 1 | `GateContext` 必须携带 `tenant`、`taskId`、`grantIssuedBy` | Permit.io 三条判据：`may user X in tenant T update adapter Y in tenant T` |
| 2 | 声明层字段（hint / 注解 / 模型自证）**不得进入** `GateVerdict` 的允许分支 | V1.69/71/72 三段式判词 |
| 3 | 每个 Gate 独立可开关，**但任一 DENY 即整体 DENY**（AND 而非 OR） | 分层失效不导致全局放行 |
| 4 | 组合链必须保留**对照组回归测试** | 「只看拦截量会选出把业务全拦死的方案」 |
| 5 | 指纹漂移 / 存量复验必须是**幂等的读操作 + 显式隔离动作** | AWS「升级不改存量」，不能靠升级顺带修 |
| 6 | 审计字段白名单化，值脱敏 | V1.69 抓到「只替换字段名会把明文值留在串里」 |

---

## 4. 工作量评估（相对估算，非承诺工时）

| 分期 | 范围 | 改动面 | 风险 | 验收标准 |
| --- | --- | --- | --- | --- |
| **① 最小可用**（建议先做） | 把 Gate A + D2 接进 `mcp-registry` 注册期回调与 `mcp-governance` 调用期；新增 2 个接口 + 1 个 `GateChain` 配置类 + 1 组回归测试 | 2 个模块、`pom.xml` 不变、无新依赖 | **低**：纯同步门，不涉及传输与存量 | `mvn -q -pl mcp-registry,mcp-governance -am test` 通过；51+32 断言在 Spring 上下文里同样跑通；现有 21 模块 CI 不回归 |
| **② 管理面** | Gate B 接入导入/改上游/升级后复验路径，接 `mcp-tenant` 租户上下文 | 2~3 模块 + 管理 API | **中**：涉及管理 API 权限模型与存量数据 | 45 断言通过；跨租户导入必拒；升级后 `revalidateStored()` 可导出报告且幂等 |
| **③ 连接路径** | Gate C 接 `mcp-gateway` 建连/重定向钩子，叠加真实 DNS 解析后 IP | 传输层 | **中高**：DNS、SNI、重定向逐跳，容易引入可用性问题 | 需独立开关 + 灰度；`default-deny` 白名单先只放行已知Issuer |
| **④ 执行层** | D1 接 `mcp-tools` 执行器（绑定参数 / 服务端只读事务 / 会话 RESET） | 执行器 + 数据源 | **高**：与具体 DB/驱动强耦合，误杀风险最大 | 需真实 DB 集成测试 + 79 断言 + 业务 SQL 回放；建议独立分支长期验证 |

**跨期待办（沿用，未在 V1.72 解决）**：
- `exportPrometheus()` 真正挂进 `mcp-monitor` actuator（`mcp_security_*` 目前仍在 `examples/` 层）
- 基于 `dataClassification` 的差异化审批引擎
- 连接路径补重定向逐跳 SNI 证书校验
- 独立分支评估 Spring AI 2.0 升级（V1.68 已出迁移说明，**未执行升级**）

---

## 5. 诚实边界

1. **四个 Gate 全部是 `examples/` 层参考实现，均未接入主模块**，不得当作已交付的生产组件对外宣称。
2. `TaskGrant` 的 `issuedBy=CONTROL_PLANE` 目前是**字符串标志而非密码学签名**；生产接入必须补 MAC/签名校验与密钥轮换。
3. 连接路径示例只做**字面量**主机检查，DNS rebinding 需叠加解析后真实 IP 校验与 IP 固定；跨主机重定向默认拒绝是**保守策略**，可能影响正常多跳部署。
4. 上表工作量为**相对估算**，未做实际分支验证；「低风险」指改动面小，不代表零回归。
5. 本框架**不受** IBM / AWS / VS Code / git server 等第三方 CVE 直接影响；它们是论证控制点必要性的公开实证。
6. 本项目**无商业合同 / 中标 / 金融案例 / 等保测评**；广发四份为预征集 ≠ 采购邀请，7 项资质全不满足，建议联合体技术分包，禁虚构材料。

---

_配套：[`deterministic-control-plane-boundary-2026-11-14.md`](deterministic-control-plane-boundary-2026-11-14.md)（12 项验收 + 买方必问 5 问）｜[`examples/task-bound-monitor/README.md`](../examples/task-bound-monitor/README.md)（51 断言自检）｜[`enterprise-rfp-checklist.md`](enterprise-rfp-checklist.md)（攻击面 30 类售前一页纸）_
