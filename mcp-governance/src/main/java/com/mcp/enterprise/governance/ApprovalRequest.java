package com.mcp.enterprise.governance;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * V1.26 人工审批请求（HITL Approval Request）。
 *
 * <p>生命周期：{@code PENDING → APPROVED|REJECTED|EXPIRED}；
 * 已批准令牌被消费一次后置为 {@code CONSUMED}（一次性使用，防重放）。</p>
 */
public class ApprovalRequest {

    /** 审批状态 */
    public enum Status {
        PENDING, APPROVED, REJECTED, EXPIRED, CONSUMED
    }

    private final String id;
    private final String toolName;
    private final String tierCode;
    private final Map<String, Object> arguments;   // 已脱敏
    private final String requestedBy;
    private final String reason;
    private final Instant createdAt;
    private final Instant expiresAt;

    private volatile Status status = Status.PENDING;
    private volatile Instant decidedAt;
    private volatile String decidedBy;
    private volatile String decisionReason;

    public ApprovalRequest(String toolName, RiskTier tier, Map<String, Object> redactedArgs,
                           String requestedBy, String reason, long ttlSeconds) {
        this.id = UUID.randomUUID().toString();
        this.toolName = toolName;
        this.tierCode = tier == null ? RiskTier.T2.getCode() : tier.getCode();
        this.arguments = redactedArgs == null ? Map.of() : redactedArgs;
        this.requestedBy = requestedBy;
        this.reason = reason;
        Instant now = Instant.now();
        this.createdAt = now;
        this.expiresAt = now.plusSeconds(ttlSeconds > 0 ? ttlSeconds : 900);
    }

    public String getId() { return id; }
    public String getToolName() { return toolName; }
    public String getTierCode() { return tierCode; }
    public Map<String, Object> getArguments() { return arguments; }
    public String getRequestedBy() { return requestedBy; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }

    public String getDecidedBy() { return decidedBy; }
    public void setDecidedBy(String decidedBy) { this.decidedBy = decidedBy; }

    public String getDecisionReason() { return decisionReason; }
    public void setDecisionReason(String decisionReason) { this.decisionReason = decisionReason; }

    /** 是否已过期（PENDING 且超过有效期）。 */
    public boolean isExpired() {
        return status == Status.PENDING && Instant.now().isAfter(expiresAt);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("tool", toolName);
        m.put("tier", tierCode);
        m.put("status", status.name());
        m.put("requestedBy", requestedBy);
        m.put("reason", reason);
        m.put("createdAt", createdAt.toString());
        m.put("expiresAt", expiresAt.toString());
        m.put("decidedAt", decidedAt == null ? null : decidedAt.toString());
        m.put("decidedBy", decidedBy);
        m.put("decisionReason", decisionReason);
        m.put("arguments", arguments);
        return m;
    }
}