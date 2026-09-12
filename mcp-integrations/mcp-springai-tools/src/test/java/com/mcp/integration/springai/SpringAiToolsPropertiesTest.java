package com.mcp.integration.springai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spring AI 工具桥接配置项测试（V1.23）
 */
class SpringAiToolsPropertiesTest {

    @Test
    void defaultsAreSafeForEnterpriseUse() {
        SpringAiToolsProperties props = new SpringAiToolsProperties();

        assertTrue(props.isEnabled());
        assertTrue(props.isScanToolAnnotatedBeans());
        assertTrue(props.isIncludeToolCallbackProviders());
        assertEquals("", props.getNamePrefix());
        assertEquals("springai", props.getCategory());
        assertEquals("1.0", props.getVersion());
        assertEquals("admin,user", props.getDefaultRoles());
        assertEquals(30000, props.getTimeoutMs());
        assertEquals(10, props.getRateLimitPerSecond());
    }

    @Test
    void applyPrefixKeepsNameWhenPrefixBlank() {
        SpringAiToolsProperties props = new SpringAiToolsProperties();
        assertEquals("getWeather", props.applyPrefix("getWeather"));
    }

    @Test
    void applyPrefixPrependsWhenConfigured() {
        SpringAiToolsProperties props = new SpringAiToolsProperties();
        props.setNamePrefix("sa_");
        assertEquals("sa_getWeather", props.applyPrefix("getWeather"));
    }
}