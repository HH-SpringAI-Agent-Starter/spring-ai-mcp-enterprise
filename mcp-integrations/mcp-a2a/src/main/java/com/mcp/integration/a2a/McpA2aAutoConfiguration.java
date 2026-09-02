package com.mcp.integration.a2a;

import com.mcp.enterprise.core.registry.ToolRegistry;
import com.mcp.enterprise.core.tool.McpToolManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RestController;

/**
 * A2A (Agent2Agent) 网关自动配置
 *
 * 启用方式（应用方 application.yml）：
 * <pre>
 * mcp:
 *   enterprise:
 *     a2a:
 *       enabled: true          # 默认关闭，opt-in
 *       api-key: ${A2A_API_KEY:}   # 可选：设置后要求 X-A2A-Key 头
 * </pre>
 *
 * 依赖 Spring MVC（RestController 在 classpath 才装配）；Controller 以 @Bean 注册，
 * 任意 Spring Boot 应用引入本模块依赖即可暴露 /a2a/agent-card 与 /a2a/rpc。
 */
@AutoConfiguration
@ConditionalOnClass(RestController.class)
@ConditionalOnProperty(name = "mcp.enterprise.a2a.enabled", havingValue = "true")
@EnableConfigurationProperties(McpA2aProperties.class)
public class McpA2aAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(McpA2aAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public A2aBridgeService a2aBridgeService(ToolRegistry registry, McpToolManager toolManager,
                                             McpA2aProperties properties) {
        log.info("🌐 [V1.17] 启用 A2A 网关: agent='{}' | authMode={} | api-key={} | oauth2={} | tools={}",
                properties.getAgentName(),
                properties.resolvedAuthMode(),
                properties.getApiKey() == null || properties.getApiKey().isBlank() ? "off" : "on",
                properties.isOAuth2Enabled() ? "on" : "off",
                registry.count());
        return new A2aBridgeService(registry, toolManager, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public A2aJwtTokenValidator a2aJwtTokenValidator(McpA2aProperties properties) {
        if (!properties.isOAuth2Enabled() || properties.getJwtSecret() == null || properties.getJwtSecret().isBlank()) {
            return null; // 未启用 oauth2：不注册校验器
        }
        log.info("🔐 [V1.17] A2A OAuth2 Bearer 校验器已启用 (jwt-secret 与 mcp-auth 同值时令牌互通)");
        return new A2aJwtTokenValidator(properties.getJwtSecret());
    }

    @Bean
    @ConditionalOnMissingBean
    public A2aRpcController a2aRpcController(A2aBridgeService bridgeService, McpA2aProperties properties,
                                             A2aJwtTokenValidator jwtValidator) {
        return new A2aRpcController(bridgeService, properties, jwtValidator);
    }
}