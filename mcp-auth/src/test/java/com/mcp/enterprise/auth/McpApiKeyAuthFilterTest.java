package com.mcp.enterprise.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * McpApiKeyAuthFilter 单元测试
 */
class McpApiKeyAuthFilterTest {

    private McpAuthProperties properties;
    private McpApiKeyAuthFilter filter;

    @BeforeEach
    void setUp() {
        properties = new McpAuthProperties();
        properties.setJwtSecret("test-secret-for-auth-filter-at-least-32-chars!");
        filter = new McpApiKeyAuthFilter(properties);
    }

    @Test
    void shouldConstructWithProperties() {
        assertNotNull(filter);
    }

    @Test
    void shouldAcceptJwtTokenProvider() {
        McpJwtTokenProvider provider = new McpJwtTokenProvider(properties);
        filter.setJwtTokenProvider(provider);
        // no exception expected
        assertNotNull(filter);
    }
}
