package com.mcp.tool.http;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * HTTP 工具配置属性
 *
 * <p>通过 application.yml 配置允许调用的域名白名单与全局默认超时：</p>
 * <pre>
 * mcp:
 *   tool:
 *     http:
 *       enabled: true
 *       allowed-hosts:
 *         - api.example.com
 *         - "*.internal.corp"
 *       connect-timeout: 5s
 *       read-timeout: 30s
 *       max-response-bytes: 1048576
 * </pre>
 *
 * <p>安全设计：未配置白名单时默认仅允许 localhost/127.0.0.1，防止 SSRF 风险。</p>
 */
@Component
@ConfigurationProperties(prefix = "mcp.tool.http")
public class McpHttpToolProperties {

    /** 是否启用 HTTP 工具 */
    private boolean enabled = true;

    /** 允许调用的主机名白名单（支持 * 通配符），为空时仅允许 localhost */
    private List<String> allowedHosts = new ArrayList<>();

    /** 连接超时 */
    private java.time.Duration connectTimeout = java.time.Duration.ofSeconds(5);

    /** 读取超时 */
    private java.time.Duration readTimeout = java.time.Duration.ofSeconds(30);

    /** 响应体最大字节数（默认 1MB） */
    private long maxResponseBytes = 1024 * 1024;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getAllowedHosts() {
        return allowedHosts;
    }

    public void setAllowedHosts(List<String> allowedHosts) {
        this.allowedHosts = allowedHosts;
    }

    public java.time.Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(java.time.Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public java.time.Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(java.time.Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public long getMaxResponseBytes() {
        return maxResponseBytes;
    }

    public void setMaxResponseBytes(long maxResponseBytes) {
        this.maxResponseBytes = maxResponseBytes;
    }
}
