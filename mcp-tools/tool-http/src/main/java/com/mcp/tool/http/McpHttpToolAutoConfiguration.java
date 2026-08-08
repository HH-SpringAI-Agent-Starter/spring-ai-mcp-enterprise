package com.mcp.tool.http;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * HTTP 工具自动配置
 *
 * <p>通过 {@code mcp.tool.http.enabled=false} 可关闭该工具。</p>
 */
@Configuration
@EnableConfigurationProperties(McpHttpToolProperties.class)
@ConditionalOnProperty(prefix = "mcp.tool.http", name = "enabled", havingValue = "true", matchIfMissing = true)
public class McpHttpToolAutoConfiguration {

    @Bean
    public HttpExecutor httpExecutor(McpHttpToolProperties properties) {
        return new HttpExecutor(properties);
    }
}
