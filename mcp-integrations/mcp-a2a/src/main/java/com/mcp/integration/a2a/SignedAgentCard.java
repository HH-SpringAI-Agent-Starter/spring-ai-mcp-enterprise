package com.mcp.integration.a2a;

/**
 * V1.18: Signed Agent Card 信封（A2A v1.2 签名卡片响应体）
 *
 * <p>当配置 {@code mcp.enterprise.a2a.card-signing-key} 后，
 * {@code GET /a2a/agent-card} 与 {@code GET /.well-known/agent-card.json}
 * 返回本信封而非裸 Agent Card：</p>
 *
 * <pre>
 * {
 *   "agentCard": { "name": "...", "skills": [...] },
 *   "signature": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXUyIsImtpZCI6Im1jcC1hMmEtMSJ9.eyJ... .<sig>",
 *   "algorithm": "HS256",
 *   "keyId": "mcp-a2a-1",
 *   "signedAt": "2026-09-03T13:30:00Z"
 * }
 * </pre>
 *
 * <p>同时响应头携带 {@code X-Agent-Card-Signature}（JWS），兼容纯 header 传递的消费方。</p>
 *
 * @param agentCard 原始 Agent Card（未签名内容，供编排器直接读取）
 * @param signature JWS Compact Serialization（RFC 7515），签名输入为规范化 JSON
 * @param algorithm 签名算法，固定 {@code HS256}
 * @param keyId     密钥标识（轮换 / 多密钥场景下供消费方选 key）
 * @param signedAt  签名时间（ISO-8601 UTC）
 */
public record SignedAgentCard(
        A2aAgentCard agentCard,
        String signature,
        String algorithm,
        String keyId,
        String signedAt
) {
}