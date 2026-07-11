package com.mcp.enterprise.server;

import com.mcp.enterprise.core.model.ToolDefinition;
import com.mcp.enterprise.core.registry.ToolRegistry;
import com.mcp.enterprise.core.security.McpSecurityManager;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * MCP Enterprise REST API
 *
 * 对外提供企业级 MCP 服务接口
 */
@RestController
@RequestMapping("/api/mcp")
public class McpServerController {

    private final ToolRegistry registry;
    private final McpSecurityManager securityManager;

    // 活跃连接记录
    private final Map<String, ClientSession> sessions = new ConcurrentHashMap<>();

    public McpServerController(ToolRegistry registry, McpSecurityManager securityManager) {
        this.registry = registry;
        this.securityManager = securityManager;
    }

    // ===== 连接管理 =====

    @PostMapping("/connect")
    public Map<String, Object> connect(@RequestHeader("X-API-Key") String apiKey,
                                       @RequestParam(defaultValue = "anonymous") String clientName) {
        boolean valid = securityManager.validateApiKey(apiKey).block();
        if (!valid) return Map.of("success", false, "error", "Invalid API Key");

        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, new ClientSession(sessionId, clientName, apiKey, System.currentTimeMillis()));

        return Map.of(
            "success", true,
            "sessionId", sessionId,
            "serverVersion", "0.0.1",
            "supportedProtocols", List.of("mcp-v1", "streaming-v1")
        );
    }

    @PostMapping("/disconnect")
    public Map<String, Object> disconnect(@RequestParam String sessionId) {
        sessions.remove(sessionId);
        return Map.of("success", true);
    }

    // ===== 工具发现 =====

    @GetMapping("/tools")
    public Map<String, Object> listTools() {
        List<ToolDefinition> toolList = registry.listAll().collectList().block();
        return Map.of(
            "success", true,
            "total", toolList.size(),
            "tools", toolList
        );
    }

    @GetMapping("/tools/{name}")
    public Map<String, Object> getTool(@PathVariable String name) {
        ToolDefinition def = registry.getDefinition(name);
        if (def == null) return Map.of("success", false, "error", "Tool not found");
        return Map.of("success", true, "tool", def);
    }

    // ===== 工具调用 =====

    @PostMapping("/tools/{name}/invoke")
    public Map<String, Object> invokeTool(@PathVariable String name,
                                          @RequestBody Map<String, Object> params,
                                          @RequestHeader(value = "X-API-Key", required = false) String apiKey) {
        ToolDefinition def = registry.getDefinition(name);
        if (def == null) return Map.of("success", false, "error", "Tool not found");
        if (!def.isEnabled()) return Map.of("success", false, "error", "Tool disabled");

        // 安全拦截
        securityManager.audit(apiKey, name, "invoke", true, "params=" + params.keySet());

        // 返回工具定义，由客户端通过 MCP SDK 调用
        return Map.of(
            "success", true,
            "tool", name,
            "status", "invokable",
            "sdkEndpoint", "/api/mcp/sdk/" + name
        );
    }

    // ===== 健康检查 =====

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
            "status", "UP",
            "toolCount", registry.count(),
            "activeSessions", sessions.size(),
            "uptime", System.currentTimeMillis()
        );
    }

    // ===== 统计 =====

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        long recentAuditCount = securityManager.getAuditLog(1000).size();
        return Map.of(
            "tools", Map.of("total", registry.count()),
            "sessions", Map.of("active", sessions.size()),
            "audit", Map.of("recentEntries", recentAuditCount)
        );
    }

    public record ClientSession(String sessionId, String clientName, String apiKey, long createdAt) {}
}
