# 任务绑定的确定性参考监视器（Task-Bound Reference Monitor）

> V1.72 · 2026-11-14 · 参考实现，`examples/` 层，**不进 Maven reactor，零构建风险**
> 纯 JDK 17 单文件、零第三方依赖：`javac -encoding UTF-8` 可编译，`main()` 自带自检。

## 一句话

**护栏写在提示词里、审批弹窗里、工具注解里，都不是安全边界；唯一可验收的边界是模型之外、按当前任务签发的确定性执行门。**

## 为什么今天写这个

| 日期 | 公开实证 | 结论 |
| --- | --- | --- |
| 11-12 | arXiv 论文（MindPattern 复盘）：Qwen2.5-1.5B/7B、13 任务 12 工具、每防御每模型 156 攻击 episode | 模型提出攻击者工具调用 **64% / 84%**；**in-prompt guardrail 栈被持久自适应攻击者推到 100% 攻击成功率**；任务绑定的模型外确定性 monitor 只放行 **1.3% / 7.7%**，severe actions 保持 **0%** |
| 11-10 | VS Code 1.145.2 `GHSA-66hq-jw93-462h`（8.3 / NVD 9.6） | MCP server 返回的 elicitation 文本被解释成交互式 command link，用户点一下通知即命令执行；1.145.3 改为纯文本渲染 + 校验 payload |
| 11-11 | 官方参考 git server `GHSA-6hqx-gqrx-c5r2` | 只读 `git_status` 因仓库自带 `core.fsmonitor` 执行任意命令；修法是把 `GIT_CONFIG_COUNT` 强制置 false、清空 filter/merge drivers、拒 Git < 2.31 |
| 11-11 | Simon Willison `llm` 0.37.2 | 两处把配置的 OpenAI key 发给 OpenAI 兼容端点（未存 key 时 / `responses:true` + `-o chat_completions 1`） |
| 11-13 | **IBM ContextForge MCP Gateway 双 CVE（官方 Bulletin 首发）** | `CVE-2026-110178` `/v1/import` 的 `conflict_strategy=update` 不校验共享实体归属 → 持 team-scoped `admin.import` 者可覆写**其他租户**的 endpoint / headers / 凭据，把后续网关调用重定向到攻击者主机，**官方 Workarounds: None**；`CVE-2026-110194` OAuth DCR 从 metadata 拿到的 registration endpoint 未校验即请求 → SSRF 打内网 |
| 11-13/14 | AWS `2026-180-AWS` / `CVE-2026-125926` 版本区间坐实 | 影响 **v1.0.3~v1.34.0**，**1.35.0 修复**；管理 API 洞在 1.25.0 修，所以「跑 1.25.0+ 仍中 SSRF」；官方明确**升级不会修改升级前已存配置** → 必须人工全量复验 + 轮换云角色凭据 |
| 11-13 | Permit.io / Authorizon 控制面授权长文 | 三条可验收判据：`adapter:create` 独立权限、内部 ID 不由用户可控名称派生（409 是安全信号不得吞）、每个决策必须携带 tenant |

**四段式收口**：V1.69「注解不是安全边界」→ V1.70「注册中心自己也不是」→ V1.71「只读声明也不是」→ **V1.72「连审批与提示词护栏本身也不是」**。前三段是在堵各个声明层，这一段回答「那到底什么才算边界」。

## 六个控制点

| 编号 | 控制点 | 对位的真实攻击 | 关键设计 |
| --- | --- | --- | --- |
| CP1 | 授权源隔离 | 模型自证「已批准」、VS Code 命令链接、注解 `readOnlyHint` | 决策**只读**控制平面签发的 `TaskGrant`；`modelAttestation` / `readOnlyHint` 全程只记审计、不参与授权；非控制平面签发的 grant 直接拒 |
| CP2 | 任务绑定 + fail-closed | 跨任务重放、对象级 `account_id=123→124`（`CVE-2026-109492`）、工具名归一化绕过、堆叠语句 | `(taskId, tenant, tool, verb, scope, objectId)` 精确匹配，默认拒绝，有效期窗口，动词白名单 |
| CP3 | 导入归属 | `CVE-2026-110178` 跨租户覆写凭据 | 跨租户覆盖一律拒；`endpoint/headers/credentials` 为受保护字段；owner 变更必须走显式 transfer；同名冲突不得被 skip/update 吞掉 |
| CP4 | 上游准入 | `CVE-2026-110194`、`CVE-2026-125926`、云元数据 SSRF | DCR 默认关闭 + issuer 白名单 + 上游字面量校验（十进制/八进制/IPv6 site-local/169.254.169.254）+ 解析后真实 IP 复检 |
| CP5 | 存量复验 | AWS「升级不改存量配置」、V1.70「打补丁≠清干净」 | 升级后对已存配置全量复验，指纹漂移即隔离，幂等 |
| CP6 | 审计 + 对照组 | 凭据进日志、只看误报更少会选错方案 | append-only 脱敏审计；内置 `legacyInPromptGuardAllows()` 旧基线，断言「新方案不得劣于基线」 |

## 跑起来

```bash
cd examples/task-bound-monitor
javac -encoding UTF-8 -d /tmp/mcpv172 McpTaskBoundReferenceMonitor.java
java -cp /tmp/mcpv172 McpTaskBoundReferenceMonitor
```

本期实测输出（Windows 控制台中文乱码仅终端显示问题，文件本身 UTF-8）：

```
[attack] 16 cases, legacy-baseline would have ALLOWED 11 of them
         (incrementally blocked by the deterministic monitor)
[control] 8 legitimate cases ALLOWED, no regression vs legacy baseline
self-test: OK | attack=36 control=8 boundary=7 | total=51 | failures=0
         | incrementally-blocked-vs-legacy=11
audit entries (append-only, redacted): 48
```

**⭐ 重点不是「拦下 36 个攻击」，而是 8 个合法调用全部放行**：参数里含 `DROP TABLE`、`ignore previous instructions`、`/* ; DROP */` 的合法只读查询都没有被误杀。**只看拦截量会选出一个把业务全拦死的方案**——对照组断言是硬要求（本项目 `agromind` 标定方法论的第三条：只看「误报更少」会选错方案，必须含对照组断言）。

### 开发期断言抓到的真实 bug（保留记录）

首版把 `PROTECTED_FIELDS` 写成带下划线的 `request_headers`，而字段比对统一走 `canon()` 归一化成 `requestheaders` → 攻击用例「导入改写 `request_headers`」被误放行。修正为受保护字段集本身也归一化。**这正是"只写顺风测试会选错方案"的又一次应验。**

## ⚠️ 诚实边界（重要）

1. **这是决策语义的参考实现，不是生产组件。** 生产需要真实 MAC/签名校验 grant、真实 DNS 解析后 IP 校验与重定向逐跳重校验、持久化审计与事务一致性、与 `mcp-registry` / `mcp-governance` 的 SPI 对接。
2. **`TaskGrant` 的签发方 `CONTROL_PLANE` 在这里只是一个字符串标志**，示例不验证密码学签名；别把它当成真实信任根。
3. CP4 的主机准入是**字面量检查**，不含真实 DNS 解析、SNI/Host 一致性、重定向逐跳重校验；需与 `examples/connect-path-guard/McpResolvedIpGuard.java`、`examples/registry-admin-guard/` 叠加使用。
4. 本示例**未接入主模块**，是第四个独立 Gate；接入点评估见 [`docs/gate-integration-map-2026-11-14.md`](../../docs/gate-integration-map-2026-11-14.md)。
5. **本框架不受** 上述 VS Code / git server / IBM / AWS / llm 等 CVE 直接影响——这些是第三方组件的公开实证，用来论证控制点的必要性。

## 直接可用在标书 / 售前

- 12 项决策层验收清单、买方必问 5 问（含不合格/合格回答对照）、竞品差异化、三档报价 → [`docs/deterministic-control-plane-boundary-2026-11-14.md`](../../docs/deterministic-control-plane-boundary-2026-11-14.md)
- 四个 Gate 的接入点图与接入 `mcp-registry` 工作量评估 → [`docs/gate-integration-map-2026-11-14.md`](../../docs/gate-integration-map-2026-11-14.md)
- 攻击面 28 → 30 类售前一页纸 → [`docs/enterprise-rfp-checklist.md`](../../docs/enterprise-rfp-checklist.md)
