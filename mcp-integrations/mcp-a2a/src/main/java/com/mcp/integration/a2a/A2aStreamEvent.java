package com.mcp.integration.a2a;

import java.util.Map;

/**
 * A2A SSE 流式事件（V1.16）
 *
 * 事件名对齐 A2A v1.0 规范：
 * - TaskStatusUpdateEvent
 * - TaskArtifactUpdateEvent
 * - MessageDeliveryEvent
 * - TaskNotFoundEvent
 *
 * data 为事件负载（Map），由订阅方序列化为 SSE data 行。
 */
public record A2aStreamEvent(String event, String taskId, Map<String, Object> data) {
}