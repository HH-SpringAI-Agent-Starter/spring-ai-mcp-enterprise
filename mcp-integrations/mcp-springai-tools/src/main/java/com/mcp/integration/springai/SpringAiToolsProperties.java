package com.mcp.integration.springai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Spring AI 工具桥接配置（V1.23）
 *
 * <p>把 Spring AI 生态里已有的 {@code @Tool} / {@link org.springframework.ai.tool.ToolCallback}
 * 工具自动注册为企业级 MCP 工具，并统一套用企业治理能力（RBAC / 限流 / 审计 / Scope / 超时）。</p>
 *
 * <p>配置前缀：{@code mcp.springai-tools.*}</p>
 */
@ConfigurationProperties(prefix = "mcp.springai-tools")
public class SpringAiToolsProperties {

    /** 是否启用 Spring AI 工具桥接（默认 true） */
    private boolean enabled = true;

    /** 是否收集 {@link org.springframework.ai.tool.ToolCallbackProvider} Bean（默认 true，兼容 MCP Client 动态工具源） */
    private boolean includeToolCallbackProviders = true;

    /** 是否扫描带 {@code @Tool} 注解方法的 Spring Bean（默认 true，覆盖绝大多数 Spring AI 用法） */
    private boolean scanToolAnnotatedBeans = true;

    /** 工具名统一前缀（默认空；用于避免与内置工具/多来源工具重名，如 {@code sa_}） */
    private String namePrefix = "";

    /** 工具分类，用于企业侧分类治理（默认 springai） */
    private String category = "springai";

    /** 工具版本号（默认 1.0） */
    private String version = "1.0";

    /** 默认所需角色（逗号分隔，沿用企业 RBAC），默认 admin,user */
    private String defaultRoles = "admin,user";

    /** 默认所需 OAuth2 scope（逗号分隔；为空表示不做 scope 约束） */
    private String requiredScopes;

    /** 默认调用超时（毫秒，默认 30000） */
    private long timeoutMs = 30000;

    /** 默认每秒限流阈值（默认 10） */
    private int rateLimitPerSecond = 10;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isIncludeToolCallbackProviders() {
        return includeToolCallbackProviders;
    }

    public void setIncludeToolCallbackProviders(boolean includeToolCallbackProviders) {
        this.includeToolCallbackProviders = includeToolCallbackProviders;
    }

    public boolean isScanToolAnnotatedBeans() {
        return scanToolAnnotatedBeans;
    }

    public void setScanToolAnnotatedBeans(boolean scanToolAnnotatedBeans) {
        this.scanToolAnnotatedBeans = scanToolAnnotatedBeans;
    }

    public String getNamePrefix() {
        return namePrefix;
    }

    public void setNamePrefix(String namePrefix) {
        this.namePrefix = namePrefix;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getDefaultRoles() {
        return defaultRoles;
    }

    public void setDefaultRoles(String defaultRoles) {
        this.defaultRoles = defaultRoles;
    }

    public String getRequiredScopes() {
        return requiredScopes;
    }

    public void setRequiredScopes(String requiredScopes) {
        this.requiredScopes = requiredScopes;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getRateLimitPerSecond() {
        return rateLimitPerSecond;
    }

    public void setRateLimitPerSecond(int rateLimitPerSecond) {
        this.rateLimitPerSecond = rateLimitPerSecond;
    }

    /** 计算最终注册名（应用前缀） */
    public String applyPrefix(String rawName) {
        if (namePrefix == null || namePrefix.isBlank()) {
            return rawName;
        }
        return namePrefix + rawName;
    }
}
