package com.mcp.integration.a2a;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V1.18: A2A Signed Agent Card 签名器单元测试
 *
 * 覆盖：JWS 三段结构 / 往返验证 / 篡改检测 / 错误密钥拒绝 / 确定性签名 / 密钥派生兼容（<32 字节补齐）
 */
class A2aAgentCardSignerTest {

    private static final String SECRET = "mcp-a2a-card-signing-secret-2026";
    private static final String KEY_ID = "mcp-a2a-1";

    private A2aAgentCard card() {
        return new A2aAgentCard(
                "MCP Enterprise A2A Gateway",
                "Enterprise MCP tool exposure via A2A",
                "https://mcp.example.com/a2a",
                "1.0.0",
                Map.of("streaming", true, "pushNotifications", false),
                List.of(Map.of("scheme", "oauth2", "tokenUrl", "https://mcp.example.com/oauth2/token")),
                List.of(A2aSkill.of("calculator", "calculator", "Basic arithmetic calculator",
                        List.of("math"), Map.of("type", "object", "properties", Map.of("expression", Map.of("type", "string")))))
        );
    }

    @Test
    void signProducesJwsCompactWithExpectedHeader() throws Exception {
        A2aAgentCardSigner signer = new A2aAgentCardSigner(SECRET, KEY_ID);
        SignedAgentCard signed = signer.sign(card());

        assertEquals("HS256", signed.algorithm());
        assertEquals(KEY_ID, signed.keyId());
        assertNotNull(signed.signedAt(), "signedAt 应输出 ISO-8601 时间戳");

        String[] parts = signed.signature().split("\\.");
        assertEquals(3, parts.length, "JWS Compact 应为三段：header.payload.signature");

        // header: {"alg":"HS256","typ":"JWS","kid":"mcp-a2a-1"}
        String headerJson = new String(java.util.Base64.getUrlDecoder().decode(parts[0]), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(headerJson.contains("\"alg\":\"HS256\""), "header 应声明 HS256: " + headerJson);
        assertTrue(headerJson.contains("\"typ\":\"JWS\""), "header 应声明 typ=JWS: " + headerJson);
        assertTrue(headerJson.contains("\"kid\":\"" + KEY_ID + "\""), "header 应包含 kid: " + headerJson);
    }

    @Test
    void roundTripVerifyPasses() throws Exception {
        A2aAgentCardSigner signer = new A2aAgentCardSigner(SECRET, KEY_ID);
        SignedAgentCard signed = signer.sign(card());

        A2aAgentCardSigner.VerificationResult result = signer.verify(signed.signature());
        assertTrue(result.valid(), "自签名应验证通过: " + result.error());
        assertEquals("HS256", result.algorithm());
        assertEquals(KEY_ID, result.keyId());
        assertNotNull(result.cardJson(), "校验应返回规范化卡片 JSON");

        A2aAgentCard restored = result.toCard();
        assertNotNull(restored);
        assertEquals(card().name(), restored.name());
        assertEquals(card().description(), restored.description());
    }

    @Test
    void tamperedSignatureIsRejected() throws Exception {
        A2aAgentCardSigner signer = new A2aAgentCardSigner(SECRET, KEY_ID);
        SignedAgentCard signed = signer.sign(card());
        String[] parts = signed.signature().split("\\.");
        String tamperedSig = new StringBuilder(parts[2]).reverse().toString(); // 反转签名段
        String tamperedJws = parts[0] + "." + parts[1] + "." + tamperedSig;

        A2aAgentCardSigner.VerificationResult result = signer.verify(tamperedJws);
        assertFalse(result.valid(), "篡改签名应验证失败");
    }

    @Test
    void wrongKeyIsRejected() throws Exception {
        A2aAgentCardSigner signer = new A2aAgentCardSigner(SECRET, KEY_ID);
        SignedAgentCard signed = signer.sign(card());

        // 客户端用错误密钥校验
        A2aAgentCardSigner.VerificationResult result =
                A2aAgentCardSigner.verify(signed.signature(), "wrong-secret-key-for-verification");
        assertFalse(result.valid(), "错误密钥应验证失败");
    }

    @Test
    void staticVerifyWithSecretMatchesInstanceVerify() throws Exception {
        A2aAgentCardSigner signer = new A2aAgentCardSigner(SECRET, KEY_ID);
        SignedAgentCard signed = signer.sign(card());

        A2aAgentCardSigner.VerificationResult viaSecret = A2aAgentCardSigner.verify(signed.signature(), SECRET);
        assertTrue(viaSecret.valid());
        assertEquals(signed.keyId(), viaSecret.keyId());
    }

    @Test
    void signingIsDeterministic() throws Exception {
        A2aAgentCardSigner signer = new A2aAgentCardSigner(SECRET, KEY_ID, java.time.Clock.fixed(
                Instant.parse("2026-09-03T13:30:00Z"), java.time.ZoneOffset.UTC));
        String jws1 = signer.sign(card()).signature();
        String jws2 = signer.sign(card()).signature();
        assertEquals(jws1, jws2, "规范化 JSON + 固定时钟下签名应完全一致（跨语言可复现）");
    }

    @Test
    void shortSecretIsPaddedTo32BytesLikeMcpAuth() throws Exception {
        // 与 mcp-auth McpJwtTokenProvider 相同的派生规则：<32 字节补齐到 32 字节
        String shortSecret = "short";
        A2aAgentCardSigner signer = new A2aAgentCardSigner(shortSecret, KEY_ID);
        SignedAgentCard signed = signer.sign(card());
        assertTrue(signer.verify(signed.signature()).valid(), "短密钥补齐后应可自验证");
        assertTrue(A2aAgentCardSigner.verify(signed.signature(), shortSecret).valid(),
                "静态校验同样适用短密钥补齐规则");
    }

    @Test
    void malformedJwsIsRejected() {
        A2aAgentCardSigner signer = new A2aAgentCardSigner(SECRET, KEY_ID);
        assertFalse(signer.verify(null).valid());
        assertFalse(signer.verify("").valid());
        assertFalse(signer.verify("only-two-segments").valid());
        assertFalse(signer.verify("a.b.!invalid-base64").valid());
    }

    @Test
    void algNoneHeaderIsRejected() throws Exception {
        // 构造 alg=none 的伪 JWS：header 非 HS256 必须拒绝
        A2aAgentCardSigner signer = new A2aAgentCardSigner(SECRET, KEY_ID);
        String header = "{\"alg\":\"none\",\"typ\":\"JWS\"}";
        String payload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"name\":\"fake\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String fake = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(header.getBytes(java.nio.charset.StandardCharsets.UTF_8)) + "." + payload + ".";

        A2aAgentCardSigner.VerificationResult result = signer.verify(fake);
        assertFalse(result.valid(), "alg=none 必须被拒绝（防算法混淆攻击）");
    }
}