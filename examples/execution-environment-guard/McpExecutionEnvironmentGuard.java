import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * McpExecutionEnvironmentGuard — V1.77 参考实现（第九段：执行环境隔离面）。
 *
 * 八段式收口（注解 → 注册中心 → 只读声明 → 决策层 → 配置面 → 结果面 → 身份传播面 → 会话/传输绑定面）
 * 之后，第九段管的是「工具真正落到执行环境那一刻，沙箱 confinement 到底闭没闭合」：
 *   MCPHub CVE-2026-79748（GHSA-mx89-jjx9-gjr8, CVSS 9.9）：POST /api/servers 与
 *     PUT /api/servers/name 只查登录不查 admin、不限制 command，任何登录非管理员可注册
 *     command=/bin/sh 的 server 立即执行（Docker 镜像里常以 root 跑），改端点无归属校验。
 *   Hermes Agent GHSA-p22p-9ph6-gmrh（11-19, High）：docker/ssh 终端后端下，agent 从任意后端
 *     进程可写目录读回 env 文件，接受每个 KEY=VALUE 合并进宿主机启动的进程，可设
 *     LD_PRELOAD / DYLD_INSERT_LIBRARIES 以 gateway 用户执行代码、触达 provider keys。
 *   Gortex CVE-2026-62964（7.8）：read 工具做仓库 confinement + symlink 检查，write_file/
 *     edit_file/move_inline 却收绝对路径、跳过两查 → 可写 ~/.zshrc 或 .git/hooks/pre-commit。
 *   yt-mcp CVE-2026-75612（7.5）：add_attachment 读任意文件外发到 YouTrack。
 *   DOMShell CVE-2026-75518（4.8）：domshell_eval 标 readOnlyHint:true 却跑任意 CDP
 *     Runtime.evaluate，Claude Desktop / Cursor 显示错误审批 UI。
 *   MCP Python SDK CVE-2026-52869（7.1, 11-20 VulnCheck 在野）：SSE / stateful Streamable HTTP
 *     仅凭 session_id 路由请求，不校验同一认证客户端；多人共享一个 OAuth client 时若不填
 *     AccessToken.subject，只按 client 分离不按 user → 跨用户会话注入。
 *
 * 判词：**沙箱 confinement 只守住作者画的那条路；声明层（readOnlyHint / sessionId / 登录态）
 * 一律不参与授权。必须在执行点独立定级、强制命令 allowlist、env 源头与键名双白名单、
 * 写路径与读路径同等 confinement、会话按 issuer+subject 绑定，默认只读 fail-closed。**
 *
 * 六道闸：
 *   CP1 PATH   读写路径同等 confinement：拒绝对路径逃逸、.. 遍历、符号链接逃逸、控制字符。
 *   CP2 CMD    命令 allowlist + argv shell 元字符拒绝 + 解释器直执拒绝（非管理员不可注册命令）。
 *   CP3 ENV    env 源头白名单 + 危险键（LD_PRELOAD 等）拒绝 + 值中命令替换拒绝。
 *   CP4 TIER   服务端独立定级：写动词不看 readOnlyHint，必须有审批令牌；声明说谎单独拒绝。
 *   CP5 SUBJ   会话按 issuer + subject 绑定；共享 OAuth client 无真实 subject 拒绝；主体不一致拒绝。
 *   CP6 AUDIT  append-only 脱敏审计：不记原始 env 值 / argv 凭据，只记规范化 op/path/subject。
 *
 * 边界（诚实标注）：示例 allowlist / issuer / 审批令牌硬编码仅为可运行演示，生产须走配置中心
 * + KMS 轮换 + 共享会话存储；符号链接检测为集合模拟非真实 lstat；本类未接入 21 主模块。
 */
public final class McpExecutionEnvironmentGuard {

    public enum Code {
        OK,
        ESCAPE_ABSOLUTE_PATH,
        ESCAPE_PATH_TRAVERSAL,
        ESCAPE_SYMLINK,
        ESCAPE_CONTROL_CHAR,
        CMD_NOT_ALLOWLISTED,
        CMD_INTERPRETER,
        CMD_SHELL_META,
        CMD_NON_ADMIN_REGISTER,
        ENV_DANGEROUS_KEY,
        ENV_UNTRUSTED_SOURCE,
        ENV_INJECTION_VALUE,
        READ_ONLY_LIE,
        WRITE_TIER_NEEDS_APPROVAL,
        SUBJECT_UNBOUND,
        SHARED_CLIENT_NO_SUBJECT,
        ISSUER_MISMATCH,
        SUBJECT_MISMATCH
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

    public static final class Request {
        String principal;
        String sessionId;
        String sessionSubject;
        String sessionIssuer;
        String authClientId;
        boolean admin;
        boolean claimedReadOnly;
        String operation;
        String command;
        List<String> argv = new ArrayList<>();
        String targetPath;
        Set<String> symlinks = new HashSet<>();
        Map<String, String> env = new LinkedHashMap<>();
        boolean envFromFile;
        String envSource;
        String approvalToken;

        Request op(String v) { this.operation = v; return this; }
        Request cmd(String v) { this.command = v; return this; }
        Request path(String v) { this.targetPath = v; return this; }
        Request subj(String v) { this.sessionSubject = v; return this; }
        Request principal(String v) { this.principal = v; return this; }
        Request issuer(String v) { this.sessionIssuer = v; return this; }
        Request sid(String v) { this.sessionId = v; return this; }
    }

    // ---------- 演示策略（生产须外部化） ----------
    private static final String CONFINEMENT_ROOT = "/srv/mcp-data";
    private static final String EXPECTED_ISSUER = "https://auth.example.com";
    private static final String APPROVAL_TOKEN = "appr-9f3c-ok";
    private static final Set<String> CMD_ALLOWLIST = Set.of(
            "read_file", "list_dir", "search", "http_get", "calc",
            "write_file", "edit_file", "add_attachment");
    private static final Set<String> INTERPRETERS = Set.of(
            "/bin/sh", "sh", "bash", "/bin/bash", "zsh", "cmd.exe", "cmd",
            "powershell", "pwsh", "python", "python3", "node -e", "perl", "ruby");
    private static final Set<String> WRITE_OPS = Set.of(
            "write_file", "edit_file", "move_inline", "add_attachment",
            "upload", "delete", "exec", "eval");
    private static final Set<String> DANGEROUS_ENV_KEYS = Set.of(
            "LD_PRELOAD", "LD_LIBRARY_PATH", "DYLD_INSERT_LIBRARIES", "DYLD_LIBRARY_PATH",
            "NODE_OPTIONS", "PYTHONPATH", "BASH_ENV", "ENV", "IFS", "SHELLOPTS");
    private static final Set<String> ENV_SOURCE_ALLOWLIST = Set.of("/etc/mcp/env.d");
    private static final Set<String> SHARED_CLIENTS = Set.of("shared-gateway", "hosted-mcp-client");
    private static final List<String> AUDIT = Collections.synchronizedList(new ArrayList<>());

    private McpExecutionEnvironmentGuard() {}

    // ---------- 旧基线：只看声明层（sessionId 非空 + readOnlyHint） ----------
    public static Verdict legacyBaseline(Request r) {
        if (r.sessionId == null || r.sessionId.isEmpty()) {
            return Verdict.deny(Code.SUBJECT_UNBOUND, "legacy: no sessionId");
        }
        if (r.claimedReadOnly) {
            return Verdict.allow();
        }
        return Verdict.allow(); // 旧基线对写动词也不独立定级 —— 这正是本期要打掉的假设
    }

    // ---------- 主判定 ----------
    public static Verdict decide(Request r) {
        // CP2 CMD：命令白名单 / 解释器直执 / argv shell 元字符 / 非管理员注册
        Verdict v = checkCommand(r);
        if (!v.allowed) return v;

        // CP1 PATH：读写路径同等 confinement
        v = checkPath(r);
        if (!v.allowed) return v;

        // CP3 ENV：源头 + 键名 + 值注入
        v = checkEnv(r);
        if (!v.allowed) return v;

        // CP4 TIER：服务端独立定级，写动词必须有审批，声明说谎单独拒绝
        v = checkTier(r);
        if (!v.allowed) return v;

        // CP5 SUBJ：issuer + subject 绑定
        v = checkSubject(r);
        if (!v.allowed) return v;

        // CP6 AUDIT：append-only 脱敏
        appendAudit(r);
        return Verdict.allow();
    }

    // ---------- CP2 命令闸 ----------
    private static Verdict checkCommand(Request r) {
        if (r.command == null || r.command.isEmpty()) {
            return Verdict.deny(Code.CMD_NOT_ALLOWLISTED, "empty command");
        }
        String c = r.command.trim().toLowerCase(Locale.ROOT).split("\\s+")[0];
        if (!CMD_ALLOWLIST.contains(c)) {
            // /bin/sh 之类解释器即便被塞进白名单名后面也按解释器拒绝
            if (INTERPRETERS.contains(c) || INTERPRETERS.contains(r.command.trim())) {
                return Verdict.deny(Code.CMD_INTERPRETER, "interpreter direct exec: " + c);
            }
            return Verdict.deny(Code.CMD_NOT_ALLOWLISTED, "not in allowlist: " + c);
        }
        if (INTERPRETERS.contains(r.command.trim())) {
            return Verdict.deny(Code.CMD_INTERPRETER, "interpreter direct exec");
        }
        if (WRITE_OPS.contains(r.operation) && !r.admin && r.command.equals("write_file")) {
            return Verdict.deny(Code.CMD_NON_ADMIN_REGISTER, "non-admin write registration");
        }
        for (String a : r.argv == null ? List.<String>of() : r.argv) {
            if (a == null) continue;
            if (a.indexOf(';') >= 0 || a.indexOf('|') >= 0 || a.indexOf('&') >= 0
                    || a.indexOf('`') >= 0 || a.indexOf('$') >= 0 || a.indexOf('\n') >= 0
                    || a.indexOf('\r') >= 0 || a.indexOf('>') >= 0 || a.indexOf('<') >= 0) {
                return Verdict.deny(Code.CMD_SHELL_META, "shell metachar in argv");
            }
        }
        return Verdict.allow();
    }

    // ---------- CP1 路径闸 ----------
    private static Verdict checkPath(Request r) {
        if (r.targetPath == null || r.targetPath.isEmpty()) return Verdict.allow();
        String p = r.targetPath;
        for (int i = 0; i < p.length(); i++) {
            char ch = p.charAt(i);
            if (ch < 0x20 || ch == 0x7f) {
                return Verdict.deny(Code.ESCAPE_CONTROL_CHAR, "control char in path");
            }
        }
        // 绝对路径必须在 confinement root 内（对位 Gortex：read 查 confinement，write 不查）
        if (p.startsWith("/") || p.matches("^[A-Za-z]:\\\\.*")) {
            String norm = normalize(p);
            if (!norm.equals(CONFINEMENT_ROOT) && !norm.startsWith(CONFINEMENT_ROOT + "/")) {
                return Verdict.deny(Code.ESCAPE_ABSOLUTE_PATH, "absolute path outside root: " + p);
            }
        } else {
            String norm = normalize(CONFINEMENT_ROOT + "/" + p);
            if (!norm.startsWith(CONFINEMENT_ROOT + "/")) {
                return Verdict.deny(Code.ESCAPE_PATH_TRAVERSAL, "traversal outside root: " + p);
            }
        }
        String leaf = p.substring(p.lastIndexOf('/') + 1);
        if (r.symlinks != null && (r.symlinks.contains(p) || r.symlinks.contains(leaf))) {
            return Verdict.deny(Code.ESCAPE_SYMLINK, "symlink escape: " + leaf);
        }
        return Verdict.allow();
    }

    private static String normalize(String p) {
        String[] parts = p.split("/");
        List<String> out = new ArrayList<>();
        for (String s : parts) {
            if (s.isEmpty() || s.equals(".")) continue;
            if (s.equals("..")) { if (!out.isEmpty()) out.remove(out.size() - 1); continue; }
            out.add(s);
        }
        return "/" + String.join("/", out);
    }

    // ---------- CP3 环境闸 ----------
    private static Verdict checkEnv(Request r) {
        if (r.envFromFile && (r.envSource == null || !ENV_SOURCE_ALLOWLIST.contains(r.envSource))) {
            return Verdict.deny(Code.ENV_UNTRUSTED_SOURCE, "env source not allowlisted: " + r.envSource);
        }
        if (r.env == null) return Verdict.allow();
        for (Map.Entry<String, String> e : r.env.entrySet()) {
            String k = e.getKey() == null ? "" : e.getKey().trim().toUpperCase(Locale.ROOT);
            if (DANGEROUS_ENV_KEYS.contains(k)) {
                return Verdict.deny(Code.ENV_DANGEROUS_KEY, "dangerous env key: " + k);
            }
            String val = e.getValue() == null ? "" : e.getValue();
            if (val.contains("$(") || val.contains("${") || val.indexOf('`') >= 0
                    || val.contains("||") || val.contains("&&")) {
                return Verdict.deny(Code.ENV_INJECTION_VALUE, "command substitution in env value");
            }
        }
        return Verdict.allow();
    }

    // ---------- CP4 独立定级闸 ----------
    private static Verdict checkTier(Request r) {
        boolean serverSideWrite = WRITE_OPS.contains(r.operation);
        if (serverSideWrite && r.claimedReadOnly) {
            return Verdict.deny(Code.READ_ONLY_LIE, "write op declared readOnlyHint=true");
        }
        if (serverSideWrite && (r.approvalToken == null || !APPROVAL_TOKEN.equals(r.approvalToken))) {
            return Verdict.deny(Code.WRITE_TIER_NEEDS_APPROVAL, "write op lacks valid approval");
        }
        return Verdict.allow();
    }

    // ---------- CP5 会话主体闸 ----------
    private static Verdict checkSubject(Request r) {
        if (r.sessionSubject == null || r.sessionSubject.isEmpty()) {
            return Verdict.deny(Code.SUBJECT_UNBOUND, "session subject missing");
        }
        if (r.authClientId != null && SHARED_CLIENTS.contains(r.authClientId)) {
            if (r.sessionSubject.equals(r.authClientId)) {
                return Verdict.deny(Code.SHARED_CLIENT_NO_SUBJECT, "shared client without per-user subject");
            }
        }
        if (r.sessionIssuer == null || !EXPECTED_ISSUER.equals(r.sessionIssuer)) {
            return Verdict.deny(Code.ISSUER_MISMATCH, "issuer not expected AS");
        }
        if (r.principal != null && !r.principal.equals(r.sessionSubject)) {
            return Verdict.deny(Code.SUBJECT_MISMATCH, "principal != session subject");
        }
        return Verdict.allow();
    }

    // ---------- CP6 脱敏审计 ----------
    private static void appendAudit(Request r) {
        StringBuilder sb = new StringBuilder();
        sb.append(Instant.now().toString()).append(" op=").append(safe(r.operation))
          .append(" cmd=").append(safe(r.command))
          .append(" path=").append(safe(normForAudit(r.targetPath)))
          .append(" subject=").append(mask(r.sessionSubject))
          .append(" issuer=").append(safe(r.sessionIssuer))
          .append(" envKeys=").append(r.env == null ? "[]" : r.env.keySet());
        AUDIT.add(sb.toString()); // append-only：无删除接口
    }

    private static String normForAudit(String p) {
        if (p == null) return "-";
        return normalize(p.startsWith("/") ? p : CONFINEMENT_ROOT + "/" + p);
    }

    private static String safe(String s) {
        if (s == null || s.isEmpty()) return "-";
        return s.replaceAll("[\\r\\n]", "_");
    }

    private static String mask(String s) {
        if (s == null || s.isEmpty()) return "-";
        if (s.length() <= 2) return "**";
        return s.charAt(0) + "***" + s.charAt(s.length() - 1);
    }

    public static List<String> auditLog() { return Collections.unmodifiableList(AUDIT); }

    // ================= main 自检 =================
    public static void main(String[] args) {
        int attackTotal = 0, attackBlocked = 0, legacyMiss = 0;
        int controlTotal = 0, controlPass = 0, boundary = 0, falsePos = 0;

        // ---------- 20 attack（应全拦） ----------
        List<Request> attacks = new ArrayList<>();
        Request a;

        a = base(); a.claimedReadOnly = true; a.path("/etc/passwd"); attacks.add(a);            // 1 绝对路径逃逸
        a = base(); a.claimedReadOnly = true; a.path("../secrets.txt"); attacks.add(a);          // 2 .. 遍历
        a = base(); a.claimedReadOnly = true; a.path("link/config"); a.symlinks.add("link/config"); attacks.add(a); // 3 符号链接
        a = base(); a.claimedReadOnly = true; a.path("report.csv" + (char) 0 + ".png"); attacks.add(a); // 4 控制字符
        a = base(); a.claimedReadOnly = true; a.cmd("curl"); attacks.add(a);                      // 5 命令不在白名单
        a = base(); a.claimedReadOnly = true; a.cmd("/bin/sh"); attacks.add(a);                   // 6 解释器直执（MCPHub）
        a = base(); a.claimedReadOnly = true; a.argv.add("x; rm -rf /"); attacks.add(a);          // 7 argv shell 元字符
        a = base(); a.claimedReadOnly = true; a.env.put("LD_PRELOAD", "/tmp/evil.so"); attacks.add(a); // 8 危险 env（Hermes）
        a = base(); a.claimedReadOnly = true; a.envFromFile = true; a.envSource = "/tmp/agent.env"; attacks.add(a); // 9 env 源头不可信
        a = base(); a.claimedReadOnly = true; a.env.put("NODE_OPTIONS", "$(curl evil)"); attacks.add(a); // 10 env 值命令替换
        a = base(); a.op("write_file"); a.cmd("write_file"); a.claimedReadOnly = true;
        a.approvalToken = APPROVAL_TOKEN; a.path("out.csv"); attacks.add(a);                       // 11 只读声明说谎（DOMShell/Gortex）
        a = base(); a.op("write_file"); a.cmd("write_file"); a.claimedReadOnly = false;
        a.approvalToken = ""; a.path("out.csv"); attacks.add(a);                                  // 12 写动词无审批
        a = base(); a.claimedReadOnly = true; a.sessionSubject = ""; attacks.add(a);              // 13 subject 未绑定
        a = base(); a.claimedReadOnly = true; a.authClientId = "shared-gateway";
        a.sessionSubject = "shared-gateway"; attacks.add(a);                                      // 14 共享 client 无 per-user subject
        a = base(); a.claimedReadOnly = true; a.sessionIssuer = "https://attacker.as"; attacks.add(a); // 15 issuer 错投
        a = base(); a.claimedReadOnly = true; a.principal = "alice"; a.sessionSubject = "bob"; attacks.add(a); // 16 主体不一致
        a = base(); a.claimedReadOnly = true; a.op("add_attachment"); a.cmd("add_attachment");
        a.approvalToken = APPROVAL_TOKEN; a.path("/home/user/.ssh/id_rsa"); attacks.add(a);       // 17 任意文件外发（yt-mcp）
        a = base(); a.claimedReadOnly = true; a.cmd("bash"); attacks.add(a);                      // 18 解释器变体
        a = base(); a.claimedReadOnly = true; a.env.put("DYLD_INSERT_LIBRARIES", "/tmp/x.dylib"); attacks.add(a); // 19 危险 env 变体
        a = base(); a.claimedReadOnly = true; a.op("eval"); a.cmd("read_file");
        a.approvalToken = APPROVAL_TOKEN; attacks.add(a);                                         // 20 CDP eval 标只读

        for (Request r : attacks) {
            attackTotal++;
            Verdict got = decide(r);
            if (!got.allowed) attackBlocked++;
            else System.out.println("FAIL attack allowed: " + r.operation + " " + r.command);
            if (legacyBaseline(r).allowed) legacyMiss++;
        }

        // ---------- 4 control（应全放行，0 误杀） ----------
        List<Request> controls = new ArrayList<>();
        Request c;
        c = base(); c.claimedReadOnly = true; c.path("report.csv"); controls.add(c);             // C1 只读读文件
        c = base(); c.op("write_file"); c.cmd("write_file"); c.claimedReadOnly = false;
        c.approvalToken = APPROVAL_TOKEN; c.path("/srv/mcp-data/out.csv"); controls.add(c);        // C2 带审批的合法写
        c = base(); c.claimedReadOnly = true; c.cmd("search"); c.argv.add("mcp"); controls.add(c);// C3 搜索
        c = base(); c.op("add_attachment"); c.cmd("add_attachment"); c.claimedReadOnly = false;
        c.approvalToken = APPROVAL_TOKEN; c.path("/srv/mcp-data/att.pdf"); controls.add(c);        // C4 范围内附件
        for (Request r : controls) {
            controlTotal++;
            Verdict got = decide(r);
            if (got.allowed) controlPass++;
            else { falsePos++; System.out.println("FAIL control denied: " + got); }
        }

        // ---------- 2 boundary ----------
        // B1：审计脱敏 —— env 原始值与 argv 凭据不得进审计行
        Request rb = base(); rb.claimedReadOnly = true; rb.env.put("API_KEY", "s3cr3t-value");
        decide(rb);
        String joined = String.join("\n", AUDIT);
        if (!joined.contains("s3cr3t-value") && !joined.contains("alice")) {
            // subject 只应出现掩码 a***e
            boundary++;
        } else {
            System.out.println("FAIL boundary B1: audit leaked secret or raw subject");
        }
        // B2：空请求 fail-closed
        Verdict empty = decide(new Request());
        if (!empty.allowed) boundary++; else System.out.println("FAIL boundary B2: empty request allowed");

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
        r.principal = "alice";
        r.sessionSubject = "alice";
        r.sessionIssuer = EXPECTED_ISSUER;
        r.sessionId = "s-7f3a-uid";
        r.authClientId = "claude-desktop";
        r.admin = true;
        r.claimedReadOnly = true;
        r.operation = "read_file";
        r.command = "read_file";
        r.targetPath = "report.csv";
        return r;
    }
}
