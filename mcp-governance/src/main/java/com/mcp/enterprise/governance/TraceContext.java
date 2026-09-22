package com.mcp.enterprise.governance;

/**
 * V1.31 链路追踪上下文：解析 W3C {@code traceparent} 请求头，将治理审计事件与
 * OpenTelemetry / Micrometer Tracing / 任意分布式追踪后端关联。
 *
 * <p>背景：V1.26~V1.30 审计链路已覆盖「发生了什么 → 存在哪 → 怎么查 → 留多久」，
 * 但审计事件与调用链（trace）相互孤立：事故复盘时，治理事件与业务调用日志
 * 无法秒级打通。V1.31 在审计事件中携带 {@code traceId}/{@code spanId}，使
 * 「一次工具调用从 Agent 下发 → 网关 → 治理判定 → 落库」成为可追踪的整条链路。</p>
 *
 * <p>格式（W3C Trace Context 规范）：{@code traceparent: version-trace-id-parent-id-flags}，
 * 例如 {@code 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01}。</p>
 *
 * <p>兼容策略（自顶向下，取第一个可用）：</p>
 * <ol>
 *   <li>合法 W3C {@code traceparent} 头 → traceId + spanId；</li>
 *   <li>{@code X-Request-Id} 头 → 作为 traceId（网关/负载均衡常见透传头）；</li>
 *   <li>SLF4J MDC 中已有的 {@code traceId}（与老版本 Logback pattern 兼容）；</li>
 *   <li>均不可用 → 生成新的 UUID traceId（保证每条审计事件永远可被追踪）。</li>
 * </ol>
 *
 * <p>注意：本类不引入 Micrometer Tracing 依赖，保持 mcp-governance 零外部追踪依赖；
 * 若项目已接入 micrometer-tracing（如 spring-boot-starter-actuator + tracing），
 * 其自动生成的 MDC {@code traceId}/{@code spanId} 会被本类直接识别，无需任何改造。</p>
 */
public final class TraceContext {

    /** 请求/响应属性键：本次调用解析出的 traceId（供下游 Filter/Interceptor 复用）。 */
    public static final String ATTR_TRACE_ID = "mcp.governance.traceId";
    /** 请求/响应属性键：本次调用解析出的 parent spanId。 */
    public static final String ATTR_SPAN_ID = "mcp.governance.spanId";

    /** W3C traceparent 头的标准名称。 */
    public static final String HEADER_TRACEPARENT = "traceparent";
    /** 兼容头：网关/负载均衡常见透传的自定义请求 ID。 */
    public static final String HEADER_X_REQUEST_ID = "X-Request-Id";

    /** traceparent 版本字段固定为 00（v1.31 仅支持 version-00）。 */
    private static final String EXPECTED_VERSION = "00";

    private TraceContext() {
    }

    /**
     * 解析 trace 上下文。
     *
     * @param traceparent   W3C traceparent 头原始值（可为 null/空）
     * @param xRequestId    X-Request-Id 头原始值（可为 null/空）
     * @param mdcTraceId    MDC 中的 traceId（可为 null/空）
     * @return 解析出的 {code traceId}/{@code spanId}（spanId 可能为 null，traceId 永不为 null）
     */
    public static Parsed parse(String traceparent, String xRequestId, String mdcTraceId) {
        Parsed fromHeader = parseTraceparent(traceparent);
        if (fromHeader != null) {
            return fromHeader;
        }
        if (isValidTraceId(xRequestId)) {
            return new Parsed(normalize(xRequestId), null);
        }
        if (isValidTraceId(mdcTraceId)) {
            return new Parsed(normalize(mdcTraceId), null);
        }
        // 兜底：生成随机 UUID traceId，保证审计事件永远可追踪
        return new Parsed(java.util.UUID.randomUUID().toString().replace("-", ""), null);
    }

    /**
     * 严格解析 W3C {@code traceparent}。
     *
     * @return 解析成功返回 {@link Parsed}；头缺失/格式非法返回 {@code null}（调用方继续降级）
     */
    public static Parsed parseTraceparent(String traceparent) {
        if (traceparent == null || traceparent.isBlank()) {
            return null;
        }
        String[] parts = traceparent.trim().split("-");
        if (parts.length < 4) {
            return null;
        }
        String version = parts[0].trim();
        String traceId = parts[1].trim();
        String parentId = parts[2].trim();
        if (!EXPECTED_VERSION.equals(version)
                || !isValidTraceId(traceId)
                || !isValidSpanId(parentId)) {
            return null;
        }
        return new Parsed(traceId, parentId);
    }

    /** W3C trace-id 约束：32 个十六进制字符（128 bit）。 */
    public static boolean isValidTraceId(String s) {
        return s != null && s.length() == 32 && isHex(s);
    }

    /** W3C span-id 约束：16 个十六进制字符（64 bit）。 */
    public static boolean isValidSpanId(String s) {
        return s != null && s.length() == 16 && isHex(s);
    }

    private static boolean isHex(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    /** 统一小写，保证跨系统 traceId 比对标淮化。 */
    private static String normalize(String s) {
        return s == null ? null : s.toLowerCase();
    }

    /**
     * 解析结果。
     *
     * @param traceId  32 位十六进制 trace id（永不为 null；兜底时为新生成的 UUID 无横线形式）
     * @param spanId   16 位十六进制 parent span id（可能为 null，表示仅由 X-Request-Id/MDC/UUID 生成）
     */
    public record Parsed(String traceId, String spanId) {
    }
}