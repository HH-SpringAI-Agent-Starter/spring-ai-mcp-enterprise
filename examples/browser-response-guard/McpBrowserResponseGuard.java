import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * McpBrowserResponseGuard — V1.78 参考实现（第十段：浏览器 / 出站响应面 + 本地绑定面）。
 *
 * 九段式收口（注解 → 注册中心 → 只读声明 → 决策层 → 配置面 → 结果面 → 身份传播面 →
 * 会话/传输绑定面 → 执行环境隔离面）之后，第十段管的是前九段都没碰的一个方向：
 * <b>请求端的绑定守住了，服务器发出去的响应头和它监听的那张网卡，照样能把边界拆掉。</b>
 *
 * 证据链（近 3 天，均已回原文核实）：
 *   CVE-2026-34237 / GHSA-hv2w-8mjj-jw22（11-22, CVSS v3 6.1）：MCP <b>Java SDK</b>
 *     io.modelcontextprotocol.sdk:mcp-core 在 SSE / Streamable HTTP 传输里发出
 *     Access-Control-Allow-Origin: *（影响 <1.0.0 / <1.1.1 / <0.18.3，修 1.0.1/1.1.1/0.18.3）。
 *     攻击者页面让受害者浏览器打开 https://internal-mcp-server/sse，通配响应头令页面读到
 *     SSE 首帧里的 session id，随后用该 id 向同一端点发 POST —— <b>浏览器成了网络位置</b>，
 *     攻击者不需要到内网的可达路由。Python SDK 两种传输都不发通配头 → Java 库替每个
 *     基于它构建的 server 做了一个跨域策略决定。
 *   CVE-2026-112136（CVSS 8.8）/ 112137（5.4）（11-23）：Siemens Mendix Studio Pro MCP Server
 *     远程交互 + 浏览器 CSRF，工程工作站上任意 tool execution，OT 供应链；官方缓解首条即
 *     「只绑 loopback + Origin/Referer allowlist」—— <b>loopback 不等于可信，浏览器路径
 *     恰好绕开「只绑 loopback」这一层</b>。
 *   CVE-2026-134817（CVSS 7.1, CWE-88, 11-22/23）：mcp-server-kubernetes <4.2.0 把 dash 开头的
 *     位置操作数（--filename=/etc/passwd）原样交给 kubectl，被解析为 flag 读宿主机文件；
 *     GHSA-8v48-c263-r637 修法为 assertNotFlagLike() —— <b>参数以数据之名行控制之实</b>。
 *   414 台公网 MCP server 审计 91.8% 无 OAuth（第三方评估，非本框架数据）。
 *
 * 判词：**CORS 是跨域读取策略、不是认证；loopback 是网络位置、不是授权；session id 是
 * 传输句柄、不是身份。出站响应头、监听地址与工具实参三处必须在服务端独立收口：通配
 * 来源一律拒绝、session 句柄绑定签发 origin、只绑 loopback、CSRF 请求必带同源 token、
 * dash 开头的位置操作数一律拒绝，默认 fail-closed。**
 *
 * 六道闸：
 *   CP1 CORS    ACAO 只能精确 allowlist；通配拒绝；allow-credentials 不得配通配；SSE 与
 *               Streamable HTTP 两条传输分别判定（advisory 指出实现不同）。
 *   CP2 SESSION 响应体里的 session id 必须绑定签发 origin；句柄暴露给未绑定来源拒绝。
 *   CP3 BIND    监听地址只许 loopback；0.0.0.0 / :: / 任意网卡 / 文档保留地址一律拒绝。
 *   CP4 ARG     工具位置操作数 dash 前缀拒绝（--flag / --flag=value / -x），对位 flag 注入。
 *   CP5 CSRF    改动类 POST 必须带 Origin 且精确匹配同源 + 有效 CSRF token；缺 Origin、
 *               跨源、缺 token、Referer 跨源全部拒绝。
 *   CP6 AUDIT   append-only 脱敏审计：session id、origin query、argv 凭据不得落盘。
 *
 * 边界（诚实标注）：示例 allowlist / CSRF token / 绑定地址硬编码仅为可运行演示，生产须由
 * ingress / servlet container / service mesh 与配置中心共同保证；升级 Java SDK 只是移除一个
 * origin 绕过，反向代理仍可能重新加上通配头；本类未接入 21 主模块。
 */
public final class McpBrowserResponseGuard {

    public enum Code {
        OK,
        CORS_WILDCARD,
        CORS_ORIGIN_NOT_ALLOWED,
        CORS_CREDENTIALS_WITH_WILDCARD,
        SESSION_ORIGIN_UNBOUND,
        SESSION_ID_EXPOSED_CROSS_ORIGIN,
        BIND_PUBLIC_INTERFACE,
        BIND_UNRESOLVABLE,
        ARG_FLAG_LIKE,
        CSRF_ORIGIN_MISSING,
        CSRF_ORIGIN_MISMATCH,
        CSRF_TOKEN_MISSING,
        CSRF_REFERER_MISMATCH
    }

    public static final class Verdict {
        public final Code code;
        public final boolean allowed;
        public final String detail;

        private Verdict(Code code, String detail) {
            this.code = code;
            this.allowed = code == Code.OK;
            this.detail = detail;
        }

        static Verdict allow() { return new Verdict(Code.OK, "pass"); }
        static Verdict deny(Code c, String d) { return new Verdict(c, d); }

        @Override public String toString() { return code + " (" + detail + ")"; }
    }

    /** 一次「响应 + 紧随其后的请求」的合成视图；真实部署应拆成响应过滤链与请求过滤链。 */
    public static final class Request {
        String transport = "streamable-http"; // sse | streamable-http
        String responseAcao;                   // Access-Control-Allow-Origin 实际下发值
        boolean responseAcaCreds;              // Access-Control-Allow-Credentials: true
        String exposedSessionId;               // 响应体 / SSE 首帧里出现的 session id
        String sessionIssuedOrigin;            // 该 session 签发时绑定的 origin
        String bindAddress = "127.0.0.1";      // MCP Server 实际监听地址
        boolean mutating = true;               // 是否为改动类 POST
        String requestOrigin;                  // 请求 Origin 头
        String requestReferer;                 // 请求 Referer 头
        String csrfToken;                      // 同源 CSRF token
        List<String> argv = new ArrayList<>(); // 工具位置操作数

        Request transport(String v) { this.transport = v; return this; }
        Request acao(String v) { this.responseAcao = v; return this; }
        Request sid(String v) { this.exposedSessionId = v; return this; }
        Request issued(String v) { this.sessionIssuedOrigin = v; return this; }
        Request bind(String v) { this.bindAddress = v; return this; }
        Request origin(String v) { this.requestOrigin = v; return this; }
        Request referer(String v) { this.requestReferer = v; return this; }
        Request token(String v) { this.csrfToken = v; return this; }
        Request arg(String v) { this.argv.add(v); return this; }
    }

    // ---------- 演示策略（生产须外部化到 ingress / 配置中心） ----------
    private static final Set<String> CORS_ALLOWLIST = Set.of(
            "https://app.example.com", "https://admin.example.com");
    private static final String SAME_ORIGIN = "https://app.example.com";
    private static final String CSRF_TOKEN = "csrf-7f3a-ok";
    private static final Set<String> LOOPBACK = Set.of("127.0.0.1", "::1", "localhost", "ip6-localhost");
    private static final Set<String> DOC_IPS = Set.of(
            "0.0.0.0", "::", "0:0:0:0:0:0:0:0", "192.0.2.1", "198.51.100.7", "203.0.113.24");
    private static final List<String> AUDIT = Collections.synchronizedList(new ArrayList<>());

    private McpBrowserResponseGuard() {}

    // ---------- 旧基线：只看「有 session id + 有 CORS 头」 ----------
    public static Verdict legacyBaseline(Request r) {
        if (r.exposedSessionId == null || r.exposedSessionId.isEmpty()) {
            return Verdict.deny(Code.SESSION_ORIGIN_UNBOUND, "legacy: no session id");
        }
        if (r.responseAcao == null || r.responseAcao.isEmpty()) {
            return Verdict.deny(Code.CORS_ORIGIN_NOT_ALLOWED, "legacy: no CORS header");
        }
        return Verdict.allow(); // 通配 * 也算「有 CORS 头」—— 这正是本期要打掉的假设
    }

    // ---------- 主判定 ----------
    public static Verdict decide(Request r) {
        Verdict v = checkCors(r);
        if (!v.allowed) return v;
        v = checkSession(r);
        if (!v.allowed) return v;
        v = checkBind(r);
        if (!v.allowed) return v;
        v = checkArg(r);
        if (!v.allowed) return v;
        v = checkCsrf(r);
        if (!v.allowed) return v;
        appendAudit(r);
        return Verdict.allow();
    }

    // ---------- CP1 CORS 闸 ----------
    private static Verdict checkCors(Request r) {
        if (r.responseAcao == null || r.responseAcao.isEmpty()) return Verdict.allow(); // 同源默认，无跨域头是安全的
        if ("*".equals(r.responseAcao.trim())) {
            return Verdict.deny(Code.CORS_WILDCARD,
                    "wildcard ACAO on " + safe(r.transport) + " (CVE-2026-34237)");
        }
        if (r.responseAcaCreds && "*".equals(r.responseAcao.trim())) {
            return Verdict.deny(Code.CORS_CREDENTIALS_WITH_WILDCARD, "credentials + wildcard");
        }
        if (!CORS_ALLOWLIST.contains(r.responseAcao.trim())) {
            return Verdict.deny(Code.CORS_ORIGIN_NOT_ALLOWED, "ACAO not allowlisted: " + safe(r.responseAcao));
        }
        return Verdict.allow();
    }

    // ---------- CP2 SESSION 闸 ----------
    private static Verdict checkSession(Request r) {
        if (r.exposedSessionId == null || r.exposedSessionId.isEmpty()) return Verdict.allow();
        if (r.sessionIssuedOrigin == null || r.sessionIssuedOrigin.isEmpty()) {
            return Verdict.deny(Code.SESSION_ORIGIN_UNBOUND, "session id not bound to an issuing origin");
        }
        // 句柄暴露给一个非签发来源的跨域读取者 → 拒绝（SSE 首帧被攻击者页面读到的情形）
        if (r.responseAcao != null && !r.responseAcao.isEmpty()
                && !"*".equals(r.responseAcao.trim())
                && CORS_ALLOWLIST.contains(r.responseAcao.trim())
                && !r.sessionIssuedOrigin.trim().equalsIgnoreCase(r.responseAcao.trim())) {
            return Verdict.deny(Code.SESSION_ID_EXPOSED_CROSS_ORIGIN,
                    "session issued to " + safe(r.sessionIssuedOrigin)
                            + " but readable cross-origin by " + safe(r.responseAcao));
        }
        return Verdict.allow();
    }

    // ---------- CP3 BIND 闸 ----------
    private static Verdict checkBind(Request r) {
        String addr = r.bindAddress == null ? "" : r.bindAddress.trim().toLowerCase(Locale.ROOT);
        if (LOOPBACK.contains(addr)) return Verdict.allow();
        if (DOC_IPS.contains(addr) || addr.isEmpty() || addr.startsWith("0.")
                || addr.indexOf(':') >= 0 && !LOOPBACK.contains(addr)) {
            return Verdict.deny(Code.BIND_PUBLIC_INTERFACE, "MCP server bound to non-loopback: " + safe(addr));
        }
        // 真实网卡 / 主机名：能解析成非 loopback 即拒绝（对位 Mendix「只绑 loopback」）
        try {
            InetAddress ia = InetAddress.getByName(addr);
            if (!ia.isLoopbackAddress()) {
                return Verdict.deny(Code.BIND_PUBLIC_INTERFACE,
                        "bind resolves to non-loopback " + ia.getHostAddress());
            }
        } catch (UnknownHostException e) {
            return Verdict.deny(Code.BIND_UNRESOLVABLE, "bind address unresolvable: " + safe(addr));
        }
        return Verdict.allow();
    }

    // ---------- CP4 ARG 闸 ----------
    private static Verdict checkArg(Request r) {
        for (String a : r.argv) {
            if (a == null || a.isEmpty()) continue;
            if (a.charAt(0) == '-') {
                return Verdict.deny(Code.ARG_FLAG_LIKE,
                        "dash-prefixed positional operand (CVE-2026-134817): " + safe(a));
            }
        }
        return Verdict.allow();
    }

    // ---------- CP5 CSRF 闸 ----------
    private static Verdict checkCsrf(Request r) {
        if (!r.mutating) return Verdict.allow();
        if (r.requestOrigin == null || r.requestOrigin.isEmpty()) {
            return Verdict.deny(Code.CSRF_ORIGIN_MISSING, "mutating request without Origin");
        }
        String origin = r.requestOrigin.trim();
        // 合法来源 = 服务端声明的 CORS allowlist（同源策略的实际白名单，不是单一硬编码 host）
        if (!CORS_ALLOWLIST.contains(origin)) {
            return Verdict.deny(Code.CSRF_ORIGIN_MISMATCH,
                    "Origin not in allowlist: " + safe(origin));
        }
        if (r.csrfToken == null || !CSRF_TOKEN.equals(r.csrfToken)) {
            return Verdict.deny(Code.CSRF_TOKEN_MISSING, "mutating request without valid CSRF token");
        }
        if (r.requestReferer != null && !r.requestReferer.isEmpty()) {
            String ref = r.requestReferer.trim();
            if (!ref.toLowerCase(Locale.ROOT).startsWith(origin.toLowerCase(Locale.ROOT) + "/")) {
                return Verdict.deny(Code.CSRF_REFERER_MISMATCH, "Referer not same-origin as Origin: " + safe(ref));
            }
        }
        return Verdict.allow();
    }

    // ---------- CP6 脱敏审计 ----------
    private static void appendAudit(Request r) {
        StringBuilder sb = new StringBuilder();
        sb.append(Instant.now().toString())
          .append(" transport=").append(safe(r.transport))
          .append(" acao=").append(safe(r.responseAcao))
          .append(" sid=").append(mask(r.exposedSessionId))
          .append(" origin=").append(stripQuery(safe(r.requestOrigin)))
          .append(" bind=").append(safe(r.bindAddress))
          .append(" argCount=").append(r.argv == null ? 0 : r.argv.size());
        AUDIT.add(sb.toString()); // append-only：无删除接口
    }

    private static String stripQuery(String s) {
        if (s == null) return "-";
        int q = s.indexOf('?');
        return q < 0 ? s : s.substring(0, q) + "?<redacted>";
    }

    private static String safe(String s) {
        if (s == null || s.isEmpty()) return "-";
        return s.replaceAll("[\\r\\n]", "_");
    }

    private static String mask(String s) {
        if (s == null || s.isEmpty()) return "-";
        if (s.length() <= 4) return "****";
        return s.substring(0, 2) + "****" + s.substring(s.length() - 2);
    }

    public static List<String> auditLog() { return Collections.unmodifiableList(AUDIT); }

    // ================= main 自检 =================
    public static void main(String[] args) {
        int attackTotal = 0, attackBlocked = 0, legacyMiss = 0;
        int controlTotal = 0, controlPass = 0, boundary = 0, falsePos = 0;

        // ---------- 20 attack（应全拦） ----------
        List<Request> attacks = new ArrayList<>();
        Request a;

        a = base(); a.acao("*"); attacks.add(a);                                            // 1 通配 CORS（34237）
        a = base(); a.acao("*"); a.responseAcaCreds = true; attacks.add(a);                 // 2 通配 + credentials
        a = base(); a.transport("sse"); a.acao("*"); attacks.add(a);                        // 3 SSE 传输通配
        a = base(); a.transport("streamable-http"); a.acao("*"); attacks.add(a);            // 4 Streamable 传输通配
        a = base(); a.acao("https://evil.com"); attacks.add(a);                             // 5 ACAO 未授权来源
        a = base(); a.acao("https://app.example.com.evil.com"); attacks.add(a);             // 6 ACAO 后缀欺骗
        a = base(); a.sid("s-leaked"); a.issued(null); attacks.add(a);                       // 7 session 未绑定签发 origin
        a = base(); a.sid("s-leaked"); a.issued(SAME_ORIGIN);
        a.acao("https://admin.example.com"); attacks.add(a);                                // 8 session 句柄跨域可读
        a = base(); a.bind("0.0.0.0"); attacks.add(a);                                       // 9 监听所有网卡
        a = base(); a.bind("::"); attacks.add(a);                                            // 10 IPv6 通配
        a = base(); a.bind("192.168.1.10"); attacks.add(a);                                  // 11 LAN 网卡（Mendix）
        a = base(); a.bind("not-a-host.invalid"); attacks.add(a);                            // 12 不可解析绑定
        a = base(); a.arg("--filename=/etc/passwd"); attacks.add(a);                         // 13 kubectl flag 注入
        a = base(); a.arg("--kubeconfig"); attacks.add(a);                                   // 14 flag 变体
        a = base(); a.arg("-n"); attacks.add(a);                                             // 15 短 flag
        a = base(); a.origin(null); attacks.add(a);                                          // 16 POST 缺 Origin（CSRF）
        a = base(); a.origin("https://evil.com"); attacks.add(a);                            // 17 POST 跨源
        a = base(); a.token(null); attacks.add(a);                                           // 18 POST 缺 CSRF token
        a = base(); a.referer("https://evil.com/"); attacks.add(a);                          // 19 Referer 跨源
        a = base(); a.acao("*"); a.sid("s-leaked"); a.issued(SAME_ORIGIN); attacks.add(a);   // 20 通配头 + 句柄暴露组合

        for (Request r : attacks) {
            attackTotal++;
            Verdict got = decide(r);
            if (!got.allowed) attackBlocked++;
            else System.out.println("FAIL attack allowed: " + got.code + " " + safe(r.responseAcao));
            if (legacyBaseline(r).allowed) legacyMiss++;
        }

        // ---------- 4 control（应全放行，0 误杀） ----------
        List<Request> controls = new ArrayList<>();
        Request c;
        c = base(); c.acao(SAME_ORIGIN); c.sid("s-ok"); c.issued(SAME_ORIGIN); controls.add(c); // C1 精确 allowlist + 同源句柄
        c = base(); c.acao(null); c.mutating = false; controls.add(c);                          // C2 无跨域头的同源 GET
        c = base(); c.transport("sse"); c.acao("https://admin.example.com");
        c.sid("s-a"); c.issued("https://admin.example.com"); c.origin("https://admin.example.com");
        c.referer("https://admin.example.com/ui"); controls.add(c); // C3 admin 精确来源（Origin/Referer 一致）
        c = base(); c.arg("pod-name"); c.arg("default"); c.referer(SAME_ORIGIN + "/ui"); controls.add(c); // C4 合法位置参数 + 同源 Referer
        for (Request r : controls) {
            controlTotal++;
            Verdict got = decide(r);
            if (got.allowed) controlPass++;
            else { falsePos++; System.out.println("FAIL control denied: " + got); }
        }

        // ---------- 2 boundary ----------
        // B1：审计脱敏 —— session id 原值、origin query 参数不得进审计行
        Request rb = base(); rb.acao(SAME_ORIGIN); rb.sid("s3cr3t-session-id");
        rb.issued(SAME_ORIGIN); rb.origin(SAME_ORIGIN);
        rb.mutating = false; // 只读请求直达审计；origin 中的 query 脱敏由 stripQuery 验证
        rb.requestOrigin = SAME_ORIGIN + "?token=s3cr3t"; // 审计输入带 query
        decide(rb);
        String joined = String.join("\n", AUDIT);
        if (!joined.contains("s3cr3t-session-id") && !joined.contains("token=s3cr3t")
                && joined.contains("<redacted>")) {
            boundary++;
        } else {
            System.out.println("FAIL boundary B1: audit leaked session id or origin query");
        }
        // B2：空请求 fail-closed
        Request empty = new Request();
        empty.mutating = true; empty.bindAddress = "";
        Verdict ev = decide(empty);
        if (!ev.allowed) boundary++; else System.out.println("FAIL boundary B2: empty request allowed");

        System.out.println("self-test: " + (attackBlocked == attackTotal && controlPass == controlTotal
                && falsePos == 0 ? "OK" : "FAILED"));
        System.out.println("  attack   : " + attackBlocked + "/" + attackTotal + " blocked");
        System.out.println("  control  : " + controlPass + "/" + controlTotal + " allowed (false positives: " + falsePos + ")");
        System.out.println("  boundary : " + boundary + "/2 assertions");
        System.out.println("  legacy-only blocks (new guard catches, baseline misses): " + legacyMiss);
        System.out.println("  audit entries: " + AUDIT.size());
        System.out.println("  total assertions: " + (attackTotal + controlTotal + boundary));
    }

    private static Request base() {
        Request r = new Request();
        r.transport = "streamable-http";
        r.responseAcao = SAME_ORIGIN;
        r.exposedSessionId = "s-base-uid";
        r.sessionIssuedOrigin = SAME_ORIGIN;
        r.bindAddress = "127.0.0.1";
        r.mutating = true;
        r.requestOrigin = SAME_ORIGIN;
        r.csrfToken = CSRF_TOKEN;
        r.requestReferer = SAME_ORIGIN + "/ui";
        return r;
    }
}
