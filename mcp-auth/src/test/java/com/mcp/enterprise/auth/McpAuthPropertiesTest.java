package com.mcp.enterprise.auth;

import com.mcp.enterprise.auth.McpAuthProperties.IdentityProvider;
import com.mcp.enterprise.auth.McpAuthProperties.RoleMapping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * McpAuthProperties 单元测试
 */
class McpAuthPropertiesTest {

    private McpAuthProperties properties;

    @BeforeEach
    void setUp() {
        properties = new McpAuthProperties();
    }

    @Test
    void shouldDefaultToApiKeyMode() {
        assertEquals("api-key", properties.getMode());
    }

    @Test
    void shouldDefaultTo60MinExpiration() {
        assertEquals(60, properties.getJwtExpirationMinutes());
    }

    @Test
    void shouldDefaultToSessionTokensEnabled() {
        assertTrue(properties.isSessionTokens());
    }

    @Test
    void shouldSupportOAuth2Config() {
        McpAuthProperties.OAuth2 oauth2 = properties.getOauth2();
        assertNotNull(oauth2);
        oauth2.setIssuerUri("https://keycloak.example.com/realms/mcp");
        oauth2.setClientId("mcp-server");
        oauth2.setClientSecret("secret-123");

        assertEquals("https://keycloak.example.com/realms/mcp", oauth2.getIssuerUri());
        assertEquals("mcp-server", oauth2.getClientId());
        assertEquals("secret-123", oauth2.getClientSecret());
        assertTrue(oauth2.getScopes().contains("openid"));
    }

    @Test
    void shouldSupportIdentityProviders() {
        IdentityProvider keycloak = new IdentityProvider();
        keycloak.setName("keycloak");
        keycloak.setDisplayName("公司 Keycloak");
        keycloak.setIssuerUri("https://sso.example.com/realms/mcp");
        keycloak.setClientId("mcp-server");
        keycloak.setClientSecret("kc-secret");

        properties.getIdentityProviders().add(keycloak);

        assertEquals(1, properties.getIdentityProviders().size());
        assertEquals("keycloak", properties.getIdentityProviders().get(0).getName());
    }

    @Test
    void shouldSupportMultipleIdentityProviders() {
        IdentityProvider okta = new IdentityProvider();
        okta.setName("okta");
        okta.setDisplayName("Okta SSO");

        IdentityProvider azure = new IdentityProvider();
        azure.setName("azure-ad");
        azure.setDisplayName("Azure AD");

        properties.getIdentityProviders().add(okta);
        properties.getIdentityProviders().add(azure);

        assertEquals(2, properties.getIdentityProviders().size());
    }

    @Test
    void shouldHaveDefaultRoleMapping() {
        RoleMapping mapping = properties.getRoleMapping();
        assertTrue(mapping.getAdminRoles().contains("admin"));
        assertTrue(mapping.getUserRoles().contains("user"));
        assertTrue(mapping.getReadOnlyRoles().contains("viewer"));
        assertEquals("ROLE_", mapping.getRolePrefix());
    }

    @Test
    void shouldSupportCustomRoleMapping() {
        RoleMapping mapping = properties.getRoleMapping();
        mapping.setAdminRoles(List.of("super-admin", "org-admin"));
        mapping.setUserRoles(List.of("employee"));
        mapping.setReadOnlyRoles(List.of("guest"));

        assertEquals(2, mapping.getAdminRoles().size());
        assertTrue(mapping.getAdminRoles().contains("super-admin"));
        assertTrue(mapping.getUserRoles().contains("employee"));
    }

    @Test
    void shouldSupportMultipleOAuth2Scopes() {
        properties.getOauth2().setScopes(List.of("openid", "profile", "email", "mcp:tools", "mcp:admin"));
        assertEquals(5, properties.getOauth2().getScopes().size());
        assertTrue(properties.getOauth2().getScopes().contains("mcp:admin"));
    }
}
