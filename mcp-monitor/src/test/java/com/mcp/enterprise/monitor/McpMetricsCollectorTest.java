package com.mcp.enterprise.monitor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
}
