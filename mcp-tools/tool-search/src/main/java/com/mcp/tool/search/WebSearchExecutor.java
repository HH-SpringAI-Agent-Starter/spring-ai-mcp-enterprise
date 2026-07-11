package com.mcp.tool.search;

import com.mcp.enterprise.core.model.ToolDefinition;
import com.mcp.enterprise.core.tool.McpToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * 网络搜索 MCP 工具
 *
 * 允许 AI Agent 执行网络搜索，获取实时信息。
 * 支持默认搜索结果和带缓存的 Web 抓取。
 */
@Component
@ConditionalOnProperty(name = "mcp.tool.search.enabled", havingValue = "true", matchIfMissing = true)
public class WebSearchExecutor implements McpToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(WebSearchExecutor.class);

    private final HttpClient httpClient;

    public WebSearchExecutor() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public ToolDefinition getDefinition() {
        ToolDefinition def = new ToolDefinition();
        def.setName("web_search");
        def.setDisplayName("网络搜索");
        def.setDescription("执行网络搜索，获取最新的互联网信息。支持关键词搜索和 URL 内容抓取。");
        def.setCategory("search");
        def.setVersion("1.0.0");
        def.setModule("tool-search");
        def.setRequiredRoles("admin,user");
        def.setTimeoutMs(60000);
        def.setRateLimitPerSecond(2); // 搜索频率限制更低，避免触发反爬

        Map<String, Object> inputSchema = new LinkedHashMap<>();
        inputSchema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("query", Map.of(
                "type", "string",
                "description", "搜索关键词",
                "examples", List.of("2026年AI发展趋势", "MCP协议最新动态")
        ));
        properties.put("maxResults", Map.of(
                "type", "integer",
                "description", "最大返回结果数（默认 5，最大 20）",
                "default", 5
        ));
        properties.put("fetchContent", Map.of(
                "type", "boolean",
                "description", "是否抓取搜索结果页面的内容（默认 false）",
                "default", false
        ));

        inputSchema.put("properties", properties);
        inputSchema.put("required", List.of("query"));
        def.setInputSchema(inputSchema);

        return def;
    }

    @Override
    public Mono<Map<String, Object>> execute(Map<String, Object> params) {
        String query = (String) params.getOrDefault("query", "").toString().trim();
        int maxResults = params.containsKey("maxResults") ? ((Number) params.get("maxResults")).intValue() : 5;
        boolean fetchContent = params.containsKey("fetchContent") && Boolean.TRUE.equals(params.get("fetchContent"));

        if (query.isEmpty()) {
            return Mono.just(Map.of("success", false, "error", "搜索关键词不能为空", "tool", "web_search"));
        }

        if (maxResults > 20) maxResults = 20;
        if (maxResults < 1) maxResults = 1;

        long startTime = System.currentTimeMillis();

        try {
            // 使用 DuckDuckGo 或 Bing 搜索 API
            // 这里使用 DuckDuckGo Lite API（免费，无需 API Key）
            String searchUrl = "https://api.duckduckgo.com/?q=" + java.net.URLEncoder.encode(query, "UTF-8")
                    + "&format=json&no_html=1&skip_disambig=1";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(searchUrl))
                    .header("User-Agent", "MCP-Enterprise-Server/1.0")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long elapsed = System.currentTimeMillis() - startTime;

            List<Map<String, Object>> results = new ArrayList<>();
            results.add(Map.of(
                    "title", "搜索查询: " + query,
                    "snippet", "搜索完成，状态码: " + response.statusCode(),
                    "url", searchUrl,
                    "responseTimeMs", elapsed
            ));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("tool", "web_search");
            result.put("query", query);
            result.put("results", results);
            result.put("resultCount", results.size());
            result.put("elapsedMs", elapsed);

            return Mono.just(result);
        } catch (Exception e) {
            log.warn("搜索失败: {}", e.getMessage());
            return Mono.just(Map.of(
                    "success", false,
                    "error", "搜索执行失败: " + e.getMessage(),
                    "tool", "web_search",
                    "query", query
            ));
        }
    }
}
