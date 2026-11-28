package examples.sharedtransportguard;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * McpSharedTransportGuard - shared-transport / cross-client isolation gate (V1.82, 2026-11-28).
 *
 * <p>Fourteenth segment of the "no declaration is a security boundary" closure.
 * This gate makes one claim only: a transport instance, a JSON-RPC request id,
 * a claimed scope and a network bind address are routing facts, NEVER proof of
 * which caller a response or a billed credential belongs to.</p>
 *
 * <p>Real-world evidence this model is derived from (all third-party, none directly
 * affecting this framework): CVE-2026-111770 (official MCP TypeScript SDK
 * &#64;modelcontextprotocol/server 2.0.0-2.3.0, CVSS 7.7, published 11-05 with fresh
 * vendor analysis 11-22: a stateless HTTP transport / McpServer shared across
 * clients correlates messages by JSON-RPC id only; SDK clients start ids at zero,
 * so responses and notifications are delivered to the WRONG client; fixed 2.3.0,
 * upgrade breaks reused instances and REQUIRES an application code change).
 * CVE-2026-138026 (VulnCheck 11-27, Crypto APIs 14 npm packages: HTTP mode bound
 * 0.0.0.0 with no auth, any caller spends the operator's key; rotate + rebind).
 * CVE-2026-77318/77359 (Jupyter MCP Server: /mcp protected but state-changing
 * /api/connect and /api/stop management routes unauthenticated, DNS rebinding).
 * CVE-2026-77318 family, 8 CVEs published 11-27 by one researcher.</p>
 *
 * <p>Pure JDK 17, zero dependencies. Not integrated into the 21 reactor modules;
 * it is an executable reference gate, not a production component.</p>
 */
public final class McpSharedTransportGuard {

    private McpSharedTransportGuard() {}

    // ------------------------------------------------------------------ model

    public static final class Verdict {
        public final boolean allowed;
        public final String control;
        public final String reason;
        private Verdict(boolean allowed, String control, String reason) {
            this.allowed = allowed;
            this.control = control;
            this.reason = reason;
        }
        @Override public String toString() {
            return (allowed ? "ALLOW" : "DENY") + "[" + control + "] " + reason;
        }
    }

    /** STDIO is single-client and, per the official advisory, not affected. */
    public enum TransportMode { STDIO, HTTP_STATEFUL, HTTP_STATELESS }
    public enum Effect { READ, WRITE, MANAGE }

    public static final class Request {
        public String tool;
        public String principal;
        public TransportMode mode = TransportMode.HTTP_STATELESS;
        public String transportId;       // the server/transport INSTANCE id
        public int jsonrpcId;            // starts at 0 in official SDKs -> collides
        public String sessionId;         // who this message belongs to (routing fact)
        public String sessionPrincipal;  // principal bound into the session
        public String route = "/mcp";
        public String bindAddress = "127.0.0.1";
        public String authToken;
        public String adminToken;        // independent token for management routes
        public String credentialRef;     // which API key is being spent
        public String credentialOwner;   // principal the key belongs to
        public String claimedScope;      // CLIENT declaration - audit only
        public boolean readOnlyHint;     // TOOL declaration - audit only
        public Effect effect = Effect.READ;
        public String payload;

        public Request tool(String v) { this.tool = v; return this; }
        public Request principal(String v) { this.principal = v; return this; }
        public Request mode(TransportMode v) { this.mode = v; return this; }
        public Request transportId(String v) { this.transportId = v; return this; }
        public Request jsonrpcId(int v) { this.jsonrpcId = v; return this; }
        public Request sessionId(String v) { this.sessionId = v; return this; }
        public Request sessionPrincipal(String v) { this.sessionPrincipal = v; return this; }
        public Request route(String v) { this.route = v; return this; }
        public Request bindAddress(String v) { this.bindAddress = v; return this; }
        public Request authToken(String v) { this.authToken = v; return this; }
        public Request adminToken(String v) { this.adminToken = v; return this; }
        public Request credentialRef(String v) { this.credentialRef = v; return this; }
        public Request credentialOwner(String v) { this.credentialOwner = v; return this; }
        public Request claimedScope(String v) { this.claimedScope = v; return this; }
        public Request readOnlyHint(boolean v) { this.readOnlyHint = v; return this; }
        public Request effect(Effect v) { this.effect = v; return this; }
        public Request payload(String v) { this.payload = v; return this; }
    }

    // ------------------------------------------------------- policy constants

    // NOTE: constants MUST be stored in canon()/norm() form. Storing raw
    // spellings while comparing through normalization caused a real false
    // positive in V1.81 — do not repeat it.
    private static final Set<String> MGMT_ROUTES = new HashSet<>(Arrays.asList(
            "/api/connect", "/api/stop", "/api/servers", "/api/register"));
    private static final Set<String> LOOPBACK =
            new HashSet<>(Arrays.asList("127.0.0.1", "localhost", "::1"));

    // transport instance -> canon(principal) that first attached it
    private static final Map<String, String> ATTACHED =
            Collections.synchronizedMap(new HashMap<>());
    private static final List<String> AUDIT =
            Collections.synchronizedList(new ArrayList<>());

    public static List<String> auditTrail() { return Collections.unmodifiableList(AUDIT); }
    public static void resetState() { ATTACHED.clear(); AUDIT.clear(); }

    /** Test hook: simulate a prior client attaching a transport instance. */
    public static void preAttach(String transportId, String principal) {
        if (transportId != null) ATTACHED.put(transportId, canon(principal));
    }

    // ------------------------------------------------------------------ gate

    public static Verdict check(Request r) {
        if (r == null || r.tool == null || r.principal == null || r.transportId == null) {
            return deny("CP0", "incomplete request - fail closed");
        }

        // CP4 - non-loopback bind must carry authentication (fail closed).
        if (!LOOPBACK.contains(norm(r.bindAddress))
                && (r.authToken == null || r.authToken.isBlank())) {
            return deny("CP4", "non-loopback bind " + r.bindAddress + " without auth token");
        }

        // CP3 - management routes need an INDEPENDENT admin token and real effect.
        if (MGMT_ROUTES.contains(norm(r.route))) {
            if (r.effect != Effect.MANAGE) {
                return deny("CP3", "management route declared as non-manage effect");
            }
            if (r.adminToken == null || r.adminToken.isBlank()) {
                return deny("CP3", "state-changing management route without admin token");
            }
        }

        // CP5 - a billed credential may only be spent by its owner.
        if (r.credentialRef != null && !r.credentialRef.isBlank()) {
            if (r.credentialOwner == null
                    || !canon(r.credentialOwner).equals(canon(r.principal))) {
                return deny("CP5", "credential owner " + r.credentialOwner
                        + " != caller " + r.principal + " (operator key spent by another)");
            }
        }

        // CP1 - one transport/server instance must not be reused across clients.
        // STDIO is single-client and exempt, per the official advisory.
        if (r.mode != TransportMode.STDIO) {
            String owner = ATTACHED.get(r.transportId);
            if (owner != null && !owner.equals(canon(r.principal))) {
                return deny("CP1", "transport instance " + r.transportId
                        + " already attached by " + owner + " (cross-client reuse)");
            }
            ATTACHED.putIfAbsent(r.transportId, canon(r.principal));
        }

        // CP2 - an HTTP message needs session ownership, not just a JSON-RPC id.
        if (r.mode != TransportMode.STDIO) {
            if (r.sessionId == null || r.sessionId.isBlank()) {
                return deny("CP2", "id-only message " + r.jsonrpcId
                        + " has no session ownership (id collision across clients)");
            }
            if (r.sessionPrincipal == null
                    || !canon(r.sessionPrincipal).equals(canon(r.principal))) {
                return deny("CP2", "session bound to a different principal than caller");
            }
        }

        // CP6 - append-only, redacted audit. Declarations are recorded here ONLY.
        appendAudit(r, true, "accepted");
        return new Verdict(true, "CP6", "allowed after shared-transport isolation checks");
    }

    /** Deliberately weak historical baseline: transport present + token present. */
    public static Verdict legacyBaseline(Request r) {
        if (r == null || r.transportId == null) return denyNoAudit("LEGACY", "incomplete");
        if (r.authToken != null && !r.authToken.isBlank()) {
            return new Verdict(true, "LEGACY", "transport + token present");
        }
        return denyNoAudit("LEGACY", "no token");
    }

    // ----------------------------------------------------------------- utils

    private static Verdict deny(String cp, String reason) {
        appendAudit(null, false, reason);
        return denyNoAudit(cp, reason);
    }

    private static Verdict denyNoAudit(String cp, String reason) {
        return new Verdict(false, cp, reason);
    }

    private static void appendAudit(Request r, boolean allowed, String reason) {
        StringBuilder b = new StringBuilder();
        b.append(allowed ? "ALLOW" : "DENY").append('|');
        if (r != null) {
            b.append(canon(r.tool)).append('|').append(canon(r.principal)).append('|');
            b.append("mode=").append(r.mode).append('|');
            b.append("transport=").append(canon(r.transportId)).append('|');
            b.append("id=").append(r.jsonrpcId).append('|');
            b.append("route=").append(norm(r.route)).append('|');
            b.append("claimed=").append(canon(r.claimedScope)).append('|');
            b.append("hint=").append(r.readOnlyHint).append('|');
            b.append("cred=").append(r.credentialRef == null
                    ? "none" : ("sha256:" + sha256(r.credentialRef).substring(0, 12)));
        } else {
            b.append("null-request");
        }
        b.append('|').append(reason);
        AUDIT.add(b.toString());   // append-only: no mutator removes entries
    }

    private static String canon(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT).replace("_", "");
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static String sha256(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256")
                    .digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (byte x : d) b.append(String.format("%02x", x & 0xff));
            return b.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Exposed for the self-test to bind a token exactly as a control plane would. */
    public static String bind(String s) { return sha256(s); }
}
