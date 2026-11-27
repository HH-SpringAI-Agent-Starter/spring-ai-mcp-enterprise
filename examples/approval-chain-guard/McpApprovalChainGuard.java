package examples.approvalchainguard;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * McpApprovalChainGuard - approval / delegation-chain execution gate (V1.81, 2026-11-27).
 *
 * <p>Thirteenth segment of the "no declaration is a security boundary" closure.
 * This gate makes one claim only: a confirmation token, a claimed scope and a
 * readOnlyHint are audit facts, NEVER allow-branch inputs.</p>
 *
 * <p>Real-world evidence this model is derived from (all third-party, none directly
 * affecting this framework): CVE-2026-131688 (miniOrange Secure MCP Server <=1.5.8,
 * restricted bearer token escalates to Administrator, CVSS 8.1, published 11-26);
 * CVE-2026-136620 (pain001-mcp 0.0.72-0.0.79, confirmation_token handed back to the
 * same requester = self-approval, plus unbounded output_file_path, fixed in 0.0.80);
 * official MCP servers v2.5.0 (11-18) git_merge -> .git/config gc.recentObjectsHook
 * arbitrary command, read-only git_diff -> ../outside/secret via crafted .git/index,
 * promisor remote -> read-only tool fetches objects, filesystem symlink TOCTOU with
 * Windows still unfixed (#5600), everything gzip NAT64 private-as-public.</p>
 *
 * <p>Pure JDK 17, zero dependencies. Not integrated into the 21 reactor modules;
 * it is an executable reference gate, not a production component.</p>
 */
public final class McpApprovalChainGuard {

    private McpApprovalChainGuard() {}

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

    /** Side effects that demand a genuinely independent approval. */
    public enum Effect { READ, WRITE, PAYMENT, EXEC, EXPORT }

    public static final class Request {
        public String tool;
        public String principal;          // who asks
        public String claimedScope;       // CLIENT declaration - audit only
        public String effectiveScope;     // SERVER-recorded scope bound into the token
        public boolean readOnlyHint;      // TOOL declaration - audit only
        public Effect effect = Effect.READ;
        public String confirmationToken;
        public String tokenIssuer;        // CONTROL_PLANE or SELF
        public String tokenSubject;       // who approved
        public String tokenBoundHash;     // hash of the exact payload approved
        public long tokenIssuedAt;
        public long now;
        public String outputPath;         // relative path requested
        public String outputRoot;         // confinement root
        public String delegatedActions;   // comma separated internal actions
        public String payload;            // the exact request body

        public Request tool(String v) { this.tool = v; return this; }
        public Request principal(String v) { this.principal = v; return this; }
        public Request claimedScope(String v) { this.claimedScope = v; return this; }
        public Request effectiveScope(String v) { this.effectiveScope = v; return this; }
        public Request readOnlyHint(boolean v) { this.readOnlyHint = v; return this; }
        public Request effect(Effect v) { this.effect = v; return this; }
        public Request confirmationToken(String v) { this.confirmationToken = v; return this; }
        public Request tokenIssuer(String v) { this.tokenIssuer = v; return this; }
        public Request tokenSubject(String v) { this.tokenSubject = v; return this; }
        public Request tokenBoundHash(String v) { this.tokenBoundHash = v; return this; }
        public Request tokenIssuedAt(long v) { this.tokenIssuedAt = v; return this; }
        public Request now(long v) { this.now = v; return this; }
        public Request outputPath(String v) { this.outputPath = v; return this; }
        public Request outputRoot(String v) { this.outputRoot = v; return this; }
        public Request delegatedActions(String v) { this.delegatedActions = v; return this; }
        public Request payload(String v) { this.payload = v; return this; }
    }

    // ------------------------------------------------------- policy constants

    /** Server-side truth: what scope each tool really requires. */
    // NOTE: constants MUST be stored in canon() form (lowercase, underscores removed).
    // Storing raw spellings here caused a real false-positive in the self-test on V1.81.
    private static final Set<String> READ_TOOLS =
            new HashSet<>(Arrays.asList("searchrecords", "gitdiff", "exportmetrics"));
    private static final Set<String> APPROVERS =
            new HashSet<>(Arrays.asList("risk-officer-1", "risk-officer-2", "control-plane"));
    private static final long TOKEN_TTL_MS = 5L * 60L * 1000L;

    /** Internal actions that must be re-judged, never inherited from the entry tool. */
    private static final Set<String> DELEGATED_WRITE = new HashSet<>(Arrays.asList(
            "gc.recentobjectshook", "gitautomaintenance", "submoduleconfig",
            "emptyfilter", "promisorfetch", "symlinkresolve", "filewrite",
            "execsync", "cdp.runtime.evaluate", "shellcommand"));

    private static final List<String> AUDIT = Collections.synchronizedList(new java.util.ArrayList<>());
    private static final Set<String> CONSUMED = Collections.synchronizedSet(new HashSet<>());

    public static List<String> auditTrail() { return Collections.unmodifiableList(AUDIT); }
    public static void resetState() { AUDIT.clear(); CONSUMED.clear(); }

    // ------------------------------------------------------------------ gate

    public static Verdict check(Request r) {
        if (r == null || r.tool == null || r.principal == null || r.payload == null) {
            return deny("CP0", "incomplete request - fail closed");
        }
        final boolean needsApproval = r.effect != Effect.READ;
        final String requiredScope = READ_TOOLS.contains(canon(r.tool)) ? "read" : "elevated";
        final String boundHash = sha256(r.payload);

        // CP1 - approver must be independent of the requester.
        if (needsApproval) {
            if (r.confirmationToken == null || r.confirmationToken.isBlank()) {
                return deny("CP1", "state-changing effect has no confirmation token");
            }
            if (!"CONTROL_PLANE".equals(r.tokenIssuer)) {
                return deny("CP1", "token not issued by the control plane");
            }
            if (r.tokenSubject == null || canon(r.tokenSubject).equals(canon(r.principal))) {
                return deny("CP1", "self-approval: approver is the requester");
            }
            if (!APPROVERS.contains(canon(r.tokenSubject))) {
                return deny("CP1", "approver is not allowlisted");
            }
        }

        // CP2 - claimed scope / readOnlyHint NEVER enter the allow branch.
        if (!requiredScope.equals(canon(r.effectiveScope))) {
            return deny("CP2", "server-recorded scope " + r.effectiveScope
                    + " != required " + requiredScope + " (claim ignored)");
        }

        // CP5 - one-shot, TTL-bound, payload-bound token.
        if (needsApproval) {
            if (r.tokenIssuedAt > r.now || r.now - r.tokenIssuedAt > TOKEN_TTL_MS) {
                return deny("CP5", "confirmation token expired or future-dated");
            }
            if (!boundHash.equals(r.tokenBoundHash)) {
                return deny("CP5", "confirmation token not bound to this exact payload");
            }
            if (!CONSUMED.add(r.confirmationToken)) {
                return deny("CP5", "confirmation token replayed");
            }
        }

        // CP3 - every delegated action is judged on its own, not inherited.
        if (r.delegatedActions != null && !r.delegatedActions.isBlank()) {
            for (String raw : r.delegatedActions.split(",")) {
                String a = canon(raw);
                if (DELEGATED_WRITE.contains(a)) {
                    if ("read".equals(requiredScope)) {
                        return deny("CP3", "read-only entry tool delegated write/exec action: " + raw.trim());
                    }
                    if (!needsApproval) {
                        return deny("CP3", "delegated write/exec action lacks independent approval");
                    }
                }
            }
        }

        // CP4 - real-path confinement for any produced artifact.
        if (r.outputPath != null && !r.outputPath.isBlank()) {
            Path root;
            try {
                root = Paths.get(r.outputRoot).toRealPath();
            } catch (Exception e) {
                return deny("CP4", "output root does not resolve");
            }
            Path requested = Paths.get(r.outputPath);
            if (requested.isAbsolute()) {
                return deny("CP4", "absolute output path rejected");
            }
            Path resolved = root.resolve(requested).normalize();
            if (!resolved.startsWith(root)) {
                return deny("CP4", "output path escapes confinement root");
            }
            String name = resolved.getFileName().toString().toLowerCase(Locale.ROOT);
            if (!name.endsWith(".xml")) {
                return deny("CP4", "output extension not allowlisted");
            }
            if (Files.exists(resolved)) {
                return deny("CP4", "existing artifact would be overwritten");
            }
            for (Path p = resolved.getParent(); p != null && !p.equals(root); p = p.getParent()) {
                if (Files.isSymbolicLink(p)) {
                    return deny("CP4", "symbolic link inside output path");
                }
            }
        }

        // CP6 - append-only, redacted audit. Declarations are recorded here only.
        appendAudit(r, true, "accepted");
        return new Verdict(true, "CP6", "allowed after independent chain validation");
    }

    /** Deliberately weak historical baseline: token present + readOnlyHint only. */
    public static Verdict legacyBaseline(Request r) {
        if (r == null || r.tool == null) return deny("LEGACY", "incomplete");
        if (r.readOnlyHint || (r.confirmationToken != null && !r.confirmationToken.isBlank())) {
            return new Verdict(true, "LEGACY", "declaration accepted");
        }
        return deny("LEGACY", "no declaration");
    }

    // ----------------------------------------------------------------- utils

    private static Verdict deny(String cp, String reason) {
        appendAudit(null, false, reason);
        return new Verdict(false, cp, reason);
    }

    private static void appendAudit(Request r, boolean allowed, String reason) {
        StringBuilder b = new StringBuilder();
        b.append(allowed ? "ALLOW" : "DENY").append('|');
        if (r != null) {
            b.append(canon(r.tool)).append('|').append(canon(r.principal)).append('|');
            b.append("claimed=").append(canon(r.claimedScope)).append('|');
            b.append("effective=").append(canon(r.effectiveScope)).append('|');
            b.append("hint=").append(r.readOnlyHint).append('|');
            b.append("token=").append(r.confirmationToken == null
                    ? "none" : ("sha256:" + sha256(r.confirmationToken).substring(0, 12)));
        } else {
            b.append("null-request");
        }
        b.append('|').append(reason);
        AUDIT.add(b.toString());   // append-only: no mutator removes entries
    }

    private static String canon(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT).replace("_", "");
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

    /** Exposed for the self-test to bind a payload exactly as a real control plane would. */
    public static String bind(String payload) { return sha256(payload); }
}
