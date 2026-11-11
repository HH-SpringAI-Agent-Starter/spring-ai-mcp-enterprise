package com.example.mcp.transport;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * MCP 本地 HTTP 监听器「绑定地址 + Content-Type / Origin / Host 准入」参考实现（V1.69）。
 *
 * <p>状态：REFERENCE ONLY，纯 JDK 零依赖，不进 Maven reactor，零构建风险。
 *
 * <p>对位 2026-11-09~11-11 实证：
 * <ul>
 *   <li><b>Siemens SSA-710379（2026-11-10）</b>：Mendix Studio Pro 内置 MCP Server
 *       `CVE-2026-112136` CWE-1327 未把 HTTP listener 限制在 loopback（CVSS 3.1 8.8 /
 *       CVSS 4.0 9.2），`CVE-2026-112137` CWE-352 未校验 Content-Type 与 Origin
 *       （5.4 / 5.1）；官方缓解第一条即「主机防火墙阻断 7782 外部流量」。</li>
 *   <li><b>`CVE-2026-123811`</b> Dynatrace MCP Server `--http` 无鉴权 / 无会话校验 /
 *       无 Host+Origin 防护，浏览器经 DNS rebinding 直接调 `execute_dql`。</li>
 *   <li><b>Adversa 2026 年 11 月 MCP 安全榜</b>：`@modelcontextprotocol/server-everything`
 *       绑定所有网卡 + wildcard CORS + 无鉴权 + `get-env` 返回全部 process.env（修 2.0.1）；
 *       Basic Memory SSE 缺 Host/Origin 校验被 DNS rebinding 打穿，FastMCP 的
 *       `HostOriginGuardMiddleware` 默认关闭（升 0.24.0）。</li>
 * </ul>
 *
 * <p>立场：把「绑在 127.0.0.1」当成安全边界是不够的 —— 浏览器标签页能打到 localhost。
 * 必须同时做四件事：只绑 loopback、非 loopback 显式 opt-in 且强制鉴权、
 * Content-Type 严格 application/json、Origin/Host 允许列表 + 禁 wildcard CORS。
 *
 * <p>自检运行：{@code java -Dfile.encoding=UTF-8 com.example.mcp.transport.McpLocalListenerGuard}
 */
public final class McpLocalListenerGuard {

    public static final class Verdict {
        public final boolean allowed;
        public final String reasonCode;
        Verdict(boolean allowed, String reasonCode) {
            this.allowed = allowed; this.reasonCode = reasonCode;
        }
        static Verdict allow(String c) { return new Verdict(true, c); }
        static Verdict deny(String c) { return new Verdict(false, c); }
        @Override public String toString() {
            return (allowed ? "ALLOW" : "DENY") + "(" + reasonCode + ")";
        }
    }

    /** 监听器配置（不可变；修改请走 withXxx 生成新实例）。 */
    public static final class ListenerConfig {
        public final String bindAddress;
        public final boolean authenticated;
        public final boolean remoteOptIn;
        public final boolean browserFacing;
        public final Set<String> allowedOrigins;
        public final Set<String> allowedHosts;

        public ListenerConfig(String bindAddress, boolean authenticated, boolean remoteOptIn,
                              boolean browserFacing, Set<String> allowedOrigins,
                              Set<String> allowedHosts) {
            this.bindAddress = bindAddress;
            this.authenticated = authenticated;
            this.remoteOptIn = remoteOptIn;
            this.browserFacing = browserFacing;
            this.allowedOrigins = Collections.unmodifiableSet(new LinkedHashSet<>(allowedOrigins));
            this.allowedHosts = Collections.unmodifiableSet(new LinkedHashSet<>(allowedHosts));
        }

        /** 默认配置：只绑 loopback、显式鉴权、浏览器面向、白名单只有本地。 */
        public static ListenerConfig defaults() {
            return new ListenerConfig("127.0.0.1", true, false, true,
                    new LinkedHashSet<>(Arrays.asList("http://localhost:7782",
                            "http://127.0.0.1:7782")),
                    new LinkedHashSet<>(Arrays.asList("localhost:7782", "127.0.0.1:7782")));
        }

        public ListenerConfig withBind(String a) {
            return new ListenerConfig(a, authenticated, remoteOptIn, browserFacing,
                    allowedOrigins, allowedHosts);
        }
        public ListenerConfig withAuthenticated(boolean a) {
            return new ListenerConfig(bindAddress, a, remoteOptIn, browserFacing,
                    allowedOrigins, allowedHosts);
        }
        public ListenerConfig withRemoteOptIn(boolean a) {
            return new ListenerConfig(bindAddress, authenticated, a, browserFacing,
                    allowedOrigins, allowedHosts);
        }
        public ListenerConfig withBrowserFacing(boolean a) {
            return new ListenerConfig(bindAddress, authenticated, remoteOptIn, a,
                    allowedOrigins, allowedHosts);
        }
    }

    /** 一次进来的 HTTP 请求（字段为 null 表示头缺失）。 */
    public static final class HttpRequest {
        public final String contentType;
        public final String origin;
        public final String host;
        public final String forwardedHost;
        public final String corsAllowOrigin;
        public final String toolName;

        public HttpRequest(String contentType, String origin, String host,
                           String forwardedHost, String corsAllowOrigin, String toolName) {
            this.contentType = contentType;
            this.origin = origin;
            this.host = host;
            this.forwardedHost = forwardedHost;
            this.corsAllowOrigin = corsAllowOrigin;
            this.toolName = toolName == null ? "" : toolName;
        }

        /** 正常浏览器侧 Streamable HTTP 调用。 */
        public static HttpRequest normal(String toolName) {
            return new HttpRequest("application/json", "http://localhost:7782",
                    "localhost:7782", null, null, toolName);
        }
    }

    private static final Pattern LOOPBACK = Pattern.compile(
            "(?i)^(127\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|localhost|::1|0:0:0:0:0:0:0:1)$");

    private static final Pattern WILDCARD_BIND = Pattern.compile("^(0\\.0\\.0\\.0|::|\\*|)$");

    private static final Pattern REBIND_SUFFIX = Pattern.compile(
            "(?i).*\\.(nip\\.io|localtest\\.me|sslip\\.io|lvh\\.me)$");

    private static final Pattern ENV_DUMP = Pattern.compile(
            "(?i)^(get[-_]?env|dump[-_]?env|read[-_]?process[-_]?env)$");

    public static boolean isLoopback(String address) {
        return address != null && LOOPBACK.matcher(address.trim()).matches();
    }

    /** 启动期准入：对位 `CVE-2026-112136` / `CVE-2026-102610` / `123811` 的绑定缺口。 */
    public static Verdict checkStart(ListenerConfig cfg) {
        String bind = cfg.bindAddress == null ? "" : cfg.bindAddress.trim();
        if (WILDCARD_BIND.matcher(bind).matches()) {
            return Verdict.deny("WILDCARD_OR_EMPTY_BIND");
        }
        if (isLoopback(bind)) {
            return Verdict.allow("LOOPBACK_ONLY");
        }
        if (!cfg.remoteOptIn) return Verdict.deny("REMOTE_BIND_NOT_OPTED_IN");
        if (!cfg.authenticated) return Verdict.deny("REMOTE_BIND_WITHOUT_AUTH");
        return Verdict.allow("REMOTE_BIND_OPTED_IN_WITH_AUTH");
    }

    /** 请求期准入：对位 `CVE-2026-112137` / `123811` / server-everything / Basic Memory。 */
    public static Verdict checkRequest(ListenerConfig cfg, HttpRequest req) {
        // 1) 鉴权与会话：未鉴权一律 401，不接受「本地所以可信」
        if (!cfg.authenticated) return Verdict.deny("UNAUTHENTICATED_CALL");

        // 2) Content-Type：缺失或非 application/json 一律拒（CSRF 用 text/plain / form 绕过预检）
        if (req.contentType == null || req.contentType.trim().isEmpty()) {
            return Verdict.deny("MISSING_CONTENT_TYPE");
        }
        String media = req.contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (!"application/json".equals(media)) {
            return Verdict.deny("CONTENT_TYPE_NOT_JSON");
        }

        // 3) Origin：浏览器面向时必须有且命中白名单；"null" 与通配符都拒
        if (req.origin != null) {
            if ("null".equalsIgnoreCase(req.origin.trim())) return Verdict.deny("NULL_ORIGIN");
            if (!cfg.allowedOrigins.contains(req.origin)) return Verdict.deny("ORIGIN_NOT_ALLOWED");
        } else if (cfg.browserFacing) {
            return Verdict.deny("MISSING_ORIGIN_BROWSER");
        }

        // 4) Host / X-Forwarded-Host：DNS rebinding 与 host-header 绕过
        if (req.host == null || req.host.trim().isEmpty()) return Verdict.deny("MISSING_HOST");
        if (REBIND_SUFFIX.matcher(req.host).matches()) return Verdict.deny("REBINDING_HOST_SUFFIX");
        if (!cfg.allowedHosts.contains(req.host)) return Verdict.deny("HOST_NOT_ALLOWED");
        if (req.forwardedHost != null && !req.forwardedHost.equals(req.host)) {
            return Verdict.deny("FORWARDED_HOST_MISMATCH");
        }

        // 5) 响应侧 CORS：通配符 + 无鉴权是 server-everything 被跨域读走环境变量的关键
        if (req.corsAllowOrigin != null && req.corsAllowOrigin.trim().equals("*")) {
            return Verdict.deny("WILDCARD_CORS");
        }

        // 6) 整包环境变量读取工具默认禁用
        if (ENV_DUMP.matcher(req.toolName).matches()) return Verdict.deny("ENV_DUMP_TOOL_DISABLED");

        return Verdict.allow("ALLOWED");
    }

    // ---------------------------------------------------------------- 自检

    private static int attack = 0, control = 0, boundary = 0, checks = 0;

    private static void expectDeny(String label, Verdict v) {
        checks++;
        if (v.allowed) throw new AssertionError(label + " expected DENY but got " + v);
        attack++;
    }
    private static void expectAllow(String label, Verdict v) {
        checks++;
        if (!v.allowed) throw new AssertionError(label + " expected ALLOW but got " + v);
        control++;
    }
    private static void expectControl(String label, boolean b) {
        checks++;
        if (!b) throw new AssertionError(label + " expected true (control group)");
        control++;
    }
    private static void expectBoundary(String label, boolean b) {
        checks++;
        if (!b) throw new AssertionError(label + " expected true (boundary)");
        boundary++;
    }

    public static void main(String[] args) {
        ListenerConfig base = ListenerConfig.defaults();

        // ---------- 启动期攻击 ----------
        expectDeny("A1 绑 0.0.0.0", checkStart(base.withBind("0.0.0.0")));
        expectDeny("A2 绑 :: (IPv6 通配)", checkStart(base.withBind("::")));
        expectDeny("A3 绑局域网地址但无 opt-in", checkStart(base.withBind("192.168.1.10")));
        expectDeny("A4 opt-in 但未鉴权", checkStart(
                base.withBind("192.168.1.10").withRemoteOptIn(true).withAuthenticated(false)));

        // ---------- 请求期攻击 ----------
        expectDeny("A5 未鉴权 tools/call", checkRequest(base.withAuthenticated(false),
                HttpRequest.normal("execute_dql")));
        expectDeny("A6 text/plain 绕过预检", checkRequest(base,
                new HttpRequest("text/plain", "http://localhost:7782", "localhost:7782",
                        null, null, "execute_dql")));
        expectDeny("A7 form 编码绕过预检", checkRequest(base,
                new HttpRequest("application/x-www-form-urlencoded", "http://localhost:7782",
                        "localhost:7782", null, null, "execute_dql")));
        expectDeny("A8 缺失 Content-Type", checkRequest(base,
                new HttpRequest(null, "http://localhost:7782", "localhost:7782",
                        null, null, "execute_dql")));
        expectDeny("A9 恶意页面 Origin", checkRequest(base,
                new HttpRequest("application/json", "https://evil.example", "localhost:7782",
                        null, null, "execute_dql")));
        expectDeny("A10 Origin: null", checkRequest(base,
                new HttpRequest("application/json", "null", "localhost:7782",
                        null, null, "execute_dql")));
        expectDeny("A11 浏览器请求缺 Origin", checkRequest(base,
                new HttpRequest("application/json", null, "localhost:7782",
                        null, null, "execute_dql")));
        expectDeny("A12 DNS rebinding Host", checkRequest(base,
                new HttpRequest("application/json", "http://localhost:7782",
                        "127.0.0.1.nip.io", null, null, "execute_dql")));
        expectDeny("A13 伪造 Host 非白名单", checkRequest(base,
                new HttpRequest("application/json", "http://localhost:7782",
                        "attacker.example:7782", null, null, "execute_dql")));
        expectDeny("A14 X-Forwarded-Host 不一致", checkRequest(base,
                new HttpRequest("application/json", "http://localhost:7782",
                        "localhost:7782", "attacker.example", null, "execute_dql")));
        expectDeny("A15 缺失 Host", checkRequest(base,
                new HttpRequest("application/json", "http://localhost:7782",
                        "", null, null, "execute_dql")));
        expectDeny("A16 wildcard CORS", checkRequest(base,
                new HttpRequest("application/json", "http://localhost:7782",
                        "localhost:7782", null, "*", "execute_dql")));
        expectDeny("A17 get-env 整包环境变量工具", checkRequest(base,
                HttpRequest.normal("get-env")));

        // ---------- 对照组：合法流量不得误杀 ----------
        expectAllow("C1 默认 loopback 启动", checkStart(base));
        expectAllow("C2 正常 Streamable HTTP 调用", checkRequest(base,
                HttpRequest.normal("execute_dql")));
        expectAllow("C3 application/json; charset=utf-8", checkRequest(base,
                new HttpRequest("application/json; charset=utf-8", "http://localhost:7782",
                        "localhost:7782", null, null, "search_docs")));
        expectAllow("C4 本地 CLI（非浏览器）可无 Origin", checkRequest(
                base.withBrowserFacing(false),
                new HttpRequest("application/json", null, "127.0.0.1:7782",
                        null, null, "search_docs")));
        expectAllow("C5 显式 opt-in + 鉴权后可绑非 loopback", checkStart(
                base.withBind("10.0.0.5").withRemoteOptIn(true).withAuthenticated(true)));
        expectAllow("C6 127.x 回环地址", checkStart(base.withBind("127.0.0.2")));
        expectAllow("C7 允许的第二个 Origin", checkRequest(base,
                new HttpRequest("application/json", "http://127.0.0.1:7782",
                        "127.0.0.1:7782", null, null, "list_tools")));

        // 「只看是否绑 127.0.0.1」的旧基线对照：
        // 旧基线会放行的「loopback + text/plain + 无 Origin」浏览器请求，新方案必须拦（增量价值）；
        // 旧基线会放行的正常请求，新方案也必须放行（不得劣于基线）。
        HttpRequest csrf = new HttpRequest("text/plain", null, "localhost:7782",
                null, null, "execute_dql");
        expectControl("C8 旧基线漏掉的 CSRF 组合被新方案拦下",
                legacyAllows(base.bindAddress) && !checkRequest(base, csrf).allowed);
        expectControl("C9 旧基线放行的正常请求新方案同样放行",
                legacyAllows(base.bindAddress) && checkRequest(base,
                        HttpRequest.normal("search_docs")).allowed);

        // ---------- 边界断言 ----------
        expectBoundary("B1 默认配置是 loopback 且已鉴权", isLoopback(base.bindAddress)
                && base.authenticated && !base.remoteOptIn);
        expectBoundary("B2 ::1 与 localhost 都识别为回环",
                isLoopback("::1") && isLoopback("localhost") && !isLoopback("169.254.169.254"));
        expectBoundary("B3 withBind 不污染原配置（不可变）",
                base.withBind("0.0.0.0").bindAddress.equals("0.0.0.0")
                        && base.bindAddress.equals("127.0.0.1"));
        expectBoundary("B4 每条拒绝都带稳定非空 reason 码",
                !checkStart(base.withBind("0.0.0.0")).reasonCode.isEmpty()
                        && !checkRequest(base, csrf).reasonCode.isEmpty());

        System.out.println("self-test OK (" + attack + " attack cases + " + control
                + " control cases + " + boundary + " boundary assertions)");
        System.out.println("total assertions: " + checks);
        System.out.println("loopback binding alone is NOT a security boundary: "
                + "Content-Type + Origin + Host + auth are all required.");
    }

    /** 旧基线：只检查「是否绑在 127.0.0.1」。 */
    private static boolean legacyAllows(String bind) {
        return "127.0.0.1".equals(bind);
    }
}
