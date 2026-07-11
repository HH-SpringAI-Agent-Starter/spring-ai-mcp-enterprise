package com.mcp.enterprise.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

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

    public Server getServer() { return server; }
    public void setServer(Server server) { this.server = server; }
    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }
    public Monitor getMonitor() { return monitor; }
    public void setMonitor(Monitor monitor) { this.monitor = monitor; }
    public Registry getRegistry() { return registry; }
    public void setRegistry(Registry registry) { this.registry = registry; }
}
