package com.mcp.integration.alibaba;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * McpAlibabaIntegration 核心集成测试
 */
@ExtendWith(MockitoExtension.class)
class McpAlibabaIntegrationTest {

    @Mock
    private RestTemplate restTemplate;

    private McpAlibabaProperties properties;
    private McpAlibabaIntegration integration;

    @BeforeEach
    void setUp() {
        properties = new McpAlibabaProperties();
        properties.setServerPort(8081);
        integration = new McpAlibabaIntegration(properties, restTemplate);
    }

    @Test
    void initialNotConnected() {
        assertFalse(integration.isConnected());
        assertEquals(0, integration.getToolCount());
    }

    @Test
    void disconnectWhenNotConnected() {
        integration.disconnect();
        assertFalse(integration.isConnected());
    }

    @Test
    void discoverToolsWhenNotConnected() {
        var tools = integration.discoverTools();
        assertTrue(tools.isEmpty());
    }

    @Test
    void invokeToolWhenUnknown() {
        var result = integration.invokeTool("nonexistent", java.util.Map.of());
        assertFalse((Boolean) result.get("success"));
        assertEquals("未知工具: nonexistent", result.get("error"));
    }

    @Test
    void invokeToolWhenNotConnected() {
        // 模拟工具定义在缓存中但未连接
        integration.getToolDefinitionsForSpringAi(); // should be empty
        var result = integration.invokeTool("system-info", java.util.Map.of("category", "all"));
        // 未连接且连接失败时返回错误
        assertFalse((Boolean) result.get("success"));
    }

    @Test
    void getToolDefinitionsEmptyWhenNoTools() {
        var defs = integration.getToolDefinitionsForSpringAi();
        assertTrue(defs.isEmpty());
    }

    @Test
    void getProperties() {
        assertEquals(properties, integration.getProperties());
        assertEquals("qwen-max", integration.getProperties().getChatModel());
    }

    @Test
    void retryCountStartsAtZero() {
        assertEquals(0, integration.getRetryCount());
    }

    @Test
    void reconnectDisconnectsFirst() {
        integration.reconnect();
        assertFalse(integration.isConnected());
    }

    @Test
    void disconnectClearsTools() {
        integration.disconnect();
        assertEquals(0, integration.getToolCount());
        assertTrue(integration.getDiscoveredTools().isEmpty());
    }
}
