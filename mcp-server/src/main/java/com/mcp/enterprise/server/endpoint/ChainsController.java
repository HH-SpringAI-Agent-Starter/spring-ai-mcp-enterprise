package com.mcp.enterprise.server.endpoint;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 因果链(Decision Flow) REST API
 *
 * 将 knowledge/chains/ 下的 17 条因果链结构化 JSON
 * 挂载到 MCP Enterprise Server 的 REST 端点，
 * 供外部 Agent / Web 前端 / 决策引擎调用。
 */
@RestController
@RequestMapping("/api/v1/chains")
public class ChainsController {

    private static final Logger log = LoggerFactory.getLogger(ChainsController.class);

    private final Map<String, Map<String, Object>> allChains = new LinkedHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChainsController() {
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
                    log.info("  📦 加载因果链: {} ({})", chain.get("name"), chainId);
                }
            }

            log.info("✅ 已加载 {} 条因果链", allChains.size());
        } catch (Exception e) {
            log.error("❌ 加载因果链失败: {}", e.getMessage(), e);
        }
    }

    // ===== 列表 =====

    /**
     * 列出所有因果链摘要
     */
    @GetMapping
    public Map<String, Object> listChains(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tag) {

        List<Map<String, Object>> summary = new ArrayList<>();

        for (Map<String, Object> chain : allChains.values()) {
            String cat = (String) chain.get("category");
            List<String> tags = (List<String>) chain.get("normalized_tags");

            // 过滤
            if (category != null && !category.isEmpty() && !category.equals(cat)) continue;
            if (tag != null && !tag.isEmpty() && (tags == null || !tags.contains(tag))) continue;

            summary.add(Map.of(
                    "chain_id", chain.get("chain_id"),
                    "name", chain.get("name"),
                    "category", chain.get("category"),
                    "confidence_weight", chain.get("confidence_weight"),
                    "version", chain.get("version"),
                    "tags", tags,
                    "trigger_price_up_pct", Optional.ofNullable((Map<String, Object>) chain.get("trigger_conditions"))
                            .map(tc -> tc.get("price_up_pct_30d")).orElse(null)
            ));
        }

        return Map.of(
                "success", true,
                "total", summary.size(),
                "chains", summary
        );
    }

    // ===== 单条详情 =====

    @GetMapping("/{chainId}")
    public Map<String, Object> getChain(@PathVariable String chainId) {
        Map<String, Object> chain = allChains.get(chainId);
        if (chain == null) {
            return Map.of("success", false, "error", "Chain not found: " + chainId);
        }
        Map<String, Object> result = new LinkedHashMap<>(chain);
        result.put("success", true);
        return result;
    }

    // ===== 搜索 =====

    @GetMapping("/search")
    public Map<String, Object> searchChains(@RequestParam String q) {
        if (q == null || q.isBlank()) return listChains(null, null);

        String query = q.toLowerCase().trim();
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

            if (name.contains(query) || desc.contains(query) || tagsStr.contains(query)) {
                matches.add(Map.of(
                        "chain_id", chain.get("chain_id"),
                        "name", chain.get("name"),
                        "category", chain.get("category"),
                        "confidence_weight", chain.get("confidence_weight")
                ));
            }
        }

        return Map.of(
                "success", true,
                "total", matches.size(),
                "results", matches
        );
    }

    // ===== 统计 =====

    @GetMapping("/stats")
    public Map<String, Object> stats() {
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

        return Map.of(
                "success", true,
                "totalChains", allChains.size(),
                "activeChains", activeCount,
                "byCategory", catCount,
                "version", "v2.0"
        );
    }
}
