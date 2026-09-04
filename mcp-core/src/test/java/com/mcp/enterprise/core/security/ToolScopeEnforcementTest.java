package com.mcp.enterprise.core.security;

import com.mcp.enterprise.core.endpoint.McpStatelessEndpoint;
import com.mcp.enterprise.core.model.ToolDefinition;
import com.mcp.enterprise.core.registry.ToolRegistry;
import com.mcp.enterprise.core.tool.McpToolExecutor;
import com.mcp.enterprise.core.tool.McpToolManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V1.19: 工具级 Scope 授权集成测试 —— McpToolManager.invokeWithScope + McpStatelessEndpoint 无状态调用。
 */
class ToolScopeEnforcementTest {

    private ToolRegistry registry;
    private McpToolManager toolManager;
    private ToolScopePolicy policy;
    private AtomicInteger financeExecutions;
    private AtomicInteger calcExecutions;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        toolManager = new McpToolManager(registry);
        policy = new ToolScopePolicy(true, Map.of(), Map.of("finance", "tools:finance:*"));
        toolManager.setScopePolicy(policy);

        financeExecutions = new AtomicInteger(0);
        calcExecutions = new AtomicInteger(0);

        McpToolExecutor finance = executor("finance_indicator", "finance", null, "✅ finance result", financeExecutions);
        McpToolExecutor calc = executor("calculator", "ai", null, "✅ calc result", calcExecutions);
        toolManager.registerExecutors(java.util.List.of(finance, calc));
    }

    private McpToolExecutor executor(String name, String category, String requiredScopes,
                                     String result, AtomicInteger counter) {
        return new McpToolExecutor() {
            @Override
            public ToolDefinition getDefinition() {
                ToolDefinition def = new ToolDefinition();
                def.setName(name);
                def.setDisplayName(name);
                def.setDescription(name);
                def.setCategory(category);
                def.setVersion("1.0.0");
                def.setRequiredScopes(requiredScopes);
                return def;
            }

            @Override
            public Mono<Map<String, Object>> execute(Map<String, Object> params) {
                counter.incrementAndGet();
                return Mono.just(Map.of("success", true, "result", result));
            }
        };
    }

    // ===== McpToolManager.invokeWithScope =====

    @Test
    void invokeWithScopeDeniesWithoutExecuting() {
        // finance 工具经分类兜底要求 tools:finance:*，令牌只有 audit:view → 拒绝且不执行
        StepVerifier.create(toolManager.invokeWithScope("finance_indicator", Map.of(), Set.of("audit:view")))
                .assertNext(result -> {
                    assertEquals(false, result.get("success"));
                    assertEquals("insufficient_scope", result.get("error"));
                    assertEquals(403, result.get("httpStatus"));
                    assertEquals(Set.of("tools:finance:*"), result.get("requiredScopes"));
                })
                .verifyComplete();
        assertEquals(0, financeExecutions.get(), "被拒绝的调用不得触发执行器");
    }

    @Test
    void invokeWithScopeAllowsMatchingScope() {
        StepVerifier.create(toolManager.invokeWithScope("finance_indicator", Map.of(), Set.of("tools:finance:read")))
                .assertNext(result -> assertEquals(true, result.get("success")))
                .verifyComplete();
        assertEquals(1, financeExecutions.get());
    }

    @Test
    void invokeWithScopeNullTokenScopesSkipsEnforcement() {
        // 无令牌上下文（旧版 X-API-Key 路径）→ 不拦截，向后兼容
        StepVerifier.create(toolManager.invokeWithScope("finance_indicator", Map.of(), null))
                .assertNext(result -> assertEquals(true, result.get("success")))
                .verifyComplete();
        assertEquals(1, financeExecutions.get());
    }

    @Test
    void invokeWithScopeUnconstrainedToolAllows() {
        // calculator 无 scope 约束 → 任意令牌可调
        StepVerifier.create(toolManager.invokeWithScope("calculator", Map.of(), Set.of("anything")))
                .assertNext(result -> assertEquals(true, result.get("success")))
                .verifyComplete();
        assertEquals(1, calcExecutions.get());
    }

    @Test
    void disabledPolicyDelegatesToPlainInvoke() {
        toolManager.setScopePolicy(new ToolScopePolicy(false, Map.of(), Map.of("finance", "tools:finance:*")));
        StepVerifier.create(toolManager.invokeWithScope("finance_indicator", Map.of(), Set.of("audit:view")))
                .assertNext(result -> assertEquals(true, result.get("success")))
                .verifyComplete();
        assertEquals(1, financeExecutions.get());
    }

    // ===== McpStatelessEndpoint（JSON-RPC tools/call） =====

    private McpStatelessEndpoint endpoint() {
        return new McpStatelessEndpoint(registry, toolManager);
    }

    @Test
    void statelessToolCallReturnsInsufficientScopeError() {
        McpStatelessEndpoint endpoint = endpoint();
        Map<String, Object> call = Map.of(
                "jsonrpc", "2.0", "id", "tool-call-1", "method", "tools/call",
                "params", Map.of("name", "finance_indicator", "arguments", Map.of())
        );
        Map<String, Object> response = endpoint.handleStatelessMessage(call, null, Set.of("audit:view"));

        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) response.get("error");
        assertNotNull(error);
        assertEquals(-32090, ((Number) error.get("code")).intValue());
        assertTrue(String.valueOf(error.get("message")).contains("insufficient_scope"));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) error.get("data");
        assertEquals(Set.of("tools:finance:*"), data.get("requiredScopes"));
        assertEquals(0, financeExecutions.get());
    }

    @Test
    void statelessToolCallWithMatchingScopeSucceeds() {
        McpStatelessEndpoint endpoint = endpoint();
        Map<String, Object> call = Map.of(
                "jsonrpc", "2.0", "id", "tool-call-2", "method", "tools/call",
                "params", Map.of("name", "finance_indicator", "arguments", Map.of())
        );
        Map<String, Object> response = endpoint.handleStatelessMessage(call, null, Set.of("tools:finance:read"));
        assertNull(response.get("error"));
        assertEquals(1, financeExecutions.get());
    }

    @Test
    void statelessLegacyCallWithoutScopesNotIntercepted() {
        McpStatelessEndpoint endpoint = endpoint();
        Map<String, Object> call = Map.of(
                "jsonrpc", "2.0", "id", "tool-call-3", "method", "tools/call",
                "params", Map.of("name", "finance_indicator", "arguments", Map.of())
        );
        // 旧签名 / 无令牌上下文 → 不拦截
        Map<String, Object> response = endpoint.handleStatelessMessage(call, null, null);
        assertNull(response.get("error"));
        assertEquals(1, financeExecutions.get());
    }

    @Test
    void statelessToolsListExposesRequiredScopesWhenEnabled() {
        McpStatelessEndpoint endpoint = endpoint();
        Map<String, Object> list = Map.of("jsonrpc", "2.0", "id", "1", "method", "tools/list", "params", Map.of());
        Map<String, Object> response = endpoint.handleStatelessMessage(list, null, Set.of("tools:finance:read"));
        @SuppressWarnings("unchecked")
        var result = (Map<String, Object>) response.get("result");
        @SuppressWarnings("unchecked")
        var tools = (java.util.List<Map<String, Object>>) result.get("tools");
        Map<String, Object> finance = tools.stream()
                .filter(t -> "finance_indicator".equals(t.get("name")))
                .findFirst().orElseThrow();
        assertEquals(Set.of("tools:finance:*"), finance.get("requiredScopes"));
    }

    @Test
    void taskCreatePreChecksScopeFailFast() {
        McpStatelessEndpoint endpoint = endpoint();
        Map<String, Object> task = Map.of(
                "jsonrpc", "2.0", "id", "t1", "method", "tasks/create",
                "params", Map.of("tool", "finance_indicator", "arguments", Map.of())
        );
        Map<String, Object> response = endpoint.handleStatelessMessage(task, null, Set.of("audit:view"));
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) response.get("error");
        assertNotNull(error);
        assertEquals(-32090, ((Number) error.get("code")).intValue());
        assertEquals(0, financeExecutions.get(), "无权限的任务不得入队执行");
    }
}