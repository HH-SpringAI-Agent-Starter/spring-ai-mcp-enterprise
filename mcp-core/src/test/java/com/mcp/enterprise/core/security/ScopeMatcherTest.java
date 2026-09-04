package com.mcp.enterprise.core.security;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ScopeMatcher 通配符匹配单测（V1.19）。
 */
class ScopeMatcherTest {

    @Test
    void exactMatch() {
        assertTrue(ScopeMatcher.matches("tools:finance:read", "tools:finance:read"));
        assertFalse(ScopeMatcher.matches("tools:finance:write", "tools:finance:read"));
    }

    @Test
    void singleSegmentWildcard() {
        // * 仅匹配当前段
        assertTrue(ScopeMatcher.matches("tools:finance:read", "tools:finance:*"));
        assertTrue(ScopeMatcher.matches("tools:finance:admin", "tools:finance:*"));
        assertFalse(ScopeMatcher.matches("tools:database:read", "tools:finance:*"));
        // token 多出段不满足单段通配
        assertFalse(ScopeMatcher.matches("tools:finance:read:extra", "tools:finance:*"));
    }

    @Test
    void multiSegmentWildcard() {
        assertTrue(ScopeMatcher.matches("tools:finance:read", "tools:**"));
        assertTrue(ScopeMatcher.matches("tools:finance:compliance:report", "tools:**"));
        assertFalse(ScopeMatcher.matches("audit:view", "tools:**"));
    }

    @Test
    void matchAll() {
        assertTrue(ScopeMatcher.matches("anything:at:all", "*"));
        assertTrue(ScopeMatcher.matches("tools:finance:read", "*"));
    }

    @Test
    void wildcardMidPattern() {
        // 前缀固定 + 尾段通配
        assertTrue(ScopeMatcher.matches("tools:finance:read", "tools:*:read"));
        assertFalse(ScopeMatcher.matches("tools:database:write", "tools:*:read"));
    }

    @Test
    void edgeCases() {
        assertFalse(ScopeMatcher.matches(null, "tools:*"));
        assertFalse(ScopeMatcher.matches("tools:read", null));
        assertFalse(ScopeMatcher.matches("", "tools:*"));
        assertFalse(ScopeMatcher.matches("tools:read", ""));
        assertFalse(ScopeMatcher.matches("tools:read", "tools:read:more"));
    }

    @Test
    void matchesAnySemantics() {
        // 工具无约束 → 放行
        assertTrue(ScopeMatcher.matchesAny(null, Set.of()));
        assertTrue(ScopeMatcher.matchesAny(Set.of("x"), Set.of()));
        // 有约束但令牌无 scope → 拒绝
        assertFalse(ScopeMatcher.matchesAny(Set.of(), Set.of("tools:finance:*")));
        assertFalse(ScopeMatcher.matchesAny(null, Set.of("tools:finance:*")));
        // 任一命中即满足
        assertTrue(ScopeMatcher.matchesAny(
                Set.of("tools:database:read", "audit:view"),
                Set.of("tools:finance:*", "audit:*")));
        // 全不命中 → 拒绝
        assertFalse(ScopeMatcher.matchesAny(
                Set.of("tools:database:read"),
                Set.of("tools:finance:*", "audit:*")));
    }

    @Test
    void findMatchedScopeReturnsFirstHit() {
        String matched = ScopeMatcher.findMatchedScope(
                Set.of("tools:database:read", "audit:view"),
                Set.of("tools:finance:*", "audit:*"));
        assertEquals("audit:view", matched);
        assertNull(ScopeMatcher.findMatchedScope(Set.of("tools:database:read"), Set.of("tools:finance:*")));
    }
}