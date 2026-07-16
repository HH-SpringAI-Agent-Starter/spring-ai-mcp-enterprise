package com.mcp.enterprise.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * MCP 工具调用监控器 — 桥接 McpToolManager 和监控系统
 *
 * 自动拦截所有工具调用，记录：
 * 1. 调用指标（延迟、成功/失败）
 * 2. 审计日志（谁、什么、结果）
 * 3. 触发告警检查
 *
 * 使用方式：在 McpToolManager 的 invoke 方法周围包裹此监控器
 *
 * 示例：
 * ```java
 * monitor.wrap((toolName, params) -> toolManager.invoke(toolName, params));
 * ```
 */
public class McpToolInvocationMonitor {

    private static final Logger log = LoggerFactory.getLogger(McpToolInvocationMonitor.class);

    private final McpMetricsCollector metricsCollector;
    private final McpAuditLogger auditLogger;

    public McpToolInvocationMonitor(McpMetricsCollector metricsCollector, McpAuditLogger auditLogger) {
        this.metricsCollector = metricsCollector;
        this.auditLogger = auditLogger;
        log.info("🔍 MCP 工具调用监控器已启动");
    }

    /**
     * 在调用工具前后自动记录指标和审计信息
     *
     * @param sessionId   会话 ID
     * @param clientName  客户端名称
     * @param toolName    工具名称
     * @param params      调用参数
     * @param executor    实际执行函数（toolName, params -> Mono<result>）
     * @return Mono<result>
     */
    public Mono<Map<String, Object>> monitorInvocation(
            String sessionId,
            String clientName,
            String toolName,
            Map<String, Object> params,
            BiFunction<String, Map<String, Object>, Mono<Map<String, Object>>> executor) {

        long startTime = System.currentTimeMillis();
        String clientIp = "unknown"; // 可由过滤器注入

        return executor.apply(toolName, params)
                .doOnSuccess(result -> {
                    long latency = System.currentTimeMillis() - startTime;
                    boolean success = result != null && !Boolean.FALSE.equals(result.get("success"));

                    // 记录指标
                    metricsCollector.recordInvocation(toolName, latency, success);

                    // 记录审计
                    String summary = result != null ? result.toString() : "null";
                    auditLogger.log(sessionId, clientName, toolName, params, success, summary, latency, clientIp);
                })
                .doOnError(error -> {
                    long latency = System.currentTimeMillis() - startTime;

                    // 记录失败指标
                    metricsCollector.recordInvocation(toolName, latency, false);

                    // 记录失败审计
                    auditLogger.log(sessionId, clientName, toolName, params, false,
                            error.getMessage(), latency, clientIp);
                });
    }

    // ===== 便捷方法 =====

    public McpMetricsCollector getMetricsCollector() {
        return metricsCollector;
    }

    public McpAuditLogger getAuditLogger() {
        return auditLogger;
    }
}
