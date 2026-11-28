package examples.sharedtransportguard;

import java.util.ArrayList;
import java.util.List;

import examples.sharedtransportguard.McpSharedTransportGuard.Effect;
import examples.sharedtransportguard.McpSharedTransportGuard.Request;
import examples.sharedtransportguard.McpSharedTransportGuard.TransportMode;
import examples.sharedtransportguard.McpSharedTransportGuard.Verdict;

/** Executable acceptance suite for V1.82 shared-transport / cross-client isolation gate. */
public final class SharedTransportGuardSelfTest {

    private static int attack = 0, control = 0, boundary = 0;
    private static int legacyMissed = 0;
    private static final List<Request> ATTACKS = new ArrayList<>();

    private static void reset() { McpSharedTransportGuard.resetState(); }

    /** A well-formed HTTP request; a caller needs an auth token but NOT a shared transport. */
    private static Request base(String transport, String principal) {
        return new Request().tool("search_records").principal(principal)
                .mode(TransportMode.HTTP_STATELESS).transportId(transport)
                .jsonrpcId(0).sessionId("sess-" + principal).sessionPrincipal(principal)
                .route("/mcp").bindAddress("127.0.0.1").authToken("tok-" + principal)
                .claimedScope("read").readOnlyHint(true).effect(Effect.READ)
                .payload("q");
    }

    private static void recordLegacy(Request r) {
        if (McpSharedTransportGuard.legacyBaseline(r).allowed) legacyMissed++;
    }

    private static void expectDeny(String id, Request r, String cp) {
        reset();
        Verdict v = McpSharedTransportGuard.check(r);
        if (v.allowed || !v.control.equals(cp)) {
            throw new AssertionError(id + " expected DENY@" + cp + " but got " + v);
        }
        recordLegacy(r);
        ATTACKS.add(r);
        attack++;
    }

    /** Deny case that needs a prior client to have attached the same transport instance. */
    private static void expectDenyAttached(String id, String transport, String prior, Request r, String cp) {
        reset();
        McpSharedTransportGuard.preAttach(transport, prior);
        Verdict v = McpSharedTransportGuard.check(r);
        if (v.allowed || !v.control.equals(cp)) {
            throw new AssertionError(id + " expected DENY@" + cp + " but got " + v);
        }
        recordLegacy(r);
        ATTACKS.add(r);
        attack++;
    }

    private static void expectAllow(String id, Request r) {
        reset();
        Verdict v = McpSharedTransportGuard.check(r);
        if (!v.allowed) throw new AssertionError(id + " expected ALLOW but got " + v);
        control++;
    }

    private static void expect(String id, boolean ok) {
        if (!ok) throw new AssertionError(id + " failed");
        boundary++;
    }

    public static void main(String[] args) throws Exception {
        // ---------- attacks: shared transport / id-only / mgmt route / bind / key ----

        // CP1 - one transport instance reused across clients (CVE-2026-111770).
        expectDenyAttached("A1", "t1", "alice", base("t1", "bob"), "CP1");
        expectDenyAttached("A2", "t2", "alice",
                base("t2", "bob").mode(TransportMode.HTTP_STATEFUL), "CP1");
        expectDenyAttached("A3", "t3", "alice", base("t3", "charlie"), "CP1");
        expectDenyAttached("A4", "t4", "alice",
                base("t4", "bob").claimedScope("admin"), "CP1");
        expectDenyAttached("A5", "t5", "alice",
                base("t5", "bob").readOnlyHint(true), "CP1");
        expectDenyAttached("A6", "t6", "alice",
                base("t6", "bob").jsonrpcId(0), "CP1");

        // CP2 - id-only message without session ownership, or session bound elsewhere.
        expectDeny("A7", base("t7", "alice").sessionId(null), "CP2");
        expectDeny("A8", base("t8", "alice").sessionId(""), "CP2");
        expectDeny("A9", base("t9", "alice").sessionPrincipal("bob"), "CP2");
        expectDeny("A10", base("t10", "alice").sessionPrincipal(null), "CP2");
        expectDeny("A11", base("t11", "alice").jsonrpcId(0).sessionPrincipal("bob"), "CP2");

        // CP3 - management routes need an independent admin token (Jupyter 77318).
        expectDeny("A12", base("t12", "alice").route("/api/connect").effect(Effect.MANAGE), "CP3");
        expectDeny("A13", base("t13", "alice").route("/api/stop").effect(Effect.MANAGE), "CP3");
        expectDeny("A14", base("t14", "alice").route("/api/servers").effect(Effect.MANAGE).adminToken(""), "CP3");
        expectDeny("A15", base("t15", "alice").route("/api/connect").effect(Effect.READ).adminToken("admin-x"), "CP3");

        // CP4 - non-loopback bind without auth (Crypto APIs 138026, 0.0.0.0).
        expectDeny("A16", base("t16", "alice").bindAddress("0.0.0.0").authToken(null), "CP4");
        expectDeny("A17", base("t17", "alice").bindAddress("192.168.1.100").authToken(""), "CP4");
        expectDeny("A18", base("t18", "alice").bindAddress("0.0.0.0").authToken(null).claimedScope("read"), "CP4");

        // CP5 - an operator/another's key spent by the caller.
        expectDeny("A19", base("t19", "alice").credentialRef("operator-key").credentialOwner("operator"), "CP5");
        expectDeny("A20", base("t20", "bob").credentialRef("alice-key").credentialOwner("alice"), "CP5");

        // ---------- controls: legitimate calls must not be killed --------
        // C1 - STDIO is single-client; no session/transport-sharing exposure.
        expectAllow("C1", new Request().tool("search_records").principal("cli")
                .mode(TransportMode.STDIO).transportId("stdio-1").jsonrpcId(0)
                .effect(Effect.READ).payload("q"));

        // C2 - each request gets its own transport + session binding.
        expectAllow("C2", base("t-c2", "alice"));

        // C3 - stateful HTTP with correct session binding.
        expectAllow("C3", base("t-c3", "alice").mode(TransportMode.HTTP_STATEFUL));

        // C4 - management route WITH an independent admin token.
        expectAllow("C4", base("t-c4", "alice").route("/api/connect")
                .effect(Effect.MANAGE).adminToken("admin-good"));

        // C5 - loopback-by-name bind with auth token.
        expectAllow("C5", base("t-c5", "alice").bindAddress("localhost"));

        // C6 - a false admin claim and readOnlyHint must NOT change the verdict.
        expectAllow("C6", base("t-c6", "alice").claimedScope("ADMIN").readOnlyHint(true));

        // C7 - credential spent by its actual owner.
        expectAllow("C7", base("t-c7", "alice").credentialRef("alice-key").credentialOwner("ALICE"));

        // C8 - payload containing SQL-looking text on a read tool is not this gate's concern.
        expectAllow("C8", base("t-c8", "alice").payload("notes LIKE '%DROP TABLE%'"));

        // C9 - same principal reusing its OWN transport is allowed (not cross-client).
        reset();
        McpSharedTransportGuard.preAttach("t-c9", "alice");
        if (!McpSharedTransportGuard.check(base("t-c9", "alice")).allowed) {
            throw new AssertionError("C9 expected ALLOW for same-principal reuse");
        }
        control++;

        // ---------- boundary: audit redaction, legacy baseline, fail-closed --------
        reset();
        Request b = base("t-b1", "alice").credentialRef("SUPER_SECRET_CRED_VALUE")
                .credentialOwner("alice");
        McpSharedTransportGuard.check(b);
        String trail = String.join("\n", McpSharedTransportGuard.auditTrail());
        expect("B1", !trail.contains("SUPER_SECRET_CRED_VALUE") && trail.contains("sha256:")
                && McpSharedTransportGuard.auditTrail().size() == 1);

        int missed = 0;
        for (Request r : ATTACKS) {
            if (McpSharedTransportGuard.legacyBaseline(r).allowed) missed++;
        }
        expect("B2", missed == legacyMissed && missed >= 17);

        reset();
        expect("B3", !McpSharedTransportGuard.check(null).allowed);

        System.out.println("V1.82 shared-transport self-test: "
                + attack + " attack + " + control + " control + " + boundary
                + " boundary = " + (attack + control + boundary) + " assertions ALL GREEN");
        System.out.println("legacy baseline (transport + token only) missed " + missed + "/" + attack
                + " attack cases; " + control + " legitimate calls, 0 mis-killed");
    }
}
