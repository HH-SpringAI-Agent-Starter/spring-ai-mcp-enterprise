package com.mcp.enterprise.core.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * MCP 工具定义 - 企业级扩展
 *
 * 在标准 MCP Tool 基础上增加分类、权限、监控字段。
 * 使用 Lombok 自动生成 getter/setter/toString/equals/hashCode。
 *
 * <p>V1.19：新增 {@code requiredScopes}（OAuth2 scope 级权限）。
 * 注意：保留 12 参构造以向后兼容既有工具，新增字段通过 setter 链式设置——
 * 工具作者可 {@code new ToolDefinition(...).setRequiredScopes("tools:finance:read")}。</p>
 */
@Data
@NoArgsConstructor
public class ToolDefinition {

    /** 工具唯一标识 */
    private String name;
    /** 显示名称 */
    private String displayName;
    /** 描述 */
    private String description;
    /** 工具分类：database / search / system / ai / custom */
    private String category;
    /** 版本号 */
    private String version;
    /** 所属模块 */
    private String module;
    /** 是否启用 */
    private boolean enabled = true;
    /** 所需角色权限，逗号分隔 */
    private String requiredRoles;
    /** V1.19: 所需 OAuth2 scope（空格/逗号分隔，支持通配符 * 与 **），如 tools:finance:read */
    private String requiredScopes;
    /** 超时时间(ms) */
    private long timeoutMs = 30000;
    /** 频率限制(每秒调用次数) */
    private int rateLimitPerSecond = 10;
    /** 输入参数 schema */
    private Map<String, Object> inputSchema;
    /** 额外元数据 */
    private Map<String, Object> metadata;

    /**
     * 兼容构造器（V1.19 前签名不变，requiredScopes 默认 null = 无 scope 约束）。
     */
    public ToolDefinition(String name, String displayName, String description, String category,
                          String version, String module, boolean enabled, String requiredRoles,
                          long timeoutMs, int rateLimitPerSecond,
                          Map<String, Object> inputSchema, Map<String, Object> metadata) {
        this(name, displayName, description, category, version, module, enabled, requiredRoles,
                timeoutMs, rateLimitPerSecond, inputSchema, metadata, null);
    }

    /**
     * 全参构造器（V1.19：末尾追加 requiredScopes）。
     */
    public ToolDefinition(String name, String displayName, String description, String category,
                          String version, String module, boolean enabled, String requiredRoles,
                          long timeoutMs, int rateLimitPerSecond,
                          Map<String, Object> inputSchema, Map<String, Object> metadata,
                          String requiredScopes) {
        this.name = name;
        this.displayName = displayName;
        this.description = description;
        this.category = category;
        this.version = version;
        this.module = module;
        this.enabled = enabled;
        this.requiredRoles = requiredRoles;
        this.requiredScopes = requiredScopes;
        this.timeoutMs = timeoutMs;
        this.rateLimitPerSecond = rateLimitPerSecond;
        this.inputSchema = inputSchema;
        this.metadata = metadata;
    }
}