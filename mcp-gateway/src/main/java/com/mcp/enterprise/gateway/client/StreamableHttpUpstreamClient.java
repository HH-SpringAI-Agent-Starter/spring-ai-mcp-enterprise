package com.mcp.enterprise.gateway.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.enterprise.gateway.McpGatewayProperties;
import com.mcp.enterprise.gateway.RemoteToolDescriptor;
import com.mcp.enterprise.gateway.UpstreamMcpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * V1.25 Streamable HTTP 上游客户端（MCP 2026-07-28）。
 *
 * <p>用 JDK {@link HttpClient} 直接做 JSON-RPC over HTTP，零额外依赖：</p>
 * <ul>
 *   <li>每个请求携带 {@code Accept: application/json, text/event-stream}；</li>
 *   <li>目标方法前先发送 {@code initialize} + {@code notifications/initialized}（无状态传输上
 *       兼容需要会话初始化的下游，如 FastMCP / Spring AI 官方实现）；</li>
 *   <li>响应兼容「单 JSON」与「SSE 流（data: 行）」两种形态；</li>
 *   <li>{@code api-key} 非空时透传 {@code Authorization: Bearer <api-key>}。</li>
 * </ul>
 */
public class StreamableHttpUpstreamClient implements UpstreamMcpClient {

    private static final Logger log = LoggerFactory.getLogger(StreamableHttpUpstreamClient.class);

    /** MCP 2026-07-28 协议版本 */
    public static final String PROTOCOL_VERSION = "2026-07-28";

    private final McpGatewayProperties.Upstream config;
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicLong idSeq = new AtomicLong(1);

    public StreamableHttpUpstreamClient(McpGatewayProperties.Upstream config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.getConnectTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public String name() {
        return config.getName();
    }

    @Override
    public Mono<Boolean> ping() {
        return call("initialize", initializeParams())
                .map(envelope -> envelope.containsKey("result") && !envelope.containsKey("error"))
                .onErrorReturn(false);
    }

    @Override
    public Mono<List<RemoteToolDescriptor>> listTools() {
        return call("tools/list", Map.of())
                .map(this::extractTools);
    }

    @Override
    public Mono<Map<String, Object>> callTool(String toolName, Map<String, Object> arguments) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", toolName);
        params.put("arguments", arguments == null ? Map.of() : arguments);
        return call("tools/call", params)
                .map(this::extractCallResult);
    }

    // ===== JSON-RPC 传输 =====

    /**
     * 发送一次 JSON-RPC 方法调用并返回整个响应信封（含 result 或 error）。
     *
     * <p>对非 initialize 方法，先在同一无状态链路上发送 initialize 与
     * notifications/initialized，再发送目标方法。</p>
     */
    private Mono<Map<String, Object>> call(String method, Map<String, Object> params) {
        if ("initialize".equals(method)) {
            return post(method, params, true);
        }
        return post("initialize", initializeParams(), true)
                .flatMap(ignored -> post("notifications/initialized", null, false))
                .then(post(method, params, true));
    }

    private Map<String, Object> initializeParams() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("protocolVersion", PROTOCOL_VERSION);
        params.put("capabilities", Map.of());
        params.put("clientInfo", Map.of(
                "name", "mcp-enterprise-gateway",
                "version", "1.25"));
        return params;
    }

    /**
     * 单次 HTTP POST：JSON-RPC 请求 → 解析信封。
     *
     * @param withId true = 普通请求（携带递增 id）；false = 通知（无 id，如 initialized）
     */
    private Mono<Map<String, Object>> post(String method, Map<String, Object> params, boolean withId) {
        return Mono.fromCallable(() -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("jsonrpc", "2.0");
            if (withId) {
                body.put("id", idSeq.incrementAndGet());
            }
            body.put("method", method);
            if (params != null) {
                body.put("params", params);
            }

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(config.getUrl()))
                    .timeout(Duration.ofMillis(config.getRequestTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8));
            if (StringUtils.hasText(config.getApiKey())) {
                builder.header("Authorization", "Bearer " + config.getApiKey());
            }

            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return parseResponse(response);
        });
    }

    /** 兼容 JSON 与 SSE 两种响应形态 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseResponse(HttpResponse<String> response) throws Exception {
        String body = response.body() == null ? "" : response.body().trim();
        if (body.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("error", Map.of("code", -32000, "message", "empty response from upstream " + config.getName()));
            return empty;
        }

        String contentType = response.headers().firstValue("Content-Type").orElse("");
        String json = body;
        if (contentType.contains("text/event-stream") || body.startsWith("data:")) {
            String sseJson = extractSseJson(body);
            if (sseJson == null) {
                Map<String, Object> noData = new LinkedHashMap<>();
                noData.put("error", Map.of("code", -32000,
                        "message", "no parseable SSE data frame from upstream " + config.getName()));
                return noData;
            }
            json = sseJson;
        }

        if (response.statusCode() >= 400) {
            Map<String, Object> httpError = new LinkedHashMap<>();
            httpError.put("error", Map.of("code", -32001,
                    "message", "upstream HTTP " + response.statusCode() + ": " + body));
            return httpError;
        }

        return mapper.readValue(json, Map.class);
    }

    /**
     * 从 SSE 响应体中提取最后一个可解析的 JSON-RPC 消息。
     * 支持单 data 行、多 data 行（拼接）、data: 前缀空白等常见形态。
     */
    private String extractSseJson(String body) {
        List<String> payloads = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : body.split("\\r?\\n")) {
            if (line.startsWith("data:")) {
                String data = line.substring(5);
                if (data.startsWith(" ")) {
                    data = data.substring(1);
                }
                if (data.isEmpty()) {
                    continue;
                }
                current.append(data);
                payloads.add(data);
            }
        }
        if (payloads.isEmpty()) {
            return null;
        }
        // 优先整体拼接解析（多行分片场景）
        try {
            mapper.readTree(current.toString());
            return current.toString();
        } catch (Exception ignored) {
            // 回退：逐条解析，取最后一个合法 JSON 对象
            for (int i = payloads.size() - 1; i >= 0; i--) {
                try {
                    mapper.readTree(payloads.get(i));
                    return payloads.get(i);
                } catch (Exception ignored2) {
                    // continue
                }
            }
        }
        return null;
    }

    // ===== 结果归一化 =====

    @SuppressWarnings("unchecked")
    private List<RemoteToolDescriptor> extractTools(Map<String, Object> envelope) {
        if (envelope.containsKey("error")) {
            log.warn("⛔ 上游 {} tools/list 错误: {}", config.getName(), envelope.get("error"));
            return List.of();
        }
        Object resultObj = envelope.get("result");
        if (!(resultObj instanceof Map<?, ?> result)) {
            return List.of();
        }
        Object toolsObj = result.get("tools");
        if (!(toolsObj instanceof List<?> tools)) {
            return List.of();
        }
        List<RemoteToolDescriptor> descriptors = new ArrayList<>();
        for (Object t : tools) {
            if (t instanceof Map<?, ?> raw) {
                descriptors.add(RemoteToolDescriptor.from((Map<String, Object>) raw));
            }
        }
        return descriptors;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractCallResult(Map<String, Object> envelope) {
        Map<String, Object> out = new LinkedHashMap<>();

        // JSON-RPC error（协议级失败）
        if (envelope.containsKey("error")) {
            Object err = envelope.get("error");
            String message = err instanceof Map<?, ?> m && m.get("message") != null
                    ? String.valueOf(m.get("message")) : String.valueOf(err);
            out.put("success", false);
            out.put("error", "upstream rpc error: " + message);
            out.put("raw", envelope);
            return out;
        }

        Object resultObj = envelope.get("result");
        Map<String, Object> result = resultObj instanceof Map<?, ?> r
                ? (Map<String, Object>) r : new LinkedHashMap<>();

        List<Map<String, Object>> content = new ArrayList<>();
        if (result.get("content") instanceof List<?> list) {
            for (Object c : list) {
                if (c instanceof Map<?, ?> cm) {
                    content.add((Map<String, Object>) cm);
                }
            }
        }
        boolean isError = Boolean.TRUE.equals(result.get("isError"));
        String text = concatText(content);

        out.put("success", !isError);
        out.put("isError", isError);
        out.put("raw", content);

        Object structured = result.get("structuredContent");
        if (!isError && structured != null) {
            out.put("result", structured);
        } else if (!isError) {
            out.put("result", text);
        } else {
            out.put("error", text.isBlank() ? "upstream tool returned isError=true" : text);
        }
        return out;
    }

    private String concatText(List<Map<String, Object>> content) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> c : content) {
            if ("text".equals(c.get("type")) && c.get("text") != null) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(c.get("text"));
            }
        }
        return sb.toString();
    }

    /** V1.25 默认工厂：按配置创建 Streamable HTTP 客户端 */
    public static class Factory implements McpUpstreamClientFactory {
        @Override
        public UpstreamMcpClient create(McpGatewayProperties.Upstream config) {
            return new StreamableHttpUpstreamClient(config);
        }
    }
}