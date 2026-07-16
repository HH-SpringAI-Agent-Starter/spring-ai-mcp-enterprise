package com.mcp.enterprise.monitor;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MCP 监控模块配置属性
 */
@ConfigurationProperties(prefix = "mcp.enterprise.monitor")
public class McpMonitorProperties {

    /** 是否启用监控模块 */
    private boolean enabled = true;

    /** 指标配置 */
    private Metrics metrics = new Metrics();

    /** 审计配置 */
    private Audit audit = new Audit();

    /** 告警配置 */
    private Alerts alerts = new Alerts();

    // ===== Getters & Setters =====

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Metrics getMetrics() { return metrics; }
    public void setMetrics(Metrics metrics) { this.metrics = metrics; }

    public Audit getAudit() { return audit; }
    public void setAudit(Audit audit) { this.audit = audit; }

    public Alerts getAlerts() { return alerts; }
    public void setAlerts(Alerts alerts) { this.alerts = alerts; }

    // ===== 嵌套配置类 =====

    public static class Metrics {
        /** 数据保留时间（毫秒，默认 1 小时） */
        private long retentionMs = 3_600_000L;

        public long getRetentionMs() { return retentionMs; }
        public void setRetentionMs(long retentionMs) { this.retentionMs = retentionMs; }
    }

    public static class Audit {
        /** 最大审计记录数 */
        private int maxEntries = 10_000;

        /** 是否记录参数详情（生产建议关闭） */
        private boolean logParams = false;

        /** 是否同时写入 SLF4J 日志 */
        private boolean logToSlf4j = true;

        public int getMaxEntries() { return maxEntries; }
        public void setMaxEntries(int maxEntries) { this.maxEntries = maxEntries; }

        public boolean isLogParams() { return logParams; }
        public void setLogParams(boolean logParams) { this.logParams = logParams; }

        public boolean isLogToSlf4j() { return logToSlf4j; }
        public void setLogToSlf4j(boolean logToSlf4j) { this.logToSlf4j = logToSlf4j; }
    }

    public static class Alerts {
        /** 最大告警历史 */
        private int maxHistory = 1000;

        /** 告警抑制时间（毫秒，默认 5 分钟） */
        private long suppressMs = 300_000L;

        public int getMaxHistory() { return maxHistory; }
        public void setMaxHistory(int maxHistory) { this.maxHistory = maxHistory; }

        public long getSuppressMs() { return suppressMs; }
        public void setSuppressMs(long suppressMs) { this.suppressMs = suppressMs; }
    }
}
