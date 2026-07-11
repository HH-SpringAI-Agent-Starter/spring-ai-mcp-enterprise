package com.mcp.integration.alibaba;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MCP Alibaba 集成配置属性
 */
@ConfigurationProperties(prefix = "mcp.enterprise.integration.alibaba")
public class McpAlibabaProperties {

    /**
     * 是否启用 Alibaba 集成 (默认 true)
     */
    private boolean enabled = true;

    /**
     * 通义千问聊天模型 (默认 qwen-max)
     */
    private String chatModel = "qwen-max";

    /**
     * 通义千问嵌入模型 (默认 text-embedding-v3)
     */
    private String embeddingModel = "text-embedding-v3";

    /**
     * MCP Server 端口 (默认 8081)
     */
    private int serverPort = 8081;

    /**
     * 是否自动检测阿里云环境 (默认 true)
     */
    private boolean autoDetectCloud = true;

    /**
     * 是否启用 MCP 客户端自动连接
     */
    private boolean mcpClientAutoConnect = true;

    /**
     * MCP 客户端连接超时 (秒)
     */
    private int connectTimeout = 10;

    /**
     * MCP 客户端读取超时 (秒)
     */
    private int readTimeout = 60;

    // ===== Getters & Setters =====

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getChatModel() { return chatModel; }
    public void setChatModel(String chatModel) { this.chatModel = chatModel; }

    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }

    public int getServerPort() { return serverPort; }
    public void setServerPort(int serverPort) { this.serverPort = serverPort; }

    public boolean isAutoDetectCloud() { return autoDetectCloud; }
    public void setAutoDetectCloud(boolean autoDetectCloud) { this.autoDetectCloud = autoDetectCloud; }

    public boolean isMcpClientAutoConnect() { return mcpClientAutoConnect; }
    public void setMcpClientAutoConnect(boolean mcpClientAutoConnect) { this.mcpClientAutoConnect = mcpClientAutoConnect; }

    public int getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(int connectTimeout) { this.connectTimeout = connectTimeout; }

    public int getReadTimeout() { return readTimeout; }
    public void setReadTimeout(int readTimeout) { this.readTimeout = readTimeout; }
}
