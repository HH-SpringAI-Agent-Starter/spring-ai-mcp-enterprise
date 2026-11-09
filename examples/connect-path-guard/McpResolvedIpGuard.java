package com.example.mcp.connectguard;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * MCP「连接路径」防护 · 解析后真实 IP 校验 + IP 固定（V1.67 新增，REFERENCE ONLY，不进 Maven reactor）。
 *
 * <p><b>为什么需要这一层：</b>V1.66 的 {@link McpConnectPathGuard} 只做<b>字面量</b>私网检查
 * （{@code isPrivateOrMetadataLiteral}），README 的「还可补的」第一条就是 DNS rebinding。
 * 攻击者注册 {@code attacker.example} 先解析到公网 IP 通过主机白名单/字面量校验，
 * 再把 TTL 设为 0 并在真正建连时改解析到 {@code 127.0.0.1} / {@code 169.254.169.254}，
 * 即可把「检查时是公网、连接时是内网」的时间差（TOCTOU）变成 SSRF / 云元数据窃取。
 * 这正对应 {@code CVE-2026-11624}（DNS rebinding，NIST 要求 Origin 校验为主动修复义务）、
 * {@code CVE-2026-123786}（Agenta workflow SSRF，出站白名单必须含「解析后私网/元数据地址阻断」）
 * 与 11-09 检索到的 CSDN / dev.to 治理实践「出站白名单须含 DNS 解析后真实 IP 阻断」。</p>
 *
 * <p><b>本类五条控制点：</b></p>
 * <ol>
 *   <li>{@link #resolveAndScreen(String, Function)}：解析<b>全部</b> A/AAAA 记录，
 *       任一地址命中黑名单即整次拒绝（不能只查第一个）；</li>
 *   <li>{@link #isForbidden(InetAddress)}：覆盖 loopback / RFC1918 / link-local 含云元数据
 *       169.254.169.254 / CGNAT 100.64/10 / 0.0.0.0/8 / 多播 / 保留段 /
 *       IPv6 ULA fc00::/7 / link-local fe80::/10 / IPv4-mapped IPv6（::ffff:a.b.c.d）
 *       / 6to4 与 Teredo 等；</li>
 *   <li>{@link #pin(String, InetAddress)}：把<b>校验通过的 IP 固定</b>下来交给实际连接层，
 *       连接时不再做第二次 DNS 解析 —— 这是防 rebinding 的关键，光「校验一遍」不够；</li>
 *   <li>{@link #screenUrl(String, Function)}：与 V1.66 的字符级/主机校验串联，
 *       顺序为「先字符后 scheme，再解析后 IP」；</li>
 *   <li>解析器可注入（{@code Function<String, InetAddress[]>}），使自检<b>不依赖外网</b>，
 *       且能对「一次公网一次私网」的 rebinding 场景做确定性断言。</li>
 * </ol>
 *
 * <p><b>诚实边界：</b>这是客户端/网关出站侧的参考实现，不替代服务端 Origin/Host 头校验，
 * 不处理 HTTP 重定向后的新目标（每一跳都要重新走本校验），也不解决已被攻陷的官方包。
 * 生产环境还应叠加：连接层 SNI/Host 与 pinned IP 一致、出站代理强制、重定向逐跳重校验、
 * 以及 {@code mcp-monitor} 的拒绝埋点。示例为纯 JDK，不进 reactor、零构建风险。</p>
 *
 * @see <a href="https://cloud.tencent.com/developer/article/2779058">MCP 认证与安全：授权是可选的，责任不是</a>
 * @see <a href="https://aisec.blog.csdn.net/article/details/170245168">MCP 协议安全攻防实战：DNS Rebinding 与纵深防御</a>
 */
public final class McpResolvedIpGuard {

    public record Verdict(boolean allowed, String reason) {
        public static Verdict ok() { return new Verdict(true, "OK"); }
        public static Verdict no(String reason) { return new Verdict(false, reason); }
    }

    /** 校验通过后固定下来的连接目标；连接层必须用 {@code address} 而不是再解析一次 host。 */
    public record Pin(String host, int port, InetAddress address) {}

    private McpResolvedIpGuard() {}

    /** 默认解析器：返回该主机的全部 A/AAAA 记录。 */
    public static final Function<String, InetAddress[]> JDK_RESOLVER = h -> {
        try { return InetAddress.getAllByName(h); }
        catch (UnknownHostException e) { return new InetAddress[0]; }
    };

    // ------------------------------------------------------------------
    // (1)(2) 解析全部记录并逐个筛查
    // ------------------------------------------------------------------

    public static Verdict resolveAndScreen(String host, Function<String, InetAddress[]> resolver) {
        if (host == null || host.isBlank()) return Verdict.no("EMPTY_HOST");
        String h = normalizeHost(host);
        if (h.isEmpty()) return Verdict.no("EMPTY_HOST");

        // 字面量 IP 也必须走同一套筛查，不能因为「是 IP」就跳过。
        InetAddress literal = tryParseLiteral(h);
        if (literal != null) return isForbidden(literal) ? Verdict.no("FORBIDDEN_LITERAL_" + literal.getHostAddress()) : Verdict.ok();

        InetAddress[] addrs;
        try { addrs = resolver.apply(h); }
        catch (RuntimeException e) { return Verdict.no("RESOLVER_FAILED"); }
        if (addrs == null || addrs.length == 0) return Verdict.no("NO_RESOLVED_ADDRESS");

        for (InetAddress a : addrs) {
            if (a == null) return Verdict.no("NULL_RESOLVED_ADDRESS");
            if (isForbidden(a)) return Verdict.no("FORBIDDEN_RESOLVED_" + a.getHostAddress());
        }
        return Verdict.ok();
    }

    /** 生成 pinned 目标：调用方应把它直接用于 Socket/HTTP 连接，避免第二次 DNS 解析。 */
    public static Pin pin(String host, int port, Function<String, InetAddress[]> resolver) {
        Verdict v = resolveAndScreen(host, resolver);
        if (!v.allowed()) throw new IllegalArgumentException("host rejected: " + v.reason());
        String h = normalizeHost(host);
        InetAddress literal = tryParseLiteral(h);
        if (literal != null) return new Pin(h, port, literal);
        InetAddress[] addrs = resolver.apply(h);
        // 固定第一个已通过筛查的地址；全部记录已在 resolveAndScreen 中验过。
        return new Pin(h, port, addrs[0]);
    }

    /** 与 V1.66 的字符级/主机校验串联：先字符后 scheme，再解析后 IP。 */
    public static Verdict screenUrl(String rawUrl, Set<String> allowedHosts, Function<String, InetAddress[]> resolver) {
        McpConnectPathGuard.Verdict base =
                McpConnectPathGuard.checkAuthorizationEndpoint(rawUrl, allowedHosts);
        if (!base.allowed()) return Verdict.no("UPSTREAM_" + base.reason());
        URI uri = URI.create(rawUrl);
        return resolveAndScreen(uri.getHost(), resolver);
    }

    private static String normalizeHost(String h) {
        String s = h.trim().toLowerCase(Locale.ROOT);
        // 去掉末尾点（FQDN 形式），防 attacker.example. 绕过字符串白名单。
        while (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        if (s.startsWith("[") && s.endsWith("]")) s = s.substring(1, s.length() - 1);
        return s;
    }

    private static InetAddress tryParseLiteral(String h) {
        // 只接受纯 IPv4 点分十进制或含冒号的 IPv6 字面量，避免把域名误当 IP。
        boolean looksV4 = h.matches("\\d{1,3}(\\.\\d{1,3}){3}");
        boolean looksV6 = h.contains(":");
        if (!looksV4 && !looksV6) return null;
        try {
            byte[] b = null;
            if (looksV4) {
                String[] p = h.split("\\.");
                b = new byte[4];
                for (int i = 0; i < 4; i++) {
                    int n = Integer.parseInt(p[i]);
                    if (n < 0 || n > 255) return null;
                    b[i] = (byte) n;
                }
            }
            return b != null ? InetAddress.getByAddress(b) : InetAddress.getByName(h);
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // 地址黑名单（IPv4 + IPv6，含 IPv4-mapped / 兼容 / 6to4 / Teredo）
    // ------------------------------------------------------------------

    public static boolean isForbidden(InetAddress addr) {
        if (addr == null) return true;
        if (addr.isAnyLocalAddress() || addr.isLoopbackAddress()
                || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()
                || addr.isMulticastAddress()) return true;

        byte[] b = addr.getAddress();
        if (addr instanceof Inet4Address) return forbiddenV4(b);
        if (addr instanceof Inet6Address) {
            // IPv4-mapped ::ffff:a.b.c.d 与 IPv4-compatible ::a.b.c.d：
            // 后 4 字节就是真实 IPv4，必须再过一遍 v4 规则。
            if (isV4MappedOrCompatible(b)) {
                return forbiddenV4(new byte[]{b[12], b[13], b[14], b[15]});
            }
            return forbiddenV6(b);
        }
        return true; // 未知地址族 fail-closed
    }

    private static boolean isV4MappedOrCompatible(byte[] b) {
        for (int i = 0; i < 10; i++) if (b[i] != 0) return false;
        if (b[10] == (byte) 0xff && b[11] == (byte) 0xff) return true;   // ::ffff:
        // ::a.b.c.d（兼容地址）：前 12 字节全 0 且后 4 字节非 0
        if (b[10] == 0 && b[11] == 0) return (b[12] | b[13] | b[14] | b[15]) != 0;
        return false;
    }

    private static boolean forbiddenV4(byte[] b) {
        int a = b[0] & 0xff, c = b[1] & 0xff, d = b[2] & 0xff;
        if (a == 0) return true;                                   // 0.0.0.0/8 本机/未指定
        if (a == 10 || a == 127) return true;                      // RFC1918 + loopback
        if (a == 172 && c >= 16 && c <= 31) return true;           // 172.16/12
        if (a == 192 && c == 168) return true;                     // 192.168/16
        if (a == 169 && c == 254) return true;                     // link-local，含 169.254.169.254 云元数据
        if (a == 100 && c >= 64 && c <= 127) return true;          // CGNAT 100.64/10
        if (a >= 224) return true;                                 // 多播 + 保留 240/4
        if (a == 192 && c == 0 && d == 0) return true;             // 192.0.0.0/24 IETF 协议分配
        if (a == 198 && (c == 18 || c == 19)) return true;         // 198.18/15 基准测试
        if (a == 192 && c == 0 && d == 2) return true;             // 192.0.2.0/24 文档（保守）
        return false;
    }

    private static boolean forbiddenV6(byte[] b) {
        int a = b[0] & 0xff;
        if ((a & 0xfe) == 0xfc) return true;                        // fc00::/7 ULA
        if (a == 0xfe && (b[1] & 0xc0) == 0x80) return true;       // fe80::/10 link-local
        if (a == 0xff) return true;                                 // ff00::/8 多播
        if (a == 0x20 && (b[1] & 0xff) == 0x02) return true;       // 2002::/16 6to4（内嵌 v4 可私网）
        if (a == 0x20 && (b[1] & 0xff) == 0x01 && (b[2] & 0xff) == 0x00) return true; // 2001:0000::/32 Teredo
        // ::1 已被 isLoopbackAddress 覆盖；:: 已被 isAnyLocalAddress 覆盖
        return false;
    }

    // ------------------------------------------------------------------
    // 自带对照组自检：确定性的解析器注入，不依赖外网
    // ------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        // 固定解析器：域名 -> 预置地址，让 rebinding 场景可确定性复现。
        Function<String, InetAddress[]> pub = h -> ips("93.184.216.34");
        Function<String, InetAddress[]> rebind = h -> ips("93.184.216.34", "127.0.0.1");
        Function<String, InetAddress[]> metadata = h -> ips("169.254.169.254");
        Function<String, InetAddress[]> multi = h -> ips("93.184.216.34", "10.0.0.7");
        Function<String, InetAddress[]> none = h -> new InetAddress[0];

        // --- 对照组：公网域名与公网字面量必须通过 ---
        assertTrue(resolveAndScreen("api.example.com", pub).allowed(), "control: public host must pass");
        assertTrue(resolveAndScreen("93.184.216.34", pub).allowed(), "control: public literal must pass");
        assertTrue(resolveAndScreen("2606:2800:220:1:248:1893:25c8:1946", pub).allowed(),
                   "control: global IPv6 must pass");

        // --- 攻击组：字面量私网 / 元数据 / loopback ---
        assertFalse(resolveAndScreen("127.0.0.1", pub).allowed(), "loopback literal must be rejected");
        assertFalse(resolveAndScreen("10.1.2.3", pub).allowed(), "RFC1918 literal must be rejected");
        assertFalse(resolveAndScreen("172.16.0.1", pub).allowed(), "172.16/12 must be rejected");
        assertFalse(resolveAndScreen("192.168.1.1", pub).allowed(), "192.168/16 must be rejected");
        assertFalse(resolveAndScreen("169.254.169.254", pub).allowed(), "cloud metadata must be rejected");
        assertFalse(resolveAndScreen("100.64.0.1", pub).allowed(), "CGNAT must be rejected");
        assertFalse(resolveAndScreen("0.0.0.0", pub).allowed(), "unspecified must be rejected");
        assertFalse(resolveAndScreen("::1", pub).allowed(), "IPv6 loopback must be rejected");
        assertFalse(resolveAndScreen("fc00::1", pub).allowed(), "IPv6 ULA must be rejected");
        assertFalse(resolveAndScreen("fe80::1", pub).allowed(), "IPv6 link-local must be rejected");
        assertFalse(resolveAndScreen("::ffff:127.0.0.1", pub).allowed(), "IPv4-mapped loopback must be rejected");
        assertFalse(resolveAndScreen("::ffff:169.254.169.254", pub).allowed(), "IPv4-mapped metadata must be rejected");

        // --- 攻击组：DNS rebinding / 多记录夹带 / 元数据解析结果 / 空解析 ---
        assertFalse(resolveAndScreen("rebind.example", rebind).allowed(),
                    "rebinding: any private address among A records must reject the whole host");
        assertFalse(resolveAndScreen("multi.example", multi).allowed(),
                    "multi-record smuggling must be rejected");
        assertFalse(resolveAndScreen("meta.example", metadata).allowed(),
                    "resolved metadata address must be rejected");
        assertFalse(resolveAndScreen("nx.example", none).allowed(),
                    "empty resolution must fail-closed");

        // --- 序列化：与 V1.66 字符级校验串联 ---
        assertFalse(screenUrl("https://evil.test/$(whoami)", Set.of(), pub).allowed(),
                    "shell payload must be rejected before DNS");
        assertFalse(screenUrl("https://rebind.example/oauth", Set.of("rebind.example"), rebind).allowed(),
                    "allowlisted host that rebinds to loopback must still be rejected");
        assertTrue(screenUrl("https://auth.example.com/o?a=b&c=d", Set.of("auth.example.com"), pub).allowed(),
                   "control: legitimate allowlisted URL must pass");

        // --- IP 固定：pin 返回的必须是已校验的地址，连接层不再解析 ---
        Pin p = pin("api.example.com", 443, pub);
        assertEquals("93.184.216.34", p.address().getHostAddress(), "pin must carry the screened address");
        assertEquals(443, p.port(), "pin port");
        boolean threw = false;
        try { pin("rebind.example", 443, rebind); } catch (IllegalArgumentException e) { threw = true; }
        assertTrue(threw, "pin must refuse a rebinding host");

        System.out.println("[McpResolvedIpGuard] self-test OK (16 attack cases + 5 control cases)");
        System.out.println("pin = " + p.host() + ":" + p.port() + " -> " + p.address().getHostAddress());
    }

    private static InetAddress[] ips(String... literals) {
        Set<InetAddress> out = new LinkedHashSet<>();
        for (String s : literals) {
            InetAddress a = tryParseLiteral(s.toLowerCase(Locale.ROOT));
            if (a == null) throw new IllegalArgumentException("bad test literal: " + s);
            out.add(a);
        }
        return out.toArray(new InetAddress[0]);
    }

    private static void assertTrue(boolean b, String msg) { if (!b) throw new AssertionError("FAIL: " + msg); }
    private static void assertFalse(boolean b, String msg) { if (b) throw new AssertionError("FAIL: " + msg); }
    private static void assertEquals(String a, String b, String msg) { if (!a.equals(b)) throw new AssertionError("FAIL: " + msg); }
    private static void assertEquals(int a, int b, String msg) { if (a != b) throw new AssertionError("FAIL: " + msg); }
}
