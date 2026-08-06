package com.mcp.enterprise.examples;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * MCP Enterprise Server — Streamable HTTP 客户端示例 (Java)
 *
 * 演示 2026-07-28 无状态协议 + Streamable HTTP 双通道调用：
 *   POST /api/mcp/v2/message — JSON-RPC 请求（initialize / tools/list / tools/call）
 *   GET  /api/mcp/v2/stream  — server→client 事件流（endpoint 事件 + listChanged 通知）
 *   POST /api/mcp/v2/notify  — 触发 tools/listChanged 广播
 *
 * 纯 JDK HttpClient 实现，无第三方依赖（Jackson 仅用于 JSON 解析，可换任意 JSON 库）。
 *
 * 使用方式:
 *   javac -cp jackson-databind.jar McpEnterpriseStreamableClient.java
 *   java -cp .;jackson-databind.jar McpEnterpriseStreamableClient
 */
public class McpEnterpriseStreamableClient {

    private static final String BASE_URL = "http://localhost:8081";
    private static final String API_KEY = System.getenv().getOrDefault("MCP_API_KEY", "default-admin-key");
    private static final String PROTOCOL_VERSION = "2026-07-28";
    private static final long STREAM_WAIT_SECONDS = 15;

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        System.out.println("🚀 MCP Enterprise Streamable HTTP Client (Java)");
        System.out.println("=================================================");

        // 1. 协议能力声明
        System.out.println("\n📡 1. 拉取协议能力声明 (GET /api/mcp/v2)...");
        JsonNode caps = get("/api/mcp/v2");
        System.out.println("   protocolVersion: " + caps.get("protocolVersion").asText());
        System.out.println("   transports: " + caps.get("transport").get("supportedTransports"));
        System.out.println("   streamableHttp 通道: "
                + caps.get("transport").get("streamableHttp").get("message").asText());

        // 2. initialize（无状态，携带协议版本）
        System.out.println("\n🔌 2. initialize (POST /api/mcp/v2/message)...");
        JsonNode init = rpc("init-1", "initialize", Map.of(
                "protocolVersion", PROTOCOL_VERSION,
                "clientInfo", Map.of("name", "java-streamable-demo", "version", "1.0.0")
        ));
        System.out.println("   server: " + init.get("result").get("serverInfo").get("name").asText()
                + " v" + init.get("result").get("serverInfo").get("version").asText());

        // 3. tools/list
        System.out.println("\n🔧 3. tools/list...");
        JsonNode toolsResp = rpc("tools-1", "tools/list", Map.of());
        JsonNode tools = toolsResp.get("result").get("tools");
        System.out.println("   工具数: " + tools.size());
        tools.forEach(t -> System.out.println("   - " + t.get("name").asText()
                + " (" + t.get("description").asText() + ")"));

        // 4. tools/call — 调用计算器
        System.out.println("\n⚡ 4. tools/call calculator...");
        JsonNode callResp = rpc("call-1", "tools/call", Map.of(
                "name", "calculator",
                "arguments", Map.of("expression", "1 + 2 * 3")
        ));
        JsonNode content = callResp.get("result").get("content");
        if (content != null && content.size() > 0) {
            System.out.println("   计算结果: " + content.get(0).get("text").asText());
        } else {
            System.out.println("   响应: " + callResp);
        }

        // 5. 事件流演示：打开 stream → 触发 notify → 收到 listChanged 通知
        System.out.println("\n🔔 5. Streamable HTTP 事件流演示...");
        demoEventStream();

        System.out.println("\n✅ Java Streamable HTTP 示例运行完成!");
    }

    /** 事件流演示：后台打开 GET /stream，触发 notify，等待 listChanged 通知 */
    private static void demoEventStream() throws Exception {
        BlockingQueue<String> events = new LinkedBlockingQueue<>();

        // 后台线程：打开 SSE 事件流并持续读取
        HttpRequest streamRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/mcp/v2/stream"))
                .header("Accept", "text/event-stream")
                .header("X-API-Key", API_KEY)
                .timeout(Duration.ofSeconds(STREAM_WAIT_SECONDS + 5))
                .GET()
                .build();

        CompletableFuture<HttpResponse<java.util.stream.Stream<String>>> streamFuture =
                httpClient.sendAsync(streamRequest, HttpResponse.BodyHandlers.ofLines());

        streamFuture.thenAccept(resp -> {
            System.out.println("   ✅ 事件流已连接 (HTTP " + resp.statusCode() + ")");
            resp.body().forEach(line -> {
                if (!line.isBlank()) {
                    try { events.offer(line, 100, TimeUnit.MILLISECONDS); } catch (InterruptedException ignored) {}
                }
            });
        });

        // 等待 endpoint 事件（连接建立信号）
        System.out.println("   等待 endpoint 事件...");
        String endpointEvent = waitForEvent(events, "endpoint", 10);
        System.out.println("   收到: " + endpointEvent);

        // 触发 tools/listChanged 广播
        System.out.println("   触发 notify 广播...");
        JsonNode notifyResp = post("/api/mcp/v2/notify", Map.of());
        System.out.println("   广播投递: " + notifyResp.get("delivered").asInt() + " 个连接");

        // 等待 listChanged 通知
        System.out.println("   等待 notifications/tools/list_changed...");
        String listChanged = waitForEvent(events, "list_changed", 10);
        System.out.println("   收到: " + listChanged);

        // 关闭事件流
        streamFuture.cancel(true);
        System.out.println("   📴 事件流已关闭");
    }

    /** 从事件队列等待指定类型事件（SSE 行格式: event: xxx / data: {...}） */
    private static String waitForEvent(BlockingQueue<String> queue, String type, long seconds)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + seconds * 1000;
        String lastEvent = "";
        while (System.currentTimeMillis() < deadline) {
            String line = queue.poll(1, TimeUnit.SECONDS);
            if (line == null) continue;
            if (line.startsWith("event:")) {
                lastEvent = line.substring(6).trim();
            } else if (line.startsWith("data:") && lastEvent.contains(type)) {
                return lastEvent + " → " + line.substring(5).trim();
            }
        }
        return "⏰ 超时未收到 " + type + " 事件";
    }

    // ===== JSON-RPC 工具方法 =====

    private static JsonNode rpc(String id, String method, Map<String, Object> params) throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.put("jsonrpc", "2.0");
        body.put("id", id);
        body.put("method", method);
        body.set("params", mapper.valueToTree(params));
        return post("/api/mcp/v2/message", body);
    }

    private static JsonNode get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .header("X-API-Key", API_KEY)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return mapper.readTree(response.body());
    }

    private static JsonNode post(String path, Object body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .header("Content-Type", "application/json")
                .header("X-API-Key", API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(
                        body instanceof String ? (String) body : mapper.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return mapper.readTree(response.body());
    }
}
