# 结果 / 字段面守卫 · 可运行参考实现（V1.74）

> **六段式收口的第六段**：注解（V1.69）→ 注册中心（V1.70）→ 只读声明（V1.71）→ 决策层（V1.72）→ 配置面（V1.73）→ **结果 / 输出面（V1.74）**。
> 纯 JDK 17、零依赖、**不进 Maven reactor**（对 21 模块 CI 零风险）。

---

## 1. 一句话判词

> **工具把什么交回给模型，也不是安全边界。**
> 前面五段管的是「谁能动、动之前怎么判」，这一段管的是**动完之后交出去的东西**：回传的字段、写出的文件、外发的端点、拼接的 shell。
> 声明层的 `readOnlyHint` **不参与授权** —— 它和注解、审批弹窗、模型自证一样，**只进审计、不进允许分支**。

---

## 2. 六道闸

| 闸 | 规则 | 对位披露（2026-11-15~17） | 自检用例 |
| --- | --- | --- | --- |
| CP1 | `SENSITIVE_FIELD_RETURNED` | **REBUILD `CVE-2026-129875`**：QueryRecords 把 `User.password` 当可过滤字段，count 当盲注 oracle 逐字符恢复管理员 SHA-256 哈希 | A01–A10 |
| CP6 | `READONLY_HINT_NOT_AUTHORITY` | **AWS Pricing `CVE-2026-126816`**：工具被错误标记 read-only → MCP 客户端自动批准，实际是路径穿越任意文件写 | A25–A27 |
| CP3 | `OUTPUT_PATH_ESCAPE` | 同上，路径穿越：输出必须落在允许根目录内、无 `..` | A14–A16 |
| CP4 | `EGRESS_ENDPOINT_NOT_ALLOWLISTED` | **AWS Timestream `CVE-2026-126681`**（prompt injection 把 API token 泄漏到攻击者端点）+ **MCP Python SDK `CVE-2026-115563`**（向不可信 auth 端点泄漏 OAuth 密钥） | A17–A20 |
| CP5 | `SHELL_METACHAR_IN_PARAM` | **code-review-mcp-server `CVE-2026-68934`（8.7/CWE-78）**：`folderPath`/`baseBranch` 拼进 `execSync` | A21–A24 |
| CP2 | `ORACLE_ENUMERATION_DETECTED` | REBUILD 129875 的盲注侧：敏感字段上的前缀匹配枚举，达到阈值即判定 oracle | A11–A13 |

**判定顺序**：CP1 → CP6 → CP3 → CP4 → CP5 → CP2。任一命中即 DENY，字段值与决策规则写入 append-only 审计（值一律 `[REDACTED]`）。

---

## 3. 运行

```bash
javac -encoding UTF-8 McpResultFieldGuard.java
java -Dfile.encoding=UTF-8 McpResultFieldGuard
```

```
attack=27 control=12 boundary=4 TOTAL=43
incremental(legacy allowed but blocked)=16 / control false-positive=0
failures=0
SELF-TEST OK (pure JDK, zero dependency, all groups green)
```

---

## 4. 对照组设计（为什么不是只写顺风测试）

- **12 条合法对照 0 误杀**：非敏感字段（name/balance/status/displayName）、非敏感字段前缀搜索（customer_name/title，即便 100 次猜测）、敏感字段的**等值单次**查询（EQUAL 1 次 / 2 次）、允许根目录内的文件写出、allowlist 端点、无元字符的 `git status`。
- **「不得劣于旧基线」断言**：旧基线（`readOnlyHint=true || 无副作用` 即放行）会放行的 16 条攻击，新守卫全部拦下（敏感字段回传 10 条 + 盲注 3 条 + 只读声明陷阱 3 条）。
- **审计脱敏断言（`B03`）**：`password=supersecret` 被拒后，明文 `supersecret` 不出现在任何审计行。
- **append-only 断言（`B02`）**：被拒的调用不写 allow 审计，仅放行的调用写 1 条。

> 「只写顺风测试会选错方案」：若只测「拦了多少攻击」，`/etc/passwd` 也会被误判为「成功拦截业务写文件」；必须有合法写文件/外发/shell 对照组才不会把正常业务一起打死。

---

## 5. 诚实边界

- 本框架**不受**上述第三方 CVE 直接影响；本示例是对其攻击范式的独立参考实现。
- 敏感字段检测是**字段名 token 归一化匹配**（`alnum` + `contains`），非语义解析：可能误杀同名业务字段（如 `hashCode` 含 `hash`），生产应配字段白名单 + 业务语义复核。
- 盲注阈值（默认 5 次前缀猜测）是**演示常量**，非统计最优；真实场景应结合身份、时间窗、字段敏感度分级。
- IP 字面量「一律拒绝」是保守策略：本守卫不接受 IP 直连，合法内网 IP 场景需走 DNS 名称 + allowlist。
- shell 元字符是**词法黑名单**，非 shell 解析器；正确姿势仍是 argv 数组启动（见 `examples/stdio-command-allowlist/`），本示例只做结果面兜底。
- **未接入 21 个主模块**，仅 `examples/` 层参考实现；`readOnlyHint` 为声明布尔值，非密码学签名。
- 正面参考 Masttro MCP（11-16，默认禁用 + 显式激活 + 严格只读 + 可撤销）与 Tochka Bank（11-16，准备付款但须本人签名）说明：**「准备不等于执行」是可验收的产品形态**，与本守卫 CP6 同源。
