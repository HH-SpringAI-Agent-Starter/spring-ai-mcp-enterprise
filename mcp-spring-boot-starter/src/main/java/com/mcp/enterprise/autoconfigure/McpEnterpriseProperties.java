package com.mcp.enterprise.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * MCP Enterprise 配置属性
 * 
 * application.yml 示例：
 * mcp:
 *   enterprise:
 *     server:
 *       port: 8081
 *     security:
 *       api-key-enabled: true
 *       rate-limit-enabled: true
 *       audit-log-enabled: true
 *       audit-log-max-size: 10000
 *     monitor:
 *       enabled: true
 *       metrics-export-interval: 60
 */
@ConfigurationProperties(prefix = "mcp.enterprise")
public class McpEnterpriseProperties {

    private Server server = new Server();
    private Security security = new Security();
    private Monitor monitor = new Monitor();
    private Registry registry = new Registry();
    private OAuth2 oauth2 = new OAuth2();

    public static class Server {
        private int port = 8081;
        private String contextPath = "/mcp";
        private int maxRequestSize = 1048576; // 1MB
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getContextPath() { return contextPath; }
        public void setContextPath(String contextPath) { this.contextPath = contextPath; }
        public int getMaxRequestSize() { return maxRequestSize; }
        public void setMaxRequestSize(int maxRequestSize) { this.maxRequestSize = maxRequestSize; }
    }

    public static class Security {
        private boolean apiKeyEnabled = true;
        private boolean rateLimitEnabled = true;
        private boolean auditLogEnabled = true;
        private int auditLogMaxSize = 10000;
        private String defaultRoles = "user";
        /** V1.19: 工具级 Scope 授权策略（Token Scope → Tool ACL） */
        private Scope scope = new Scope();
        public boolean isApiKeyEnabled() { return apiKeyEnabled; }
        public void setApiKeyEnabled(boolean apiKeyEnabled) { this.apiKeyEnabled = apiKeyEnabled; }
        public boolean isRateLimitEnabled() { return rateLimitEnabled; }
        public void setRateLimitEnabled(boolean rateLimitEnabled) { this.rateLimitEnabled = rateLimitEnabled; }
        public boolean isAuditLogEnabled() { return auditLogEnabled; }
        public void setAuditLogEnabled(boolean auditLogEnabled) { this.auditLogEnabled = auditLogEnabled; }
        public int getAuditLogMaxSize() { return auditLogMaxSize; }
        public void setAuditLogMaxSize(int auditLogMaxSize) { this.auditLogMaxSize = auditLogMaxSize; }
        public String getDefaultRoles() { return defaultRoles; }
        public void setDefaultRoles(String defaultRoles) { this.defaultRoles = defaultRoles; }
        public Scope getScope() { return scope; }
        public void setScope(Scope scope) { this.scope = scope; }
    }

    // ===== V1.19: 工具级 Scope 授权（Token Scope → Tool ACL） =====
    public static class Scope {
        /** 总开关：false 恒放行（与引入前行为一致，向后兼容） */
        private boolean enabled = false;
        /** 工具名 → 所需 scope 模式（空格/逗号分隔，支持 * / ** 通配） */
        private Map<String, String> toolOverrides = new java.util.LinkedHashMap<>();
        /** 工具分类 → 所需 scope 模式（未显式声明时按分类兜底），如 finance → tools:finance:* */
        private Map<String, String> categoryDefaults = new java.util.LinkedHashMap<>();
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public Map<String, String> getToolOverrides() { return toolOverrides; }
        public void setToolOverrides(Map<String, String> toolOverrides) { this.toolOverrides = toolOverrides; }
        public Map<String, String> getCategoryDefaults() { return categoryDefaults; }
        public void setCategoryDefaults(Map<String, String> categoryDefaults) { this.categoryDefaults = categoryDefaults; }
    }

    public static class Monitor {
        private boolean enabled = true;
        private int metricsExportInterval = 60;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getMetricsExportInterval() { return metricsExportInterval; }
        public void setMetricsExportInterval(int metricsExportInterval) { this.metricsExportInterval = metricsExportInterval; }
    }

    public static class Registry {
        private boolean autoScanEnabled = true;
        private String scanPackages = "com.mcp.tool";
        public boolean isAutoScanEnabled() { return autoScanEnabled; }
        public void setAutoScanEnabled(boolean autoScanEnabled) { this.autoScanEnabled = autoScanEnabled; }
        public String getScanPackages() { return scanPackages; }
        public void setScanPackages(String scanPackages) { this.scanPackages = scanPackages; }
    }

    // ===== V1.8: OAuth2 Client Credentials + EMA =====
    public static class OAuth2 {
        private boolean enabled = true;
        private String signingKey = "change-me-in-production-oauth2-signing-key";
        private long tokenTtlSeconds = 3600;
        // V1.9: refresh token 轮换 + 网关 Bearer 自动校验
        private long refreshTokenTtlSeconds = 2592000;   // 30 天
        private boolean enforceBearer = false;           // 开启后网关对非公开路径强制 Bearer 校验
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getSigningKey() { return signingKey; }
        public void setSigningKey(String signingKey) { this.signingKey = signingKey; }
        public long getTokenTtlSeconds() { return tokenTtlSeconds; }
        public void setTokenTtlSeconds(long tokenTtlSeconds) { this.tokenTtlSeconds = tokenTtlSeconds; }
        public long getRefreshTokenTtlSeconds() { return refreshTokenTtlSeconds; }
        public void setRefreshTokenTtlSeconds(long refreshTokenTtlSeconds) { this.refreshTokenTtlSeconds = refreshTokenTtlSeconds; }
        public boolean isEnforceBearer() { return enforceBearer; }
        public void setEnforceBearer(boolean enforceBearer) { this.enforceBearer = enforceBearer; }
    }

    public Server getServer() { return server; }
    public void setServer(Server server) { this.server = server; }
    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }
    public Monitor getMonitor() { return monitor; }
    public void setMonitor(Monitor monitor) { this.monitor = monitor; }
    public Registry getRegistry() { return registry; }
    public void setRegistry(Registry registry) { this.registry = registry; }
    public OAuth2 getOauth2() { return oauth2; }
    public void setOauth2(OAuth2 oauth2) { this.oauth2 = oauth2; }
}
