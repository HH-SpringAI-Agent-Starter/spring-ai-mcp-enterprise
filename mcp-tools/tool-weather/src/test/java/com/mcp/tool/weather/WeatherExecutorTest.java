package com.mcp.tool.weather;

import com.mcp.enterprise.core.model.ToolDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WeatherExecutorTest {

    private WeatherExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new WeatherExecutor();
    }

    @Test
    void getDefinitionShouldReturnValidToolDefinition() {
        ToolDefinition def = executor.getDefinition();
        assertNotNull(def);
        assertEquals("weather", def.getName());
        assertEquals("demo", def.getCategory());
        assertNotNull(def.getInputSchema());
    }

    @Test
    void getDefinitionShouldIncludeLocationProperty() {
        ToolDefinition def = executor.getDefinition();
        Map<String, Object> schema = def.getInputSchema();
        assertNotNull(schema);

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertNotNull(properties);
        assertTrue(properties.containsKey("location"));
    }
}
