package com.mcp.enterprise.gateway.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.enterprise.gateway.McpGatewayProperties;
import com.mcp.enterprise.gateway.RemoteToolDescriptor;
import com.mcp.enterprise.gateway.UpstreamMcpClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V1.25 Streamable HTTP 上游客户端测试：用 JDK HttpServer 起一个内存桩 MCP Server，
 * 验证 JSON-RPC 握手、tools/list、tools/call（JSON 与 SSE 两种响应）、错误与鉴权头透传。
 */
class StreamableHttpUpstreamClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpServer server;
    private volatile String lastAuthorizationHeader;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mcp", this::handle);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    // ===== 桩实现：按 JSON-RPC method 分派 =====

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        lastAuthorizationHeader = exchange.getRequestHeaders().getFirst("Authorization");
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");

        String method = "unknown";
        String id = "0";
        if (body.contains("\"method\"")) {
            Map<?, ?> req = MAPPER.readValue(body, Map.class);
            method = String.valueOf(req.get("method"));
            Object idObj = req.get("id");
            if (idObj != null) {
                id = String.valueOf(idObj);
            }
        }

        String payload;
        boolean sse = false;
        switch (method) {
            case "initialize" -> payload = rpcResult(id, Map.of(
                    "protocolVersion", "2026-07-28",
                    "capabilities", Map.of("tools", Map.of()),
                    "serverInfo", Map.of("name", "stub", "version", "1.0")));
            case "notifications/initialized" -> payload = "";
            case "tools/list" -> payload = rpcResult(id, Map.of("tools", List.of(
                    Map.of("name", "get_user", "description", "查询用户",
                            "inputSchema", Map.of("type", "object", "properties", Map.of("id", Map.of("type", "integer")))),
                    Map.of("name", "list_orders", "description", "订单列表"))));
            case "tools/call" -> {
                Map<?, ?> req = MAPPER.readValue(body, Map.class);
                Map<?, ?> params = (Map<?, ?>) req.get("params");
                String tool = String.valueOf(params.get("name"));
                switch (tool) {
                    case "echo" -> payload = rpcResult(id, Map.of(
                            "content", List.of(Map.of("type", "text", "text", "hello")),
                            "isError", false,
                            "structuredContent", Map.of("greeting", "hello", "tool", tool)));
                    case "sse_echo" -> {
                        sse = true;
                        payload = "event: message\ndata: " + rpcResult(id, Map.of(
                                "content", List.of(Map.of("type", "text", "text", "sse ok")),
                                "isError", false,
                                "structuredContent", Map.of("transport", "sse"))) + "\n\n";
                    }
                    case "boom" -> payload = rpcResult(id, Map.of(
                            "content", List.of(Map.of("type", "text", "text", "业务校验失败")),
                            "isError", true));
                    default -> payload = rpcError(id, -32602, "unknown tool " + tool);
                }
            }
            default -> payload = rpcError(id, -32601, "method not found");
        }

        if (payload.isEmpty()) {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            return;
        }
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", sse ? "text/event-stream" : "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String rpcResult(String id, Object result) throws IOException {
        return MAPPER.writeValueAsString(Map.of(
                "jsonrpc", "2.0", "id", id, "result", result));
    }

    private static String rpcError(String id, int code, String message) throws IOException {
        return MAPPER.writeValueAsString(Map.of(
                "jsonrpc", "2.0", "id", id,
                "error", Map.of("code", code, "message", message)));
    }

    // ===== 客户端 =====

    private McpGatewayProperties.Upstream config(Map<String, Object> overrides) {
        McpGatewayProperties.Upstream u = new McpGatewayProperties.Upstream();
        u.setName("stub");
        u.setUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/mcp");
        u.setApiKey("test-key-123");
        u.setRequestTimeoutMs(5000);
        u.setConnectTimeoutMs(2000);
        overrides.forEach((k, v) -> {
            switch (k) {
                case "apiKey" -> u.setApiKey((String) v);
                default -> throw new IllegalArgumentException("unknown override " + k);
            }
        });
        return u;
    }

    // ===== 测试 =====

    @Test
    @DisplayName("initialize 握手 → ping 为 true，且透传 Bearer 鉴权头")
    void ping_handshakeAndAuthHeader() {
        UpstreamMcpClient client = new StreamableHttpUpstreamClient(config(Map.of()));
        assertThat(client.ping().block()).isTrue();
        assertThat(lastAuthorizationHeader).isEqualTo("Bearer test-key-123");
    }

    @Test
    @DisplayName("tools/list 解析出远程工具描述（名称/描述/入参 Schema）")
    void listTools_parsesDescriptors() {
        UpstreamMcpClient client = new StreamableHttpUpstreamClient(config(Map.of()));
        List<RemoteToolDescriptor> tools = client.listTools().block();
        assertThat(tools).hasSize(2);
        assertThat(tools.get(0).name()).isEqualTo("get_user");
        assertThat(tools.get(0).description()).isEqualTo("查询用户");
        assertThat(tools.get(0).inputSchema()).isNotEmpty();
        assertThat(tools.get(1).name()).isEqualTo("list_orders");
    }

    @Test
    @DisplayName("tools/call 成功 → success=true 且 result 取 structuredContent")
    void callTool_success_structuredContent() {
        UpstreamMcpClient client = new StreamableHttpUpstreamClient(config(Map.of()));
        Map<String, Object> result = client.callTool("echo", Map.of("msg", "hi")).block();
        assertThat(result.get("success")).isEqualTo(Boolean.TRUE);
        assertThat(result.get("result")).isEqualTo(Map.of("greeting", "hello", "tool", "echo"));
    }

    @Test
    @DisplayName("tools/call isError=true → success=false 且 error 含文本内容")
    void callTool_isError() {
        UpstreamMcpClient client = new StreamableHttpUpstreamClient(config(Map.of()));
        Map<String, Object> result = client.callTool("boom", Map.of()).block();
        assertThat(result.get("success")).isEqualTo(Boolean.FALSE);
        assertThat(String.valueOf(result.get("error"))).contains("业务校验失败");
    }

    @Test
    @DisplayName("tools/call 协议级 JSON-RPC error → success=false")
    void callTool_rpcError() {
        UpstreamMcpClient client = new StreamableHttpUpstreamClient(config(Map.of()));
        Map<String, Object> result = client.callTool("nope", Map.of()).block();
        assertThat(result.get("success")).isEqualTo(Boolean.FALSE);
        assertThat(String.valueOf(result.get("error"))).contains("unknown tool nope");
    }

    @Test
    @DisplayName("SSE 响应（text/event-stream data: 行）可解析")
    void callTool_sseResponse() {
        UpstreamMcpClient client = new StreamableHttpUpstreamClient(config(Map.of()));
        Map<String, Object> result = client.callTool("sse_echo", Map.of()).block();
        assertThat(result.get("success")).isEqualTo(Boolean.TRUE);
        assertThat(result.get("result")).isEqualTo(Map.of("transport", "sse"));
    }
}