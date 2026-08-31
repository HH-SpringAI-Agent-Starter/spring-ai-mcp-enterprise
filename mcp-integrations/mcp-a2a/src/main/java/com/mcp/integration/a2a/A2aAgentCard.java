package com.mcp.integration.a2a;

import java.util.List;
import java.util.Map;

/**
 * A2A Agent Card（Agent 元数据，GET /a2a/agent-card）
 *
 * 遵循 A2A 协议 Agent Card 核心字段；capabilities 声明当前网关能力：
 * - streaming: false （当前为同步 JSON-RPC，流式 SSE 为后续版本）
 * - pushNotifications: false
 * - stateTransitionHistory: false
 */
public record A2aAgentCard(
        String name,
        String description,
        String url,
        String version,
        Map<String, Object> capabilities,
        List<A2aSkill> skills
) {
}