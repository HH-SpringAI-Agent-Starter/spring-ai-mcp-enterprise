package com.mcp.enterprise.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OAuth2 Client Credentials 端点测试
 */
class McpOAuth2TokenEndpointTest {

    private McpAuthProperties properties;
    private McpJwtTokenProvider tokenProvider;
    private McpOAuth2TokenEndpoint endpoint;

    @BeforeEach
    void setUp() {
        properties = new McpAuthProperties();
        properties.setJwtSecret("test-secret-key-0123456789abcdef0123456789abcdef");
        properties.setJwtExpirationMinutes(60);
        tokenProvider = new McpJwtTokenProvider(properties);
        endpoint = new McpOAuth2TokenEndpoint(properties, tokenProvider);
    }

    @Test
    void token_defaultAccount_shouldSucceed() {
        ResponseEntity<Map<String, Object>> response = endpoint.token(
                "client_credentials", "mcp-service",
                "change-me-client-secret", "tools:read");

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertTrue(String.valueOf(body.get("access_token")).length() > 20);
        assertEquals("Bearer", body.get("token_type"));
        assertEquals(3600L, body.get("expires_in"));
    }

    @Test
    void token_wrongSecret_shouldFail() {
        ResponseEntity<Map<String, Object>> response = endpoint.token(
                "client_credentials", "mcp-service", "wrong-secret", null);

        assertEquals(401, response.getStatusCode().value());
        assertEquals("invalid_client", response.getBody().get("error"));
    }

    @Test
    void token_unknownClient_shouldFail() {
        ResponseEntity<Map<String, Object>> response = endpoint.token(
                "client_credentials", "unknown-client", "any", null);

        assertEquals(401, response.getStatusCode().value());
        assertEquals("invalid_client", response.getBody().get("error"));
    }

    @Test
    void token_unsupportedGrantType_shouldFail() {
        ResponseEntity<Map<String, Object>> response = endpoint.token(
                "password", "mcp-service", "change-me-client-secret", null);

        assertEquals(401, response.getStatusCode().value());
        assertEquals("unsupported_grant_type", response.getBody().get("error"));
    }

    @Test
    void token_customRegisteredAccount_shouldSucceed() {
        endpoint.registerServiceAccount("erp-integration", "erp-secret-123", Set.of("user"));

        ResponseEntity<Map<String, Object>> response = endpoint.token(
                "client_credentials", "erp-integration", "erp-secret-123", "tools:query");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody().get("access_token"));
        assertFalse(String.valueOf(response.getBody().get("access_token")).isBlank());
    }
}
