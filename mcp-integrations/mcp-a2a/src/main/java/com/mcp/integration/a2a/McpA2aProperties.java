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
 * </pre>
 */
@ConfigurationProperties(prefix = "mcp.enterprise.a2a")
public class McpA2aProperties {

    /** 是否启用 A2A 网关（默认关闭，opt-in 防止意外暴露 HTTP 面） */
    private boolean enabled = false;

    /** HTTP 基础路径（默认 /a2a；agent-card 与 rpc 端点挂在其下） */
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
}