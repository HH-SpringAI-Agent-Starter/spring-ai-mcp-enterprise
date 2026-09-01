package com.mcp.integration.a2a;

import java.util.List;
import java.util.Map;

/**
 * A2A Agent Card（Agent 元数据，GET /a2a/agent-card）
 *
 * 遵循 A2A 协议 Agent Card 核心字段：
 * - capabilities 声明当前网关能力（V1.16 起 streaming=true，支持 SSE 流式）
 * - securitySchemes 声明鉴权方案（V1.16 起，mcp-auth 打通第一步）：
 *   - api-key 配置时声明 apiKey scheme（header: X-A2A-Key）
 *   - oauth2 token-url 配置时声明 oauth2 clientCredentials 流（对接 mcp-auth）
 *
 * 字段：
 * - name/description/url/version：Agent 基本信息
 * - capabilities：streaming / pushNotifications / stateTransitionHistory
 * - securitySchemes：A2A 规范鉴权声明数组（空 = 无鉴权）
 * - skills：由 MCP 工具注册中心派生的 A2A Skill 列表
 */
public record A2aAgentCard(
        String name,
        String description,
        String url,
        String version,
        Map<String, Object> capabilities,
        List<Map<String, Object>> securitySchemes,
        List<A2aSkill> skills
) {
}