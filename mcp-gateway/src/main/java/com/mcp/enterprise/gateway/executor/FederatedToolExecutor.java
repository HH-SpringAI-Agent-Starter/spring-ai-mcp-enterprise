package com.mcp.enterprise.gateway.executor;

import com.mcp.enterprise.core.model.ToolDefinition;
import com.mcp.enterprise.core.tool.McpToolExecutor;
import com.mcp.enterprise.gateway.McpGatewayProperties;
import com.mcp.enterprise.gateway.RemoteToolDescriptor;
import com.mcp.enterprise.gateway.UpstreamMcpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * V1.25 联邦工具执行器：把下游 MCP Server 的远程工具包装成本地 {@link McpToolExecutor}。
 *
 * <p>因此联邦工具**自动继承**企业治理全家桶：注册进 {@code McpToolManager} 后，
 * RBAC 角色校验、RateLimit、审计日志、工具级 Scope 授权、健康检查、调用统计
 * 全部按本地工具同等生效——对调用方（Agent/业务系统）完全透明。</p>
 *
 * <p>本地工具名 = {@code <prefix>__<remoteName>}（prefix 缺省为上游 name），
 * 用于跨上游命名空间隔离，避免工具名冲突。</p>
 */
public class FederatedToolExecutor implements McpToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(FederatedToolExecutor.class);

    private final UpstreamMcpClient client;
    private final McpGatewayProperties.Upstream config;
    private final RemoteToolDescriptor descriptor;
    private final String localName;

    public FederatedToolExecutor(UpstreamMcpClient client,
                                 McpGatewayProperties.Upstream config,
                                 RemoteToolDescriptor descriptor) {
        this.client = client;
        this.config = config;
        this.descriptor = descriptor;
        String prefix = StringUtils.hasText(config.getPrefix()) ? config.getPrefix() : config.getName();
        this.localName = StringUtils.hasText(prefix) ? prefix + "__" + descriptor.name() : descriptor.name();
    }

    /** 本地（带前缀）工具名 */
    public String getLocalName() {
        return localName;
    }

    /** 上游原始工具名 */
    public String getRemoteName() {
        return descriptor.name();
    }

    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("federated", true);
        metadata.put("upstream", config.getName());
        metadata.put("upstreamUrl", config.getUrl());
        metadata.put("remoteName", descriptor.name());

        return new ToolDefinition(
                localName,
                descriptor.name(),
                descriptor.description() == null ? "" : descriptor.description(),
                "federated",
                "1.0.0",
                "upstream:" + config.getName(),
                true,
                null,
                config.getRequestTimeoutMs(),
                config.getRateLimitPerSecond(),
                descriptor.inputSchema() == null ? Map.of() : descriptor.inputSchema(),
                metadata,
                null);
    }

    @Override
    public Mono<Map<String, Object>> execute(Map<String, Object> params) {
        return client.callTool(descriptor.name(), params)
                .map(result -> {
                    Map<String, Object> out = new LinkedHashMap<>(result);
                    out.putIfAbsent("success", true);
                    out.put("tool", localName);
                    out.put("upstream", config.getName());
                    return out;
                })
                .onErrorResume(err -> {
                    log.warn("⛔ 联邦工具 {} (上游 {}) 调用失败: {}", localName, config.getName(), err.getMessage());
                    Map<String, Object> failed = new LinkedHashMap<>();
                    failed.put("success", false);
                    failed.put("error", err.getMessage());
                    failed.put("tool", localName);
                    failed.put("upstream", config.getName());
                    return Mono.just(failed);
                });
    }

    @Override
    public Mono<Boolean> healthCheck() {
        return client.ping();
    }

    @Override
    public Map<String, Object> getStats() {
        return Map.of(
                "executor", getDefinition().getName(),
                "upstream", config.getName(),
                "remoteTool", descriptor.name(),
                "status", "ready");
    }
}