package com.mcp.integration.alibaba;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.autoconfigure.dashscope.DashScopeConnectionProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 自动集成 Spring AI Alibaba (DashScope/通义千问)
 *
 * 当 classpath 中存在 DashScope 相关类且配置 spring.ai.dashscope.api-key 时生效。
 *
 * application.yml 配置示例:
 * ```yaml
 * spring:
 *   ai:
 *     dashscope:
 *       api-key: ${DASHSCOPE_API_KEY}
 *     mcp:
 *       server:
 *         endpoints:
 *           - url: http://localhost:8081/api/mcp
 *             headers:
 *               X-API-Key: ${MCP_API_KEY}
 * ```
 */
@AutoConfiguration
@ConditionalOnClass(DashScopeConnectionProperties.class)
@ConditionalOnProperty(prefix = "spring.ai.dashscope", name = "api-key")
@EnableConfigurationProperties(McpAlibabaProperties.class)
public class McpAlibabaAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(McpAlibabaAutoConfiguration.class);

    private final McpAlibabaProperties properties;

    public McpAlibabaAutoConfiguration(McpAlibabaProperties properties) {
        this.properties = properties;
    }

    @Bean
    @ConditionalOnProperty(prefix = "mcp.enterprise.integration.alibaba", name = "enabled", havingValue = "true", matchIfMissing = true)
    public McpAlibabaIntegration alibabaIntegration() {
        log.info("☁️ Spring AI Alibaba MCP 集成已激活");
        log.info("   Chat Model: {}", properties.getChatModel());
        log.info("   Embedding Model: {}", properties.getEmbeddingModel());
        log.info("   MCP Server: http://localhost:{}/api/mcp", properties.getServerPort());

        if (properties.isAutoDetectCloud()) {
            detectAlibabaCloud();
        }

        return new McpAlibabaIntegration(properties);
    }

    private void detectAlibabaCloud() {
        // 检测是否在阿里云 ECS 环境
        String region = System.getenv("ALIBABA_CLOUD_REGION_ID");
        if (region != null && !region.isEmpty()) {
            log.info("🏗️ 检测到阿里云环境: region={}", region);
        }

        // 检测 K8s 环境
        String k8sSvc = System.getenv("KUBERNETES_SERVICE_HOST");
        if (k8sSvc != null && !k8sSvc.isEmpty()) {
            log.info("☸️ 检测到 Kubernetes 环境: {}", k8sSvc);
        }
    }
}
