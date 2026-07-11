package com.mcp.enterprise.autoconfigure;

import com.mcp.enterprise.core.registry.ToolRegistry;
import com.mcp.enterprise.core.security.McpSecurityManager;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;

import java.util.*;

/**
 * MCP Enterprise Actuator Endpoint
 * GET  /actuator/mcp-enterprise          - 概况
 * GET  /actuator/mcp-enterprise/tools    - 工具列表
 * GET  /actuator/mcp-enterprise/security - 安全状态
 * GET  /actuator/mcp-enterprise/audit    - 审计日志
 */
@Endpoint(id = "mcp-enterprise")
public class McpEnterpriseEndpoint {

    private final ToolRegistry registry;
    private final McpSecurityManager securityManager;

    public McpEnterpriseEndpoint(ToolRegistry registry, McpSecurityManager securityManager) {
        this.registry = registry;
        this.securityManager = securityManager;
    }

    @ReadOperation
    public Map<String, Object> overview() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("toolCount", registry.count());
        result.put("toolsByCategory", buildCategorySummary());
        result.put("health", "UP");
        return result;
    }

    @ReadOperation
    public List<?> tools() {
        return registry.listAll().collectList().block();
    }

    @ReadOperation
    public Map<String, Object> security() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "enabled");
        return result;
    }

    private Map<String, Long> buildCategorySummary() {
        Map<String, Long> summary = new LinkedHashMap<>();
        registry.listAll().toStream()
                .forEach(t -> {
                    String cat = t.getCategory() != null ? t.getCategory() : "uncategorized";
                    summary.merge(cat, 1L, Long::sum);
                });
        return summary;
    }
}
