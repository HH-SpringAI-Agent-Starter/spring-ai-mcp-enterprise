package com.mcp.tool.finance;

import com.mcp.enterprise.core.model.ToolDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FinanceRiskExecutor 单元测试
 *
 * 覆盖：工具定义正确性 + 综合风险评分（健康/高杠杆/亏损企业）+ 单维度诊断 + 异常分支
 */
class FinanceRiskExecutorTest {

    private FinanceRiskExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new FinanceRiskExecutor();
    }

    // ===== 工具定义 =====

    @Test
    void shouldHaveCorrectDefinition() {
        ToolDefinition def = executor.getDefinition();

        assertEquals("finance_risk", def.getName());
        assertEquals("风险评分", def.getDisplayName());
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

    // ===== 综合风险评分 =====

    @Test
    void shouldScoreHealthyCompanyAsLowRisk() {
        // 低负债、高流动、高盈利、高增长、现金流充沛 → 低风险
        StepVerifier.create(executor.execute(Map.of(
                        "action", "score",
                        "params", Map.of(
                                "debtRatioPct", 25, "currentRatio", 2.5,
                                "grossMarginPct", 40, "netMarginPct", 20,
                                "revenueGrowthPct", 25, "ocfToNetProfit", 1.5))))
                .assertNext(result -> {
                    assertTrue((Boolean) result.get("success"));
                    double score = (Double) result.get("riskScore");
                    assertTrue(score < 20, "健康企业风险分应 < 20，实际 " + score);
                    assertEquals("低风险", result.get("riskLevel"));
                    assertNotNull(result.get("dimensions"));
                })
                .verifyComplete();
    }

    @Test
    void shouldScoreLeveragedLossCompanyAsHighRisk() {
        // 高负债、低流动、亏损、负增长、现金流为负 → 高风险
        StepVerifier.create(executor.execute(Map.of(
                        "action", "score",
                        "params", Map.of(
                                "debtRatioPct", 85, "currentRatio", 0.6,
                                "grossMarginPct", 5, "netMarginPct", -8,
                                "revenueGrowthPct", -20, "ocfToNetProfit", -0.5))))
                .assertNext(result -> {
                    assertTrue((Boolean) result.get("success"));
                    double score = (Double) result.get("riskScore");
                    assertTrue(score > 70, "高风险企业风险分应 > 70，实际 " + score);
                    assertEquals("高风险", result.get("riskLevel"));
                })
                .verifyComplete();
    }

    @Test
    void shouldScoreMissingParamsWithDefaults() {
        // 缺参时按默认值(0)计算，不抛异常
        StepVerifier.create(executor.execute(Map.of("action", "score", "params", Map.of())))
                .assertNext(result -> {
                    assertTrue((Boolean) result.get("success"));
                    assertNotNull(result.get("riskScore"));
                    assertNotNull(result.get("riskLevel"));
                })
                .verifyComplete();
    }

    // ===== 单维度诊断 =====

    @Test
    void shouldDiagnoseDebtDimension() {
        StepVerifier.create(executor.execute(Map.of(
                        "action", "diagnose",
                        "params", Map.of("dimension", "debt", "debtRatioPct", 80))))
                .assertNext(result -> {
                    assertTrue((Boolean) result.get("success"));
                    assertEquals("debt", result.get("dimension"));
                    assertNotNull(result.get("riskScore"));
                    assertNotNull(result.get("advice"));
                    assertTrue(String.valueOf(result.get("advice")).contains("高杠杆")
                            || String.valueOf(result.get("advice")).contains("杠杆"));
                })
                .verifyComplete();
    }

    @Test
    void shouldDiagnoseCashflowDimension() {
        StepVerifier.create(executor.execute(Map.of(
                        "action", "diagnose",
                        "params", Map.of("dimension", "cashflow", "ocfToNetProfit", -0.3))))
                .assertNext(result -> {
                    assertTrue((Boolean) result.get("success"));
                    assertNotNull(result.get("advice"));
                    assertTrue(String.valueOf(result.get("advice")).contains("现金"));
                })
                .verifyComplete();
    }

    @Test
    void shouldRejectUnknownDimension() {
        StepVerifier.create(executor.execute(Map.of(
                        "action", "diagnose",
                        "params", Map.of("dimension", "brand"))))
                .assertNext(result -> {
                    assertFalse((Boolean) result.get("success"));
                    assertNotNull(result.get("error"));
                })
                .verifyComplete();
    }

    @Test
    void shouldRejectUnknownAction() {
        StepVerifier.create(executor.execute(Map.of("action", "report")))
                .assertNext(result -> {
                    assertFalse((Boolean) result.get("success"));
                    assertNotNull(result.get("error"));
                })
                .verifyComplete();
    }
}
