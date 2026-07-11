package com.mcp.tool.search;

import com.mcp.enterprise.core.model.ToolDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WebSearchExecutor 单元测试
 *
 * 测试工具定义和参数校验逻辑，实际的网络搜索用集成测试验证。
 */
class WebSearchExecutorTest {

    private final WebSearchExecutor executor = new WebSearchExecutor();

    @Test
    void testDefinition() {
        ToolDefinition def = executor.getDefinition();
        assertEquals("web_search", def.getName());
        assertEquals("search", def.getCategory());
        assertNotNull(def.getInputSchema());
    }

    @Test
    void testEmptyQuery() {
        Map<String, Object> result = executor.execute(Map.of("query", "")).block();
        assertNotNull(result);
        assertEquals(false, result.get("success"));
        assertNotNull(result.get("error"));
    }

    @Test
    void testToolName() {
        ToolDefinition def = executor.getDefinition();
        assertEquals("web_search", def.getName());
    }

    @Test
    void testRequiredRoles() {
        ToolDefinition def = executor.getDefinition();
        assertTrue(def.getRequiredRoles().contains("user"));
    }

    @Test
    void testSchemaProperties() {
        Map<String, Object> schema = executor.getDefinition().getInputSchema();
        assertNotNull(schema);
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertNotNull(properties);
        assertTrue(properties.containsKey("query"));
        assertTrue(properties.containsKey("maxResults"));
        assertTrue(properties.containsKey("fetchContent"));
        // query 是必须的
        assertTrue(((List<String>) schema.get("required")).contains("query"));
    }

    @Test
    void testDisplayName() {
        ToolDefinition def = executor.getDefinition();
        assertEquals("网络搜索", def.getDisplayName());
    }

    @Test
    void testDescription() {
        ToolDefinition def = executor.getDefinition();
        assertNotNull(def.getDescription());
        assertTrue(def.getDescription().length() > 10);
    }

    @Test
    void testModule() {
        ToolDefinition def = executor.getDefinition();
        assertEquals("tool-search", def.getModule());
    }

    @Test
    void testVersion() {
        ToolDefinition def = executor.getDefinition();
        assertEquals("1.0.0", def.getVersion());
    }

    @Test
    void testTimeout() {
        ToolDefinition def = executor.getDefinition();
        assertEquals(60000, def.getTimeoutMs());
    }
}
