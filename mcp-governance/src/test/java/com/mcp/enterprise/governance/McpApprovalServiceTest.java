package com.mcp.enterprise.governance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V1.26 审批服务测试：创建 / 批准 / 拒绝 / 一次性消费 / 过期 / 非法流转。
 */
class McpApprovalServiceTest {

    private McpGovernanceProperties props;
    private InMemoryApprovalStore store;
    private McpApprovalService service;
    private SensitiveDataRedactor redactor;

    @BeforeEach
    void setUp() {
        props = new McpGovernanceProperties();
        store = new InMemoryApprovalStore(100);
        redactor = new SensitiveDataRedactor(props);
        service = new McpApprovalService(store, redactor, props);
    }

    @Test
    @DisplayName("创建审批：参数脱敏入库，状态 PENDING")
    void createRedactsAndStores() {
        ApprovalRequest req = service.requestApproval("finance_transfer", RiskTier.T4,
                Map.of("to", "acct_123", "email", "victim@corp.com", "phone", "13800138000"),
                "alice", "大额转账");

        assertThat(req.getStatus()).isEqualTo(ApprovalRequest.Status.PENDING);
        assertThat(req.getArguments().get("to")).isEqualTo("acct_123");
        // 脱敏生效：邮箱/手机全部打码
        String email = (String) req.getArguments().get("email");
        String phone = (String) req.getArguments().get("phone");
        assertThat(email).doesNotContain("victim");
        assertThat(phone).doesNotContain("138001");
        assertThat(service.get(req.getId())).isNotNull();
    }

    @Test
    @DisplayName("批准后消费一次即 CONSUMED，重复消费被拒")
    void consumeOnceThenRejected() {
        ApprovalRequest req = service.requestApproval("db_delete", RiskTier.T4,
                Map.of("table", "users"), "alice", "清库");
        service.approve(req.getId(), "admin", "确认");

        assertThat(service.consume(req.getId(), "db_delete", "alice"))
                .isEqualTo(McpApprovalService.Validation.OK);
        assertThat(req.getStatus()).isEqualTo(ApprovalRequest.Status.CONSUMED);

        // 二次消费：不再是 APPROVED
        assertThat(service.consume(req.getId(), "db_delete", "alice"))
                .isEqualTo(McpApprovalService.Validation.NOT_APPROVED);
    }

    @Test
    @DisplayName("工具不一致 / 调用方不一致 → 校验失败且不消费")
    void mismatchRejected() {
        ApprovalRequest req = service.requestApproval("db_delete", RiskTier.T4,
                Map.of(), "alice", "清库");
        service.approve(req.getId(), "admin", "ok");

        assertThat(service.consume(req.getId(), "OTHER_TOOL", "alice"))
                .isEqualTo(McpApprovalService.Validation.TOOL_MISMATCH);
        assertThat(service.consume(req.getId(), "db_delete", "bob"))
                .isEqualTo(McpApprovalService.Validation.CALLER_MISMATCH);
        // 仍然 APPROVED（未被消费）
        assertThat(req.getStatus()).isEqualTo(ApprovalRequest.Status.APPROVED);
    }

    @Test
    @DisplayName("拒绝后不可消费")
    void rejectBlocksConsume() {
        ApprovalRequest req = service.requestApproval("pay", RiskTier.T3, Map.of(), "alice", "付款");
        service.reject(req.getId(), "admin", "预算不足");

        assertThat(service.consume(req.getId(), "pay", "alice"))
                .isEqualTo(McpApprovalService.Validation.NOT_APPROVED);
        assertThat(req.getStatus()).isEqualTo(ApprovalRequest.Status.REJECTED);
    }

    @Test
    @DisplayName("非法状态流转抛出（对已批准记录再批准）")
    void illegalTransitionThrows() {
        ApprovalRequest req = service.requestApproval("pay", RiskTier.T3, Map.of(), "alice", null);
        service.approve(req.getId(), "admin", "ok");

        assertThatThrownBy(() -> service.approve(req.getId(), "another", "again"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("过期 PENDING 自动标记 EXPIRED，不可消费")
    void expiredRejected() {
        // TTL 在服务构造时读取，需先改配置再重建服务
        props.getApproval().setTtlSeconds(1);
        service = new McpApprovalService(store, redactor, props);
        ApprovalRequest req = service.requestApproval("pay", RiskTier.T3, Map.of(), "alice", null);

        try {
            Thread.sleep(1100);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        assertThat(service.consume(req.getId(), "pay", "alice"))
                .isEqualTo(McpApprovalService.Validation.EXPIRED);
        assertThat(req.getStatus()).isEqualTo(ApprovalRequest.Status.EXPIRED);
    }

    @Test
    @DisplayName("sweep 清理过期请求并返回数量")
    void sweepCleansExpired() {
        props.getApproval().setTtlSeconds(1);
        service = new McpApprovalService(store, redactor, props);
        service.requestApproval("a", RiskTier.T3, Map.of(), "u", null);
        service.requestApproval("b", RiskTier.T3, Map.of(), "u", null);

        try {
            Thread.sleep(1100);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        assertThat(service.sweep()).isEqualTo(2);
        assertThat(service.list(ApprovalRequest.Status.EXPIRED)).hasSize(2);
    }

    @Test
    @DisplayName("存储有界：超过上限逐出最旧记录")
    void storeBounded() {
        InMemoryApprovalStore bounded = new InMemoryApprovalStore(3);
        for (int i = 0; i < 10; i++) {
            bounded.save(new ApprovalRequest("t", RiskTier.T2, Map.of(), "u", null, 900));
        }
        assertThat(bounded.count()).isEqualTo(3);
    }

    @Test
    @DisplayName("审批关闭时 requestApproval 仍可用但 Guard 应 Fail-closed（此处验证 enabled 标志）")
    void disabledFlagExposed() {
        props.getApproval().setEnabled(false);
        McpApprovalService disabled = new McpApprovalService(store, redactor, props);
        assertThat(disabled.isEnabled()).isFalse();
    }
}