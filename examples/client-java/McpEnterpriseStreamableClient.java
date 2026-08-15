package com.mcp.enterprise.examples;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * MCP Enterprise Server — Streamable HTTP 无状态客户端示例 (2026-07-28 规范)
 *
 * 不依赖 session，直接调用 /api/mcp/v2 端点。
 * 适合与 Claude、Cursor、通义千问等支持 MCP 的 Agent 集成。
 */
public class McpEnterpriseStreamableClient {

    private static final String BASE_URL = "http://localhost:8081";
    private static final String API_KEY = System.getenv().getOrDefault("MCP_API_KEY", "default-admin-key");
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        System.out.println("🚀 MCP Enterprise Streamable HTTP Client Demo");
        System.out.println("==============================================");

        // 1. 能力声明
        System.out.println("\n📡 1. Server capabilities");
        JsonNode caps = get("/api/mcp/v2");
        System.out.println("   Protocol: " + caps.get("protocolVersion").asText());
        System.out.println("   Server: " + caps.get("serverInfo").get("name").asText());

        // 2. Initialize
        System.out.println("\n🚀 2. Initialize");
        ObjectNode initBody = mapper.createObjectNode();
        initBody.put("protocolVersion", "2026-07-28");
        ObjectNode clientInfo = mapper.createObjectNode();
        clientInfo.put("name", "java-streamable-client");
        clientInfo.put("version", "1.0.0");
        initBody.set("clientInfo", clientInfo);
        JsonNode init = post("/api/mcp/v2/initialize", initBody);
        System.out.println("   Initialized: " + init.toString());

        // 3. List tools (stateless)
        System.out.println("\n🔧 3. List tools (stateless)");
        JsonNode tools = get("/api/mcp/v2/tools");
        System.out.println("   Tools: " + tools.toPrettyString());

        // 4. Call tool (stateless)
        System.out.println("\n⚡ 4. Call tool (stateless)");
        ObjectNode callBody = mapper.createObjectNode();
        callBody.put("name", "system_info");
        callBody.set("arguments", mapper.createObjectNode());
        JsonNode callResult = post("/api/mcp/v2/tools/call", callBody);
        System.out.println("   Result: " + callResult.toPrettyString());

        System.out.println("\n✅ Streamable HTTP 示例运行完成!");
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

    private static JsonNode post(String path, ObjectNode body) throws Exception {
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
