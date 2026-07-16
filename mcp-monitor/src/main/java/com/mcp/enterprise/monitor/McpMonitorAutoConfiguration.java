package com.mcp.enterprise.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * MCP 监控模块自动配置
 *
 * 启用方式（默认自动启用）：
 * application.yml:
 * ```yaml
 * mcp:
 *   enterprise:
 *     monitor:
 *       enabled: true
 *       metrics:
 *         retention-ms: 3600000    # 数据保留时间（1小时）
 *       audit:
 *         max-entries: 10000       # 最大审计记录数
 *         log-params: false        # 是否记录参数（生产建议关闭）
 *       alerts:
 *         max-history: 1000        # 最大告警历史
 *         suppress-ms: 300000      # 告警抑制时间（5分钟）
 * ```
 */
@AutoConfiguration
@EnableConfigurationProperties(McpMonitorProperties.class)
@ConditionalOnProperty(prefix = "mcp.enterprise.monitor", name = "enabled", havingValue = "true", matchIfMissing = true)
public class McpMonitorAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(McpMonitorAutoConfiguration.class);

    private final McpMonitorProperties properties;

    public McpMonitorAutoConfiguration(McpMonitorProperties properties) {
        this.properties = properties;
    }

    @Bean
    @ConditionalOnMissingBean
    public McpMetricsCollector metricsCollector() {
        return new McpMetricsCollector(properties.getMetrics().getRetentionMs());
    }

    @Bean
    @ConditionalOnMissingBean
    public McpAuditLogger auditLogger() {
        var auditProps = properties.getAudit();
        return new McpAuditLogger(
                auditProps.getMaxEntries(),
                auditProps.isLogParams(),
                auditProps.isLogToSlf4j()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public McpAlertService alertService(McpMetricsCollector metricsCollector) {
        var alertProps = properties.getAlerts();
        return new McpAlertService(
                metricsCollector,
                alertProps.getMaxHistory(),
                alertProps.getSuppressMs()
        );
    }

    /**
     * MCP 工具调用监听器 — 自动记录调用指标和审计日志
     */
    @Bean
    @ConditionalOnMissingBean
    public McpToolInvocationMonitor toolInvocationMonitor(
            McpMetricsCollector metricsCollector,
            McpAuditLogger auditLogger) {
        return new McpToolInvocationMonitor(metricsCollector, auditLogger);
    }
}
