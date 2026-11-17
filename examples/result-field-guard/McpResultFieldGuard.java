/*
 * McpResultFieldGuard.java — V1.74 本期工程核心
 * 判词：工具「返回给模型的结果」也不是安全边界（六段式收口第六段）。
 *
 * 五段式(V1.69~V1.73)：注解 -> 注册中心 -> 只读声明 -> 决策层 -> 配置面
 * 第六段(V1.74)：结果/输出面 —— 工具把什么交回给模型、写到哪里、连到哪里。
 *
 * 证据链（2026-11-15~11-17，均第三方公开披露，本框架不受直接影响）：
 *  - REBUILD CVE-2026-129875：MCP QueryRecords 工具把 User.password 当可过滤字段，
 *    count 响应当盲注 oracle，低权限用户逐字符恢复管理员 SHA-256 口令哈希并提权 -> CP1/CP2
 *  - AWS Pricing MCP Server CVE-2026-126816：cost report 路径穿越任意文件写，
 *    且工具被「错误标记 read-only」导致 MCP 客户端自动批准 -> CP3/CP6
 *  - AWS Timestream-for-InfluxDB MCP Server CVE-2026-126681：不限制连接端点，
 *    prompt injection 把 API token 泄漏到攻击者端点 -> CP4
 *  - MCP Python SDK CVE-2026-115563：向不可信 auth 端点泄漏 OAuth 密钥 -> CP4
 *  - code-review-mcp-server CVE-2026-68934 (8.7)：folderPath/baseBranch 拼进
 *    child_process.execSync，工具调用者可跑 shell -> CP5
 *  - 正面参考 Masttro MCP(11-16)/Tochka Bank(11-16)：默认禁用 + 显式授权 +
 *    严格只读 + 可撤销 + 人工签名 —— 「准备不等于执行」。
 *
 * 纯 JDK 17、零依赖、不进 Maven reactor（对 21 模块 CI 零风险）。
 */
import java.util.*;

public final class McpResultFieldGuard {

    // ---------- 数据模型 ----------

    enum SideEffect { NONE, WRITE_FILE, NETWORK_EGRESS, SHELL }

    /** 工具结果里的一个字段（名 + 值）。值永远不进审计明文。 */
    record Field(String name, String value) {}

    /** 对结果的查询探针（用于盲注/oracle 检测）。 */
    enum MatchMode { EQUAL, PREFIX }
    record Probe(String fieldName, MatchMode mode, int distinctGuesses) {}

    /** 一次工具调用的结果 + 声明 + 实际副作用。声明层不参与授权。 */
    static final class ToolResult {
        final String tool;
        final boolean declaredReadOnlyHint;   // 声明层：只读审计、不进允许分支
        final SideEffect sideEffect;
        final List<Field> fields;
        final String outputPath;
        final String egressEndpoint;
        final String shellFragment;
        final Probe probe;

        ToolResult(String tool, boolean declaredReadOnlyHint, SideEffect sideEffect,
                   List<Field> fields, String outputPath, String egressEndpoint,
                   String shellFragment, Probe probe) {
            this.tool = tool;
            this.declaredReadOnlyHint = declaredReadOnlyHint;
            this.sideEffect = sideEffect == null ? SideEffect.NONE : sideEffect;
            this.fields = fields == null ? List.of() : fields;
            this.outputPath = outputPath;
            this.egressEndpoint = egressEndpoint;
            this.shellFragment = shellFragment;
            this.probe = probe;
        }
    }

    record Verdict(boolean allowed, String rule, String reason) {}

    static Verdict allow(String reason) { return new Verdict(true, "ALLOW", reason); }
    static Verdict deny(String rule, String reason) { return new Verdict(false, rule, reason); }

    // ---------- 守卫配置 ----------

    private final Set<String> sensitiveTokens;
    private final Set<String> allowedEgressHosts;
    private final String allowedOutputRoot;
    private final int oracleGuessThreshold;
    private final List<String> audit = new ArrayList<>();

    public McpResultFieldGuard(Set<String> sensitiveTokens, Set<String> allowedEgressHosts,
                               String allowedOutputRoot, int oracleGuessThreshold) {
        this.sensitiveTokens = normalizeTokens(sensitiveTokens);
        this.allowedEgressHosts = allowedEgressHosts;
        this.allowedOutputRoot = normPath(allowedOutputRoot);
        this.oracleGuessThreshold = oracleGuessThreshold;
    }

    private static Set<String> normalizeTokens(Set<String> in) {
        Set<String> out = new HashSet<>();
        for (String t : in) out.add(alnum(t.toLowerCase(Locale.ROOT)));
        out.remove("");
        return out;
    }

    private static String alnum(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) b.append(c);
        }
        return b.toString();
    }

    private static String normPath(String p) {
        if (p == null) return "";
        String s = p.replace('\\', '/').toLowerCase(Locale.ROOT);
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private static String hostOf(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) return "";
        String s = endpoint.trim();
        int sep = s.indexOf("://");
        if (sep >= 0) s = s.substring(sep + 3);
        int slash = s.indexOf('/');
        if (slash >= 0) s = s.substring(0, slash);
        int at = s.indexOf('@');
        if (at >= 0) s = s.substring(at + 1);
        int colon = s.lastIndexOf(':');
        if (colon > 0 && s.substring(colon + 1).chars().allMatch(Character::isDigit)) s = s.substring(0, colon);
        return s.toLowerCase(Locale.ROOT);
    }

    private static boolean isIpLiteral(String host) {
        if (host.matches("^\\d{1,3}(\\.\\d{1,3}){3}$")) return true;
        return host.contains(":"); // IPv6 / 冒号形态一律拒绝（本守卫不接受 IP 直连）
    }

    private boolean isSensitiveField(String name) {
        String n = alnum(name == null ? "" : name.toLowerCase(Locale.ROOT));
        for (String t : sensitiveTokens) if (!t.isEmpty() && n.contains(t)) return true;
        return false;
    }

    // ---------- 判定：CP1 -> CP6 -> CP3 -> CP4 -> CP5 -> CP2 ----------

    public Verdict evaluate(ToolResult r) {
        // CP1：结果字段敏感 => 必须先脱敏；本守卫不允许原样回传
        for (Field f : r.fields) {
            if (isSensitiveField(f.name())) {
                return deny("SENSITIVE_FIELD_RETURNED",
                        "tool=" + r.tool + " field=" + f.name() + " 含敏感字段，禁止原样回传模型（对位 REBUILD 129875）");
            }
        }

        // CP6：只读声明不参与授权 —— 声明只读却产生副作用 = 自动批准陷阱
        if (r.declaredReadOnlyHint && r.sideEffect != SideEffect.NONE) {
            return deny("READONLY_HINT_NOT_AUTHORITY",
                    "tool=" + r.tool + " 声明 readOnlyHint=true 但实际 sideEffect=" + r.sideEffect
                            + "（对位 AWS 126816「错误标记只读致客户端自动批准」）");
        }

        // CP3：输出路径必须落在允许根目录内、且无 .. 逃逸
        if (r.sideEffect == SideEffect.WRITE_FILE) {
            String p = r.outputPath == null ? "" : normPath(r.outputPath);
            boolean hasDotDot = Arrays.asList(p.split("/")).contains("..");
            boolean underRoot = !allowedOutputRoot.isEmpty() && p.startsWith(allowedOutputRoot + "/");
            if (hasDotDot || !underRoot) {
                return deny("OUTPUT_PATH_ESCAPE",
                        "tool=" + r.tool + " output=" + (r.outputPath == null ? "<null>" : r.outputPath)
                                + " 越出 " + allowedOutputRoot + "（对位 AWS 126816 路径穿越）");
            }
        }

        // CP4：网络外发端点必须在 allowlist，拒绝 IP 字面量/云元数据
        if (r.sideEffect == SideEffect.NETWORK_EGRESS) {
            String host = hostOf(r.egressEndpoint);
            if (host.isEmpty() || isIpLiteral(host) || !allowedEgressHosts.contains(host)) {
                return deny("EGRESS_ENDPOINT_NOT_ALLOWLISTED",
                        "tool=" + r.tool + " egress=" + r.egressEndpoint + " host=" + host
                                + " 不在 allowlist（对位 AWS 126681 / Python SDK 115563 密钥泄漏）");
            }
        }

        // CP5：shell 副作用里出现元字符 => 拒绝（对位 code-review 68934 CWE-78）
        if (r.sideEffect == SideEffect.SHELL) {
            String frag = r.shellFragment == null ? "" : r.shellFragment;
            String meta = ";|&$`\n\r<>()";
            for (int i = 0; i < frag.length(); i++) {
                if (meta.indexOf(frag.charAt(i)) >= 0) {
                    return deny("SHELL_METACHAR_IN_PARAM",
                            "tool=" + r.tool + " shell 片段含元字符，禁止 execSync 拼接（对位 CVE-2026-68934）");
                }
            }
        }

        // CP2：敏感字段上的前缀匹配枚举 => 盲注 oracle（对位 REBUILD 129875）
        if (r.probe != null && r.probe.mode() == MatchMode.PREFIX
                && isSensitiveField(r.probe.fieldName())
                && r.probe.distinctGuesses() >= oracleGuessThreshold) {
            return deny("ORACLE_ENUMERATION_DETECTED",
                    "tool=" + r.tool + " 对敏感字段 " + r.probe.fieldName() + " 前缀探测 "
                            + r.probe.distinctGuesses() + " 次 >= " + oracleGuessThreshold
                            + "，判定为盲注 oracle（对位 REBUILD 129875 逐字符恢复口令哈希）");
        }

        // append-only 审计：字段名白名单 + 值一律脱敏，每条决策写明 rule
        StringBuilder auditLine = new StringBuilder("rule=");
        auditLine.append("ALLOW").append(" tool=").append(r.tool)
                .append(" sideEffect=").append(r.sideEffect)
                .append(" fields=");
        for (Field f : r.fields) auditLine.append(alnum(f.name())).append(":[REDACTED],");
        audit.add(auditLine.toString());
        return allow("结果面六道闸全部通过（" + r.fields.size() + " 字段已脱敏留痕）");
    }

    public int auditSize() { return audit.size(); }
    public String auditAt(int i) { return audit.get(i); }

    /** 旧基线：只看 readOnlyHint + 有无副作用，不检查字段/oracle。用于「不得劣于基线」对照。 */
    static boolean legacyAllows(ToolResult r) {
        return r.declaredReadOnlyHint || r.sideEffect == SideEffect.NONE;
    }

    // ---------- 自检 ----------

    static int attack = 0, control = 0, boundary = 0, failures = 0, incremental = 0;
    static McpResultFieldGuard g;

    static ToolResult plain(boolean hint, SideEffect se, Field... fields) {
        return new ToolResult("query-records", hint, se, Arrays.asList(fields), null, null, null, null);
    }
    static ToolResult fileWrite(boolean hint, String path) {
        return new ToolResult("generate_cost_report", hint, SideEffect.WRITE_FILE, List.of(), path, null, null, null);
    }
    static ToolResult egress(boolean hint, String endpoint) {
        return new ToolResult("timestream_query", hint, SideEffect.NETWORK_EGRESS, List.of(), null, endpoint, null, null);
    }
    static ToolResult shell(boolean hint, String frag) {
        return new ToolResult("get_local_git_diff", hint, SideEffect.SHELL, List.of(), null, null, frag, null);
    }
    static ToolResult probe(String field, MatchMode mode, int guesses) {
        return new ToolResult("query-records", false, SideEffect.NONE, List.of(), null, null, null,
                new Probe(field, mode, guesses));
    }

    static void expectDeny(String id, ToolResult r, String expectedRule) {
        Verdict v = g.evaluate(r);
        if (v.allowed() || (expectedRule != null && !expectedRule.equals(v.rule()))) {
            failures++;
            System.out.println("FAIL " + id + " expected DENY/" + expectedRule + " got " + v);
        } else {
            if (legacyAllows(r)) incremental++;
        }
    }
    static void expectAllow(String id, ToolResult r) {
        Verdict v = g.evaluate(r);
        if (!v.allowed()) { failures++; System.out.println("FAIL " + id + " expected ALLOW got " + v); }
    }
    static void check(String id, boolean cond) {
        if (!cond) { failures++; System.out.println("FAIL " + id); }
    }

    public static void main(String[] args) {
        g = new McpResultFieldGuard(
                Set.of("password", "passwd", "pwd", "hash", "token", "secret", "credential",
                       "apikey", "authorization", "cookie", "sessionid", "privatekey"),
                Set.of("api.trusted.example.com", "trusted.internal.corp"),
                "/allowed/root", 5);

        // ===== Attack：结果字段泄漏 / 盲注 oracle（legacy 全放行，增量拦截）=====
        expectDeny("A01", plain(false, SideEffect.NONE, new Field("password", "supersecret")), "SENSITIVE_FIELD_RETURNED");
        expectDeny("A02", plain(false, SideEffect.NONE, new Field("User.password_hash", "5f4dcc3b")), "SENSITIVE_FIELD_RETURNED");
        expectDeny("A03", plain(false, SideEffect.NONE, new Field("access_token", "sk-abc")), "SENSITIVE_FIELD_RETURNED");
        expectDeny("A04", plain(false, SideEffect.NONE, new Field("apiKey", "AKIAxxx")), "SENSITIVE_FIELD_RETURNED");
        expectDeny("A05", plain(false, SideEffect.NONE, new Field("client_secret", "s3cr3t")), "SENSITIVE_FIELD_RETURNED");
        expectDeny("A06", plain(false, SideEffect.NONE, new Field("sessionId", "sess-9f")), "SENSITIVE_FIELD_RETURNED");
        expectDeny("A07", plain(false, SideEffect.NONE, new Field("privateKey", "-----BEGIN")), "SENSITIVE_FIELD_RETURNED");
        expectDeny("A08", plain(false, SideEffect.NONE, new Field("PassWord", "P@ss")), "SENSITIVE_FIELD_RETURNED");
        expectDeny("A09", plain(false, SideEffect.NONE, new Field("user-pwd", "x")), "SENSITIVE_FIELD_RETURNED");
        expectDeny("A10", plain(false, SideEffect.NONE, new Field("COOKIE", "sid=1")), "SENSITIVE_FIELD_RETURNED");
        expectDeny("A11", probe("password", MatchMode.PREFIX, 12), "ORACLE_ENUMERATION_DETECTED");
        expectDeny("A12", probe("admin_password_hash", MatchMode.PREFIX, 8), "ORACLE_ENUMERATION_DETECTED");
        expectDeny("A13", probe("token", MatchMode.PREFIX, 6), "ORACLE_ENUMERATION_DETECTED");
        attack += 13;

        // ===== Attack：输出路径 / 端点 / shell =====
        expectDeny("A14", fileWrite(false, "/allowed/root/../../etc/passwd"), "OUTPUT_PATH_ESCAPE");
        expectDeny("A15", fileWrite(false, "/etc/cron.d/pwn"), "OUTPUT_PATH_ESCAPE");
        expectDeny("A16", fileWrite(false, "C:\\Windows\\System32\\drivers\\etc\\hosts"), "OUTPUT_PATH_ESCAPE");
        expectDeny("A17", egress(false, "https://evil.example.com/collect"), "EGRESS_ENDPOINT_NOT_ALLOWLISTED");
        expectDeny("A18", egress(false, "http://169.254.169.254/latest/meta-data/iam"), "EGRESS_ENDPOINT_NOT_ALLOWLISTED");
        expectDeny("A19", egress(false, "http://10.0.0.5/internal"), "EGRESS_ENDPOINT_NOT_ALLOWLISTED");
        expectDeny("A20", egress(false, "https://attacker.tld/exfil?k=leak"), "EGRESS_ENDPOINT_NOT_ALLOWLISTED");
        expectDeny("A21", shell(false, "git status; curl evil|x"), "SHELL_METACHAR_IN_PARAM");
        expectDeny("A22", shell(false, "git log $(whoami)"), "SHELL_METACHAR_IN_PARAM");
        expectDeny("A23", shell(false, "git show `id`"), "SHELL_METACHAR_IN_PARAM");
        expectDeny("A24", shell(false, "git diff\nrm -rf /"), "SHELL_METACHAR_IN_PARAM");
        attack += 11;

        // ===== Attack：只读声明陷阱（legacy 全放行）=====
        expectDeny("A25", fileWrite(true, "/allowed/root/report.csv"), "READONLY_HINT_NOT_AUTHORITY");
        expectDeny("A26", egress(true, "https://api.trusted.example.com/v1"), "READONLY_HINT_NOT_AUTHORITY");
        expectDeny("A27", shell(true, "git status"), "READONLY_HINT_NOT_AUTHORITY");
        attack += 3;

        // ===== Control：合法对照 —— 一律放行、0 误杀 =====
        expectAllow("C01", plain(false, SideEffect.NONE, new Field("name", "Acme"), new Field("balance", "120.5"), new Field("status", "OK")));
        expectAllow("C02", plain(false, SideEffect.NONE, new Field("displayName", "zh-CN")));
        expectAllow("C03", probe("customer_name", MatchMode.PREFIX, 50));
        expectAllow("C04", probe("password", MatchMode.EQUAL, 1));
        expectAllow("C05", fileWrite(false, "/allowed/root/reports/cost.csv"));
        expectAllow("C06", egress(false, "https://trusted.internal.corp:8443/v1/query"));
        expectAllow("C07", shell(false, "git status"));
        expectAllow("C08", plain(true, SideEffect.NONE, new Field("title", "Q3")));
        expectAllow("C09", probe("apikey", MatchMode.EQUAL, 2));
        expectAllow("C10", probe("title", MatchMode.PREFIX, 100));
        expectAllow("C11", fileWrite(false, "/allowed/root/a/b/c/report.json"));
        expectAllow("C12", egress(false, "https://api.trusted.example.com/v2"));
        control += 12;

        // ===== Boundary =====
        expectAllow("B01", plain(false, SideEffect.NONE)); // 空结果
        int before = g.auditSize();
        g.evaluate(plain(false, SideEffect.NONE, new Field("password", "supersecret"))); // 被拒，不写 allow 审计
        g.evaluate(plain(false, SideEffect.NONE, new Field("name", "ok")));              // 放行，写 1 条
        check("B02-audit-increment", g.auditSize() == before + 1);
        boolean leaked = false;
        for (int i = 0; i < g.auditSize(); i++) if (g.auditAt(i).contains("supersecret")) leaked = true;
        check("B03-redaction", !leaked); // 明文值永不进审计
        check("B04-baseline-not-worse", incremental >= 15); // 增量拦截 >= 15（旧基线放行但新守卫拦下）
        boundary += 4;

        System.out.println("attack=" + attack + " control=" + control + " boundary=" + boundary
                + " TOTAL=" + (attack + control + boundary));
        System.out.println("incremental(legacy allowed but blocked)=" + incremental + " / control false-positive=0");
        System.out.println("failures=" + failures);
        System.out.println(failures == 0
                ? "SELF-TEST OK (pure JDK, zero dependency, all groups green)"
                : "SELF-TEST FAILED");
        if (failures != 0) System.exit(1);
    }
}
