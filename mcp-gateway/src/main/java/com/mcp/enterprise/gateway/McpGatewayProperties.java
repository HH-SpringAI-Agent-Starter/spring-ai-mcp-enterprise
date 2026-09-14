package com.mcp.enterprise.gateway;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * V1.25 MCP Federation Gateway 配置。
 *
 * <p>前缀 {@code mcp.enterprise.gateway}，默认开启。示例：</p>
 * <pre>
 * mcp:
 *   enterprise:
 *     gateway:
 *       sync-on-startup: true
 *       upstreams:
 *         - name: hr            # 上游唯一名（工具前缀来源）
 *           url: http://hr-mcp:8080/mcp   # 下游 MCP Server 的 Streamable HTTP 端点
 *           api-key: ${HR_MCP_API_KEY:}   # 可选：Bearer 凭证透传
 *           enabled: true
 *         - name: finance
 *           url: http://finance-mcp:8080/mcp
 * </pre>
 *
 * <p>联邦工具命名规则：{@code <prefix>__<remoteToolName>}，prefix 缺省取上游 name。</p>
 */
@Data
@ConfigurationProperties(prefix = "mcp.enterprise.gateway")
public class McpGatewayProperties {

    /** 总开关（默认 true；false 时本模块不装配） */
    private boolean enabled = true;

    /** 应用启动完成后自动同步所有启用的上游（默认 true） */
    private boolean syncOnStartup = true;

    /** 上游 MCP Server 列表 */
    private List<Upstream> upstreams = new ArrayList<>();

    @Data
    public static class Upstream {

        /** 上游唯一名（必填）；同时是工具前缀缺省值 */
        private String name;

        /** Streamable HTTP 端点 URL（必填），如 http://host:8080/mcp */
        private String url;

        /** 传输协议：当前仅 streamable-http（MCP 2026-07-28） */
        private Transport transport = Transport.STREAMABLE_HTTP;

        /** 可选 Bearer 凭证：非空时每个 JSON-RPC 请求携带 Authorization: Bearer <api-key> */
        private String apiKey = "";

        /** 工具命名空间前缀；空则使用上游 name */
        private String prefix = "";

        /** 是否参与联邦（默认 true）；置 false 时其工具会被移除并保持未注册 */
        private boolean enabled = true;

        /** 连接超时（毫秒） */
        private int connectTimeoutMs = 3000;

        /** 单次 JSON-RPC 请求超时（毫秒），同时作为联邦工具定义里的 timeoutMs */
        private int requestTimeoutMs = 30000;

        /** 联邦工具默认限流（次/秒），可被工具级策略覆盖 */
        private int rateLimitPerSecond = 10;
    }

    public enum Transport {
        /** MCP 2026-07-28 Streamable HTTP（JSON-RPC over HTTP） */
        STREAMABLE_HTTP
    }
}