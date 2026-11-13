# read-only-enforcement —— 「只读」执行层强制门（V1.71）

> 纯 JDK 零依赖、不进 Maven reactor、零构建风险。可直接 `javac` 编译并跑 `main()` 自检。
> 对位 2026-11-10~11-12 的 **pgEdge Postgres MCP Server 7 个 CVE** 与
> **modelcontextprotocol/servers 5 个 GHSA**。

## 一句话

`read_only` 是**声明**，不是**边界**。工具注解、工具描述里的「只读」、连接串上的
`read_only` 标签都只是元数据——它们描述期望，不产生强制。pgEdge 一个产品在 4 天内
被连续报出 7 个 CVE，全部是「声明只读」被「执行层」绕过：**安全边界必须落在语句
真正执行之前，且由多个相互独立的控制点组成**。

本示例把 `ReadOnlyClaim`（声明层）**完全排除在授权判定之外**，由 6 个控制点裁决。

## 编译与自检

```bash
javac -encoding UTF-8 -d out examples/read-only-enforcement/McpReadOnlyEnforcementGate.java
java -Dfile.encoding=UTF-8 -cp out com.example.mcp.readonly.McpReadOnlyEnforcementGate
```

预期输出：

```
[McpReadOnlyEnforcementGate] self-test OK (22 attack + 9 control + 8 boundary = 79 assertions; 20 incremental blocks over legacy baseline)
CP1 lex  CP2 bind+tx  CP3 session  CP4 origin  CP5 env  CP6 path/ip
```

其中 **20 条增量拦截**＝旧基线「看 `readOnlyAnnotation` + 首个关键字是 SELECT/WITH
就放行」会全部错误放行、而执行层门全部拦下的真实载荷形态。对照组 9 条合法只读
查询全部放行，含引号内分号、dollar-quote 字符串、内联注释、只读 CTE、双引号标识符
——**不得劣于既有基线**（不误杀）。

## 6 个控制点 → CVE/GHSA 对位

| CP | 控制点 | 拒绝码 | 对位实证 |
| --- | --- | --- | --- |
| CP1 | 词法归一化语句分类：剥离注释/字符串/标识符/dollar-quote 后**全文扫描动词**，对分号堆叠计数 | `MULTI_STATEMENT_VIA_SIMPLE_QUERY_PROTOCOL` / `WRITE_VERB_IN_READONLY_TOOL_*` / `DOLLAR_QUOTE_TAG_ESCAPE` / `NON_READ_HEAD_VERB_*` / `UNCLOSED_*` | `CVE-2026-126196`(8.1) 简单查询协议堆叠多语句、`126199`(6.8) 只按首关键字分类、`126198`(3.1) 浅层写确认、`126197`(8.8) `$mcp_custom_tool$` / `$mcp_args$` 提前闭合 DO 块、`GHSA-w766` 系修复不完整 |
| CP2 | 强制参数绑定 + 服务端只读事务 + `where` 参数不得引用声明表之外 | `UNBOUND_PARAMS_FORCE_BIND_REQUIRED` / `NO_SERVER_SIDE_READ_ONLY_TRANSACTION` / `WHERE_SUBQUERY_OUT_OF_DECLARED_SCOPE` | `CVE-2026-125322`(6.5) `count_rows` 的 where 接受跨表子查询；`126196` 未识别语句无 bind 直发 |
| CP3 | 池化会话归还前 RESET / DISCARD，禁止状态残留 | `POOLED_SESSION_STATE_NOT_RESET` | `CVE-2026-126196` 后半：池化会话状态残留让**后续**调用变可写 |
| CP4 | Origin 白名单 + Host/Origin 一致性 + 身份不得取最左调用方可控值 | `ORIGIN_MISSING_OR_UNTRUSTED` / `HOST_ORIGIN_MISMATCH_DNS_REBINDING` / `IDENTITY_FROM_CALLER_SUPPLIED_LEFTMOST_VALUE` | `CVE-2026-125320`(5.3) 不校验 Origin 可 DNS rebinding 读库；`125321`(6.5) 取最左 XFF 派生身份 |
| CP5 | 只读工具执行环境净化：禁止加载仓库/工作区自带可执行配置 | `READONLY_TOOL_LOADS_UNTRUSTED_REPO_CONFIG` | `GHSA-6hqx-gqrx-c5r2`(7.8) `mcp-server-git` 只读工具信任仓库自身 `core.fsmonitor` → 任意命令执行 |
| CP6 | 校验路径与实际抓取路径同源 + 解析后 IP 分类完整 | `VALIDATED_PATH_NOT_SAME_AS_ACTUALLY_FETCHED` / `SSRF_IP_CLASSIFIER_GAP_*` | `GHSA-f9hh-cmx9-2f23`(3.1) robots.txt `//private` 绕过 `Disallow: /private`；`GHSA-3rxj-6jhw-qfv7`(3.1) IPv6 site-local `fec0::/10` 误判 public；`GHSA-66qx` NAT64 前缀 |

## 与既有示例的关系（组合而非重复）

| 既有示例 | 覆盖层 | 本示例增量 |
| --- | --- | --- |
| [`tool-input-grammar/`](../tool-input-grammar/README.md) | 语句**动词文法**（VACUUM INTO / 前缀检查） | 增加**词法归一化**（注释/字符串/dollar-quote 剥离后再判定）与**堆叠语句计数** |
| [`tool-annotation-gate/`](../tool-annotation-gate/README.md) | **注册期**注解完整性与审批门 | 增加**执行期**：声明层不参与授权，运行时上下文（绑定/事务/会话/来源/环境/IP）强制 |
| [`local-listener-guard/`](../local-listener-guard/README.md) | 监听绑定与 Content-Type/Origin 准入 | 增加**数据访问层**：只读事务与会话状态隔离 |
| [`connect-path-guard/`](../connect-path-guard/README.md) | 连接 URL 与解析后 IP 校验 | 增加**校验路径同源**与 IP 分类完整性断言 |
| [`registry-admin-guard/`](../registry-admin-guard/README.md) | **管理面**自防护与存量复验 | 三段式收口：注解不是边界 → 注册中心不是边界 → **只读声明也不是边界** |

## 诚实边界（务必随演示一起说明）

- 这是**参考实现**，未接入 `mcp-core` / `mcp-registry` 主模块，也不替代数据库自身的
  权限模型；生产必须**同时**使用最小权限只读角色（本示例的 CP2 是应用层第二道闸）。
- CP2 的 `where` 作用域、CP3 的会话状态、CP4 的身份来源、CP5 的仓库配置、CP6 的路径/IP
  在本示例中以**执行上下文标志**表达，用于演示控制点存在性与 fail-closed 行为；
  真实落地需接入 JDBC/`pgx` 元数据、连接池 hook、反向代理可信头、Git 配置隔离与
  DNS 解析后 IP 分类器。
- 动词黑名单是**保守白名单式拒绝**：正常业务若需要 `EXPLAIN ANALYZE`、临时表或
  `UNION` 跨表只读，应走显式审批与独立工具契约，而不是放宽本门。
- 与 [`tool-input-grammar/`](../tool-input-grammar/README.md) 一样，**只看顺风测试会选错方案**：
  自检内含 9 条合法只读对照与「不得劣于旧基线」断言，专门防止为了拦攻击而误杀正常查询。
- 上述 CVE/GHSA **不直接影响本框架**（本框架不内置 pgEdge / 官方参考 server 实现）；
  引用它们是为了说明**行业级失效模式**与买方验收口径。
