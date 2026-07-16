package com.mcp.integration.alibaba;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.autoconfigure.dashscope.DashScopeConnectionProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

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
 * mcp:
 *   enterprise:
 *     integration:
 *       alibaba:
 *         enabled: true
 *         mcp-server-port: 8081
 *         chat-model: qwen-max
 *         mcp-client-auto-connect: true
 * ```
 *
 * 启动后自动:
 * 1. 连接到 MCP Enterprise Server
 * 2. 发现并缓存注册的工具
 * 3. 提供工具调用桥接
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
    @ConditionalOnMissingBean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    @ConditionalOnProperty(prefix = "mcp.enterprise.integration.alibaba", name = "enabled", havingValue = "true", matchIfMissing = true)
    public McpAlibabaIntegration alibabaIntegration(RestTemplate restTemplate) {
        McpAlibabaIntegration integration = new McpAlibabaIntegration(properties, restTemplate);
        log.info("☁️ Spring AI Alibaba MCP 集成已激活");
        log.info("   Chat Model: {}", properties.getChatModel());
        log.info("   Embedding Model: {}", properties.getEmbeddingModel());
        log.info("   MCP Server: http://localhost:{}/api/mcp", properties.getServerPort());
        log.info("   自动连接: {}", properties.isMcpClientAutoConnect());

        // 如果配置了自动连接，立即尝试连接
        if (properties.isMcpClientAutoConnect()) {
            boolean connected = integration.connect();
            if (connected) {
                log.info("✅ MCP Server 连接成功，已发现 {} 个工具", integration.getToolCount());
            } else {
                log.warn("⚠️ MCP Server 连接失败，将在首次调用时重试");
            }
        }

        return integration;
    }
}
