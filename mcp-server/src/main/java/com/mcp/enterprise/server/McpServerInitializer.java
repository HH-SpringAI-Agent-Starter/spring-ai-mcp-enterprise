package com.mcp.enterprise.server;

import com.mcp.enterprise.core.registry.ToolRegistry;
import com.mcp.enterprise.core.security.McpSecurityManager;
import com.mcp.enterprise.core.tool.McpToolManager;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * MCP Server 初始化器
 * - 开机注册内置工具
 * - 创建默认 API Key
 * - 加载 SPI 工具
 * - 输出服务状态
 */
@Component
public class McpServerInitializer {

    private static final Logger log = LoggerFactory.getLogger(McpServerInitializer.class);

    private final ToolRegistry registry;
    private final McpSecurityManager securityManager;
    private final McpToolManager toolManager;

    public McpServerInitializer(ToolRegistry registry,
                                McpSecurityManager securityManager,
                                McpToolManager toolManager) {
        this.registry = registry;
        this.securityManager = securityManager;
        this.toolManager = toolManager;
    }

    @PostConstruct
    public void init() {
        log.info("===========================================");
        log.info("   MCP Enterprise Server v0.0.2");
        log.info("===========================================");

        // 创建默认管理员 API Key
        String adminKey = securityManager.createApiKey("admin", Set.of("admin", "user", "readonly"));
        String userKey = securityManager.createApiKey("default", Set.of("user"));

        log.info("📋 注册工具数: {}", registry.count());
        log.info("   ├─ 工具管理器就绪: {} 个执行器", toolManager.count());
        log.info("   └─ 安全模块就绪: {} 个活跃 API Key", securityManager.getApiKeyCount());

        // 列出所有注册的工具
        registry.listAll().toStream().forEach(tool ->
                log.info("   🔧 {} ({}) - {}", tool.getName(), tool.getCategory(), tool.getDisplayName())
        );

        log.info("📋 默认 API Key:");
        log.info("   ├─ 🔑 管理员: {}", maskKey(adminKey));
        log.info("   ├─ 🔑 普通用户: {}", maskKey(userKey));
        log.info("   └─ 📍 服务端口: 8081");
        log.info("===========================================");
    }

    private String maskKey(String key) {
        if (key == null || key.length() < 12) return key;
        return key.substring(0, 8) + "..." + key.substring(key.length() - 4);
    }
}
