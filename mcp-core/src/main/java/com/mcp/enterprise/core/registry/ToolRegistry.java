package com.mcp.enterprise.core.registry;

import com.mcp.enterprise.core.model.ToolDefinition;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 企业级 MCP 工具注册中心
 *
 * 核心职责：
 * 1. 工具注册/注销
 * 2. 动态发现（通过 spring.factories 或 SPI）
 * 3. 健康检查
 * 4. 运行时热加载
 */
public class ToolRegistry {

    private final Map<String, ToolDefinition> tools = new ConcurrentHashMap<>();
    private final Map<String, Object> toolInstances = new ConcurrentHashMap<>();

    public void register(String name, ToolDefinition definition, Object instance) {
        tools.put(name, definition);
        toolInstances.put(name, instance);
    }

    public void unregister(String name) {
        tools.remove(name);
        toolInstances.remove(name);
    }

    public ToolDefinition getDefinition(String name) {
        return tools.get(name);
    }

    public Object getInstance(String name) {
        return toolInstances.get(name);
    }

    public Flux<ToolDefinition> listAll() {
        return Flux.fromIterable(tools.values());
    }

    public Flux<ToolDefinition> listByCategory(String category) {
        return Flux.fromIterable(tools.values())
                .filter(t -> category.equals(t.getCategory()));
    }

    public boolean isRegistered(String name) {
        return tools.containsKey(name);
    }

    public int count() {
        return tools.size();
    }

    /**
     * 检查工具是否可用
     */
    public Mono<Boolean> checkHealth(String name) {
        ToolDefinition def = tools.get(name);
        if (def == null) return Mono.just(false);
        return Mono.just(def.isEnabled());
    }
}
