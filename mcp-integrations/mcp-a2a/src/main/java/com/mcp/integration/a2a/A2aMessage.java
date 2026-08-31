package com.mcp.integration.a2a;

import java.util.List;
import java.util.Map;

/**
 * A2A Message（对话消息）
 *
 * role: user | agent
 * parts: 文本片段（text）；本版实现 text 单片段，结构化输出放入 Task.artifacts
 */
public record A2aMessage(
        String role,
        List<Map<String, Object>> parts,
        Map<String, Object> metadata,
        String taskId,
        String contextId
) {

    public static A2aMessage userText(String text, Map<String, Object> metadata) {
        return new A2aMessage("user", List.of(Map.of("text", text)),
                metadata == null ? Map.of() : metadata, null, null);
    }

    public static A2aMessage agentText(String text, Map<String, Object> metadata) {
        return new A2aMessage("agent", List.of(Map.of("text", text)),
                metadata == null ? Map.of() : metadata, null, null);
    }

    /** 提取首段文本，无文本段返回空串 */
    public String firstText() {
        if (parts == null || parts.isEmpty()) {
            return "";
        }
        Object text = parts.get(0).get("text");
        return text == null ? "" : String.valueOf(text);
    }

    /** 提取 metadata.skillId（A2A 调用方指定要触发的 MCP 工具） */
    public String skillId() {
        if (metadata == null) {
            return null;
        }
        Object skillId = metadata.get("skillId");
        return skillId == null ? null : String.valueOf(skillId);
    }

    /** 提取 metadata.arguments（调用 MCP 工具的参数 Map） */
    @SuppressWarnings("unchecked")
    public Map<String, Object> arguments() {
        if (metadata == null) {
            return Map.of();
        }
        Object args = metadata.get("arguments");
        if (args instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }
}