package com.mcp.enterprise.server.endpoint;

import com.mcp.enterprise.core.model.ToolDefinition;
import com.mcp.enterprise.core.tool.McpToolExecutor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

/**
 * MCP Tool: 因果链查询引擎 (Decision Flow Engine)
 *
 * 将 17 条因果关系链作为 MCP Tool 暴露，
 * 支持：
 * - list: 列出所有链（可过滤分类/标签）
 * - get: 获取单条链详情
 * - search: 关键词搜索
 * - match: 根据触发条件匹配活跃链
 * - stats: 统计信息
 */
@Component
public class ChainQueryExecutor implements McpToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ChainQueryExecutor.class);

    private final Map<String, Map<String, Object>> allChains = new LinkedHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChainQueryExecutor() {
        loadChains();
    }

    private void loadChains() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:chains/*.json");

            for (Resource res : resources) {
                Map<String, Object> chain = objectMapper.readValue(
                        res.getInputStream(),
                        new TypeReference<Map<String, Object>>() {}
                );
                String chainId = (String) chain.get("chain_id");
                if (chainId != null) {
                    allChains.put(chainId, chain);
                }
            }
            log.info("chain-tool: 已加载 {} 条因果链", allChains.size());
        } catch (Exception e) {
            log.error("chain-tool: 加载失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> inputSchema = new LinkedHashMap<>();
        inputSchema.put("type", "object");
        inputSchema.put("required", List.of("action"));

        Map<String, Object> properties = new LinkedHashMap<>();

        // action
        Map<String, Object> actionProp = new LinkedHashMap<>();
        actionProp.put("type", "string");
        actionProp.put("enum", List.of("list", "get", "search", "match", "stats"));
        actionProp.put("description", "操作类型: list=列表, get=详情, search=搜索, match=匹配, stats=统计");
        properties.put("action", actionProp);

        // chain_id（get 需要）
        Map<String, Object> chainIdProp = new LinkedHashMap<>();
        chainIdProp.put("type", "string");
        chainIdProp.put("description", "链 ID (仅 action=get 时需要)");
        properties.put("chain_id", chainIdProp);

        // q（search 需要）
        Map<String, Object> qProp = new LinkedHashMap<>();
        qProp.put("type", "string");
        qProp.put("description", "搜索关键词 (仅 action=search 时需要)");
        properties.put("q", qProp);

        // category（list/match 过滤）
        Map<String, Object> catProp = new LinkedHashMap<>();
        catProp.put("type", "string");
        catProp.put("description", "分类过滤: AI/半导体 或 化工/材料 (仅 list/match 使用)");
        properties.put("category", catProp);

        // trigger（match 需要）
        Map<String, Object> triggerProp = new LinkedHashMap<>();
        triggerProp.put("type", "object");
        triggerProp.put("description", "触发条件 (仅 action=match 时需要): {price_up_pct_30d: 15, supply_shock: '描述', demand_boost: '描述'}");
        properties.put("trigger", triggerProp);

        inputSchema.put("properties", properties);

        // 列出所有可用 chain_id（用于 IDE 提示）
        List<String> chainIds = new ArrayList<>(allChains.keySet());

        return new ToolDefinition(
                "chain_query",
                "因果链查询引擎",
                "查询/匹配 17 条因果链：硫磺→磷化工、WF6断供、光通信InP、MLCC、PCB/CCL、液冷、封装、功率半导体、超节点等。支持按涨跌幅/供给冲击/需求拉动匹配活跃链。",
                "ai",
                "v2.0",
                "mcp-server",
                true,
                "user",
                15000,
                20,
                inputSchema,
                Map.of(
                        "source", "knowledge/chains/ v2.0",
                        "totalChains", chainIds.size(),
                        "chainIds", chainIds,
                        "description", "每条链含触发条件、传导路径、瓶颈分析、标的映射、验证信号、退出规则"
                )
        );
    }

    @Override
    public Mono<Map<String, Object>> execute(Map<String, Object> params) {
        String action = params != null ? (String) params.get("action") : null;
        if (action == null) {
            return Mono.just(Map.of("success", false, "error", "Missing required parameter: action"));
        }

        return switch (action) {
            case "list" -> executeList(params);
            case "get" -> executeGet(params);
            case "search" -> executeSearch(params);
            case "match" -> executeMatch(params);
            case "stats" -> executeStats();
            default -> Mono.just(Map.of("success", false, "error", "Unknown action: " + action));
        };
    }

    // ===== list =====

    private Mono<Map<String, Object>> executeList(Map<String, Object> params) {
        String category = (String) params.get("category");
        String tag = (String) params.get("tag");

        List<Map<String, Object>> summary = new ArrayList<>();

        for (Map<String, Object> chain : allChains.values()) {
            String cat = (String) chain.get("category");
            List<String> tags = (List<String>) chain.get("normalized_tags");

            if (category != null && !category.isEmpty() && !category.equals(cat)) continue;
            if (tag != null && !tag.isEmpty() && (tags == null || !tags.contains(tag))) continue;

            summary.add(Map.of(
                    "chain_id", chain.get("chain_id"),
                    "name", chain.get("name"),
                    "category", chain.get("category"),
                    "confidence_weight", chain.get("confidence_weight"),
                    "version", chain.get("version")
            ));
        }

        return Mono.just(Map.of(
                "success", true,
                "total", summary.size(),
                "chains", summary
        ));
    }

    // ===== get =====

    @SuppressWarnings("unchecked")
    private Mono<Map<String, Object>> executeGet(Map<String, Object> params) {
        String chainId = (String) params.get("chain_id");
        if (chainId == null) {
            return Mono.just(Map.of("success", false, "error", "Missing parameter: chain_id"));
        }

        Map<String, Object> chain = allChains.get(chainId);
        if (chain == null) {
            return Mono.just(Map.of("success", false, "error", "Chain not found: " + chainId));

        }

        // 返回完整链数据（含标的）
        Map<String, Object> result = new LinkedHashMap<>(chain);
        result.put("success", true);

        // 提取简洁版标的映射
        Map<String, Object> tickerSummary = new LinkedHashMap<>();
        Map<String, Object> mapping = (Map<String, Object>) chain.get("ticker_mapping");
        if (mapping != null) {
            for (String tier : List.of("T1_core_bottleneck", "T2_supply_demand_beneficiary", "T3_full_chain", "T4_catalytic")) {
                Map<String, Object> tierData = (Map<String, Object>) mapping.get(tier);
                if (tierData != null) {
                    List<Map<String, Object>> stocks = (List<Map<String, Object>>) tierData.get("stocks");
                    if (stocks != null && !stocks.isEmpty()) {
                        tickerSummary.put(tier, stocks.stream().map(s -> Map.of(
                                "code", s.get("code"),
                                "name", s.get("name"),
                                "reason", s.get("reason"),
                                "tag", s.get("position_tag")
                        )).collect(Collectors.toList()));
                    }
                }
            }
        }

        result.put("tickers", tickerSummary);

        return Mono.just(result);
    }

    // ===== search =====

    private Mono<Map<String, Object>> executeSearch(Map<String, Object> params) {
        String query = (String) params.get("q");
        if (query == null || query.isBlank()) {
            return executeList(params);
        }

        String q = query.toLowerCase().trim();
        List<Map<String, Object>> matches = new ArrayList<>();

        for (Map<String, Object> chain : allChains.values()) {
            String name = ((String) chain.getOrDefault("name", "")).toLowerCase();
            String desc = "";
            try {
                Map<String, Object> cp = (Map<String, Object>) chain.get("conduction_path");
                if (cp != null) desc = ((String) cp.getOrDefault("description", "")).toLowerCase();
            } catch (Exception ignored) {}
            List<String> tags = (List<String>) chain.get("normalized_tags");
            String tagsStr = tags != null ? String.join(" ", tags).toLowerCase() : "";

            if (name.contains(q) || desc.contains(q) || tagsStr.contains(q)) {
                matches.add(Map.of(
                        "chain_id", chain.get("chain_id"),
                        "name", chain.get("name"),
                        "category", chain.get("category"),
                        "confidence_weight", chain.get("confidence_weight")
                ));
            }
        }

        return Mono.just(Map.of(
                "success", true,
                "total", matches.size(),
                "results", matches
        ));
    }

    // ===== match（核心：根据触发条件匹配活跃链） =====

    @SuppressWarnings("unchecked")
    private Mono<Map<String, Object>> executeMatch(Map<String, Object> params) {
        Map<String, Object> trigger = (Map<String, Object>) params.get("trigger");
        if (trigger == null) trigger = Map.of();

        Number priceUp = toNumber(trigger.get("price_up_pct_30d"));
        String supplyShock = (String) trigger.get("supply_shock");
        String demandBoost = (String) trigger.get("demand_boost");

        List<Map<String, Object>> matched = new ArrayList<>();

        for (Map<String, Object> chain : allChains.values()) {
            Map<String, Object> conditions = (Map<String, Object>) chain.get("trigger_conditions");
            if (conditions == null) continue;

            int score = 0;
            List<String> reasons = new ArrayList<>();

            // 涨跌幅匹配
            Number threshold = toNumber(conditions.get("price_up_pct_30d"));
            if (threshold != null && priceUp != null && priceUp.doubleValue() >= threshold.doubleValue()) {
                score += 40;
                reasons.add("涨跌幅达标: " + priceUp + "% >= " + threshold + "%");
            }

            // 供给冲击匹配
            String condSupply = (String) conditions.get("supply_shock");
            if (condSupply != null && supplyShock != null && !supplyShock.isEmpty()) {
                score += 30;
                reasons.add("供给冲击匹配");
            }

            // 需求拉动匹配
            String condDemand = (String) conditions.get("demand_boost");
            if (condDemand != null && demandBoost != null && !demandBoost.isEmpty()) {
                score += 30;
                reasons.add("需求拉动匹配");
            }

            if (score > 0) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("chain_id", chain.get("chain_id"));
                entry.put("name", chain.get("name"));
                entry.put("category", chain.get("category"));
                entry.put("confidence_weight", chain.get("confidence_weight"));
                entry.put("match_score", score);
                entry.put("match_reasons", reasons);
                entry.put("bottleneck", ((Map<String, Object>) chain.get("bottleneck_analysis")).get("bottleneck_stage"));

                // T1 标的（核心瓶颈）
                Map<String, Object> mapping = (Map<String, Object>) chain.get("ticker_mapping");
                if (mapping != null) {
                    Map<String, Object> t1 = (Map<String, Object>) mapping.get("T1_core_bottleneck");
                    if (t1 != null) {
                        entry.put("core_tickers", t1.get("stocks"));
                    }
                }
                matched.add(entry);
            }
        }

        // 按匹配度排序
        matched.sort((a, b) -> Integer.compare((int) b.get("match_score"), (int) a.get("match_score")));

        return Mono.just(Map.of(
                "success", true,
                "total", matched.size(),
                "matches", matched,
                "trigger", trigger
        ));
    }

    // ===== stats =====

    private Mono<Map<String, Object>> executeStats() {
        long activeCount = allChains.values().stream()
                .filter(c -> {
                    Integer w = (Integer) c.get("confidence_weight");
                    return w != null && w >= 60;
                }).count();

        Map<String, Long> catCount = allChains.values().stream()
                .collect(Collectors.groupingBy(
                        c -> (String) c.getOrDefault("category", "unknown"),
                        Collectors.counting()
                ));

        return Mono.just(Map.of(
                "success", true,
                "totalChains", allChains.size(),
                "activeChains", activeCount,
                "byCategory", catCount,
                "version", "v2.0"
        ));
    }

    private Number toNumber(Object value) {
        if (value instanceof Number) return (Number) value;
        if (value instanceof String) {
            try { return Double.parseDouble((String) value); } catch (Exception e) { return null; }
        }
        return null;
    }
}
