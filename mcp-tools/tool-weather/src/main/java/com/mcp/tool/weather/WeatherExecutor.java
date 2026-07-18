package com.mcp.tool.weather;

import com.mcp.enterprise.core.model.ToolDefinition;
import com.mcp.enterprise.core.tool.McpToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 天气预报工具 (McpToolExecutor SPI 演示)
 *
 * 展示如何通过 SPI 扩展添加自定义 MCP 工具。
 * 使用 wttr.in 免费天气 API。
 */
@Component
public class WeatherExecutor implements McpToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(WeatherExecutor.class);
    private static final String API_TEMPLATE = "https://wttr.in/%s?format=%%t+%%C+%%h+%%w&lang=zh";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("location", Map.of(
                "type", "string",
                "description", "城市名称（中文或英文，如：北京、Shanghai）",
                "default", "北京"
        ));

        return new ToolDefinition(
                "weather", "天气预报", "查询指定城市的实时天气，包括温度、天气状况、湿度和风速", "demo",
                "1.0.0", null, true, "admin,user", 10000, 10,
                Map.of("type", "object", "properties", properties), null
        );
    }

    @Override
    public Mono<Map<String, Object>> execute(Map<String, Object> params) {
        String location = params != null && params.containsKey("location")
                ? (String) params.get("location")
                : "北京";

        log.debug("查询天气: {}", location);

        return Mono.fromCallable(() -> {
            try {
                String url = String.format(API_TEMPLATE, URLEncoder.encode(location, "UTF-8"));
                URI uri = new URI(url);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(uri)
                        .timeout(Duration.ofSeconds(8))
                        .header("User-Agent", "MCP-Enterprise/1.0")
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(Charset.forName("UTF-8")));

                if (response.statusCode() == 200) {
                    String body = response.body().trim();
                    return Map.of(
                            "success", true,
                            "result", location + " 天气: " + body,
                            "location", location,
                            "raw", body
                    );
                } else {
                    return Map.of(
                            "success", false,
                            "error", "天气查询失败: HTTP " + response.statusCode()
                    );
                }
            } catch (Exception e) {
                log.error("天气查询异常: {}", e.getMessage());
                return Map.of(
                        "success", false,
                        "error", "天气查询异常: " + e.getMessage()
                );
            }
        });
    }
}
