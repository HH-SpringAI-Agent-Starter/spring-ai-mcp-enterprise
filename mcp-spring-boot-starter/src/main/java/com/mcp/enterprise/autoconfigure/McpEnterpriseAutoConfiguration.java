package com.mcp.enterprise.autoconfigure;

import com.mcp.enterprise.core.endpoint.McpAdminEndpoint;
import com.mcp.enterprise.core.endpoint.McpSseEndpoint;
import com.mcp.enterprise.core.endpoint.McpStatelessEndpoint;
import com.mcp.enterprise.core.registry.ToolRegistry;
import com.mcp.enterprise.core.security.McpOAuth2Manager;
import com.mcp.enterprise.core.security.McpSecurityManager;
import com.mcp.enterprise.core.tool.McpToolExecutor;
import com.mcp.enterprise.core.tool.McpToolManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * MCP Enterprise 自动配置
 *
 * 自动创建并装配 MCP Server 的核心组件：
 * - ToolRegistry: 工具注册中心
 * - McpSecurityManager: 安全管理器
 * - McpToolManager: 工具管理器（自动扫描所有 McpToolExecutor Bean）
 *
 * ⚠️ 注意：Spring Boot 3.4 fat jar 有 @ConditionalOnAvailableEndpoint bug，
 * 已移除 actuator endpoint；使用 /api/mcp/health 替代。
 */
@Configuration
@EnableConfigurationProperties(McpEnterpriseProperties.class)
public class McpEnterpriseAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(McpEnterpriseAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public ToolRegistry toolRegistry() {
        log.info("🔧 初始化 MCP ToolRegistry");
        return new ToolRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public McpSecurityManager mcpSecurityManager(McpEnterpriseProperties properties) {
        McpSecurityManager manager = new McpSecurityManager();
        if (properties.getSecurity() != null) {
            manager.setMaxAuditLogSize(properties.getSecurity().getAuditLogMaxSize());
        }
        log.info("🔒 初始化 MCP SecurityManager");
        return manager;
    }

    // ===== V1.8: OAuth2 Client Credentials + EMA 管理器 =====
    // 为 M2M 场景提供短时令牌，替代长期共享 API Key；可委托企业 IdP 做集中授权（EMA）
    @Bean
    @ConditionalOnMissingBean
    public McpOAuth2Manager mcpOAuth2Manager(McpEnterpriseProperties properties) {
        String signingKey = properties.getOauth2().getSigningKey();
        McpOAuth2Manager manager = new McpOAuth2Manager(signingKey);
        long ttl = properties.getOauth2().getTokenTtlSeconds();
        if (ttl > 0) {
            manager.setTokenTtlSeconds(ttl);
        }
        long refreshTtl = properties.getOauth2().getRefreshTokenTtlSeconds();
        if (refreshTtl > 0) {
            manager.setRefreshTokenTtlSeconds(refreshTtl);
        }
        log.info("🔐 初始化 MCP OAuth2 Manager (token_ttl={}s, refresh_ttl={}s)", manager.getTokenTtlSeconds(), manager.getRefreshTokenTtlSeconds());
        return manager;
    }

    @Bean
    @ConditionalOnMissingBean
    public McpToolManager mcpToolManager(ToolRegistry registry) {
        log.info("⚙️ 初始化 MCP ToolManager");
        return new McpToolManager(registry);
    }

    /**
     * 自动发现并注册所有实现了 McpToolExecutor 接口的 Spring Bean
     *
     * 开发者只需实现 McpToolExecutor 接口并将类标记为 @Component，
     * 框架会自动发现并注册工具。
     */
    @Bean
    @ConditionalOnProperty(name = "mcp.enterprise.registry.auto-scan-enabled", havingValue = "true", matchIfMissing = true)
    public McpToolExecutorRegistrar mcpToolExecutorRegistrar(
            McpToolManager toolManager,
            List<McpToolExecutor> executors) {

        return new McpToolExecutorRegistrar(toolManager, executors);
    }

    @Bean
    @ConditionalOnMissingBean
    public McpSseEndpoint mcpSseEndpoint(ToolRegistry registry, McpToolManager toolManager) {
        return new McpSseEndpoint(registry, toolManager);
    }

    // ===== V1.6: 无状态核心端点（McpStatelessEndpoint）======
    // Streamable HTTP 2026-07-28 无状态传输：每个请求独立，无需 session
    // 网关友好标头（Mcp-Method / Mcp-Name） + 传输验证 + ttlMs/cacheScope 缓存控制
    @Bean
    @ConditionalOnMissingBean
    public McpStatelessEndpoint mcpStatelessEndpoint(ToolRegistry registry, McpToolManager toolManager) {
        return new McpStatelessEndpoint(registry, toolManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public McpAdminEndpoint mcpAdminEndpoint(ToolRegistry registry,
                                              McpSecurityManager securityManager,
                                              McpToolManager toolManager) {
        return new McpAdminEndpoint(registry, securityManager, toolManager);
    }

    /**
     * 内部类：批量注册 McpToolExecutor
     */
    public static class McpToolExecutorRegistrar {

        private static final Logger log = LoggerFactory.getLogger(McpToolExecutorRegistrar.class);

        public McpToolExecutorRegistrar(McpToolManager toolManager, List<McpToolExecutor> executors) {
            if (executors != null && !executors.isEmpty()) {
                log.info("🔍 自动发现 {} 个 MCP 工具执行器", executors.size());
                toolManager.registerExecutors(executors);
            } else {
                log.warn("⚠️ 未发现任何 MCP 工具执行器，请确保至少有一个 McpToolExecutor 实现");
            }
        }
    }
}
