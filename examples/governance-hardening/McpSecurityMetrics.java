package com.example.mcp.hardening;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@code mcp_security_*} 埋点契约的**可运行实现**（V1.66，兑现 V1.65 待办 ④）。
 *
 * <p>背景：V1.65 的 {@code examples/monitor-alert-rules/} 定义了 10 个安全指标与
 * Prometheus/Alertmanager 告警规则，但诚实标注了「{@code mcp-monitor} 尚未导出这些指标、
 * 需由网关过滤器在拒绝路径上埋点」。本文件把该契约补成零依赖实现，并挂进
 * {@link McpGatewayGuardFilter} 的每个拒绝分支，使告警规则开箱可用。</p>
 *
 * <p><b>为什么单独写一份、不直接改 mcp-monitor：</b>本目录是 examples/ 参考实现，
 * 不进 Maven reactor、不参与 CI 构建，避免给 21 模块主构建引入风险；集成时只需把
 * {@link #exportPrometheus()} 挂到现有 actuator endpoint 或替换为 Micrometer
 * {@code MeterRegistry} 即可。指标名 / label 与
 * {@code examples/monitor-alert-rules/mcp-security-alerts.yml} 逐字一致。</p>
 *
 * <p>线程安全：{@link ConcurrentHashMap} + {@link AtomicLong}；拒绝路径为高频热点，
 * 只做一次 map lookup 与一次 increment，不做字符串拼接以外的 I/O。</p>
 *
 * <pre>{@code
 * // Spring 装配示例
 * @Bean McpSecurityMetrics mcpSecurityMetrics() { return new McpSecurityMetrics(); }
 * @Bean FilterRegistrationBean<...> ...  // 把实例注入 McpGatewayGuardFilter
 * @GetMapping("/actuator/prometheus-mcp-security")
 * String export() { return metrics.exportPrometheus(); }
 * }</pre>
 *
 * ⚠️ 参考实现（REFERENCE ONLY），位于 examples/，不在 Maven reactor 中。
 *
 * @see McpGatewayGuardFilter
 */
public final class McpSecurityMetrics {

    private static final String PREFIX = "mcp_security_";

    private final ConcurrentMap<SeriesKey, AtomicLong> series = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, MetricMeta> meta = new ConcurrentHashMap<>();

    public McpSecurityMetrics() {
        counter("rejections_total", "reason", "tool", "tenant");
        counter("short_bearer_total", "tenant");
        counter("registration_rejected_total", "reason", "tenant");
        counter("host_mismatch_total", "tenant");
        counter("egress_blocked_total", "host", "tool");
        counter("credential_access_total", "principal", "tool");
        counter("child_process_total", "parent", "exe");
        counter("body_too_large_total", "tenant");
        counter("tool_enabled_total", "tool", "actor");
        gauge("pending_approvals", "risk_tier");
    }

    // ------------------------------------------------------------------
    // 埋点 API —— 与 examples/monitor-alert-rules/README.md 埋点契约一一对应
    // ------------------------------------------------------------------

    /** 每次 fail-closed 拒绝（401 / 403 / 413 / 202 待审批）都必须调用。 */
    public void rejection(String reason, String tool, String tenant) {
        inc("rejections_total", labels("reason", reason, "tool", tool, "tenant", tenant));
    }

    /** {@code Authorization: Bearer x} 一类短令牌（CVE-2026-59822）。 */
    public void shortBearer(String tenant) {
        inc("short_bearer_total", labels("tenant", tenant));
    }

    /** 注册请求含 {@code command} / shell 字段，或默认 disabled 的工具被尝试启用。 */
    public void registrationRejected(String reason, String tenant) {
        inc("registration_rejected_total", labels("reason", reason, "tenant", tenant));
    }

    /** Host / X-Forwarded-Host 不一致（CVE-2026-48710）。 */
    public void hostMismatch(String tenant) {
        inc("host_mismatch_total", labels("tenant", tenant));
    }

    /** 出站白名单拒绝：非同源重定向、DNS 私网地址、云元数据端点。 */
    public void egressBlocked(String host, String tool) {
        inc("egress_blocked_total", labels("host", host, "tool", tool));
    }

    /** 凭据代理取用（短令牌 / provider secret）；短时突增即告警。 */
    public void credentialAccess(String principal, String tool) {
        inc("credential_access_total", labels("principal", principal, "tool", tool));
    }

    /** 非白名单子进程派生（对标 XMRig 藏 {@code .claude/unicorn}）。 */
    public void childProcess(String parent, String exe) {
        inc("child_process_total", labels("parent", parent, "exe", exe));
    }

    /** 请求体超 4 MiB（CVE-2026-108395）。 */
    public void bodyTooLarge(String tenant) {
        inc("body_too_large_total", labels("tenant", tenant));
    }

    /** 工具从 disabled → enabled 的准入变更；短时批量启用＝注册准入被绕过。 */
    public void toolEnabled(String tool, String actor) {
        inc("tool_enabled_total", labels("tool", tool, "actor", actor));
    }

    /** HITL 审批单创建 / 结单；delta = +1 创建、-1 结单。 */
    public void pendingApproval(String riskTier, int delta) {
        add("pending_approvals", labels("risk_tier", riskTier), delta);
    }

    // ------------------------------------------------------------------
    // 导出
    // ------------------------------------------------------------------

    /** Prometheus 文本格式，可直接被 {@code mcp-security-alerts.yml} 的规则查询。 */
    public String exportPrometheus() {
        List<Map.Entry<SeriesKey, AtomicLong>> rows = new ArrayList<>(series.entrySet());
        rows.sort((a, b) -> {
            int byName = a.getKey().name.compareTo(b.getKey().name);
            return byName != 0 ? byName : a.getKey().labels.toString().compareTo(b.getKey().labels.toString());
        });

        StringBuilder sb = new StringBuilder();
        String current = null;
        for (Map.Entry<SeriesKey, AtomicLong> row : rows) {
            SeriesKey key = row.getKey();
            if (!key.name.equals(current)) {
                current = key.name;
                MetricMeta m = meta.get(current);
                sb.append("# HELP ").append(PREFIX).append(current).append(' ')
                  .append(m == null ? "" : m.help).append('\n');
                sb.append("# TYPE ").append(PREFIX).append(current).append(' ')
                  .append(m == null ? "counter" : m.type).append('\n');
            }
            sb.append(PREFIX).append(current);
            if (!key.labels.isEmpty()) {
                sb.append('{');
                boolean first = true;
                for (Map.Entry<String, String> e : key.labels.entrySet()) {
                    if (!first) sb.append(',');
                    first = false;
                    sb.append(e.getKey()).append("=\"").append(escape(e.getValue())).append('"');
                }
                sb.append('}');
            }
            sb.append(' ').append(row.getValue().get()).append('\n');
        }
        return sb.toString();
    }

    /** 便于单元测试 / 自检：返回 {@code 指标名{labels} -> 值} 快照。 */
    public Map<String, Long> snapshot() {
        Map<String, Long> out = new LinkedHashMap<>();
        series.forEach((k, v) -> out.put(PREFIX + k.name + k.labels, v.get()));
        return Collections.unmodifiableMap(out);
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    private void counter(String name, String... labelNames) {
        meta.put(name, new MetricMeta("counter", "MCP security event: " + name));
    }

    private void gauge(String name, String... labelNames) {
        meta.put(name, new MetricMeta("gauge", "MCP security state: " + name));
    }

    private void inc(String name, LinkedHashMap<String, String> labels) {
        add(name, labels, 1L);
    }

    private void add(String name, LinkedHashMap<String, String> labels, long delta) {
        if (!meta.containsKey(name)) {
            throw new IllegalArgumentException("undeclared metric: " + name);
        }
        series.computeIfAbsent(new SeriesKey(name, labels), k -> new AtomicLong()).addAndGet(delta);
    }

    private static LinkedHashMap<String, String> labels(String... kv) {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1] == null ? "" : kv[i + 1]);
        }
        return m;
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private record MetricMeta(String type, String help) {}

    private record SeriesKey(String name, Map<String, String> labels) {}

    /** 自带对照组自检：合法事件必须计数、未声明指标必须被拒（防止 label 写错静默丢数据）。 */
    public static void main(String[] args) {
        McpSecurityMetrics m = new McpSecurityMetrics();
        m.rejection("BODY_LIMIT", "search", "t1");
        m.rejection("BODY_LIMIT", "search", "t1");
        m.shortBearer("t1");
        m.pendingApproval("high", 1);
        m.pendingApproval("high", -1);
        String out = m.exportPrometheus();
        assertContains(out, "mcp_security_rejections_total{reason=\"BODY_LIMIT\",tool=\"search\",tenant=\"t1\"} 2");
        assertContains(out, "mcp_security_short_bearer_total{tenant=\"t1\"} 1");
        assertContains(out, "mcp_security_pending_approvals{risk_tier=\"high\"} 0");
        // 对照组：未声明指标必须抛异常，而不是悄悄写进一个告警规则永远查不到的名字
        boolean rejected = false;
        try {
            m.add("typo_metric", labels("tenant", "t1"), 1);
        } catch (IllegalArgumentException e) {
            rejected = true;
        }
        if (!rejected) throw new AssertionError("undeclared metric was silently accepted");
        System.out.println("[McpSecurityMetrics] self-test OK, series=" + m.snapshot().size());
        System.out.print(out);
    }

    private static void assertContains(String text, String expected) {
        if (!text.contains(expected)) {
            throw new AssertionError("missing: " + expected + "\n---\n" + text);
        }
    }
}
