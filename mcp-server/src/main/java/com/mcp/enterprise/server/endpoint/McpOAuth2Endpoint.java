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
 *   <li>{@code POST /oauth2/token} —— client_credentials 授权，签发 access_token</li>
 *   <li>{@code GET  /oauth2/introspect} —— RFC 7662 令牌内省，供网关/资源服务器校验</li>
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
     * OAuth2 Token 端点（仅支持 grant_type=client_credentials）。
     * 兼容标准 x-www-form-urlencoded 请求体。
     */
    @PostMapping(value = "/token")
    public ResponseEntity<Map<String, Object>> token(
            @RequestParam("grant_type") String grantType,
            @RequestParam("client_id") String clientId,
            @RequestParam("client_secret") String clientSecret,
            @RequestParam(value = "scope", required = false) String scope) {

        if (!"client_credentials".equals(grantType)) {
            return unsupported("unsupported_grant_type", "Only client_credentials is supported");
        }
        if (!oauth2.isClientEnabled(clientId)) {
            return error(HttpStatus.UNAUTHORIZED, "invalid_client", "Unknown or disabled client");
        }

        Set<String> requestedScopes = null;
        if (scope != null && !scope.isBlank()) {
            requestedScopes = Set.of(scope.trim().split("\\s+"));
        }

        McpOAuth2Manager.TokenResponse resp = oauth2.issueClientCredentialsToken(clientId, clientSecret, requestedScopes);
        if (resp == null) {
            return error(HttpStatus.UNAUTHORIZED, "invalid_client", "Client authentication failed");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("access_token", resp.accessToken());
        body.put("token_type", resp.tokenType());
        body.put("expires_in", resp.expiresIn());
        body.put("scope", String.join(" ", resp.scope()));
        return ResponseEntity.ok(body);
    }

    /** RFC 7662 Token 内省端点（支持 Bearer 头或 query 参数）。 */
    @GetMapping("/introspect")
    public Map<String, Object> introspect(@RequestParam("token") String token) {
        return oauth2.introspect(token);
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
