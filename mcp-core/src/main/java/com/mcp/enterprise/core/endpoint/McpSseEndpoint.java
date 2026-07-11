package com.mcp.enterprise.core.endpoint;

import com.mcp.enterprise.core.model.ToolDefinition;
import com.mcp.enterprise.core.registry.ToolRegistry;
import com.mcp.enterprise.core.tool.McpToolManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * MCP SSE (Server-Sent Events) 核心处理器
 *
 * 提供 SSE 通信的基础能力，包括：
 * - MCP 协议消息处理（tools/list, tools/call, initialize, ping）
 * - JSON-RPC 2.0 响应格式
 * - 广播通知
 *
 * 此为核心类，不含 @RestController 注解。
 * Web 控制器在 mcp-server 模块中实现。
 */
public class McpSseEndpoint {

    private static final Logger log = LoggerFactory.getLogger(McpSseEndpoint.class);

    private final ToolRegistry registry;
    private final McpToolManager toolManager;

    /** 服务端能力声明 */
    public static final Map<String, Object> SERVER_CAPABILITIES = Map.of(
            "protocolVersion", "2025-03-26",
            "capabilities", Map.of(
                    "tools", Map.of("listChanged", true),
                    "logging", Map.of(),
                    "experimental", Map.of()
            ),
            "serverInfo", Map.of(
                    "name", "Spring-AI-MCP-Enterprise",
                    "version", "0.0.2"
            )
    );

    public McpSseEndpoint(ToolRegistry registry, McpToolManager toolManager) {
        this.registry = registry;
        this.toolManager = toolManager;
    }

    // ===== MCP 消息处理 =====

    /**
     * 处理 MCP JSON-RPC 消息
     */
    public Map<String, Object> handleMessage(Map<String, Object> message) {
        if (message == null || !message.containsKey("method")) {
            return errorResponse(null, -32600, "Invalid Request: missing 'method'");
        }

        String method = (String) message.get("method");
        Object id = message.get("id");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) message.getOrDefault("params", Map.of());

        log.debug("MCP 消息: method={}, id={}", method, id);

        return switch (method) {
            case "initialize" -> handleInitialize(id, params);
            case "tools/list" -> handleToolsList(id, params);
            case "tools/call" -> handleToolCall(id, params);
            case "tools/listChanged" -> handleToolsList(id, params);
            case "ping" -> successResponse(id, Map.of());
            default -> errorResponse(id, -32601, "Method not found: " + method);
        };
    }

    // ===== MCP 方法处理 =====

    private Map<String, Object> handleInitialize(Object id, Map<String, Object> params) {
        return successResponse(id, SERVER_CAPABILITIES);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> handleToolsList(Object id, Map<String, Object> params) {
        List<ToolDefinition> tools = registry.listAll().collectList().block();
        List<Map<String, Object>> mcpTools = new ArrayList<>();

        if (tools != null) {
            for (ToolDefinition def : tools) {
                mcpTools.add(convertToMcpTool(def));
            }
        }

        String cursor = params != null ? (String) params.get("cursor") : null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tools", mcpTools);
        if (cursor != null) {
            result.put("nextCursor", null);
        }
        return successResponse(id, result);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> handleToolCall(Object id, Map<String, Object> params) {
        if (params == null) {
            return errorResponse(id, -32602, "Invalid params");
        }

        String toolName = (String) params.get("name");
        Map<String, Object> arguments = (Map<String, Object>) params.getOrDefault("arguments", Map.of());

        if (toolName == null) {
            return errorResponse(id, -32602, "Missing tool name");
        }

        // 使用 toolManager 执行
        Map<String, Object> result = toolManager.invoke(toolName, arguments).block();

        if (result == null) {
            return errorResponse(id, -32603, "Tool execution failed");
        }

        String content = result.containsKey("result") ? String.valueOf(result.get("result"))
                : result.containsKey("error") ? "Error: " + result.get("error")
                : String.valueOf(result);

        List<Map<String, Object>> contentList = List.of(
                Map.of("type", "text", "text", content)
        );

        Map<String, Object> toolResult = new LinkedHashMap<>();
        toolResult.put("content", contentList);
        if (!result.getOrDefault("success", true).equals(true)) {
            toolResult.put("isError", true);
        }

        return successResponse(id, toolResult);
    }

    // ===== 工具方法 =====

    private Map<String, Object> convertToMcpTool(ToolDefinition def) {
        Map<String, Object> mcpTool = new LinkedHashMap<>();
        mcpTool.put("name", def.getName());
        mcpTool.put("description", def.getDescription());
        mcpTool.put("displayName", def.getDisplayName());
        mcpTool.put("category", def.getCategory());
        mcpTool.put("version", def.getVersion());
        mcpTool.put("inputSchema", def.getInputSchema() != null ? def.getInputSchema() : Map.of("type", "object", "properties", Map.of()));
        return mcpTool;
    }

    // ===== 响应工具 =====

    public static Map<String, Object> successResponse(Object id, Object result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", result);
        return response;
    }

    public static Map<String, Object> errorResponse(Object id, int code, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("error", Map.of("code", code, "message", message));
        return response;
    }
}
