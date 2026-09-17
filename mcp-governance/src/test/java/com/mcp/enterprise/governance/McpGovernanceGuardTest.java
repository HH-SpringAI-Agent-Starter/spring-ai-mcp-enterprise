package com.mcp.enterprise.governance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V1.26 治理判定核心测试：deny 硬拒 / 需审批 / Fail-closed / 放行。
 */
class McpGovernanceGuardTest {

    private McpGovernanceProperties props;
    private McpApprovalService approvalService;
    private McpGovernanceGuard guard;

    @BeforeEach
    void setUp() {
        props = new McpGovernanceProperties();
        props.setEnforce(true);
        InMemoryApprovalStore store = new InMemoryApprovalStore(100);
        SensitiveDataRedactor redactor = new SensitiveDataRedactor(props);
        approvalService = new McpApprovalService(store, redactor, props);
        guard = new McpGovernanceGuard(props, new McpToolRiskClassifier(props), approvalService);
    }

    @Test
    @DisplayName("低等级工具直接放行")
    void lowTierAllowed() {
        GovernanceDecision d = guard.evaluate("weather_get", "alice", Map.of(), null);
        assertThat(d.allowed()).isTrue();
        assertThat(d.outcome()).isEqualTo(GovernanceDecision.Outcome.ALLOW);
    }

    @Test
    @DisplayName("T3/T4 无令牌 → REQUIRE_APPROVAL 并产出 approvalId")
    void highTierRequiresApproval() {
        GovernanceDecision d = guard.evaluate("user_create", "alice", Map.of("name", "x"), null);
        assertThat(d.outcome()).isEqualTo(GovernanceDecision.Outcome.REQUIRE_APPROVAL);
        assertThat(d.approvalId()).isNotBlank();
        assertThat(d.errorCode()).isEqualTo("approval_required");
        assertThat(d.tier()).isEqualTo(RiskTier.T3);
    }

    @Test
    @DisplayName("完整闭环：创建 → 批准 → 带令牌重试放行 → 再次调用需要新审批")
    void fullApprovalRoundTrip() {
        GovernanceDecision first = guard.evaluate("db_delete_all", "alice", Map.of(), null);
        assertThat(first.outcome()).isEqualTo(GovernanceDecision.Outcome.REQUIRE_APPROVAL);

        approvalService.approve(first.approvalId(), "admin", "ok");

        GovernanceDecision second = guard.evaluate("db_delete_all", "alice", Map.of(), first.approvalId());
        assertThat(second.allowed()).isTrue();

        // 令牌已一次性消费：第三次无令牌 → 需要新审批
        GovernanceDecision third = guard.evaluate("db_delete_all", "alice", Map.of(), null);
        assertThat(third.outcome()).isEqualTo(GovernanceDecision.Outcome.REQUIRE_APPROVAL);
        assertThat(third.approvalId()).isNotEqualTo(first.approvalId());
    }

    @Test
    @DisplayName("deny-tiers 硬闸门：T4 工具被拒绝")
    void denyTierRejected() {
        props.setDenyTiers(java.util.List.of("T4"));
        McpGovernanceGuard hardGuard = new McpGovernanceGuard(props,
                new McpToolRiskClassifier(props), approvalService);

        GovernanceDecision d = hardGuard.evaluate("db_delete_all", "alice", Map.of(), null);
        assertThat(d.outcome()).isEqualTo(GovernanceDecision.Outcome.DENY);
        assertThat(d.errorCode()).isEqualTo("governance_denied");
    }

    @Test
    @DisplayName("审批服务关闭 + 需审批等级 → Fail-closed 拒绝")
    void approvalDisabledFailsClosed() {
        // enabled 在服务构造时读取，需先改配置再重建服务
        props.getApproval().setEnabled(false);
        InMemoryApprovalStore freshStore = new InMemoryApprovalStore(100);
        McpApprovalService disabledService = new McpApprovalService(freshStore,
                new SensitiveDataRedactor(props), props);
        McpGovernanceGuard closedGuard = new McpGovernanceGuard(props,
                new McpToolRiskClassifier(props), disabledService);

        GovernanceDecision d = closedGuard.evaluate("user_create", "alice", Map.of(), null);
        assertThat(d.outcome()).isEqualTo(GovernanceDecision.Outcome.DENY);
        assertThat(d.errorCode()).isEqualTo("approval_unavailable");
    }

    @Test
    @DisplayName("模块关闭（enabled=false）→ 全部放行（向后兼容）")
    void disabledModuleAllowsAll() {
        props.setEnabled(false);
        McpGovernanceGuard offGuard = new McpGovernanceGuard(props,
                new McpToolRiskClassifier(props), approvalService);

        assertThat(offGuard.evaluate("db_delete_all", "alice", Map.of(), null).allowed()).isTrue();
        assertThat(offGuard.evaluate("pay_money", "alice", Map.of(), null).allowed()).isTrue();
    }
}