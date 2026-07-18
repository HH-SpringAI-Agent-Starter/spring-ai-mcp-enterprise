package com.mcp.enterprise.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * McpAuthController 单元测试
 */
class McpAuthControllerTest {

    private McpAuthProperties properties;
    private McpJwtTokenProvider tokenProvider;
    private McpAuthController controller;

    @BeforeEach
    void setUp() {
        properties = new McpAuthProperties();
        properties.setJwtSecret("test-secret-key-must-be-at-least-32-chars-long!!");
        properties.setJwtExpirationMinutes(60);
        tokenProvider = new McpJwtTokenProvider(properties);
        controller = new McpAuthController(properties, tokenProvider);
    }

    @Test
    void loginPageShouldReturnAuthMode() {
        Map<String, Object> page = controller.loginPage();
        assertEquals("api-key", page.get("authMode"));
        assertNotNull(page.get("supportedAuthMethods"));
    }

    @Test
    void loginPageWithOAuth2() {
        properties.setMode("oauth2");
        McpAuthProperties.IdentityProvider idp = new McpAuthProperties.IdentityProvider();
        idp.setName("keycloak");
        idp.setDisplayName("Keycloak SSO");
        properties.getIdentityProviders().add(idp);

        Map<String, Object> page = controller.loginPage();
        assertEquals("oauth2", page.get("authMode"));
        assertNotNull(page.get("loginUrl"));
        assertNotNull(page.get("providers"));
    }

    @Test
    void exchangeApiKeyShouldReturnToken() {
        Map<String, Object> result = controller.exchangeApiKey("test-api-key-123");

        assertTrue((Boolean) result.get("success"));
        assertNotNull(result.get("token"));
        assertEquals("Bearer", result.get("tokenType"));
        assertTrue(((Long) result.get("expiresIn")) > 0);
    }

    @Test
    void exchangeApiKeyTokenShouldBeValid() {
        Map<String, Object> result = controller.exchangeApiKey("api-key-test");

        String token = (String) result.get("token");
        var validation = tokenProvider.validateToken(token);
        assertTrue(validation.valid());
        assertTrue(validation.roles().contains("ROLE_USER"));
    }

    @Test
    void verifyTokenShouldReturnValidForGoodToken() {
        String token = tokenProvider.generateToken("test-user", Set.of("ROLE_USER"), Map.of());

        Map<String, Object> result = controller.verifyToken("Bearer " + token);
        assertTrue((Boolean) result.get("valid"));
        assertEquals("test-user", result.get("subject"));
    }

    @Test
    void verifyTokenShouldReturnInvalidForBadToken() {
        Map<String, Object> result = controller.verifyToken("Bearer invalid-token");
        assertFalse((Boolean) result.get("valid"));
        assertNotNull(result.get("error"));
    }

    @Test
    void verifyTokenShouldHandleNonBearerToken() {
        Map<String, Object> result = controller.verifyToken("raw-token");
        assertFalse((Boolean) result.get("valid"));
    }

    @Test
    void listProvidersShouldReturnEmptyByDefault() {
        var providers = controller.listProviders();
        assertNotNull(providers);
        assertTrue(providers.isEmpty());
    }

    @Test
    void listProvidersShouldReturnConfiguredProviders() {
        McpAuthProperties.IdentityProvider idp = new McpAuthProperties.IdentityProvider();
        idp.setName("okta");
        idp.setDisplayName("Okta SSO");
        idp.setIssuerUri("https://okta.example.com");
        properties.getIdentityProviders().add(idp);

        var providers = controller.listProviders();
        assertEquals(1, providers.size());
        assertEquals("okta", providers.get(0).get("name"));
    }
}
