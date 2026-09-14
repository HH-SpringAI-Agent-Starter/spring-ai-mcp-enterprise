package com.mcp.enterprise.gateway.manager;

import com.mcp.enterprise.core.tool.McpToolManager;
import com.mcp.enterprise.gateway.McpGatewayProperties;
import com.mcp.enterprise.gateway.McpUpstreamStatus;
import com.mcp.enterprise.gateway.RemoteToolDescriptor;
import com.mcp.enterprise.gateway.UpstreamMcpClient;
import com.mcp.enterprise.gateway.client.McpUpstreamClientFactory;
import com.mcp.enterprise.gateway.executor.FederatedToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * V1.25 联邦 MCP 网关管理器。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>按配置为每个上游创建客户端（{@link McpUpstreamClientFactory}）；</li>
 *   <li>{@code sync}：拉取上游 {@code tools/list}，把远程工具包装成
 *       {@link FederatedToolExecutor} 注册进 {@link McpToolManager}
 *       （RBAC/限流/审计/Scope 自动继承）；</li>
 *   <li>同步失败时**保留已注册工具**并标记上游 unhealthy（可用性优先）；</li>
 *   <li>上游 disabled / 下线时移除其全部联邦工具；</li>
 *   <li>暴露管理 API 数据：上游状态、聚合健康、手动刷新。</li>
 * </ul>
 */
public class McpFederationManager {

    private static final Logger log = LoggerFactory.getLogger(McpFederationManager.class);

    private final McpGatewayProperties properties;
    private final McpUpstreamClientFactory clientFactory;
    /** 允许为 null：仅用于展示/状态场景（未装配 starter 时降级为只读） */
    private final McpToolManager toolManager;

    private final Map<String, UpstreamEntry> entries = new ConcurrentHashMap<>();

    private static class UpstreamEntry {
        final McpGatewayProperties.Upstream config;
        final UpstreamMcpClient client;
        final McpUpstreamStatus status;
        final Set<String> registered = new ConcurrentSkipListSet<>();

        UpstreamEntry(McpGatewayProperties.Upstream config, UpstreamMcpClient client) {
            this.config = config;
            this.client = client;
            this.status = new McpUpstreamStatus(config);
        }
    }

    public McpFederationManager(McpGatewayProperties properties,
                                McpUpstreamClientFactory clientFactory,
                                McpToolManager toolManager) {
        this.properties = properties;
        this.clientFactory = clientFactory;
        this.toolManager = toolManager;

        List<McpGatewayProperties.Upstream> configs = properties.getUpstreams();
        if (configs != null) {
            for (McpGatewayProperties.Upstream cfg : configs) {
                if (!StringUtils.hasText(cfg.getName()) || !StringUtils.hasText(cfg.getUrl())) {
                    log.warn("⚠️ [V1.25] 跳过非法上游配置（name/url 必填）: {}", cfg);
                    continue;
                }
                if (entries.containsKey(cfg.getName())) {
                    log.warn("⚠️ [V1.25] 上游名称重复，后者覆盖: {}", cfg.getName());
                }
                entries.put(cfg.getName(), new UpstreamEntry(cfg, clientFactory.create(cfg)));
            }
        }
        log.info("🌐 [V1.25] MCP Federation Gateway 初始化: {} 个上游", entries.size());
    }

    // ===== 同步 =====

    /** 同步全部上游（启动时/手动全量刷新） */
    public Mono<Map<String, Object>> syncAll() {
        return Flux.fromIterable(entries.keySet())
                .concatMap(this::sync)
                .collectList()
                .map(results -> {
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("success", true);
                    out.put("synced", results.size());
                    out.put("upstreams", results);
                    return out;
                });
    }

    /**
     * 同步单个上游：tools/list → 注册联邦工具。
     *
     * <p>失败语义：标记 unhealthy、保留旧工具（避免可用性抖动）；</p>
     * <p>禁用语义：移除该上游全部联邦工具并保持未注册。</p>
     */
    public Mono<Map<String, Object>> sync(String name) {
        UpstreamEntry entry = entries.get(name);
        if (entry == null) {
            return Mono.just(Map.of(
                    "success", false, "upstream", name, "error", "unknown upstream: " + name));
        }

        if (!entry.config.isEnabled()) {
            removeRegistered(entry);
            entry.status.setHealthy(null);
            entry.status.setToolCount(0);
            entry.status.setLastSyncAt(Instant.now().toString());
            entry.status.setError("");
            log.info("🗑️ [V1.25] 上游 {} 已禁用，移除其 {} 个联邦工具", name, entry.registered.size());
            return Mono.just(Map.of(
                    "success", true, "upstream", name, "disabled", true, "status", entry.status.toMap()));
        }

        entry.status.setHealthy(null);
        entry.status.setError("");

        java.util.concurrent.atomic.AtomicBoolean ok = new java.util.concurrent.atomic.AtomicBoolean(true);
        return entry.client.listTools()
                .doOnNext(tools -> {
                    int oldCount = entry.registered.size();
                    replaceTools(entry, tools);
                    entry.status.setHealthy(true);
                    entry.status.setToolCount(entry.registered.size());
                    entry.status.setLastSyncAt(Instant.now().toString());
                    log.info("✅ [V1.25] 上游 {} 同步完成: {} → {} 个工具", name, oldCount, entry.registered.size());
                })
                .doOnError(err -> {
                    ok.set(false);
                    entry.status.setHealthy(false);
                    entry.status.setError(err.getMessage());
                    log.warn("⚠️ [V1.25] 上游 {} 同步失败（保留已注册工具）: {}", name, err.getMessage());
                })
                .onErrorResume(err -> Mono.empty())
                .then(Mono.fromCallable(() -> Map.of(
                        "success", ok.get(), "upstream", name, "status", entry.status.toMap())));
    }

    /** 移除上游全部联邦工具（下线/卸载） */
    public Mono<Map<String, Object>> removeTools(String name) {
        UpstreamEntry entry = entries.get(name);
        if (entry == null) {
            return Mono.just(Map.of(
                    "success", false, "upstream", name, "error", "unknown upstream: " + name));
        }
        int removed = entry.registered.size();
        removeRegistered(entry);
        entry.status.setToolCount(0);
        entry.status.setLastSyncAt(Instant.now().toString());
        log.info("🗑️ [V1.25] 移除上游 {} 的 {} 个联邦工具", name, removed);
        return Mono.just(Map.of(
                "success", true, "upstream", name, "removed", removed, "status", entry.status.toMap()));
    }

    // ===== 查询 =====

    /** 所有上游状态快照（管理 API） */
    public List<Map<String, Object>> listUpstreams() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (UpstreamEntry entry : entries.values()) {
            list.add(entry.status.toMap());
        }
        return list;
    }

    /** 聚合健康视图 */
    public Mono<Map<String, Object>> health() {
        return Mono.fromCallable(() -> {
            int total = entries.size();
            int healthy = 0;
            int federatedTools = 0;
            for (UpstreamEntry entry : entries.values()) {
                if (Boolean.TRUE.equals(entry.status.getHealthy())) {
                    healthy++;
                }
                federatedTools += entry.status.getToolCount();
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("healthy", healthy);
            out.put("total", total);
            out.put("unhealthy", total - healthy);
            out.put("federatedTools", federatedTools);
            out.put("upstreams", listUpstreams());
            return out;
        });
    }

    // ===== 内部 =====

    /** 用新拉取的远程工具列表替换该上游的注册集（先摘旧、后挂新） */
    private void replaceTools(UpstreamEntry entry, List<RemoteToolDescriptor> tools) {
        removeRegistered(entry);
        if (toolManager == null) {
            log.warn("⚠️ [V1.25] McpToolManager 不可用，跳过注册（上游 {}）", entry.config.getName());
            return;
        }
        for (RemoteToolDescriptor tool : tools) {
            if (!StringUtils.hasText(tool.name())) {
                continue;
            }
            FederatedToolExecutor executor = new FederatedToolExecutor(entry.client, entry.config, tool);
            toolManager.registerExecutor(executor);
            entry.registered.add(executor.getLocalName());
        }
    }

    private void removeRegistered(UpstreamEntry entry) {
        if (toolManager == null) {
            entry.registered.clear();
            return;
        }
        for (String localName : entry.registered) {
            try {
                toolManager.unregisterTool(localName);
            } catch (Exception e) {
                log.debug("[V1.25] 注销工具 {} 失败（可能已不存在）: {}", localName, e.getMessage());
            }
        }
        entry.registered.clear();
    }
}