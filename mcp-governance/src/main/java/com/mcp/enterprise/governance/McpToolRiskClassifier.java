package com.mcp.enterprise.governance;

import com.mcp.enterprise.core.model.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * V1.26 工具风险分类器。
 *
 * <p>优先级（高 → 低）：</p>
 * <ol>
 *   <li>显式配置 {@code tool-tiers}（最可靠，建议生产必配）；</li>
 *   <li>工具 category 语义（system/finance 默认敏感读）；</li>
 *   <li>工具名关键词启发式：critical → write → sensitive → read；</li>
 *   <li>默认等级 {@code default-tier}（安全默认 T2）。</li>
 * </ol>
 *
 * <p>初始化时校验配置中的等级代码，非法值回退默认并告警，避免「配错等级=静默降级」。</p>
 */
public class McpToolRiskClassifier {

    private static final Logger log = LoggerFactory.getLogger(McpToolRiskClassifier.class);

    private final Map<String, RiskTier> explicitTiers;  // lower-case tool name -> tier
    private final RiskTier defaultTier;
    private final List<String> criticalKeywords;
    private final List<String> writeKeywords;
    private final List<String> sensitiveKeywords;
    private final List<String> readKeywords;

    public McpToolRiskClassifier(McpGovernanceProperties properties) {
        this.defaultTier = parseOr(properties.getDefaultTier(), RiskTier.T2);
        this.criticalKeywords = properties.getCriticalKeywords();
        this.writeKeywords = properties.getWriteKeywords();
        this.sensitiveKeywords = properties.getSensitiveKeywords();
        this.readKeywords = properties.getReadKeywords();

        Map<String, RiskTier> explicit = new java.util.LinkedHashMap<>();
        properties.getToolTiers().forEach((name, tierCode) -> {
            RiskTier tier = RiskTier.fromCode(tierCode);
            if (tier == null) {
                log.warn("⚠️ [V1.26] 非法工具等级配置: tool={} tier={}（已忽略，将按启发式归类）", name, tierCode);
                return;
            }
            explicit.put(name.toLowerCase(Locale.ROOT), tier);
        });
        this.explicitTiers = explicit;
        log.info("🛡️ [V1.26] 风险分类器就绪: 显式覆盖 {} 条, 默认等级 {}", explicit.size(), defaultTier);
    }

    /** 分类一个已注册工具。 */
    public RiskTier classify(String toolName, ToolDefinition definition) {
        RiskTier tier = classifyByExplicit(toolName);
        if (tier != null) {
            return tier;
        }
        if (definition != null && definition.getCategory() != null) {
            String cat = definition.getCategory().toLowerCase(Locale.ROOT);
            if (cat.contains("admin") || cat.contains("system")) {
                tier = RiskTier.T3;
            } else if (cat.contains("finance") || cat.contains("db") || cat.contains("data")) {
                tier = RiskTier.T2;
            } else if (cat.contains("search") || cat.contains("weather") || cat.contains("calc")) {
                tier = RiskTier.T1;
            }
            if (tier != null) {
                return tier;
            }
        }
        return classifyByKeywords(toolName);
    }

    /** 仅有工具名时的分类（无 ToolDefinition 上下文）。 */
    public RiskTier classify(String toolName) {
        RiskTier tier = classifyByExplicit(toolName);
        return tier != null ? tier : classifyByKeywords(toolName);
    }

    public RiskTier getDefaultTier() {
        return defaultTier;
    }

    public Set<String> getExplicitToolNames() {
        return new HashSet<>(explicitTiers.keySet());
    }

    private RiskTier classifyByExplicit(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return defaultTier;
        }
        return explicitTiers.get(toolName.toLowerCase(Locale.ROOT));
    }

    private RiskTier classifyByKeywords(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return defaultTier;
        }
        String name = toolName.toLowerCase(Locale.ROOT);
        if (matchesAny(name, criticalKeywords)) {
            return RiskTier.T4;
        }
        if (matchesAny(name, writeKeywords)) {
            return RiskTier.T3;
        }
        if (matchesAny(name, sensitiveKeywords)) {
            return RiskTier.T2;
        }
        if (matchesAny(name, readKeywords)) {
            return RiskTier.T1;
        }
        return defaultTier;
    }

    private static boolean matchesAny(String name, List<String> keywords) {
        for (String kw : keywords) {
            if (kw == null || kw.isEmpty()) {
                continue;
            }
            if (name.contains(kw.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static RiskTier parseOr(String code, RiskTier fallback) {
        RiskTier tier = RiskTier.fromCode(code);
        if (tier == null) {
            log.warn("⚠️ [V1.26] 非法 default-tier={}，回退 {} 并抬升为只读策略安全默认", code, fallback);
            return fallback;
        }
        return tier;
    }
}