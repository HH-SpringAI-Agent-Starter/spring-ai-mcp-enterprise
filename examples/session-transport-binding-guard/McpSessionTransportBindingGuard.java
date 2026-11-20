import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * McpSessionTransportBindingGuard — V1.76 参考实现（第八段：会话/传输绑定面）。
 *
 * 七段式收口（注解 → 注册中心 → 只读声明 → 决策层 → 配置面 → 结果面 → 身份传播面）
 * 之后，第八段管的是「这条调用是从哪个会话、哪个 Origin、哪个 Host 进来的」：
 *   Supergateway CVE-2026-132121（2.4.0–4.2.2）stdio-to-WebSocket 无 Origin 校验，
 *     恶意网页跨站 WebSocket 劫持本机 MCP 网关，PoC 2026-11-17 12/12 runs 外传本地文件；
 *     兄弟 flaw CVE-2026-131857 未认证者用 prototype-inherited sessionId 打崩网关。
 *   k8s-mcp-server CVE-2026-130007（11-19 NVD）SSE 默认 0.0.0.0:8080 无 auth，
 *     sessionId 仅为 correlation value、POST /message 无 authorization/Origin 校验即派发工具，
 *     read-only 默认关闭 → 写工具以服务端集群权限运行（修 1.1.0）。
 *   Starlette CVE-2026-48710 BadHost —— Host 注入问号使 URL 重解析边界漂移，
 *     /admin 被当成 query，鉴权中间件读到路径 / 而放行；MCP 规范强制的免鉴权 OAuth
 *     discovery 端点使其成为可靠利用路径（修 1.0.1）。
 *   MCP Python SDK CVE-2026-115563 —— OAuth client 把凭据发给 server 自选的 AS，
 *     issuer 未逐 discovery path 校验、凭据未绑定 AS；修 2.2.0/1.30.0 且必须显式传 issuer=。
 *
 * 判词：**sessionId 不是安全边界；Host / Origin 头不是装饰；OAuth 凭据必须绑定 issuer。
 * 必须在每次调用点强制校验会话主体绑定、Origin 白名单与会话一致、Host RFC 合法性、
 * 凭据 issuer 绑定，默认只读且 fail-closed。**
 *
 * 六道闸：
 *   CP1 会话绑定：仅接受服务端签发（HMAC）且绑定 principal+origin 的 sessionId；
 *       拒绝伪造签名、未注册、prototype-inherited 形态的 sessionId。
 *   CP2 Origin：WebSocket 升级 / SSE 连接必须有 Origin、须在白名单、须与会话绑定 Origin 一致。
 *   CP3 Host：按 RFC 9112/3986 拒绝含斜杠/问号/井号/@/空白/控制字符的 Host（BadHost）。
 *   CP4 issuer 绑定：携带 OAuth 凭据时必须声明 issuer 且等于预期；未绑定/不匹配一律拒绝。
 *   CP5 默认只读 + 未认证监听拒绝：只读会话禁止写工具；无认证主体不得进入工具派发。
 *   CP6 append-only 脱敏审计：sessionId 打码、不记原始凭据、记录规范化 Host/Origin。
 *
 * 边界（诚实标注）：示例密钥硬编码仅为可运行演示，生产须走 KMS 轮换；会话表在进程内，
 * 多实例须换共享存储；Host / Origin 判定为字面量校验非完整 DNS 解析；本类未接入 21 主模块。
 */
public final class McpSessionTransportBindingGuard {

    // ---------- 数据模型 ----------
    public enum Code {
        OK,
        FORGED_SESSION,
        INHERITED_SESSION,
        UNREGISTERED_SESSION,
        SESSION_PRINCIPAL_MISMATCH,
        MISSING_ORIGIN,
        NULL_ORIGIN,
        ORIGIN_NOT_ALLOWED,
        ORIGIN_SESSION_MISMATCH,
        BAD_HOST,
        ISSUER_UNBOUND,
        ISSUER_MISMATCH,
        WRITE_NOT_PERMITTED,
        UNBOUND_LISTENER
    }

    public record Verdict(boolean allowed, Code code, String reason) {
        public boolean isBlocked() { return !allowed; }
    }

    /** 服务端签发的会话记录。签名覆盖 sessionId|principal|origin|readOnlyOnly。 */
    public static final class SessionRecord {
        final String sessionId;
        final String principal;
        final String boundOrigin;
        final boolean readOnlyOnly;
        final String signature;
        final long issuedAt;

        SessionRecord(String sessionId, String principal, String boundOrigin,
                      boolean readOnlyOnly, String signature, long issuedAt) {
            this.sessionId = sessionId;
            this.principal = principal;
            this.boundOrigin = canonOrigin(boundOrigin);
            this.readOnlyOnly = readOnlyOnly;
            this.signature = signature;
            this.issuedAt = issuedAt;
        }
    }

    /** 一次调用的请求上下文。 */
    public static final class Request {
        final String sessionId;
        final String principal;
        final String origin;
        final String host;
        final String oauthIssuer;
        final boolean hasCredential;
        final boolean writeTool;
        final boolean handshake; // WS 升级 / SSE 连接

        public Request(String sessionId, String principal, String origin, String host,
                       String oauthIssuer, boolean hasCredential, boolean writeTool, boolean handshake) {
            this.sessionId = sessionId;
            this.principal = principal;
            this.origin = origin;
            this.host = host;
            this.oauthIssuer = oauthIssuer;
            this.hasCredential = hasCredential;
            this.writeTool = writeTool;
            this.handshake = handshake;
        }
    }

    // ---------- 配置 / 状态 ----------
    private static final String DEMO_SECRET = "v176-demo-signing-key-not-for-production";
    private static final long MAX_SESSION_AGE_SECONDS = 3600L;

    private final Set<String> allowedOrigins = new LinkedHashSet<>();
    private final String expectedIssuer;
    private final Map<String, SessionRecord> sessions = new LinkedHashMap<>();
    private final List<String> auditTrail = new ArrayList<>();
    private long nowEpoch; // 便于自检注入；生产用 Instant.now()

    public McpSessionTransportBindingGuard(Set<String> allowedOrigins, String expectedIssuer) {
        for (String o : allowedOrigins) this.allowedOrigins.add(canonOrigin(o));
        this.expectedIssuer = expectedIssuer == null ? "" : expectedIssuer.trim();
        this.nowEpoch = Instant.now().getEpochSecond();
    }

    void setClockForSelfTest(long epoch) { this.nowEpoch = epoch; }
    List<String> auditTrail() { return Collections.unmodifiableList(auditTrail); }

    /** 服务端签发会话（生产仅在认证成功后调用）。 */
    public SessionRecord issueSession(String principal, String origin, boolean readOnlyOnly) {
        String sid = "s-" + Integer.toHexString(Math.abs(principal.hashCode()))
                + "-" + Long.toHexString(nowEpoch) + "-" + Long.toHexString(sessions.size() + 1);
        String sig = sign(sid, principal, canonOrigin(origin), String.valueOf(readOnlyOnly));
        SessionRecord rec = new SessionRecord(sid, principal, origin, readOnlyOnly, sig, nowEpoch);
        sessions.put(sid, rec);
        return rec;
    }

    // ---------- 主判定：CP1 → CP2 → CP3 → CP4 → CP5，全程 CP6 审计 ----------
    public Verdict evaluate(Request r) {
        Verdict v = decide(r);
        appendAudit(r, v);
        return v;
    }

    private Verdict decide(Request r) {
        // CP5a 未认证监听：非握手请求无认证主体 → 第一道门直接拒绝
        // （k8s-mcp-server CVE-2026-130007：POST /message 无 authorization 即派发）
        if (!r.handshake && r.principal == null) {
            return verdict(Code.UNBOUND_LISTENER, "unauthenticated dispatch");
        }

        // CP1 会话绑定
        String sid = r.sessionId;
        if (sid == null || sid.isBlank()) return verdict(Code.UNREGISTERED_SESSION, "empty sessionId");
        String low = sid.toLowerCase(Locale.ROOT);
        if (low.contains("__proto__") || low.contains("constructor") || low.contains("prototype")
                || low.contains("hasownproperty")) {
            return verdict(Code.INHERITED_SESSION, "prototype-inherited sessionId");
        }
        SessionRecord rec = sessions.get(sid);
        if (rec == null) {
            // 形似服务端签发但未注册 → 伪造；否则视为未注册
            if (sid.startsWith("s-")) return verdict(Code.FORGED_SESSION, "signed-shape session not registered");
            return verdict(Code.UNREGISTERED_SESSION, "session not registered");
        }
        String expectSig = sign(rec.sessionId, rec.principal, rec.boundOrigin, String.valueOf(rec.readOnlyOnly));
        if (!constantEquals(expectSig, rec.signature)) return verdict(Code.FORGED_SESSION, "bad session signature");
        if (nowEpoch - rec.issuedAt > MAX_SESSION_AGE_SECONDS) {
            return verdict(Code.FORGED_SESSION, "session expired");
        }
        if (r.principal == null || !r.principal.equals(rec.principal)) {
            return verdict(Code.SESSION_PRINCIPAL_MISMATCH, "principal not bound to session");
        }

        // CP2 Origin（握手期强制，调用期若提供也校验）
        String origin = r.origin;
        if (r.handshake && (origin == null || origin.isBlank())) {
            return verdict(Code.MISSING_ORIGIN, "handshake requires Origin");
        }
        if (origin != null && !origin.isBlank()) {
            String co = canonOrigin(origin);
            if ("null".equalsIgnoreCase(co)) return verdict(Code.NULL_ORIGIN, "null Origin");
            if (!allowedOrigins.contains(co)) return verdict(Code.ORIGIN_NOT_ALLOWED, "Origin not allowlisted: " + co);
            if (!co.equals(rec.boundOrigin)) {
                return verdict(Code.ORIGIN_SESSION_MISMATCH, "Origin differs from session-bound Origin");
            }
        }

        // CP3 Host 合法性（BadHost / CVE-2026-48710）
        if (!validHost(r.host)) return verdict(Code.BAD_HOST, "illegal Host header");

        // CP4 OAuth issuer 绑定
        if (r.hasCredential) {
            if (r.oauthIssuer == null || r.oauthIssuer.isBlank()) {
                return verdict(Code.ISSUER_UNBOUND, "credential without issuer binding");
            }
            if (!r.oauthIssuer.trim().equals(expectedIssuer)) {
                return verdict(Code.ISSUER_MISMATCH, "issuer not the expected authorization server");
            }
        }

        // CP5 默认只读 + 未认证监听拒绝
        if (r.writeTool && rec.readOnlyOnly) {
            return verdict(Code.WRITE_NOT_PERMITTED, "write tool on read-only session");
        }
        return new Verdict(true, Code.OK, "allowed");
    }

    private Verdict verdict(Code code, String reason) { return new Verdict(false, code, reason); }

    // ---------- 旧基线（对照组）：只看 sessionId 非空 + Host 非空 + 有 Origin 头 ----------
    public Verdict legacyBaseline(Request r) {
        boolean ok = r.sessionId != null && !r.sessionId.isBlank()
                && r.host != null && !r.host.isBlank()
                && r.origin != null && !r.origin.isBlank();
        return ok ? new Verdict(true, Code.OK, "legacy: superficial checks passed")
                  : new Verdict(false, Code.UNREGISTERED_SESSION, "legacy: superficial checks failed");
    }

    // ---------- 辅助 ----------
    private static String canonOrigin(String o) {
        if (o == null) return "";
        String s = o.trim().toLowerCase(Locale.ROOT);
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private static boolean validHost(String host) {
        if (host == null || host.isBlank() || host.length() > 253) return false;
        // 逐字符拒绝 RFC 9112 非法字符：分隔符 / ? # @ 与空白 / 控制字符
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            if (c == '/' || c == '?' || c == '#' || c == '@' || c == '\\') return false;
            if (Character.isWhitespace(c) || Character.isISOControl(c)) return false;
        }
        // IPv6 字面量 [::1]:8080 允许；其余按域名/端口字符集校验
        if (host.startsWith("[")) {
            int end = host.indexOf(']');
            if (end < 0) return false;
            String tail = host.substring(end + 1);
            return tail.isEmpty() || (tail.startsWith(":") && tail.substring(1).chars().allMatch(Character::isDigit));
        }
        return host.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == ':');
    }

    private static String sign(String sid, String principal, String origin, String readOnly) {
        String data = sid + "|" + principal + "|" + origin + "|" + readOnly;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(DEMO_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] out = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : out) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC init failed", e);
        }
    }

    private static boolean constantEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) diff |= a.charAt(i) ^ b.charAt(i);
        return diff == 0;
    }

    private void appendAudit(Request r, Verdict v) {
        String sid = r.sessionId == null ? "-" : (r.sessionId.length() <= 6
                ? r.sessionId : r.sessionId.substring(0, 4) + "***" + r.sessionId.substring(r.sessionId.length() - 2));
        String origin = r.origin == null ? "-" : canonOrigin(r.origin);
        String host = r.host == null ? "-" : r.host;
        String cred = r.hasCredential ? "credential-present(issuer=" + (r.oauthIssuer == null ? "-" : r.oauthIssuer) + ")" : "no-credential";
        auditTrail.add(sid + "|" + origin + "|" + host + "|" + cred + "|" + v.code() + "|" + v.allowed());
    }

    // ---------- 自检 ----------
    public static void main(String[] args) {
        int attack = 0, control = 0, boundary = 0, legacyOnly = 0, falsePositive = 0;
        Set<String> origins = new LinkedHashSet<>(Arrays.asList("https://app.example.com", "https://console.example.com"));
        String issuer = "https://auth.example.com";
        McpSessionTransportBindingGuard g = new McpSessionTransportBindingGuard(origins, issuer);
        g.setClockForSelfTest(1_700_000_000L);

        SessionRecord ro = g.issueSession("alice", "https://app.example.com", true);
        SessionRecord rw = g.issueSession("bob", "https://console.example.com", false);

        // ===== 合法对照（0 误杀）=====
        Request legalWs = new Request(ro.sessionId, "alice", "https://app.example.com",
                "mcp.example.com:443", null, false, false, true);
        expectAllow(g, "control: WS upgrade bound session", legalWs); control++;
        expectAllow(g, "control: SSE connect", new Request(rw.sessionId, "bob", "https://console.example.com",
                "mcp.example.com:443", null, false, false, true)); control++;
        expectAllow(g, "control: tool call with bound issuer", new Request(rw.sessionId, "bob", "https://console.example.com",
                "mcp.example.com:443", issuer, true, true, false)); control++;
        expectAllow(g, "control: IPv6 host literal", new Request(rw.sessionId, "bob", "https://console.example.com",
                "[::1]:8080", null, false, false, false)); control++;
        Verdict la = g.evaluate(legalWs);
        if (!la.allowed()) falsePositive++;

        // ===== CP1 会话绑定 =====
        expectBlock(g, "CP1 forged signed-shape session", new Request("s-deadbeef-1", "alice",
                "https://app.example.com", "mcp.example.com", null, false, false, false), Code.FORGED_SESSION); attack++;
        expectBlock(g, "CP1 prototype-inherited session", new Request("__proto__.sessionId", "alice",
                "https://app.example.com", "mcp.example.com", null, false, false, false), Code.INHERITED_SESSION); attack++;
        expectBlock(g, "CP1 constructor-inherited session", new Request("constructor.prototype.sid", "alice",
                "https://app.example.com", "mcp.example.com", null, false, false, false), Code.INHERITED_SESSION); attack++;
        expectBlock(g, "CP1 unregistered session", new Request("random-id", "alice",
                "https://app.example.com", "mcp.example.com", null, false, false, false), Code.UNREGISTERED_SESSION); attack++;
        expectBlock(g, "CP1 principal mismatch", new Request(ro.sessionId, "mallory",
                "https://app.example.com", "mcp.example.com", null, false, false, false), Code.SESSION_PRINCIPAL_MISMATCH); attack++;
        SessionRecord tampered = new SessionRecord("s-tamper", "alice", "https://app.example.com", true, "deadbeef", 1_700_000_000L);
        g.sessions.put("s-tamper", tampered);
        expectBlock(g, "CP1 bad signature", new Request("s-tamper", "alice",
                "https://app.example.com", "mcp.example.com", null, false, false, false), Code.FORGED_SESSION); attack++;

        // ===== CP2 Origin =====
        expectBlock(g, "CP2 missing Origin on handshake", new Request(ro.sessionId, "alice",
                "", "mcp.example.com", null, false, false, true), Code.MISSING_ORIGIN); attack++;
        expectBlock(g, "CP2 null Origin", new Request(ro.sessionId, "alice",
                "null", "mcp.example.com", null, false, false, true), Code.NULL_ORIGIN); attack++;
        expectBlock(g, "CP2 Origin not allowlisted", new Request(ro.sessionId, "alice",
                "https://evil.attacker", "mcp.example.com", null, false, false, true), Code.ORIGIN_NOT_ALLOWED); attack++;
        expectBlock(g, "CP2 Origin/session mismatch", new Request(ro.sessionId, "alice",
                "https://console.example.com", "mcp.example.com", null, false, false, true), Code.ORIGIN_SESSION_MISMATCH); attack++;
        expectBlock(g, "CP2 cross-site WS hijack (CVE-2026-132121)", new Request(ro.sessionId, "alice",
                "https://malicious.page", "mcp.example.com", null, false, false, true), Code.ORIGIN_NOT_ALLOWED); attack++;

        // ===== CP3 Host（BadHost）=====
        expectBlock(g, "CP3 Host with question mark", new Request(ro.sessionId, "alice",
                "https://app.example.com", "foo?/admin", null, false, false, false), Code.BAD_HOST); attack++;
        expectBlock(g, "CP3 Host with slash", new Request(ro.sessionId, "alice",
                "https://app.example.com", "foo/admin", null, false, false, false), Code.BAD_HOST); attack++;
        expectBlock(g, "CP3 Host with hash", new Request(ro.sessionId, "alice",
                "https://app.example.com", "foo#/x", null, false, false, false), Code.BAD_HOST); attack++;
        expectBlock(g, "CP3 Host with userinfo", new Request(ro.sessionId, "alice",
                "https://app.example.com", "user@evil.com", null, false, false, false), Code.BAD_HOST); attack++;
        expectBlock(g, "CP3 Host with control char", new Request(ro.sessionId, "alice",
                "https://app.example.com", "mcp.example.com\nX:1", null, false, false, false), Code.BAD_HOST); attack++;

        // ===== CP4 issuer 绑定 =====
        expectBlock(g, "CP4 credential without issuer", new Request(rw.sessionId, "bob",
                "https://console.example.com", "mcp.example.com", null, true, false, false), Code.ISSUER_UNBOUND); attack++;
        expectBlock(g, "CP4 issuer chosen by server", new Request(rw.sessionId, "bob",
                "https://console.example.com", "mcp.example.com", "https://attacker.as", true, false, false), Code.ISSUER_MISMATCH); attack++;

        // ===== CP5 默认只读 / 未认证监听 =====
        expectBlock(g, "CP5 write on read-only session", new Request(ro.sessionId, "alice",
                "https://app.example.com", "mcp.example.com", null, false, true, false), Code.WRITE_NOT_PERMITTED); attack++;
        expectBlock(g, "CP5 unauthenticated dispatch", new Request(null, null,
                "https://app.example.com", "mcp.example.com", null, false, false, false), Code.UNBOUND_LISTENER); attack++;

        // ===== 不得劣于旧基线：旧基线放行的攻击，新守卫必须拦下 =====
        Request[] legacyMisses = {
                new Request("s-deadbeef-1", "alice", "https://app.example.com", "mcp.example.com", null, false, false, false),
                new Request(ro.sessionId, "alice", "https://malicious.page", "mcp.example.com", null, false, false, true),
                new Request(ro.sessionId, "alice", "https://app.example.com", "foo?/admin", null, false, false, false),
                new Request(rw.sessionId, "bob", "https://console.example.com", "mcp.example.com", "https://attacker.as", true, false, false),
                new Request(ro.sessionId, "alice", "https://app.example.com", "mcp.example.com", null, false, true, false),
                new Request("__proto__.sessionId", "alice", "https://app.example.com", "mcp.example.com", null, false, false, false)
        };
        for (Request req : legacyMisses) {
            Verdict lg = g.legacyBaseline(req);
            Verdict nv = g.evaluate(req);
            if (lg.allowed() && !nv.allowed()) legacyOnly++;
            else throw new AssertionError("legacy-baseline assertion broken: " + req.sessionId);
        }

        // ===== 边界：审计脱敏 =====
        boundary++;
        String trail = g.auditTrail().toString();
        if (trail.contains(ro.sessionId) || trail.contains("s-deadbeef-1")) {
            throw new AssertionError("CP6 audit must not contain full sessionId");
        }
        boundary++;
        int before = g.auditTrail().size();
        g.evaluate(new Request(ro.sessionId, "alice", "https://app.example.com", "mcp.example.com", null, false, false, false));
        if (g.auditTrail().size() != before + 1) throw new AssertionError("CP6 audit must be append-only");

        System.out.println("self-test OK: " + attack + " attack + " + control + " control + "
                + boundary + " boundary assertions");
        System.out.println("  legacy-only blocks (new guard catches, baseline misses): " + legacyOnly);
        System.out.println("  false positives on legal calls: " + falsePositive);
        System.out.println("  audit entries: " + g.auditTrail().size());
    }

    private static void expectAllow(McpSessionTransportBindingGuard g, String label, Request r) {
        Verdict v = g.evaluate(r);
        if (!v.allowed()) throw new AssertionError(label + " should be allowed but got " + v.code() + ": " + v.reason());
    }

    private static void expectBlock(McpSessionTransportBindingGuard g, String label, Request r, Code expected) {
        Verdict v = g.evaluate(r);
        if (v.allowed() || v.code() != expected) {
            throw new AssertionError(label + " should be " + expected + " but got " + v.code() + ": " + v.reason());
        }
    }
}
