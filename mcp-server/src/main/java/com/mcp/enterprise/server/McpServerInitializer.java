package com.mcp.enterprise.server;

import com.mcp.enterprise.core.registry.ToolRegistry;
import com.mcp.enterprise.core.security.McpSecurityManager;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * MCP Server 初始化器
 * - 开机注册内置工具
 * - 创建默认 API Key
 * - 加载 SPI 工具
 */
@Component
public class McpServerInitializer {

    private static final Logger log = LoggerFactory.getLogger(McpServerInitializer.class);

    private final ToolRegistry registry;
    private final McpSecurityManager securityManager;

    public McpServerInitializer(ToolRegistry registry, McpSecurityManager securityManager) {
        this.registry = registry;
        this.securityManager = securityManager;
    }

    @PostConstruct
    public void init() {
        log.info("=== MCP Enterprise Server 启动 ===");

        // 创建默认管理员 API Key
        String adminKey = securityManager.createApiKey("admin", Set.of("admin", "user", "readonly"));
        String userKey = securityManager.createApiKey("default", Set.of("user"));

        log.info("默认 API Key (管理员): {}", adminKey);
        log.info("默认 API Key (用户):   {}", userKey);
        log.info("注册工具数: {}", registry.count());
        log.info("================================");
    }
}
