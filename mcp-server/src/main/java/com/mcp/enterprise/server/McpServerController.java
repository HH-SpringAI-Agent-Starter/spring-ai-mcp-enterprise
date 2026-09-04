package com.mcp.enterprise.server;

import com.mcp.enterprise.core.endpoint.McpStatelessEndpoint;
import com.mcp.enterprise.core.model.ToolDefinition;
import com.mcp.enterprise.core.registry.ToolRegistry;
import com.mcp.enterprise.core.security.McpOAuth2Manager;
import com.mcp.enterprise.core.security.McpSecurityManager;
import com.mcp.enterprise.core.security.ToolScopePolicy;
import com.mcp.enterprise.core.tool.McpToolManager;
import com.mcp.enterprise.server.security.McpBearerAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP Enterprise REST API
 *
 * 对外提供企业级 MCP 服务接口
 */
@RestController
@RequestMapping("/api/mcp")
public class McpServerController {

    private static final Logger log = LoggerFactory.getLogger(McpServerController.class);

    private final ToolRegistry registry;
    private final McpSecurityManager securityManager;
    private final McpToolManager toolManager;

    // 活跃连接记录
    private final Map<String, ClientSession> sessions = new ConcurrentHashMap<>();

    public McpServerController(ToolRegistry registry,
                               McpSecurityManager securityManager,
                               McpToolManager toolManager) {
        this.registry = registry;
        this.securityManager = securityManager;
        this.toolManager = toolManager;
    }

    // ===== 连接管理 =====

    @PostMapping("/connect")
    public Map<String, Object> connect(@RequestHeader("X-API-Key") String apiKey,
                                       @RequestParam(defaultValue = "anonymous") String clientName) {
        boolean valid = securityManager.validateApiKey(apiKey).block();
        if (!valid) {
            securityManager.audit(apiKey, "connect", "auth_failed", false, "Invalid API Key");
            return Map.of("success", false, "error", "Invalid API Key");
        }

        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, new ClientSession(sessionId, clientName, apiKey, System.currentTimeMillis()));

        securityManager.audit(apiKey, "connect", "connected", true, "session=" + sessionId + " client=" + clientName);

        return Map.of(
                "success", true,
                "sessionId", sessionId,
                "serverVersion", "1.0.0",
                "supportedProtocols", List.of("mcp-v1", "streaming-v1"),
                "serverName", "Spring-AI-MCP-Enterprise"
        );
    }

    @PostMapping("/disconnect")
    public Map<String, Object> disconnect(@RequestParam String sessionId) {
        ClientSession session = sessions.remove(sessionId);
        if (session != null) {
            securityManager.audit(session.apiKey(), "disconnect", "disconnected", true, "session=" + sessionId);
        }
        return Map.of("success", true);
    }

    // ===== 工具发现 =====

    @GetMapping("/tools")
    public Map<String, Object> listTools() {
        List<ToolDefinition> toolList = registry.listAll().collectList().block();
        if (toolList == null) toolList = List.of();
        return Map.of(
                "success", true,
                "total", toolList.size(),
                "tools", toolList
        );
    }

    @GetMapping("/tools/{name}")
    public Map<String, Object> getTool(@PathVariable String name) {
        ToolDefinition def = registry.getDefinition(name);
        if (def == null) return Map.of("success", false, "error", "Tool not found: " + name);
        return Map.of("success", true, "tool", def);
    }

    // ===== 工具调用（企业版：走 McpToolManager） =====

    @PostMapping("/tools/{name}/invoke")
    public Map<String, Object> invokeTool(@PathVariable String name,
                                          @RequestBody Map<String, Object> params,
                                          @RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                          HttpServletRequest request, HttpServletResponse response) throws IOException {

        // ===== V1.19: 工具级 Scope 授权（Token Scope → Tool ACL，RFC 6750 insufficient_scope） =====
        // 携带 Bearer 令牌（mcp.tokenInfo 由 McpBearerAuthFilter 写入）→ 强制 scope 校验；
        // 无令牌上下文（旧版 X-API-Key 路径）→ 不做 scope 拦截，保持向后兼容
        Object tokenInfoAttr = request.getAttribute(McpBearerAuthFilter.ATTR_TOKEN_INFO);
        if (tokenInfoAttr instanceof McpOAuth2Manager.TokenInfo tokenInfo) {
            Map<String, Object> result = toolManager.invokeWithScope(name, params, tokenInfo.scopes()).block();
            if (result != null && result.containsKey("httpStatus")) {
                int status = ((Number) result.get("httpStatus")).intValue();
                if (status == 403) {
                    log.warn("⛔ 工具级 scope 拒绝(REST): tool={}", name);
                    securityManager.audit(apiKey, name, "invoke", false,
                            "insufficient_scope required=" + result.get("requiredScopes"));
                    // RFC 6750 §3.1: 403 + WWW-Authenticate: Bearer error="insufficient_scope"
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    response.setHeader("WWW-Authenticate",
                            "Bearer realm=\"mcp-enterprise\", error=\"insufficient_scope\", " +
                                    "error_description=\"The request requires higher privileges than provided by the access token\"");
                    response.getWriter().write("{\"success\":false,\"error\":\"insufficient_scope\"," +
                            "\"tool\":\"" + name + "\"," +
                            "\"requiredScopes\":" + result.get("requiredScopes") + "," +
                            "\"tokenScopes\":" + result.get("tokenScopes") + "}");
                    return null;
                }
            }
            // 安全审计
            securityManager.audit(apiKey, name, "invoke", true, "params=" + params.keySet());
            return result;
        }

        // 安全审计
        securityManager.audit(apiKey, name, "invoke", true, "params=" + params.keySet());

        // 通过 toolManager 执行
        return toolManager.invoke(name, params).block();
    }

    // ===== V1.19: 工具级 Scope 授权（Token Scope → Tool ACL） =====

    /**
     * GET /api/mcp/scope/policy — Scope 授权策略观察端点。
     * 返回：开关状态 + 已解析的各工具所需 scope（客户端可在令牌签发时按需申请）。
     */
    @GetMapping("/scope/policy")
    public Map<String, Object> scopePolicy() {
        ToolScopePolicy policy = toolManager.getScopePolicy();
        Map<String, Object> result = new LinkedHashMap<>();
        if (policy == null || !policy.isEnabled()) {
            result.put("enabled", false);
            result.put("message", "Tool-level scope enforcement is disabled (V1.18 compatible behavior)");
            return result;
        }
        result.put("enabled", true);
        result.put("scopeMatch", "exact|*|**");
        result.put("insufficientScopeHttpStatus", 403);
        result.put("insufficientScopeJsonRpcCode", -32090);

        List<ToolDefinition> tools = registry.listAll().collectList().block();
        Map<String, Object> perTool = new LinkedHashMap<>();
        if (tools != null) {
            for (ToolDefinition def : tools) {
                Set<String> required = policy.resolveRequiredScopes(def);
                if (!required.isEmpty()) {
                    perTool.put(def.getName(), required);
                }
            }
        }
        result.put("tools", perTool);
        return result;
    }

    // ===== 健康检查 =====

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "version", "1.0.0",
                "toolCount", registry.count(),
                "activeSessions", sessions.size(),
                "uptime", System.currentTimeMillis()
        );
    }

    // ===== 连接状态 =====

    @GetMapping("/sessions")
    public Map<String, Object> listSessions() {
        List<Map<String, Object>> sessionList = new ArrayList<>();
        for (ClientSession s : sessions.values()) {
            sessionList.add(Map.of(
                    "sessionId", s.sessionId(),
                    "clientName", s.clientName(),
                    "createdAt", s.createdAt(),
                    "age", System.currentTimeMillis() - s.createdAt()
            ));
        }
        return Map.of("success", true, "sessions", sessionList, "total", sessionList.size());
    }

    // ===== 统计 =====

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        int recentAuditCount = securityManager.getAuditLog(1000).size();
        return Map.of(
                "tools", toolManager.getStats(),
                "sessions", Map.of("active", sessions.size()),
                "audit", Map.of("recentEntries", recentAuditCount)
        );
    }

    // ===== V0.15: MCP 2026-07-28 能力发现端点 =====

    /**
     * GET /api/mcp/discover — Server 级能力发现
     * MCP 2026-07-28 规范要求 Server 提供能力发现端点。
     * 返回完整的 Server 能力清单，供 MCP Marketplace / 网关 / AI 客户端自动发现。
     */
    @GetMapping("/discover")
    public Map<String, Object> discover() {
        Map<String, Object> discovery = new LinkedHashMap<>();

        discovery.put("protocolVersion", McpStatelessEndpoint.MCP_2026_PROTOCOL_VERSION);
        discovery.put("supportedProtocolVersions", List.of(
                McpStatelessEndpoint.MCP_2026_PROTOCOL_VERSION,
                McpStatelessEndpoint.MCP_2025_PROTOCOL_VERSION
        ));

        discovery.put("server", Map.of(
                "name", "spring-ai-mcp-enterprise",
                "version", "1.0.0",
                "vendor", "HH-SpringAI-Agent-Starter",
                "homepage", "https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise",
                "language", "Java 17",
                "framework", "Spring Boot 3.4",
                "mcpSpec", "2026-07-28"
        ));

        discovery.put("endpoints", Map.of(
                "rest", "/api/mcp",
                "tools", "/api/mcp/tools",
                "invoke", "/api/mcp/tools/{name}/invoke",
                "health", "/api/mcp/health",
                "stats", "/api/mcp/stats",
                "discover", "/api/mcp/discover"
        ));

        discovery.put("capabilities", Map.of(
                "tools", Map.of(
                        "total", registry.count(),
                        "listChanged", true,
                        "supportsPagination", true,
                        "supportsDiscover", true
                ),
                "tasks", Map.of("supported", true, "maxTimeoutMs", 300_000),
                "extensions", Map.of("supported", true, "namespaces", List.of("mcp-enterprise", "custom"))
        ));

        discovery.put("security", Map.of(
                "auth", List.of("api-key", "oauth2", "oidc"),
                "rateLimit", true, "auditLog", true, "rbac", true
        ));

        discovery.put("infrastructure", Map.of(
                "caching", Map.of("supportsETag", true, "supportsCacheControl", true),
                "tracing", Map.of("supportsTraceContext", true, "standard", "W3C Trace Context"),
                "schema", Map.of("jsonSchemaVersion", "2020-12")
        ));

        int toolCount = registry.count();
        List<ToolDefinition> tools = registry.listAll().collectList().block();
        discovery.put("status", Map.of(
                "health", "UP", "toolCount", toolCount, "activeSessions", sessions.size(),
                "activeTools", tools != null ? tools.stream().filter(ToolDefinition::isEnabled).count() : 0
        ));

        if (tools != null) {
            List<Map<String, Object>> toolSummary = new ArrayList<>();
            for (ToolDefinition t : tools) {
                toolSummary.add(Map.of(
                        "name", t.getName(), "displayName", t.getDisplayName(),
                        "description", t.getDescription(), "category", t.getCategory(),
                        "enabled", t.isEnabled()
                ));
            }
            discovery.put("tools", toolSummary);
        }

        return discovery;
    }

    public record ClientSession(String sessionId, String clientName, String apiKey, long createdAt) {}
}
