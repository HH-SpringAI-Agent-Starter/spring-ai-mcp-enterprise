package com.mcp.enterprise.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V1.32 HttpGovernanceAuditSink 集成测试（JDK 内置 HttpServer 本地端点）。
 * 验证：批量发送大小、定时 flush 兜底、JSON payload 结构、附加请求头、
 * 本地可查视图（recent/search/deleteBefore）语义与内存实现一致、fail-soft。
 */
class HttpGovernanceAuditSinkTest {

    private HttpServer server;
    private volatile int serverPort;
    private final List<String> receivedBodies = new CopyOnWriteArrayList<>();
    private final AtomicInteger receivedCount = new AtomicInteger();
    private CountDownLatch latch;
    private volatile Map<String, List<String>> lastRequestHeaders = Collections.emptyMap();
    private volatile int responseCode = 200;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        serverPort = server.getAddress().getPort();
        server.createContext("/audit", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                byte[] body = exchange.getRequestBody().readAllBytes();
                receivedBodies.add(new String(body, StandardCharsets.UTF_8));
                receivedCount.addAndGet(1);
                lastRequestHeaders = exchange.getRequestHeaders();
                exchange.sendResponseHeaders(responseCode, -1);
                exchange.close();
                latch.countDown();
            }
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private String endpoint() {
        return "http://127.0.0.1:" + serverPort + "/audit";
    }

    private McpGovernanceAuditSink.Event event(String tool, String decision, String tier,
                                               Map<String, Object> args, String traceId) {
        return new McpGovernanceAuditSink.Event(Instant.now(), tool, tier, "caller-a",
                decision, "apr-1", args, "unit test event", traceId, "00f067aa0ba902b7");
    }

    @Test
    void recordsBatchBySize() throws Exception {
        latch = new CountDownLatch(2);
        // batch=3, flush 兜底很长 -> 只由攒批触发
        long sentCount;
        long failedCount;
        try (HttpGovernanceAuditSink sink = new HttpGovernanceAuditSink(
                endpoint(), Map.of("X-Audit-Source", "unit-test"), 3, 60_000, 2000, 100, false)) {
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("account", "a-1");
            for (int i = 0; i < 6; i++) {
                sink.record(event("finance_transfer", "APPROVED", "T4", args, "trace-" + i));
            }
            assertTrue(latch.await(5, TimeUnit.SECONDS), "expected 2 HTTP batch requests");
            sentCount = sink.sentCount();
            failedCount = sink.failedCount();
        }
        assertEquals(2, receivedBodies.size(), "6 events / batch 3 = 2 requests");
        // 每个请求体都是 3 元素 JSON 数组；两个批次并发到达顺序不定，验证整体 traceId 集合
        ObjectMapper om = new ObjectMapper();
        java.util.Set<String> seenTraces = new java.util.HashSet<>();
        for (String body : receivedBodies) {
            List<?> arr = om.readValue(body, List.class);
            assertEquals(3, arr.size());
            for (Object o : arr) {
                Map<?, ?> m = (Map<?, ?>) o;
                assertEquals("finance_transfer", m.get("tool"));
                assertEquals("00f067aa0ba902b7", m.get("spanId"));
                seenTraces.add((String) m.get("traceId"));
            }
        }
        assertEquals(java.util.Set.of("trace-0", "trace-1", "trace-2", "trace-3", "trace-4", "trace-5"),
                seenTraces);
        assertEquals(6, sentCount);
        assertEquals(0, failedCount);
        // 附加请求头透传
        assertTrue(lastRequestHeaders.containsKey("X-Audit-Source"),
                "custom header must be forwarded");
        assertEquals(List.of("unit-test"), lastRequestHeaders.get("X-Audit-Source"));
    }

    @Test
    void flushesByIntervalAsFallback() throws Exception {
        latch = new CountDownLatch(1);
        long flushMs = 400;
        // batch 很大 -> 靠定时 flush 兜底
        int sent = 0;
        try (HttpGovernanceAuditSink sink = new HttpGovernanceAuditSink(
                endpoint(), null, 1000, flushMs, 2000, 100, false)) {
            sink.record(event("db_query", "ALLOWED", "T2", Map.of(), "trace-a"));
            assertTrue(latch.await(5, TimeUnit.SECONDS), "timer flush should send pending events");
            sent = sinkSentCountVia(receivedBodies);
        }
        assertEquals(1, receivedBodies.size());
        assertEquals(1, sent);
    }

    private int sinkSentCountVia(List<String> bodies) {
        // 简化断言：1 批 1 条
        ObjectMapper om = new ObjectMapper();
        try {
            List<?> arr = om.readValue(bodies.get(0), List.class);
            return arr.size();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void keepsLocalViewSemantics() {
        try (HttpGovernanceAuditSink sink = new HttpGovernanceAuditSink(
                endpoint(), null, 100, 60_000, 2000, 100, false)) {
            Map<String, Object> args = Map.of("id", "x");
            sink.record(event("read_secret", "ALLOWED", "T3", args, "trace-1"));
            sink.record(event("read_secret", "DENIED", "T3", args, "trace-2"));

            List<Map<String, Object>> recent = sink.recent(10);
            assertEquals(2, recent.size());
            assertEquals("DENIED", recent.get(0).get("decision")); // 时间倒序

            List<Map<String, Object>> denied = sink.search(
                    new McpGovernanceAuditSink.Query(null, null, "DENIED", null, null, null, 10));
            assertEquals(1, denied.size());
            assertEquals("trace-2", denied.get(0).get("traceId"));

            // V1.31 traceId 过滤
            List<Map<String, Object>> byTrace = sink.search(
                    new McpGovernanceAuditSink.Query(null, null, null, null, null, null, 10, "trace-1"));
            assertEquals(1, byTrace.size());
            assertEquals("ALLOWED", byTrace.get(0).get("decision"));

            int removed = sink.deleteBefore(Instant.now().plusSeconds(1));
            assertEquals(2, removed);
            assertEquals(0, sink.recent(10).size());
        }
    }

    @Test
    void failSoftOnUnreachableEndpoint() throws IOException {
        // 指向未监听端口：记录不抛异常，事件保留在本地队列待重试
        // 拿到一个空闲端口后立即释放（此刻几乎肯定无人监听），避免 +偏移 越界 65535
        int deadPort;
        try (java.net.ServerSocket tmp = new java.net.ServerSocket(0)) {
            deadPort = tmp.getLocalPort();
        }
        String deadEndpoint = "http://127.0.0.1:" + deadPort + "/audit";
        try (HttpGovernanceAuditSink sink = new HttpGovernanceAuditSink(
                deadEndpoint, null, 10, 200, 500, 100, false)) {
            sink.record(event("db_query", "ALLOWED", "T2", Map.of(), "trace-1"));
            // 等 2 个 flush 周期让发送失败发生（connect 超时 500ms）
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            assertTrue(sink.failedCount() > 0, "unreachable endpoint should count as failed");
            // 本地视图仍可用（审计不丢）
            assertEquals(1, sink.recent(10).size());
        }
    }
}