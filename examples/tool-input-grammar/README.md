# MCP 工具入参「逐工具语句文法」（Reference Only）

> V1.67 新增 · 2026-11-09 · **不进 Maven reactor、不参与 CI 构建、零外部依赖、纯 JDK 17**
> `javac -encoding UTF-8 -d /tmp/out McpToolInputGrammar.java && java -cp /tmp/out com.example.mcp.toolgrammar.McpToolInputGrammar`

---

## 为什么单独做这一层

2026-11-08，研究者 ShieldPi 一次性披露 **7 个 CVE（`CVE-2026-41901` ~ `CVE-2026-41907`）**，其中 4 个打的是 **MCP 官方参考实现** `modelcontextprotocol/servers`（git / sqlite / fetch），3 个打 Desktop Commander。最典型的是 `CVE-2026-41901`：

```json
{"tool": "write_query", "arguments": {"query": "VACUUM INTO '/tmp/escaped.db'"}}
```

`mcp-server-sqlite 2025.4.25` 的 `write_query` **只拒绝以 `SELECT` 开头的语句**，其他一律 `cursor.execute()`。而 SQLite 的 `VACUUM INTO` 是自提交的 → 把整个数据库写到 server 进程可写的任意路径；同一入口还能跑 `DROP TABLE` / `ALTER TABLE` / `PRAGMA` / `CREATE TRIGGER`（触发器体夹带多语句 DML，后续 insert 时触发）。披露原文定性为：

> **textbook prefix-check failure: one forbidden keyword where a per-tool statement grammar belonged**
> （教科书级的前缀检查失败：本该放「逐工具语句文法」的位置，只放了一个禁用关键字）

同批 `CVE-2026-41903` 是另一半问题：`mcp-server-git` 带仓库自身配置调用 Git，标注 **`read-only`** 的 `git_status` 会触发 `core.fsmonitor`、执行 `post-checkout` hook → 在主机上任意代码执行。**「只读」是注解，不是权限边界。**

> ⚠️ 补充事实：PyPI 上的 `mcp-server-sqlite` 上游**已归档**，报告明确指出补丁「必须落在真正 vendored 代码的地方」—— 也就是说「升级官方依赖」这条路在这批洞上根本不存在。

---

## 五条控制点

| # | 方法 | 挡住什么 | 验收 |
| --- | --- | --- | --- |
| 1 | `ToolContract(name, allowed, readOnlyAnnotation)` | 未声明的工具 / 未声明的语句类型 | 每个工具显式声明允许的 `Verb` 集合，**默认拒绝一切**；`readOnlyAnnotation` 只作元数据，**不参与任何放行判断** |
| 2 | `normalize(sql)` | 注释绕过前缀、Unicode 空白混淆、未闭合块注释 | 剥离 `--` 行注释与块注释、归一 `\u00a0`/`\u3000` 等空白；引号内内容原样保留 |
| 3 | `rejectStacked(normalized)` | 堆叠语句 `INSERT ...; VACUUM INTO ...` | 除末尾单个 `;` 外出现分号即拒；**引号内的分号不误杀**（`SELECT 'a;b'` 必须通过） |
| 4 | `verbOf(normalized)` | `VACUUM` / `PRAGMA` / `ATTACH` / `DROP` / `ALTER` / `CREATE` / `DETACH` / `REINDEX` | 取第一个 token 做 **allowlist**，不是「禁用关键字」黑名单；`WITH` 仅当最终为 SELECT 时归 `WITH_SELECT` |
| 5 | `validate(tool, sql, contracts)` | 上述全部串联 + 可审计 reason | 每条拒绝带 reason 码（`VERB_NOT_IN_CONTRACT_*` / `STACKED_STATEMENT` / `UNKNOWN_TOOL_*` …），可直接落 `mcp_security_*` 埋点 |

**默认契约**：`read_query` / `list_tables` → `{SELECT, WITH_SELECT}`；`write_query` → `{INSERT, UPDATE, DELETE}`。

---

## 自检结果（已实测）

```
[McpToolInputGrammar] self-test OK (8 attack + 6 guard + 7 control + 4 boundary cases)
contracts = [read_query, write_query, list_tables]
```

- **8 个攻击用例**：全部来自 11-08 七 CVE 批次的真实载荷形态（`VACUUM INTO` / `DROP` / `ALTER` / `PRAGMA` / `CREATE TRIGGER` / 堆叠 / 块注释绕过 / 行注释绕过）→ 新文法全拒，且每个用例都断言**旧的前缀检查会（错误地）放行**。
- **6 个越权用例**：read 工具塞写语句、write 工具塞 SELECT、未声明工具、只读注解不能当权限、超大语句、空语句。
- **7 个对照组**：正常 SELECT / 小写 select / 只读 CTE / 引号内分号 / 三条合法写语句 → 全部必须通过；并显式断言 **新方案不得劣于旧基线**（旧放行的正常查询，新方案同样放行）。
- **4 个边界**：未闭合块注释、>16 KiB 语句、空白语句、只读注解独立性。

> 这条「对照组 + 不得劣于基线」是项目固化的方法论：只写顺风测试会选出错误的方案。

---

## 与已有示例的分工

| 层 | 位置 | 负责 | 目录 |
| --- | --- | --- | --- |
| **工具入参（本目录）** | 工具实现内部 / 网关参数校验 | 语句类型 allowlist、堆叠语句、注释绕过、只读注解不可信 | `examples/tool-input-grammar/` |
| 请求路径 | 服务端 / 网关 | 4 MiB 上限、fail-closed 鉴权、会话按用户、按 caller 授权、HITL、审计 | [`../governance-hardening/`](../governance-hardening/README.md) |
| 连接路径 | 客户端 / 调试工具 | discovery 字符校验、argv 启动、版本下限、**解析后真实 IP 校验 + IP 固定** | [`../connect-path-guard/`](../connect-path-guard/README.md) |
| 检测响应 | 监控 / SIEM | 拒绝事件、出站、子进程、凭据访问 | [`../monitor-alert-rules/`](../monitor-alert-rules/README.md) |

在 RFP / 售前清单中的对应条目：[`docs/enterprise-rfp-checklist.md`](../../docs/enterprise-rfp-checklist.md) 攻击面第 19 / 20 类。

---

## 诚实边界（必须随示例一起说明）

- 这是**字符串层**的参考实现，**不能替代数据库侧真实权限隔离**。生产必须同时做到：
  - 每个工具用**独立的只读 / 读写 DB 连接与账号**；
  - SQLite 打开时设 `SQLITE_DBCONFIG_DEFENSIVE` + `trusted_schema=OFF`；
  - 禁用 `ATTACH` 与扩展加载；文件路径限制在允许的 worktree 内（对应 `CVE-2026-41902`「只校验 3 条路径、漏了第 4 条」的教训）；
  - 网关层对工具参数做 JSON Schema 校验（本框架 `mcp-governance`）。
- 本示例**不解析 SQL AST**，`WITH` 分支只做关键字粗判；复杂方言、存储过程、数据库特有语法需接真实 parser。
- 纯 JDK、不进 reactor、零构建风险；`mcp-governance` 主模块尚未内置本契约，接入前不能作为生产交付物承诺。
- 七 CVE 的披露日期、编号与根因以 [AL-ICE 11-08 复盘](https://al-ice.ai/posts/2026/11/mcp-reference-servers-desktop-commander-seven-cve-batch-november-2026) 与 NVD 官方条目为准。

---

## 参考

- 七 CVE 完整复盘（11-08）：https://al-ice.ai/posts/2026/11/mcp-reference-servers-desktop-commander-seven-cve-batch-november-2026
- 官方参考实现仓库：https://github.com/modelcontextprotocol/servers
- 完整情报：[`docs/earnings-report-2026-11-09.md`](../../docs/earnings-report-2026-11-09.md)
- 售前验收清单：[`docs/enterprise-rfp-checklist.md`](../../docs/enterprise-rfp-checklist.md)
