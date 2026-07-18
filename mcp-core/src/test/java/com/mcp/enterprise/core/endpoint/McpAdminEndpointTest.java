package com.mcp.enterprise.core.endpoint;

import com.mcp.enterprise.core.model.ToolDefinition;
import com.mcp.enterprise.core.registry.ToolRegistry;
import com.mcp.enterprise.core.security.McpSecurityManager;
import com.mcp.enterprise.core.tool.McpToolExecutor;
import com.mcp.enterprise.core.tool.McpToolManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * McpAdminEndpoint 单元测试
 */
class McpAdminEndpointTest {

    private ToolRegistry registry;
    private McpSecurityManager securityManager;
    private McpToolManager toolManager;
    private McpAdminEndpoint endpoint;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        securityManager = new McpSecurityManager();
        toolManager = new McpToolManager(registry);
        endpoint = new McpAdminEndpoint(registry, securityManager, toolManager);

        // 注册测试工具
        McpToolExecutor testTool = new McpToolExecutor() {
            @Override
            public ToolDefinition getDefinition() {
                return new ToolDefinition("adminTest", "AdminTest", "管理测试工具", "test",
                        "1.0.0", null, true, "admin", 5000, 10,
                        Map.of("type", "object", "properties", Map.of()), null);
            }

            @Override
            public Mono<Map<String, Object>> execute(Map<String, Object> params) {
                return Mono.just(Map.of("success", true, "result", "ok"));
            }
        };
        toolManager.registerExecutor(testTool);
    }

    @Test
    void dashboardShouldReturnStatus() {
        Map<String, Object> dash = endpoint.dashboard();
        assertEquals("UP", dash.get("status"));
        assertEquals(1, dash.get("toolCount"));
        assertTrue(dash.containsKey("timestamp"));
    }

    @Test
    void listToolsShouldReturnRegisteredTools() {
        Map<String, Object> result = endpoint.listTools();
        assertTrue((Boolean) result.get("success"));
        assertEquals(1, result.get("total"));
    }

    @Test
    void healthCheckShouldReturnAllToolStatus() {
        Map<String, Object> health = endpoint.healthCheck();
        assertEquals("UP", health.get("server"));
        assertTrue(health.containsKey("tools"));
    }

    @Test
    void toggleToolShouldEnableAndDisable() {
        // 禁用
        Map<String, Object> disableResult = endpoint.toggleTool("adminTest", false);
        assertTrue((Boolean) disableResult.get("success"));
        assertEquals(false, disableResult.get("enabled"));

        // 验证 registry 层已禁用
        ToolDefinition def = registry.getDefinition("adminTest");
        assertFalse(def.isEnabled());

        // 重新启用
        Map<String, Object> enableResult = endpoint.toggleTool("adminTest", true);
        assertTrue((Boolean) enableResult.get("success"));
        assertEquals(true, enableResult.get("enabled"));

        // 验证 registry 层已启用
        def = registry.getDefinition("adminTest");
        assertTrue(def.isEnabled());
    }

    @Test
    void toggleNonExistentToolShouldReturnError() {
        Map<String, Object> result = endpoint.toggleTool("notexist", false);
        assertFalse((Boolean) result.get("success"));
    }

    @Test
    void createApiKeyShouldReturnMaskedKey() {
        Map<String, Object> result = endpoint.createApiKey("testUser", "admin,user");
        assertTrue((Boolean) result.get("success"));
        String masked = (String) result.get("apiKey");
        assertNotNull(masked);
        assertTrue(masked.contains("..."));
    }

    @Test
    void revokeApiKeyShouldSucceed() {
        Map<String, Object> result = endpoint.revokeApiKey("test-key-123");
        assertTrue((Boolean) result.get("success"));
    }

    @Test
    void getAuditLogShouldReturnEntries() {
        Map<String, Object> result = endpoint.getAuditLog(50);
        assertTrue((Boolean) result.get("success"));
        assertTrue(result.containsKey("entries"));
    }

    @Test
    void getStatsShouldReturnToolStats() {
        // 先调用一次以产生统计
        toolManager.invoke("adminTest", Map.of()).block();

        Map<String, Object> stats = endpoint.getStats();
        assertNotNull(stats.get("tools"));
        assertNotNull(stats.get("audit"));
    }
}
