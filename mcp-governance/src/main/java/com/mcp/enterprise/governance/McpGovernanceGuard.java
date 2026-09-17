package com.mcp.enterprise.governance;

import com.mcp.enterprise.core.model.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * V1.26 治理判定核心：按风险等级决定 放行 / 需审批 / 拒绝。
 *
 * <p>判定顺序（Fail-closed）：</p>
 * <ol>
 *   <li>模块关闭 → 放行（向后兼容）；</li>
 *   <li>等级命中 {@code deny-tiers} → 直接拒绝（硬闸门）；</li>
 *   <li>等级命中 {@code require-approval-tiers}：无令牌 → 创建审批请求并返回 REQUIRE_APPROVAL；
 *       带令牌 → 校验（工具一致 / 调用方一致 / 未过期 / 已批准）通过则消费并放行，否则拒绝；</li>
 *   <li>其余 → 放行。</li>
 * </ol>
 */
public class McpGovernanceGuard {

    private static final Logger log = LoggerFactory.getLogger(McpGovernanceGuard.class);

    private final McpGovernanceProperties properties;
    private final McpToolRiskClassifier classifier;
    private final McpApprovalService approvalService;

    private final Set<RiskTier> requireApprovalTiers;
    private final Set<RiskTier> denyTiers;

    public McpGovernanceGuard(McpGovernanceProperties properties, McpToolRiskClassifier classifier,
                              McpApprovalService approvalService) {
        this.properties = properties;
        this.classifier = classifier;
        this.approvalService = approvalService;
        this.requireApprovalTiers = parseTiers(properties.getRequireApprovalTiers());
        this.denyTiers = parseTiers(properties.getDenyTiers());
        log.info("🛡️ [V1.26] 治理判定器就绪: 需审批等级={} 硬拒绝等级={} enforce={}",
                requireApprovalTiers, denyTiers, properties.isEnforce());
    }

    /** 仅按工具名判定（无 ToolDefinition 上下文）。 */
    public GovernanceDecision evaluate(String toolName, String caller,
                                       Map<String, Object> arguments, String approvalId) {
        return evaluate(toolName, null, caller, arguments, approvalId);
    }

    public GovernanceDecision evaluate(String toolName, ToolDefinition definition, String caller,
                                       Map<String, Object> arguments, String approvalId) {
        RiskTier tier = classifier.classify(toolName, definition);

        if (!properties.isEnabled()) {
            return GovernanceDecision.allow(tier);
        }
        if (denyTiers.contains(tier)) {
            log.warn("⛔ [V1.26] 硬闸门拒绝: tool={} tier={} caller={}", toolName, tier.getCode(), caller);
            return GovernanceDecision.denied(tier, "governance_denied",
                    "工具等级 " + tier.getCode() + " 已被治理策略禁止调用");
        }
        if (!requireApprovalTiers.contains(tier)) {
            return GovernanceDecision.allow(tier);
        }

        String effectiveCaller = (caller == null || caller.isBlank()) ? "anonymous" : caller;

        // 未携带审批令牌 → 创建审批请求
        if (approvalId == null || approvalId.isBlank()) {
            if (!approvalService.isEnabled()) {
                log.warn("⛔ [V1.26] 等级 {} 需审批但审批服务已关闭 → Fail-closed 拒绝: tool={}",
                        tier.getCode(), toolName);
                return GovernanceDecision.denied(tier, "approval_unavailable",
                        "审批服务未启用，无法授权高等级工具调用");
            }
            ApprovalRequest req = approvalService.requestApproval(toolName, tier, arguments,
                    effectiveCaller, "自动触发：等级 " + tier.getCode() + " 需人工审批");
            return GovernanceDecision.requireApproval(tier, req);
        }

        // 携带令牌 → 校验并消费
        McpApprovalService.Validation v = approvalService.consume(approvalId, toolName, effectiveCaller);
        if (v == McpApprovalService.Validation.OK) {
            log.info("✅ [V1.26] 审批令牌有效，放行: tool={} tier={} approval={}", toolName, tier.getCode(), approvalId);
            return GovernanceDecision.allow(tier);
        }
        log.warn("⛔ [V1.26] 审批令牌校验失败({}): tool={} approval={}", v, toolName, approvalId);
        return GovernanceDecision.denied(tier, "approval_invalid", "审批令牌无效: " + v.name());
    }

    public RiskTier tierOf(String toolName) {
        return classifier.classify(toolName);
    }

    public boolean requiresApproval(RiskTier tier) {
        return requireApprovalTiers.contains(tier);
    }

    public boolean isDenied(RiskTier tier) {
        return denyTiers.contains(tier);
    }

    public Set<RiskTier> getRequireApprovalTiers() {
        return requireApprovalTiers;
    }

    public Set<RiskTier> getDenyTiers() {
        return denyTiers;
    }

    private static Set<RiskTier> parseTiers(List<String> codes) {
        Set<RiskTier> tiers = new LinkedHashSet<>();
        if (codes == null) {
            return tiers;
        }
        for (String code : codes) {
            RiskTier t = RiskTier.fromCode(code == null ? null : code.toLowerCase(Locale.ROOT));
            if (t != null) {
                tiers.add(t);
            }
        }
        return tiers;
    }
}