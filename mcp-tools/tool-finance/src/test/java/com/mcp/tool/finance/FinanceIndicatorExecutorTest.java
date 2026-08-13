package com.mcp.tool.finance;

import com.mcp.enterprise.core.model.ToolDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FinanceIndicatorExecutor 单元测试
 *
 * 覆盖：工具定义正确性 + 6 类指标计算（CAGR/ROE/PEG/复利/定投/利润率）+ 异常分支
 */
class FinanceIndicatorExecutorTest {

    private FinanceIndicatorExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new FinanceIndicatorExecutor();
    }

    // ===== 工具定义 =====

    @Test
    void shouldHaveCorrectDefinition() {
        ToolDefinition def = executor.getDefinition();

        assertEquals("finance_indicator", def.getName());
        assertEquals("财务指标计算器", def.getDisplayName());
        assertEquals("finance", def.getCategory());
        assertNotNull(def.getDescription());
        assertEquals("1.0.0", def.getVersion());
        assertTrue(def.getRequiredRoles().contains("admin"));
        assertTrue(def.getRequiredRoles().contains("user"));
        assertEquals(5000, def.getTimeoutMs());

        Map<String, Object> schema = def.getInputSchema();
        assertNotNull(schema);
        assertEquals("object", schema.get("type"));
        assertTrue(schema.containsKey("properties"));
        assertTrue(schema.containsKey("required"));
    }

    // ===== CAGR =====

    @Test
    void shouldCalcCagr() {
        // 100 -> 200, 3 年: (2)^(1/3)-1 ≈ 0.2599
        StepVerifier.create(executor.execute(Map.of(
                        "indicator", "cagr",
                        "params", Map.of("beginValue", 100, "endValue", 200, "years", 3))))
                .assertNext(result -> {
                    assertTrue((Boolean) result.get("success"));
                    assertEquals("CAGR", result.get("indicator"));
                    assertEquals(25.99, (Double) result.get("cagrPct"), 0.01);
                })
                .verifyComplete();
    }

    @Test
    void shouldRejectCagrWithNonPositiveBegin() {
        StepVerifier.create(executor.execute(Map.of(
                        "indicator", "cagr",
                        "params", Map.of("beginValue", 0, "endValue", 200, "years", 3))))
                .assertNext(result -> {
                    assertFalse((Boolean) result.get("success"));
                    assertTrue(((String) result.get("error")).contains("beginValue"));
                })
                .verifyComplete();
    }

    // ===== ROE =====

    @Test
    void shouldCalcRoe() {
        StepVerifier.create(executor.execute(Map.of(
                        "indicator", "roe",
                        "params", Map.of("netProfit", 10, "equity", 100))))
                .assertNext(result -> {
                    assertTrue((Boolean) result.get("success"));
                    assertEquals(10.0, (Double) result.get("roePct"), 0.001);
                })
                .verifyComplete();
    }

    // ===== PEG =====

    @Test
    void shouldCalcPegAndAssessment() {
        // PE=20, 增速=25% -> PEG=0.8 -> 低估
        StepVerifier.create(executor.execute(Map.of(
                        "indicator", "peg",
                        "params", Map.of("pe", 20, "earningsGrowthPct", 25))))
                .assertNext(result -> {
                    assertTrue((Boolean) result.get("success"));
                    assertEquals(0.8, (Double) result.get("peg"), 0.001);
                    assertEquals("低估(相对成长)", result.get("assessment"));
                })
                .verifyComplete();
    }

    // ===== 复利终值 =====

    @Test
    void shouldCalcCompound() {
        // 10000, 年化 10%, 5 年 -> 10000*1.1^5 = 16105.1
        StepVerifier.create(executor.execute(Map.of(
                        "indicator", "compound",
                        "params", Map.of("principal", 10000, "annualRatePct", 10, "years", 5))))
                .assertNext(result -> {
                    assertTrue((Boolean) result.get("success"));
                    assertEquals(16105.10, (Double) result.get("futureValue"), 0.01);
                })
                .verifyComplete();
    }

    // ===== 定投终值 =====

    @Test
    void shouldCalcAnnuity() {
        // 每月 1000, 年化 12%, 1 年 (r=0.01, n=12) -> 1000*((1.01^12-1)/0.01) = 12682.50
        StepVerifier.create(executor.execute(Map.of(
                        "indicator", "annuity",
                        "params", Map.of("monthly", 1000, "annualRatePct", 12, "years", 1))))
                .assertNext(result -> {
                    assertTrue((Boolean) result.get("success"));
                    assertEquals(12682.50, (Double) result.get("futureValue"), 0.5);
                    assertEquals(12000.0, (Double) result.get("totalInvested"), 0.001);
                })
                .verifyComplete();
    }

    @Test
    void shouldCalcAnnuityWithZeroRate() {
        // 无息定投: 1000*12 = 12000
        StepVerifier.create(executor.execute(Map.of(
                        "indicator", "annuity",
                        "params", Map.of("monthly", 1000, "annualRatePct", 0, "years", 1))))
                .assertNext(result -> {
                    assertTrue((Boolean) result.get("success"));
                    assertEquals(12000.0, (Double) result.get("futureValue"), 0.001);
                })
                .verifyComplete();
    }

    // ===== 利润率 =====

    @Test
    void shouldCalcMargin() {
        // 营收 1000, 成本 600, 净利 100 -> 毛利率 40%, 净利率 10%
        StepVerifier.create(executor.execute(Map.of(
                        "indicator", "margin",
                        "params", Map.of("revenue", 1000, "cost", 600, "netProfit", 100))))
                .assertNext(result -> {
                    assertTrue((Boolean) result.get("success"));
                    assertEquals(40.0, (Double) result.get("grossMarginPct"), 0.001);
                    assertEquals(10.0, (Double) result.get("netMarginPct"), 0.001);
                })
                .verifyComplete();
    }

    // ===== 异常分支 =====

    @Test
    void shouldRejectBlankIndicator() {
        StepVerifier.create(executor.execute(Map.of()))
                .assertNext(result -> {
                    assertFalse((Boolean) result.get("success"));
                    assertNotNull(result.get("error"));
                })
                .verifyComplete();
    }

    @Test
    void shouldRejectUnknownIndicator() {
        StepVerifier.create(executor.execute(Map.of("indicator", "magic")))
                .assertNext(result -> {
                    assertFalse((Boolean) result.get("success"));
                    assertTrue(((String) result.get("error")).contains("不支持的指标"));
                })
                .verifyComplete();
    }

    @Test
    void shouldRejectInvalidNumberParam() {
        StepVerifier.create(executor.execute(Map.of(
                        "indicator", "cagr",
                        "params", Map.of("beginValue", "abc", "endValue", 200, "years", 3))))
                .assertNext(result -> {
                    assertFalse((Boolean) result.get("success"));
                    assertTrue(((String) result.get("error")).contains("不是有效数字"));
                })
                .verifyComplete();
    }
}
