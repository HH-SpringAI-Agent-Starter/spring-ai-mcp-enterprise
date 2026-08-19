package com.mcp.enterprise.core.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 网关限流路由表单元测试
 */
class GatewayRateLimitManagerTest {

    private GatewayRateLimitManager manager;

    @BeforeEach
    void setUp() {
        manager = new GatewayRateLimitManager();
    }

    @Test
    void testAddAndListRules() {
        manager.addRule("tools/call", "*", 100);
        manager.addRule("tools/call", "greet", 10);
        manager.addRule("tools/list", "", 5);

        var snapshot = manager.getRuleSnapshot();
        assertEquals(3, snapshot.size());
        assertTrue(snapshot.stream().anyMatch(r -> "tools/call".equals(r.get("method")) && "*".equals(r.get("name"))));
    }

    @Test
    void testExactMatchPriority() {
        manager.addRule("tools/call", "*", 100);
        manager.addRule("tools/call", "greet", 10);

        // greet 精确匹配 10QPS，其他调用走 100QPS
        // 多次调用 greet：前10次通过，第11次开始被限流
        for (int i = 0; i < 10; i++) {
            assertTrue(manager.checkRateLimit("tools/call", "greet"),
                    "第 " + (i + 1) + " 次调用应该放行");
        }
        assertFalse(manager.checkRateLimit("tools/call", "greet"),
                "第11次调用应该限流");
    }

    @Test
    void testWildcardPrefixMatch() {
        manager.addRule("tools/call", "finance_*", 5);

        // finance_greet 匹配 finance_* 模式
        for (int i = 0; i < 5; i++) {
            assertTrue(manager.checkRateLimit("tools/call", "finance_greet"),
                    "finance_greet 第 " + (i + 1) + " 次应放行");
        }
        assertFalse(manager.checkRateLimit("tools/call", "finance_greet"));
    }

    @Test
    void testNoRuleDefaultsToAllow() {
        manager.addRule("tools/call", "greet", 1);
        // greet 限流，但 greet2 没有规则 → 放行
        assertTrue(manager.checkRateLimit("tools/call", "greet2"));
    }

    @Test
    void testNullMethodNameDefaultsToUnknown() {
        manager.addRule("tools/call", "*", 1);
        // null method → unknown，放行（因为没有 unknown 规则）
        assertTrue(manager.checkRateLimit(null, null));
        assertTrue(manager.checkRateLimit(null, "greet"));
    }

    @Test
    void testDisabledManagerAllowsAll() {
        manager.addRule("tools/call", "*", 1);
        manager.setEnabled(false);
        for (int i = 0; i < 100; i++) {
            assertTrue(manager.checkRateLimit("tools/call", "greet"));
        }
    }

    @Test
    void testRemoveRule() {
        manager.addRule("tools/call", "greet", 10);
        assertTrue(manager.removeRule("tools/call", "greet"));
        assertFalse(manager.removeRule("tools/call", "greet"));
        assertEquals(0, manager.getRuleCount());
    }

    @Test
    void testClearRules() {
        manager.addRule("tools/call", "*", 100);
        manager.addRule("tools/list", "", 5);
        manager.clearRules();
        assertEquals(0, manager.getRuleCount());
        assertTrue(manager.checkRateLimit("tools/call", "anything"));
    }

    @Test
    void testRuleReplacedOnDuplicate() {
        manager.addRule("tools/call", "*", 100);
        manager.addRule("tools/call", "*", 200); // 替换
        var snapshot = manager.getRuleSnapshot();
        assertEquals(1, snapshot.size());
        assertEquals(200, snapshot.get(0).get("maxPerSecond"));
    }

    @Test
    void testMaxRulesLimit() {
        // 快速添加 MAX_RULES 条规则（256），再添加应被拒绝
        int maxRules = 256;
        for (int i = 0; i < maxRules; i++) {
            manager.addRule("method" + i, "*", 10);
        }
        assertEquals(maxRules, manager.getRuleCount());
        // 第257条应被拒绝（不抛异常）
        assertDoesNotThrow(() -> manager.addRule("overflow", "*", 10));
        assertEquals(maxRules, manager.getRuleCount());
    }

    @Test
    void testInvalidMaxPerSecondRejected() {
        assertThrows(IllegalArgumentException.class, () -> manager.addRule("tools/call", "*", 0));
        assertThrows(IllegalArgumentException.class, () -> manager.addRule("tools/call", "*", -1));
    }

    @Test
    void testMatchScore() {
        var rule = new GatewayRateLimitManager.RateLimitRule("tools/call", "greet", 10);
        assertEquals(30, rule.matchScore("tools/call", "greet"));
        assertEquals(-1, rule.matchScore("tools/call", "finance_greet"));
        assertEquals(-1, rule.matchScore("tools/call", "anything"));
        assertEquals(-1, rule.matchScore("tools/list", "greet"));

        var wildcardRule = new GatewayRateLimitManager.RateLimitRule("tools/call", "finance_*", 10);
        assertEquals(20, wildcardRule.matchScore("tools/call", "finance_greet"));
        assertEquals(-1, wildcardRule.matchScore("tools/call", "greet"));

        var allRule = new GatewayRateLimitManager.RateLimitRule("tools/call", "*", 10);
        assertEquals(10, allRule.matchScore("tools/call", "anything"));
        assertEquals(10, allRule.matchScore("tools/call", ""));
    }

    @Test
    void testEmptyNameRuleOnlyMatchesEmptyName() {
        manager.addRule("ping", "", 20);

        // 空 name 命中规则（20 QPS，但只调用一次，放行）
        assertTrue(manager.checkRateLimit("ping", ""));
        // 非空 name 不匹配该规则 → 无规则命中 → 放行
        assertTrue(manager.checkRateLimit("ping", "someName"));

        // 验证规则确实存在且生效：耗尽空 name 的配额
        for (int i = 0; i < 19; i++) {
            manager.checkRateLimit("ping", "");
        }
        assertFalse(manager.checkRateLimit("ping", ""), "空 name 超过配额应被限流");
        // 但非空 name 仍不受影响
        assertTrue(manager.checkRateLimit("ping", "someName"));
    }

    @Test
    void testRuleSnapshotFormat() {
        manager.addRule("tools/call", "greet", 10);
        List<Map<String, Object>> snapshot = manager.getRuleSnapshot();

        assertEquals(1, snapshot.size());
        assertEquals("tools/call", snapshot.get(0).get("method"));
        assertEquals("greet", snapshot.get(0).get("name"));
        assertEquals(10, snapshot.get(0).get("maxPerSecond"));
    }
}
