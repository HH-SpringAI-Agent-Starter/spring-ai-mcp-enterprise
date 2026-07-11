package com.mcp.enterprise.core;

import com.mcp.enterprise.core.security.McpSecurityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * McpSecurityManager 单元测试
 */
class McpSecurityManagerTest {

    private McpSecurityManager securityManager;
    private String adminKey;
    private String userKey;

    @BeforeEach
    void setUp() {
        securityManager = new McpSecurityManager();
        adminKey = securityManager.createApiKey("admin", Set.of("admin", "user"));
        userKey = securityManager.createApiKey("user1", Set.of("user"));
    }

    @Test
    void testCreateAndValidateApiKey() {
        StepVerifier.create(securityManager.validateApiKey(adminKey))
                .expectNext(true)
                .verifyComplete();

        StepVerifier.create(securityManager.validateApiKey("invalid_key"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void testRevokeApiKey() {
        securityManager.revokeApiKey(adminKey);
        StepVerifier.create(securityManager.validateApiKey(adminKey))
                .expectNext(false)
                .verifyComplete();

        assertEquals(1, securityManager.getApiKeyCount()); // only user key remains
    }

    @Test
    void testCheckPermission() {
        // admin has admin+user roles
        StepVerifier.create(securityManager.checkPermission(adminKey, "any_tool", "admin"))
                .expectNext(true)
                .verifyComplete();

        // user only has user role
        StepVerifier.create(securityManager.checkPermission(userKey, "any_tool", "admin"))
                .expectNext(false)
                .verifyComplete();

        StepVerifier.create(securityManager.checkPermission(userKey, "any_tool", "user"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void testCheckPermissionWithNullRole() {
        StepVerifier.create(securityManager.checkPermission(adminKey, "any_tool", null))
                .expectNext(true)
                .verifyComplete();

        StepVerifier.create(securityManager.checkPermission(adminKey, "any_tool", ""))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void testRateLimiter() {
        McpSecurityManager.RateLimiter limiter = new McpSecurityManager.RateLimiter(10);

        // 可以连续获取
        for (int i = 0; i < 10; i++) {
            assertTrue(limiter.tryAcquire(), "第 " + (i + 1) + " 次获取应成功");
        }

        // 超过限制
        assertFalse(limiter.tryAcquire(), "超过限制时应该被拒绝");
    }

    @Test
    void testAuditLog() {
        securityManager.audit(adminKey, "test_tool", "invoke", true, "test invoke");
        securityManager.audit(userKey, "read_tool", "invoke", true, "read data");
        securityManager.audit(adminKey, "admin_tool", "invoke", false, "permission denied");

        List<McpSecurityManager.AuditLogEntry> logs = securityManager.getAuditLog(10);
        assertEquals(3, logs.size());
        assertEquals(3, securityManager.getAuditLogSize());

        // 最新的在最后
        McpSecurityManager.AuditLogEntry last = logs.get(2);
        assertFalse(last.success());
        assertEquals("admin_tool", last.toolName());
    }

    @Test
    void testAuditLogLimit() {
        securityManager.setMaxAuditLogSize(10);
        for (int i = 0; i < 20; i++) {
            securityManager.audit("key", "tool", "test", true, "entry " + i);
        }
        assertTrue(securityManager.getAuditLogSize() <= 10,
                "审计日志不应超过最大限制");
    }

    @Test
    void testIpWhitelist() {
        // 空白名单 = 允许所有
        assertTrue(securityManager.isIpAllowed("192.168.1.1"));

        securityManager.addIpToWhitelist("10.0.0.1");
        assertTrue(securityManager.isIpAllowed("10.0.0.1"));
        assertFalse(securityManager.isIpAllowed("192.168.1.1"));

        securityManager.removeIpFromWhitelist("10.0.0.1");
        assertTrue(securityManager.isIpAllowed("10.0.0.1")); // 回到空=全允许
    }
}
