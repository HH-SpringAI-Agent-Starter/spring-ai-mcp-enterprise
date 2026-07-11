package com.mcp.enterprise.core;

import com.mcp.enterprise.core.model.ToolDefinition;
import com.mcp.enterprise.core.registry.ToolRegistry;
import com.mcp.enterprise.core.tool.McpToolExecutor;
import com.mcp.enterprise.core.tool.McpToolManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * McpToolManager 单元测试
 */
class McpToolManagerTest {

    private ToolRegistry registry;
    private McpToolManager toolManager;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        toolManager = new McpToolManager(registry);
    }

    @Test
    void testRegisterAndInvoke() {
        McpToolExecutor executor = new McpToolExecutor() {
            @Override
            public ToolDefinition getDefinition() {
                ToolDefinition def = new ToolDefinition();
                def.setName("echo");
                def.setDisplayName("回声工具");
                def.setDescription("返回传入的参数");
                def.setCategory("test");
                def.setVersion("1.0.0");
                return def;
            }

            @Override
            public Mono<Map<String, Object>> execute(Map<String, Object> params) {
                return Mono.just(Map.of("success", true, "result", params));
            }
        };

        toolManager.registerExecutor(executor);

        assertTrue(toolManager.isRegistered("echo"));
        assertEquals(1, toolManager.count());
        assertNotNull(toolManager.getExecutor("echo"));

        StepVerifier.create(toolManager.invoke("echo", Map.of("msg", "hello")))
                .expectNextMatches(result ->
                        result.get("success").equals(true) &&
                        ((Map<?, ?>) result.get("result")).get("msg").equals("hello"))
                .verifyComplete();
    }

    @Test
    void testInvokeNotFound() {
        StepVerifier.create(toolManager.invoke("nonexistent", Map.of()))
                .expectNextMatches(result ->
                        result.get("success").equals(false) &&
                        ((String) result.get("error")).contains("not found"))
                .verifyComplete();
    }

    @Test
    void testInvokeDisabledTool() {
        McpToolExecutor executor = new McpToolExecutor() {
            @Override
            public ToolDefinition getDefinition() {
                ToolDefinition def = new ToolDefinition();
                def.setName("disabled_tool");
                def.setDisplayName("已禁用");
                def.setDescription("此工具已被禁用");
                def.setCategory("test");
                def.setVersion("1.0.0");
                def.setEnabled(false);
                return def;
            }

            @Override
            public Mono<Map<String, Object>> execute(Map<String, Object> params) {
                return Mono.just(Map.of("success", true));
            }
        };

        toolManager.registerExecutor(executor);

        StepVerifier.create(toolManager.invoke("disabled_tool", Map.of()))
                .expectNextMatches(result ->
                        result.get("success").equals(false) &&
                        ((String) result.get("error")).contains("disabled"))
                .verifyComplete();
    }

    @Test
    void testInvokeError() {
        McpToolExecutor executor = new McpToolExecutor() {
            @Override
            public ToolDefinition getDefinition() {
                ToolDefinition def = new ToolDefinition();
                def.setName("error_tool");
                def.setDisplayName("错误工具");
                def.setDescription("执行时抛出异常");
                def.setCategory("test");
                def.setVersion("1.0.0");
                return def;
            }

            @Override
            public Mono<Map<String, Object>> execute(Map<String, Object> params) {
                return Mono.error(new RuntimeException("测试异常"));
            }
        };

        toolManager.registerExecutor(executor);

        StepVerifier.create(toolManager.invoke("error_tool", Map.of()))
                .expectNextMatches(result ->
                        result.get("success").equals(false) &&
                        ((String) result.get("error")).contains("测试异常"))
                .verifyComplete();
    }

    @Test
    void testHealthCheckAll() {
        toolManager.registerExecutor(new McpToolExecutor() {
            @Override
            public ToolDefinition getDefinition() {
                ToolDefinition def = new ToolDefinition();
                def.setName("healthy");
                def.setDisplayName("健康工具");
                def.setCategory("test");
                def.setVersion("1.0.0");
                return def;
            }

            @Override
            public Mono<Map<String, Object>> execute(Map<String, Object> params) {
                return Mono.just(Map.of("success", true));
            }
        });

        toolManager.registerExecutor(new McpToolExecutor() {
            @Override
            public ToolDefinition getDefinition() {
                ToolDefinition def = new ToolDefinition();
                def.setName("unhealthy");
                def.setDisplayName("不健康工具");
                def.setCategory("test");
                def.setVersion("1.0.0");
                return def;
            }

            @Override
            public Mono<Map<String, Object>> execute(Map<String, Object> params) {
                return Mono.just(Map.of("success", true));
            }

            @Override
            public Mono<Boolean> healthCheck() {
                return Mono.just(false);
            }
        });

        StepVerifier.create(toolManager.healthCheckAll())
                .expectNextMatches(result -> {
                    Map<?, ?> summary = (Map<?, ?>) result.get("_summary");
                    return summary.get("healthy").equals(1) && summary.get("unhealthy").equals(1);
                })
                .verifyComplete();
    }

    @Test
    void testRegisterExecutors() {
        McpToolExecutor e1 = new McpToolExecutor() {
            @Override
            public ToolDefinition getDefinition() {
                ToolDefinition def = new ToolDefinition();
                def.setName("tool1"); def.setCategory("test"); def.setVersion("1.0.0");
                return def;
            }
            @Override
            public Mono<Map<String, Object>> execute(Map<String, Object> params) {
                return Mono.just(Map.of("success", true));
            }
        };

        McpToolExecutor e2 = new McpToolExecutor() {
            @Override
            public ToolDefinition getDefinition() {
                ToolDefinition def = new ToolDefinition();
                def.setName("tool2"); def.setCategory("test"); def.setVersion("1.0.0");
                return def;
            }
            @Override
            public Mono<Map<String, Object>> execute(Map<String, Object> params) {
                return Mono.just(Map.of("success", true));
            }
        };

        toolManager.registerExecutors(List.of(e1, e2));
        assertEquals(2, toolManager.count());
        assertTrue(toolManager.isRegistered("tool1"));
        assertTrue(toolManager.isRegistered("tool2"));
    }

    @Test
    void testUnregister() {
        McpToolExecutor executor = new McpToolExecutor() {
            @Override
            public ToolDefinition getDefinition() {
                ToolDefinition def = new ToolDefinition();
                def.setName("temp"); def.setCategory("test"); def.setVersion("1.0.0");
                return def;
            }
            @Override
            public Mono<Map<String, Object>> execute(Map<String, Object> params) {
                return Mono.just(Map.of("success", true));
            }
        };

        toolManager.registerExecutor(executor);
        assertEquals(1, toolManager.count());

        toolManager.unregisterTool("temp");
        assertEquals(0, toolManager.count());
        assertFalse(toolManager.isRegistered("temp"));
    }

    @Test
    void testStats() {
        McpToolExecutor executor = new McpToolExecutor() {
            @Override
            public ToolDefinition getDefinition() {
                ToolDefinition def = new ToolDefinition();
                def.setName("counter");
                def.setDisplayName("计数器");
                def.setCategory("test");
                def.setVersion("1.0.0");
                return def;
            }

            @Override
            public Mono<Map<String, Object>> execute(Map<String, Object> params) {
                return Mono.just(Map.of("success", true));
            }
        };

        toolManager.registerExecutor(executor);

        // 执行几次
        toolManager.invoke("counter", Map.of("n", "1")).block();
        toolManager.invoke("counter", Map.of("n", "2")).block();
        toolManager.invoke("counter", Map.of("n", "3")).block();

        Map<String, Object> stats = toolManager.getStats();
        assertEquals(1, stats.get("totalTools"));
        assertEquals(3L, stats.get("totalInvokes"));

        assertEquals(3L, toolManager.getInvokeCount("counter"));
    }
}
