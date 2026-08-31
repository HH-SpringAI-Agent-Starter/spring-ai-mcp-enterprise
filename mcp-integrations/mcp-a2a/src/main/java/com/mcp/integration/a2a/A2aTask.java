package com.mcp.integration.a2a;

import java.util.List;
import java.util.Map;

/**
 * A2A Task（任务单元）
 *
 * 状态机：submitted → working → completed | failed | canceled
 * 与 MCP 工具调用一一映射：一次工具调用 = 一个 Task，工具结果进入 artifacts
 */
public record A2aTask(
        String id,
        String status,
        List<A2aMessage> messages,
        List<Map<String, Object>> artifacts,
        Map<String, Object> metadata,
        String error
) {

    public static A2aTask working(String id, A2aMessage userMessage) {
        return new A2aTask(id, "working", List.of(userMessage), List.of(),
                userMessage.metadata(), null);
    }

    public A2aTask completed(List<Map<String, Object>> artifacts) {
        return new A2aTask(id, "completed", messages, artifacts, metadata, null);
    }

    public A2aTask failed(String error) {
        return new A2aTask(id, "failed", messages, artifacts, metadata, error);
    }

    public A2aTask canceled() {
        return new A2aTask(id, "canceled", messages, artifacts, metadata, null);
    }

    /** 追加 agent 消息 */
    public A2aTask withAgentMessage(A2aMessage agentMessage) {
        List<A2aMessage> updated = new java.util.ArrayList<>(messages);
        updated.add(agentMessage);
        return new A2aTask(id, status, List.copyOf(updated), artifacts, metadata, error);
    }
}