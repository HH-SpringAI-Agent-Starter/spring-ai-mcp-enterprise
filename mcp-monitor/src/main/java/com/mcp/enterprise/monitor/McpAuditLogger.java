package com.mcp.enterprise.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * MCP 审计日志 — 记录所有工具调用的安全审计信息
 *
 * 功能：
 * 1. 自动记录每次工具调用（谁、什么时间、调用了什么工具、参数、结果）
 * 2. 按工具/用户/时间段查询审计日志
 * 3. 内存环形缓冲区，自动淘汰最旧记录
 * 4. 可用于合规审查和安全追溯
 */
public class McpAuditLogger {

    private static final Logger log = LoggerFactory.getLogger(McpAuditLogger.class);

    /** 最大保留审计记录数 */
    private final int maxEntries;

    /** 环形缓冲区 — 线程安全写 */
    private final CopyOnWriteArrayList<AuditEntry> entries = new CopyOnWriteArrayList<>();

    /** 是否记录参数详情（生产环境建议关闭） */
    private final boolean logParams;

    /** 是否记录审计到 SLF4J */
    private final boolean logToSlf4j;

    public McpAuditLogger() {
        this(10_000, false, true); // 默认 1 万条，不记录参数，记录到 SLF4J
    }

    public McpAuditLogger(int maxEntries, boolean logParams, boolean logToSlf4j) {
        this.maxEntries = maxEntries;
        this.logParams = logParams;
        this.logToSlf4j = logToSlf4j;
        log.info("📝 MCP 审计日志初始化完成 | 最大记录: {} | 参数记录: {} | SLF4J: {}",
                maxEntries, logParams, logToSlf4j);
    }

    /**
     * 记录审计条目
     */
    public void log(String sessionId, String clientName, String toolName,
                    Map<String, Object> params, boolean success, String resultSummary,
                    long latencyMs, String clientIp) {
        // 清理参数中的敏感信息
        Map<String, Object> safeParams = logParams ? sanitizeParams(params) : Map.of();

        AuditEntry entry = new AuditEntry(
                UUID.randomUUID().toString(),
                sessionId,
                clientName != null ? clientName : "anonymous",
                toolName,
                safeParams,
                success,
                resultSummary != null ? resultSummary.substring(0, Math.min(resultSummary.length(), 200)) : "",
                latencyMs,
                clientIp != null ? clientIp : "unknown",
                System.currentTimeMillis()
        );

        entries.add(entry);

        // 超过上限时淘汰最旧记录
        while (entries.size() > maxEntries) {
            entries.remove(0);
        }

        if (logToSlf4j) {
            if (success) {
                log.info("📋 审计: session={} client={} tool={} 成功 | {}ms",
                        sessionId, clientName, toolName, latencyMs);
            } else {
                log.warn("📋 审计: session={} client={} tool={} 失败 | {}ms | {}",
                        sessionId, clientName, toolName, latencyMs, resultSummary);
            }
        }
    }

    /**
     * 按工具查询审计记录
     */
    public List<AuditEntry> queryByTool(String toolName) {
        return entries.stream()
                .filter(e -> e.toolName.equals(toolName))
                .sorted((a, b) -> Long.compare(b.timestamp, a.timestamp))
                .collect(Collectors.toList());
    }

    /**
     * 按客户端查询审计记录
     */
    public List<AuditEntry> queryByClient(String clientName) {
        return entries.stream()
                .filter(e -> e.clientName.equals(clientName))
                .sorted((a, b) -> Long.compare(b.timestamp, a.timestamp))
                .collect(Collectors.toList());
    }

    /**
     * 按时间范围查询
     */
    public List<AuditEntry> queryByTimeRange(long fromMs, long toMs) {
        return entries.stream()
                .filter(e -> e.timestamp >= fromMs && e.timestamp <= toMs)
                .sorted((a, b) -> Long.compare(b.timestamp, a.timestamp))
                .collect(Collectors.toList());
    }

    /**
     * 查询最近的审计记录
     */
    public List<AuditEntry> getRecent(int limit) {
        int size = entries.size();
        return entries.subList(Math.max(0, size - limit), size).stream()
                .sorted((a, b) -> Long.compare(b.timestamp, a.timestamp))
                .collect(Collectors.toList());
    }

    /**
     * 查询失败记录
     */
    public List<AuditEntry> getFailedEntries(int limit) {
        return entries.stream()
                .filter(e -> !e.success)
                .sorted((a, b) -> Long.compare(b.timestamp, a.timestamp))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * 获取审计统计
     */
    public Map<String, Object> getStats() {
        long total = entries.size();
        long failed = entries.stream().filter(e -> !e.success).count();
        long uniqueClients = entries.stream().map(e -> e.clientName).distinct().count();
        long uniqueTools = entries.stream().map(e -> e.toolName).distinct().count();

        return Map.of(
                "totalEntries", total,
                "failedEntries", failed,
                "uniqueClients", uniqueClients,
                "uniqueTools", uniqueTools,
                "maxEntries", maxEntries,
                "isLoggingParams", logParams
        );
    }

    /**
     * 清除敏感参数
     */
    private Map<String, Object> sanitizeParams(Map<String, Object> params) {
        if (params == null) return Map.of();
        Map<String, Object> safe = new HashMap<>(params);
        Set<String> sensitiveKeys = Set.of("password", "secret", "token", "apiKey", "api_key", "key", "auth");
        safe.keySet().removeIf(k -> sensitiveKeys.contains(k.toLowerCase()));
        return safe;
    }

    // ===== 内部类 =====

    /**
     * 审计条目
     */
    public record AuditEntry(
            String id,
            String sessionId,
            String clientName,
            String toolName,
            Map<String, Object> params,
            boolean success,
            String resultSummary,
            long latencyMs,
            String clientIp,
            long timestamp
    ) {
        public Map<String, Object> toMap() {
            return Map.of(
                    "id", id,
                    "sessionId", sessionId,
                    "clientName", clientName,
                    "toolName", toolName,
                    "success", success,
                    "resultSummary", resultSummary,
                    "latencyMs", latencyMs,
                    "clientIp", clientIp,
                    "timestamp", Instant.ofEpochMilli(timestamp).toString()
            );
        }
    }
}
