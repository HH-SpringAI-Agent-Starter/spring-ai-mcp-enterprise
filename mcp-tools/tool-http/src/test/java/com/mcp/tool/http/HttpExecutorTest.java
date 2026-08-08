package com.mcp.tool.http;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HTTP 工具单元测试
 */
class HttpExecutorTest {

    private McpHttpToolProperties properties;
    private HttpExecutor executor;

    @BeforeEach
    void setUp() {
        properties = new McpHttpToolProperties();
        properties.setConnectTimeout(Duration.ofSeconds(3));
        properties.setReadTimeout(Duration.ofSeconds(10));
        executor = new HttpExecutor(properties);
    }

    @Test
    void definition_shouldExposeHttpTool() {
        var def = executor.getDefinition();
        assertNotNull(def);
        assertEquals("http", def.getName());
        assertEquals("admin", def.getRequiredRoles());
    }

    @Test
    void execute_missingUrl_shouldReturnError() {
        var result = executor.execute(Map.of()).block();
        assertNotNull(result);
        assertFalse((Boolean) result.get("success"));
    }

    @Test
    void execute_unsupportedMethod_shouldReturnError() {
        var result = executor.execute(Map.of(
                "url", "http://localhost:8081/health",
                "method", "DELETE"
        )).block();
        assertNotNull(result);
        assertFalse((Boolean) result.get("success"));
        assertTrue(String.valueOf(result.get("error")).contains("仅支持 GET/POST"));
    }

    @Test
    void execute_ssrf_blockedHost_shouldReturnError() {
        var result = executor.execute(Map.of(
                "url", "http://169.254.169.254/latest/meta-data/",
                "method", "GET"
        )).block();
        assertNotNull(result);
        assertFalse((Boolean) result.get("success"));
        assertTrue(String.valueOf(result.get("error")).contains("白名单"));
    }

    @Test
    void isHostAllowed_localhostAlwaysAllowed() {
        assertTrue(executor.isHostAllowed("localhost"));
        assertTrue(executor.isHostAllowed("127.0.0.1"));
    }

    @Test
    void isHostAllowed_emptyWhitelist_deniesExternal() {
        assertFalse(executor.isHostAllowed("api.example.com"));
    }

    @Test
    void isHostAllowed_exactMatch() {
        properties.setAllowedHosts(List.of("api.example.com"));
        assertTrue(executor.isHostAllowed("api.example.com"));
        assertFalse(executor.isHostAllowed("api.example.org"));
    }

    @Test
    void isHostAllowed_wildcardSuffix() {
        properties.setAllowedHosts(List.of("*.internal.corp"));
        assertTrue(executor.isHostAllowed("order.internal.corp"));
        assertTrue(executor.isHostAllowed("erp.internal.corp"));
        assertFalse(executor.isHostAllowed("internal.corp"));
        assertFalse(executor.isHostAllowed("order.external.corp"));
    }
}
