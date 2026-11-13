package com.example.mcp.readonly;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MCP「只读」执行层强制门（V1.71，2026-11-13）。
 *
 * <p>状态：REFERENCE ONLY，纯 JDK 零依赖，不进 Maven reactor，零构建风险。
 *
 * <p>核心立场：<b>read_only 是声明，不是边界</b>。工具注解、工具描述里写的
 * 「只读」、连接串上的 read_only 标签，都只是元数据；它们描述期望，不产生强制。
 * 真正的安全边界必须落在执行层，且由多个相互独立、任一失效也不全局放行的
 * 控制点组成。本类把声明层字段完全排除在授权判定之外。
 *
 * <p>对位实证（2026-11-10 ~ 11-12，pgEdge Postgres MCP Server 7 个 CVE +
 * modelcontextprotocol/servers 5 个 GHSA）：
 * <ul>
 *   <li>CVE-2026-126196（8.1）：query_database 在 read_only 连接上把未识别语句
 *       经 pgx Exec 以<b>简单查询协议</b>发出，允许分号堆叠多语句、事务/会话状态改写，
 *       池化会话状态残留会让后续调用变可写。</li>
 *   <li>CVE-2026-126197（8.8）：custom pl-do 工具把 JSON 参数插值进以固定
 *       dollar-quote 标签分隔的 PostgreSQL DO 块，参数里带 mcp_custom_tool 或
 *       mcp_args 即可提前闭合块并注入任意 SQL / plpgsql。</li>
 *   <li>CVE-2026-126199（6.8）/ 126198（3.1）：bundled CLI / web client 只按
 *       语句<b>首个关键字</b>分类就决定是否请求写确认，注释前缀、CTE 内写操作可绕过。</li>
 *   <li>CVE-2026-125322（6.5）：count_rows 的 where 参数接受针对<b>声明表以外</b>的子查询。</li>
 *   <li>CVE-2026-125321（6.5）：启用认证的 HTTP transport 取<b>最左的调用方可控值</b>
 *       （X-Forwarded-For 最左项）派生身份。</li>
 *   <li>CVE-2026-125320（5.3）：HTTP transport 不校验 Origin，网页可 DNS rebinding 读库。</li>
 *   <li>GHSA-6hqx-gqrx-c5r2（7.8）：mcp-server-git 标为只读的工具处理仓库时信任
 *       仓库自带的 Git 配置，core.fsmonitor 被触发即任意命令执行 →
 *       「只读」工具照样执行代码。</li>
 *   <li>GHSA-f9hh-cmx9-2f23（3.1）：robots.txt 校验路径与实际抓取路径规范化不一致，
 *       双斜杠路径可绕过 Disallow。</li>
 *   <li>GHSA-3rxj-6jhw-qfv7（3.1）：SSRF 分类器把 IPv6 site-local 段误判为 public。</li>
 * </ul>
 *
 * <p>修复的共同形状：不是把 read_only 标签贴得更牢，而是<b>在语句真正执行前
 * 做词法归一化分类、强制参数绑定、服务端只读事务、会话状态复位、调用方身份与
 * 来源校验、工具执行环境净化、校验路径与实际路径同源</b>。
 *
 * <p>自检运行：{@code java -Dfile.encoding=UTF-8 com.example.mcp.readonly.McpReadOnlyEnforcementGate}
 */
public final class McpReadOnlyEnforcementGate {

    private static final int MAX_SQL_LEN = 16 * 1024;

    private static final Set<String> KNOWN_TOOLS = Set.of(
            "query_database", "count_rows", "list_tables",
            "read_text_file", "git_status", "fetch_url");

    private static final Set<String> ALLOWED_ORIGINS = Set.of(
            "https://app.corp.example", "https://mcp.corp.example");

    /** pgEdge pl-do 模板保留标签：客户端语句里出现即为块闭合逃逸。 */
    private static final Set<String> RESERVED_TEMPLATE_TAGS = Set.of(
            "mcp_custom_tool", "mcp_args");

    /** 只读工具禁止出现的动词（含事务、会话、DDL、DML、副作用函数）。 */
    private static final Set<String> WRITE_VERBS = Set.of(
            "INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "CREATE", "TRUNCATE",
            "VACUUM", "GRANT", "REVOKE", "COPY", "DO", "CALL", "EXECUTE",
            "SET", "RESET", "DISCARD", "BEGIN", "COMMIT", "ROLLBACK",
            "INTO", "PRAGMA", "ATTACH", "DETACH", "REINDEX", "REFRESH",
            "LOCK", "MERGE", "REPLACE", "UPSERT", "ANALYZE", "CLUSTER",
            "LISTEN", "NOTIFY", "UNLISTEN", "IMPORT", "LOAD", "COMMENT",
            "SECURITY", "OWNER", "PREPARE", "DEALLOCATE");

    /** 只读语句允许的首动词。注意 WITH 里的写动词会被全文扫描单独抓出。 */
    private static final Set<String> READ_HEADS = Set.of(
            "SELECT", "WITH", "VALUES", "TABLE", "SHOW", "EXPLAIN");

    private static final Pattern WORD = Pattern.compile("[A-Z_]+");

    public enum IpClass { PUBLIC, PRIVATE_OR_SITE_LOCAL, UNCLASSIFIED }

    // ---------------------------------------------------------------- 模型

    /**
     * 声明层：工具注册时提供的元数据。只读注解与连接标签仅用于展示 / 文档，
     * 绝不参与授权判定 —— 这正是 126196 的根因。
     */
    public static final class ReadOnlyClaim {
        public boolean declared = true;
        public String toolName = "query_database";
        public boolean readOnlyAnnotation = true;
        public String connectionLabel = "read_only";
    }

    /** 执行层上下文。默认值即「所有控制点均已就位」的安全态。 */
    public static final class ExecutionContext {
        public String sql = "SELECT id, name FROM customers WHERE id = ?";
        public boolean paramsBound = true;
        public boolean readOnlyTransaction = true;
        public boolean whereReferencesUndeclaredTable = false;
        public boolean sessionReused = true;
        public boolean sessionResetOnReturn = true;
        public boolean httpTransport = true;
        public String origin = "https://app.corp.example";
        public boolean hostMatchesOrigin = true;
        public boolean clientIdentityFromLeftmostForwarded = false;
        public boolean toolLoadsUntrustedRepoConfig = false;
        public String validatedPath = null;
        public String actualPath = null;
        public IpClass resolvedIpClass = IpClass.PUBLIC;

        static ExecutionContext safe() { return new ExecutionContext(); }
    }

    public static final class Verdict {
        public final boolean allowed;
        public final String reasonCode;
        public final boolean approvalRequired;

        Verdict(boolean allowed, String reasonCode, boolean approvalRequired) {
            this.allowed = allowed;
            this.reasonCode = reasonCode;
            this.approvalRequired = approvalRequired;
        }

        static Verdict allow(String code) { return new Verdict(true, code, false); }
        static Verdict deny(String code) { return new Verdict(false, code, true); }

        @Override public String toString() {
            return (allowed ? "ALLOW" : "DENY") + "(" + reasonCode + ")";
        }
    }

    private static final class LexResult {
        final boolean wellFormed;
        final boolean readOnly;
        final String reason;
        final boolean dollarQuoteEscape;
        final int statementCount;
        final String headVerb;
        final String hitVerb;

        LexResult(boolean wellFormed, boolean readOnly, String reason,
                  boolean dollarQuoteEscape, int statementCount,
                  String headVerb, String hitVerb) {
            this.wellFormed = wellFormed;
            this.readOnly = readOnly;
            this.reason = reason;
            this.dollarQuoteEscape = dollarQuoteEscape;
            this.statementCount = statementCount;
            this.headVerb = headVerb;
            this.hitVerb = hitVerb;
        }

        static LexResult bad(String reason) {
            return new LexResult(false, false, reason, false, 0, "-", "-");
        }
    }

    // ---------------------------------------------------------------- 主裁决

    /**
     * 执行层强制门。声明层字段只读不参与判定；6 个控制点全部通过才放行，
     * 任一失败即 fail-closed 并要求审批。
     */
    public static Verdict enforce(ReadOnlyClaim claim, ExecutionContext ctx) {
        // CP0：只有已注册工具可调用，fail-closed。声明层的 readOnlyAnnotation 不读。
        if (claim == null || !claim.declared) return Verdict.deny("UNDECLARED_TOOL_FAIL_CLOSED");
        if (!KNOWN_TOOLS.contains(claim.toolName)) return Verdict.deny("TOOL_NOT_REGISTERED_FAIL_CLOSED");
        if (ctx == null) return Verdict.deny("NULL_EXECUTION_CONTEXT");

        // CP1：词法归一化语句分类（对位 126199 / 126198 / 126197 / 126196）
        LexResult lr = classify(ctx.sql);
        if (!lr.wellFormed) return Verdict.deny(lr.reason);
        if (lr.dollarQuoteEscape) return Verdict.deny("DOLLAR_QUOTE_TAG_ESCAPE");
        if (lr.statementCount > 1) return Verdict.deny("MULTI_STATEMENT_VIA_SIMPLE_QUERY_PROTOCOL");
        if (lr.hitVerb != null) return Verdict.deny("WRITE_VERB_IN_READONLY_TOOL_" + lr.hitVerb);
        if (!READ_HEADS.contains(lr.headVerb)) return Verdict.deny("NON_READ_HEAD_VERB_" + lr.headVerb);

        // CP2：强制参数绑定 + 服务端只读事务 + where 参数作用域（对位 126196 / 125322）
        if (!ctx.paramsBound) return Verdict.deny("UNBOUND_PARAMS_FORCE_BIND_REQUIRED");
        if (!ctx.readOnlyTransaction) return Verdict.deny("NO_SERVER_SIDE_READ_ONLY_TRANSACTION");
        if (ctx.whereReferencesUndeclaredTable) return Verdict.deny("WHERE_SUBQUERY_OUT_OF_DECLARED_SCOPE");

        // CP3：池化会话状态隔离（对位 126196 后半：残留状态让后续调用变可写）
        if (ctx.sessionReused && !ctx.sessionResetOnReturn) {
            return Verdict.deny("POOLED_SESSION_STATE_NOT_RESET");
        }

        // CP4：调用方身份与来源校验（对位 125320 / 125321）
        if (ctx.httpTransport) {
            if (ctx.origin == null || !ALLOWED_ORIGINS.contains(ctx.origin)) {
                return Verdict.deny("ORIGIN_MISSING_OR_UNTRUSTED");
            }
            if (!ctx.hostMatchesOrigin) return Verdict.deny("HOST_ORIGIN_MISMATCH_DNS_REBINDING");
            if (ctx.clientIdentityFromLeftmostForwarded) {
                return Verdict.deny("IDENTITY_FROM_CALLER_SUPPLIED_LEFTMOST_VALUE");
            }
        }

        // CP5：工具执行环境净化（对位 GHSA-6hqx：只读工具加载仓库自带可执行配置）
        if (ctx.toolLoadsUntrustedRepoConfig) {
            return Verdict.deny("READONLY_TOOL_LOADS_UNTRUSTED_REPO_CONFIG");
        }

        // CP6：校验路径与实际路径同源 + 解析后 IP 分类完整（对位 f9hh / 3rxj）
        if (ctx.validatedPath != null && !samePath(ctx.validatedPath, ctx.actualPath)) {
            return Verdict.deny("VALIDATED_PATH_NOT_SAME_AS_ACTUALLY_FETCHED");
        }
        if (ctx.resolvedIpClass != IpClass.PUBLIC) {
            return Verdict.deny("SSRF_IP_CLASSIFIER_GAP_" + ctx.resolvedIpClass);
        }

        return Verdict.allow("READ_ONLY_ENFORCED_AT_EXECUTION_LAYER");
    }

    /** 旧基线：只读声明 + 首个关键字看起来是 SELECT/WITH 就放行。用于对照，不用于生产。 */
    public static boolean legacyReadOnlyCheck(ReadOnlyClaim claim, ExecutionContext ctx) {
        if (claim == null || !claim.readOnlyAnnotation) return false;
        String s = (ctx == null || ctx.sql == null) ? "" : ctx.sql.trim().toUpperCase(Locale.ROOT);
        return s.startsWith("SELECT") || s.startsWith("WITH");
    }

    // ---------------------------------------------------------------- 词法分类

    /**
     * 把注释、单引号字符串、双引号标识符、dollar-quote 块全部剥离后再分类。
     * 关键：<b>不看首关键字</b>，而是对归一化后的全文做动词扫描，并对分号堆叠计数。
     */
    static LexResult classify(String sql) {
        if (sql == null) return LexResult.bad("NULL_STATEMENT");
        if (sql.length() > MAX_SQL_LEN) return LexResult.bad("OVERSIZED_STATEMENT");

        StringBuilder clean = new StringBuilder(sql.length());
        boolean escapeTag = false;
        int i = 0, n = sql.length();

        while (i < n) {
            char c = sql.charAt(i);

            if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
                i += 2; clean.append("  ");
                while (i < n && sql.charAt(i) != '\n') { clean.append(' '); i++; }
                continue;
            }
            if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                i += 2; clean.append("  ");
                boolean closed = false;
                while (i < n) {
                    if (sql.charAt(i) == '*' && i + 1 < n && sql.charAt(i + 1) == '/') {
                        i += 2; clean.append("  "); closed = true; break;
                    }
                    clean.append(sql.charAt(i) == '\n' ? '\n' : ' ');
                    i++;
                }
                if (!closed) return LexResult.bad("UNCLOSED_BLOCK_COMMENT");
                continue;
            }
            if (c == '\'') {
                i++; clean.append(' ');
                boolean closed = false;
                while (i < n) {
                    if (sql.charAt(i) == '\'') {
                        if (i + 1 < n && sql.charAt(i + 1) == '\'') { clean.append("  "); i += 2; continue; }
                        i++; clean.append(' '); closed = true; break;
                    }
                    clean.append(' ');
                    i++;
                }
                if (!closed) return LexResult.bad("UNCLOSED_STRING_LITERAL");
                continue;
            }
            if (c == '"') {
                i++; clean.append(' ');
                boolean closed = false;
                while (i < n) {
                    if (sql.charAt(i) == '"') { i++; clean.append(' '); closed = true; break; }
                    clean.append(sql.charAt(i) == ';' ? ' ' : sql.charAt(i));
                    i++;
                }
                if (!closed) return LexResult.bad("UNCLOSED_QUOTED_IDENTIFIER");
                continue;
            }
            if (c == '$') {
                int tagEnd = dollarTagEnd(sql, i);
                if (tagEnd > i) {
                    String tag = sql.substring(i + 1, tagEnd - 1);
                    if (RESERVED_TEMPLATE_TAGS.contains(tag)) escapeTag = true;
                    String closing = "$" + tag + "$";
                    int close = sql.indexOf(closing, tagEnd);
                    if (close < 0) return LexResult.bad("UNCLOSED_DOLLAR_QUOTE");
                    for (int k = i; k < close + closing.length(); k++) clean.append(' ');
                    i = close + closing.length();
                    continue;
                }
            }
            clean.append(c);
            i++;
        }

        String cleaned = clean.toString();
        String[] parts = cleaned.split(";", -1);
        int stmtCount = 0;
        for (String p : parts) if (!p.trim().isEmpty()) stmtCount++;
        if (stmtCount == 0) return LexResult.bad("EMPTY_STATEMENT");

        String upper = cleaned.toUpperCase(Locale.ROOT);
        Set<String> tokens = new HashSet<>();
        Matcher m = WORD.matcher(upper);
        while (m.find()) tokens.add(m.group());

        String head = "-";
        Matcher hm = WORD.matcher(upper);
        if (hm.find()) head = hm.group();

        String hit = null;
        for (String v : WRITE_VERBS) {
            if (tokens.contains(v)) { hit = v; break; }
        }
        boolean readOnly = (hit == null) && READ_HEADS.contains(head);
        return new LexResult(true, readOnly, readOnly ? "READ_ONLY" : "WRITE_DETECTED",
                             escapeTag, stmtCount, head, hit);
    }

    /** 返回 dollar-quote 起始标签结束位置（第二个美元符之后），不匹配返回 -1。 */
    private static int dollarTagEnd(String s, int start) {
        int j = start + 1;
        if (j < s.length() && s.charAt(j) == '$') return j + 1;
        if (j >= s.length() || Character.isDigit(s.charAt(j))) return -1;
        while (j < s.length() && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '_')) j++;
        return (j < s.length() && s.charAt(j) == '$') ? j + 1 : -1;
    }

    private static boolean samePath(String a, String b) {
        return a != null && b != null && a.equals(b);
    }

    // ---------------------------------------------------------------- 自检

    private static int checks = 0;

    public static void main(String[] args) {
        int attack = 0, control = 0, boundary = 0, incremental = 0;

        // ===== 攻击组 A：旧基线会错误放行（声明层看不见的执行层绕过）=====
        // 全部以 SELECT/WITH 开头，保证 legacyReadOnlyCheck 返回 true。
        List<String> sqlAttacks = List.of(
            "SELECT 1; UPDATE accounts SET balance = 0",                       // 126196 堆叠
            "SELECT 1; COMMIT",                                                 // 126196 事务状态改写
            "SELECT * FROM t WHERE id = ?; SET session.authorization = 'admin'",// 126196 会话状态
            "WITH c AS (UPDATE t SET x = 1 RETURNING *) SELECT * FROM c",      // CTE 内写
            "SELECT $mcp_custom_tool$; DROP TABLE t; -- $mcp_custom_tool$",     // 126197 标签逃逸
            "SELECT $mcp_args$ BEGIN PERFORM evil(); END $mcp_args$; DELETE FROM t", // 126197
            "SELECT /* hint */ 1; VACUUM INTO '/tmp/x.db'",                    // 注释 + 堆叠
            "SELECT 1 -- tail\n; DELETE FROM audit_log"                          // 行注释 + 堆叠
        );
        for (String q : sqlAttacks) {
            ExecutionContext c = ExecutionContext.safe();
            c.sql = q;
            ReadOnlyClaim claim = new ReadOnlyClaim();
            assertFalse(enforce(claim, c).allowed, "attack must be rejected: " + q);
            assertTrue(legacyReadOnlyCheck(claim, c), "legacy baseline must (wrongly) allow: " + q);
            attack++; incremental++;
        }

        // ===== 攻击组 B：旧基线会错误放行（声明层完全不看的上下文维度）=====
        // 合法 SELECT + 单个执行层控制点被破坏。
        List<String> contextAttacks = List.of(
            "UNBOUND_PARAMS_FORCE_BIND_REQUIRED",
            "NO_SERVER_SIDE_READ_ONLY_TRANSACTION",
            "WHERE_SUBQUERY_OUT_OF_DECLARED_SCOPE",
            "POOLED_SESSION_STATE_NOT_RESET",
            "ORIGIN_MISSING_OR_UNTRUSTED",
            "HOST_ORIGIN_MISMATCH_DNS_REBINDING",
            "IDENTITY_FROM_CALLER_SUPPLIED_LEFTMOST_VALUE",
            "READONLY_TOOL_LOADS_UNTRUSTED_REPO_CONFIG",
            "VALIDATED_PATH_NOT_SAME_AS_ACTUALLY_FETCHED",
            "SSRF_IP_CLASSIFIER_GAP_PRIVATE_OR_SITE_LOCAL",
            "SSRF_IP_CLASSIFIER_GAP_UNCLASSIFIED"
        );
        for (String reason : contextAttacks) {
            ExecutionContext c = ExecutionContext.safe();
            switch (reason) {
                case "UNBOUND_PARAMS_FORCE_BIND_REQUIRED" -> c.paramsBound = false;
                case "NO_SERVER_SIDE_READ_ONLY_TRANSACTION" -> c.readOnlyTransaction = false;
                case "WHERE_SUBQUERY_OUT_OF_DECLARED_SCOPE" -> c.whereReferencesUndeclaredTable = true;
                case "POOLED_SESSION_STATE_NOT_RESET" -> c.sessionResetOnReturn = false;
                case "ORIGIN_MISSING_OR_UNTRUSTED" -> c.origin = "https://evil.example";
                case "HOST_ORIGIN_MISMATCH_DNS_REBINDING" -> c.hostMatchesOrigin = false;
                case "IDENTITY_FROM_CALLER_SUPPLIED_LEFTMOST_VALUE" -> c.clientIdentityFromLeftmostForwarded = true;
                case "READONLY_TOOL_LOADS_UNTRUSTED_REPO_CONFIG" -> c.toolLoadsUntrustedRepoConfig = true;
                case "VALIDATED_PATH_NOT_SAME_AS_ACTUALLY_FETCHED" -> {
                    c.validatedPath = "/private";
                    c.actualPath = "//private";                                  // f9hh 双斜杠
                }
                case "SSRF_IP_CLASSIFIER_GAP_PRIVATE_OR_SITE_LOCAL" -> c.resolvedIpClass = IpClass.PRIVATE_OR_SITE_LOCAL;
                case "SSRF_IP_CLASSIFIER_GAP_UNCLASSIFIED" -> c.resolvedIpClass = IpClass.UNCLASSIFIED;
                default -> throw new AssertionError("unmapped case: " + reason);
            }
            ReadOnlyClaim claim = new ReadOnlyClaim();
            Verdict v = enforce(claim, c);
            assertFalse(v.allowed, "context attack must be rejected: " + reason);
            assertTrue(v.reasonCode.equals(reason), "reason mismatch, got " + v.reasonCode + " expected " + reason);
            assertTrue(legacyReadOnlyCheck(claim, c), "legacy baseline must (wrongly) allow: " + reason);
            attack++; incremental++;
        }

        // ===== 攻击组 C：旧基线也能拦（注释前缀），不计入增量 =====
        List<String> prefixAttacks = List.of(
            "/* prefix */ VACUUM INTO '/tmp/x.db'",
            "-- comment\nDELETE FROM t",
            "DROP TABLE customers"
        );
        for (String q : prefixAttacks) {
            ExecutionContext c = ExecutionContext.safe();
            c.sql = q;
            assertFalse(enforce(new ReadOnlyClaim(), c).allowed, "prefix attack must be rejected: " + q);
            attack++;
        }

        // ===== 对照组：合法只读查询必须放行，且不得劣于旧基线 =====
        List<String> legit = List.of(
            "SELECT id, name FROM customers WHERE id = ?",
            "select * from orders limit 10;",
            "WITH recent AS (SELECT * FROM orders) SELECT * FROM recent",
            "SELECT 'DROP TABLE t; DELETE FROM t' AS literal",
            "SELECT 'a;b' AS literal",
            "SELECT /* inline hint */ count(*) FROM t",
            "SELECT $$a; b; UPDATE nothing$$ AS body",
            "SELECT \"weird;column\" FROM \"t\"",
            "SELECT 1 -- trailing comment\n"
        );
        for (String q : legit) {
            ExecutionContext c = ExecutionContext.safe();
            c.sql = q;
            ReadOnlyClaim claim = new ReadOnlyClaim();
            Verdict v = enforce(claim, c);
            assertTrue(v.allowed, "control: legitimate read must pass: " + q + " -> " + v);
            assertTrue(legacyReadOnlyCheck(claim, c), "control: legacy baseline also allows (comparable part): " + q);
            control++;
        }

        // ===== 边界组 =====
        ExecutionContext blank = ExecutionContext.safe(); blank.sql = "   ";
        assertFalse(enforce(new ReadOnlyClaim(), blank).allowed, "blank statement must be rejected");
        boundary++;

        ExecutionContext oversized = ExecutionContext.safe();
        oversized.sql = "SELECT " + "x".repeat(MAX_SQL_LEN + 1);
        assertFalse(enforce(new ReadOnlyClaim(), oversized).allowed, "oversized statement must be rejected");
        boundary++;

        ExecutionContext unclosedComment = ExecutionContext.safe(); unclosedComment.sql = "/* never closed SELECT 1";
        assertFalse(enforce(new ReadOnlyClaim(), unclosedComment).allowed, "unclosed block comment must be rejected");
        boundary++;

        ExecutionContext unclosedQuote = ExecutionContext.safe(); unclosedQuote.sql = "SELECT 'oops";
        assertFalse(enforce(new ReadOnlyClaim(), unclosedQuote).allowed, "unclosed string literal must be rejected");
        boundary++;

        ExecutionContext unclosedDollar = ExecutionContext.safe(); unclosedDollar.sql = "SELECT $$never closed";
        assertFalse(enforce(new ReadOnlyClaim(), unclosedDollar).allowed, "unclosed dollar quote must be rejected");
        boundary++;

        ReadOnlyClaim unregistered = new ReadOnlyClaim(); unregistered.toolName = "unknown_tool";
        assertFalse(enforce(unregistered, ExecutionContext.safe()).allowed, "unregistered tool must fail closed");
        boundary++;

        ExecutionContext stdio = ExecutionContext.safe();
        stdio.httpTransport = false; stdio.origin = null; stdio.hostMatchesOrigin = false;
        assertTrue(enforce(new ReadOnlyClaim(), stdio).allowed,
                   "boundary: non-HTTP transport must not require Origin (but all other CPs still hold)");
        boundary++;

        // ===== 「只读声明不是边界」显式断言 =====
        ReadOnlyClaim allDeclaredReadOnly = new ReadOnlyClaim();
        allDeclaredReadOnly.readOnlyAnnotation = true;
        allDeclaredReadOnly.connectionLabel = "read_only";
        ExecutionContext writeSql = ExecutionContext.safe();
        writeSql.sql = "SELECT * INTO stolen FROM customers";
        assertFalse(enforce(allDeclaredReadOnly, writeSql).allowed,
                    "readOnly annotation + read_only connection label must NOT permit SELECT INTO");
        assertTrue(legacyReadOnlyCheck(allDeclaredReadOnly, writeSql),
                   "legacy baseline wrongly allows SELECT INTO because it starts with SELECT");
        boundary++; incremental++;

        System.out.println("[McpReadOnlyEnforcementGate] self-test OK ("
                + attack + " attack + " + control + " control + " + boundary + " boundary = "
                + checks + " assertions; " + incremental + " incremental blocks over legacy baseline)");
        System.out.println("CP1 lex  CP2 bind+tx  CP3 session  CP4 origin  CP5 env  CP6 path/ip");
    }

    private static void assertTrue(boolean b, String msg) {
        checks++;
        if (!b) throw new AssertionError("FAIL: " + msg);
    }

    private static void assertFalse(boolean b, String msg) {
        checks++;
        if (b) throw new AssertionError("FAIL: " + msg);
    }
}
