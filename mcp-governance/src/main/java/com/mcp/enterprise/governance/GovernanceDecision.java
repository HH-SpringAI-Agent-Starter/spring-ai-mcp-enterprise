package com.mcp.enterprise.governance;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * V1.26 治理判定结果。
 *
 * @param outcome       判定结论：ALLOW / REQUIRE_APPROVAL / DENY
 * @param tier          工具风险等级
 * @param approvalId    需要审批时的审批请求 ID（否则 null）
 * @param approvalExpiresAt 审批有效期（否则 null）
 * @param errorCode     拒绝/需审批时的机器可读错误码
 * @param message       人类可读说明
 */
public record GovernanceDecision(Outcome outcome, RiskTier tier, String approvalId,
                                 Instant approvalExpiresAt, String errorCode, String message) {

    public enum Outcome { ALLOW, REQUIRE_APPROVAL, DENY }

    public static GovernanceDecision allow(RiskTier tier) {
        return new GovernanceDecision(Outcome.ALLOW, tier, null, null, null, "allowed");
    }

    public static GovernanceDecision requireApproval(RiskTier tier, ApprovalRequest req) {
        return new GovernanceDecision(Outcome.REQUIRE_APPROVAL, tier, req.getId(), req.getExpiresAt(),
                "approval_required", "工具等级 " + tier.getCode() + " 需人工审批后执行");
    }

    public static GovernanceDecision denied(RiskTier tier, String errorCode, String message) {
        return new GovernanceDecision(Outcome.DENY, tier, null, null, errorCode, message);
    }

    public boolean allowed() {
        return outcome == Outcome.ALLOW;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("outcome", outcome.name());
        m.put("tier", tier == null ? null : tier.getCode());
        if (approvalId != null) {
            m.put("approvalId", approvalId);
            m.put("approvalExpiresAt", approvalExpiresAt == null ? null : approvalExpiresAt.toString());
        }
        if (errorCode != null) {
            m.put("errorCode", errorCode);
        }
        m.put("message", message);
        return m;
    }
}