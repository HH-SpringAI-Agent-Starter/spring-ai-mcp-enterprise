# 工具注解完整性 + 发现期审批门（V1.69 参考实现）

> **状态**：REFERENCE ONLY，纯 JDK 17 零依赖，不进 Maven reactor、零构建风险。
> **兑现**：V1.68 待办 ③ ——「把 `destructiveHint` 注解校验 + 写工具审批门落进 `mcp-registry` 注册期示例（对位 `CVE-2026-123812`）」。

---

## 一句话立场

**`annotations` 是 hints，不是 authorization。** 本示例把服务端声明的 `readOnlyHint` / `destructiveHint` / `openWorldHint` 只当作**待审材料**：风险档位由服务端独立计算，放行判断永远发生在服务端审批门，而不是客户端或模型对 hint 的解读。

---

## 为什么今晚必须做这个（11-09 ~ 11-11 四股力量同指一处）

| 时间 | 事实 | 对注册层的含义 |
| --- | --- | --- |
| **11-11** | [INITE Atlas 全量普查](https://inite.ai/en/atlas/mcp/report)（`numbers from 2026-11-11`）：当日实测 **11,393** 个 server；**25%（2,222）工具缺 read-only/destructive hint**、**4%（326）删除/发送/支付却标记非破坏性**、**23%（2,051）免登录 server 会改数据**、47% 参数无描述、52% 免登录即响应 | 注解谎报/缺失不是个别 bug，是**全生态基线缺陷**；靠客户端「看到 hint 就弹窗」不可用 |
| **11-09 / 11-11** | [Microsoft Agent Framework Python **1.25.0**](https://www.cloudproinc.com.au/index.php/2026/11/11/microsoft-agent-framework-mcp-approval-policies-governance-guide) 新增 **callable MCP discovery approval policies**（tool-name / read-only 过滤、caller override、**catalogue 刷新后复验审批**）；11-11 治理指南逐字「**Metadata should be verified rather than blindly trusted**」「tool metadata ... **an input to your policy rather than the final security decision**」「Read-only does not always mean harmless」 | 主流框架把「逐工具审批策略」做成产品能力，同时**明确否认元数据可作授权依据**；目录变更会使旧审批失效 |
| **11 月** | [OpenAI / Anthropic 目录上架规则](https://dawloom.com/blog/submit-mcp-server-to-chatgpt-and-claude)：OpenAI 要求三个 hint **显式 boolean**、逐操作独立工具；Anthropic 用 read/write hint 决定 **auto-permissions**（只读免逐次确认 / destructive 必弹窗）并**拒收 catch-all 工具**（如带 `method` 参数的 `api_request`） | 注解已变成**自动权限判定依据**，一旦谎报就是越权；但规范与[信任模型](https://devpress.csdn.net/aibjcy/6af303c1e5f4ca4eff8fa677.html)都写明「Hint 后缀是刻意设计，注解仅供参考、不构成安全约束」 |
| **11-10** | [Google Cloud Agent Registry 文档](https://docs.cloud.google.cn/agent-registry/manage-mcp-tools)（最后更新 11-10）：中央注册表 + Agent Gateway 强制执行 IAM 政策，「谁在什么条件下访问**特定工具**」 | 注册期准入被第五朵云做成标准产品层；本框架需在**开源 Java 侧**给出同口径的可验收实现 |
| **11-10** | [`CVE-2026-123812`](https://aithreatalert.com/cve/CVE-2026-123811) 写工具漏审批门 + 缺 `destructiveHint`；**同 server 的 `send_event` 因有 `requestHumanApproval` 挡住同一条攻击链** | 审批门是真实生效的边界，不是 UX 装饰 |
| **11-07** | [mcp-gatehouse v0.5.0](http://hol.org/registry/plugins/nickgeorgeseo%2Fmcp-gatehouse)（Python）READ/WRITE/DESTRUCTIVE 分层 + fail-closed 审批门 + append-only JSONL 审计 + 密钥脱敏 | 同构竞品已出现；差异化在 **Java/Spring 企业级跨模块** + **元数据漂移复验**（gatehouse 未覆盖） |

**结论**：治理的起点既不是网关也不是模型，而是**工具被注册进来的那一刻**。本示例把「注册期校验 → 独立定级 → 发现期审批 → 目录漂移复验 → 运行期强制门 → append-only 审计」串成一条可现场演示的链。

---

## 六条注册期硬校验

1. **三个 hint 必须显式 boolean**（`Boolean` 可为 null），缺一即拒 —— 对位 OpenAI 上架要求；**缺 hint 按最坏档 `DESTRUCTIVE` 推断**（`assumeWorstCase`）。
2. **自相矛盾拒收**：`readOnlyHint=true && destructiveHint=true`。
3. **谎报只读拒收**：名称 / 描述 / 参数中出现副作用动词（中英文 + 大小写归一：`delete/remove/send/pay/transfer/drop/…` / `删除/修改/写入/发送/支付/转账/…`）却声明 `readOnlyHint=true`。
4. **openWorld 谎报拒收**：`readOnlyHint=true && openWorldHint=true` —— 对位 INITE「标非破坏性却发送/支付」的 326 个实例。
5. **catch-all 工具拒收**：`api_request` / `generic_call` / `proxy` 等，或带 `method` / `action` / `operation` 参数且有副作用语义 —— 对位 Anthropic 目录硬性拒绝。
6. **整包环境变量读取工具默认禁用**（`get_env` / `get-env`）—— 对位 `@modelcontextprotocol/server-everything` 的 `get-env` 返回全部 `process.env`。

通过者按 **`classify()` 独立定级**（`READ` / `WRITE` / `DESTRUCTIVE`），并落 **SHA-256 fingerprint**（名称 + 描述 + 参数 + 四 hint + 数据分类）。

## 运行期闸门

| 控制点 | 行为 |
| --- | --- |
| 未注册工具 | `DENY(UNREGISTERED_TOOL)` |
| 无服务端认证身份 | `DENY(NO_AUTHENTICATED_SUBJECT)`—— **不接受模型自称 `role=admin`** |
| `READ` | 自动放行，仍落审计（含脱敏后的参数） |
| `WRITE` / `DESTRUCTIVE` | 必须提供绑定 `toolName + fingerprint + subject` 的**人工审批流水号** |
| 审批器缺失 / 抛异常 / 返回 false | 一律 `DENY`（`NO_APPROVER_FAIL_CLOSED` / `APPROVER_ERROR_FAIL_CLOSED`） |
| `DESTRUCTIVE` + override | 一律拒绝；`WRITE` override 必须带理由，并审计「who / bypassed which policy / reason / outcome」 |
| **catalogue 刷新** | `refreshCatalogue()` 重算 fingerprint，任何变更**立即作废既有审批**（对位 MS 1.25.0「refresh approval decisions」） |
| 审计 | append-only 列表；`redact()` 把 `api_key` / `password` / `token` / `secret` / `credential` 的值打码；**拒绝分支同样留痕** |

## 运行

```bash
javac -encoding UTF-8 -d /tmp/mcp-selftest McpToolAnnotationGate.java
java -Dfile.encoding=UTF-8 -cp /tmp/mcp-selftest com.example.mcp.registry.McpToolAnnotationGate
```

本期实测输出（2026-11-11，JDK 17.0.8）：

```
self-test OK (17 attack cases + 9 control cases + 6 boundary assertions)
total assertions: 32
tier classification is INDEPENDENT of declared hints; annotations are inputs to policy, never the authorization decision.
```

**对照组断言**（延续本项目工程约定）：`legacyAllows()` 模拟「只查工具名黑名单」的旧基线 —— 旧基线放行的合法只读工具新方案必须同样放行，旧基线会拦的删除类新方案也不得放行调用（**不得劣于既有基线**）。本次开发中断言确实抓到两处逻辑错误：① 诚实声明 `destructiveHint=true` 的删除工具应当**允许注册但强制审批**，而不是拒绝注册；② 审计脱敏若只替换字段名会把明文值留在串里 —— 两处均已修正。**只看「拦得更多」或只看「误报更少」都会选错方案。**

## 框架模块对位

| 示例能力 | 本框架模块 / 文件 |
| --- | --- |
| 注册期 hint 校验 + fingerprint | `mcp-registry` / `mcp-core`（工具定义与描述、参数校验） |
| 独立风险分级 + 发现期策略 | `mcp-governance`（准入策略、逐工具 scope） |
| 人工审批门 / override 留痕 | `mcp-governance`（HITL）+ `mcp-auth`（服务端身份） |
| append-only 审计 + 脱敏 | `mcp-audit` + `examples/audit-ropa-export/RopaExportService.java` |
| 审批作废与指标 | `examples/governance-hardening/McpSecurityMetrics.java` + `examples/monitor-alert-rules/` |

## 诚实边界

- 这是 `examples/` 下的**参考实现**，尚未接入 `mcp-registry` 主模块，也未写进 reactor；`main()` 自检是纯 JDK 逻辑验证，不是集成测试。
- 副作用动词表是**保守启发式**，不是语义证明：真实生产仍需 per-tool 语句文法（见 [`examples/tool-input-grammar/`](../tool-input-grammar/README.md)）与人工复核；合法 `POST /search` 会被误判为 `WRITE` 需走白名单豁免。
- `readOnlyHint=true` 不等于无害 —— 只读查询高管邮箱同样是高风险，本示例只把数据分类作为字段保留，**未实现基于数据分级的差异化策略**（MS 指南明确要求）。
- fingerprint 覆盖元数据，但**不覆盖工具实现代码**；实现被替换而描述不变时无法检出。
