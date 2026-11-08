package com.example.mcp.connectguard;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * MCP「连接路径」防护参考实现（V1.66 新增，REFERENCE ONLY，不进 Maven reactor）。
 *
 * <p><b>为什么需要这一层：</b>2026-11-05 Pillar Security 公开的
 * {@code CVE-2026-119025}（MCP Inspector，CVSS 4.0 7.7 / GHSA-q978-78c7-phqw 3.1 8.8）
 * 证明了一类工具级 guardrail **根本看不见**的攻击：
 * 攻击者控制的远程 MCP server 在 OAuth discovery 的 {@code authorization_endpoint}
 * 里返回 {@code https://evil.test/$([IO.File]::WriteAllText(...))}，客户端自动打开浏览器时
 * npm {@code open} 10.x 在 Windows/WSL 上把 URL 塞进 PowerShell 双引号并 base64 后
 * {@code -EncodedCommand} 执行 —— PowerShell 在双引号里求值 {@code $(...)}。</p>
 *
 * <p>全过程：<b>零工具调用、零审批提示、零模型参与</b>，注入发生在比 agent 低一层的
 * 连接路径。而且 URL 是合法的 {@code https://}，所以「只校验 scheme」完全挡不住
 * （真正修好的是 {@code open} 11 的参数转义 + 单引号，以及 argv 形状）。</p>
 *
 * <p>本类给出四条可独立验收的控制点：</p>
 * <ol>
 *   <li>{@link #checkAuthorizationEndpoint(String, Set)}：发现文档里的 URL 做字符级 + 主机白名单校验；</li>
 *   <li>{@link #checkDiscoveryDocument(byte[])}：发现文档大小上限（对齐 4 MiB 口径，防无界响应体）；</li>
 *   <li>{@link #browserArgs(String, String)}：浏览器启动一律用 argument vector，
 *       绝不把服务端给的字符串交给 shell / PowerShell；</li>
 *   <li>{@link #enforceVersionFloor(String, String, String)} +
 *       {@link #autoOpenAllowed(boolean, boolean)}：远程目标默认 opt-in，
 *       且客户端工具强制版本下限 —— 因为 11-06 的一周五洞复盘显示
 *       {@code opencode} 的同类 RCE 是「无 CVE 的 refactor 修复」，
 *       Dependabot / SBOM 一个都报不出来。</li>
 * </ol>
 *
 * <p><b>诚实边界：</b>这是客户端侧防护，与服务端 {@code McpGatewayGuardFilter} 互补而非替代；
 * 它不解决已被攻陷的官方包、也不做 DNS rebinding 的完整防护（需配合解析后私网地址阻断）。
 * 研究者的 Windows/WSL 可达性来自代码审阅，尚无在野利用报告。</p>
 *
 * @see <a href="https://dev.to/piekwerk/mcp-inspectors-oauth-flow-could-run-powershell-on-windows-inside-cve-2026-119025-and-why-npm-1hei">CVE-2026-119025 分析</a>
 */
public final class McpConnectPathGuard {

    public static final int MAX_DISCOVERY_BYTES = 64 * 1024;
    private static final Set<String> SAFE_SCHEMES = Set.of("http", "https");

    /**
     * PowerShell 双引号字符串里会被求值 / 转义的字符。
     * 注意：{@code &}、{@code ?}、{@code =}、{@code [ ]} 是合法 URL 的常见组成部分，
     * 不在此列 —— 因为本方案用 argv 启动浏览器，URL 根本不经过 shell；
     * 只对「一旦被拼进 PowerShell 就会出事」的字符 fail-closed。
     */
    private static final String FORBIDDEN_CHARS = "$`|;<>()\\\"'\n\r\t";

    private McpConnectPathGuard() {}

    public record Verdict(boolean allowed, String reason) {
        public static Verdict ok() { return new Verdict(true, "OK"); }
        public static Verdict no(String reason) { return new Verdict(false, reason); }
    }

    // ------------------------------------------------------------------
    // (1) discovery 中的 authorization_endpoint
    // ------------------------------------------------------------------

    public static Verdict checkAuthorizationEndpoint(String rawUrl, Set<String> allowedHosts) {
        if (rawUrl == null || rawUrl.isBlank()) return Verdict.no("EMPTY_URL");
        if (rawUrl.length() > 2048) return Verdict.no("URL_TOO_LONG");

        // 关键：先做字符级校验，再做 scheme 校验。只校验 scheme 会放过 $(...) 载荷。
        for (int i = 0; i < rawUrl.length(); i++) {
            char c = rawUrl.charAt(i);
            if (FORBIDDEN_CHARS.indexOf(c) >= 0 || Character.isISOControl(c)) {
                return Verdict.no("SHELL_METACHAR_AT_" + i);
            }
        }
        if (rawUrl.contains("%24") || rawUrl.contains("%28") || rawUrl.contains("%60")) {
            return Verdict.no("ENCODED_SHELL_METACHAR");
        }

        final URI uri;
        try {
            uri = URI.create(rawUrl);
        } catch (IllegalArgumentException e) {
            return Verdict.no("MALFORMED_URI");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!SAFE_SCHEMES.contains(scheme)) return Verdict.no("BAD_SCHEME_" + scheme);
        if (uri.getRawUserInfo() != null) return Verdict.no("USERINFO_NOT_ALLOWED");
        String host = uri.getHost();
        if (host == null || host.isBlank()) return Verdict.no("NO_HOST");
        if (isPrivateOrMetadataLiteral(host)) return Verdict.no("PRIVATE_OR_METADATA_HOST");
        if (allowedHosts != null && !allowedHosts.isEmpty() && !allowedHosts.contains(host.toLowerCase(Locale.ROOT))) {
            return Verdict.no("HOST_NOT_ALLOWLISTED");
        }
        return Verdict.ok();
    }

    /** 字面量检查（不触发 DNS）；生产环境还须在解析后再次校验真实 IP，防 DNS rebinding。 */
    private static boolean isPrivateOrMetadataLiteral(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        if (h.equals("localhost") || h.endsWith(".localhost") || h.endsWith(".internal")) return true;
        if (h.equals("169.254.169.254") || h.startsWith("metadata.")) return true;
        String[] parts = h.split("\\.");
        if (parts.length != 4) return false;
        try {
            int a = Integer.parseInt(parts[0]), b = Integer.parseInt(parts[1]);
            return a == 10 || a == 127 || (a == 192 && b == 168) || (a == 172 && b >= 16 && b <= 31)
                || (a == 169 && b == 254) || a == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // (2) 发现文档大小上限
    // ------------------------------------------------------------------

    public static Verdict checkDiscoveryDocument(byte[] doc) {
        if (doc == null) return Verdict.no("EMPTY_DOCUMENT");
        if (doc.length > MAX_DISCOVERY_BYTES) return Verdict.no("DISCOVERY_TOO_LARGE");
        String s = new String(doc, StandardCharsets.UTF_8);
        if (s.contains("$(") || s.contains("-EncodedCommand") || s.contains("`")) {
            return Verdict.no("SHELL_PAYLOAD_IN_DOCUMENT");
        }
        return Verdict.ok();
    }

    // ------------------------------------------------------------------
    // (3) 浏览器启动：argv，不是 shell 字符串
    // ------------------------------------------------------------------

    /**
     * 返回可直接交给 {@code new ProcessBuilder(argv).start()} 的参数向量。
     * 注意：Windows 上不得把 URL 拼进 {@code powershell -EncodedCommand ...}；
     * 任何由被审查 server 提供的字符串都不应到达 shell 解释器。
     */
    public static List<String> browserArgs(String browserExecutable, String url) {
        if (browserExecutable == null || !browserExecutable.matches("[A-Za-z0-9_./\\\\: -]+")) {
            throw new IllegalArgumentException("browser executable must be a fixed, operator-configured path");
        }
        Verdict v = checkAuthorizationEndpoint(url, null);
        if (!v.allowed()) throw new IllegalArgumentException("url rejected: " + v.reason());
        List<String> argv = new ArrayList<>();
        argv.add(browserExecutable);
        argv.add("--");           // 阻止后续参数被当作浏览器选项
        argv.add(url);            // 单个 argv 元素，不经过 shell 求值
        return List.copyOf(argv);
    }

    // ------------------------------------------------------------------
    // (4) 自动打开策略 + 无 CVE 修复的版本下限
    // ------------------------------------------------------------------

    /** 远程目标默认不自动打开；必须由操作者显式 opt-in。 */
    public static boolean autoOpenAllowed(boolean remoteTarget, boolean operatorOptIn) {
        return !remoteTarget || operatorOptIn;
    }

    /** 比较点分数字版本；返回 true 表示 {@code current >= floor}。 */
    public static boolean enforceVersionFloor(String tool, String current, String floor) {
        if (tool == null || current == null || floor == null) return false;
        int[] c = parse(current), f = parse(floor);
        if (c == null || f == null) return false;
        for (int i = 0; i < Math.max(c.length, f.length); i++) {
            int cv = i < c.length ? c[i] : 0, fv = i < f.length ? f[i] : 0;
            if (cv > fv) return true;
            if (cv < fv) return false;
        }
        return true;
    }

    private static int[] parse(String v) {
        String[] p = v.replaceAll("^v", "").split("\\.");
        int[] out = new int[p.length];
        for (int i = 0; i < p.length; i++) {
            try { out[i] = Integer.parseInt(p[i].replaceAll("[^0-9].*$", "")); }
            catch (NumberFormatException e) { return null; }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // 自带对照组自检：能挡住真实载荷，也不能误杀正常 https URL
    // ------------------------------------------------------------------

    public static void main(String[] args) {
        String legit = "https://auth.example.com/oauth/authorize?client_id=abc&state=xyz";
        String payload = "https://evil.test/$([IO.File]::WriteAllText('C:/Users/Public/pwned.txt','RCE'))";

        assertTrue(checkAuthorizationEndpoint(legit, Set.of("auth.example.com")).allowed(),
                   "对照组：正常 https URL 必须通过");
        assertFalse(checkAuthorizationEndpoint(payload, null).allowed(),
                    "真实 $(...) 载荷必须被拒（scheme 校验挡不住）");
        assertFalse(checkAuthorizationEndpoint("https://169.254.169.254/latest/meta-data", null).allowed(),
                    "云元数据地址必须被拒");
        assertFalse(checkAuthorizationEndpoint("https://a:b@evil.test/auth", null).allowed(),
                    "userinfo 必须被拒");
        assertFalse(checkAuthorizationEndpoint(legit, Set.of("other.example.com")).allowed(),
                    "非白名单主机必须被拒");

        assertFalse(checkDiscoveryDocument(new byte[MAX_DISCOVERY_BYTES + 1]).allowed(),
                    "超大发现文档必须被拒");
        assertTrue(checkDiscoveryDocument(legit.getBytes(StandardCharsets.UTF_8)).allowed(),
                   "对照组：正常发现文档必须通过");

        List<String> argv = browserArgs("C:/Program Files/browser/browser.exe", legit);
        assertEquals(3, argv.size(), "argv 长度");
        assertTrue(argv.get(2).equals(legit), "URL 必须作为单个 argv 元素");
        assertFalse(String.join(" ", argv).contains("powershell"), "argv 中不得出现 shell");

        assertFalse(autoOpenAllowed(true, false), "远程目标默认禁止自动打开");
        assertTrue(autoOpenAllowed(true, true), "显式 opt-in 后允许");
        assertTrue(enforceVersionFloor("inspector", "2.14.0", "2.12.0"), "2.14.0 应满足 2.12.0 下限");
        assertFalse(enforceVersionFloor("inspector", "2.11.9", "2.12.0"), "2.11.9 必须被版本下限拦下");

        System.out.println("[McpConnectPathGuard] self-test OK (6 attack cases + 3 control cases)");
        System.out.println("argv = " + argv);
    }

    private static void assertTrue(boolean b, String msg) { if (!b) throw new AssertionError("FAIL: " + msg); }
    private static void assertFalse(boolean b, String msg) { if (b) throw new AssertionError("FAIL: " + msg); }
    private static void assertEquals(int a, int b, String msg) { if (a != b) throw new AssertionError("FAIL: " + msg); }
}
