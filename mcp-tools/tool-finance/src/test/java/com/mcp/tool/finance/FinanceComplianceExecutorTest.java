package com.mcp.tool.finance;

import com.mcp.enterprise.core.model.ToolDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FinanceComplianceExecutor 单元测试
 *
 * 覆盖：工具定义正确性 + 合规日历生成（月份过滤）+ 披露截止日查询 + 异常分支
 */
class FinanceComplianceExecutorTest {

    private FinanceComplianceExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new FinanceComplianceExecutor();
    }

    // ===== 工具定义 =====

    @Test
    void shouldHaveCorrectDefinition() {
        ToolDefinition def = executor.getDefinition();

        assertEquals("finance_compliance", def.getName());
        assertEquals("合规日历", def.getDisplayName());
        assertEquals("finance", def.getCategory());
        assertNotNull(def.getDescription());
        assertEquals("1.0.0", def.getVersion());
        assertTrue(def.getRequiredRoles().contains("admin"));
        assertTrue(def.getRequiredRoles().contains("user"));

        Map<String, Object> schema = def.getInputSchema();
        assertNotNull(schema);
        assertEquals("object", schema.get("type"));
        assertTrue(schema.containsKey("properties"));
        assertTrue(schema.containsKey("required"));
        assertTrue(((List<?>) schema.get("required")).contains("action"));
    }

    // ===== 合规日历 =====

    @Test
    void shouldReturnAprilCalendarWithAnnualReportEvents() {
        // 4 月应包含年报/一季报披露截止事件
        StepVerifier.create(executor.execute(Map.of(
                        "action", "calendar", "year", 2026, "month", 4)))
                .assertNext(result -> {
                    assertTrue((Boolean) result.get("success"));
                    assertEquals("calendar", result.get("action"));
                    assertEquals("2026-04", result.get("month"));
                    int count = (Integer) result.get("eventCount");
                    assertTrue(count >= 1);
                    List<?> events = (List<?>) result.get("events");
                    assertTrue(events.stream().anyMatch(e ->
                            String.valueOf(((Map<?, ?>) e).get("type")).contains("年报")));
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnEmptyCalendarForMonthWithoutEvents() {
        // 6 月无固定披露节点 → eventCount 为 0
        StepVerifier.create(executor.execute(Map.of(
                        "action", "calendar", "year", 2026, "month", 6)))
                .assertNext(result -> {
                    assertTrue((Boolean) result.get("success"));
                    assertEquals(0, result.get("eventCount"));
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnAugustCalendarWithInterimReport() {
        StepVerifier.create(executor.execute(Map.of(
                        "action", "calendar", "year", 2026, "month", 8)))
                .assertNext(result -> {
                    assertTrue((Boolean) result.get("success"));
                    List<?> events = (List<?>) result.get("events");
                    assertTrue(events.stream().anyMatch(e ->
                            String.valueOf(((Map<?, ?>) e).get("type")).equals("中报")));
                })
                .verifyComplete();
    }

    @Test
    void shouldRejectInvalidMonth() {
        StepVerifier.create(executor.execute(Map.of(
                        "action", "calendar", "year", 2026, "month", 13)))
                .assertNext(result -> {
                    assertFalse((Boolean) result.get("success"));
                    assertNotNull(result.get("error"));
                })
                .verifyComplete();
    }

    // ===== 披露截止日 =====

    @Test
    void shouldReturnAnnualDeadline() {
        StepVerifier.create(executor.execute(Map.of(
                        "action", "deadline", "year", 2026, "period", "annual")))
                .assertNext(result -> {
                    assertTrue((Boolean) result.get("success"));
                    assertEquals("2026-04-30", result.get("disclosureDeadline"));
                    assertEquals("年报", result.get("reportName"));
                    assertNotNull(result.get("daysRemaining"));
                    assertNotNull(result.get("overdue"));
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnInterimDeadline() {
        StepVerifier.create(executor.execute(Map.of(
                        "action", "deadline", "year", 2026, "period", "interim")))
                .assertNext(result -> {
                    assertTrue((Boolean) result.get("success"));
                    assertEquals("2026-08-31", result.get("disclosureDeadline"));
                })
                .verifyComplete();
    }

    @Test
    void shouldRejectUnknownPeriod() {
        StepVerifier.create(executor.execute(Map.of(
                        "action", "deadline", "year", 2026, "period", "yearly")))
                .assertNext(result -> {
                    assertFalse((Boolean) result.get("success"));
                    assertNotNull(result.get("error"));
                })
                .verifyComplete();
    }

    @Test
    void shouldRejectUnknownAction() {
        StepVerifier.create(executor.execute(Map.of("action", "foo")))
                .assertNext(result -> {
                    assertFalse((Boolean) result.get("success"));
                    assertNotNull(result.get("error"));
                })
                .verifyComplete();
    }
}
