package com.mcp.enterprise.core.endpoint;

import com.mcp.enterprise.core.model.ToolDefinition;
import com.mcp.enterprise.core.registry.ToolRegistry;
import com.mcp.enterprise.core.tool.McpToolExecutor;
import com.mcp.enterprise.core.tool.McpToolManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * McpSseEndpoint 单元测试
 */
class McpSseEndpointTest {

    private ToolRegistry registry;
    private McpToolManager toolManager;
    private McpSseEndpoint endpoint;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        toolManager = new McpToolManager(registry);
        endpoint = new McpSseEndpoint(registry, toolManager);

        // 注册一个测试工具
        McpToolExecutor testTool = new McpToolExecutor() {
            @Override
            public ToolDefinition getDefinition() {
                return new ToolDefinition("echo", "Echo", "回显消息", "test", "1.0.0",
                        null, true, "user", 5000, 10,
                        Map.of("type", "object", "properties", Map.of("msg", Map.of("type", "string")),
                                "required", java.util.List.of("msg")),
                        null);
            }

            @Override
            public Mono<Map<String, Object>> execute(Map<String, Object> params) {
                String msg = params != null ? (String) params.get("msg") : "none";
                return Mono.just(Map.of("success", true, "result", "echo: " + msg));
            }
        };
        toolManager.registerExecutor(testTool);
    }

    @Test
    void shouldHaveCorrectCapabilities() {
        Map<String, Object> caps = McpSseEndpoint.SERVER_CAPABILITIES;
        assertEquals("2025-03-26", caps.get("protocolVersion"));
    }

    @Test
    void shouldHandleInitialize() {
        Map<String, Object> response = endpoint.handleMessage(Map.of(
                "jsonrpc", "2.0", "id", "1", "method", "initialize"
        ));
        assertTrue(response.containsKey("result"));
    }

    @Test
    void shouldListTools() {
        Map<String, Object> response = endpoint.handleMessage(Map.of(
                "jsonrpc", "2.0", "id", "2", "method", "tools/list"
        ));
        Map<String, Object> result = (Map<String, Object>) response.get("result");
        java.util.List<?> tools = (java.util.List<?>) result.get("tools");
        assertEquals(1, tools.size());
    }

    @Test
    void shouldCallTool() {
        Map<String, Object> response = endpoint.handleMessage(Map.of(
                "jsonrpc", "2.0", "id", "3", "method", "tools/call",
                "params", Map.of("name", "echo", "arguments", Map.of("msg", "hello"))
        ));
        assertNotNull(response.get("result"));
    }

    @Test
    void shouldHandlePing() {
        Map<String, Object> response = endpoint.handleMessage(Map.of(
                "jsonrpc", "2.0", "id", "4", "method", "ping"
        ));
        assertNotNull(response.get("result"));
    }

    @Test
    void shouldReturnErrorForUnknownMethod() {
        Map<String, Object> response = endpoint.handleMessage(Map.of(
                "jsonrpc", "2.0", "id", "5", "method", "unknown"
        ));
        assertTrue(response.containsKey("error"));
    }

    @Test
    void shouldReturnErrorForMissingMethod() {
        Map<String, Object> response = endpoint.handleMessage(Map.of("jsonrpc", "2.0"));
        assertTrue(response.containsKey("error"));
    }

    @Test
    void shouldHandleNullMessage() {
        Map<String, Object> response = endpoint.handleMessage(null);
        assertTrue(response.containsKey("error"));
    }
}
