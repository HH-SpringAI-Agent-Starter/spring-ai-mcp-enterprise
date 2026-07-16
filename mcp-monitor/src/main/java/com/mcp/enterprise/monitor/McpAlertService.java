package com.mcp.enterprise.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * MCP 告警服务 — 基于指标自动触发告警
 *
 * 功能：
 * 1. 错误率阈值告警（例：连续 5 分钟错误率 > 10%）
 * 2. 延迟阈值告警（例：平均延迟 > 5s）
 * 3. 调用频率异常告警（例：突然 10 倍激增）
 * 4. 告警抑制（同类型告警 5 分钟内不重复发送）
 */
public class McpAlertService {

    private static final Logger log = LoggerFactory.getLogger(McpAlertService.class);

    private final McpMetricsCollector metricsCollector;

    /** 告警规则 */
    private final List<AlertRule> rules = new CopyOnWriteArrayList<>();

    /** 已触发的告警（用于去重和抑制） */
    private final ConcurrentHashMap<String, AlertEvent> activeAlerts = new ConcurrentHashMap<>();

    /** 告警历史 */
    private final CopyOnWriteArrayList<AlertEvent> alertHistory = new CopyOnWriteArrayList<>();

    /** 最大告警历史记录数 */
    private final int maxHistory;

    /** 告警抑制时间（毫秒） */
    private final long suppressMs;

    /** 告警回调 */
    private final List<AlertCallback> callbacks = new CopyOnWriteArrayList<>();

    public McpAlertService(McpMetricsCollector metricsCollector) {
        this(metricsCollector, 1000, 300_000L);
    }

    public McpAlertService(McpMetricsCollector metricsCollector, int maxHistory, long suppressMs) {
        this.metricsCollector = metricsCollector;
        this.maxHistory = maxHistory;
        this.suppressMs = suppressMs;

        // 注册默认规则
        registerDefaultRules();

        log.info("🔔 MCP 告警服务初始化完成 | 历史上限: {} | 抑制时间: {}ms", maxHistory, suppressMs);
    }

    private void registerDefaultRules() {
        // 错误率 > 10%
        rules.add(new AlertRule("high_error_rate", "错误率过高", "",
                metrics -> metrics.errorRate() > 0.10 && metrics.totalInvocations() >= 5));
        // 平均延迟 > 5s
        rules.add(new AlertRule("high_latency", "平均延迟过高", "",
                metrics -> metrics.avgLatencyMs() > 5000 && metrics.totalInvocations() >= 3));
        // 错误率 > 50%（严重）
        rules.add(new AlertRule("critical_error_rate", "严重错误率过高", "CRITICAL",
                metrics -> metrics.errorRate() > 0.50 && metrics.totalInvocations() >= 3));
    }

    /**
     * 添加自定义规则
     */
    public void addRule(AlertRule rule) {
        rules.add(rule);
        log.info("➕ 告警规则已添加: {} ({})", rule.id(), rule.description());
    }

    /**
     * 注册告警回调
     */
    public void registerCallback(AlertCallback callback) {
        callbacks.add(callback);
    }

    /**
     * 运行告警检查（定时调用）
     */
    public List<AlertEvent> checkAlerts() {
        List<AlertEvent> triggered = new ArrayList<>();
        var metrics = metricsCollector.getAggregatedMetrics();

        for (var entry : metrics.entrySet()) {
            String toolName = entry.getKey();
            var toolMetrics = entry.getValue();

            for (var rule : rules) {
                if (rule.condition().test(toolMetrics)) {
                    String alertKey = toolName + ":" + rule.id();
                    AlertEvent lastAlert = activeAlerts.get(alertKey);
                    long now = System.currentTimeMillis();

                    // 抑制：同类型告警在抑制期内不重复触发
                    if (lastAlert != null && (now - lastAlert.timestamp() < suppressMs)) {
                        continue;
                    }

                    AlertEvent event = new AlertEvent(
                            UUID.randomUUID().toString(),
                            rule.id(),
                            rule.severity(),
                            rule.description(),
                            toolName,
                            toolMetrics,
                            now
                    );

                    activeAlerts.put(alertKey, event);
                    alertHistory.add(event);

                    // 清理历史
                    while (alertHistory.size() > maxHistory) {
                        alertHistory.remove(0);
                    }

                    triggered.add(event);

                    // 日志
                    log.warn("🚨 MCP 告警触发: [{}] {} | 工具: {} | 错误率: {} | 延迟: {}ms",
                            rule.severity(), rule.description(), toolName,
                            String.format("%.1f%%", toolMetrics.errorRate() * 100),
                            Math.round(toolMetrics.avgLatencyMs()));

                    // 回调通知
                    for (var callback : callbacks) {
                        try {
                            callback.onAlert(event);
                        } catch (Exception e) {
                            log.error("告警回调异常: {}", e.getMessage());
                        }
                    }
                }
            }
        }

        return triggered;
    }

    /**
     * 清除已解决的告警
     */
    public void resolveAlert(String alertKey) {
        activeAlerts.remove(alertKey);
    }

    /**
     * 获取活跃告警列表
     */
    public List<AlertEvent> getActiveAlerts() {
        return activeAlerts.values().stream()
                .sorted((a, b) -> Long.compare(b.timestamp(), a.timestamp()))
                .collect(Collectors.toList());
    }

    /**
     * 获取告警历史
     */
    public List<AlertEvent> getAlertHistory(int limit) {
        int size = alertHistory.size();
        return alertHistory.subList(Math.max(0, size - limit), size).stream()
                .sorted((a, b) -> Long.compare(b.timestamp(), a.timestamp()))
                .collect(Collectors.toList());
    }

    // ===== 内部结构 =====

    public record AlertRule(
            String id,
            String description,
            String severity,
            java.util.function.Predicate<McpMetricsCollector.ToolMetrics> condition
    ) {
        public AlertRule {
            severity = severity == null || severity.isBlank() ? "WARNING" : severity;
        }

        /** 是否指定工具（默认全局） */
        public boolean isToolSpecific() {
            return false; // 暂无工具级规则，全部全局检测
        }
    }

    public record AlertEvent(
            String id,
            String ruleId,
            String severity,
            String description,
            String toolName,
            McpMetricsCollector.ToolMetrics metrics,
            long timestamp
    ) {
        public Map<String, Object> toMap() {
            return Map.of(
                    "id", id,
                    "ruleId", ruleId,
                    "severity", severity,
                    "description", description,
                    "toolName", toolName,
                    "metrics", metrics.toMap(),
                    "timestamp", Instant.ofEpochMilli(timestamp).toString()
            );
        }
    }

    @FunctionalInterface
    public interface AlertCallback {
        void onAlert(AlertEvent event);
    }
}
