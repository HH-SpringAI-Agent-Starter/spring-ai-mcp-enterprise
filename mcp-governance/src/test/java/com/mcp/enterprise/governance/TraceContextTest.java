package com.mcp.enterprise.governance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V1.31 TraceContext 单元测试。
 * 验证：W3C traceparent 解析、X-Request-Id 降级、MDC traceId 降级、
 * UUID 兜底、非法头拒绝、traceId/spanId 校验。
 */
class TraceContextTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String SPAN_ID = "00f067aa0ba902b7";

    @Test
    void parsesValidTraceparent() {
        TraceContext.Parsed p = TraceContext.parse(
                "00-" + TRACE_ID + "-" + SPAN_ID + "-01", null, null);
        assertNotNull(p);
        assertEquals(TRACE_ID, p.traceId());
        assertEquals(SPAN_ID, p.spanId());
    }

    @Test
    void rejectsInvalidVersion() {
        TraceContext.Parsed p = TraceContext.parseTraceparent(
                "01-" + TRACE_ID + "-" + SPAN_ID + "-01");
        assertNull(p);
    }

    @Test
    void rejectsMalformedHeader() {
        assertNull(TraceContext.parseTraceparent("00-" + TRACE_ID + "-" + SPAN_ID)); // 4 段缺失
        assertNull(TraceContext.parseTraceparent("00-zzzz-" + SPAN_ID + "-01"));     // 非 hex traceId
        assertNull(TraceContext.parseTraceparent("00-" + TRACE_ID + "-zzzz-01"));     // 非 hex spanId
        assertNull(TraceContext.parseTraceparent(""));
        assertNull(TraceContext.parseTraceparent(null));
    }

    @Test
    void fallsBackToXRequestId() {
        TraceContext.Parsed p = TraceContext.parse(null, TRACE_ID, null);
        assertNotNull(p);
        assertEquals(TRACE_ID, p.traceId());
        assertNull(p.spanId());
    }

    @Test
    void fallsBackToMdcTraceId() {
        TraceContext.Parsed p = TraceContext.parse(null, null, TRACE_ID);
        assertNotNull(p);
        assertEquals(TRACE_ID, p.traceId());
    }

    @Test
    void traceparentTakesPriorityOverMdc() {
        TraceContext.Parsed p = TraceContext.parse(
                "00-" + TRACE_ID + "-" + SPAN_ID + "-01", null, "11111111111111111111111111111111");
        assertEquals(TRACE_ID, p.traceId());
        assertEquals(SPAN_ID, p.spanId());
    }

    @Test
    void generatesUuidFallbackWhenNothingAvailable() {
        TraceContext.Parsed p = TraceContext.parse(null, null, null);
        assertNotNull(p);
        assertEquals(32, p.traceId().length());
        assertTrue(p.traceId().matches("[0-9a-f]{32}"));
        assertNull(p.spanId());
    }

    @Test
    void traceIdValidation() {
        assertTrue(TraceContext.isValidTraceId(TRACE_ID));
        assertTrue(!TraceContext.isValidTraceId("1234"));       // 长度不足
        assertTrue(!TraceContext.isValidTraceId(TRACE_ID + "x")); // 非 hex
        assertTrue(TraceContext.isValidSpanId(SPAN_ID));
        assertTrue(!TraceContext.isValidSpanId(TRACE_ID));        // 长度 32 ≠ 16
    }

    @Test
    void normalizesXRequestIdToLowercase() {
        TraceContext.Parsed p = TraceContext.parse(null, "4BF92F3577B34DA6A3CE929D0E0E4736", null);
        assertEquals(TRACE_ID, p.traceId()); // 大写会被 normalize 成小写
    }
}