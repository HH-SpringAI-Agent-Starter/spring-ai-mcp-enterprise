package com.mcp.integration.springai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.enterprise.core.model.ToolDefinition;
import com.mcp.enterprise.core.tool.McpToolExecutor;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spring AI 工具 → 企业 MCP 工具执行器适配器（V1.23）
 *
 * <p>把一个 {@link ToolCallback}（来自 Spring AI 的 {@code @Tool} 注解方法、{@code FunctionToolCallback}、
 * 或 MCP Client 动态发现的工具）适配成企业级 {@link McpToolExecutor}：</p>
 *
 * <ul>
 *   <li>定义侧：把 Spring AI 的 {@code ToolDefinition}（name/description/inputSchema-JSON）转成企业
 *       {@link ToolDefinition}，并补齐 category / version / RBAC 角色 / Scope / 超时 / 限流。</li>
 *   <li>执行侧：把 MCP 的 {@code Map} 参数序列化为 JSON，调用 {@code ToolCallback.call(json)}，
 *       再把返回的 JSON 字符串解析回 {@code Map}，统一包装为 {@code {success, result, tool}}。</li>
 * </ul>
 *
 * <p>由于注册进的是企业 {@code McpToolManager}，这些工具会自动获得 RBAC、限流、审计日志、
 * 工具级 Scope 授权、健康检查等治理能力，无需改动任何 Spring AI 业务代码。</p>
 */
public class SpringAiToolCallbackExecutor implements McpToolExecutor {

    private final ToolCallback callback;
    private final ToolDefinition definition;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SpringAiToolCallbackExecutor(ToolCallback callback, SpringAiToolsProperties properties) {
        this.callback = callback;
        this.definition = buildDefinition(callback, properties);
    }

    private ToolDefinition buildDefinition(ToolCallback callback, SpringAiToolsProperties properties) {
        org.springframework.ai.tool.definition.ToolDefinition saDefinition = callback.getToolDefinition();
        String rawName = saDefinition.name();

        return new ToolDefinition(
                properties.applyPrefix(rawName),
                rawName,
                saDefinition.description() != null ? saDefinition.description() : rawName,
                properties.getCategory(),
                properties.getVersion(),
                "spring-ai",
                true,
                properties.getDefaultRoles(),
                properties.getTimeoutMs(),
                properties.getRateLimitPerSecond(),
                parseSchema(saDefinition.inputSchema()),
                Map.of("springAiTool", true, "source", "spring-ai", "rawName", rawName),
                properties.getRequiredScopes()
        );
    }

    /** 把 Spring AI 的 JSON Schema 字符串解析为 Map；解析失败时降级为宽松 object schema */
    private Map<String, Object> parseSchema(String inputSchemaJson) {
        if (inputSchemaJson == null || inputSchemaJson.isBlank()) {
            return fallbackSchema();
        }
        try {
            return objectMapper.readValue(inputSchemaJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return fallbackSchema();
        }
    }

    private Map<String, Object> fallbackSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<>());
        return schema;
    }

    @Override
    public ToolDefinition getDefinition() {
        return definition;
    }

    @Override
    public Mono<Map<String, Object>> execute(Map<String, Object> params) {
        Map<String, Object> args = params == null ? Map.of() : params;
        return Mono.fromCallable(() -> {
                    String json = objectMapper.writeValueAsString(args);
                    String rawResult = callback.call(json);
                    return buildResult(rawResult);
                })
                .onErrorResume(error -> {
                    Map<String, Object> failure = new LinkedHashMap<>();
                    failure.put("success", false);
                    failure.put("error", error.getMessage());
                    failure.put("tool", definition.getName());
                    return Mono.just(failure);
                });
    }

    private Map<String, Object> buildResult(String rawResult) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("tool", definition.getName());
        if (rawResult == null) {
            result.put("result", "");
            return result;
        }
        try {
            JsonNode node = objectMapper.readTree(rawResult);
            result.put("result", normalizeResult(node));
        } catch (Exception e) {
            // 非 JSON 返回值（如纯文本）原样透传
            result.put("result", rawResult);
        }
        return result;
    }

    /**
     * 归一化 Spring AI 工具返回值：
     * <ul>
     *   <li>Object/Array 节点 → Map/List；</li>
     *   <li>文本节点（Spring AI 对 String 返回类型会做 JSON 双重编码）→ 尝试二次解析为 Map/List，失败则透传文本；</li>
     *   <li>数字/布尔 → 原始标量。</li>
     * </ul>
     */
    private Object normalizeResult(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            String text = node.asText();
            try {
                return objectMapper.readValue(text, new TypeReference<Map<String, Object>>() {
                });
            } catch (Exception e) {
                try {
                    return objectMapper.readValue(text, java.util.List.class);
                } catch (Exception ignored) {
                    return text;
                }
            }
        }
        if (node.isObject()) {
            return objectMapper.convertValue(node, Map.class);
        }
        if (node.isArray()) {
            return objectMapper.convertValue(node, java.util.List.class);
        }
        return node.asText();
    }

    /** 暴露底层 Spring AI ToolCallback，便于测试与二次集成 */
    public ToolCallback getToolCallback() {
        return callback;
    }
}
