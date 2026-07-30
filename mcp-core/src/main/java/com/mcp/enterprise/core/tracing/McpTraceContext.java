package com.mcp.enterprise.core.tracing;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP W3C Trace Context 工具类
 *
 * 实现 W3C Trace Context 标准（MCP 2026-07-28 规范要求）：
 * - traceparent: 00-{traceId}-{parentId}-{traceFlags}
 * - tracestate: 厂商扩展的追踪状态
 *
 * 用于全链路追踪：AI Agent → MCP Server → 下游工具服务
 *
 * @see <a href="https://www.w3.org/TR/trace-context/">W3C Trace Context</a>
 */
public final class McpTraceContext {

    /** W3C Trace Context 标准头部名称 */
    public static final String TRACEPARENT_HEADER = "traceparent";
    public static final String TRACESTATE_HEADER = "tracestate";

    /** Trace Context 版本号（当前为 00） */
    private static final String VERSION = "00";

    /** 线程本地 traceId（请求级） */
    private static final ThreadLocal<String> CURRENT_TRACE_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_SPAN_ID = new ThreadLocal<>();

    private McpTraceContext() {
        // utility class
    }

    /**
     * 从 HTTP 头部提取或生成 traceId
     *
     * @param headers HTTP 请求头部
     * @return traceId（32字符十六进制）
     */
    public static String extractOrGenerate(Map<String, String> headers) {
        String traceparent = headers != null ? headers.get(TRACEPARENT_HEADER) : null;

        if (traceparent != null && traceparent.length() >= 55) {
            String traceId = traceparent.substring(3, 35);
            CURRENT_TRACE_ID.set(traceId);

            String parentId = traceparent.substring(36, 52);
            CURRENT_SPAN_ID.set(parentId);
            return traceId;
        }

        String traceId = generateTraceId();
        CURRENT_TRACE_ID.set(traceId);
        CURRENT_SPAN_ID.set(generateSpanId());
        return traceId;
    }

    /**
     * 生成符合 W3C 标准的 traceparent 头部值
     */
    public static String generateTraceParent() {
        String traceId = getCurrentTraceId() != null ? getCurrentTraceId() : generateTraceId();
        String spanId = generateSpanId();
        return VERSION + "-" + traceId + "-" + spanId + "-01";
    }

    /**
     * 获取当前请求的 traceId
     */
    public static String getCurrentTraceId() {
        return CURRENT_TRACE_ID.get();
    }

    /**
     * 获取当前请求的 spanId
     */
    public static String getCurrentSpanId() {
        return CURRENT_SPAN_ID.get();
    }

    /**
     * 清理线程本地（请求处理完成后调用）
     */
    public static void clear() {
        CURRENT_TRACE_ID.remove();
        CURRENT_SPAN_ID.remove();
    }

    /**
     * 生成 32 位十六进制 traceId
     */
    private static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成 16 位十六进制 spanId
     */
    private static String generateSpanId() {
        return Long.toHexString(System.nanoTime() & 0xFFFFFFFFFFFFL);
    }

    /**
     * 创建带 traceId 的上下文 Map（用于传递给下游服务）
     */
    public static Map<String, String> createTraceHeaders() {
        Map<String, String> headers = new ConcurrentHashMap<>();
        headers.put(TRACEPARENT_HEADER, generateTraceParent());
        String traceState = CURRENT_TRACE_ID.get() != null
                ? "mcp-enterprise=" + CURRENT_TRACE_ID.get()
                : null;
        if (traceState != null) {
            headers.put(TRACESTATE_HEADER, traceState);
        }
        return headers;
    }
}
