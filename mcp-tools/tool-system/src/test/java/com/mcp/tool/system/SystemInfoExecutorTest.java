package com.mcp.tool.system;

import com.mcp.enterprise.core.model.ToolDefinition;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SystemInfoExecutor 单元测试
 */
class SystemInfoExecutorTest {

    private final SystemInfoExecutor executor = new SystemInfoExecutor();

    @Test
    void testDefinition() {
        ToolDefinition def = executor.getDefinition();
        assertEquals("system_info", def.getName());
        assertEquals("system", def.getCategory());
        assertEquals("admin", def.getRequiredRoles());
        assertNotNull(def.getInputSchema());
    }

    @Test
    void testBasicInfo() {
        Map<String, Object> result = executor.execute(Map.of("type", "basic")).block();

        assertNotNull(result);
        assertEquals(true, result.get("success"));
        assertNotNull(result.get("jvm"));
        assertNotNull(result.get("memory"));
        assertNotNull(result.get("os"));
        assertTrue(result.containsKey("elapsedMs"));
    }

    @Test
    void testMemoryInfo() {
        Map<String, Object> result = executor.execute(Map.of("type", "memory")).block();

        assertNotNull(result);
        assertEquals(true, result.get("success"));
        assertNotNull(result.get("memory"));

        Map<String, Object> memory = (Map<String, Object>) result.get("memory");
        assertNotNull(memory.get("heap"));
        assertTrue(((String) ((Map<String, Object>) memory.get("heap")).get("used")).endsWith("MB")
                || ((String) ((Map<String, Object>) memory.get("heap")).get("used")).endsWith("GB")
                || ((String) ((Map<String, Object>) memory.get("heap")).get("used")).endsWith("KB"));
    }

    @Test
    void testGcInfo() {
        Map<String, Object> result = executor.execute(Map.of("type", "gc")).block();

        assertNotNull(result);
        assertEquals(true, result.get("success"));
        assertNotNull(result.get("gc"));

        Map<String, Object> gc = (Map<String, Object>) result.get("gc");
        assertNotNull(gc.get("collectors"));
    }

    @Test
    void testAllInfo() {
        Map<String, Object> result = executor.execute(Map.of("type", "all")).block();

        assertNotNull(result);
        assertEquals(true, result.get("success"));
        assertNotNull(result.get("jvm"));
        assertNotNull(result.get("memory"));
        assertNotNull(result.get("threads"));
        assertNotNull(result.get("gc"));
        assertNotNull(result.get("os"));
    }

    @Test
    void testDefaultType() {
        // 不传 type 应该返回 basic
        Map<String, Object> result = executor.execute(Map.of()).block();
        assertNotNull(result);
        assertEquals(true, result.get("success"));
        assertNotNull(result.get("jvm"));
    }

    @Test
    void testJvmInfo() {
        Map<String, Object> result = executor.execute(Map.of("type", "basic")).block();

        Map<String, Object> jvm = (Map<String, Object>) result.get("jvm");
        assertNotNull(jvm.get("name"));
        assertNotNull(jvm.get("version"));
        assertNotNull(jvm.get("uptime"));
        assertNotNull(jvm.get("startTime"));
    }

    @Test
    void testOsInfo() {
        Map<String, Object> result = executor.execute(Map.of("type", "basic")).block();

        Map<String, Object> os = (Map<String, Object>) result.get("os");
        assertNotNull(os.get("name"));
        assertNotNull(os.get("arch"));
        assertNotNull(os.get("version"));
        assertTrue((Integer) os.get("availableProcessors") > 0);
    }

    @Test
    void testAdminOnly() {
        ToolDefinition def = executor.getDefinition();
        assertEquals("admin", def.getRequiredRoles());
    }

    @Test
    void testElapsedMs() {
        Map<String, Object> result = executor.execute(Map.of("type", "basic")).block();
        assertNotNull(result);
        assertTrue((Long) result.get("elapsedMs") >= 0);
    }

    @Test
    void testMemoryPoolInfo() {
        Map<String, Object> result = executor.execute(Map.of("type", "memory")).block();
        Map<String, Object> memory = (Map<String, Object>) result.get("memory");
        assertNotNull(memory.get("pools"));
    }

    @Test
    void testThreadInfo() {
        Map<String, Object> result = executor.execute(Map.of("type", "all")).block();
        Map<String, Object> threads = (Map<String, Object>) result.get("threads");
        assertTrue((Integer) threads.get("activeThreads") > 0);
        assertTrue((Long) threads.get("totalStarted") > 0);
    }
}
