package com.mcp.enterprise.server;

import com.mcp.enterprise.core.model.ToolDefinition;
import com.mcp.enterprise.core.registry.ToolRegistry;
import com.mcp.enterprise.core.security.McpSecurityManager;
import com.mcp.enterprise.core.tool.McpToolManager;
import org.springframework.web.bind.annotation.*;

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
                "serverVersion", "0.0.2",
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
                                          @RequestHeader(value = "X-API-Key", required = false) String apiKey) {
        // 安全审计
        securityManager.audit(apiKey, name, "invoke", true, "params=" + params.keySet());

        // 通过 toolManager 执行
        return toolManager.invoke(name, params).block();
    }

    // ===== 健康检查 =====

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "version", "0.0.2",
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

    public record ClientSession(String sessionId, String clientName, String apiKey, long createdAt) {}
}
