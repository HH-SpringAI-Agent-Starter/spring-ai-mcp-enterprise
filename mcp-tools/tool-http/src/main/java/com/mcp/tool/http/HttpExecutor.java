package com.mcp.tool.http;

import com.mcp.enterprise.core.model.ToolDefinition;
import com.mcp.enterprise.core.tool.McpToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 通用 HTTP 调用工具
 *
 * <p>让 AI Agent 通过白名单机制调用企业内部或外部的 REST API，
 * 是企业 MCP Server 最常见的落地场景（对接内部订单/库存/CRM 系统等）。</p>
 *
 * <p>安全设计：</p>
 * <ul>
 *     <li>域名白名单：仅允许调用 {@code mcp.tool.http.allowed-hosts} 配置的主机，默认仅 localhost</li>
 *     <li>仅支持 GET/POST，禁止 DELETE 等危险方法</li>
 *     <li>响应体大小上限（默认 1MB），防止内存耗尽</li>
 *     <li>请求头白名单：只透传 X-API-Key / Authorization / Content-Type</li>
 * </ul>
 *
 * <p>由 {@link McpHttpToolAutoConfiguration} 注册为 Bean（非 @Component，避免双注册）。</p>
 */
public class HttpExecutor implements McpToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(HttpExecutor.class);

    /** 允许透传的请求头白名单 */
    private static final List<String> ALLOWED_HEADERS = List.of(
            "content-type", "authorization", "x-api-key", "accept"
    );

    private final McpHttpToolProperties properties;
    private final HttpClient httpClient;

    public HttpExecutor(McpHttpToolProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("url", Map.of(
                "type", "string",
                "description", "请求的完整 URL（必须命中 mcp.tool.http.allowed-hosts 白名单）"
        ));
        properties.put("method", Map.of(
                "type", "string",
                "description", "HTTP 方法：GET 或 POST",
                "enum", List.of("GET", "POST")
        ));
        properties.put("headers", Map.of(
                "type", "object",
                "description", "请求头（仅透传 content-type/authorization/x-api-key/accept）"
        ));
        properties.put("body", Map.of(
                "type", "string",
                "description", "POST 请求体（JSON 字符串）"
        ));

        return new ToolDefinition(
                "http", "HTTP 调用", "通过白名单机制调用企业内外 REST API（GET/POST），用于对接内部系统数据", "system",
                "1.0.0", null, true, "admin", 30000, 20,
                Map.of("type", "object", "properties", properties,
                        "required", List.of("url")), null
        );
    }

    @Override
    public Mono<Map<String, Object>> execute(Map<String, Object> params) {
        if (params == null || !params.containsKey("url")) {
            return Mono.just(Map.of("success", false, "error", "缺少必填参数 url"));
        }

        String url = String.valueOf(params.get("url"));
        String method = params.getOrDefault("method", "GET").toString().toUpperCase(Locale.ROOT);
        @SuppressWarnings("unchecked")
        Map<String, Object> headers = params.get("headers") instanceof Map<?, ?>
                ? (Map<String, Object>) params.get("headers") : Map.of();
        String body = params.get("body") != null ? String.valueOf(params.get("body")) : null;

        // 1. 方法校验
        if (!"GET".equals(method) && !"POST".equals(method)) {
            return Mono.just(Map.of("success", false, "error", "仅支持 GET/POST 方法: " + method));
        }

        // 2. 域名白名单校验（SSRF 防护）
        String host;
        try {
            host = new URI(url).getHost();
        } catch (Exception e) {
            return Mono.just(Map.of("success", false, "error", "URL 解析失败: " + e.getMessage()));
        }
        if (!isHostAllowed(host)) {
            log.warn("HTTP 工具拒绝访问未授权主机: {} (url={})", host, url);
            return Mono.just(Map.of(
                    "success", false,
                    "error", "主机不在白名单内: " + host
            ));
        }

        // 3. 构建请求
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(properties.getReadTimeout());

            // 透传白名单请求头
            headers.forEach((k, v) -> {
                String key = k.toLowerCase(Locale.ROOT);
                if (ALLOWED_HEADERS.contains(key)) {
                    builder.header(k, String.valueOf(v));
                }
            });

            HttpRequest request;
            if ("POST".equals(method)) {
                request = builder.header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body == null ? "{}" : body, StandardCharsets.UTF_8))
                        .build();
            } else {
                request = builder.GET().build();
            }

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            // 4. 响应体大小限制
            String responseBody = response.body();
            if (responseBody.getBytes(StandardCharsets.UTF_8).length > properties.getMaxResponseBytes()) {
                responseBody = responseBody.substring(0, (int) Math.min(properties.getMaxResponseBytes(), 4096)) + "...(truncated)";
            }

            return Mono.just(Map.of(
                    "success", response.statusCode() < 400,
                    "status", response.statusCode(),
                    "body", responseBody,
                    "url", url
            ));
        } catch (Exception e) {
            log.error("HTTP 调用异常: {} {}", url, e.getMessage());
            return Mono.just(Map.of(
                    "success", false,
                    "error", "HTTP 调用异常: " + e.getMessage(),
                    "url", url
            ));
        }
    }

    /**
     * 校验主机是否命中白名单。
     * 白名单为空时仅允许 localhost / 127.0.0.1；支持 *.example.com 通配。
     */
    boolean isHostAllowed(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String h = host.toLowerCase(Locale.ROOT);
        if (h.equals("localhost") || h.equals("127.0.0.1") || h.equals("::1")) {
            return true;
        }
        List<String> allowed = properties.getAllowedHosts();
        if (allowed == null || allowed.isEmpty()) {
            return false;
        }
        for (String pattern : allowed) {
            String p = pattern.trim().toLowerCase(Locale.ROOT);
            if (p.isEmpty()) {
                continue;
            }
            if (p.startsWith("*.")) {
                String suffix = p.substring(1); // ".example.com"
                if (h.endsWith(suffix)) {
                    return true;
                }
            } else if (h.equals(p)) {
                return true;
            }
        }
        return false;
    }
}
