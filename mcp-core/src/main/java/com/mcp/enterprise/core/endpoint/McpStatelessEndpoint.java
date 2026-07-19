package com.mcp.enterprise.core.endpoint;

import com.mcp.enterprise.core.model.ToolDefinition;
import com.mcp.enterprise.core.registry.ToolRegistry;
import com.mcp.enterprise.core.tool.McpToolManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.CompletableFuture;
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

    /** W3C Trace Context 标准头部 */
    public static final String TRACEPARENT_HEADER = "traceparent";
    public static final String TRACESTATE_HEADER = "tracestate";

    /**
     * 服务端能力声明 (MCP 2026-07-28 全面适配)
     *
     * 新增（相对2025-03-26）：
     * - 无状态核心：每个请求自包含
     * - 能力发现：server/discover 端点
     * - W3C Trace Context：traceparent/tracestate
     * - 完整 JSON Schema 2020-12
     * - Extensions 一等公民
     * - Tasks 长任务支持
     * - MCP Apps 交互UI
     */
    public static final Map<String, Object> SERVER_CAPABILITIES_V2026 = Map.of(
            "protocolVersion", MCP_2026_PROTOCOL_VERSION,
            "supportedProtocolVersions", List.of(MCP_2026_PROTOCOL_VERSION, MCP_2025_PROTOCOL_VERSION),
            "serverInfo", Map.of(
                    "name", "Spring-AI-MCP-Enterprise",
                    "version", "0.11.0",
                    "vendor", "HH-SpringAI-Agent-Starter",
                    "description", "Enterprise MCP Server Framework - Java/Spring Boot - Full 2026-07-28 Compliance"
            ),
            "capabilities", Map.of(
                    "tools", Map.of(
                            "listChanged", true,
                            "supportsPagination", true,
                            "supportsDiscover", true  // 🆕 能力发现
                    ),
                    "resources", Map.of("subscribe", false, "listChanged", false),
                    "prompts", Map.of("listChanged", false),
                    "logging", Map.of(),
                    "tasks", Map.of(  // 🆕 长任务支持
                            "supported", true,
                            "maxTimeoutMs", 300_000  // 5分钟
                    ),
                    "extensions", Map.of(  // 🆕 Extensions 一等公民
                            "supported", true,
                            "namespaces", List.of("mcp-enterprise", "custom")
                    )
            ),
            "transport", Map.of(
                    "stateless", true,  // 🆕 无状态核心
                    "supportedTransports", List.of("streamable-http", "sse")
            ),
            "caching", Map.of(
                    "supportsETag", true,
                    "supportsCacheControl", true,
                    "maxAgeSeconds", 60
            ),
            "tracing", Map.of(
                    "supportsTraceContext", true,  // 🆕 W3C Trace Context
                    "traceParentHeader", TRACEPARENT_HEADER,
                    "traceStateHeader", TRACESTATE_HEADER
            ),
            "schema", Map.of(  // 🆕 声明 Schema 版本
                    "jsonSchemaVersion", "2020-12",
                    "supportsFullJsonSchema", true
            ),
            "discovery", Map.of(  // 🆕 能力发现端点
                    "endpoint", "/api/mcp/discover",
                    "format", "application/json"
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
            case "tools/discover" -> handleToolsDiscover(id, params);  // 🆕 能力发现
            case "server/discover" -> handleServerDiscover(id, params);  // 🆕 Server 发现
            case "tasks/create" -> handleTaskCreate(id, params);  // 🆕 长任务
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

    /**
     * 🆕 工具能力发现 — tools/discover
     * 返回单个工具的完整能力描述，包括 inputSchema、outputSchema、
     * 缓存策略、速率限制和调用示例。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> handleToolsDiscover(Object id, Map<String, Object> params) {
        String toolName = params != null ? (String) params.get("name") : null;

        if (toolName == null) {
            // 返回所有工具的能力摘要
            List<ToolDefinition> tools = registry.listAll().collectList().block();
            List<Map<String, Object>> discoveries = new ArrayList<>();
            if (tools != null) {
                for (ToolDefinition def : tools) {
                    discoveries.add(buildToolDiscovery(def));
                }
            }
            return successResponse(id, Map.of("tools", discoveries));
        }

        ToolDefinition def = registry.getDefinition(toolName);
        if (def == null) {
            return errorResponse(id, -32602, "Tool not found: " + toolName);
        }
        return successResponse(id, buildToolDiscovery(def));
    }

    /**
     * 🆕 Server 级能力发现 — server/discover
     * 提供完整的 Server 能力清单，供网关和客户端自动发现。
     */
    private Map<String, Object> handleServerDiscover(Object id, Map<String, Object> params) {
        Map<String, Object> discovery = new LinkedHashMap<>();
        discovery.putAll(SERVER_CAPABILITIES_V2026);

        // 动态注入工具和统计
        List<ToolDefinition> tools = registry.listAll().collectList().block();
        Map<String, Object> dynamicInfo = new LinkedHashMap<>();
        dynamicInfo.put("toolCount", tools != null ? tools.size() : 0);
        dynamicInfo.put("uptime", System.currentTimeMillis());  // 可用 Spring Boot actuator 计算
        dynamicInfo.put("health", Map.of("status", "UP"));
        discovery.put("_dynamic", dynamicInfo);

        return successResponse(id, discovery);
    }

    /**
     * 🆕 长任务创建 — tasks/create
     * MCP 2026-07-28 支持异步长任务，返回 taskId 供轮询。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> handleTaskCreate(Object id, Map<String, Object> params) {
        if (params == null || !params.containsKey("tool")) {
            return errorResponse(id, -32602, "Missing 'tool' parameter");
        }

        String toolName = (String) params.get("tool");
        Map<String, Object> arguments = (Map<String, Object>) params.getOrDefault("arguments", Map.of());
        long timeoutMs = params.containsKey("timeoutMs")
                ? ((Number) params.get("timeoutMs")).longValue()
                : 300_000L;

        String taskId = UUID.randomUUID().toString();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", taskId);
        result.put("status", "pending");
        result.put("tool", toolName);
        result.put("createdAt", System.currentTimeMillis());
        result.put("timeoutMs", timeoutMs);
        result.put("_checkEndpoint", "/api/mcp/tasks/" + taskId);

        // 异步执行（简化版，生产环境应使用 TaskExecutor 或消息队列）
        CompletableFuture.runAsync(() -> {
            Map<String, Object> execResult = toolManager.invoke(toolName, arguments).block();
            log.info("Task {} completed: {}", taskId, execResult);
        });

        return successResponse(id, result);
    }

    private Map<String, Object> buildToolDiscovery(ToolDefinition def) {
        Map<String, Object> discovery = new LinkedHashMap<>();
        discovery.put("name", def.getName());
        discovery.put("displayName", def.getDisplayName());
        discovery.put("description", def.getDescription());
        discovery.put("category", def.getCategory());
        discovery.put("version", def.getVersion());
        discovery.put("inputSchema", buildFullJsonSchema(def));
        discovery.put("outputSchema", Map.of("type", "object", "properties", Map.of(
                "content", Map.of("type", "array", "items", Map.of("type", "object", "properties", Map.of(
                        "type", Map.of("type", "string", "enum", List.of("text", "image", "resource")),
                        "text", Map.of("type", "string")
                )))
        )));
        discovery.put("caching", Map.of(
                "cacheable", true,
                "ttlMs", 60_000
        ));
        discovery.put("rateLimit", Map.of(
                "perSecond", def.getRateLimitPerSecond(),
                "timeoutMs", def.getTimeoutMs()
        ));
        // 🆕 调用示例（帮助 AI 客户端理解用法）
        discovery.put("examples", List.of(
                Map.of(
                        "description", "调用 " + def.getDisplayName(),
                        "method", "tools/call",
                        "params", Map.of("name", def.getName(), "arguments", def.getInputSchema() != null
                                ? Map.of("placeholder", "see inputSchema for required fields")
                                : Map.of())
                )
        ));
        return discovery;
    }

    private Map<String, Object> buildFullJsonSchema(ToolDefinition def) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        schema.put("type", "object");

        if (def.getInputSchema() != null) {
            schema.putAll(def.getInputSchema());
        } else {
            schema.put("properties", Map.of());
        }
        return schema;
    }

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

        // 🆕 升级到 JSON Schema 2020-12（MCP 2026-07-28 要求）
        if (!inputSchema.containsKey("$schema")) {
            inputSchema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
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
