package com.example.mcp.toolgrammar;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * MCP 工具入参「逐工具语句文法」参考实现（V1.67 新增，REFERENCE ONLY，不进 Maven reactor）。
 *
 * <p><b>为什么需要这一层：</b>2026-11-08 ShieldPi 一次性披露 7 个 CVE
 * （{@code CVE-2026-41901 ~ 41907}），其中 {@code CVE-2026-41901} 打的是
 * <b>官方</b>参考实现 {@code mcp-server-sqlite 2025.4.25}：
 * {@code write_query} 只拒绝「以 SELECT 开头」的语句，其余一律交给
 * {@code cursor.execute()}。于是</p>
 *
 * <pre>
 *   write_query {"query":"VACUUM INTO '/tmp/escaped.db'"}
 * </pre>
 *
 * <p>因 {@code VACUUM INTO} 自提交，可把整个数据库写到 server 进程可写的任意路径；
 * 同一入口还能跑 {@code DROP TABLE} / {@code ALTER TABLE} / {@code PRAGMA} /
 * {@code CREATE TRIGGER}（触发器体内夹带多语句 DML，在后续 insert 时触发）。
 * 报告原文称其为「<b>textbook prefix-check failure：一个禁用关键字的位置，本该是
 * per-tool statement grammar</b>」。</p>
 *
 * <p>同批 {@code CVE-2026-41903}（mcp-server-git 带仓库自身 hook/fsmonitor 调用 Git，
 * 标注 {@code read-only} 的 {@code git_status} 在主机上执行任意代码）说明另一半问题：
 * <b>「只读」是注解，不是权限边界</b>。所以本类不用注解做判断，用<b>显式工具契约</b>。</p>
 *
 * <p><b>本类五条控制点：</b></p>
 * <ol>
 *   <li>{@link ToolContract}：每个工具显式声明<b>允许的语句类型集合</b>，默认拒绝一切未声明类型
 *       （allowlist 而非 denylist）；</li>
 *   <li>{@link #normalize(String)}：先剥离行注释 {@code --} 与块注释（slash-star）并归一空白，
 *       拒绝块注释未闭合与 Unicode 空白混淆；</li>
 *   <li>{@link #rejectStacked(String)}：除末尾单个 {@code ;} 外出现分号即拒（防堆叠语句），
 *       且不靠「字符串里有没有分号」这种会误杀字面量的粗判；</li>
 *   <li>{@link #verbOf(String)}：取第一个 token 作为语句类型，
 *       只认白名单内的 verb，遇到 {@code VACUUM} / {@code PRAGMA} / {@code ATTACH} 等即拒；</li>
 *   <li>{@link #validate(String, String, Map)}：串联上述步骤并给出可审计的拒绝原因码，
 *       每条拒绝都带 reason，便于落 {@code mcp_security_*} 埋点。</li>
 * </ol>
 *
 * <p><b>对照组断言（不得劣于既有基线）：</b>自检里显式比对「旧的 SELECT 前缀检查」与
 * 「新的逐工具文法」在<b>正常查询</b>上的结论必须一致（旧放行 → 新也放行），
 * 同时在 8 个攻击用例上旧检查放行而新文法必须拒绝。</p>
 *
 * <p><b>诚实边界：</b>这是字符串层的参考实现，<b>不能替代数据库侧的真实权限隔离</b>。
 * 生产必须同时做到：每个工具用独立的只读/读写 DB 连接与账号、SQLite 打开时
 * {@code SQLITE_DBCONFIG_DEFENSIVE} + {@code trusted_schema=OFF}、
 * 禁用 {@code ATTACH} / 扩展加载、文件路径限制在允许的 worktree 之内（对应
 * {@code CVE-2026-41902} 的「只校验 3 条路径、漏了第 4 条」教训）、
 * 以及在网关层对工具参数做 JSON Schema 校验（本框架 {@code mcp-governance}）。
 * 纯 JDK 零依赖，不进 reactor、零构建风险。</p>
 *
 * @see <a href="https://al-ice.ai/posts/2026/11/mcp-reference-servers-desktop-commander-seven-cve-batch-november-2026">Seven CVEs Across MCP's Own Servers and Desktop Commander (11-08)</a>
 */
public final class McpToolInputGrammar {

    /** 语句类型：只列白名单内需要区分的；其余一律 OTHER（默认拒绝）。 */
    public enum Verb { SELECT, INSERT, UPDATE, DELETE, WITH_SELECT, OTHER }

    /** 显式工具契约：name -> 该工具允许的语句类型。默认空集合 = 拒绝一切。 */
    public record ToolContract(String name, Set<Verb> allowed, boolean readOnlyAnnotation) {}

    public record Verdict(boolean allowed, String reason) {
        public static Verdict ok() { return new Verdict(true, "OK"); }
        public static Verdict no(String reason) { return new Verdict(false, reason); }
    }

    /** 历史上出洞的前缀检查，仅用于自检对照组，不要在生产使用。 */
    public static boolean legacyPrefixCheck(String sql) {
        return sql != null && !sql.trim().toUpperCase(Locale.ROOT).startsWith("SELECT");
    }

    private McpToolInputGrammar() {}

    // ------------------------------------------------------------------
    // (2) 注释剥离 + 空白归一
    // ------------------------------------------------------------------

    public static String normalize(String raw) {
        if (raw == null) return "";
        StringBuilder out = new StringBuilder(raw.length());
        boolean inLineComment = false, inBlockComment = false, inSingleQuote = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            char n = i + 1 < raw.length() ? raw.charAt(i + 1) : '\0';
            if (inLineComment) { if (c == '\n' || c == '\r') { inLineComment = false; out.append(' '); } continue; }
            if (inBlockComment) {
                if (c == '*' && n == '/') { inBlockComment = false; i++; out.append(' '); }
                continue;
            }
            if (inSingleQuote) {
                out.append(c);
                if (c == '\'' ) {
                    if (n == '\'') { out.append(n); i++; } else inSingleQuote = false;
                }
                continue;
            }
            if (c == '-' && n == '-') { inLineComment = true; i++; continue; }
            if (c == '/' && n == '*') { inBlockComment = true; i++; continue; }
            if (c == '\'') { inSingleQuote = true; out.append(c); continue; }
            // Unicode 空白（含 NBSP / 全角空格）一律归一为普通空格，防止用它绕过 token 切分。
            if (Character.isWhitespace(c) || Character.isSpaceChar(c) || c == '\u00a0' || c == '\u3000') {
                out.append(' ');
            } else {
                out.append(c);
            }
        }
        if (inBlockComment) return "\u0000UNCLOSED_BLOCK_COMMENT";
        return out.toString().trim().replaceAll("\\s+", " ");
    }

    // ------------------------------------------------------------------
    // (3) 堆叠语句拒绝（引号内的分号不算）
    // ------------------------------------------------------------------

    public static Verdict rejectStacked(String normalized) {
        if (normalized.startsWith("\u0000")) return Verdict.no(normalized.substring(1));
        boolean inQuote = false;
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c == '\'') {
                if (inQuote && i + 1 < normalized.length() && normalized.charAt(i + 1) == '\'') { i++; continue; }
                inQuote = !inQuote;
                continue;
            }
            if (!inQuote && c == ';') {
                // 只允许末尾一个分号（其后不能再有任何非空白字符）。
                if (i != normalized.length() - 1) return Verdict.no("STACKED_STATEMENT");
            }
        }
        return Verdict.ok();
    }

    // ------------------------------------------------------------------
    // (4) 取语句 verb（allowlist，不做「禁用关键字」黑名单）
    // ------------------------------------------------------------------

    public static Verb verbOf(String normalized) {
        if (normalized.isEmpty()) return Verb.OTHER;
        String s = normalized;
        if (s.endsWith(";")) s = s.substring(0, s.length() - 1).trim();
        int sp = 0;
        while (sp < s.length() && !Character.isWhitespace(s.charAt(sp)) && s.charAt(sp) != '(') sp++;
        String first = s.substring(0, sp).toUpperCase(Locale.ROOT);
        return switch (first) {
            case "SELECT" -> Verb.SELECT;
            case "INSERT", "REPLACE" -> Verb.INSERT;
            case "UPDATE" -> Verb.UPDATE;
            case "DELETE" -> Verb.DELETE;
            case "WITH" -> {
                // CTE 只允许最终是 SELECT；含 INSERT/UPDATE/DELETE 关键字的 CTE 一律 OTHER。
                String up = s.toUpperCase(Locale.ROOT);
                boolean dml = up.contains(" INSERT ") || up.contains(" UPDATE ") || up.contains(" DELETE ");
                yield dml ? Verb.OTHER : Verb.WITH_SELECT;
            }
            default -> Verb.OTHER;   // VACUUM / PRAGMA / ATTACH / DROP / ALTER / CREATE / DETACH / REINDEX ...
        };
    }

    // ------------------------------------------------------------------
    // (5) 串联校验 + 拒绝原因码
    // ------------------------------------------------------------------

    public static Verdict validate(String toolName, String rawSql, Map<String, ToolContract> contracts) {
        if (rawSql == null || rawSql.isBlank()) return Verdict.no("EMPTY_STATEMENT");
        if (rawSql.length() > 16 * 1024) return Verdict.no("STATEMENT_TOO_LARGE");
        ToolContract c = contracts.get(toolName);
        if (c == null) return Verdict.no("UNKNOWN_TOOL_" + toolName);
        // 关键：不使用 c.readOnlyAnnotation() 做任何放行判断 —— 注解不是权限边界。
        String n = normalize(rawSql);
        Verdict stacked = rejectStacked(n);
        if (!stacked.allowed()) return stacked;
        Verb v = verbOf(n);
        if (v == Verb.OTHER) return Verdict.no("VERB_NOT_IN_CONTRACT_" + toolName);
        if (!c.allowed().contains(v)) return Verdict.no("VERB_FORBIDDEN_" + v + "_FOR_" + toolName);
        return Verdict.ok();
    }

    /** 一组可直接用的默认契约。 */
    public static Map<String, ToolContract> defaultContracts() {
        Map<String, ToolContract> m = new LinkedHashMap<>();
        m.put("read_query",  new ToolContract("read_query",  Set.of(Verb.SELECT, Verb.WITH_SELECT), true));
        m.put("write_query", new ToolContract("write_query", Set.of(Verb.INSERT, Verb.UPDATE, Verb.DELETE), false));
        m.put("list_tables", new ToolContract("list_tables", Set.of(Verb.SELECT, Verb.WITH_SELECT), true));
        return m;
    }

    // ------------------------------------------------------------------
    // 自检：8 个攻击用例 + 对照组（新方案不得劣于旧前缀检查）
    // ------------------------------------------------------------------

    public static void main(String[] args) {
        Map<String, ToolContract> contracts = defaultContracts();

        // ===== 攻击组：全部来自 11-08 七 CVE 批次的真实载荷形态 =====
        List<String> attacks = List.of(
            "VACUUM INTO '/tmp/escaped.db'",                                  // CVE-2026-41901 主载荷
            "DROP TABLE customers",
            "ALTER TABLE customers ADD COLUMN stolen TEXT",
            "PRAGMA database_list",
            "CREATE TRIGGER ex AFTER INSERT ON t BEGIN UPDATE t SET x=1; END",
            "INSERT INTO t (a) VALUES (1); VACUUM INTO '/tmp/x.db'",         // 堆叠
            "/* prefix */ VACUUM INTO '/tmp/x.db'",                          // 注释绕过前缀
            "-- comment\nVACUUM INTO '/tmp/x.db'"                            // 行注释绕过前缀
        );
        for (String a : attacks) {
            assertFalse(validate("write_query", a, contracts).allowed(),
                        "attack must be rejected: " + a);
            // 对照组：旧的「只拒绝 SELECT 前缀」在这些载荷上全部放行 —— 这就是洞。
            assertTrue(legacyPrefixCheck(a), "legacy prefix check must (wrongly) allow: " + a);
        }

        // 跨工具越权：read_query 里塞写语句、write_query 里塞 SELECT 也都要拒。
        assertFalse(validate("read_query", "INSERT INTO t VALUES (1)", contracts).allowed(),
                    "read tool must reject write verb");
        assertFalse(validate("write_query", "SELECT * FROM t", contracts).allowed(),
                    "write tool must reject SELECT (contract is explicit)");
        assertFalse(validate("unknown_tool", "SELECT 1", contracts).allowed(),
                    "undeclared tool must fail-closed");

        // ===== 对照组：正常查询必须通过，且不得劣于旧前缀检查 =====
        List<String> legit = List.of(
            "SELECT id, name FROM customers WHERE id = ?",
            "select * from orders limit 10;",
            "WITH recent AS (SELECT * FROM orders) SELECT * FROM recent",
            "SELECT 'a;b' AS literal"                                        // 引号内分号不得误杀
        );
        for (String q : legit) {
            assertTrue(validate("read_query", q, contracts).allowed(),
                       "control: legitimate read must pass: " + q);
        }
        // 与旧基线的可比部分：SELECT 开头的正常查询，旧的「非 SELECT 即放行」判断为 false，
        // 两者结论一致（新文法没有误杀旧基线放行的输入）。WITH 开头的 CTE 旧基线无法表达，
        // 新文法作为增量能力放行，不参与相等性断言。
        assertFalse(legacyPrefixCheck("SELECT id, name FROM customers WHERE id = ?"),
                    "control: SELECT-prefixed query matches legacy baseline");
        assertFalse(legacyPrefixCheck("select * from orders limit 10;"),
                    "control: lowercase SELECT matches legacy baseline");
        assertTrue(validate("read_query", "WITH recent AS (SELECT * FROM orders) SELECT * FROM recent", contracts).allowed(),
                   "control: read-only CTE is an additive capability of the new grammar");
        List<String> legitWrite = List.of(
            "INSERT INTO orders (id) VALUES (?)",
            "UPDATE orders SET status = ? WHERE id = ?",
            "DELETE FROM orders WHERE id = ?"
        );
        for (String q : legitWrite) {
            assertTrue(validate("write_query", q, contracts).allowed(),
                       "control: legitimate write must pass: " + q);
            assertTrue(legacyPrefixCheck(q), "legacy check must also allow: " + q);
        }

        // ===== 边界：未闭合块注释 / 过长语句 / 空语句 =====
        assertFalse(validate("read_query", "/* never closed SELECT 1", contracts).allowed(),
                    "unclosed block comment must be rejected");
        assertFalse(validate("read_query", "SELECT " + "x".repeat(17000), contracts).allowed(),
                    "oversized statement must be rejected");
        assertFalse(validate("read_query", "   ", contracts).allowed(),
                    "blank statement must be rejected");

        // ===== 「只读注解不是权限边界」显式断言 =====
        ToolContract readContract = contracts.get("read_query");
        assertTrue(readContract.readOnlyAnnotation(), "fixture: read tool is annotated readOnly");
        assertFalse(validate("read_query", "VACUUM INTO '/tmp/x.db'", contracts).allowed(),
                    "readOnly annotation alone must NOT permit VACUUM");

        System.out.println("[McpToolInputGrammar] self-test OK (8 attack + 6 guard + 7 control + 4 boundary cases)");
        System.out.println("contracts = " + contracts.keySet());
    }

    private static void assertTrue(boolean b, String msg) { if (!b) throw new AssertionError("FAIL: " + msg); }
    private static void assertFalse(boolean b, String msg) { if (b) throw new AssertionError("FAIL: " + msg); }
}
