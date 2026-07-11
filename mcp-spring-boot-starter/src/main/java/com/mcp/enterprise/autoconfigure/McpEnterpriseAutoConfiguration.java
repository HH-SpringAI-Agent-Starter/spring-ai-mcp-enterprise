package com.mcp.enterprise.autoconfigure;

import com.mcp.enterprise.core.registry.ToolRegistry;
import com.mcp.enterprise.core.security.McpSecurityManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP Enterprise 自动配置
 */
@Configuration
@EnableConfigurationProperties(McpEnterpriseProperties.class)
public class McpEnterpriseAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ToolRegistry toolRegistry() {
        return new ToolRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public McpSecurityManager mcpSecurityManager() {
        return new McpSecurityManager();
    }

    @Bean
    public McpEnterpriseEndpoint mcpEnterpriseEndpoint(ToolRegistry registry, McpSecurityManager securityManager) {
        return new McpEnterpriseEndpoint(registry, securityManager);
    }
}
