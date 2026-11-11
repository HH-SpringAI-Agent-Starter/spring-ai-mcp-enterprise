package com.example.mcp.registry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * MCP 工具「注册期注解完整性 + 发现期审批门」参考实现（V1.69）。
 *
 * <p>状态：REFERENCE ONLY，纯 JDK 零依赖，不进 Maven reactor，零构建风险。
 *
 * <p>核心立场（2026-11-09~11-11 四股力量共同指向）：
 * <b>annotations 是 hints，不是 authorization</b>。本类把服务端声明的
 * readOnlyHint / destructiveHint / openWorldHint 只当作「待审材料」，
 * 风险分级由服务端独立计算；放行判断永远发生在服务端审批门，而不是客户端
 * 或模型对 hint 的解读。
 *
 * <p>对位实证：
 * <ul>
 *   <li>INITE Atlas 2026-11-11 全量普查：11,393 servers 中 25% 工具缺
 *       readOnly/destructive hint、4% 标非破坏性却删除/发送/支付、23% 免登录
 *       仍会改数据、47% 参数无描述。</li>
 *   <li>OpenAI / Anthropic 目录上架规则：三个 hint 必须显式 boolean；
 *       read-only 免确认、destructive 必弹窗；禁 catch-all 工具。</li>
 *   <li>Microsoft Agent Framework Python 1.25.0（2026-11-09 发布）：
 *       callable MCP discovery approval policies；11-11 治理指南明确
 *       「metadata should be verified rather than blindly trusted」、
 *       「read-only does not always mean harmless」、catalogue 刷新后须复验审批。</li>
 *   <li>Google Cloud Agent Registry 文档（更新 2026-11-10）：
 *       中央注册表 + Agent Gateway 逐工具 IAM 强制执行。</li>
 *   <li>CVE-2026-123812：写工具漏审批门 + 缺 destructiveHint；同 server 的
 *       send_event 有审批门挡住同一条攻击链。</li>
 * </ul>
 *
 * <p>自检运行：{@code java -Dfile.encoding=UTF-8 com.example.mcp.registry.McpToolAnnotationGate}
 */
public final class McpToolAnnotationGate {

    /** 服务端独立计算的风险档位，与调用方声明的 hint 解耦。 */
    public enum Tier { READ, WRITE, DESTRUCTIVE }

    /** 调用结果。 */
    public static final class Verdict {
        public final boolean allowed;
        public final String reasonCode;
        public final Tier tier;
        public final boolean approvalRequired;

        Verdict(boolean allowed, String reasonCode, Tier tier, boolean approvalRequired) {
            this.allowed = allowed;
            this.reasonCode = reasonCode;
            this.tier = tier;
            this.approvalRequired = approvalRequired;
        }

        static Verdict allow(String code, Tier tier) { return new Verdict(true, code, tier, false); }
        static Verdict deny(String code, Tier tier) { return new Verdict(false, code, tier, true); }

        @Override public String toString() {
            return (allowed ? "ALLOW" : "DENY") + "(" + reasonCode + "," + tier + ")";
        }
    }

    /** 工具声明（来自 server 的 tools/list，一律视为不可信输入）。 */
    public static final class ToolSpec {
        public final String name;
        public final String description;
        public final List<String> inputFields;
        /** Boolean（可为 null）= 缺 hint；刻意不用 boolean 基本类型。 */
        public final Boolean readOnlyHint;
        public final Boolean destructiveHint;
        public final Boolean openWorldHint;
        public final String dataClassification;

        public ToolSpec(String name, String description, List<String> inputFields,
                        Boolean readOnlyHint, Boolean destructiveHint, Boolean openWorldHint,
                        String dataClassification) {
            this.name = Objects.requireNonNull(name);
            this.description = description == null ? "" : description;
            this.inputFields = inputFields == null ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(inputFields));
            this.readOnlyHint = readOnlyHint;
            this.destructiveHint = destructiveHint;
            this.openWorldHint = openWorldHint;
            this.dataClassification = dataClassification == null ? "internal" : dataClassification;
        }

        /** 模拟 catalogue 刷新：返回仅描述被改写的新声明。 */
        public ToolSpec withDescription(String newDescription) {
            return new ToolSpec(name, newDescription, inputFields, readOnlyHint,
                    destructiveHint, openWorldHint, dataClassification);
        }
    }

    /** 注册期校验结果。 */
    public static final class Registration {
        public final boolean accepted;
        public final String reasonCode;
        public final Tier tier;
        public final String fingerprint;

        Registration(boolean accepted, String reasonCode, Tier tier, String fingerprint) {
            this.accepted = accepted;
            this.reasonCode = reasonCode;
            this.tier = tier;
            this.fingerprint = fingerprint;
        }
    }

    /** 调用上下文：subject 必须是服务端认证后的身份，不接受模型自称。 */
    public static final class CallContext {
        public final String subject;            // 认证后身份；null = 未认证
        public final String claimedRole;       // 模型自称，永不作为授权依据
        public final String approvalId;        // 人工审批流水号
        public final boolean override;
        public final String overrideReason;
        public final String environment;
        public final String arguments;

        public CallContext(String subject, String claimedRole, String approvalId,
                           boolean override, String overrideReason,
                           String environment, String arguments) {
            this.subject = subject;
            this.claimedRole = claimedRole;
            this.approvalId = approvalId;
            this.override = override;
            this.overrideReason = overrideReason;
            this.environment = environment == null ? "dev" : environment;
            this.arguments = arguments == null ? "" : arguments;
        }

        public static CallContext read(String subject) {
            return new CallContext(subject, null, null, false, null, "dev", "");
        }
    }

    /** 可注入的人工审批器（对应 MS Agent Framework 的 callable approval policy）。 */
    @FunctionalInterface
    public interface Approver {
        /** 返回 true = 人工批准；抛异常 / 返回 false = 拒绝。 */
        boolean approve(ToolSpec tool, Tier tier, CallContext ctx);
    }

    private static final class Registered {
        final ToolSpec spec;
        final Tier tier;
        final String fingerprint;
        Registered(ToolSpec spec, Tier tier, String fingerprint) {
            this.spec = spec; this.tier = tier; this.fingerprint = fingerprint;
        }
    }

    /** 副作用动词：中英文 + 大小写归一，用于独立风险分级与「谎报只读」检测。 */
    private static final Pattern SIDE_EFFECT = Pattern.compile(
            "(?i)(delete|remove|drop|truncate|purge|destroy|erase|overwrite|" +
            "send|post|publish|submit|transfer|pay|refund|charge|withdraw|" +
            "create|insert|update|modify|write|append|execute|run|invoke|" +
            "grant|revoke|disable|deploy|restart|shutdown|" +
            "删除|修改|写入|发送|支付|转账|创建|执行|撤销|禁用)");

    private static final Pattern DESTRUCTIVE = Pattern.compile(
            "(?i)(delete|remove|drop|truncate|purge|destroy|erase|" +
            "pay|transfer|refund|withdraw|revoke|shutdown|删除|支付|转账)");

    private static final Pattern CATCH_ALL_NAME = Pattern.compile(
            "(?i)^.*(api_request|generic_call|raw_request|proxy|everything|exec_any).*$");

    private final java.util.Map<String, Registered> registry = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, String> approvals = new java.util.LinkedHashMap<>();
    private final List<String> auditLog = new ArrayList<>();
    private final java.util.Set<String> bannedTools = new LinkedHashSet<>();
    private Approver approver;

    public McpToolAnnotationGate() {
        // 对位 @modelcontextprotocol/server-everything 的 get-env：整包环境变量读取工具默认禁用
        bannedTools.add("get_env");
        bannedTools.add("get-env");
    }

    public void setApprover(Approver a) { this.approver = a; }

    public List<String> auditSnapshot() { return Collections.unmodifiableList(new ArrayList<>(auditLog)); }

    // ---------------------------------------------------------------- 注册期

    /** 缺 hint 时按最坏情况推断（fail-closed），供旧基线对照与边界断言使用。 */
    public static Tier assumeWorstCase(ToolSpec t) {
        if (t.readOnlyHint == null || t.destructiveHint == null || t.openWorldHint == null) {
            return Tier.DESTRUCTIVE;
        }
        return classify(t);
    }

    /** 服务端独立定级：只看工具真实语义线索，不采信声明的 hint 作为放行依据。 */
    public static Tier classify(ToolSpec t) {
        String haystack = t.name + " " + t.description + " " + String.join(" ", t.inputFields);
        boolean destructive = DESTRUCTIVE.matcher(haystack).find();
        boolean sideEffect = SIDE_EFFECT.matcher(haystack).find();
        boolean openWorld = Boolean.TRUE.equals(t.openWorldHint);
        boolean sensitive = !"public".equalsIgnoreCase(t.dataClassification);
        if (destructive) return Tier.DESTRUCTIVE;
        if (sideEffect || openWorld) return Tier.WRITE;
        // read-only 也可能读敏感数据：仍不放行写操作，但保持 READ 档
        return Tier.READ;
    }

    /**
     * 注册期准入：六条硬校验，任何一条不通过都不予注册。
     * 通过者落 fingerprint，供运行期做元数据漂移复验。
     */
    public Registration register(ToolSpec t) {
        // 1) 三个 hint 必须显式 boolean（OpenAI 上架要求 / spec 默认 unset）
        if (t.readOnlyHint == null || t.destructiveHint == null || t.openWorldHint == null) {
            return reject("MISSING_EXPLICIT_HINTS", t);
        }
        // 2) 自相矛盾
        if (t.readOnlyHint && t.destructiveHint) {
            return reject("CONTRADICTORY_HINTS", t);
        }
        String haystack = (t.name + " " + t.description + " " + String.join(" ", t.inputFields))
                .toLowerCase(Locale.ROOT);
        boolean sideEffect = SIDE_EFFECT.matcher(haystack).find();
        // 3) 谎报只读：描述/名称/参数含副作用动词却声明 readOnlyHint=true
        if (t.readOnlyHint && sideEffect) {
            return reject("READONLY_LIE", t);
        }
        // 4) openWorld 却声明只读（对位 INITE「标非破坏性却发送/支付」）
        if (t.readOnlyHint && t.openWorldHint) {
            return reject("OPENWORLD_LIE", t);
        }
        // 5) catch-all 工具拒收（Anthropic 目录硬性拒绝）
        if (CATCH_ALL_NAME.matcher(t.name).find()
                || t.inputFields.stream().anyMatch(f ->
                        f.equalsIgnoreCase("method") || f.equalsIgnoreCase("action")
                                || f.equalsIgnoreCase("operation")) && sideEffect) {
            return reject("CATCH_ALL_TOOL", t);
        }
        // 6) 整包环境变量读取工具默认禁用
        if (bannedTools.contains(t.name.toLowerCase(Locale.ROOT))) {
            return reject("BANNED_ENV_DUMP", t);
        }

        Tier tier = classify(t);
        String fp = fingerprint(t);
        registry.put(t.name, new Registered(t, tier, fp));
        audit("REGISTER", t.name, tier, "ACCEPT", "fingerprint=" + fp.substring(0, 12));
        return new Registration(true, "ACCEPTED", tier, fp);
    }

    private Registration reject(String code, ToolSpec t) {
        audit("REGISTER", t.name, assumeWorstCase(t), "REJECT", code);
        return new Registration(false, code, assumeWorstCase(t), null);
    }

    /** catalogue 刷新：重新计算 fingerprint，任何变更都使既有审批作废。 */
    public boolean refreshCatalogue(ToolSpec updated) {
        Registered old = registry.get(updated.name);
        if (old == null) return false;
        String fp = fingerprint(updated);
        boolean changed = !fp.equals(old.fingerprint);
        registry.put(updated.name, new Registered(updated, classify(updated), fp));
        if (changed) {
            approvals.remove(updated.name);
            audit("CATALOGUE_REFRESH", updated.name, classify(updated), "APPROVAL_REVOKED",
                    "metadata changed, re-approval required");
        }
        return changed;
    }

    // ---------------------------------------------------------------- 运行期

    /**
     * 调用期闸门。注解不参与放行判断：READ 直接放行（仍审计），
     * WRITE / DESTRUCTIVE 必须经服务端人工审批，任何异常路径 fail-closed。
     */
    public Verdict call(String toolName, CallContext ctx) {
        Registered reg = registry.get(toolName);
        if (reg == null) {
            audit("CALL", toolName, Tier.DESTRUCTIVE, "DENY", "UNREGISTERED_TOOL");
            return Verdict.deny("UNREGISTERED_TOOL", Tier.DESTRUCTIVE);
        }
        Tier tier = reg.tier;

        // 身份不接受模型自称
        if (ctx.subject == null || ctx.subject.isBlank()) {
            audit("CALL", toolName, tier, "DENY", "NO_AUTHENTICATED_SUBJECT");
            return Verdict.deny("NO_AUTHENTICATED_SUBJECT", tier);
        }

        // 元数据漂移复验：fingerprint 变了，旧审批一律作废
        if (!fingerprint(reg.spec).equals(reg.fingerprint)) {
            approvals.remove(toolName);
            audit("CALL", toolName, tier, "DENY", "STALE_APPROVAL");
            return Verdict.deny("STALE_APPROVAL", tier);
        }

        if (tier == Tier.READ && !ctx.override) {
            audit("CALL", toolName, tier, "ALLOW", "READ_AUTO_APPROVED subject=" + ctx.subject
                    + " args=" + redact(ctx.arguments));
            return Verdict.allow("READ_AUTO_APPROVED", tier);
        }

        // DESTRUCTIVE 不允许 override 绕过（override 只能用于 WRITE 且必须留痕）
        if (ctx.override && tier == Tier.DESTRUCTIVE) {
            audit("CALL", toolName, tier, "DENY", "OVERRIDE_FORBIDDEN_FOR_DESTRUCTIVE");
            return Verdict.deny("OVERRIDE_FORBIDDEN_FOR_DESTRUCTIVE", tier);
        }
        if (ctx.override && (ctx.overrideReason == null || ctx.overrideReason.isBlank())) {
            audit("CALL", toolName, tier, "DENY", "OVERRIDE_WITHOUT_REASON");
            return Verdict.deny("OVERRIDE_WITHOUT_REASON", tier);
        }

        String approvalKey = toolName + "|" + reg.fingerprint + "|" + ctx.subject;
        if (ctx.approvalId == null || ctx.approvalId.isBlank()
                || !ctx.approvalId.equals(approvals.get(approvalKey))) {
            audit("CALL", toolName, tier, "DENY", "NO_VALID_APPROVAL");
            return Verdict.deny("NO_VALID_APPROVAL", tier);
        }

        // 审批器 fail-closed：缺失 / 抛异常 / 返回 false 一律拒绝
        if (approver == null) {
            audit("CALL", toolName, tier, "DENY", "NO_APPROVER_FAIL_CLOSED");
            return Verdict.deny("NO_APPROVER_FAIL_CLOSED", tier);
        }
        boolean approved;
        try {
            approved = approver.approve(reg.spec, tier, ctx);
        } catch (RuntimeException ex) {
            audit("CALL", toolName, tier, "DENY", "APPROVER_ERROR_FAIL_CLOSED");
            return Verdict.deny("APPROVER_ERROR_FAIL_CLOSED", tier);
        }
        if (!approved) {
            audit("CALL", toolName, tier, "DENY", "APPROVER_REJECTED");
            return Verdict.deny("APPROVER_REJECTED", tier);
        }

        String outcome = ctx.override
                ? "ALLOW_WITH_OVERRIDE who=" + ctx.subject + " bypassed=WRITE_APPROVAL_POLICY reason="
                        + ctx.overrideReason
                : "ALLOW subject=" + ctx.subject;
        audit("CALL", toolName, tier, "ALLOW", outcome + " args=" + redact(ctx.arguments));
        return Verdict.allow(ctx.override ? "ALLOW_WITH_OVERRIDE" : "ALLOW_APPROVED", tier);
    }

    /** 人工审批通过后登记流水号，绑定 fingerprint（元数据一变即失效）。 */
    public String grantApproval(String toolName, String subject) {
        Registered reg = registry.get(toolName);
        if (reg == null) throw new IllegalArgumentException("unregistered tool: " + toolName);
        String key = toolName + "|" + reg.fingerprint + "|" + subject;
        String id = "APR-" + Integer.toHexString((key + System.nanoTime()).hashCode());
        approvals.put(key, id);
        audit("APPROVAL", toolName, reg.tier, "GRANTED", "id=" + id + " subject=" + subject);
        return id;
    }

    // ---------------------------------------------------------------- 工具方法

    private static String fingerprint(ToolSpec t) {
        String canonical = String.join("\u001f",
                t.name, t.description, String.join(",", t.inputFields),
                String.valueOf(t.readOnlyHint), String.valueOf(t.destructiveHint),
                String.valueOf(t.openWorldHint), t.dataClassification);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 审计脱敏：密钥字段名对应的值一律打码（对位 gatehouse / GDPR 只存字段名）。 */
    public static String redact(String arguments) {
        if (arguments == null || arguments.isEmpty()) return "{}";
        // 一次性替换「字段名 + 分隔符 + 值」，避免只替换字段名却把明文留在串里
        String s = arguments.replaceAll(
                "(?i)(\"?(?:api[_-]?key|password|passwd|secret|token|credential|authorization)" +
                        "\"?\\s*[:=]\\s*)(\"[^\"]*\"|'[^']*'|[^,}\\s]+)",
                "$1\"***REDACTED***\"");
        return s.length() > 220 ? s.substring(0, 220) + "...(truncated)" : s;
    }

    private void audit(String phase, String tool, Tier tier, String outcome, String detail) {
        auditLog.add(phase + "|" + tool + "|" + tier + "|" + outcome + "|" + detail);
    }

    // ---------------------------------------------------------------- 自检

    private static int attack = 0, control = 0, boundary = 0, checks = 0;

    private static void expectDeny(String label, Verdict v) {
        checks++;
        if (v.allowed) throw new AssertionError(label + " expected DENY but got " + v);
        attack++;
    }

    private static void expectReject(String label, Registration r) {
        checks++;
        if (r.accepted) throw new AssertionError(label + " expected REJECT but got ACCEPT");
        attack++;
    }

    private static void expectAllow(String label, Verdict v) {
        checks++;
        if (!v.allowed) throw new AssertionError(label + " expected ALLOW but got " + v);
        control++;
    }

    private static void expectAccept(String label, Registration r) {
        checks++;
        if (!r.accepted) throw new AssertionError(label + " expected ACCEPT but got " + r.reasonCode);
        control++;
    }

    private static void expectTrue(String label, boolean b) {
        checks++;
        if (!b) throw new AssertionError(label + " expected true");
        boundary++;
    }

    /** 对照组断言：新方案不得劣于既有基线（历史工程约定）。 */
    private static void expectControl(String label, boolean b) {
        checks++;
        if (!b) throw new AssertionError(label + " expected true (control group)");
        control++;
    }

    public static void main(String[] args) {
        // ---------- 攻击用例：注册期谎报 / 缺 hint / catch-all ----------
        McpToolAnnotationGate g = new McpToolAnnotationGate();
        expectReject("A1 readOnly 谎报删除", g.register(new ToolSpec("delete_customer",
                "Permanently delete a customer record", Arrays.asList("customer_id"),
                true, false, false, "internal")));
        expectReject("A2 缺显式 hint", g.register(new ToolSpec("list_orders",
                "List orders", Arrays.asList("limit"), null, false, false, "internal")));
        expectReject("A3 hint 自相矛盾", g.register(new ToolSpec("wipe_table",
                "Drop and recreate table", Arrays.asList("table"), true, true, false, "internal")));
        expectReject("A4 catch-all api_request", g.register(new ToolSpec("api_request",
                "Call any HTTP method", Arrays.asList("method", "url", "body"),
                false, true, true, "internal")));
        expectReject("A5 openWorld 谎报只读", g.register(new ToolSpec("fetch_invoice",
                "Read remote invoice", Arrays.asList("url"), true, false, true, "internal")));
        expectReject("A6 中文写动词谎报只读", g.register(new ToolSpec("query_user",
                "查询并删除用户", Arrays.asList("id"), true, false, false, "internal")));
        expectReject("A7 整包环境变量读取", g.register(new ToolSpec("get_env",
                "Return all process env vars", Collections.emptyList(),
                true, false, false, "internal")));

        // ---------- 对照用例：合法工具不得误杀（不得劣于旧基线） ----------
        ToolSpec readTool = new ToolSpec("search_docs", "Search documentation by keyword",
                Arrays.asList("query", "limit"), true, false, false, "public");
        expectAccept("C1 只读工具注册", g.register(readTool));
        expectAllow("C2 只读调用自动放行", g.call("search_docs", CallContext.read("alice")));

        ToolSpec writeTool = new ToolSpec("create_ticket", "Create a support ticket",
                Arrays.asList("title", "body"), false, false, false, "internal");
        expectAccept("C3 写工具注册", g.register(writeTool));
        expectControl("C4 写工具被独立定级为 WRITE", classify(writeTool) == Tier.WRITE);

        // 「只查工具名黑名单」的旧基线：合法只读应放行、删除类应拦；新方案结论不得劣于基线
        expectControl("C5a 旧基线放行的只读新方案也放行", legacyAllows("search_docs")
                && g.call("search_docs", CallContext.read("alice")).allowed);
        Registration honestDelete = g.register(new ToolSpec("delete_customer", "Delete record",
                Collections.emptyList(), false, true, false, "internal"));
        expectControl("C5b 旧基线会拦的删除：新方案允许诚实注册但定级 DESTRUCTIVE 且调用仍需审批",
                !legacyAllows("delete_customer") && honestDelete.accepted
                        && honestDelete.tier == Tier.DESTRUCTIVE
                        && !g.call("delete_customer", CallContext.read("alice")).allowed);

        // ---------- 攻击用例：运行期审批门 fail-closed ----------
        expectDeny("A8 未注册工具调用", g.call("ghost_tool", CallContext.read("alice")));
        expectDeny("A9 模型自称身份无真实 subject", g.call("create_ticket",
                new CallContext(null, "admin", null, false, null, "prod", "")));
        expectDeny("A10 写工具无审批流水号", g.call("create_ticket", CallContext.read("alice")));

        g.setApprover((tool, tier, ctx) -> true);
        expectDeny("A11 伪造审批流水号", g.call("create_ticket",
                new CallContext("alice", null, "APR-FORGED", false, null, "prod", "")));

        String apr = g.grantApproval("create_ticket", "alice");
        expectAllow("C6 合法审批后写工具放行", g.call("create_ticket",
                new CallContext("alice", null, apr, false, null, "prod", "title=hello")));

        expectDeny("A12 override 无理由", g.call("create_ticket",
                new CallContext("alice", null, apr, true, "", "prod", "")));
        String aprDel = g.grantApproval("delete_customer", "alice");
        expectDeny("A13 DESTRUCTIVE 禁止 override", g.call("delete_customer",
                new CallContext("alice", null, aprDel, true, "urgent", "prod", "")));

        // 审批器异常 / 返回 false / 缺失 → 全部 fail-closed
        McpToolAnnotationGate g2 = new McpToolAnnotationGate();
        g2.register(writeTool);
        String apr2 = g2.grantApproval("create_ticket", "bob");
        g2.setApprover((tool, tier, ctx) -> { throw new IllegalStateException("approver down"); });
        expectDeny("A14 审批器抛异常 fail-closed", g2.call("create_ticket",
                new CallContext("bob", null, apr2, false, null, "prod", "")));
        g2.setApprover((tool, tier, ctx) -> false);
        expectDeny("A15 审批器显式拒绝", g2.call("create_ticket",
                new CallContext("bob", null, apr2, false, null, "prod", "")));
        g2.setApprover(null);
        expectDeny("A16 无审批器 fail-closed", g2.call("create_ticket",
                new CallContext("bob", null, apr2, false, null, "prod", "")));

        // ---------- 元数据漂移：catalogue 刷新后旧审批必须作废 ----------
        ToolSpec drifted = writeTool.withDescription("Create a ticket and send it to external webhook");
        expectTrue("B1 catalogue 变更被检出", g.refreshCatalogue(drifted));
        expectDeny("A17 描述改写后旧审批失效", g.call("create_ticket",
                new CallContext("alice", null, apr, false, null, "prod", "")));
        expectControl("C7 复验后新描述独立升级为 WRITE/DESTRUCTIVE",
                classify(drifted) != Tier.READ);

        // ---------- 审计脱敏与 append-only ----------
        String leak = "{\"api_key\":\"sk-live-ABC123\",\"password\":\"hunter2\",\"title\":\"ok\"}";
        String red = redact(leak);
        expectTrue("B2 审计不得出现 api_key 明文", !red.contains("sk-live-ABC123"));
        expectTrue("B3 审计不得出现 password 明文", !red.contains("hunter2"));
        int before = g.auditSnapshot().size();
        g.call("create_ticket", new CallContext("alice", null, "APR-BAD", false, null, "prod", leak));
        expectControl("C8 拒绝分支也必须留痕（append-only）", g.auditSnapshot().size() == before + 1);

        // ---------- 边界断言 ----------
        expectTrue("B4 缺 hint 一律按最坏档 DESTRUCTIVE", assumeWorstCase(
                new ToolSpec("x", "x", Collections.emptyList(), null, null, null, "internal"))
                == Tier.DESTRUCTIVE);
        expectTrue("B5 独立定级不采信谎报 hint：删除即 DESTRUCTIVE", classify(
                new ToolSpec("delete_customer", "Delete record", Collections.emptyList(),
                        true, false, false, "internal")) == Tier.DESTRUCTIVE);
        expectTrue("B6 审计日志为不可变快照", isImmutableSnapshot(g.auditSnapshot()));

        System.out.println("self-test OK (" + attack + " attack cases + " + control
                + " control cases + " + boundary + " boundary assertions)");
        System.out.println("total assertions: " + checks);
        System.out.println("tier classification is INDEPENDENT of declared hints; "
                + "annotations are inputs to policy, never the authorization decision.");
    }

    /** 旧基线：只按工具名黑名单拦删除类，其余一律放行。用于「不得劣于基线」对照。 */
    private static boolean legacyAllows(String name) {
        Set<String> blocked = new LinkedHashSet<>(Arrays.asList("delete_customer", "drop_table"));
        return !blocked.contains(name);
    }

    private static boolean isImmutableSnapshot(List<String> snapshot) {
        try {
            snapshot.add("tamper");
            return false;
        } catch (UnsupportedOperationException expected) {
            return true;
        }
    }
}
