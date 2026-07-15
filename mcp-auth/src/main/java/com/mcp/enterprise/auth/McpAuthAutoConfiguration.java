package com.mcp.enterprise.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * MCP 企业认证自动配置
 *
 * 根据 mcp.auth.mode 自动配置：
 * - none: 无认证
 * - api-key: API Key 认证（默认）
 * - oauth2: OAuth2/OIDC 企业认证
 */
@AutoConfiguration
@EnableConfigurationProperties(McpAuthProperties.class)
public class McpAuthAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(McpAuthAutoConfiguration.class);

    private final McpAuthProperties properties;

    public McpAuthAutoConfiguration(McpAuthProperties properties) {
        this.properties = properties;
    }

    @Bean
    @ConditionalOnMissingBean
    public McpJwtTokenProvider jwtTokenProvider() {
        return new McpJwtTokenProvider(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public McpAuthController mcpAuthController(McpJwtTokenProvider tokenProvider) {
        return new McpAuthController(properties, tokenProvider);
    }
}
