package com.mcp.enterprise.core.endpoint;

import com.mcp.enterprise.core.model.ToolDefinition;
import com.mcp.enterprise.core.ratelimit.GatewayRateLimitManager;
import com.mcp.enterprise.core.registry.ToolRegistry;
import com.mcp.enterprise.core.security.ToolScopePolicy;
import com.mcp.enterprise.core.tool.McpToolManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.Comparator;

/**
 * MCP 2026-07-28 无状态核心端点 (Stateless Core)
 *
 * 支持无状态 HTTP 架构，适用于 Kubernetes / Cloud Run 弹性伸缩。
 * 不维护 SSE 长连接，每次请求独立处理。
 *
 * 2026-07-28 规范新特性：
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

    /** V1.7: 网关限流路由表（按 Mcp-Method/Mcp-Name 维度限流，2026-07-28 规范落地） */
    private final GatewayRateLimitManager gatewayRateLimiter = new GatewayRateLimitManager();

    /** 2026-07-28 协议版本声明 */
    public static final String MCP_2026_PROTOCOL_VERSION = "2026-07-28";
    public static final String MCP_2025_PROTOCOL_VERSION = "2025-03-26";

    /** W3C Trace Context 标准头部 */
    public static final String TRACEPARENT_HEADER = "traceparent";
    public static final String TRACESTATE_HEADER = "tracestate";

    // ===== 2026-07-28 最终版：网关友好标头 (Gateway-Friendly Headers) =====
    // 网关/API 网关无需解析请求体即可按操作进行速率限制或授权
    public static final String MCP_METHOD_HEADER = "Mcp-Method";
    public static final String MCP_NAME_HEADER = "Mcp-Name";

    /** 缓存控制默认值：工具目录默认 60s 新鲜度 */
    public static final long DEFAULT_TOOLS_TTL_MS = 60_000L;

    /**
     * 服务端能力声明 (MCP 2026-07-28 全面适配)
     *
     * 新增（相对 2025-03-26）：
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
                    "version", "1.0.0",
                    "vendor", "HH-SpringAI-Agent-Starter",
                    "description", "Enterprise MCP Server Framework - Java/Spring Boot - Full 2026-07-28 Compliance"
            ),
            "capabilities", Map.of(
                    "tools", Map.of(
                            "listChanged", true,
                            "supportsPagination", true,
                            "supportsDiscover", true  // ✨ 能力发现
                    ),
                    "resources", Map.of("subscribe", false, "listChanged", false),
                    "prompts", Map.of("listChanged", false),
                    "logging", Map.of(),
                    "tasks", Map.of(  // ✨ 长任务支持
                            "supported", true,
                            "maxTimeoutMs", 300_000  // 5分钟
                    ),
                    "extensions", Map.of(  // ✨ Extensions 一等公民
                            "supported", true,
                            "namespaces", List.of("mcp-enterprise", "custom")
                    ),
                    "security", Map.of(  // ✨ V1.19 工具级 Scope 授权声明
                            "scopeEnforcement", true,
                            "scopeMatch", "exact|*|**",
                            "insufficientScopeCode", -32090
                    )
            ),
            "transport", Map.of(
                    "stateless", true,  // ✨ 无状态核心
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
                    "maxAgeSeconds", 60,
                    "ttlMs", DEFAULT_TOOLS_TTL_MS,       // ✨ 2026-07-28 最终版：新鲜度提示
                    "cacheScope", "global",              // ✨ cacheScope 支持
                    "deterministicOrder", true           // ✨ 确定性排序
            ),
            "gateway", Map.of(  // ✨ 2026-07-28 最终版：网关友好标头
                    "methodHeader", MCP_METHOD_HEADER,
                    "nameHeader", MCP_NAME_HEADER,
                    "transportValidation", true,
                    "benefits", List.of("rate-limit-without-body-parse", "authz-without-body-parse")
            ),
            "tracing", Map.of(
                    "supportsTraceContext", true,  // ✨ W3C Trace Context
                    "traceParentHeader", TRACEPARENT_HEADER,
                    "traceStateHeader", TRACESTATE_HEADER
            ),
            "schema", Map.of(  // ✨ 声明 Schema 版本
                    "jsonSchemaVersion", "2020-12",
                    "supportsFullJsonSchema", true
            ),
            "discovery", Map.of(  // ✨ 能力发现端点
                    "endpoint", "/api/mcp/discover",
                    "format", "application/json"
            )
    );

    public McpStatelessEndpoint(ToolRegistry registry, McpToolManager toolManager) {
        this.registry = registry;
        this.toolManager = toolManager;
        initDefaultRateLimitRules();
    }
    /**
     * V1.7: 默认网关限流规则（可通过管理端点或配置覆盖）
     * - tools/list: 5 QPS（目录拉取防刷）
     * - ping: 20 QPS（健康探测防刷）
     * - tools/call: 100 QPS（所有工具调用总上限）
     * - initialize/server/discover: 10 QPS
     */
    private void initDefaultRateLimitRules() {
        gatewayRateLimiter.addRule("tools/list", "*", 5);
        gatewayRateLimiter.addRule("tools/listChanged", "*", 5);
        gatewayRateLimiter.addRule("ping", "", 20);
        gatewayRateLimiter.addRule("tools/call", "*", 100);
        gatewayRateLimiter.addRule("initialize", "", 10);
        gatewayRateLimiter.addRule("server/discover", "", 10);
        gatewayRateLimiter.addRule("tools/discover", "*", 10);
    }

    // ===== 无状态消息处理 (2026-07-28) =====

    /**
     * 处理 MCP JSON-RPC 消息（无状态模式，兼容旧签名：无令牌上下文，scope 不拦截）
     */
    public Map<String, Object> handleStatelessMessage(Map<String, Object> message, String traceId) {
        return handleStatelessMessage(message, traceId, null);
    }

    /**
     * 处理 MCP JSON-RPC 消息（无状态模式，V1.19：按令牌 scope 做工具级授权）
     *
     * @param tokenScopes 令牌携带的 scope 集合；null 表示调用方无令牌上下文（不拦截，向后兼容）
     */
    public Map<String, Object> handleStatelessMessage(Map<String, Object> message, String traceId, Set<String> tokenScopes) {
        if (message == null || !message.containsKey("method")) {
            return errorResponse(null, -32600, "Invalid Request: missing 'method'");
        }

        String method = (String) message.get("method");
        Object id = message.get("id");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) message.getOrDefault("params", Map.of());

        // V1.7: 按操作限流（网关路由表）。仅凭 method/name 即可决策，无需解析请求体。
        // 命中限流规则且超出配额 → 429 Too Many Requests
        String opName = params != null ? String.valueOf(params.getOrDefault("name", "")) : "";
        if (!gatewayRateLimiter.checkRateLimit(method, opName)) {
            log.warn("⛔ 网关限流拒绝: method={}, name={}, traceId={}", method, opName, traceId);
            Map<String, Object> error = errorResponse(id, -32029, "Rate limit exceeded");
            if (traceId != null) {
                error.put("_traceId", traceId);
            }
            return error;
        }

        log.debug("MCP 无状态消息: method={}, id={}, traceId={}, scopes={}", method, id, traceId, tokenScopes);

        // 添加 traceId 到响应
        Map<String, Object> result = switch (method) {
            case "initialize" -> handleInitialize(id, params);
            case "tools/list" -> handleToolsList(id, params);
            case "tools/call" -> handleToolCall(id, params, tokenScopes);
            case "tools/listChanged" -> handleToolsList(id, params);
            case "tools/discover" -> handleToolsDiscover(id, params);  // ✨ 能力发现
            case "server/discover" -> handleServerDiscover(id, params);  // ✨ Server 发现
            case "tasks/create" -> handleTaskCreate(id, params, tokenScopes);  // ✨ 长任务
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

        // ✨ 2026-07-28 最终版：确定性排序（按 name 字典序）
        // 提升提示词缓存命中率；大规模场景下降低延迟与 Token 成本
        mcpTools.sort(Comparator.comparing(t -> String.valueOf(t.get("name"))));

        String cursor = params != null ? (String) params.get("cursor") : null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tools", mcpTools);

        // 分页支持
        if (cursor != null) {
            result.put("nextCursor", null);
        }

        // ✨ 2026-07-28 最终版：ttlMs + cacheScope 缓存控制（替代旧 _etag/_cachedAt）
        // ttlMs 是新鲜度提示（参考 HTTP Cache-Control）；cacheScope 支持全局/用户级缓存
        String cacheScope = params != null ? (String) params.get("cacheScope") : null;
        Map<String, Object> caching = new LinkedHashMap<>();
        caching.put("ttlMs", DEFAULT_TOOLS_TTL_MS);
        caching.put("cacheScope", "global");
        caching.put("etag", "W/\"" + Integer.toHexString(mcpTools.hashCode()) + "\"");
        caching.put("deterministicOrder", true);
        result.put("caching", caching);

        // 兼容旧字段（部分客户端仍读取）
        result.put("_etag", caching.get("etag"));
        result.put("_cachedAt", System.currentTimeMillis());

        return successResponse(id, result);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> handleToolCall(Object id, Map<String, Object> params, Set<String> tokenScopes) {
        if (params == null) {
            return errorResponse(id, -32602, "Invalid params");
        }

        String toolName = (String) params.get("name");
        Map<String, Object> arguments = (Map<String, Object>) params.getOrDefault("arguments", Map.of());

        if (toolName == null) {
            return errorResponse(id, -32602, "Missing tool name");
        }

        // V1.19: 工具级 Scope 授权（Token Scope → Tool ACL）
        // tokenScopes != null 表示调用方携带 Bearer 令牌上下文 → 强制执行；否则跳过（向后兼容）
        ToolScopePolicy policy = toolManager.getScopePolicy();
        if (tokenScopes != null && policy != null && policy.isEnabled()) {
            ToolDefinition def = registry.getDefinition(toolName);
            if (def != null) {
                ToolScopePolicy.ScopeDecision decision = policy.authorize(tokenScopes, def);
                if (!decision.allowed()) {
                    log.warn("⛔ 工具级 scope 拒绝(stateless): tool={} required={} token={}",
                            toolName, decision.requiredScopes(), decision.tokenScopes());
                    Map<String, Object> errorData = new LinkedHashMap<>();
                    errorData.put("requiredScopes", decision.requiredScopes());
                    errorData.put("tokenScopes", decision.tokenScopes());
                    return insufficientScopeError(id, toolName, errorData);
                }
            }
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

    /**
     * V1.19: RFC 6750 语义的 insufficient_scope 错误响应（JSON-RPC 封装，MCP 自定义错误码 -32090）。
     * HTTP 层（Controller）会叠加 403 状态码与 WWW-Authenticate 头。
     */
    public static Map<String, Object> insufficientScopeError(Object id, String toolName, Map<String, Object> data) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", -32090);
        error.put("message", "insufficient_scope: token lacks required scope for tool " + toolName);
        if (data != null) {
            error.put("data", data);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("error", error);
        return response;
    }

    /** V1.7: 获取网关限流路由表（管理端点用） */
    public GatewayRateLimitManager getGatewayRateLimiter() {
        return gatewayRateLimiter;
    }

    // ===== 工具方法 =====

    /**
     * ✨ 工具能力发现 — tools/discover
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
     * ✨ Server 级能力发现 — server/discover
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

    // ===== 2026-07-28 最终版：网关友好标头处理 (Gateway-Friendly Headers) =====

    /**
     * 校验请求标头与请求体的一致性（传输验证规则）。
     * <p>
     * 网关可仅凭 Mcp-Method / Mcp-Name 标头进行速率限制或授权，
     * 无需解析 JSON 请求体；但后端必须拒绝任何与正文不符的标头，
     * 否则看似无害的标头可能掩盖实际执行的另一项调用。
     *
     * @param mcpMethod  Mcp-Method 标头值（可空）
     * @param mcpName    Mcp-Name 标头值（可空，仅 tools/call 等命名操作）
     * @param message    JSON-RPC 请求体
     * @return null 表示校验通过；否则返回错误响应（不处理请求）
     */
    public Map<String, Object> validateGatewayHeaders(String mcpMethod, String mcpName, Map<String, Object> message) {
        if (mcpMethod == null || mcpMethod.isBlank()) {
            return null;  // 未携带标头 = 传统调用路径，不强制校验
        }
        String bodyMethod = message != null ? String.valueOf(message.get("method")) : null;
        if (bodyMethod == null) {
            return errorResponse(null, -32600, "Invalid Request: missing 'method'");
        }
        // 标头必须与请求体方法一致（规范化：tools/call vs tools.call 均接受）
        String normalizedHeader = mcpMethod.trim().replace('.', '/');
        String normalizedBody = bodyMethod.replace('.', '/');
        if (!normalizedHeader.equals(normalizedBody)) {
            return errorResponse(null, -32600,
                    "Transport validation failed: Mcp-Method header '" + mcpMethod
                            + "' does not match request body method '" + bodyMethod + "'");
        }
        // 命名操作（tools/call / tools/discover 等）：Mcp-Name 必须与 body 中 name 一致
        if (mcpName != null && !mcpName.isBlank()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) message.getOrDefault("params", Map.of());
            Object bodyName = params.get("name");
            if (bodyName != null && !mcpName.trim().equals(String.valueOf(bodyName))) {
                return errorResponse(null, -32600,
                        "Transport validation failed: Mcp-Name header '" + mcpName
                                + "' does not match request body name '" + bodyName + "'");
            }
        }
        return null;
    }

    /**
     * 根据请求推断网关路由信息（供网关限流/授权中间件使用）。
     *
     * @param method MCP 方法（如 tools/call）
     * @param name   命名操作的目标名（如工具名，可空）
     * @return 网关路由元数据
     */
    public static Map<String, Object> buildGatewayRoute(String method, String name) {
        Map<String, Object> route = new LinkedHashMap<>();
        route.put("method", method);
        route.put("operationType", classifyOperation(method));
        if (name != null && !name.isBlank()) {
            route.put("name", name);
        }
        return route;
    }

    private static String classifyOperation(String method) {
        if (method == null) return "unknown";
        if (method.startsWith("tools/")) return "tool";
        if (method.startsWith("resources/")) return "resource";
        if (method.startsWith("prompts/")) return "prompt";
        if (method.startsWith("tasks/")) return "task";
        return "protocol";
    }

    /**
     * ✨ 长任务创建 — tasks/create
     * MCP 2026-07-28 支持异步长任务，返回 taskId 供轮询。
     * V1.19：同步做工具级 scope 预检，避免无权限任务白白排队。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> handleTaskCreate(Object id, Map<String, Object> params, Set<String> tokenScopes) {
        if (params == null || !params.containsKey("tool")) {
            return errorResponse(id, -32602, "Missing 'tool' parameter");
        }

        String toolName = (String) params.get("tool");
        Map<String, Object> arguments = (Map<String, Object>) params.getOrDefault("arguments", Map.of());
        long timeoutMs = params.containsKey("timeoutMs")
                ? ((Number) params.get("timeoutMs")).longValue()
                : 300_000L;

        // V1.19: tasks/create 同样执行 scope 预检（fail-fast，避免任务入队后才发现无权限）
        ToolScopePolicy policy = toolManager.getScopePolicy();
        if (tokenScopes != null && policy != null && policy.isEnabled()) {
            ToolDefinition def = registry.getDefinition(toolName);
            if (def != null) {
                ToolScopePolicy.ScopeDecision decision = policy.authorize(tokenScopes, def);
                if (!decision.allowed()) {
                    log.warn("⛔ 工具级 scope 拒绝(tasks/create): tool={} required={}", toolName, decision.requiredScopes());
                    Map<String, Object> errorData = new LinkedHashMap<>();
                    errorData.put("requiredScopes", decision.requiredScopes());
                    errorData.put("tokenScopes", decision.tokenScopes());
                    return insufficientScopeError(id, toolName, errorData);
                }
            }
        }

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
        // ✨ V1.19 工具级 scope 声明：客户端可在令牌签发时按需申请
        ToolScopePolicy policy = toolManager.getScopePolicy();
        if (policy != null && policy.isEnabled()) {
            discovery.put("security", Map.of(
                    "requiredScopes", policy.resolveRequiredScopes(def),
                    "scopeMatch", "exact|*|**"
            ));
        }
        // ✨ 调用示例（帮助 AI 客户端理解用法）
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

        // ✨ 升级到 JSON Schema 2020-12（MCP 2026-07-28 要求）
        if (!inputSchema.containsKey("$schema")) {
            inputSchema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        }
        if (!inputSchema.containsKey("type")) {
            inputSchema.put("type", "object");
        }
        mcpTool.put("inputSchema", inputSchema);

        // ✨ V1.19 工具级 scope 声明（客户端可据此在 token 签发时申请正确 scope）
        ToolScopePolicy policy = toolManager.getScopePolicy();
        if (policy != null && policy.isEnabled()) {
            Set<String> required = policy.resolveRequiredScopes(def);
            if (!required.isEmpty()) {
                mcpTool.put("requiredScopes", required);
            }
        }

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
