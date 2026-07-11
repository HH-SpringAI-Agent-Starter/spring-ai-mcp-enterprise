package com.mcp.integration.alibaba;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MCP Alibaba 集成核心 — 管理 DashScope MCP 客户端生命周期
 *
 * 功能:
 * 1. 连接 MCP Enterprise Server
 * 2. 自动发现已注册工具
 * 3. 集成到 Spring AI 的 Tool Context 中
 */
public class McpAlibabaIntegration {

    private static final Logger log = LoggerFactory.getLogger(McpAlibabaIntegration.class);

    private final McpAlibabaProperties properties;
    private volatile boolean connected = false;

    public McpAlibabaIntegration(McpAlibabaProperties properties) {
        this.properties = properties;
        log.info("☁️ Spring AI Alibaba 集成初始化完成");
        log.info("   - 聊天模型: {}", properties.getChatModel());
        log.info("   - Embedding: {}", properties.getEmbeddingModel());
        log.info("   - MCP Server: :{}", properties.getServerPort());
        log.info("   - 环境检测: {}", properties.isAutoDetectCloud() ? "已启用" : "已禁用");
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public McpAlibabaProperties getProperties() {
        return properties;
    }
}
