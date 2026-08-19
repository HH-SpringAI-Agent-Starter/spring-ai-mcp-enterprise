package com.mcp.enterprise.server.endpoint;

import com.mcp.enterprise.core.endpoint.McpStatelessEndpoint;
import com.mcp.enterprise.core.model.ToolDefinition;
import com.mcp.enterprise.core.registry.ToolRegistry;
import com.mcp.enterprise.core.tool.McpToolExecutor;
import com.mcp.enterprise.core.tool.McpToolManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * McpStatelessController 单元测试
 */
class McpStatelessControllerTest {

    private McpStatelessController controller;

    @BeforeEach
    void setUp() {
        ToolRegistry registry = new ToolRegistry();
        McpToolManager toolManager = new McpToolManager(registry);
        McpStatelessEndpoint endpoint = new McpStatelessEndpoint(registry, toolManager);
        controller = new McpStatelessController(endpoint);

        // 注册一个测试工具
        toolManager.registerExecutor(new McpToolExecutor() {
            @Override
            public ToolDefinition getDefinition() {
                return new ToolDefinition(
                        "greet", "Greet", "问候", "test",
                        "1.0.0", null, true, "user", 5000, 10,
                        Map.of("type", "object", "properties", Map.of("name", Map.of("type", "string")),
                                "required", List.of("name")),
                        null);
            }

            @Override
            public Mono<Map<String, Object>> execute(Map<String, Object> params) {
                String name = params != null ? (String) params.get("name") : "World";
                return Mono.just(Map.of("success", true, "result", "Hello, " + name));
            }
        });
    }

    @Test
    void getCapabilitiesShouldReturnProtocol() {
        Map<String, Object> caps = controller.getCapabilities();
        assertEquals("2026-07-28", caps.get("protocolVersion"));
    }

    @Test
    void handleStatelessMessageShouldProcessMessage() {
        Map<String, Object> message = Map.of(
                "jsonrpc", "2.0", "id", "1", "method", "ping"
        );
        Map<String, Object> response = controller.handleStatelessMessage(message, null, null, null, null);
        assertNotNull(response);
        assertTrue(response.containsKey("result"));
    }

    @Test
    void initializeShouldReturnServerInfo() {
        Map<String, Object> result = controller.initialize(null);
        assertNotNull(result);
        assertTrue(result.containsKey("result"));
    }

    @Test
    void initializeWithParamsShouldHandleProtocol() {
        Map<String, Object> result = controller.initialize(Map.of("protocolVersion", "2026-07-28"));
        assertNotNull(result);
    }

    @Test
    void listToolsShouldReturnRegisteredTools() {
        Map<String, Object> result = controller.listTools(null);
        assertNotNull(result);
        Map<String, Object> rpcResult = (Map<String, Object>) result.get("result");
        assertNotNull(rpcResult);
        assertTrue(rpcResult.containsKey("tools"));
    }

    @Test
    void listToolsWithCursorShouldSupportPagination() {
        Map<String, Object> result = controller.listTools("page1");
        assertNotNull(result);
    }

    @Test
    void callToolShouldExecuteTool() {
        Map<String, Object> result = controller.callTool(Map.of("name", "greet", "arguments", Map.of("name", "QClaw")));
        assertNotNull(result);
        assertTrue(result.containsKey("result"));
    }

    @Test
    void callToolWithoutNameShouldReturnError() {
        Map<String, Object> result = controller.callTool(Map.of());
        assertTrue(result.containsKey("error"));
    }

    @Test
    void healthShouldReturnUp() {
        Map<String, Object> health = controller.health();
        assertEquals("UP", health.get("status"));
        assertEquals("1.0.0", health.get("version"));
        assertEquals("stateless", health.get("mode"));
    }

    @Test
    void healthShouldReportStreamableHttpTransport() {
        Map<String, Object> health = controller.health();
        assertEquals("streamable-http", health.get("transport"));
        assertTrue(health.containsKey("connectedStreams"));
    }

    @Test
    void notifyWithoutStreamsShouldReturnZeroDelivered() {
        Map<String, Object> result = controller.notifyToolsChanged();
        assertNotNull(result);
        assertEquals("ok", result.get("status"));
        assertEquals(0, result.get("delivered"));
        assertEquals(0, result.get("connectedStreams"));
    }

    @Test
    void openStreamShouldReturnEmitter() {
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter = controller.stream();
        assertNotNull(emitter);
        // 关闭以释放心跳线程
        emitter.complete();
    }

    @Test
    void notifyAfterOpenStreamShouldDeliverToConnectedClient() throws Exception {
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter = controller.stream();
        assertNotNull(emitter);
        Map<String, Object> result = controller.notifyToolsChanged();
        assertEquals(1, result.get("delivered"));
        emitter.complete();
    }

    @Test
    void handleStatelessMessageShouldIncludeTraceId() {
        Map<String, Object> message = Map.of(
                "jsonrpc", "2.0", "id", "1", "method", "ping"
        );
        Map<String, Object> response = controller.handleStatelessMessage(message, "trace-id-001", null, null, null);
        assertEquals("trace-id-001", response.get("_traceId"));
    }

    @Test
    void handleMessageShouldHandleNullBody() {
        Map<String, Object> response = controller.handleStatelessMessage(null, null, null, null, null);
        assertTrue(response.containsKey("error"));
    }

    // ===== V1.6: 网关友好标头（Mcp-Method / Mcp-Name） =====

    @Test
    void handleMessageWithGatewayHeadersShouldPassValidation() {
        Map<String, Object> message = Map.of(
                "jsonrpc", "2.0", "id", "1", "method", "tools/call",
                "params", Map.of("name", "greet")
        );
        // Mcp-Method=tools/call + Mcp-Name=greet 与请求体一致 → 正常处理
        Map<String, Object> response = controller.handleStatelessMessage(message, null, null, "tools/call", "greet");
        assertNotNull(response);
        assertFalse(response.containsKey("error"));
    }

    @Test
    void handleMessageWithMismatchedGatewayHeaderShouldFailValidation() {
        Map<String, Object> message = Map.of(
                "jsonrpc", "2.0", "id", "1", "method", "tools/list"
        );
        // Mcp-Method=tools/call 与请求体 tools/list 不一致 → 拒绝（防标头掩盖）
        Map<String, Object> response = controller.handleStatelessMessage(message, null, null, "tools/call", null);
        assertNotNull(response);
        assertTrue(response.containsKey("error"));
    }
}
