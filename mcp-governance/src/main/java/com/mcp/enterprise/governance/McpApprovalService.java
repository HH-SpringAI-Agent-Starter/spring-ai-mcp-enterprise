package com.mcp.enterprise.governance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * V1.26 审批服务：创建 / 批准 / 拒绝 / 消费 审批请求，Fail-closed 语义。
 *
 * <ul>
 *   <li>创建：参数经 {@link SensitiveDataRedactor} 脱敏后入库，审批人只看打码内容；</li>
 *   <li>批准令牌一次性使用，消费后置为 {@code CONSUMED}，防重放；</li>
 *   <li>任何非法状态流转（对已批准/已拒绝的记录再批准）→ 抛出，不做静默覆盖；</li>
 *   <li>{@link #sweep()} 惰性清理过期 PENDING。</li>
 * </ul>
 */
public class McpApprovalService {

    private static final Logger log = LoggerFactory.getLogger(McpApprovalService.class);

    /** 审批令牌校验结果 */
    public enum Validation {
        OK, NOT_FOUND, NOT_APPROVED, EXPIRED, TOOL_MISMATCH, CALLER_MISMATCH
    }

    private final ApprovalStore store;
    private final SensitiveDataRedactor redactor;
    private final long ttlSeconds;
    private final boolean enabled;

    public McpApprovalService(ApprovalStore store, SensitiveDataRedactor redactor,
                              McpGovernanceProperties properties) {
        this.store = store;
        this.redactor = redactor;
        this.ttlSeconds = properties.getApproval().getTtlSeconds();
        this.enabled = properties.getApproval().isEnabled();
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** 创建一个待审批请求。 */
    public ApprovalRequest requestApproval(String toolName, RiskTier tier, Map<String, Object> arguments,
                                           String requestedBy, String reason) {
        Map<String, Object> safeArgs = redactor.redactMap(arguments);
        ApprovalRequest req = new ApprovalRequest(toolName, tier, safeArgs,
                blankTo(requestedBy, "anonymous"), reason, ttlSeconds);
        store.save(req);
        log.info("🕓 [V1.26] 创建审批请求: id={} tool={} tier={} by={} ttl={}s",
                req.getId(), toolName, req.getTierCode(), req.getRequestedBy(), ttlSeconds);
        return req;
    }

    /** 批准。非法状态流转抛 {@link IllegalStateException}。 */
    public synchronized ApprovalRequest approve(String id, String decidedBy, String reason) {
        ApprovalRequest req = requireDecidable(id);
        req.setStatus(ApprovalRequest.Status.APPROVED);
        req.setDecidedAt(Instant.now());
        req.setDecidedBy(blankTo(decidedBy, "admin"));
        req.setDecisionReason(reason);
        store.update(req);
        log.info("✅ [V1.26] 审批通过: id={} tool={} by={}", id, req.getToolName(), req.getDecidedBy());
        return req;
    }

    /** 拒绝。 */
    public synchronized ApprovalRequest reject(String id, String decidedBy, String reason) {
        ApprovalRequest req = requireDecidable(id);
        req.setStatus(ApprovalRequest.Status.REJECTED);
        req.setDecidedAt(Instant.now());
        req.setDecidedBy(blankTo(decidedBy, "admin"));
        req.setDecisionReason(reason);
        store.update(req);
        log.info("⛔ [V1.26] 审批拒绝: id={} tool={} by={} reason={}", id, req.getToolName(), req.getDecidedBy(), reason);
        return req;
    }

    /** 查询（惰性过期）。 */
    public ApprovalRequest get(String id) {
        ApprovalRequest req = store.get(id);
        if (req != null && req.isExpired()) {
            req.setStatus(ApprovalRequest.Status.EXPIRED);
            store.update(req);
        }
        return req;
    }

    public List<ApprovalRequest> list(ApprovalRequest.Status status) {
        sweep();
        return store.list(status);
    }

    /**
     * 校验并消费审批令牌（一次性）。返回 {@link Validation};
     * 仅当返回 {@code OK} 时调用方才可执行工具。
     */
    public synchronized Validation consume(String id, String toolName, String caller) {
        ApprovalRequest req = store.get(id);
        if (req == null) {
            return Validation.NOT_FOUND;
        }
        if (req.isExpired()) {
            req.setStatus(ApprovalRequest.Status.EXPIRED);
            store.update(req);
            return Validation.EXPIRED;
        }
        if (req.getStatus() != ApprovalRequest.Status.APPROVED) {
            return Validation.NOT_APPROVED;
        }
        if (toolName != null && !toolName.equals(req.getToolName())) {
            return Validation.TOOL_MISMATCH;
        }
        // 调用方一致性：任一方为空则跳过（兼容匿名/无身份场景），否则必须一致
        if (caller != null && !caller.isBlank() && req.getRequestedBy() != null
                && !req.getRequestedBy().isBlank()
                && !"anonymous".equals(req.getRequestedBy())
                && !caller.equals(req.getRequestedBy())) {
            return Validation.CALLER_MISMATCH;
        }
        req.setStatus(ApprovalRequest.Status.CONSUMED);
        store.update(req);
        log.info("🔓 [V1.26] 审批令牌已消费: id={} tool={} caller={}", id, toolName, caller);
        return Validation.OK;
    }

    /** 清理过期 PENDING，返回清理数量。 */
    public int sweep() {
        int n = 0;
        for (ApprovalRequest req : store.list(ApprovalRequest.Status.PENDING)) {
            if (req.isExpired()) {
                req.setStatus(ApprovalRequest.Status.EXPIRED);
                store.update(req);
                n++;
            }
        }
        if (n > 0) {
            log.info("🧹 [V1.26] 已过期审批 {} 条", n);
        }
        return n;
    }

    public Map<String, Object> stats() {
        return Map.of(
                "total", store.count(),
                "pending", store.list(ApprovalRequest.Status.PENDING).size(),
                "approved", store.list(ApprovalRequest.Status.APPROVED).size(),
                "rejected", store.list(ApprovalRequest.Status.REJECTED).size(),
                "expired", store.list(ApprovalRequest.Status.EXPIRED).size(),
                "consumed", store.list(ApprovalRequest.Status.CONSUMED).size(),
                "ttlSeconds", ttlSeconds);
    }

    private ApprovalRequest requireDecidable(String id) {
        ApprovalRequest req = store.get(id);
        if (req == null) {
            throw new IllegalArgumentException("审批请求不存在: " + id);
        }
        if (req.isExpired()) {
            req.setStatus(ApprovalRequest.Status.EXPIRED);
            store.update(req);
            throw new IllegalStateException("审批请求已过期: " + id);
        }
        if (req.getStatus() != ApprovalRequest.Status.PENDING) {
            throw new IllegalStateException("审批请求状态不可再决策: " + req.getStatus());
        }
        return req;
    }

    private static String blankTo(String v, String fallback) {
        return (v == null || v.isBlank()) ? fallback : v;
    }
}