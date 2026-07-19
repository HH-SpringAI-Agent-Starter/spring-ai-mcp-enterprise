package com.mcp.integration.alibaba;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * McpAlibabaProperties 配置属性测试
 */
class McpAlibabaPropertiesTest {

    @Test
    void defaultValues() {
        McpAlibabaProperties props = new McpAlibabaProperties();
        assertTrue(props.isEnabled(), "默认启用");
        assertEquals("qwen-max", props.getChatModel());
        assertEquals("text-embedding-v3", props.getEmbeddingModel());
        assertEquals(8081, props.getServerPort());
        assertTrue(props.isAutoDetectCloud());
        assertTrue(props.isMcpClientAutoConnect());
        assertEquals(10, props.getConnectTimeout());
        assertEquals(60, props.getReadTimeout());
    }

    @Test
    void setters() {
        McpAlibabaProperties props = new McpAlibabaProperties();
        props.setEnabled(false);
        props.setChatModel("qwen-plus");
        props.setEmbeddingModel("text-embedding-v4");
        props.setServerPort(9090);
        props.setAutoDetectCloud(false);
        props.setMcpClientAutoConnect(false);
        props.setConnectTimeout(30);
        props.setReadTimeout(120);

        assertFalse(props.isEnabled());
        assertEquals("qwen-plus", props.getChatModel());
        assertEquals("text-embedding-v4", props.getEmbeddingModel());
        assertEquals(9090, props.getServerPort());
        assertFalse(props.isAutoDetectCloud());
        assertFalse(props.isMcpClientAutoConnect());
        assertEquals(30, props.getConnectTimeout());
        assertEquals(120, props.getReadTimeout());
    }

    @Test
    void disabledIntegration() {
        McpAlibabaProperties props = new McpAlibabaProperties();
        props.setEnabled(false);
        assertFalse(props.isEnabled());
    }
}
