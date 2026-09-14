package com.mcp.enterprise.gateway;

import lombok.Data;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * V1.25 上游 MCP Server 的运行时状态（供管理 API / 健康检查使用）。
 *
 * <p>可变状态，由 {@code McpFederationManager#sync} / {@code #removeTools} 更新。</p>
 */
@Data
public class McpUpstreamStatus {

    private final String name;
    private final String url;
    private final String prefix;
    private final String transport;
    private final boolean enabled;

    /** null = 尚未同步；true/false = 最近一次同步结果 */
    private Boolean healthy;
    private int toolCount;
    private String lastSyncAt = "";
    private String error = "";

    public McpUpstreamStatus(McpGatewayProperties.Upstream config) {
        this.name = config.getName();
        this.url = config.getUrl();
        this.prefix = StringUtils.hasText(config.getPrefix()) ? config.getPrefix() : config.getName();
        this.transport = config.getTransport() == null
                ? "streamable-http"
                : config.getTransport().name().toLowerCase().replace('_', '-');
        this.enabled = config.isEnabled();
    }

    /** 管理 API 输出视图 */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("url", url);
        m.put("prefix", prefix);
        m.put("transport", transport);
        m.put("enabled", enabled);
        m.put("healthy", healthy);
        m.put("toolCount", toolCount);
        m.put("lastSyncAt", lastSyncAt);
        m.put("error", error);
        return m;
    }
}