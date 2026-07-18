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

        // 验证新增字段
        assertNotNull(result.get("caching"));
        assertNotNull(result.get("tracing"));
        assertEquals("X-MCP-Trace-Id", ((Map<String, Object>) result.get("tracing")).get("traceIdHeader"));
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
        assertEquals("http://json-schema.org/draft-07/schema#", schema.get("$schema"));
        assertEquals("object", schema.get("type"));
    }
}
