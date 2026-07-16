package com.mcp.enterprise.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * MCP 监控 REST 控制器 — 提供监控数据查询接口
 *
 * 端点：
 *   GET  /api/monitor/metrics       — 获取全量聚合指标
 *   GET  /api/monitor/metrics/{tool} — 获取指定工具指标
 *   GET  /api/monitor/audit          — 获取最近审计日志
 *   GET  /api/monitor/audit/failed   — 获取失败审计记录
 *   GET  /api/monitor/alerts         — 获取活跃告警
 *   GET  /api/monitor/alerts/history  — 获取告警历史
 *   POST /api/monitor/alerts/check   — 手动触发告警检查
 *   GET  /api/monitor/summary        — 获取摘要统计
 */
@RestController
@RequestMapping("/api/monitor")
@ConditionalOnProperty(prefix = "mcp.enterprise.monitor", name = "enabled", havingValue = "true", matchIfMissing = true)
public class McpMonitorController {

    private static final Logger log = LoggerFactory.getLogger(McpMonitorController.class);

    private final McpMetricsCollector metricsCollector;
    private final McpAuditLogger auditLogger;
    private final McpAlertService alertService;

    public McpMonitorController(McpMetricsCollector metricsCollector,
                                 McpAuditLogger auditLogger,
                                 McpAlertService alertService) {
        this.metricsCollector = metricsCollector;
        this.auditLogger = auditLogger;
        this.alertService = alertService;
        log.info("📊 MCP 监控控制器已启动");
    }

    // ===== 指标 =====

    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getMetrics() {
        var aggregated = metricsCollector.getAggregatedMetrics();
        var result = new java.util.LinkedHashMap<String, Object>();
        result.put("tools", aggregated.values().stream()
                .map(McpMetricsCollector.ToolMetrics::toMap)
                .toList());
        result.put("total", aggregated.size());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/metrics/{tool}")
    public ResponseEntity<?> getToolMetrics(@PathVariable String tool) {
        var metrics = metricsCollector.getAggregatedMetrics().get(tool);
        if (metrics == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(metrics.toMap());
    }

    // ===== 审计 =====

    @GetMapping("/audit")
    public ResponseEntity<Map<String, Object>> getAuditLog(
            @RequestParam(defaultValue = "50") int limit) {
        var entries = auditLogger.getRecent(Math.min(limit, 500));
        return ResponseEntity.ok(Map.of(
                "entries", entries.stream().map(McpAuditLogger.AuditEntry::toMap).toList(),
                "total", entries.size()
        ));
    }

    @GetMapping("/audit/failed")
    public ResponseEntity<Map<String, Object>> getFailedAudit(
            @RequestParam(defaultValue = "50") int limit) {
        var entries = auditLogger.getFailedEntries(Math.min(limit, 500));
        return ResponseEntity.ok(Map.of(
                "entries", entries.stream().map(McpAuditLogger.AuditEntry::toMap).toList(),
                "total", entries.size()
        ));
    }

    // ===== 告警 =====

    @GetMapping("/alerts")
    public ResponseEntity<Map<String, Object>> getActiveAlerts() {
        var alerts = alertService.getActiveAlerts();
        return ResponseEntity.ok(Map.of(
                "alerts", alerts.stream().map(McpAlertService.AlertEvent::toMap).toList(),
                "active", alerts.size()
        ));
    }

    @GetMapping("/alerts/history")
    public ResponseEntity<Map<String, Object>> getAlertHistory(
            @RequestParam(defaultValue = "50") int limit) {
        var history = alertService.getAlertHistory(Math.min(limit, 500));
        return ResponseEntity.ok(Map.of(
                "alerts", history.stream().map(McpAlertService.AlertEvent::toMap).toList(),
                "total", history.size()
        ));
    }

    @PostMapping("/alerts/check")
    public ResponseEntity<Map<String, Object>> checkAlerts() {
        var triggered = alertService.checkAlerts();
        return ResponseEntity.ok(Map.of(
                "triggered", triggered.stream().map(McpAlertService.AlertEvent::toMap).toList(),
                "count", triggered.size()
        ));
    }

    // ===== 摘要 =====

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        var summary = metricsCollector.getSummarySnapshot();
        summary.put("audit", auditLogger.getStats());
        summary.put("activeAlerts", alertService.getActiveAlerts().size());
        return ResponseEntity.ok(summary);
    }
}
