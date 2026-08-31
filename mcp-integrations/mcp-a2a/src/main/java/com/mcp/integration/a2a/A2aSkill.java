package com.mcp.integration.a2a;

import java.util.List;
import java.util.Map;

/**
 * A2A Agent Skill（能力单元）
 *
 * 由 MCP ToolDefinition 派生：
 * - id = MCP 工具名（A2A 调用方通过 metadata.skillId 或消息前缀定位工具）
 * - inputModes/outputModes 固定 text；additional 携带 MCP inputSchema 供协议方强类型校验
 */
public record A2aSkill(
        String id,
        String name,
        String description,
        List<String> tags,
        List<String> inputModes,
        List<String> outputModes,
        Map<String, Object> additional
) {

    public static A2aSkill of(String id, String name, String description,
                              List<String> tags, Map<String, Object> inputSchema) {
        Map<String, Object> additional = inputSchema == null || inputSchema.isEmpty()
                ? Map.of()
                : Map.of("inputSchema", inputSchema);
        return new A2aSkill(id, name, description, tags,
                List.of("text"), List.of("text"), additional);
    }
}