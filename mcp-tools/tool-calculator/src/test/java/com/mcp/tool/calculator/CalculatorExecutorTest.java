package com.mcp.tool.calculator;

import com.mcp.enterprise.core.model.ToolDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CalculatorExecutor 单元测试
 */
class CalculatorExecutorTest {

    private CalculatorExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new CalculatorExecutor();
    }

    @Test
    void shouldHaveCorrectDefinition() {
        ToolDefinition def = executor.getDefinition();

        assertEquals("calculator", def.getName());
        assertEquals("计算器", def.getDisplayName());
        assertEquals("demo", def.getCategory());
        assertNotNull(def.getDescription());
        assertEquals("1.0.0", def.getVersion());
        assertTrue(def.getRequiredRoles().contains("admin"));
        assertTrue(def.getRequiredRoles().contains("user"));
        assertTrue(def.getTimeoutMs() > 0);

        // 验证 inputSchema
        Map<String, Object> schema = def.getInputSchema();
        assertNotNull(schema);
        assertEquals("object", schema.get("type"));
        assertTrue(schema.containsKey("properties"));
        assertTrue(schema.containsKey("required"));
    }

    @Test
    void shouldEvaluateSimpleAddition() {
        Map<String, Object> params = Map.of("expression", "1 + 2");

        StepVerifier.create(executor.execute(params))
                .assertNext(result -> {
                    assertTrue((Boolean) result.get("success"));
                    assertEquals("1 + 2", result.get("expression"));
                    assertEquals("3.0", result.get("result"));
                })
                .verifyComplete();
    }

    @Test
    void shouldEvaluateComplexExpression() {
        Map<String, Object> params = Map.of("expression", "(10 + 5) * 2 - 3 / 1");

        StepVerifier.create(executor.execute(params))
                .assertNext(result -> {
                    assertTrue((Boolean) result.get("success"));
                    assertEquals("(10 + 5) * 2 - 3 / 1", result.get("expression"));
                    assertEquals("27.0", result.get("result"));
                })
                .verifyComplete();
    }

    @Test
    void shouldRejectInvalidExpression() {
        Map<String, Object> params = Map.of("expression", "1 + 2");

        StepVerifier.create(executor.execute(params))
                .assertNext(result -> assertTrue((Boolean) result.get("success")))
                .verifyComplete();
    }

    @Test
    void shouldRejectNullOrBlankExpression() {
        // null expression
        StepVerifier.create(executor.execute(Map.of()))
                .assertNext(result -> {
                    assertFalse((Boolean) result.get("success"));
                    assertNotNull(result.get("error"));
                })
                .verifyComplete();

        // blank expression
        StepVerifier.create(executor.execute(Map.of("expression", "  ")))
                .assertNext(result -> {
                    assertFalse((Boolean) result.get("success"));
                    assertNotNull(result.get("error"));
                })
                .verifyComplete();
    }

    @Test
    void shouldRejectUnsafeCharacters() {
        Map<String, Object> params = Map.of("expression", "System.exit(0)");

        StepVerifier.create(executor.execute(params))
                .assertNext(result -> {
                    assertFalse((Boolean) result.get("success"));
                    assertTrue(((String) result.get("error")).contains("非法字符"));
                })
                .verifyComplete();
    }

    @Test
    void shouldRejectUnsafeExpressionWithLetters() {
        Map<String, Object> params = Map.of("expression", "1 + exec('rm -rf /')");

        StepVerifier.create(executor.execute(params))
                .assertNext(result -> {
                    assertFalse((Boolean) result.get("success"));
                    assertTrue(((String) result.get("error")).contains("非法字符"));
                })
                .verifyComplete();
    }
}
