package com.mcp.enterprise.examples;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * MCP Enterprise Server Java 客户端示例
 *
 * 演示如何通过 REST API 对接 MCP Enterprise Server
 * 不依赖 Spring Boot，纯 JDK HttpClient 实现
 *
 * 使用方式:
 *   javac McpEnterpriseClient.java
 *   java McpEnterpriseClient
 */
public class McpEnterpriseClient {

    private static final String BASE_URL = "http://localhost:8081";
    private static final String API_KEY = System.getenv().getOrDefault("MCP_API_KEY", "default-admin-key");
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        System.out.println("🚀 MCP Enterprise Client Demo");
        System.out.println("================================");

        // 1. 健康检查
        System.out.println("\n📡 1. 健康检查...");
        JsonNode health = get("/api/mcp/health");
        System.out.println("   状态: " + health.get("status").asText());
        System.out.println("   工具数: " + health.get("toolCount").asInt());
        System.out.println("   活跃会话: " + health.get("activeSessions").asInt());

        // 2. 连接服务（获取 sessionId）
        System.out.println("\n🔗 2. 连接服务...");
        JsonNode connectResult = post("/api/mcp/connect", Map.of("clientName", "java-demo-client"));
        String sessionId = connectResult.get("sessionId").asText();
        System.out.println("   Session ID: " + sessionId);
        System.out.println("   服务版本: " + connectResult.get("serverVersion").asText());

        // 3. 列出可用工具
        System.out.println("\n🔧 3. 可用工具列表...");
        JsonNode tools = get("/api/mcp/tools");
        System.out.println("   总工具数: " + tools.get("total").asInt());
        tools.get("tools").forEach(tool -> {
            System.out.println("   - " + tool.get("name").asText()
                    + " (" + tool.get("displayName").asText() + ")"
                    + " [" + tool.get("category").asText() + "]");
        });

        // 4. 获取某个工具详情
        System.out.println("\n📋 4. 查看工具详情...");
        // 尝试获取第一个工具的详情
        if (tools.get("tools").size() > 0) {
            String firstTool = tools.get("tools").get(0).get("name").asText();
            JsonNode toolDetail = get("/api/mcp/tools/" + firstTool);
            System.out.println("   工具名: " + toolDetail.get("tool").get("name").asText());
            System.out.println("   描述: " + toolDetail.get("tool").get("description").asText());
            System.out.println("   启用状态: " + toolDetail.get("tool").get("enabled").asBoolean());
        }

        // 5. 调用工具
        System.out.println("\n⚡ 5. 调用工具...");
        ObjectNode params = mapper.createObjectNode();
        params.put("query", "example query");
        JsonNode invokeResult = postWithApiKey("/api/mcp/tools/example/invoke", params);
        System.out.println("   调用结果: " + invokeResult.get("status").asText());

        // 6. 查看统计
        System.out.println("\n📊 6. 服务统计...");
        JsonNode stats = get("/api/mcp/stats");
        System.out.println("   工具总数: " + stats.get("tools").get("total").asInt());
        System.out.println("   活跃会话: " + stats.get("sessions").get("active").asInt());

        // 7. 断开连接
        System.out.println("\n👋 7. 断开连接...");
        JsonNode disconnect = post("/api/mcp/disconnect", Map.of("sessionId", sessionId));
        System.out.println("   断开成功: " + disconnect.get("success").asBoolean());

        System.out.println("\n✅ 示例运行完成!");
    }

    // ===== HTTP 工具方法 =====

    private static JsonNode get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .header("X-API-Key", API_KEY)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return mapper.readTree(response.body());
    }

    private static JsonNode post(String path, Map<String, Object> body) throws Exception {
        String json = mapper.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .header("Content-Type", "application/json")
                .header("X-API-Key", API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return mapper.readTree(response.body());
    }

    private static JsonNode postWithApiKey(String path, ObjectNode body) throws Exception {
        String json = mapper.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .header("Content-Type", "application/json")
                .header("X-API-Key", API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return mapper.readTree(response.body());
    }
}
