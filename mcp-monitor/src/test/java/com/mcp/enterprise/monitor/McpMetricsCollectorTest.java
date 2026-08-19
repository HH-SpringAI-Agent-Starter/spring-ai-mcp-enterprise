package com.mcp.enterprise.monitor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 指标采集器单元测试
 */
class McpMetricsCollectorTest {

    private McpMetricsCollector collector;

    @BeforeEach
    void setUp() {
        collector = new McpMetricsCollector(60_000L); // 1分钟 retention
    }

    @Test
    void testRecordInvocation() {
        collector.recordInvocation("test-tool", 100, true);
        collector.recordInvocation("test-tool", 200, true);
        collector.recordInvocation("test-tool", 300, false);

        var metrics = collector.getAggregatedMetrics();
        var toolMetrics = metrics.get("test-tool");

        assertNotNull(toolMetrics);
        assertEquals(3, toolMetrics.totalInvocations());
        assertEquals(1, toolMetrics.errors());
        assertTrue(toolMetrics.avgLatencyMs() > 0);
    }

    @Test
    void testMultipleTools() {
        collector.recordInvocation("tool-a", 100, true);
        collector.recordInvocation("tool-a", 150, true);
        collector.recordInvocation("tool-b", 200, false);

        var metrics = collector.getAggregatedMetrics();
        assertTrue(metrics.size() >= 2); // at least 2 tools with records
    }

    @Test
    void testEmptyMetrics() {
        var metrics = collector.getAggregatedMetrics();
        assertTrue(metrics.isEmpty() || metrics.values().stream()
                .allMatch(m -> m.totalInvocations() == 0));
    }

    @Test
    void testHighErrorDetection() {
        // 5 invocations, 3 errors = 60% error rate
        for (int i = 0; i < 2; i++) {
            collector.recordInvocation("buggy-tool", 100, true);
        }
        for (int i = 0; i < 3; i++) {
            collector.recordInvocation("buggy-tool", 100, false);
        }

        var highErrorTools = collector.getToolsWithHighErrorRate(0.5);
        assertTrue(highErrorTools.stream().anyMatch(m -> m.toolName().equals("buggy-tool")));
    }

    @Test
    void testSummarySnapshot() {
        collector.recordInvocation("tool-a", 100, true);
        collector.recordInvocation("tool-b", 200, false);

        var summary = collector.getSummarySnapshot();
        assertEquals(2L, summary.get("totalCalls"));
        assertEquals(1L, summary.get("totalErrors"));
        assertNotNull(summary.get("avgLatencyMs"));
        assertNotNull(summary.get("timestamp"));
    }

    // ===== V1.6: 网关路由指标（Mcp-Method / Mcp-Name 标头维度） =====

    @Test
    void testGatewayInvocationRecording() {
        collector.recordGatewayInvocation("tools/call", "greet", 120, true);
        collector.recordGatewayInvocation("tools/call", "greet", 80, true);
        collector.recordGatewayInvocation("tools/call", "greet", 50, false);
        collector.recordGatewayInvocation("tools/list", "", 5, true);

        var snapshot = collector.getGatewayMetricsSnapshot();
        @SuppressWarnings("unchecked")
        var operations = (java.util.List<Map<String, Object>>) snapshot.get("operations");

        assertEquals(2, operations.size());
        assertEquals(4L, snapshot.get("totalInvocations"));

        // 确定性排序：tools/call 在 tools/list 之前
        assertEquals("tools/call", operations.get(0).get("method"));
        assertEquals("tools/list", operations.get(1).get("method"));

        // greet 维度：3 次调用 1 次错误
        var greet = operations.get(0);
        assertEquals(3L, greet.get("totalInvocations"));
        assertEquals(1L, greet.get("errors"));
        assertTrue(((String) greet.get("errorRate")).contains("33.33"));
    }

    @Test
    void testGatewayInvocationWithNullMethodDefaultsToUnknown() {
        collector.recordGatewayInvocation(null, null, 10, true);

        var snapshot = collector.getGatewayMetricsSnapshot();
        @SuppressWarnings("unchecked")
        var operations = (java.util.List<Map<String, Object>>) snapshot.get("operations");
        assertEquals(1, operations.size());
        assertEquals("unknown", operations.get(0).get("method"));
    }

    @Test
    void testGatewayMetricsReset() {
        collector.recordGatewayInvocation("ping", "", 1, true);
        assertEquals(1, ((java.util.List<?>) collector.getGatewayMetricsSnapshot().get("operations")).size());

        collector.resetGatewayMetrics();
        assertEquals(0, ((java.util.List<?>) collector.getGatewayMetricsSnapshot().get("operations")).size());
    }

    @Test
    void testSummaryIncludesGatewayMetrics() {
        collector.recordGatewayInvocation("tools/call", "greet", 100, true);

        var summary = collector.getSummarySnapshot();
        assertEquals(1L, summary.get("gatewayInvocations"));
        assertEquals(1, summary.get("gatewayOperations"));
    }

    // ===== V1.7: Prometheus 文本导出 =====

    @Test
    void testExportPrometheusIncludesToolMetrics() {
        collector.recordInvocation("greet", 120, true);
        collector.recordInvocation("greet", 80, true);
        collector.recordInvocation("greet", 50, false);

        String output = collector.exportPrometheus();

        assertTrue(output.contains("# TYPE mcp_tool_invocations_total counter"));
        assertTrue(output.contains("mcp_tool_invocations_total{tool=\"greet\"} 3"));
        assertTrue(output.contains("mcp_tool_errors_total{tool=\"greet\"} 1"));
        assertTrue(output.contains("mcp_tool_latency_ms{tool=\"greet\"}"));
    }

    @Test
    void testExportPrometheusIncludesGatewayMetrics() {
        collector.recordGatewayInvocation("tools/call", "greet", 120, true);
        collector.recordGatewayInvocation("tools/call", "greet", 80, false);
        collector.recordGatewayInvocation("ping", "", 5, true);

        String output = collector.exportPrometheus();

        assertTrue(output.contains("mcp_gateway_invocations_total{method=\"ping\",name=\"\"} 1"));
        assertTrue(output.contains("mcp_gateway_invocations_total{method=\"tools/call\",name=\"greet\"} 2"));
        assertTrue(output.contains("mcp_gateway_errors_total{method=\"tools/call\",name=\"greet\"} 1"));
        assertTrue(output.contains("mcp_gateway_latency_ms{method=\"tools/call\",name=\"greet\"}"));
        // 确定性排序：ping 在 tools/call 之前
        assertTrue(output.indexOf("ping") < output.indexOf("tools/call"));
    }

    @Test
    void testExportPrometheusEscapesLabels() {
        collector.recordGatewayInvocation("tools/call", "say\"hello", 10, true);

        String output = collector.exportPrometheus();
        assertTrue(output.contains("name=\"say\\\"hello\""));
    }

    @Test
    void testExportPrometheusEmptyState() {
        String output = collector.exportPrometheus();
        // 空状态不应抛异常，包含 build_info 与 TYPE 头
        assertTrue(output.contains("mcp_build_info{version=\"1.1.0\"} 1"));
        assertFalse(output.contains("mcp_tool_invocations_total{tool"));
    }
}
