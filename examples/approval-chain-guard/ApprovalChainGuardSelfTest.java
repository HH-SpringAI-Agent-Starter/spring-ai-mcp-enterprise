package examples.approvalchainguard;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import examples.approvalchainguard.McpApprovalChainGuard.Effect;
import examples.approvalchainguard.McpApprovalChainGuard.Request;
import examples.approvalchainguard.McpApprovalChainGuard.Verdict;

/** Executable acceptance suite for V1.81 approval/delegation-chain gate. */
public final class ApprovalChainGuardSelfTest {

    private static int attack = 0, control = 0, boundary = 0;
    private static int legacyMissed = 0;
    private static final List<Request> ATTACKS = new ArrayList<>();

    private static void reset() { McpApprovalChainGuard.resetState(); }

    private static void expectDeny(String id, Request r, String cp) {
        reset();
        Verdict v = McpApprovalChainGuard.check(r);
        if (v.allowed || !v.control.equals(cp)) {
            throw new AssertionError(id + " expected DENY@" + cp + " but got " + v);
        }
        Verdict legacy = McpApprovalChainGuard.legacyBaseline(r);
        if (legacy.allowed) legacyMissed++;
        ATTACKS.add(r);
        attack++;
    }

    private static void expectAllow(String id, Request r) {
        reset();
        Verdict v = McpApprovalChainGuard.check(r);
        if (!v.allowed) throw new AssertionError(id + " expected ALLOW but got " + v);
        control++;
    }

    private static void expect(String id, boolean ok) {
        if (!ok) throw new AssertionError(id + " failed");
        boundary++;
    }

    private static Request approve(Request r, String approver, String token, String payload) {
        return r.confirmationToken(token)
                .tokenIssuer("CONTROL_PLANE")
                .tokenSubject(approver)
                .tokenBoundHash(McpApprovalChainGuard.bind(payload))
                .tokenIssuedAt(1000L).now(1000L + 60_000L);
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("approval-chain-");
        String dir = root.toRealPath().toString();

        // ---------------- attacks: confirmation / delegation / confinement ----
        String p = "batch-001";
        Request a1 = approve(new Request().tool("commit_payment_batch").principal("agent-a")
                .claimedScope("read").effectiveScope("elevated").effect(Effect.PAYMENT)
                .readOnlyHint(true).payload(p), "agent-a", "tok-self-1", p)
                .tokenIssuer("SELF");
        expectDeny("A1", a1, "CP1");

        Request a2 = approve(new Request().tool("commit_payment_batch").principal("agent-a")
                .claimedScope("limited").effectiveScope("elevated").effect(Effect.PAYMENT)
                .payload(p), "AGENT-A", "tok-self-2", p);
        expectDeny("A2", a2, "CP1");

        Request a3 = approve(new Request().tool("export_batch").principal("agent-a")
                .claimedScope("limited").effectiveScope("elevated").effect(Effect.EXPORT)
                .payload(p), "random-user", "tok-bad-approver", p);
        expectDeny("A3", a3, "CP1");

        Request a4 = new Request().tool("commit_payment_batch").principal("agent-a")
                .claimedScope("read").effectiveScope("elevated").effect(Effect.PAYMENT)
                .readOnlyHint(true).payload(p);
        expectDeny("A4", a4, "CP1");

        Request a5 = new Request().tool("search_records").principal("agent-a")
                .claimedScope("admin").effectiveScope("admin").readOnlyHint(true)
                .payload(p).effect(Effect.READ);
        expectDeny("A5", a5, "CP2");

        Request a6 = approve(new Request().tool("commit_payment_batch").principal("agent-a")
                .claimedScope("read").effectiveScope("read").effect(Effect.PAYMENT)
                .payload(p), "risk-officer-1", "tok-scope-1", p);
        expectDeny("A6", a6, "CP2");

        Request a7 = approve(new Request().tool("commit_payment_batch").principal("agent-a")
                .claimedScope("x").effectiveScope("elevated").effect(Effect.PAYMENT)
                .payload(p), "risk-officer-1", "tok-expired", p)
                .tokenIssuedAt(1000L).now(1000L + 600_001L);
        expectDeny("A7", a7, "CP5");

        Request a8 = approve(new Request().tool("commit_payment_batch").principal("agent-a")
                .claimedScope("x").effectiveScope("elevated").effect(Effect.PAYMENT)
                .payload(p), "risk-officer-1", "tok-future", p)
                .tokenIssuedAt(999_999L).now(1000L);
        expectDeny("A8", a8, "CP5");

        Request a9 = approve(new Request().tool("commit_payment_batch").principal("agent-a")
                .claimedScope("x").effectiveScope("elevated").effect(Effect.PAYMENT)
                .payload(p + "-tampered"), "risk-officer-1", "tok-unbound", p);
        expectDeny("A9", a9, "CP5");

        Request a10 = approve(new Request().tool("commit_payment_batch").principal("agent-a")
                .claimedScope("x").effectiveScope("elevated").effect(Effect.PAYMENT)
                .payload(p), "risk-officer-1", "tok-replay", p);
        reset();
        if (!McpApprovalChainGuard.check(a10).allowed) throw new AssertionError("A10 setup failed");
        ATTACKS.add(a10); attack++;
        Verdict replay = McpApprovalChainGuard.check(a10);
        if (replay.allowed || !replay.control.equals("CP5")) {
            throw new AssertionError("A10 expected replay denial but got " + replay);
        }

        Request a11 = new Request().tool("git_diff").principal("agent-a")
                .claimedScope("read").effectiveScope("read").readOnlyHint(true)
                .delegatedActions("gc.recentObjectsHook").payload(p).effect(Effect.READ);
        expectDeny("A11", a11, "CP3");

        Request a12 = new Request().tool("git_diff").principal("agent-a")
                .claimedScope("read").effectiveScope("read").readOnlyHint(true)
                .delegatedActions("promisorfetch").payload(p).effect(Effect.READ);
        expectDeny("A12", a12, "CP3");

        Request a13 = new Request().tool("git_diff").principal("agent-a")
                .claimedScope("read").effectiveScope("read").readOnlyHint(true)
                .delegatedActions("symlinkresolve").payload(p).effect(Effect.READ);
        expectDeny("A13", a13, "CP3");

        Request a14 = new Request().tool("export_metrics").principal("agent-a")
                .claimedScope("read").effectiveScope("read").readOnlyHint(true)
                .delegatedActions("execSync").payload(p).effect(Effect.READ);
        expectDeny("A14", a14, "CP3");

        expectDeny("A15", new Request().tool("export_metrics").principal("agent-a")
                .claimedScope("read").effectiveScope("read").payload(p)
                .outputRoot(dir).outputPath(dir + "/evil.xml").effect(Effect.READ), "CP4");

        expectDeny("A16", new Request().tool("export_metrics").principal("agent-a")
                .claimedScope("read").effectiveScope("read").payload(p)
                .outputRoot(dir).outputPath("../outside/secret.xml").effect(Effect.READ), "CP4");

        expectDeny("A17", new Request().tool("export_metrics").principal("agent-a")
                .claimedScope("read").effectiveScope("read").payload(p)
                .outputRoot(dir).outputPath("evil.sh").effect(Effect.READ), "CP4");

        Path existing = root.resolve("existing.xml");
        Files.write(existing, "old".getBytes("UTF-8"));
        expectDeny("A18", new Request().tool("export_metrics").principal("agent-a")
                .claimedScope("read").effectiveScope("read").payload(p)
                .outputRoot(dir).outputPath("existing.xml").effect(Effect.READ), "CP4");

        Path linkDir = root.resolve("linked");
        Files.createSymbolicLink(linkDir, root);
        expectDeny("A19", new Request().tool("export_metrics").principal("agent-a")
                .claimedScope("read").effectiveScope("read").payload(p)
                .outputRoot(dir).outputPath("linked/new.xml").effect(Effect.READ), "CP4");

        Request a20 = approve(new Request().tool("commit_payment_batch").principal("agent-a")
                .claimedScope("read").effectiveScope("elevated").effect(Effect.PAYMENT)
                .readOnlyHint(true).payload(p), "risk-officer-1", "tok-hint-1", p)
                .tokenIssuer("SELF").tokenSubject("agent-a");
        expectDeny("A20", a20, "CP1");

        // ---------------- controls: legitimate calls must not be killed -------
        expectAllow("C1", new Request().tool("search_records").principal("user-1")
                .claimedScope("read").effectiveScope("read").readOnlyHint(true)
                .payload("select id from t where owner='u'").effect(Effect.READ));

        expectAllow("C2", new Request().tool("git_diff").principal("user-1")
                .claimedScope("read").effectiveScope("read").readOnlyHint(true)
                .delegatedActions("blame_read").payload("diff").effect(Effect.READ));

        String p3 = "payment-good-3";
        expectAllow("C3", approve(new Request().tool("commit_payment_batch").principal("user-1")
                .claimedScope("elevated").effectiveScope("elevated").effect(Effect.PAYMENT)
                .payload(p3), "risk-officer-1", "tok-good-3", p3));

        String p4 = "payment-good-4";
        expectAllow("C4", approve(new Request().tool("commit_payment_batch").principal("user-1")
                .claimedScope("elevated").effectiveScope("elevated").effect(Effect.PAYMENT)
                .payload(p4), "risk-officer-2", "tok-good-4", p4));

        // Claim says admin but server record stays read -> claim ignored, still allowed.
        expectAllow("C5", new Request().tool("search_records").principal("user-1")
                .claimedScope("ADMIN").effectiveScope("read").readOnlyHint(true)
                .payload("q").effect(Effect.READ));

        expectAllow("C6", new Request().tool("export_metrics").principal("user-1")
                .claimedScope("read").effectiveScope("read").payload("m")
                .outputRoot(dir).outputPath("reports/metrics.xml").effect(Effect.READ));

        String p7 = "write-good-7";
        expectAllow("C7", approve(new Request().tool("write_records").principal("user-1")
                .claimedScope("elevated").effectiveScope("elevated").effect(Effect.WRITE)
                .delegatedActions("filewrite").payload(p7)
                .outputRoot(dir).outputPath("out/write-7.xml"), "risk-officer-1", "tok-good-7", p7));

        // Payload contains SQL-looking text but this gate does not judge SQL semantics.
        expectAllow("C8", new Request().tool("search_records").principal("user-1")
                .claimedScope("read").effectiveScope("read").readOnlyHint(true)
                .payload("notes LIKE '%DROP TABLE%'").effect(Effect.READ));

        // ---------------- boundary: audit, legacy baseline, fail-closed --------
        reset();
        String pB = "audit-boundary";
        Request b = approve(new Request().tool("commit_payment_batch").principal("user-1")
                .claimedScope("elevated").effectiveScope("elevated").effect(Effect.PAYMENT)
                .payload(pB), "risk-officer-1", "SUPER_SECRET_TOKEN_VALUE", pB);
        McpApprovalChainGuard.check(b);
        String trail = String.join("\n", McpApprovalChainGuard.auditTrail());
        expect("B1", !trail.contains("SUPER_SECRET_TOKEN_VALUE") && trail.contains("sha256:")
                && McpApprovalChainGuard.auditTrail().size() == 1);

        int missed = 0;
        for (Request r : ATTACKS) {
            if (McpApprovalChainGuard.legacyBaseline(r).allowed) missed++;
        }
        // a10 (replay) is registered manually; legacyMissed counts only expectDeny cases.
        expect("B2", legacyMissed >= 14 && missed == legacyMissed + 1 && missed >= 14);

        reset();
        expect("B3", !McpApprovalChainGuard.check(null).allowed);

        System.out.println("V1.81 approval-chain self-test: "
                + attack + " attack + " + control + " control + " + boundary
                + " boundary = " + (attack + control + boundary) + " assertions ALL GREEN");
        System.out.println("legacy baseline missed " + missed + "/" + attack
                + " attack cases; 0 legitimate calls mis-killed");
    }
}
