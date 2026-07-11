package com.mcp.enterprise.core.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.Map;

/**
 * MCP 工具定义 - 企业级扩展
 *
 * 在标准 MCP Tool 基础上增加分类、权限、监控字段。
 * 使用 Lombok 自动生成 getter/setter/toString/equals/hashCode。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
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
    /** 超时时间(ms) */
    private long timeoutMs = 30000;
    /** 频率限制(每秒调用次数) */
    private int rateLimitPerSecond = 10;
    /** 输入参数 schema */
    private Map<String, Object> inputSchema;
    /** 额外元数据 */
    private Map<String, Object> metadata;
}
