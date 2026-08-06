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
 * MCP 2026-07-28 鏃犵姸鎬佹牳蹇冪鐐?(Stateless Core)
 *
 * 鏀寔鏃犵姸鎬?HTTP 鏋舵瀯锛岄€傜敤浜?Kubernetes / Cloud Run 寮规€т几缂┿€?
 * 涓嶇淮鎶?SSE 闀胯繛鎺ワ紝姣忔璇锋眰鐙珛澶勭悊銆?
 *
 * 2026-07-28 瑙勮寖鏂板鐗规€э細
 * - 鏃犵姸鎬佹牳蹇?鈫?姣忎釜璇锋眰鑷寘鍚紝鏃犻渶 session
 * - 鑳藉姏鍙戠幇 鈫?Server 鍦?initialize 鍝嶅簲涓０鏄庡畬鏁磋兘鍔?
 * - 缂撳瓨 鈫?鏀寔 ETag/Cache-Control
 * - 閾捐矾杩借釜 鈫?閫氳繃 traceId 杩借釜
 * - 瀹屾暣 JSON Schema 鈫?inputSchema 浣跨敤瀹屾暣 JSON Schema 瑙勮寖
 *
 * 鍏煎妯″紡锛氬悓鏃舵敮鎸佹棫鐗?MCP 2025-03-26 鍗忚鍜岃€佺増 SSE 绔偣
 */
public class McpStatelessEndpoint {

    private static final Logger log = LoggerFactory.getLogger(McpStatelessEndpoint.class);

    private final ToolRegistry registry;
    private final McpToolManager toolManager;

    /** 2026-07-28 鍗忚鐗堟湰澹版槑 */
    public static final String MCP_2026_PROTOCOL_VERSION = "2026-07-28";
    public static final String MCP_2025_PROTOCOL_VERSION = "2025-03-26";

    /** W3C Trace Context 鏍囧噯澶撮儴 */
    public static final String TRACEPARENT_HEADER = "traceparent";
    public static final String TRACESTATE_HEADER = "tracestate";

    /**
     * 鏈嶅姟绔兘鍔涘０鏄?(MCP 2026-07-28 鍏ㄩ潰閫傞厤)
     *
     * 鏂板锛堢浉瀵?025-03-26锛夛細
     * - 鏃犵姸鎬佹牳蹇冿細姣忎釜璇锋眰鑷寘鍚?
     * - 鑳藉姏鍙戠幇锛歴erver/discover 绔偣
     * - W3C Trace Context锛歵raceparent/tracestate
     * - 瀹屾暣 JSON Schema 2020-12
     * - Extensions 涓€绛夊叕姘?
     * - Tasks 闀夸换鍔℃敮鎸?
     * - MCP Apps 浜や簰UI
     */
    public static final Map<String, Object> SERVER_CAPABILITIES_V2026 = Map.of(
            "protocolVersion", MCP_2026_PROTOCOL_VERSION,
            "supportedProtocolVersions", List.of(MCP_2026_PROTOCOL_VERSION, MCP_2025_PROTOCOL_VERSION),
            "serverInfo", Map.of(
                    "name", "Spring-AI-MCP-Enterprise",
                    "version", "1.0.0",
                    "vendor", "HH-SpringAI-Agent-Starter",
                    "description", "Enterprise MCP Server Framework - Java/Spring Boot - Full 2026-07-28 Compliance"
            ),
            "capabilities", Map.of(
                    "tools", Map.of(
                            "listChanged", true,
                            "supportsPagination", true,
                            "supportsDiscover", true  // 馃啎 鑳藉姏鍙戠幇
                    ),
                    "resources", Map.of("subscribe", false, "listChanged", false),
                    "prompts", Map.of("listChanged", false),
                    "logging", Map.of(),
                    "tasks", Map.of(  // 馃啎 闀夸换鍔℃敮鎸?
                            "supported", true,
                            "maxTimeoutMs", 300_000  // 5鍒嗛挓
                    ),
                    "extensions", Map.of(  // 馃啎 Extensions 涓€绛夊叕姘?
                            "supported", true,
                            "namespaces", List.of("mcp-enterprise", "custom")
                    )
            ),
            "transport", Map.of(
                    "stateless", true,  // 馃啎 鏃犵姸鎬佹牳蹇?
                    "supportedTransports", List.of("streamable-http", "sse"),
                    "streamableHttp", Map.of(  // 首次明确流通道定位
                            "endpoint", "/api/mcp/v2",
                            "stream", "/api/mcp/v2/stream",      // GET: server→client 通知流
                            "message", "/api/mcp/v2/message",    // POST: JSON-RPC 请求/响应
                            "notify", "/api/mcp/v2/notify"       // POST: tools/listChanged 广播
                    )
            ),
            "caching", Map.of(
                    "supportsETag", true,
                    "supportsCacheControl", true,
                    "maxAgeSeconds", 60
            ),
            "tracing", Map.of(
                    "supportsTraceContext", true,  // 馃啎 W3C Trace Context
                    "traceParentHeader", TRACEPARENT_HEADER,
                    "traceStateHeader", TRACESTATE_HEADER
            ),
            "schema", Map.of(  // 馃啎 澹版槑 Schema 鐗堟湰
                    "jsonSchemaVersion", "2020-12",
                    "supportsFullJsonSchema", true
            ),
            "discovery", Map.of(  // 馃啎 鑳藉姏鍙戠幇绔偣
                    "endpoint", "/api/mcp/discover",
                    "format", "application/json"
            )
    );

    public McpStatelessEndpoint(ToolRegistry registry, McpToolManager toolManager) {
        this.registry = registry;
        this.toolManager = toolManager;
    }

    // ===== 鏃犵姸鎬佹秷鎭鐞?(2026-07-28) =====

    /**
     * 澶勭悊 MCP JSON-RPC 娑堟伅锛堟棤鐘舵€佹ā寮忥級
     * 姣忎釜璇锋眰鐙珛澶勭悊锛屼笉渚濊禆 session
     */
    public Map<String, Object> handleStatelessMessage(Map<String, Object> message, String traceId) {
        if (message == null || !message.containsKey("method")) {
            return errorResponse(null, -32600, "Invalid Request: missing 'method'");
        }

        String method = (String) message.get("method");
        Object id = message.get("id");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) message.getOrDefault("params", Map.of());

        log.debug("MCP 鏃犵姸鎬佹秷鎭? method={}, id={}, traceId={}", method, id, traceId);

        // 娣诲姞 traceId 鍒板搷搴?
        Map<String, Object> result = switch (method) {
            case "initialize" -> handleInitialize(id, params);
            case "tools/list" -> handleToolsList(id, params);
            case "tools/call" -> handleToolCall(id, params);
            case "tools/listChanged" -> handleToolsList(id, params);
            case "tools/discover" -> handleToolsDiscover(id, params);  // 馃啎 鑳藉姏鍙戠幇
            case "server/discover" -> handleServerDiscover(id, params);  // 馃啎 Server 鍙戠幇
            case "tasks/create" -> handleTaskCreate(id, params);  // 馃啎 闀夸换鍔?
            case "ping" -> successResponse(id, Map.of("status", "ok"));
            default -> errorResponse(id, -32601, "Method not found: " + method);
        };

        if (traceId != null) {
            result.put("_traceId", traceId);
        }

        return result;
    }

    // ===== MCP 鏂规硶澶勭悊 =====

    private Map<String, Object> handleInitialize(Object id, Map<String, Object> params) {
        // 妫€鏌ュ鎴风澹版槑鐨勫崗璁増鏈紝鍐冲畾杩斿洖鍝釜鐗堟湰鐨勮兘鍔涘０鏄?
        if (params != null) {
            String clientVersion = (String) params.get("protocolVersion");
            if (MCP_2026_PROTOCOL_VERSION.equals(clientVersion)) {
                return successResponse(id, SERVER_CAPABILITIES_V2026);
            }
        }
        // 鍏煎鏃х増瀹㈡埛绔?
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

        // 鍒嗛〉鏀寔
        if (cursor != null) {
            result.put("nextCursor", null);
        }

        // 缂撳瓨鏀寔
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

        // 浣跨敤 toolManager 鎵ц
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

    // ===== 宸ュ叿鏂规硶 =====

    /**
     * 馃啎 宸ュ叿鑳藉姏鍙戠幇 鈥?tools/discover
     * 杩斿洖鍗曚釜宸ュ叿鐨勫畬鏁磋兘鍔涙弿杩帮紝鍖呮嫭 inputSchema銆乷utputSchema銆?
     * 缂撳瓨绛栫暐銆侀€熺巼闄愬埗鍜岃皟鐢ㄧず渚嬨€?
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> handleToolsDiscover(Object id, Map<String, Object> params) {
        String toolName = params != null ? (String) params.get("name") : null;

        if (toolName == null) {
            // 杩斿洖鎵€鏈夊伐鍏风殑鑳藉姏鎽樿
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
     * 馃啎 Server 绾ц兘鍔涘彂鐜?鈥?server/discover
     * 鎻愪緵瀹屾暣鐨?Server 鑳藉姏娓呭崟锛屼緵缃戝叧鍜屽鎴风鑷姩鍙戠幇銆?
     */
    private Map<String, Object> handleServerDiscover(Object id, Map<String, Object> params) {
        Map<String, Object> discovery = new LinkedHashMap<>();
        discovery.putAll(SERVER_CAPABILITIES_V2026);

        // 鍔ㄦ€佹敞鍏ュ伐鍏峰拰缁熻
        List<ToolDefinition> tools = registry.listAll().collectList().block();
        Map<String, Object> dynamicInfo = new LinkedHashMap<>();
        dynamicInfo.put("toolCount", tools != null ? tools.size() : 0);
        dynamicInfo.put("uptime", System.currentTimeMillis());  // 鍙敤 Spring Boot actuator 璁＄畻
        dynamicInfo.put("health", Map.of("status", "UP"));
        discovery.put("_dynamic", dynamicInfo);

        return successResponse(id, discovery);
    }

    /**
     * 馃啎 闀夸换鍔″垱寤?鈥?tasks/create
     * MCP 2026-07-28 鏀寔寮傛闀夸换鍔★紝杩斿洖 taskId 渚涜疆璇€?
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

        // 寮傛鎵ц锛堢畝鍖栫増锛岀敓浜х幆澧冨簲浣跨敤 TaskExecutor 鎴栨秷鎭槦鍒楋級
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
        // 馃啎 璋冪敤绀轰緥锛堝府鍔?AI 瀹㈡埛绔悊瑙ｇ敤娉曪級
        discovery.put("examples", List.of(
                Map.of(
                        "description", "璋冪敤 " + def.getDisplayName(),
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

        // 瀹屾暣 JSON Schema 鏀寔 (2026-07-28 鏂扮壒鎬?
        Map<String, Object> inputSchema = def.getInputSchema() != null
                ? new LinkedHashMap<>(def.getInputSchema())
                : new LinkedHashMap<String, Object>();

        // 馃啎 鍗囩骇鍒?JSON Schema 2020-12锛圡CP 2026-07-28 瑕佹眰锛?
        if (!inputSchema.containsKey("$schema")) {
            inputSchema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        }
        if (!inputSchema.containsKey("type")) {
            inputSchema.put("type", "object");
        }
        mcpTool.put("inputSchema", inputSchema);

        return mcpTool;
    }

    // ===== 鍝嶅簲宸ュ叿 =====

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
