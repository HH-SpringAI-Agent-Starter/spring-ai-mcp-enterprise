import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * McpIdentityPropagationGuard — V1.75 参考实现（第七段：身份传播面）。
 *
 * 六段式收口（注解 → 注册中心 → 只读声明 → 决策层 → 配置面 → 结果面）之后，
 * 第七段管的是「被调用方实际收到谁的身份」：
 *   IBM ContextForge MCP Gateway CVE-2026-110278（CVSS 7.4，11-12）
 *   —— per-server 身份转发配置（user id / email / group / team）在 agent
 *      调用工具或读资源时被【忽略】，身份被转发给明确配置为「不接收」的
 *      上游 server；静态签名 payload 被捕获后可重放冒充用户（修 v1.1.1）。
 *   IBM CVE-2026-110279 —— 客户端 header 覆盖上游凭据，破坏按用户授权与审计归因。
 *   Impartner MCP（11-17）—— 「governed by existing permissions」+ 敏感字段不交给 AI。
 *
 * 判词：**声明「不接收身份」不是安全边界；必须在每次出站调用点强制裁剪 +
 * 校验签名新鲜度 + 拒绝 header 覆盖 + 绑定租户，且进程内 fail-closed。**
 *
 * 六道闸：
 *   CP1 declared-block 强制：目标 server 声明 block 的字段若出现在出站意图里 → 拦截（非静默放行）。
 *   CP2 字段最小化：只转发任务按需声明且 server allowlist 允许的字段，多余字段剥离。
 *   CP3 动态签名 + 新鲜度：拒绝空/静态签名；时间戳 5 分钟窗口；nonce 防重放；HMAC 校验。
 *   CP4 header 完整性：拒绝客户端试图覆盖上游凭据/身份的 header。
 *   CP5 租户绑定：身份 tenant 必须与目标 server tenant 一致。
 *   CP6 append-only 脱敏审计：只记字段数量与打码值，不记明文 email/group。
 *
 * 边界（诚实标注）：示例密钥硬编码仅为可运行演示，生产须走 KMS 轮换；
 * 状态全在进程内，多实例部署须换共享存储（Redis/DB）做 nonce 去重；
 * 身份字段判定为词法白名单非语义解析；本类未接入 21 个主模块。
 */
public final class McpIdentityPropagationGuard {

    // ---------- 数据模型 ----------
    public enum Code {
        OK,
        UNKNOWN_SERVER,
        EMPTY_IDENTITY,
        TENANT_MISMATCH,
        HEADER_OVERRIDE,
        STATIC_OR_MISSING_SIGNATURE,
        STALE_SIGNATURE,
        REPLAYED_NONCE,
        BAD_SIGNATURE,
        BLOCKED_FIELD_FORWARDED
    }

    public record Verdict(boolean allowed, Code code, String reason,
                          Map<String, String> forwarded, List<String> stripped) {
        public boolean isBlocked() { return !allowed; }
    }

    /** 目标 server 的身份接收策略。declaredBlockedFields = 声明「不接收」的字段。 */
    public static final class ServerPolicy {
        final String serverId;
        final String tenantId;
        final Set<String> allowedFields;
        final Set<String> declaredBlockedFields;

        public ServerPolicy(String serverId, String tenantId,
                            Set<String> allowedFields, Set<String> declaredBlockedFields) {
            this.serverId = serverId;
            this.tenantId = tenantId;
            this.allowedFields = norm(allowedFields);
            this.declaredBlockedFields = norm(declaredBlockedFields);
        }
    }

    /** 已认证上下文里的身份；signature 覆盖 userId|tenantId|timestampEpoch|nonce。 */
    public static final class IdentityContext {
        final String userId;
        final String email;
        final Set<String> groups;
        final String tenantId;
        final String signature;
        final String timestampEpoch;
        final String nonce;

        public IdentityContext(String userId, String email, Set<String> groups, String tenantId,
                               String signature, String timestampEpoch, String nonce) {
            this.userId = userId;
            this.email = email;
            this.groups = groups == null ? Collections.emptySet() : groups;
            this.tenantId = tenantId;
            this.signature = signature;
            this.timestampEpoch = timestampEpoch;
            this.nonce = nonce;
        }
    }

    /** 一次出站调用意图：任务需要哪些身份字段 + 客户端携带的 header。 */
    public static final class Invocation {
        final String serverId;
        final String taskId;
        final Set<String> requiredFields;
        final IdentityContext identity;
        final Map<String, String> clientHeaders;

        public Invocation(String serverId, String taskId, Set<String> requiredFields,
                          IdentityContext identity, Map<String, String> clientHeaders) {
            this.serverId = serverId;
            this.taskId = taskId;
            this.requiredFields = requiredFields == null ? Collections.emptySet() : requiredFields;
            this.identity = identity;
            this.clientHeaders = clientHeaders == null ? Collections.emptyMap() : clientHeaders;
        }
    }

    // ---------- 控制点常量 ----------
    private static final long FRESH_WINDOW_SECONDS = 300L; // CP3: 5 分钟
    private static final byte[] DEMO_KEY = "v1.75-demo-key-replace-with-kms".getBytes(StandardCharsets.UTF_8);
    private static final Set<String> FORBIDDEN_HEADERS = normOf(
            "authorization", "proxy-authorization", "x-api-key", "x-auth-token",
            "x-user-id", "x-user-email", "x-identity", "x-tenant-id", "x-forwarded-user");
    private static final Set<String> IDENTITY_FIELDS = normOf("userid", "email", "groups", "tenantid");

    private final Map<String, ServerPolicy> policies = new LinkedHashMap<>();
    private final Set<String> usedNonces = new HashSet<>();          // CP3 防重放（进程内）
    private final List<String> audit = new ArrayList<>();            // CP6 append-only

    public McpIdentityPropagationGuard() {}

    public void register(ServerPolicy policy) {
        policies.put(policy.serverId, policy);
    }

    /** 演示用 HMAC 签名器；生产应由受保护服务签发，密钥走 KMS。 */
    public static String sign(String userId, String tenantId, String timestampEpoch, String nonce) {
        String payload = userId + "|" + tenantId + "|" + timestampEpoch + "|" + nonce;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(DEMO_KEY, "HmacSHA256"));
            byte[] out = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : out) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------- 主决策 ----------
    public Verdict evaluate(Invocation inv) {
        ServerPolicy policy = policies.get(inv.serverId);
        if (policy == null) {
            return block(Code.UNKNOWN_SERVER, "server not registered: " + inv.serverId);
        }
        IdentityContext id = inv.identity;
        if (id == null || isBlank(id.userId)) {
            return block(Code.EMPTY_IDENTITY, "no authenticated identity context");
        }

        // CP5 租户绑定
        if (!eq(id.tenantId, policy.tenantId)) {
            return block(Code.TENANT_MISMATCH,
                    "identity tenant " + id.tenantId + " != server tenant " + policy.tenantId);
        }

        // CP4 header 完整性：客户端不得覆盖上游凭据/身份
        for (String key : inv.clientHeaders.keySet()) {
            if (FORBIDDEN_HEADERS.contains(norm1(key))) {
                return block(Code.HEADER_OVERRIDE, "client header attempts to override upstream identity: " + key);
            }
        }

        // CP3 动态签名 + 新鲜度 + 防重放
        if (isBlank(id.signature) || isBlank(id.timestampEpoch) || isBlank(id.nonce)
                || id.signature.toLowerCase(Locale.ROOT).startsWith("static")) {
            return block(Code.STATIC_OR_MISSING_SIGNATURE, "static/missing signature or freshness material");
        }
        long ts;
        try {
            ts = Long.parseLong(id.timestampEpoch);
        } catch (NumberFormatException e) {
            return block(Code.STALE_SIGNATURE, "non-numeric timestamp");
        }
        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - ts) > FRESH_WINDOW_SECONDS) {
            return block(Code.STALE_SIGNATURE, "signature outside freshness window");
        }
        String expected = sign(id.userId, id.tenantId, id.timestampEpoch, id.nonce);
        if (!constantEquals(expected, id.signature)) {
            return block(Code.BAD_SIGNATURE, "HMAC mismatch");
        }
        if (usedNonces.contains(id.nonce)) {
            return block(Code.REPLAYED_NONCE, "nonce already consumed");
        }

        // CP1 + CP2 逐字段：声明 block 的字段一旦被请求 → 拦截；非 allowlist 字段 → 剥离
        List<String> stripped = new ArrayList<>();
        Map<String, String> forwarded = new LinkedHashMap<>();
        Set<String> required = norm(inv.requiredFields);
        for (String field : IDENTITY_FIELDS) {
            boolean wanted = required.contains(field);
            if (!wanted) continue;
            if (policy.declaredBlockedFields.contains(field)) {
                return block(Code.BLOCKED_FIELD_FORWARDED,
                        "server declared block for identity field but invocation requested it: " + field);
            }
            if (!policy.allowedFields.contains(field)) {
                stripped.add(field); // CP2 最小化：不转发
                continue;
            }
            String value = switch (field) {
                case "userid" -> id.userId;
                case "email" -> id.email;
                case "groups" -> String.join(",", new java.util.TreeSet<>(id.groups));
                case "tenantid" -> id.tenantId;
                default -> null;
            };
            if (value != null && !value.isEmpty()) forwarded.put(field, value);
        }

        // 全绿才消费 nonce（CP3 防重放）并写审计（CP6）
        usedNonces.add(id.nonce);
        audit.add("ALLOW task=" + safe(inv.taskId) + " server=" + safe(inv.serverId)
                + " fields=" + forwarded.keySet()
                + " email=" + mask(id.email) + " groups=" + id.groups.size()
                + " stripped=" + stripped);
        return new Verdict(true, Code.OK, "allowed", forwarded, stripped);
    }

    /** 旧基线（仅信任 server 声明的 block 配置，不做新鲜度/header/租户/最小化校验）。 */
    public Verdict legacyBaseline(Invocation inv) {
        ServerPolicy policy = policies.get(inv.serverId);
        if (policy == null) return block(Code.UNKNOWN_SERVER, "server not registered");
        for (String field : norm(inv.requiredFields)) {
            if (policy.declaredBlockedFields.contains(field)) {
                return block(Code.BLOCKED_FIELD_FORWARDED, "declared block");
            }
        }
        return new Verdict(true, Code.OK, "legacy allowed", Collections.emptyMap(), Collections.emptyList());
    }

    public List<String> auditTrail() { return Collections.unmodifiableList(audit); }

    // ---------- helpers ----------
    private static Verdict block(Code code, String reason) {
        return new Verdict(false, code, reason, Collections.emptyMap(), Collections.emptyList());
    }
    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
    private static boolean eq(String a, String b) { return a != null && a.equals(b); }
    private static boolean constantEquals(String a, String b) {
        if (a == null || b == null) return false;
        if (a.length() != b.length()) return false;
        int d = 0;
        for (int i = 0; i < a.length(); i++) d |= a.charAt(i) ^ b.charAt(i);
        return d == 0;
    }
    private static String mask(String email) {
        if (isBlank(email)) return "<none>";
        int at = email.indexOf('@');
        if (at <= 1) return "*";
        return email.charAt(0) + "***" + email.substring(at);
    }
    private static String safe(String s) { return isBlank(s) ? "?" : s.replaceAll("[\\r\\n]", ""); }
    private static Set<String> norm(Set<String> s) {
        Set<String> out = new LinkedHashSet<>();
        if (s != null) for (String x : s) if (!isBlank(x)) out.add(norm1(x));
        return out;
    }
    private static Set<String> normOf(String... xs) {
        Set<String> out = new LinkedHashSet<>();
        for (String x : xs) out.add(norm1(x));
        return out;
    }
    private static String norm1(String s) { return s.trim().toLowerCase(Locale.ROOT).replace(" ", ""); }

    // ---------- 自检 ----------
    private static int attack, control, boundary, legacyOnly, falsePositive;

    private static void expectBlock(McpIdentityPropagationGuard g, String name,
                                    Invocation inv, Code expected) {
        Verdict v = g.evaluate(inv);
        if (v.allowed()) {
            System.out.println("FAIL(attack) " + name + " -> unexpectedly ALLOWED");
            throw new AssertionError(name);
        }
        if (v.code() != expected) {
            System.out.println("FAIL(attack) " + name + " -> " + v.code() + " expected " + expected);
            throw new AssertionError(name);
        }
        attack++;
    }

    private static void expectAllow(McpIdentityPropagationGuard g, String name, Invocation inv) {
        Verdict v = g.evaluate(inv);
        if (!v.allowed()) {
            System.out.println("FAIL(control) " + name + " -> blocked " + v.code() + ": " + v.reason());
            throw new AssertionError(name);
        }
        control++;
    }

    public static void main(String[] args) {
        McpIdentityPropagationGuard g = new McpIdentityPropagationGuard();
        g.register(new ServerPolicy("crm-server", "tenant-A",
                set("userId", "groups"), set("email")));          // 声明：不接收 email
        g.register(new ServerPolicy("report-server", "tenant-B",
                set("userId"), set("email", "groups")));
        g.register(new ServerPolicy("profile-server", "tenant-A",
                set("userId"), set())); // email 既未 block 也未 allow → CP2 应剥离

        String now = String.valueOf(Instant.now().getEpochSecond());
        String old = String.valueOf(Instant.now().getEpochSecond() - 901);

        IdentityContext goodA = new IdentityContext("u-1001", "alice@corp.com",
                set("sales", "admin"), "tenant-A", "", now, "");
        String sigA = sign(goodA.userId, goodA.tenantId, now, "nonce-001");
        IdentityContext a1 = new IdentityContext(goodA.userId, goodA.email, goodA.groups,
                goodA.tenantId, sigA, now, "nonce-001");

        // ===== 对照组：合法调用必须 0 误杀 =====
        expectAllow(g, "control: need-to-know userId+groups",
                new Invocation("crm-server", "t-list", set("userId", "groups"), a1, map()));
        Verdict vAllow = g.evaluate(new Invocation("crm-server", "t-again", set("userId", "groups"),
                signed(goodA, now, "nonce-002x"), map()));
        // (单独验证 allowlist 剥离：email 未 allow 但也未 requested → 不下发)
        if (!vAllow.forwarded().containsKey("userid") || vAllow.forwarded().containsKey("email")) {
            throw new AssertionError("control: forwarding minimization broken");
        }
        control++;

        // CP1 声明 block 仍被请求 → 拦截（旧基线也拦，不算增量）
        expectBlock(g, "CP1 email declared-block but requested",
                new Invocation("crm-server", "t1", set("email"),
                        withSig(goodA, "nonce-b1"), map()), Code.BLOCKED_FIELD_FORWARDED);

        // CP3 静态/缺失签名
        expectBlock(g, "CP3 static signature",
                new Invocation("crm-server", "t2", set("userId"),
                        new IdentityContext("u-1", "a@b.com", set("g"), "tenant-A", "static-token", now, "n2"),
                        map()), Code.STATIC_OR_MISSING_SIGNATURE);
        expectBlock(g, "CP3 missing freshness material",
                new Invocation("crm-server", "t3", set("userId"),
                        new IdentityContext("u-1", "a@b.com", set("g"), "tenant-A", "x", "", ""), map()),
                Code.STATIC_OR_MISSING_SIGNATURE);
        expectBlock(g, "CP3 stale timestamp",
                new Invocation("crm-server", "t4", set("userId"),
                        signed(goodA, old, "n4"), map()), Code.STALE_SIGNATURE);
        expectBlock(g, "CP3 HMAC mismatch",
                new Invocation("crm-server", "t5", set("userId"),
                        new IdentityContext("u-1", "a@b.com", set("g"), "tenant-A",
                                "deadbeef", now, "n5"), map()), Code.BAD_SIGNATURE);

        // CP4 header 覆盖
        expectBlock(g, "CP4 header override upstream credential",
                new Invocation("crm-server", "t6", set("userId"), withSig(goodA, "n6"),
                        map("X-User-Id", "attacker", "x-api-key", "stolen")), Code.HEADER_OVERRIDE);

        // CP5 租户
        expectBlock(g, "CP5 cross-tenant identity",
                new Invocation("report-server", "t7", set("userId"), withSig(goodA, "n7"), map()),
                Code.TENANT_MISMATCH);

        // 未注册 server
        expectBlock(g, "unknown server",
                new Invocation("ghost-server", "t8", set("userId"), withSig(goodA, "n8"), map()),
                Code.UNKNOWN_SERVER);

        // 空身份
        expectBlock(g, "empty identity",
                new Invocation("crm-server", "t9", set("userId"), null, map()), Code.EMPTY_IDENTITY);

        // nonce 重放（先成功一次，再重放）
        expectAllow(g, "control: first use of nonce-replay",
                new Invocation("crm-server", "t10", set("userId"), withSig(goodA, "n10"), map()));
        expectBlock(g, "CP3 replayed nonce",
                new Invocation("crm-server", "t11", set("userId"), withSig(goodA, "n10"), map()),
                Code.REPLAYED_NONCE);

        // ===== 不得劣于旧基线：旧基线放行的攻击，新守卫必须拦下 =====
        Invocation[] legacyMisses = {
                new Invocation("crm-server", "L1", set("userId"),
                        new IdentityContext("u-1", "a@b.com", set("g"), "tenant-A", "static-token", now, "l1"), map()),
                new Invocation("crm-server", "L2", set("userId"), withSig(goodA, "l2"),
                        map("X-Identity", "attacker")),
                new Invocation("report-server", "L3", set("userId"), withSig(goodA, "l3"), map())
        };
        for (Invocation inv : legacyMisses) {
            Verdict lg = g.legacyBaseline(inv);
            Verdict nv = g.evaluate(inv);
            if (lg.allowed() && !nv.allowed()) legacyOnly++;
            else throw new AssertionError("legacy-baseline assertion broken: " + inv.taskId);
        }

        // CP2 最小化：请求了 allowlist 之外的字段 → 放行但该字段被剥离
        Verdict stripped = g.evaluate(new Invocation("profile-server", "t12",
                set("userId", "email"), withSig(goodA, "n12"), map()));
        if (!stripped.allowed() || !stripped.forwarded().containsKey("userid")
                || stripped.forwarded().containsKey("email")
                || !stripped.stripped().contains("email")) {
            throw new AssertionError("CP2 field-mimination strip broken");
        }
        boundary++;

        // 边界：审计脱敏
        boundary++;
        String trail = g.auditTrail().toString();
        if (trail.contains("alice@corp.com") || !trail.contains("a***@corp.com")) {
            throw new AssertionError("CP6 audit must mask email plaintext");
        }

        System.out.println("self-test OK: " + attack + " attack + " + control + " control + "
                + boundary + " boundary assertions");
        System.out.println("  legacy-only blocks (new guard catches, baseline misses): " + legacyOnly);
        System.out.println("  false positives on legal calls: " + falsePositive);
        System.out.println("  audit entries: " + g.auditTrail().size());
    }

    private static IdentityContext withSig(IdentityContext base, String nonce) {
        String ts = base.timestampEpoch;
        if (isBlank(ts)) ts = String.valueOf(Instant.now().getEpochSecond());
        return new IdentityContext(base.userId, base.email, base.groups, base.tenantId,
                sign(base.userId, base.tenantId, ts, nonce), ts, nonce);
    }

    private static IdentityContext signed(IdentityContext base, String ts, String nonce) {
        return new IdentityContext(base.userId, base.email, base.groups, base.tenantId,
                sign(base.userId, base.tenantId, ts, nonce), ts, nonce);
    }

    private static Set<String> set(String... xs) { return new LinkedHashSet<>(Arrays.asList(xs)); }
    private static Map<String, String> map() { return new LinkedHashMap<>(); }
    private static Map<String, String> map(String k, String v) {
        Map<String, String> m = new LinkedHashMap<>(); m.put(k, v); return m;
    }
    private static Map<String, String> map(String k1, String v1, String k2, String v2) {
        Map<String, String> m = map(k1, v1); m.put(k2, v2); return m;
    }
}
