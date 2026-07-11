package com.mcp.enterprise.core;

import com.mcp.enterprise.core.model.ToolDefinition;
import com.mcp.enterprise.core.registry.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolRegistry 单元测试
 */
class ToolRegistryTest {

    private ToolRegistry registry;
    private ToolDefinition testTool;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        testTool = new ToolDefinition();
        testTool.setName("test_tool");
        testTool.setDisplayName("测试工具");
        testTool.setDescription("用于单元测试的工具");
        testTool.setCategory("test");
        testTool.setVersion("1.0.0");
        testTool.setEnabled(true);

        registry.register("test_tool", testTool, Map.of("key", "value"));
    }

    @Test
    void testRegisterAndGet() {
        assertTrue(registry.isRegistered("test_tool"));
        assertEquals("测试工具", registry.getDefinition("test_tool").getDisplayName());
        assertEquals(1, registry.count());
    }

    @Test
    void testUnregister() {
        registry.unregister("test_tool");
        assertFalse(registry.isRegistered("test_tool"));
        assertEquals(0, registry.count());
    }

    @Test
    void testListAll() {
        ToolDefinition tool2 = new ToolDefinition();
        tool2.setName("tool2");
        tool2.setCategory("database");
        registry.register("tool2", tool2, new Object());

        StepVerifier.create(registry.listAll()
                        .map(ToolDefinition::getName)
                        .collectList())
                .expectNextMatches(names -> names.containsAll(List.of("test_tool", "tool2")) && names.size() == 2)
                .verifyComplete();
    }

    @Test
    void testListByCategory() {
        ToolDefinition tool2 = new ToolDefinition();
        tool2.setName("tool2");
        tool2.setCategory("database");
        registry.register("tool2", tool2, new Object());

        StepVerifier.create(registry.listByCategory("database")
                        .map(ToolDefinition::getName))
                .expectNext("tool2")
                .verifyComplete();

        StepVerifier.create(registry.listByCategory("test")
                        .map(ToolDefinition::getName))
                .expectNext("test_tool")
                .verifyComplete();

        StepVerifier.create(registry.listByCategory("nonexistent"))
                .verifyComplete();
    }

    @Test
    void testCheckHealth() {
        StepVerifier.create(registry.checkHealth("test_tool"))
                .expectNext(true)
                .verifyComplete();

        StepVerifier.create(registry.checkHealth("nonexistent"))
                .expectNext(false)
                .verifyComplete();

        // 禁用工具
        testTool.setEnabled(false);
        StepVerifier.create(registry.checkHealth("test_tool"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void testCountEmptyRegistry() {
        registry.unregister("test_tool");
        assertEquals(0, registry.count());
    }

    @Test
    void testGetInstance() {
        Map<String, String> instance = Map.of("key", "value");
        registry.register("instance_test", testTool, instance);
        assertSame(instance, registry.getInstance("instance_test"));
    }
}
