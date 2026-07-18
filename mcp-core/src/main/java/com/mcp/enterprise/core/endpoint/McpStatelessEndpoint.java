package com.mcp.enterprise.core.endpoint;

import com.mcp.enterprise.core.model.ToolDefinition;
import com.mcp.enterprise.core.registry.ToolRegistry;
import com.mcp.enterprise.core.tool.McpToolManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * MCP 2026-07-28 无状态核心端点 (Stateless Core)
 *
 * 支持无状态 HTTP 架构，适用于 Kubernetes / Cloud Run 弹性伸缩。
 * 不维护 SSE 长连接，每次请求独立处理。
 *
 * 2026-07-28 规范新增特性：
 * - 无状态核心 → 每个请求自包含，无需 session
 * - 能力发现 → Server 在 initialize 响应中声明完整能力
 * - 缓存 → 支持 ETag/Cache-Control
 * - 链路追踪 → 通过 traceId 追踪
 * - 完整 JSON Schema → inputSchema 使用完整 JSON Schema 规范
 *
 * 兼容模式：同时支持旧版 MCP 2025-03-26 协议和老版 SSE 端点
 */
public class McpStatelessEndpoint {

    private static final Logger log = LoggerFactory.getLogger(McpStatelessEndpoint.class);

    private final ToolRegistry registry;
    private final McpToolManager toolManager;

    /** 2026-07-28 协议版本声明 */
    public static final String MCP_2026_PROTOCOL_VERSION = "2026-07-28";
    public static final String MCP_2025_PROTOCOL_VERSION = "2025-03-26";

    /** 服务端能力声明 (2026-07-28 兼容) */
    public static final Map<String, Object> SERVER_CAPABILITIES_V2026 = Map.of(
            "protocolVersion", MCP_2026_PROTOCOL_VERSION,
            "supportedProtocolVersions", List.of(MCP_2026_PROTOCOL_VERSION, MCP_2025_PROTOCOL_VERSION),
            "capabilities", Map.of(
                    "tools", Map.of("listChanged", true, "supportsPagination", true),
                    "resources", Map.of("subscribe", false),
                    "prompts", Map.of(),
                    "logging", Map.of(),
                    "experimental", Map.of("stateless", true, "caching", true, "tracing", true)
            ),
            "serverInfo", Map.of(
                    "name", "Spring-AI-MCP-Enterprise",
                    "version", "0.0.2",
                    "vendor", "HH-SpringAI-Agent-Starter",
                    "description", "Enterprise MCP Server Framework - Java/Spring Boot"
            ),
            "caching", Map.of(
                    "supportsETag", true,
                    "supportsCacheControl", true,
                    "maxAgeSeconds", 60
            ),
            "tracing", Map.of(
                    "supportsTraceId", true,
                    "traceIdHeader", "X-MCP-Trace-Id"
            )
    );

    public McpStatelessEndpoint(ToolRegistry registry, McpToolManager toolManager) {
        this.registry = registry;
        this.toolManager = toolManager;
    }

    // ===== 无状态消息处理 (2026-07-28) =====

    /**
     * 处理 MCP JSON-RPC 消息（无状态模式）
     * 每个请求独立处理，不依赖 session
     */
    public Map<String, Object> handleStatelessMessage(Map<String, Object> message, String traceId) {
        if (message == null || !message.containsKey("method")) {
            return errorResponse(null, -32600, "Invalid Request: missing 'method'");
        }

        String method = (String) message.get("method");
        Object id = message.get("id");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) message.getOrDefault("params", Map.of());

        log.debug("MCP 无状态消息: method={}, id={}, traceId={}", method, id, traceId);

        // 添加 traceId 到响应
        Map<String, Object> result = switch (method) {
            case "initialize" -> handleInitialize(id, params);
            case "tools/list" -> handleToolsList(id, params);
            case "tools/call" -> handleToolCall(id, params);
            case "tools/listChanged" -> handleToolsList(id, params);
            case "ping" -> successResponse(id, Map.of("status", "ok"));
            default -> errorResponse(id, -32601, "Method not found: " + method);
        };

        if (traceId != null) {
            result.put("_traceId", traceId);
        }

        return result;
    }

    // ===== MCP 方法处理 =====

    private Map<String, Object> handleInitialize(Object id, Map<String, Object> params) {
        // 检查客户端声明的协议版本，决定返回哪个版本的能力声明
        if (params != null) {
            String clientVersion = (String) params.get("protocolVersion");
            if (MCP_2026_PROTOCOL_VERSION.equals(clientVersion)) {
                return successResponse(id, SERVER_CAPABILITIES_V2026);
            }
        }
        // 兼容旧版客户端
        return successResponse(id, McpSseEndpoint.SERVER_CAPABILITIES);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> handleToolsList(Object id, Map<String, Object> params) {
        List<ToolDefinition> tools = registry.listAll().collectList().block();
        List<Map<String, Object>> mcpTools = new ArrayList<>();

        if (tools != null) {
            for (ToolDefinition def : tools) {
                mcpTools.add(convertToMcpToolV2026(def));
            }
        }

        String cursor = params != null ? (String) params.get("cursor") : null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tools", mcpTools);

        // 分页支持
        if (cursor != null) {
            result.put("nextCursor", null);
        }

        // 缓存支持
        result.put("_etag", "W/\"" + Integer.toHexString(mcpTools.hashCode()) + "\"");
        result.put("_cachedAt", System.currentTimeMillis());

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

        String content = result.containsKey("result")
                ? String.valueOf(result.get("result"))
                : result.containsKey("error")
                ? "Error: " + result.get("error")
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

    private Map<String, Object> convertToMcpToolV2026(ToolDefinition def) {
        Map<String, Object> mcpTool = new LinkedHashMap<>();
        mcpTool.put("name", def.getName());
        mcpTool.put("description", def.getDescription());
        mcpTool.put("displayName", def.getDisplayName());
        mcpTool.put("category", def.getCategory());
        mcpTool.put("version", def.getVersion());

        // 完整 JSON Schema 支持 (2026-07-28 新特性)
        Map<String, Object> inputSchema = def.getInputSchema() != null
                ? new LinkedHashMap<>(def.getInputSchema())
                : new LinkedHashMap<String, Object>();

        if (!inputSchema.containsKey("$schema")) {
            inputSchema.put("$schema", "http://json-schema.org/draft-07/schema#");
        }
        if (!inputSchema.containsKey("type")) {
            inputSchema.put("type", "object");
        }
        mcpTool.put("inputSchema", inputSchema);

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
