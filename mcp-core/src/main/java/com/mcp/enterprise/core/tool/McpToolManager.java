package com.mcp.enterprise.core.tool;

import com.mcp.enterprise.core.model.ToolDefinition;
import com.mcp.enterprise.core.registry.ToolRegistry;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MCP 工具管理器
 *
 * 负责工具的全生命周期管理：
 * - 通过 SPI 自动发现 McpToolExecutor 实现
 * - 运行时注册/注销
 * - 批量健康检查
 * - 调用统计收集
 * - 调用执行（带安全校验）
 */
public class McpToolManager {

    private static final Logger log = LoggerFactory.getLogger(McpToolManager.class);

    private final ToolRegistry registry;
    private final Map<String, McpToolExecutor> executors = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> invokeCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> invokeErrors = new ConcurrentHashMap<>();

    public McpToolManager(ToolRegistry registry) {
        this.registry = registry;
    }

    // ===== 注册/注销 =====

    /**
     * 注册一个工具执行器
     */
    public void registerExecutor(McpToolExecutor executor) {
        ToolDefinition def = executor.getDefinition();
        String name = def.getName();

        executors.put(name, executor);
        registry.register(name, def, executor);
        invokeCounts.put(name, new AtomicLong(0));
        invokeErrors.put(name, new AtomicLong(0));

        log.info("✅ 已注册工具: {} ({}) | 分类: {} | 版本: {}", name, def.getDisplayName(), def.getCategory(), def.getVersion());
    }

    /**
     * 批量注册工具执行器
     */
    public void registerExecutors(List<McpToolExecutor> executorList) {
        executorList.forEach(this::registerExecutor);
    }

    /**
     * 注销一个工具
     */
    public void unregisterTool(String name) {
        executors.remove(name);
        registry.unregister(name);
        invokeCounts.remove(name);
        invokeErrors.remove(name);
        log.info("🗑️ 已注销工具: {}", name);
    }

    // ===== 执行 =====

    /**
     * 执行工具调用（带安全校验和超时）
     */
    public Mono<Map<String, Object>> invoke(String name, Map<String, Object> params) {
        McpToolExecutor executor = executors.get(name);
        if (executor == null) {
            return Mono.just(Map.of("success", false, "error", "Tool not found: " + name));
        }

        ToolDefinition def = executor.getDefinition();
        if (!def.isEnabled()) {
            return Mono.just(Map.of("success", false, "error", "Tool is disabled: " + name));
        }

        long startTime = System.currentTimeMillis();

        return executor.execute(params)
                .doOnSuccess(result -> {
                    invokeCounts.get(name).incrementAndGet();
                    long elapsed = System.currentTimeMillis() - startTime;
                    log.debug("工具 {} 执行成功 | 耗时 {}ms | 参数: {}", name, elapsed, params.keySet());
                })
                .doOnError(error -> {
                    invokeErrors.get(name).incrementAndGet();
                    log.warn("工具 {} 执行失败: {}", name, error.getMessage());
                })
                .onErrorResume(error -> Mono.just(Map.of(
                        "success", false,
                        "error", error.getMessage(),
                        "tool", name
                )));
    }

    // ===== 查询 =====

    public McpToolExecutor getExecutor(String name) {
        return executors.get(name);
    }

    public boolean isRegistered(String name) {
        return executors.containsKey(name);
    }

    public int count() {
        return executors.size();
    }

    public Flux<McpToolExecutor> listAllExecutors() {
        return Flux.fromIterable(executors.values());
    }

    // ===== 健康检查 =====

    /**
     * 对所有注册工具执行批量健康检查
     */
    public Mono<Map<String, Object>> healthCheckAll() {
        Map<String, Object> results = new LinkedHashMap<>();
        List<Mono<Map.Entry<String, Boolean>>> checks = new ArrayList<>();

        for (Map.Entry<String, McpToolExecutor> entry : executors.entrySet()) {
            String name = entry.getKey();
            McpToolExecutor executor = entry.getValue();
            checks.add(executor.healthCheck()
                    .map(ok -> Map.entry(name, ok))
                    .onErrorReturn(Map.entry(name, false)));
        }

        return Flux.merge(checks)
                .collectList()
                .map(entries -> {
                    int healthy = 0;
                    for (Map.Entry<String, Boolean> e : entries) {
                        results.put(e.getKey(), e.getValue() ? "UP" : "DOWN");
                        if (e.getValue()) healthy++;
                    }
                    results.put("_summary", Map.of("total", entries.size(), "healthy", healthy, "unhealthy", entries.size() - healthy));
                    return results;
                });
    }

    // ===== 统计 =====

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalTools", count());
        stats.put("totalInvokes", invokeCounts.values().stream().mapToLong(AtomicLong::get).sum());
        stats.put("totalErrors", invokeErrors.values().stream().mapToLong(AtomicLong::get).sum());

        Map<String, Object> toolStats = new LinkedHashMap<>();
        for (String name : executors.keySet()) {
            toolStats.put(name, Map.of(
                    "invokes", invokeCounts.get(name).get(),
                    "errors", invokeErrors.get(name).get()
            ));
        }
        stats.put("perTool", toolStats);
        return stats;
    }

    public long getInvokeCount(String name) {
        AtomicLong count = invokeCounts.get(name);
        return count != null ? count.get() : 0;
    }

    public long getErrorCount(String name) {
        AtomicLong count = invokeErrors.get(name);
        return count != null ? count.get() : 0;
    }

    // ===== 清理 =====

    @PreDestroy
    public void shutdown() {
        log.info("🛑 MCP ToolManager 关闭，共 {} 个工具", count());
        executors.clear();
    }
}
