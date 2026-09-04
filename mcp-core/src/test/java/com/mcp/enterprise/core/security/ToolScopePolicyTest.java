package com.mcp.enterprise.core.security;

import com.mcp.enterprise.core.model.ToolDefinition;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolScopePolicy 授权决策单测（V1.19）。
 *
 * 覆盖：策略开关、解析优先级（显式声明 &gt; 工具名覆盖 &gt; 分类兜底）、
 * 通配匹配放行/拒绝、无令牌上下文、审计友好字段。
 */
class ToolScopePolicyTest {

    private ToolDefinition tool(String name, String category, String requiredScopes) {
        ToolDefinition def = new ToolDefinition();
        def.setName(name);
        def.setCategory(category);
        def.setRequiredScopes(requiredScopes);
        return def;
    }

    @Test
    void disabledPolicyAlwaysAllows() {
        ToolScopePolicy policy = new ToolScopePolicy(false, Map.of(), Map.of());
        ToolDefinition def = tool("finance_indicator", "finance", "tools:finance:*");
        ToolScopePolicy.ScopeDecision decision = policy.authorize(Set.of(), def);
        assertTrue(decision.allowed());
        // 禁用时即使令牌无 scope 也放行（与 V1.18 行为一致）
        assertTrue(policy.authorize(Set.of("unrelated:scope"), def).allowed());
        // 解析独立于开关：scope 声明始终可被 tools/list 展示
        assertEquals(Set.of("tools:finance:*"), policy.resolveRequiredScopes(def));
    }

    @Test
    void explicitDeclarationTakesPriority() {
        // 显式声明 tools:finance:read；分类兜底 tools:finance:* 不生效
        ToolScopePolicy policy = new ToolScopePolicy(true, Map.of(), Map.of("finance", "tools:finance:*"));
        ToolDefinition def = tool("finance_indicator", "finance", "tools:finance:read");
        assertEquals(Set.of("tools:finance:read"), policy.resolveRequiredScopes(def));

        assertTrue(policy.authorize(Set.of("tools:finance:read"), def).allowed());
        assertFalse(policy.authorize(Set.of("tools:finance:write"), def).allowed());
    }

    @Test
    void toolOverrideWinsOverCategoryDefault() {
        ToolScopePolicy policy = new ToolScopePolicy(true,
                Map.of("finance_indicator", "tools:finance:write"),
                Map.of("finance", "tools:finance:*"));
        ToolDefinition def = tool("finance_indicator", "finance", null);
        assertEquals(Set.of("tools:finance:write"), policy.resolveRequiredScopes(def));
        assertTrue(policy.authorize(Set.of("tools:finance:write"), def).allowed());
        // 覆盖为精确 scope（无通配）→ 其他动作拒绝
        assertFalse(policy.authorize(Set.of("tools:finance:read"), def).allowed());
        assertFalse(policy.authorize(Set.of("tools:audit:view"), def).allowed());
    }

    @Test
    void categoryDefaultAsFallback() {
        ToolScopePolicy policy = new ToolScopePolicy(true, Map.of(), Map.of("database", "tools:database:*"));
        ToolDefinition def = tool("db_query", "database", null);
        assertEquals(Set.of("tools:database:*"), policy.resolveRequiredScopes(def));
        assertTrue(policy.authorize(Set.of("tools:database:read"), def).allowed());
        assertFalse(policy.authorize(Set.of("tools:finance:read"), def).allowed());
    }

    @Test
    void noConstraintAllows() {
        ToolScopePolicy policy = new ToolScopePolicy(true, Map.of(), Map.of());
        ToolDefinition def = tool("calculator", "ai", null);
        assertTrue(policy.resolveRequiredScopes(def).isEmpty());
        assertTrue(policy.authorize(Set.of(), def).allowed());
        assertTrue(policy.authorize(null, def).allowed());
    }

    @Test
    void denyDecisionCarriesDiagnostics() {
        ToolScopePolicy policy = new ToolScopePolicy(true, Map.of(), Map.of("finance", "tools:finance:*"));
        ToolDefinition def = tool("finance_risk", "finance", null);
        ToolScopePolicy.ScopeDecision decision = policy.authorize(Set.of("tools:audit:view"), def);
        assertFalse(decision.allowed());
        assertEquals(Set.of("tools:finance:*"), decision.requiredScopes());
        assertEquals(Set.of("tools:audit:view"), decision.tokenScopes());
        assertNull(decision.matchedScope());
    }

    @Test
    void allowDecisionRecordsMatchedScope() {
        ToolScopePolicy policy = new ToolScopePolicy(true, Map.of(), Map.of("finance", "tools:finance:*"));
        ToolDefinition def = tool("finance_indicator", "finance", null);
        ToolScopePolicy.ScopeDecision decision = policy.authorize(Set.of("tools:finance:read"), def);
        assertTrue(decision.allowed());
        assertEquals("tools:finance:read", decision.matchedScope());
    }

    @Test
    void scopesResolutionSupportsMultiplePatternsAndSeparators() {
        ToolScopePolicy policy = new ToolScopePolicy(true, Map.of(), Map.of());
        ToolDefinition def = tool("hybrid", "ai", "tools:finance:read,audit:view tools:report:**");
        Set<String> required = policy.resolveRequiredScopes(def);
        assertEquals(Set.of("tools:finance:read", "audit:view", "tools:report:**"), required);

        // 多模式任一命中即放行
        assertTrue(policy.authorize(Set.of("audit:view"), def).allowed());
        assertTrue(policy.authorize(Set.of("tools:report:daily:2026"), def).allowed());
        assertFalse(policy.authorize(Set.of("tools:database:read"), def).allowed());
    }
}