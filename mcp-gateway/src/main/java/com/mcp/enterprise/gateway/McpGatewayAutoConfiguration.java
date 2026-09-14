package com.mcp.enterprise.gateway;

import com.mcp.enterprise.core.tool.McpToolManager;
import com.mcp.enterprise.gateway.client.McpUpstreamClientFactory;
import com.mcp.enterprise.gateway.client.StreamableHttpUpstreamClient;
import com.mcp.enterprise.gateway.manager.McpFederationManager;
import com.mcp.enterprise.gateway.manager.McpGatewayAdminController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RestController;

/**
 * V1.25 MCP Federation Gateway 自动配置。
 *
 * <p>默认开启（{@code mcp.enterprise.gateway.enabled=false} 关闭）。装配：</p>
 * <ul>
 *   <li>{@link McpUpstreamClientFactory}：Streamable HTTP 客户端工厂（可替换）；</li>
 *   <li>{@link McpFederationManager}：联邦编排（依赖 McpToolManager，缺失时降级为只读状态）；</li>
 *   <li>{@link McpGatewayAdminController}：管理 REST API；</li>
 *   <li>启动同步 Runner（{@code sync-on-startup=true} 时）。</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnClass(RestController.class)
@ConditionalOnProperty(prefix = "mcp.enterprise.gateway", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(McpGatewayProperties.class)
public class McpGatewayAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(McpGatewayAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public McpUpstreamClientFactory mcpUpstreamClientFactory() {
        return new StreamableHttpUpstreamClient.Factory();
    }

    @Bean
    @ConditionalOnMissingBean
    public McpFederationManager mcpFederationManager(McpGatewayProperties properties,
                                                      McpUpstreamClientFactory clientFactory,
                                                      ObjectProvider<McpToolManager> toolManagerProvider) {
        McpToolManager toolManager = toolManagerProvider.getIfAvailable();
        if (toolManager == null) {
            log.warn("⚠️ [V1.25] McpToolManager 不可用（未装配 mcp-spring-boot-starter？），"
                    + "联邦网关降级为只读状态管理，不会注册任何工具");
        }
        return new McpFederationManager(properties, clientFactory, toolManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public McpGatewayAdminController mcpGatewayAdminController(McpFederationManager manager) {
        return new McpGatewayAdminController(manager);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "mcp.enterprise.gateway", name = "sync-on-startup",
            havingValue = "true", matchIfMissing = true)
    public ApplicationRunner mcpGatewayStartupSyncRunner(McpFederationManager manager) {
        log.info("🚀 [V1.25] 启动时同步联邦上游（mcp.enterprise.gateway.sync-on-startup=true）");
        return args -> manager.syncAll()
                .doOnNext(summary -> log.info("🌐 [V1.25] 启动联邦同步完成: {}", summary))
                .subscribe();
    }
}