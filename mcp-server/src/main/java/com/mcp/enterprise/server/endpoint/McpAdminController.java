package com.mcp.enterprise.server.endpoint;

import com.mcp.enterprise.core.endpoint.McpAdminEndpoint;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * MCP 管理控制台 REST API
 *
 * 供运维人员管理 MCP Server。
 * 核心逻辑委托给 McpAdminEndpoint 核心类。
 */
@RestController
@RequestMapping("/api/admin")
public class McpAdminController {

    private final McpAdminEndpoint adminEndpoint;

    public McpAdminController(McpAdminEndpoint adminEndpoint) {
        this.adminEndpoint = adminEndpoint;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        return adminEndpoint.dashboard();
    }

    @PostMapping("/api-keys")
    public Map<String, Object> createApiKey(@RequestParam String owner,
                                            @RequestParam(defaultValue = "user") String roles) {
        return adminEndpoint.createApiKey(owner, roles);
    }

    @DeleteMapping("/api-keys/{key}")
    public Map<String, Object> revokeApiKey(@PathVariable String key) {
        return adminEndpoint.revokeApiKey(key);
    }

    @GetMapping("/tools")
    public Map<String, Object> listTools() {
        return adminEndpoint.listTools();
    }

    @PostMapping("/tools/{name}/toggle")
    public Map<String, Object> toggleTool(@PathVariable String name,
                                          @RequestParam boolean enabled) {
        return adminEndpoint.toggleTool(name, enabled);
    }

    @GetMapping("/audit-log")
    public Map<String, Object> getAuditLog(@RequestParam(defaultValue = "100") int limit) {
        return adminEndpoint.getAuditLog(limit);
    }

    @GetMapping("/health")
    public Map<String, Object> healthCheck() {
        return adminEndpoint.healthCheck();
    }

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        return adminEndpoint.getStats();
    }
}
