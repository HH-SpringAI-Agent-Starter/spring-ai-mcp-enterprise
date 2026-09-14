package com.mcp.enterprise.gateway;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * V1.25 下游 MCP Server 暴露的远程工具描述（从 {@code tools/list} 结果中裁剪）。
 *
 * @param name        远程工具名（未加前缀）
 * @param description 远程工具描述（可能为空）
 * @param inputSchema 远程工具入参 JSON Schema（可能为空）
 */
public record RemoteToolDescriptor(String name, String description, Map<String, Object> inputSchema) {

    /** 从 MCP tools/list 的单个 tool 元素构造；缺字段容错 */
    public static RemoteToolDescriptor from(Map<String, Object> raw) {
        String n = raw.get("name") == null ? "" : String.valueOf(raw.get("name"));
        String d = raw.get("description") == null ? "" : String.valueOf(raw.get("description"));
        @SuppressWarnings("unchecked")
        Map<String, Object> schema = raw.get("inputSchema") instanceof Map<?, ?>
                ? (Map<String, Object>) raw.get("inputSchema")
                : new LinkedHashMap<>();
        return new RemoteToolDescriptor(n, d, schema);
    }
}