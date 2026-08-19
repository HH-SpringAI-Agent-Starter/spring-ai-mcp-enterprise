package com.mcp.enterprise.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * MCP 指标采集器 — 实时收集工具调用指标
 *
 * 功能：
 * 1. 按工具统计调用次数、错误次数、平均延迟
 * 2. 按时间窗口查询最新指标
 * 3. 支持滑动窗口（最近 N 秒数据）
 * 4. 自动清理过期数据
 */
public class McpMetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(McpMetricsCollector.class);

    /** 数据保留时间（毫秒） */
    private final long retentionMs;

    /** 按工具存储调用记录（线程安全双端队列，尾部插入，头部过期淘汰） */
    private final ConcurrentHashMap<String, ConcurrentLinkedDeque<InvocationRecord>> toolRecords = new ConcurrentHashMap<>();

    // ===== V1.6: 网关操作指标（Mcp-Method / Mcp-Name 标头） =====
    /** 按 操作:工具名 维度统计网关路由调用（2026-07-28 网关友好标头） */
    private final ConcurrentHashMap<String, GatewayOpMetrics> gatewayOpMetrics = new ConcurrentHashMap<>();

    /** 网关操作指标上限，防止内存无限增长 */
    private static final int MAX_GATEWAY_OPS = 500;

    /** 聚合缓存（最近一次的聚合结果） */
    private volatile Map<String, ToolMetrics> lastAggregated = Map.of();

    /** 最后聚合时间 */
    private volatile long lastAggregateTime = 0;

    /** 聚合间隔（毫秒） */
    private static final long AGGREGATE_INTERVAL_MS = 30_000; // 30秒

    public McpMetricsCollector() {
        this(3_600_000L); // 默认保留1小时
    }

    public McpMetricsCollector(long retentionMs) {
        this.retentionMs = retentionMs;
        log.info("📊 MCP 指标采集器初始化完成 | 数据保留: {}ms", retentionMs);
    }

    /**
     * 记录一次调用
     */
    public void recordInvocation(String toolName, long latencyMs, boolean success) {
        var records = toolRecords.computeIfAbsent(toolName, k -> new ConcurrentLinkedDeque<>());
        records.addLast(new InvocationRecord(toolName, latencyMs, success, System.currentTimeMillis()));
        evictExpired(toolName, records);
    }

    /**
     * 移除过期记录
     */
    private void evictExpired(String toolName, ConcurrentLinkedDeque<InvocationRecord> records) {
        long cutoff = System.currentTimeMillis() - retentionMs;
        while (!records.isEmpty() && records.peekFirst().timestamp < cutoff) {
            records.pollFirst();
        }
    }

    /**
     * 获取全量聚合指标（缓存加速）
     */
    public Map<String, ToolMetrics> getAggregatedMetrics() {
        long now = System.currentTimeMillis();
        if (now - lastAggregateTime > AGGREGATE_INTERVAL_MS) {
            lastAggregated = doAggregate();
            lastAggregateTime = now;
        }
        return lastAggregated;
    }

    /**
     * 强制重新计算全量指标
     */
    public Map<String, ToolMetrics> aggregateNow() {
        lastAggregated = doAggregate();
        lastAggregateTime = System.currentTimeMillis();
        return lastAggregated;
    }

    private Map<String, ToolMetrics> doAggregate() {
        long cutoff = System.currentTimeMillis() - retentionMs;
        Map<String, ToolMetrics> result = new LinkedHashMap<>();

        for (var entry : toolRecords.entrySet()) {
            String name = entry.getKey();
            var records = entry.getValue();

            if (records.isEmpty()) {
                result.put(name, ToolMetrics.empty(name));
                continue;
            }

            long totalLatency = 0;
            int count = 0;
            int errors = 0;
            long firstTime = Long.MAX_VALUE;
            long lastTime = 0;

            // 滑动窗口内统计
            for (var rec : records) {
                if (rec.timestamp < cutoff) continue;
                totalLatency += rec.latencyMs;
                count++;
                if (!rec.success) errors++;
                if (rec.timestamp < firstTime) firstTime = rec.timestamp;
                if (rec.timestamp > lastTime) lastTime = rec.timestamp;
            }

            double avgLatency = count > 0 ? (double) totalLatency / count : 0;
            double errorRate = count > 0 ? (double) errors / count : 0;

            result.put(name, new ToolMetrics(
                    name, count, errors, avgLatency, errorRate,
                    firstTime == Long.MAX_VALUE ? 0 : firstTime,
                    lastTime
            ));
        }

        return result;
    }

    /**
     * 获取错误率超过阈值的工具
     */
    public List<ToolMetrics> getToolsWithHighErrorRate(double threshold) {
        return getAggregatedMetrics().values().stream()
                .filter(m -> m.totalInvocations > 0 && m.errorRate >= threshold)
                .sorted((a, b) -> Double.compare(b.errorRate, a.errorRate))
                .collect(Collectors.toList());
    }

    /**
     * 获取总调用统计快照
     */
    public Map<String, Object> getSummarySnapshot() {
        var aggregated = getAggregatedMetrics();
        long totalCalls = aggregated.values().stream().mapToLong(m -> m.totalInvocations).sum();
        long totalErrors = aggregated.values().stream().mapToLong(m -> m.errors).sum();
        double avgLatency = aggregated.values().stream()
                .filter(m -> m.totalInvocations > 0)
                .mapToDouble(m -> m.avgLatencyMs * m.totalInvocations)
                .sum() / Math.max(totalCalls, 1);
        int activeTools = (int) aggregated.values().stream().filter(m -> m.totalInvocations > 0).count();

        // V1.6: 汇总网关操作数
        long gatewayOps = gatewayOpMetrics.values().stream().mapToLong(GatewayOpMetrics::totalInvocations).sum();

        return Map.of(
                "totalCalls", totalCalls,
                "totalErrors", totalErrors,
                "errorRate", totalCalls > 0 ? (double) totalErrors / totalCalls : 0,
                "avgLatencyMs", Math.round(avgLatency * 100.0) / 100.0,
                "activeTools", activeTools,
                "gatewayOperations", gatewayOpMetrics.size(),
                "gatewayInvocations", gatewayOps,
                "timestamp", Instant.now().toString()
        );
    }

    /**
     * 记录一次网关路由调用（V1.6：Mcp-Method/Mcp-Name 标头维度）
     *
     * @param method    Mcp-Method 标头值（tools/call、tools/list、initialize、ping 等）
     * @param name      Mcp-Name 标头值（工具/资源名，可为空）
     * @param latencyMs 处理耗时
     * @param success   是否成功
     */
    public void recordGatewayInvocation(String method, String name, long latencyMs, boolean success) {
        String methodKey = method == null || method.isBlank() ? "unknown" : method;
        String nameKey = name == null ? "" : name;
        String opKey = methodKey + ":" + nameKey;

        gatewayOpMetrics.compute(opKey, (k, v) -> {
            if (v == null) {
                v = new GatewayOpMetrics(methodKey, nameKey);
            }
            v.record(latencyMs, success);
            return v;
        });

        // 内存保护：超出上限时重置（网关指标为短周期观测数据，重置可接受）
        if (gatewayOpMetrics.size() > MAX_GATEWAY_OPS) {
            gatewayOpMetrics.clear();
            log.warn("网关操作指标超出上限({})，已重置", MAX_GATEWAY_OPS);
        }
    }

    /**
     * 获取网关路由指标快照（按操作字典序，确定性排序 — V1.5 规范）
     */
    public Map<String, Object> getGatewayMetricsSnapshot() {
        var list = gatewayOpMetrics.values().stream()
                .sorted(Comparator.comparing(GatewayOpMetrics::getMethod)
                        .thenComparing(GatewayOpMetrics::getName))
                .map(GatewayOpMetrics::toMap)
                .toList();
        long totalOps = gatewayOpMetrics.values().stream().mapToLong(GatewayOpMetrics::totalInvocations).sum();
        return Map.of(
                "operations", list,
                "total", list.size(),
                "totalInvocations", totalOps,
                "timestamp", Instant.now().toString()
        );
    }

    /** 清空网关操作指标 */
    public void resetGatewayMetrics() {
        gatewayOpMetrics.clear();
    }

    // ===== V1.7: Prometheus 文本格式导出 =====

    /**
     * 导出 Prometheus 文本格式指标（OpenMetrics 风格）
     * 供 Prometheus 抓取 / Grafana 可视化，兼容标准 scrape 语义。
     *
     * 指标名：
     *   mcp_tool_invocations_total{tool="..."}  — 工具调用总数
     *   mcp_tool_errors_total{tool="..."}       — 工具调用错误数
     *   mcp_tool_latency_ms{tool="..."}         — 工具平均延迟（毫秒）
     *   mcp_gateway_invocations_total{method="...",name="..."} — 网关操作调用数
     *   mcp_gateway_errors_total{method="...",name="..."}      — 网关操作错误数
     *   mcp_gateway_latency_ms{method="...",name="..."}        — 网关操作平均延迟
     *   mcp_build_info{version="..."}           — 版本信息
     */
    public String exportPrometheus() {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("# HELP mcp_tool_invocations_total Total tool invocations in retention window.\n");
        sb.append("# TYPE mcp_tool_invocations_total counter\n");
        var aggregated = getAggregatedMetrics();
        aggregated.values().stream()
                .filter(m -> m.totalInvocations > 0)
                .sorted(Comparator.comparing(McpMetricsCollector.ToolMetrics::toolName))
                .forEach(m -> sb.append("mcp_tool_invocations_total{tool=\"")
                        .append(escapeLabel(m.toolName())).append("\"} ")
                        .append(m.totalInvocations()).append('\n'));

        sb.append("# HELP mcp_tool_errors_total Total tool invocation errors in retention window.\n");
        sb.append("# TYPE mcp_tool_errors_total counter\n");
        aggregated.values().stream()
                .filter(m -> m.totalInvocations > 0)
                .sorted(Comparator.comparing(McpMetricsCollector.ToolMetrics::toolName))
                .forEach(m -> sb.append("mcp_tool_errors_total{tool=\"")
                        .append(escapeLabel(m.toolName())).append("\"} ")
                        .append(m.errors()).append('\n'));

        sb.append("# HELP mcp_tool_latency_ms Average tool latency in milliseconds.\n");
        sb.append("# TYPE mcp_tool_latency_ms gauge\n");
        aggregated.values().stream()
                .filter(m -> m.totalInvocations > 0)
                .sorted(Comparator.comparing(McpMetricsCollector.ToolMetrics::toolName))
                .forEach(m -> sb.append("mcp_tool_latency_ms{tool=\"")
                        .append(escapeLabel(m.toolName())).append("\"} ")
                        .append(formatDouble(m.avgLatencyMs())).append('\n'));

        sb.append("# HELP mcp_gateway_invocations_total Gateway route invocations by Mcp-Method/Mcp-Name.\n");
        sb.append("# TYPE mcp_gateway_invocations_total counter\n");
        gatewayOpMetrics.values().stream()
                .sorted(Comparator.comparing(GatewayOpMetrics::getMethod).thenComparing(GatewayOpMetrics::getName))
                .forEach(m -> sb.append("mcp_gateway_invocations_total{method=\"")
                        .append(escapeLabel(m.getMethod())).append("\",name=\"")
                        .append(escapeLabel(m.getName())).append("\"} ")
                        .append(m.totalInvocations()).append('\n'));

        sb.append("# HELP mcp_gateway_errors_total Gateway route errors.\n");
        sb.append("# TYPE mcp_gateway_errors_total counter\n");
        gatewayOpMetrics.values().stream()
                .sorted(Comparator.comparing(GatewayOpMetrics::getMethod).thenComparing(GatewayOpMetrics::getName))
                .forEach(m -> sb.append("mcp_gateway_errors_total{method=\"")
                        .append(escapeLabel(m.getMethod())).append("\",name=\"")
                        .append(escapeLabel(m.getName())).append("\"} ")
                        .append(m.errors()).append('\n'));

        sb.append("# HELP mcp_gateway_latency_ms Average gateway route latency in milliseconds.\n");
        sb.append("# TYPE mcp_gateway_latency_ms gauge\n");
        gatewayOpMetrics.values().stream()
                .sorted(Comparator.comparing(GatewayOpMetrics::getMethod).thenComparing(GatewayOpMetrics::getName))
                .forEach(m -> {
                    long total = m.totalInvocations();
                    double avg = total > 0 ? gatewayOpAvgLatencyMs(m) : 0;
                    sb.append("mcp_gateway_latency_ms{method=\"")
                            .append(escapeLabel(m.getMethod())).append("\",name=\"")
                            .append(escapeLabel(m.getName())).append("\"} ")
                            .append(formatDouble(avg)).append('\n');
                });

        sb.append("# HELP mcp_build_info MCP Enterprise build information.\n");
        sb.append("# TYPE mcp_build_info gauge\n");
        sb.append("mcp_build_info{version=\"1.1.0\"} 1\n");
        return sb.toString();
    }

    /** Prometheus 标签值转义（\ → \\，" → \"，\n → \\n） */
    private static String escapeLabel(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
    }

    /** 浮点格式化：去掉尾部无效 0，整数不带小数点 */
    private static String formatDouble(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.ROOT, "%.3f", value);
    }

    /** 网关操作平均延迟（供 Prometheus 导出使用） */
    public double gatewayOpAvgLatencyMs(GatewayOpMetrics m) {
        long total = m.totalInvocations();
        return total > 0 ? (double) m.totalLatencyMs.get() / total : 0;
    }

    // ===== 内部类 =====

    /**
     * 单次调用记录
     */
    public record InvocationRecord(String toolName, long latencyMs, boolean success, long timestamp) {}

    /**
     * 网关操作维度指标（V1.6：Mcp-Method/Mcp-Name 路由统计）
     */
    public static final class GatewayOpMetrics {
        private final String method;
        private final String name;
        private final AtomicLong totalInvocations = new AtomicLong();
        private final AtomicLong errors = new AtomicLong();
        private final AtomicLong totalLatencyMs = new AtomicLong();

        GatewayOpMetrics(String method, String name) {
            this.method = method;
            this.name = name;
        }

        void record(long latencyMs, boolean success) {
            totalInvocations.incrementAndGet();
            totalLatencyMs.addAndGet(latencyMs);
            if (!success) {
                errors.incrementAndGet();
            }
        }

        public String getMethod() { return method; }
        public String getName() { return name; }
        public long totalInvocations() { return totalInvocations.get(); }
        public long errors() { return errors.get(); }

        public Map<String, Object> toMap() {
            long total = totalInvocations.get();
            double avgLatency = total > 0 ? (double) totalLatencyMs.get() / total : 0;
            return Map.of(
                    "method", method,
                    "name", name,
                    "totalInvocations", total,
                    "errors", errors.get(),
                    "errorRate", total > 0 ? Math.round(errors.get() * 10000.0 / total) / 100.0 + "%" : "0%",
                    "avgLatencyMs", Math.round(avgLatency * 100.0) / 100.0
            );
        }
    }

    /**
     * 工具聚合指标
     */
    public record ToolMetrics(
            String toolName,
            long totalInvocations,
            long errors,
            double avgLatencyMs,
            double errorRate,
            long firstInvocationTime,
            long lastInvocationTime
    ) {
        static ToolMetrics empty(String name) {
            return new ToolMetrics(name, 0, 0, 0, 0, 0, 0);
        }

        public Map<String, Object> toMap() {
            return Map.of(
                    "toolName", toolName,
                    "totalInvocations", totalInvocations,
                    "errors", errors,
                    "avgLatencyMs", Math.round(avgLatencyMs * 100.0) / 100.0,
                    "errorRate", Math.round(errorRate * 10000.0) / 100.0 + "%",
                    "lastInvocationTime", lastInvocationTime > 0
                            ? Instant.ofEpochMilli(lastInvocationTime).toString()
                            : "N/A"
            );
        }
    }
}
