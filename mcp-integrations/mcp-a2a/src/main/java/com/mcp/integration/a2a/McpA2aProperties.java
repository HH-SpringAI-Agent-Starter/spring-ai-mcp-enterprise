package com.mcp.integration.a2a;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * A2A (Agent2Agent) 网关配置
 *
 * 前缀: mcp.enterprise.a2a
 *
 * 示例:
 * <pre>
 * mcp:
 *   enterprise:
 *     a2a:
 *       enabled: true
 *       agent-name: "MCP Enterprise Gateway"
 *       agent-description: "企业 MCP 工具统一 A2A 出口"
 *       api-key: ""            # 非空时要求 X-A2A-Key 请求头
 *       streaming-enabled: true   # V1.16: SSE 流式 (message/stream + task/resubscribe)
 *       security-scheme: api-key  # V1.16: agent-card securitySchemes 声明 (none|api-key|oauth2)
 *       oauth2-token-url: ""      # security-scheme=oauth2 时的 token 端点（对接 mcp-auth）
 * </pre>
 */
@ConfigurationProperties(prefix = "mcp.enterprise.a2a")
public class McpA2aProperties {

    /** 是否启用 A2A 网关（默认关闭，opt-in 防止意外暴露 HTTP 面） */
    private boolean enabled = false;

    /** HTTP 基础路径（默认 /a2a）；agent-card 与 rpc 端点挂在其下 */ 
    private String basePath = "/a2a";

    /** Agent Card 名称 */
    private String agentName = "MCP Enterprise A2A Gateway";

    /** Agent Card 描述 */
    private String agentDescription = "将企业 MCP 工具注册中心的全部工具以 A2A Agent Skill 暴露，供任意 A2A Agent 调用";

    /** Agent 版本号 */
    private String version = "1.0.0";

    /** 可选简单鉴权：非空时，所有请求必须携带 X-A2A-Key 请求头且值一致 */
    private String apiKey = "";

    /** 任务执行超时（毫秒），默认 30s 与 ToolDefinition.timeoutMs 对齐 */
    private long taskTimeoutMs = 30000;

    /** 任务记录保留上限，防止内存无限增长（默认 1000） */
    private int maxTasks = 1000;

    /** V1.16: 是否启用 SSE 流式（message/stream + task/resubscribe），默认 true */
    private boolean streamingEnabled = true;

    /** V1.16: SSE 流心跳间隔毫秒（0=不发送心跳注释行） */
    private long streamHeartbeatMs = 15000;

    /** V1.16: Agent Card securitySchemes 声明：none | api-key | oauth2（默认随 api-key 自动推导） */
    private String securityScheme = "";

    /** V1.16: security-scheme=oauth2 时的 token 端点（对接 mcp-auth OAuth2 Client Credentials） */
    private String oauth2TokenUrl = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public String getAgentDescription() {
        return agentDescription;
    }

    public void setAgentDescription(String agentDescription) {
        this.agentDescription = agentDescription;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public long getTaskTimeoutMs() {
        return taskTimeoutMs;
    }

    public void setTaskTimeoutMs(long taskTimeoutMs) {
        this.taskTimeoutMs = taskTimeoutMs;
    }

    public int getMaxTasks() {
        return maxTasks;
    }

    public void setMaxTasks(int maxTasks) {
        this.maxTasks = maxTasks;
    }

    public boolean isStreamingEnabled() {
        return streamingEnabled;
    }

    public void setStreamingEnabled(boolean streamingEnabled) {
        this.streamingEnabled = streamingEnabled;
    }

    public long getStreamHeartbeatMs() {
        return streamHeartbeatMs;
    }

    public void setStreamHeartbeatMs(long streamHeartbeatMs) {
        this.streamHeartbeatMs = streamHeartbeatMs;
    }

    public String getSecurityScheme() {
        return securityScheme;
    }

    public void setSecurityScheme(String securityScheme) {
        this.securityScheme = securityScheme;
    }

    public String getOauth2TokenUrl() {
        return oauth2TokenUrl;
    }

    public void setOauth2TokenUrl(String oauth2TokenUrl) {
        this.oauth2TokenUrl = oauth2TokenUrl;
    }

    /** 推导实际 securityScheme：显式配置优先，否则 api-key 非空则 api-key，否则 none */
    public String resolvedSecurityScheme() {
        if (securityScheme != null && !securityScheme.isBlank()) {
            return securityScheme;
        }
        return (apiKey != null && !apiKey.isBlank()) ? "api-key" : "none";
    }
}