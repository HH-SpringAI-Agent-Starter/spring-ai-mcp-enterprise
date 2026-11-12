package com.example.mcp.registry;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * MCP 注册中心「管理面自防护 + 存量注册项复验」参考实现（V1.70）。
 *
 * <p>状态：REFERENCE ONLY，纯 JDK 零依赖，不进 Maven reactor，零构建风险。
 *
 * <p>核心立场（2026-11-11 AWS 官方公告把问题钉死）：
 * <b>注册中心是所有治理决策的信任根，因此它自己必须是最先被治理的对象</b>。
 * 认证不等于授权；注册时校验过不等于永远可信；升级不清除攻击者已经写进去的东西。
 * 本类把管理 API 授权、上游地址准入、存量全量复验、凭据读取授权做成四个相互独立、
 * 任一失效也不会全局放行的控制点。
 *
 * <p>对位实证：
 * <ul>
 *   <li>CVE-2026-100307（AWS 公告 2026-179-AWS，11-11，CVSS 3.1 8.8）：
 *       {@code POST /api/servers/groups/import} 只认证不授权，低权限用户把自己 IdP
 *       group 映射到 mcp-registry-admin，管理员身份按请求派生、下次请求即生效；
 *       可读取注册表内解密后的 server 凭据与自定义 header。</li>
 *   <li>CVE-2026-125926（AWS 公告 2026-180-AWS，11-11，CVSS 3.1 8.5）：
 *       注册 / 修改 API 可把网关上游指向内网与云实例元数据端点并读回响应。
 *       第一个洞发出的正是第二个洞所需的权限，构成链式提权。</li>
 *   <li>官方修复的边界：1.35.0 只在「注册 / 修改时」校验上游地址，升级前已存配置不变；
 *       升级也不会撤销已植入的 group 映射或使已泄露凭据失效 → 必须全量复验存量。</li>
 *   <li>CVE-2026-109492（Microsoft mcp-gateway，11-10，IDOR / CWE-639，8.8）：
 *       用户可控键越权，网关自身成为攻击面。</li>
 *   <li>CVE-2026-125271（AWS API MCP Server）：操作名归一化错误导致安全策略绕过。</li>
 *   <li>dev.to 企业注册表横评：Official MCP Registry 的 Access Control Model 标注为
 *       None（open discovery layer）→ 开放发现层本身不带授权。</li>
 * </ul>
 *
 * <p>自检运行：{@code java -Dfile.encoding=UTF-8 com.example.mcp.registry.McpRegistryAdminGuard}
 */
public final class McpRegistryAdminGuard {

    // ---------------------------------------------------------------- 模型

    /** 管理面权限。授权必须逐操作显式判定，禁止「登录即可管理」。 */
    public enum Permission {
        SERVER_LIST, SERVER_REGISTER, SERVER_MODIFY, SERVER_TOGGLE, SERVER_REMOVE,
        GROUP_IMPORT, CREDENTIAL_READ, POLICY_ADMIN
    }

    /** 调用结果。 */
    public static final class Verdict {
        public final boolean allowed;
        public final String reasonCode;
        public final boolean approvalRequired;

        Verdict(boolean allowed, String reasonCode, boolean approvalRequired) {
            this.allowed = allowed;
            this.reasonCode = reasonCode;
            this.approvalRequired = approvalRequired;
        }

        static Verdict allow(String code) { return new Verdict(true, code, false); }
        static Verdict deny(String code) { return new Verdict(false, code, true); }

        @Override public String toString() {
            return (allowed ? "ALLOW" : "DENY") + "(" + reasonCode + ")";
        }
    }

    /**
     * 主体。授权集合来自独立、只读、带版本的授权源，
     * 绝不由 group import 等可写数据派生 —— 这是 100307 的根因。
     */
    public static final class Principal {
        public final String id;
        public final Set<Permission> granted;
        public final boolean bootstrapAdmin;

        public Principal(String id, boolean bootstrapAdmin, Permission... permissions) {
            this.id = id;
            this.bootstrapAdmin = bootstrapAdmin;
            Set<Permission> copy = new LinkedHashSet<>(Arrays.asList(permissions));
            this.granted = Collections.unmodifiableSet(copy);
        }

        boolean has(Permission p) {
            // 凭据读取是独立权限，管理员身份不得隐含 —— 对位 100307 提权后可解密读取
            // registry-stored credentials / custom headers 的攻击链终点。
            if (p == Permission.CREDENTIAL_READ) return granted.contains(p);
            return bootstrapAdmin || granted.contains(p);
        }
    }

    /** 已注册的上游 MCP server。approvedFingerprint 记录「上次批准时」的配置指纹。 */
    public static final class Registration {
        public final String id;
        public String upstreamUrl;
        public String approvedFingerprint;
        public final String registeredBy;
        public boolean quarantined;

        Registration(String id, String upstreamUrl, String registeredBy) {
            this.id = id;
            this.upstreamUrl = upstreamUrl;
            this.registeredBy = registeredBy;
            this.approvedFingerprint = fingerprint(upstreamUrl);
        }
    }

    /** 策略拒绝列表：以归一化操作名存储，防大小写 / 下划线 / 重复分隔符绕过。 */
    private static final Set<String> DENIED_OPERATIONS = new LinkedHashSet<>(Arrays.asList(
            "deleteallservers", "exportcredentials", "disablepolicy", "setregistryadmin"));

    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]");
    private static final Pattern DIGIT_HOST = Pattern.compile("\\d+");
    private static final Pattern DOTTED_QUAD = Pattern.compile("\\d{1,3}(?:\\.\\d{1,3}){3}");

    private final Map<String, Registration> store = new LinkedHashMap<>();
    private final List<String> audit = new ArrayList<>();

    // ---------------------------------------------------------------- A. 管理面授权

    /**
     * 归一化操作名：去所有非字母数字、转小写。
     * 对位 CVE-2026-125271「归一化错误导致策略绕过」：
     * get_user / GetUser / Get__User 必须落到同一个策略键。
     */
    public static String canonicalizeOperation(String raw) {
        if (raw == null) return "";
        return NON_ALNUM.matcher(raw.toLowerCase(Locale.ROOT)).replaceAll("");
    }

    /** 管理 API 入口：认证与授权是两件事，逐操作显式判定。 */
    public Verdict authorize(Principal caller, boolean authenticated, Permission required,
                             String rawOperation) {
        if (caller == null || !authenticated) return Verdict.deny("NOT_AUTHENTICATED");
        String op = canonicalizeOperation(rawOperation);
        if (DENIED_OPERATIONS.contains(op)) return Verdict.deny("OPERATION_DENY_LIST");
        if (!caller.has(required)) return Verdict.deny("MISSING_PERMISSION:" + required);
        log("AUTH", caller.id, required + "/" + op, "ALLOW");
        return Verdict.allow("AUTHORIZED:" + required);
    }

    /**
     * 组导入：允许导入普通 group，但任何指向管理员 scope 的映射一律拒绝，
     * 且导入动作永远不能写回授权源。对位 100307 的 POST /api/servers/groups/import。
     */
    public Verdict importGroup(Principal caller, boolean authenticated,
                               String groupName, String targetScope) {
        Verdict pre = authorize(caller, authenticated, Permission.GROUP_IMPORT, "importGroup");
        if (!pre.allowed) return pre;
        String scope = canonicalizeOperation(targetScope);
        if (scope.isEmpty() || scope.contains("admin") || scope.contains("root")
                || scope.contains("superuser") || scope.contains("policy")) {
            log("GROUP_IMPORT", caller.id, groupName + "->" + targetScope, "DENY_PRIVILEGED_SCOPE");
            return Verdict.deny("PRIVILEGED_SCOPE_MAPPING_FORBIDDEN");
        }
        log("GROUP_IMPORT", caller.id, groupName + "->" + targetScope, "ALLOW");
        return Verdict.allow("GROUP_IMPORTED_NON_PRIVILEGED");
    }

    // ---------------------------------------------------------------- B. 上游地址准入（SSRF）

    /** 注册 / 修改时校验上游地址。对位 125926。 */
    public Verdict validateUpstream(String rawUrl) {
        if (rawUrl == null || rawUrl.isEmpty()) return Verdict.deny("EMPTY_UPSTREAM");
        // 反斜杠与控制字符：URL 解析器与 HTTP 客户端解释不一致的经典绕过
        if (rawUrl.indexOf('\\') >= 0 || rawUrl.indexOf('\r') >= 0 || rawUrl.indexOf('\n') >= 0
                || rawUrl.indexOf('\t') >= 0) {
            return Verdict.deny("AMBIGUOUS_URL_CHARS");
        }
        URI u;
        try {
            u = new URI(rawUrl);
        } catch (Exception e) {
            return Verdict.deny("MALFORMED_URL");
        }
        if (!"https".equalsIgnoreCase(u.getScheme())) return Verdict.deny("SCHEME_NOT_HTTPS");
        if (u.getRawUserInfo() != null) return Verdict.deny("USERINFO_FORBIDDEN");
        String host = u.getHost();
        if (host == null || host.isEmpty()) return Verdict.deny("NO_HOST");
        if (forbiddenHost(host)) return Verdict.deny("FORBIDDEN_UPSTREAM_HOST");
        return Verdict.allow("UPSTREAM_OK");
    }

    /** 内网 / 环回 / 链路本地 / 云元数据 / IP 字面量伪装 统一判定。 */
    public static boolean forbiddenHost(String rawHost) {
        if (rawHost == null) return true;
        String h = rawHost.trim().toLowerCase(Locale.ROOT);
        if (h.startsWith("[") && h.endsWith("]")) h = h.substring(1, h.length() - 1);
        while (h.endsWith(".")) h = h.substring(0, h.length() - 1);
        if (h.isEmpty()) return true;

        if (h.equals("localhost") || h.endsWith(".localhost")) return true;
        if (h.endsWith(".internal") || h.endsWith(".local") || h.endsWith(".localdomain")) return true;
        if (h.contains("metadata") || h.equals("instance-data")) return true;

        // 纯十进制 / 八进制数字主机（2130706433 = 127.0.0.1）
        if (DIGIT_HOST.matcher(h).matches()) return true;

        // IPv6（含 IPv4 映射 ::ffff:127.0.0.1）
        if (h.contains(":")) {
            if (h.equals("::") || h.equals("::1") || h.startsWith("::ffff:")) return true;
            if (h.startsWith("fc") || h.startsWith("fd") || h.startsWith("fe80")) return true;
            if (h.startsWith("2001:db8")) return true;
            String flat = h.replace(":", "");
            if (flat.matches("0*1")) return true; // ::1 的其它写法
            // IPv4 映射后缀仍要复验
            int last = h.lastIndexOf(':');
            String tail = h.substring(last + 1);
            if (DOTTED_QUAD.matcher(tail).matches()) return forbiddenIpv4(tail);
            return false;
        }

        if (h.matches("[0-9.]+")) {
            // 标准点分四组交严格解析；其余（前导 0 / 八进制 / 超长 / 多余点）
            // 都是 IP 字面量伪装，解析器与 HTTP 客户端解释不一致，一律拒绝。
            if (DOTTED_QUAD.matcher(h).matches()) return forbiddenIpv4(h);
            return true;
        }
        // 域名不能是纯 IP 的十六进制写法
        if (h.matches("0x[0-9a-f.]+")) return true;
        return false;
    }

    private static boolean forbiddenIpv4(String h) {
        String[] parts = h.split("\\.");
        if (parts.length != 4) return true;
        int[] o = new int[4];
        for (int i = 0; i < 4; i++) {
            String p = parts[i];
            if (p.isEmpty() || !p.matches("\\d+")) return true;
            // 前导 0 可能是八进制写法（0177.0.0.1）
            if (p.length() > 1 && p.startsWith("0")) return true;
            try { o[i] = Integer.parseInt(p); } catch (NumberFormatException e) { return true; }
            if (o[i] > 255) return true;
        }
        int a = o[0], b = o[1];
        if (a == 0 || a == 10 || a == 127) return true;
        if (a == 169 && b == 254) return true;              // link-local + 云元数据 169.254.169.254
        if (a == 172 && b >= 16 && b <= 31) return true;
        if (a == 192 && b == 168) return true;
        if (a == 100 && b >= 64 && b <= 127) return true;  // CGNAT
        if (a == 192 && b == 0 && o[2] == 0) return true;
        if (a == 198 && (b == 18 || b == 19)) return true;
        if (a >= 224) return true;                         // multicast / reserved
        return false;
    }

    /**
     * 重定向逐跳重校验 + Host/SNI 一致性（兑现 V1.69 跨期待办）。
     * 策略选择：跨主机重定向默认拒绝，需显式 allowlist；保守但可验收。
     */
    public Verdict validateRedirectHop(String previousUrl, String locationHeader) {
        if (locationHeader == null || locationHeader.isEmpty()) return Verdict.deny("EMPTY_LOCATION");
        String resolved;
        try {
            URI prev = new URI(previousUrl);
            URI next = new URI(locationHeader);
            if (!next.isAbsolute()) next = prev.resolve(next);
            resolved = next.toString();
            String prevHost = prev.getHost();
            String nextHost = next.getHost();
            if (prevHost == null || nextHost == null) return Verdict.deny("NO_HOST_ON_HOP");
            String p = prevHost.toLowerCase(Locale.ROOT);
            String n = nextHost.toLowerCase(Locale.ROOT);
            // SNI / Host 一致性：跨主机一律不复用上一跳的准入结论
            if (!p.equals(n)) return Verdict.deny("HOST_SNI_MISMATCH_ON_REDIRECT");
        } catch (Exception e) {
            return Verdict.deny("MALFORMED_LOCATION");
        }
        Verdict v = validateUpstream(resolved);
        if (!v.allowed) return Verdict.deny("REHOP_" + v.reasonCode);
        return Verdict.allow("REDIRECT_HOP_OK");
    }

    // ---------------------------------------------------------------- C. 注册 / 修改 / 存量复验

    public Verdict register(Principal caller, boolean authenticated, String id, String upstreamUrl) {
        Verdict auth = authorize(caller, authenticated, Permission.SERVER_REGISTER, "registerServer");
        if (!auth.allowed) return auth;
        Verdict v = validateUpstream(upstreamUrl);
        if (!v.allowed) { log("REGISTER", caller.id, id + "=" + upstreamUrl, "DENY_" + v.reasonCode); return v; }
        if (store.containsKey(id)) return Verdict.deny("DUPLICATE_ID");
        store.put(id, new Registration(id, upstreamUrl, caller.id));
        log("REGISTER", caller.id, id + "=" + upstreamUrl, "ALLOW");
        return Verdict.allow("REGISTERED");
    }

    /** 修改上游地址：等同重新注册，必须重跑全量准入并重签指纹，不能沿用旧批准。 */
    public Verdict modifyUpstream(Principal caller, boolean authenticated, String id,
                                  String newUpstreamUrl) {
        Verdict auth = authorize(caller, authenticated, Permission.SERVER_MODIFY, "modifyServer");
        if (!auth.allowed) return auth;
        Registration r = store.get(id);
        if (r == null) return Verdict.deny("UNKNOWN_ID");
        Verdict v = validateUpstream(newUpstreamUrl);
        if (!v.allowed) { log("MODIFY", caller.id, id + "=" + newUpstreamUrl, "DENY_" + v.reasonCode); return v; }
        r.upstreamUrl = newUpstreamUrl;
        r.approvedFingerprint = fingerprint(newUpstreamUrl);
        r.quarantined = false;
        log("MODIFY", caller.id, id + "=" + newUpstreamUrl, "ALLOW_REAPPROVED");
        return Verdict.allow("MODIFIED_REAPPROVED");
    }

    /**
     * 存量全量复验：对位 AWS「1.35.0 只校验注册 / 修改时，升级前已存配置不变」
     * 与「升级不撤销已植入 group 映射」。任何升级 / 策略变更后必须调用一次。
     * 返回被隔离的 id 列表；可重复调用（幂等）。
     */
    public List<String> reverifyAll() {
        List<String> quarantinedIds = new ArrayList<>();
        for (Registration r : store.values()) {
            Verdict v = validateUpstream(r.upstreamUrl);
            boolean drift = !fingerprint(r.upstreamUrl).equals(r.approvedFingerprint);
            boolean bad = !v.allowed || drift;
            if (bad && !r.quarantined) {
                r.quarantined = true;
                quarantinedIds.add(r.id);
                log("REVERIFY", "system", r.id + "=" + r.upstreamUrl,
                        "QUARANTINE_" + (drift ? "FINGERPRINT_DRIFT" : v.reasonCode));
            } else if (!bad && r.quarantined) {
                r.quarantined = false;
                log("REVERIFY", "system", r.id, "RELEASED");
            }
        }
        return Collections.unmodifiableList(quarantinedIds);
    }

    public boolean isQuarantined(String id) {
        Registration r = store.get(id);
        return r != null && r.quarantined;
    }

    /** 直接模拟「攻击者绕过 API 改库」：用于自检与事故演练，生产不应暴露。 */
    public void simulateOutOfBandTamper(String id, String poisonedUrl) {
        Registration r = store.get(id);
        if (r != null) r.upstreamUrl = poisonedUrl; // 指纹不改 → 复验应抓到漂移
    }

    // ---------------------------------------------------------------- D. 凭据读取

    /** 读注册表内凭据 / 自定义 header：需独立 CREDENTIAL_READ，不能只凭管理员身份。 */
    public Verdict readCredentials(Principal caller, boolean authenticated, String id) {
        Verdict auth = authorize(caller, authenticated, Permission.CREDENTIAL_READ, "readCredentials");
        if (!auth.allowed) return auth;
        if (!store.containsKey(id)) return Verdict.deny("UNKNOWN_ID");
        // 审计只记字段名与主体，绝不记录值
        log("CREDENTIAL_READ", caller.id, id + " fields=[auth_header,api_key]", "ALLOW_MASKED");
        return Verdict.allow("CREDENTIAL_FIELDS_RETURNED_MASKED_AUDIT");
    }

    // ---------------------------------------------------------------- 工具

    public static String fingerprint(String value) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", d[i]));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void log(String action, String actor, String target, String outcome) {
        audit.add(action + "|" + actor + "|" + target + "|" + outcome);
    }

    /** append-only 快照（不可变），支持事后取证。 */
    public List<String> auditSnapshot() {
        return Collections.unmodifiableList(new ArrayList<>(audit));
    }

    // ---------------------------------------------------------------- 自检

    private static int checks = 0;

    private static void expectTrue(String label, boolean condition) {
        checks++;
        if (!condition) throw new AssertionError("FAILED: " + label);
    }

    public static void main(String[] args) {
        int attack = 0, control = 0, boundary = 0;

        Principal low = new Principal("low-user", false,
                Permission.SERVER_LIST, Permission.GROUP_IMPORT);
        Principal operator = new Principal("operator", false,
                Permission.SERVER_LIST, Permission.SERVER_REGISTER, Permission.SERVER_MODIFY);
        Principal admin = new Principal("admin", true);
        Principal vault = new Principal("vault-svc", false, Permission.CREDENTIAL_READ);

        // ---------------- 攻击用例 ----------------
        McpRegistryAdminGuard g = new McpRegistryAdminGuard();

        expectTrue("A1 group import 到 mcp-registry-admin 必须拒绝",
                !g.importGroup(low, true, "dev-team", "mcp-registry-admin").allowed); attack++;
        expectTrue("A2 大写变形 MCP_REGISTRY_ADMIN 必须拒绝",
                !g.importGroup(low, true, "dev-team", "MCP_REGISTRY_ADMIN").allowed); attack++;
        expectTrue("A3 点分隔变形 mcp.registry.admin 必须拒绝",
                !g.importGroup(low, true, "dev-team", "mcp.registry.admin").allowed); attack++;
        expectTrue("A4 registry-admin 必须拒绝",
                !g.importGroup(low, true, "dev-team", "registry-admin").allowed); attack++;
        expectTrue("A5 只认证不授权不能注册 server",
                !g.authorize(low, true, Permission.SERVER_REGISTER, "registerServer").allowed); attack++;
        expectTrue("A6 未认证不能列 server",
                !g.authorize(low, false, Permission.SERVER_LIST, "listServers").allowed); attack++;
        expectTrue("A7 归一化后命中拒绝列表（Get__User 型绕过）",
                !g.authorize(admin, true, Permission.SERVER_LIST, "Export__Credentials").allowed); attack++;
        expectTrue("A8 云元数据 169.254.169.254（http）拒绝",
                !g.validateUpstream("http://169.254.169.254/latest/meta-data/iam/security-credentials/").allowed); attack++;
        expectTrue("A9 云元数据 169.254.169.254（https）同样拒绝",
                !g.validateUpstream("https://169.254.169.254/latest/meta-data/").allowed); attack++;
        expectTrue("A10 loopback 127.0.0.1 拒绝",
                !g.validateUpstream("https://127.0.0.1:8080/mcp").allowed); attack++;
        expectTrue("A11 localhost 拒绝",
                !g.validateUpstream("https://localhost/mcp").allowed); attack++;
        expectTrue("A12 10/8 私网拒绝", !g.validateUpstream("https://10.0.0.5/mcp").allowed); attack++;
        expectTrue("A13 172.16/12 私网拒绝", !g.validateUpstream("https://172.20.1.1/mcp").allowed); attack++;
        expectTrue("A14 192.168/16 私网拒绝", !g.validateUpstream("https://192.168.1.1/mcp").allowed); attack++;
        expectTrue("A15 userinfo 欺骗 api.example.com@169.254.169.254 拒绝",
                !g.validateUpstream("https://api.example.com@169.254.169.254/mcp").allowed); attack++;
        expectTrue("A16 常规 userinfo 拒绝",
                !g.validateUpstream("https://user:pass@api.example.com/mcp").allowed); attack++;
        expectTrue("A17 IPv6 loopback ::1 拒绝", !g.validateUpstream("https://[::1]/mcp").allowed); attack++;
        expectTrue("A18 IPv6 ULA fd00::1 拒绝", !g.validateUpstream("https://[fd00::1]/mcp").allowed); attack++;
        expectTrue("A19 metadata.google.internal 拒绝",
                !g.validateUpstream("https://metadata.google.internal/computeMetadata/v1").allowed); attack++;
        expectTrue("A20 十进制 IP 2130706433 拒绝",
                !g.validateUpstream("https://2130706433/mcp").allowed); attack++;
        expectTrue("A21 八进制伪装 0177.0.0.1 拒绝",
                !g.validateUpstream("https://0177.0.0.1/mcp").allowed); attack++;
        expectTrue("A22 http 明文拒绝", !g.validateUpstream("http://api.example.com/mcp").allowed); attack++;
        expectTrue("A23 反斜杠歧义拒绝",
                !g.validateUpstream("https://api.example.com\\@169.254.169.254/mcp").allowed); attack++;
        expectTrue("A24 重定向逐跳到内网必须拒绝",
                !g.validateRedirectHop("https://api.example.com/mcp",
                        "https://169.254.169.254/latest/meta-data/").allowed); attack++;
        expectTrue("A25 跨主机重定向不复用准入结论",
                !g.validateRedirectHop("https://api.example.com/mcp",
                        "https://evil.example.net/mcp").allowed); attack++;
        expectTrue("A26 仅凭 admin 身份读凭据仍需独立 CREDENTIAL_READ",
                !g.readCredentials(new Principal("admin-no-vault", true, Permission.SERVER_LIST),
                        true, "any").allowed); attack++;
        expectTrue("A27 低权限用户读凭据拒绝", !g.readCredentials(low, true, "any").allowed); attack++;

        // ---------------- 合法对照（不得劣于基线） ----------------
        expectTrue("C1 普通 group import 放行", g.importGroup(low, true, "dev-team", "read_only").allowed); control++;
        expectTrue("C2 有权限者注册合法公网 https 放行",
                g.register(operator, true, "srv-1", "https://mcp.vendor.example.com/mcp").allowed); control++;
        expectTrue("C3 低权限列 server 放行",
                g.authorize(low, true, Permission.SERVER_LIST, "listServers").allowed); control++;
        expectTrue("C4 公网显式端口放行",
                g.validateUpstream("https://mcp.vendor.example.com:8443/mcp").allowed); control++;
        expectTrue("C5 非私网文档网段放行",
                g.validateUpstream("https://203.0.113.10/mcp").allowed); control++;
        expectTrue("C6 操作名未命中拒绝列表放行",
                g.authorize(admin, true, Permission.SERVER_LIST, "list_tools").allowed); control++;
        expectTrue("C7 同主机相对路径重定向放行",
                g.validateRedirectHop("https://api.example.com/mcp", "/mcp/v2").allowed); control++;
        expectTrue("C8 有 CREDENTIAL_READ 读凭据放行（先注册该项）",
                g.readCredentials(vault, true, "srv-1").allowed); control++;
        expectTrue("C9 修改上游并重批准放行",
                g.modifyUpstream(operator, true, "srv-1", "https://mcp2.vendor.example.com/mcp").allowed); control++;
        expectTrue("C10 合法项在复验中不被隔离",
                !g.isQuarantined("srv-1") && g.reverifyAll().isEmpty()); control++;

        // ---------------- 边界 / 增量 / 不变式 ----------------
        String[] attackUrls = {
                "http://169.254.169.254/latest/meta-data/", "https://169.254.169.254/latest/",
                "https://127.0.0.1:8080/mcp", "https://10.0.0.5/mcp", "https://172.20.1.1/mcp",
                "https://192.168.1.1/mcp", "https://user:p@api.example.com/mcp",
                "https://api.example.com@169.254.169.254/mcp", "https://[::1]/mcp",
                "https://[fd00::1]/mcp", "https://metadata.google.internal/x",
                "https://2130706433/mcp", "https://0177.0.0.1/mcp", "http://api.example.com/mcp"
        };
        int incremental = 0;
        for (String u : attackUrls) {
            if (legacyAllowsUpstream(u) && !g.validateUpstream(u).allowed) incremental++;
        }
        expectTrue("B1 旧基线（仅 http/https 前缀检查）漏掉至少 12 个，新实现全部拦下",
                incremental >= 12); boundary++;

        expectTrue("B2 归一化三种写法一致",
                canonicalizeOperation("get_user").equals(canonicalizeOperation("GetUser"))
                        && canonicalizeOperation("GetUser").equals(canonicalizeOperation("Get__User"))); boundary++;

        Set<Permission> before = low.granted;
        g.importGroup(low, true, "dev-team", "mcp-registry-admin");
        expectTrue("B3 group import 无法写回授权源（授权集合不变且不可变）",
                low.granted == before && !low.granted.contains(Permission.SERVER_REGISTER)); boundary++;
        boolean immutable = false;
        try { low.granted.add(Permission.POLICY_ADMIN); } catch (UnsupportedOperationException e) { immutable = true; }
        expectTrue("B4 授权源不可变", immutable); boundary++;

        // 存量复验：模拟升级前已存在的注册项被带外改成内网地址
        g.simulateOutOfBandTamper("srv-1", "http://169.254.169.254/latest/meta-data/");
        List<String> q = g.reverifyAll();
        expectTrue("B5 升级后存量项被带外污染 → 复验抓到并隔离",
                q.contains("srv-1") && g.isQuarantined("srv-1")); boundary++;
        expectTrue("B6 复验幂等（第二次不再重复报同一项）", g.reverifyAll().isEmpty()); boundary++;

        List<String> snap = g.auditSnapshot();
        boolean auditImmutable = false;
        try { snap.add("tamper"); } catch (UnsupportedOperationException e) { auditImmutable = true; }
        expectTrue("B7 审计快照不可变", auditImmutable); boundary++;
        boolean noPlainSecret = true;
        for (String line : snap) {
            if (line.contains("user:pass") || line.contains("security-credentials/iam")) {
                noPlainSecret = false;
            }
        }
        expectTrue("B8 审计不落明文凭据值", noPlainSecret); boundary++;

        System.out.println("self-test OK (" + attack + " attack cases + " + control
                + " control cases + " + boundary + " boundary assertions)");
        System.out.println("total assertions: " + checks);
        System.out.println("incremental blocks over legacy baseline: " + incremental);
        System.out.println("control plane authorization is INDEPENDENT of writable registry data;");
        System.out.println("registration-time approval is NOT a permanent trust decision.");
    }

    /** 旧基线：只检查 scheme 前缀，不做主机判定 —— 用于「不得劣于基线」对照。 */
    private static boolean legacyAllowsUpstream(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }
}
