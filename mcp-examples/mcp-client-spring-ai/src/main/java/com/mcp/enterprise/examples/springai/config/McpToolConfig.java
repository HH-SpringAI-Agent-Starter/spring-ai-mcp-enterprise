package com.mcp.enterprise.examples.springai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 从 MCP Enterprise Server 拉取工具并注册为 Spring AI ToolCallback
 */
@Configuration
public class McpToolConfig {

    private static final Logger log = LoggerFactory.getLogger(McpToolConfig.class);

    @Value("${mcp.enterprise.server.url:http://localhost:8081}")
    private String serverUrl;

    @Value("${mcp.enterprise.server.api-key:default-admin-key}")
    private String apiKey;

    @Bean
    public RestClient mcpRestClient() {
        return RestClient.builder()
                .baseUrl(serverUrl)
                .defaultHeader("X-API-Key", apiKey)
                .build();
    }

    /**
     * 从 MCP Server 发现工具并包装成 Spring AI ToolCallback
     */
    @Bean
    public List<ToolCallback> mcpTools(RestClient restClient) {
        List<ToolCallback> callbacks = new ArrayList<>();

        try {
            List<Map<String, Object>> tools = restClient.get()
                    .uri("/api/mcp/tools")
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (tools == null) {
                log.warn("MCP Server 返回空工具列表");
                return callbacks;
            }

            for (Map<String, Object> tool : tools) {
                String name = (String) tool.get("name");
                String description = (String) tool.get("description");
                if (name == null || description == null) {
                    continue;
                }

                callbacks.add(buildToolCallback(restClient, name, description));
                log.info("已注册 MCP 工具: {}", name);
            }
        } catch (Exception e) {
            log.error("从 MCP Server 发现工具失败: {}", e.getMessage());
        }

        return callbacks;
    }

    @SuppressWarnings("unchecked")
    private ToolCallback buildToolCallback(RestClient restClient, String name, String description) {
        Function<String, String> executor = requestJson -> {
            try {
                Map<String, Object> response = restClient.post()
                        .uri("/api/mcp/tools/{name}/invoke", name)
                        .body(requestJson)
                        .retrieve()
                        .body(Map.class);
                return response != null ? response.toString() : "null response";
            } catch (Exception e) {
                return "调用失败: " + e.getMessage();
            }
        };

        return FunctionToolCallback.builder(name, executor)
                .description(description)
                .inputType(String.class)
                .build();
    }
}
