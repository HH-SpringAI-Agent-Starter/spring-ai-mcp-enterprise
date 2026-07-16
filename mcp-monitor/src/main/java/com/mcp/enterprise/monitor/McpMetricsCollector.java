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

        return Map.of(
                "totalCalls", totalCalls,
                "totalErrors", totalErrors,
                "errorRate", totalCalls > 0 ? (double) totalErrors / totalCalls : 0,
                "avgLatencyMs", Math.round(avgLatency * 100.0) / 100.0,
                "activeTools", activeTools,
                "timestamp", Instant.now().toString()
        );
    }

    // ===== 内部类 =====

    /**
     * 单次调用记录
     */
    public record InvocationRecord(String toolName, long latencyMs, boolean success, long timestamp) {}

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
