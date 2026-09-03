package com.mcp.integration.a2a;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

/**
 * V1.18: A2A Signed Agent Card 签名器 / 校验器（A2A v1.2 Signed Agent Card 供应链安全基线）
 *
 * <p>企业部署 A2A 时，Agent Card 是编排器做能力发现与鉴权协商的唯一依据——
 * 若不签名，攻击者可通过 DNS 劫持 / 中间人篡改卡片，把编排器引向恶意工具。
 * A2A v1.2 将 <b>签名 Agent Card</b> 列为供应链安全基线（不签名 = 供应链漏洞）。</p>
 *
 * <p>本实现采用 <b>JWS Compact Serialization（RFC 7515）</b>：
 * <pre>
 *   base64url(header) . base64url(canonicalCardJson) . base64url(HMAC-SHA256(signingInput, key))
 *   header = {"alg":"HS256","typ":"JWS","kid":"&lt;card-key-id&gt;"}
 * </pre>
 *
 * <p><b>密钥派生规则与 mcp-auth 完全一致</b>（{@code McpJwtTokenProvider} / {@code A2aJwtTokenValidator}）：
 * 低于 32 字节的 secret 以零字节补齐到 32 字节（HS256 最小密钥长度）。
 * 配置 {@code mcp.enterprise.a2a.card-signing-key} 与 {@code mcp.auth.jwt-secret} 同值时，
 * 可实现"同一把密钥：mcp-auth 发证 + 网关验签 + Agent Card 签名"的完整闭环。</p>
 *
 * <p><b>规范化（Canonical JSON）</b>：签名前对 Agent Card 做确定性序列化
 * （sorted map keys + 无空白），保证不同语言客户端可复现同一签名输入。</p>
 *
 * <p>客户端校验示例（Java）：{@code A2aAgentCardSigner.verify(jws, secret)}。</p>
 */
public class A2aAgentCardSigner {

    private static final Logger log = LoggerFactory.getLogger(A2aAgentCardSigner.class);

    /** JWS 签名算法（A2A v1.2 建议的对称签名基线） */
    public static final String ALG_HS256 = "HS256";
    public static final String HEADER_TYP = "JWS";
    public static final String DEFAULT_KEY_ID = "mcp-a2a-1";

    /** 确定性 JSON 序列化器：sorted map keys（跨语言可复现） */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    private final SecretKey signingKey;
    private final String keyId;
    private final Clock clock;

    public A2aAgentCardSigner(String secret, String keyId) {
        this(secret, keyId, Clock.systemUTC());
    }

    public A2aAgentCardSigner(String secret, String keyId, Clock clock) {
        // 密钥派生与 mcp-auth McpJwtTokenProvider / A2aJwtTokenValidator 完全一致（HS256 至少 32 字节）
        byte[] keyBytes = secret.length() < 32
                ? Arrays.copyOf(secret.getBytes(StandardCharsets.UTF_8), 32)
                : secret.getBytes(StandardCharsets.UTF_8);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.keyId = (keyId == null || keyId.isBlank()) ? DEFAULT_KEY_ID : keyId;
        this.clock = clock;
    }

    /**
     * 对 Agent Card 签名，返回 {@link SignedAgentCard} 信封（agentCard + JWS signature）。
     *
     * @throws com.fasterxml.jackson.core.JsonProcessingException 卡片无法序列化时抛出
     */
    public SignedAgentCard sign(A2aAgentCard card) throws Exception {
        byte[] canonical = MAPPER.writeValueAsBytes(card);
        String payloadB64 = b64(canonical);
        String headerJson = MAPPER.writeValueAsString(
                java.util.Map.of("alg", ALG_HS256, "typ", HEADER_TYP, "kid", keyId));
        String headerB64 = b64(headerJson.getBytes(StandardCharsets.UTF_8));

        String signingInput = headerB64 + "." + payloadB64;
        byte[] mac = hmacSha256(signingInput.getBytes(StandardCharsets.UTF_8), signingKey);
        String jws = signingInput + "." + b64(mac);

        return new SignedAgentCard(card, jws, ALG_HS256, keyId, Instant.now(clock).toString());
    }

    /**
     * 校验 JWS（使用本签名器密钥）。
     *
     * @return 校验结果；{@code valid=true} 时 cardJson 为规范化后的卡片 JSON
     */
    public VerificationResult verify(String jws) {
        return verify(jws, signingKey);
    }

    /**
     * 客户端便捷校验：用明文 secret 派生密钥后校验 JWS（无需构造签名器）。
     * <pre>
     *   A2aAgentCardSigner.VerificationResult r = A2aAgentCardSigner.verify(jws, secret);
     *   if (r.valid()) { A2aAgentCard card = r.toCard(); ... }
     * </pre>
     */
    public static VerificationResult verify(String jws, String secret) {
        byte[] keyBytes = secret.length() < 32
                ? Arrays.copyOf(secret.getBytes(StandardCharsets.UTF_8), 32)
                : secret.getBytes(StandardCharsets.UTF_8);
        return verify(jws, new SecretKeySpec(keyBytes, "HmacSHA256"));
    }

    private static VerificationResult verify(String jws, SecretKey key) {
        if (jws == null || jws.isBlank()) {
            return VerificationResult.invalid("empty signature");
        }
        String[] parts = jws.split("\\.");
        if (parts.length != 3) {
            return VerificationResult.invalid("malformed JWS (expected 3 segments)");
        }
        try {
            // 1) 头部校验：仅接受 HS256 JWS（拒绝 alg=none / 其他算法）
            String decodedHeader = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            String headerAlg = MAPPER.readTree(decodedHeader).path("alg").asText("");
            String headerKid = MAPPER.readTree(decodedHeader).path("kid").asText("");
            if (!ALG_HS256.equals(headerAlg)) {
                return VerificationResult.invalid("unsupported alg: " + headerAlg);
            }

            // 2) 重算签名并常量时间比较
            String signingInput = parts[0] + "." + parts[1];
            byte[] expected = Base64.getUrlDecoder().decode(parts[2]);
            byte[] actual = hmacSha256(signingInput.getBytes(StandardCharsets.UTF_8), key);
            if (!MessageDigest.isEqual(expected, actual)) {
                return VerificationResult.invalid("signature mismatch");
            }

            // 3) 返回规范化卡片 JSON（客户端可反序列化为 A2aAgentCard）
            String cardJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            return new VerificationResult(true, cardJson, headerAlg, headerKid, null);
        } catch (IllegalArgumentException e) {
            return VerificationResult.invalid("invalid base64url: " + e.getMessage());
        } catch (Exception e) {
            log.warn("A2A Agent Card 校验失败: {} ({})", e.getClass().getSimpleName(), e.getMessage());
            return VerificationResult.invalid(e.getMessage());
        }
    }

    private static byte[] hmacSha256(byte[] data, SecretKey key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(key);
        return mac.doFinal(data);
    }

    private static String b64(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    /** JWS 校验结果 */
    public record VerificationResult(boolean valid, String cardJson, String algorithm, String keyId, String error) {

        static VerificationResult invalid(String error) {
            return new VerificationResult(false, null, null, null, error);
        }

        /** 将规范化 JSON 反序列化为 A2A Agent Card；失败返回 null */
        public A2aAgentCard toCard() {
            if (!valid || cardJson == null) {
                return null;
            }
            try {
                return MAPPER.readValue(cardJson, A2aAgentCard.class);
            } catch (Exception e) {
                return null;
            }
        }
    }
}