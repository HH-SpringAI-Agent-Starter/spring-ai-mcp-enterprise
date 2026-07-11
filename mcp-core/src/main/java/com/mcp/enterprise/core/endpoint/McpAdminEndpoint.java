package com.mcp.enterprise.core.endpoint;

import com.mcp.enterprise.core.model.ToolDefinition;
import com.mcp.enterprise.core.registry.ToolRegistry;
import com.mcp.enterprise.core.security.McpSecurityManager;
import com.mcp.enterprise.core.tool.McpToolManager;

import java.util.*;
import java.util.stream.Collectors;

/**
 * MCP 管理核心处理器
 *
 * 提供 MCP Server 的管理功能：
 * - API Key 管理
 * - 工具管理
 * - 审计日志查询
 * - 系统状态
 *
 * 此为核心类，不含 @RestController 注解。
 * Web 控制器在 mcp-server 模块中实现。
 */
public class McpAdminEndpoint {

    private final ToolRegistry registry;
    private final McpSecurityManager securityManager;
    private final McpToolManager toolManager;

    public McpAdminEndpoint(ToolRegistry registry,
                            McpSecurityManager securityManager,
                            McpToolManager toolManager) {
        this.registry = registry;
        this.securityManager = securityManager;
        this.toolManager = toolManager;
    }

    // ===== Dashboard =====

    public Map<String, Object> dashboard() {
        return Map.of(
                "status", "UP",
                "version", "0.0.2",
                "toolCount", registry.count(),
                "totalInvokes", toolManager.getStats().get("totalInvokes"),
                "timestamp", System.currentTimeMillis()
        );
    }

    // ===== API Key 管理 =====

    public Map<String, Object> createApiKey(String owner, String roles) {
        Set<String> roleSet = Arrays.stream(roles.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        String key = securityManager.createApiKey(owner, roleSet);
        return Map.of("success", true, "apiKey", maskKey(key));
    }

    public Map<String, Object> revokeApiKey(String key) {
        securityManager.revokeApiKey(key);
        return Map.of("success", true);
    }

    // ===== 工具管理 =====

    public Map<String, Object> listTools() {
        List<ToolDefinition> tools = registry.listAll().collectList().block();
        if (tools == null) return Map.of("success", true, "tools", List.of(), "total", 0);
        return Map.of("success", true, "tools", tools, "total", tools.size());
    }

    public Map<String, Object> toggleTool(String name, boolean enabled) {
        ToolDefinition def = registry.getDefinition(name);
        if (def == null) return Map.of("success", false, "error", "Tool not found: " + name);
        def.setEnabled(enabled);
        return Map.of("success", true, "name", name, "enabled", enabled);
    }

    // ===== 审计日志 =====

    public Map<String, Object> getAuditLog(int limit) {
        List<McpSecurityManager.AuditLogEntry> entries = securityManager.getAuditLog(limit);
        return Map.of("success", true, "entries", entries, "total", entries.size());
    }

    // ===== 健康检查 =====

    public Map<String, Object> healthCheck() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("server", "UP");
        health.put("toolCount", registry.count());
        health.put("activeExecutors", toolManager.count());

        Map<String, Object> toolHealth = toolManager.healthCheckAll().block();
        health.put("tools", toolHealth);

        return health;
    }

    // ===== 统计 =====

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("tools", toolManager.getStats());
        stats.put("audit", Map.of("recentEntries", securityManager.getAuditLog(100).size()));
        return stats;
    }

    // ===== 工具方法 =====

    private String maskKey(String key) {
        if (key == null || key.length() < 12) return key;
        return key.substring(0, 8) + "..." + key.substring(key.length() - 4);
    }
}
