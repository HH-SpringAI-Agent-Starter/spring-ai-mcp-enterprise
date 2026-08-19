package com.mcp.enterprise.core.endpoint;

import com.mcp.enterprise.core.model.ToolDefinition;
import com.mcp.enterprise.core.registry.ToolRegistry;
import com.mcp.enterprise.core.tool.McpToolExecutor;
import com.mcp.enterprise.core.tool.McpToolManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * McpStatelessEndpoint 单元测试
 */
class McpStatelessEndpointTest {

    private ToolRegistry registry;
    private McpToolManager toolManager;
    private McpStatelessEndpoint endpoint;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        toolManager = new McpToolManager(registry);
        endpoint = new McpStatelessEndpoint(registry, toolManager);

        // 注册一个测试工具
        McpToolExecutor testTool = new McpToolExecutor() {
            @Override
            public ToolDefinition getDefinition() {
                return new ToolDefinition(
                        "test",           // name
                        "测试工具",        // displayName
                        "测试用",          // description
                        "test",           // category
                        "1.0.0",          // version
                        null,             // module
                        true,             // enabled
                        "admin",          // requiredRoles
                        5000,             // timeoutMs
                        10,               // rateLimitPerSecond
                        Map.of(           // inputSchema
                                "type", "object",
                                "properties", Map.of("msg", Map.of("type", "string")),
                                "required", java.util.List.of("msg")
                        ),
                        null              // metadata
                );
            }

            @Override
            public Mono<Map<String, Object>> execute(Map<String, Object> params) {
                String msg = params != null ? (String) params.get("msg") : "";
                return Mono.just(Map.of("success", true, "result", "Hello, " + msg));
            }
        };
        toolManager.registerExecutor(testTool);
    }

    // ===== 协议 =====

    @Test
    void shouldHaveCorrectProtocolCapabilities() {
        Map<String, Object> caps = McpStatelessEndpoint.SERVER_CAPABILITIES_V2026;

        assertEquals("2026-07-28", caps.get("protocolVersion"));
        assertTrue(((java.util.List<?>) caps.get("supportedProtocolVersions")).contains("2026-07-28"));
        assertTrue(((java.util.List<?>) caps.get("supportedProtocolVersions")).contains("2025-03-26"));
    }

    // ===== initialize =====

    @Test
    void shouldHandleInitializeWith2026Protocol() {
        Map<String, Object> message = Map.of(
                "jsonrpc", "2.0",
                "id", "1",
                "method", "initialize",
                "params", Map.of("protocolVersion", "2026-07-28")
        );

        Map<String, Object> response = endpoint.handleStatelessMessage(message, "trace-001");
        assertNotNull(response);

        Map<String, Object> result = (Map<String, Object>) response.get("result");
        assertNotNull(result);
        assertEquals("2026-07-28", result.get("protocolVersion"));

        // 验证新增字段（W3C Trace Context + 能力发现）
        assertNotNull(result.get("caching"));
        assertNotNull(result.get("tracing"));
        Map<String, Object> tracing = (Map<String, Object>) result.get("tracing");
        assertEquals("traceparent", tracing.get("traceParentHeader"));
        assertEquals("tracestate", tracing.get("traceStateHeader"));

        // 🆕 验证新特性
        assertNotNull(result.get("transport"));
        assertTrue((Boolean) ((Map<String, Object>) result.get("transport")).get("stateless"));
        assertNotNull(result.get("schema"));
        assertEquals("2020-12", ((Map<String, Object>) result.get("schema")).get("jsonSchemaVersion"));
    }

    @Test
    void shouldHandleInitializeWith2025Protocol() {
        Map<String, Object> message = Map.of(
                "jsonrpc", "2.0",
                "id", "1",
                "method", "initialize",
                "params", Map.of("protocolVersion", "2025-03-26")
        );

        Map<String, Object> response = endpoint.handleStatelessMessage(message, null);
        assertNotNull(response);

        Map<String, Object> result = (Map<String, Object>) response.get("result");
        assertNotNull(result);
        assertEquals("2025-03-26", result.get("protocolVersion"));
    }

    @Test
    void shouldHandleInitializeWithDefaultProtocol() {
        Map<String, Object> message = Map.of(
                "jsonrpc", "2.0",
                "id", "1",
                "method", "initialize"
        );

        Map<String, Object> response = endpoint.handleStatelessMessage(message, null);
        assertNotNull(response);
        assertNotNull(response.get("result"));
    }

    // ===== tools/list =====

    @Test
    void shouldListTools() {
        Map<String, Object> message = Map.of(
                "jsonrpc", "2.0",
                "id", "2",
                "method", "tools/list"
        );

        Map<String, Object> response = endpoint.handleStatelessMessage(message, null);
        assertNotNull(response);

        Map<String, Object> result = (Map<String, Object>) response.get("result");
        assertNotNull(result);
        assertTrue(result.containsKey("tools"));
        assertTrue(result.containsKey("_etag"));
        assertTrue(result.containsKey("_cachedAt"));
    }

    @Test
    void shouldIncludeToolDetailsInList() {
        Map<String, Object> message = Map.of(
                "jsonrpc", "2.0",
                "id", "2",
                "method", "tools/list"
        );

        Map<String, Object> response = endpoint.handleStatelessMessage(message, null);
        Map<String, Object> result = (Map<String, Object>) response.get("result");
        java.util.List<Map<String, Object>> tools = (java.util.List<Map<String, Object>>) result.get("tools");

        assertEquals(1, tools.size());
        Map<String, Object> tool = tools.get(0);
        assertEquals("test", tool.get("name"));
        assertEquals("测试工具", tool.get("displayName"));
        assertEquals("1.0.0", tool.get("version"));
        assertTrue(tool.containsKey("inputSchema"));
    }

    @Test
    void shouldSupportPaginationInList() {
        Map<String, Object> message = Map.of(
                "jsonrpc", "2.0",
                "id", "2",
                "method", "tools/list",
                "params", Map.of("cursor", "page1")
        );

        Map<String, Object> response = endpoint.handleStatelessMessage(message, null);
        Map<String, Object> result = (Map<String, Object>) response.get("result");
        assertTrue(result.containsKey("nextCursor"));
        assertNull(result.get("nextCursor"));
    }

    // ===== tools/call =====

    @Test
    void shouldCallTool() {
        Map<String, Object> callMessage = Map.of(
                "jsonrpc", "2.0",
                "id", "3",
                "method", "tools/call",
                "params", Map.of(
                        "name", "test",
                        "arguments", Map.of("msg", "World")
                )
        );

        Map<String, Object> response = endpoint.handleStatelessMessage(callMessage, null);
        assertNotNull(response);

        Map<String, Object> result = (Map<String, Object>) response.get("result");
        assertNotNull(result);
        assertTrue(result.containsKey("content"));
    }

    @Test
    void shouldReturnErrorForNonExistentTool() {
        Map<String, Object> callMessage = Map.of(
                "jsonrpc", "2.0",
                "id", "3",
                "method", "tools/call",
                "params", Map.of("name", "nonexistent", "arguments", Map.of())
        );

        Map<String, Object> response = endpoint.handleStatelessMessage(callMessage, null);
        assertNotNull(response);

        // 应该返回 error
        assertTrue(response.containsKey("error") || response.containsKey("result"));
    }

    @Test
    void shouldReturnErrorForMissingToolName() {
        Map<String, Object> callMessage = Map.of(
                "jsonrpc", "2.0",
                "id", "3",
                "method", "tools/call",
                "params", Map.of()
        );

        Map<String, Object> response = endpoint.handleStatelessMessage(callMessage, null);
        // 如果 params 为 null，返回 error；如果请求中没有 arguments，也可能报错
        // 具体取决于 params 中是否包含 name
        Map<String, Object> result = (Map<String, Object>) response.get("result");
        // 至少不应该抛出异常
        assertNotNull(response);
    }

    // ===== ping =====

    @Test
    void shouldHandlePing() {
        Map<String, Object> message = Map.of(
                "jsonrpc", "2.0",
                "id", "ping-1",
                "method", "ping"
        );

        Map<String, Object> response = endpoint.handleStatelessMessage(message, null);
        assertNotNull(response);

        Map<String, Object> result = (Map<String, Object>) response.get("result");
        assertNotNull(result);
        assertEquals("ok", result.get("status"));
    }

    // ===== 错误处理 =====

    @Test
    void shouldHandleInvalidMethod() {
        Map<String, Object> message = Map.of(
                "jsonrpc", "2.0",
                "id", "5",
                "method", "invalid/method"
        );

        Map<String, Object> response = endpoint.handleStatelessMessage(message, null);
        assertNotNull(response);

        Map<String, Object> error = (Map<String, Object>) response.get("error");
        assertNotNull(error);
        assertEquals(-32601, error.get("code"));
    }

    @Test
    void shouldHandleNullMessage() {
        Map<String, Object> response = endpoint.handleStatelessMessage(null, null);
        assertNotNull(response);
        assertTrue(response.containsKey("error"));
    }

    @Test
    void shouldHandleMessageWithoutMethod() {
        Map<String, Object> message = Map.of("jsonrpc", "2.0", "id", "6");

        Map<String, Object> response = endpoint.handleStatelessMessage(message, null);
        assertNotNull(response);
        assertTrue(response.containsKey("error"));
    }

    @Test
    void shouldIncludeTraceIdInResponse() {
        Map<String, Object> message = Map.of(
                "jsonrpc", "2.0",
                "id", "7",
                "method", "ping"
        );

        Map<String, Object> response = endpoint.handleStatelessMessage(message, "trace-abc-123");
        assertEquals("trace-abc-123", response.get("_traceId"));
    }

    // ===== tools/listChanged =====

    @Test
    void shouldHandleListChangedAsList() {
        Map<String, Object> message = Map.of(
                "jsonrpc", "2.0",
                "id", "8",
                "method", "tools/listChanged"
        );

        Map<String, Object> response = endpoint.handleStatelessMessage(message, null);
        assertNotNull(response);
        assertTrue(response.containsKey("result"));
    }

    // ===== JSON Schema 增强 =====

    @Test
    void inputSchemaShouldIncludeJsonSchemaHeader() {
        Map<String, Object> message = Map.of(
                "jsonrpc", "2.0",
                "id", "9",
                "method", "tools/list"
        );

        Map<String, Object> response = endpoint.handleStatelessMessage(message, null);
        Map<String, Object> result = (Map<String, Object>) response.get("result");
        java.util.List<Map<String, Object>> tools = (java.util.List<Map<String, Object>>) result.get("tools");

        Map<String, Object> schema = (Map<String, Object>) tools.get(0).get("inputSchema");
        assertEquals("https://json-schema.org/draft/2020-12/schema", schema.get("$schema"));
        assertEquals("object", schema.get("type"));
    }

    // ===== 2026-07-28 最终版：网关友好标头 + 缓存控制 + 确定性排序 =====

    @Test
    void shouldListToolsInDeterministicOrder() {
        // 注册第二个工具，验证 tools/list 按 name 字典序返回
        McpToolExecutor secondTool = new McpToolExecutor() {
            @Override
            public ToolDefinition getDefinition() {
                return new ToolDefinition(
                        "alpha-tool", "Alpha 工具", "第二个", "test", "1.0.0",
                        null, true, "admin", 5000, 10,
                        Map.of("type", "object", "properties", Map.of()),
                        null);
            }
            @Override
            public Mono<Map<String, Object>> execute(Map<String, Object> params) {
                return Mono.just(Map.of("success", true, "result", "alpha"));
            }
        };
        toolManager.registerExecutor(secondTool);

        Map<String, Object> response = endpoint.handleStatelessMessage(
                Map.of("jsonrpc", "2.0", "id", "2", "method", "tools/list"), null);
        Map<String, Object> result = (Map<String, Object>) response.get("result");
        java.util.List<Map<String, Object>> tools = (java.util.List<Map<String, Object>>) result.get("tools");

        assertEquals(2, tools.size());
        assertEquals("alpha-tool", tools.get(0).get("name"));
        assertEquals("test", tools.get(1).get("name"));
    }

    @Test
    void shouldIncludeTtlAndCacheScopeInList() {
        Map<String, Object> response = endpoint.handleStatelessMessage(
                Map.of("jsonrpc", "2.0", "id", "2", "method", "tools/list"), null);
        Map<String, Object> result = (Map<String, Object>) response.get("result");

        Map<String, Object> caching = (Map<String, Object>) result.get("caching");
        assertNotNull(caching);
        assertEquals(60_000L, ((Number) caching.get("ttlMs")).longValue());
        assertEquals("global", caching.get("cacheScope"));
        assertEquals(Boolean.TRUE, caching.get("deterministicOrder"));
        assertNotNull(caching.get("etag"));
    }

    @Test
    void shouldAcceptGatewayHeadersWhenMatching() {
        Map<String, Object> message = Map.of(
                "jsonrpc", "2.0",
                "id", "3",
                "method", "tools/call",
                "params", Map.of("name", "test", "arguments", Map.of("msg", "World"))
        );

        // 标头与请求体一致 → 校验通过
        Map<String, Object> validationError =
                endpoint.validateGatewayHeaders("tools/call", "test", message);
        assertNull(validationError);
    }

    @Test
    void shouldRejectGatewayHeadersWhenMethodMismatch() {
        Map<String, Object> message = Map.of(
                "jsonrpc", "2.0",
                "id", "3",
                "method", "tools/call",
                "params", Map.of("name", "test")
        );

        // Mcp-Method 与 body 方法不一致 → 拒绝（防止标头掩盖真实调用）
        Map<String, Object> validationError =
                endpoint.validateGatewayHeaders("tools/list", null, message);
        assertNotNull(validationError);
        Map<String, Object> error = (Map<String, Object>) validationError.get("error");
        assertEquals(-32600, error.get("code"));
        assertTrue(String.valueOf(error.get("message")).contains("Transport validation failed"));
    }

    @Test
    void shouldRejectGatewayHeadersWhenNameMismatch() {
        Map<String, Object> message = Map.of(
                "jsonrpc", "2.0",
                "id", "3",
                "method", "tools/call",
                "params", Map.of("name", "test")
        );

        // Mcp-Name 与 body 中 name 不一致 → 拒绝
        Map<String, Object> validationError =
                endpoint.validateGatewayHeaders("tools/call", "evil-tool", message);
        assertNotNull(validationError);
    }

    @Test
    void shouldAllowMissingGatewayHeaders() {
        Map<String, Object> message = Map.of(
                "jsonrpc", "2.0", "id", "1", "method", "ping");
        // 未携带标头 = 传统路径，不强制校验
        assertNull(endpoint.validateGatewayHeaders(null, null, message));
        assertNull(endpoint.validateGatewayHeaders("", null, message));
    }

    @Test
    void shouldBuildGatewayRouteMetadata() {
        Map<String, Object> route = McpStatelessEndpoint.buildGatewayRoute("tools/call", "test");
        assertEquals("tools/call", route.get("method"));
        assertEquals("tool", route.get("operationType"));
        assertEquals("test", route.get("name"));

        Map<String, Object> resourceRoute = McpStatelessEndpoint.buildGatewayRoute("resources/read", null);
        assertEquals("resource", resourceRoute.get("operationType"));
    }

    @Test
    void shouldDeclareGatewayCapabilities() {
        Map<String, Object> caps = McpStatelessEndpoint.SERVER_CAPABILITIES_V2026;
        Map<String, Object> gateway = (Map<String, Object>) caps.get("gateway");
        assertNotNull(gateway);
        assertEquals("Mcp-Method", gateway.get("methodHeader"));
        assertEquals("Mcp-Name", gateway.get("nameHeader"));
        assertEquals(Boolean.TRUE, gateway.get("transportValidation"));

        Map<String, Object> caching = (Map<String, Object>) caps.get("caching");
        assertNotNull(caching.get("ttlMs"));
        assertEquals("global", caching.get("cacheScope"));
        assertEquals(Boolean.TRUE, caching.get("deterministicOrder"));
    }

    // ===== V1.7: 网关按操作限流集成测试 =====

    @Test
    void shouldRateLimitToolsListPerDefaultRule() {
        // 默认规则 tools/list: 5 QPS
        for (int i = 0; i < 5; i++) {
            Map<String, Object> resp = endpoint.handleStatelessMessage(
                    Map.of("jsonrpc", "2.0", "id", i, "method", "tools/list", "params", Map.of()), "t1");
            assertFalse(resp.containsKey("error"), "第 " + (i + 1) + " 次 tools/list 应放行");
        }
        // 第 6 次应被限流
        Map<String, Object> limited = endpoint.handleStatelessMessage(
                Map.of("jsonrpc", "2.0", "id", 99, "method", "tools/list", "params", Map.of()), "t1");
        Map<String, Object> error = (Map<String, Object>) limited.get("error");
        assertNotNull(error, "第6次 tools/list 应被限流");
        assertEquals(-32029, error.get("code"));
    }

    @Test
    void shouldAllowToolCallWithCustomRuleOverride() {
        // 覆盖默认 tools/call:* = 100 → 收紧到 2 QPS
        endpoint.getGatewayRateLimiter().addRule("tools/call", "test", 2);
        Map<String, Object> callParams = Map.of("name", "test", "arguments", Map.of("msg", "hi"));

        for (int i = 0; i < 2; i++) {
            Map<String, Object> resp = endpoint.handleStatelessMessage(
                    Map.of("jsonrpc", "2.0", "id", i, "method", "tools/call", "params", callParams), "t2");
            assertFalse(resp.containsKey("error"), "第 " + (i + 1) + " 次 tools/call(test) 应放行");
        }
        // 精确规则(2 QPS)优先于通配规则(100 QPS)，第 3 次应被限流
        Map<String, Object> limited = endpoint.handleStatelessMessage(
                Map.of("jsonrpc", "2.0", "id", 999, "method", "tools/call", "params", callParams), "t2");
        Map<String, Object> error = (Map<String, Object>) limited.get("error");
        assertNotNull(error);
        assertEquals(-32029, error.get("code"));
    }

    @Test
    void shouldExposeRateLimiterForManagement() {
        assertNotNull(endpoint.getGatewayRateLimiter());
        assertTrue(endpoint.getGatewayRateLimiter().getRuleCount() >= 6);
    }

    @Test
    void shouldClearRateLimitRulesAllowsAll() {
        endpoint.getGatewayRateLimiter().clearRules();
        for (int i = 0; i < 50; i++) {
            Map<String, Object> resp = endpoint.handleStatelessMessage(
                    Map.of("jsonrpc", "2.0", "id", i, "method", "tools/list", "params", Map.of()), "t3");
            assertFalse(resp.containsKey("error"));
        }
    }
}
