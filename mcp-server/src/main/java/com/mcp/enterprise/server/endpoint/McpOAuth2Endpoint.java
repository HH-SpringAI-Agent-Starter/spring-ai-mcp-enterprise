package com.mcp.enterprise.server.endpoint;

import com.mcp.enterprise.core.security.McpOAuth2Manager;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * MCP OAuth 2.0 端点（machine-to-machine 凭证）。
 *
 * <p>为企业 AI Agent / 系统间调用提供短时令牌，替代长期共享的 API Key：</p>
 * <ul>
 *   <li>{@code POST /oauth2/token} —— client_credentials 授权，签发 access_token + refresh_token（轮换制）</li>
 *   <li>{@code GET  /oauth2/introspect} —— RFC 7662 令牌内省，供网关/资源服务器校验</li>
 *   <li>{@code POST /oauth2/revoke} —— RFC 7009 令牌吊销（access_token / refresh_token）</li>
 * </ul>
 *
 * <p>令牌校验通过 {@link McpOAuth2Manager#validateToken} 完成，可无缝委托给企业 IdP（EMA）。</p>
 */
@RestController
@RequestMapping("/oauth2")
public class McpOAuth2Endpoint {

    private final McpOAuth2Manager oauth2;

    public McpOAuth2Endpoint(McpOAuth2Manager oauth2) {
        this.oauth2 = oauth2;
    }

    /**
     * OAuth2 Token 端点。
     * 支持 {@code grant_type=client_credentials}（M2M 签发）与 {@code grant_type=refresh_token}（轮换换发）。
     * 兼容标准 x-www-form-urlencoded 请求体。
     */
    @PostMapping(value = "/token")
    public ResponseEntity<Map<String, Object>> token(
            @RequestParam("grant_type") String grantType,
            @RequestParam("client_id") String clientId,
            @RequestParam("client_secret") String clientSecret,
            @RequestParam(value = "scope", required = false) String scope,
            @RequestParam(value = "refresh_token", required = false) String refreshToken) {

        if (!oauth2.isClientEnabled(clientId)) {
            return error(HttpStatus.UNAUTHORIZED, "invalid_client", "Unknown or disabled client");
        }

        if ("refresh_token".equals(grantType)) {
            if (refreshToken == null || refreshToken.isBlank()) {
                return error(HttpStatus.BAD_REQUEST, "invalid_request", "refresh_token is required");
            }
            McpOAuth2Manager.TokenResponse refreshed =
                    oauth2.refreshClientCredentialsToken(clientId, clientSecret, refreshToken);
            if (refreshed == null) {
                return error(HttpStatus.UNAUTHORIZED, "invalid_grant", "Refresh token invalid, expired, or reused (family revoked)");
            }
            return ok(refreshed);
        }

        if (!"client_credentials".equals(grantType)) {
            return unsupported("unsupported_grant_type", "Supported: client_credentials, refresh_token");
        }

        Set<String> requestedScopes = null;
        if (scope != null && !scope.isBlank()) {
            requestedScopes = Set.of(scope.trim().split("\\s+"));
        }

        McpOAuth2Manager.TokenResponse resp = oauth2.issueClientCredentialsToken(clientId, clientSecret, requestedScopes);
        if (resp == null) {
            return error(HttpStatus.UNAUTHORIZED, "invalid_client", "Client authentication failed");
        }
        return ok(resp);
    }

    private ResponseEntity<Map<String, Object>> ok(McpOAuth2Manager.TokenResponse resp) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("access_token", resp.accessToken());
        body.put("token_type", resp.tokenType());
        body.put("expires_in", resp.expiresIn());
        body.put("scope", String.join(" ", resp.scope()));
        if (resp.refreshToken() != null && !resp.refreshToken().isBlank()) {
            body.put("refresh_token", resp.refreshToken());
        }
        return ResponseEntity.ok(body);
    }

    /** RFC 7662 Token 内省端点（支持 Bearer 头或 query 参数）。 */
    @GetMapping("/introspect")
    public Map<String, Object> introspect(@RequestParam("token") String token) {
        return oauth2.introspect(token);
    }

    /** RFC 7009 Token 吊销端点：可吊销 access_token 或 refresh_token。 */
    @PostMapping("/revoke")
    public ResponseEntity<Map<String, Object>> revoke(
            @RequestParam("token") String token,
            @RequestParam(value = "token_type_hint", required = false) String tokenTypeHint) {
        if (token == null || token.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "invalid_request", "token is required");
        }
        boolean revoked = "refresh_token".equals(tokenTypeHint)
                ? oauth2.revokeRefreshToken(token)
                : (oauth2.revokeToken(token) || oauth2.revokeRefreshToken(token));
        // RFC 7009：无论 token 是否有效都应返回 200，避免泄露令牌状态
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("revoked", Boolean.TRUE);
        return ResponseEntity.ok(body);
    }

    // ===== 客户端管理（运维/后台） =====

    @PostMapping("/clients")
    public Map<String, Object> registerClient(@RequestParam String clientId,
                                              @RequestParam(defaultValue = "oauth2-client") String owner,
                                              @RequestParam(defaultValue = "user") String roles,
                                              @RequestParam(defaultValue = "tools:read tools:call") String scopes) {
        McpOAuth2Manager.ClientRegistration reg = oauth2.registerClient(
                clientId, owner, Set.of(roles.trim().split("\\s+")), Set.of(scopes.trim().split("\\s+")));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("client_id", reg.clientId());
        body.put("client_secret", reg.clientSecret()); // 仅此一次明文返回，请妥善保存
        body.put("note", "client_secret 仅此一次明文展示，服务端仅存散列");
        return body;
    }

    @DeleteMapping("/clients/{clientId}")
    public Map<String, Object> revokeClient(@PathVariable String clientId) {
        oauth2.revokeClient(clientId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("client_id", clientId);
        body.put("revoked", true);
        return body;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("active_clients", oauth2.getClientCount());
        body.put("token_ttl_seconds", oauth2.getTokenTtlSeconds());
        body.put("external_ema_idp", oauth2.hasExternalIntrospector());
        return body;
    }

    // ===== helpers =====

    private ResponseEntity<Map<String, Object>> unsupported(String code, String desc) {
        return error(HttpStatus.BAD_REQUEST, code, desc);
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String desc) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", code);
        body.put("error_description", desc);
        return ResponseEntity.status(status).body(body);
    }
}
