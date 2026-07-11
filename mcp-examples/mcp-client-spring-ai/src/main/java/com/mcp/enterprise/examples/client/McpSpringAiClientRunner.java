package com.mcp.enterprise.examples.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Spring AI MCP Client 示例 — 通过 RestTemplate 调用 MCP Enterprise Server
 *
 * 使用 Spring AI MCP Client 的实际集成方式：
 * 方式一：Spring AI ChatClient + MCP Tool 调用（生产推荐）
 * 方式二：REST API 直接调用（演示/测试用）
 *
 * 本示例展示方式一的核心配置思路 + 方式二的具体实现。
 */
@Component
public class McpSpringAiClientRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(McpSpringAiClientRunner.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${mcp.server.url:http://localhost:8081}")
    private String mcpServerUrl;

    @Value("${mcp.server.api-key:default-admin-key}")
    private String apiKey;

    public McpSpringAiClientRunner(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("========================================");
        log.info("  MCP Enterprise Spring AI Client Demo");
        log.info("========================================");

        // ===== 步骤 1: 健康检查 =====
        log.info("\n📡 Step 1: 健康检查");
        String healthUrl = mcpServerUrl + "/api/mcp/health";
        try {
            JsonNode health = restTemplate.getForObject(healthUrl, JsonNode.class);
            log.info("   状态: {}", health.get("status").asText());
            log.info("   工具数: {}", health.get("toolCount").asInt());
            log.info("   活跃会话: {}", health.get("activeSessions").asInt());
        } catch (Exception e) {
            log.warn("   ❌ 无法连接到 MCP Server ({}): {}", healthUrl, e.getMessage());
            log.warn("   请确保 MCP Enterprise Server 已在 localhost:8081 启动");
            return;
        }

        // ===== 步骤 2: 连接服务 =====
        log.info("\n🔗 Step 2: 连接服务");
        String connectResult = postForJson("/api/mcp/connect",
                Map.of("clientName", "spring-ai-client-demo"));
        JsonNode connectJson = objectMapper.readTree(connectResult);
        String sessionId = connectJson.get("sessionId").asText();
        log.info("   Session ID: {}", sessionId);

        // ===== 步骤 3: 列出工具 =====
        log.info("\n🔧 Step 3: 列出可用工具");
        String toolsResult = getForJson("/api/mcp/tools");
        JsonNode toolsJson = objectMapper.readTree(toolsResult);
        int totalTools = toolsJson.get("total").asInt();
        log.info("   总工具数: {}", totalTools);
        if (toolsJson.has("tools")) {
            for (JsonNode tool : toolsJson.get("tools")) {
                log.info("   - {} [{}] 描述: {}",
                        tool.get("name").asText(),
                        tool.get("category").asText(),
                        tool.get("description").asText());
            }
        }

        // ===== 步骤 4: 调用工具 =====
        log.info("\n⚡ Step 4: 调用工具 (system-info)");
        String invokeResult = postForJson("/api/mcp/tools/system-info/invoke",
                Map.of("category", "all"));
        JsonNode invokeJson = objectMapper.readTree(invokeResult);
        log.info("   调用结果: {}", invokeJson.get("success").asText());
        if (invokeJson.has("result")) {
            log.info("   工具返回: {}", invokeJson.get("result").toPrettyString());
        }

        // ===== 步骤 5: 断开连接 =====
        log.info("\n👋 Step 5: 断开连接");
        String disconnectResult = postForJson("/api/mcp/disconnect",
                Map.of("sessionId", sessionId));
        JsonNode disconnectJson = objectMapper.readTree(disconnectResult);
        log.info("   断开成功: {}", disconnectJson.get("success").asBoolean());

        log.info("\n========================================");
        log.info("  ✅ Spring AI Client 示例运行完成!");
        log.info("========================================");
    }

    /**
     * ===== Spring AI MCP 生产级集成方式 =====
     *
     * 在实际生产项目中，不需要手动调用 REST API，而是通过 Spring AI 配置直接集成：
     *
     * application.yml:
     * ```yaml
     * spring:
     *   ai:
     *     mcp:
     *       client:
     *         enabled: true
     *         endpoints:
     *           - url: http://localhost:8081/api/mcp
     *             headers:
     *               X-API-Key: ${MCP_API_KEY}
     * ```
     *
     * 然后在代码中直接使用 ChatClient 自动获取 MCP 工具：
     * ```java
     * @Bean
     * CommandLineRunner demo(ChatClient.Builder chatClientBuilder) {
     *     return args -> {
     *         ChatClient chatClient = chatClientBuilder.build();
     *         String response = chatClient.prompt()
     *             .user("查询系统信息")
     *             .call()
     *             .content();
     *     };
     * }
     * ```
     *
     * 这种方式下，Spring AI 自动管理 MCP 客户端生命周期、
     * 自动发现工具、自动注入到 ChatClient 的 tool context 中。
     */

    // ===== HTTP 工具方法 =====

    private String getForJson(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", apiKey);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(
                mcpServerUrl + path, HttpMethod.GET, entity, String.class);
        return response.getBody();
    }

    private String postForJson(String path, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-Key", apiKey);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.exchange(
                mcpServerUrl + path, HttpMethod.POST, entity, String.class);
        return response.getBody();
    }
}
