package com.mcp.enterprise.governance;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V1.29 JdbcGovernanceAuditSink 集成测试（H2 内存库）。
 * 验证：建表幂等、record 落库往返、recent 时间倒序、count、
 * 脱敏参数 JSON 特殊值往返、fail-soft（不因单条失败打挂调用）。
 */
class JdbcGovernanceAuditSinkTest {

    private EmbeddedDatabase db;
    private JdbcTemplate jdbc;
    private JdbcGovernanceAuditSink sink;

    @BeforeEach
    void setUp() {
        db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .build();
        jdbc = new JdbcTemplate(db);
        sink = new JdbcGovernanceAuditSink(jdbc, "mcp_governance_audit", false);
        sink.initSchema();
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    private McpGovernanceAuditSink.Event event(String tool, String decision, String tier,
                                               Map<String, Object> args) {
        return new McpGovernanceAuditSink.Event(Instant.now(), tool, tier, "caller-a",
                decision, "apr-1", args, "unit test event");
    }

    @Test
    void initSchemaIsIdempotent() {
        // 二次建表不报错
        sink.initSchema();
        assertTrue(sink.count() == 0);
    }

    @Test
    void recordAndRecentRoundTrip() {
        sink.record(event("finance_transfer", "APPROVAL_REQUIRED", "T4", Map.of("amount", 100)));
        sink.record(event("user_query", "ALLOW", "T1", Map.of("q", "hello")));

        List<Map<String, Object>> recent = sink.recent(10);
        assertEquals(2, recent.size());

        // 时间倒序：后写入的在前
        Map<String, Object> first = recent.get(0);
        assertEquals("user_query", first.get("tool"));
        assertEquals("T1", first.get("tier"));
        assertEquals("ALLOW", first.get("decision"));
        assertEquals("caller-a", first.get("caller"));
        assertEquals("apr-1", first.get("approvalId"));
        assertEquals("unit test event", first.get("message"));
        assertNotNull(first.get("timestamp"));
    }

    @Test
    void argumentsJsonSpecialValuesRoundTrip() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("text", "长字符串 a".repeat(50));
        args.put("amount", 1234.56);
        args.put("flag", true);
        args.put("nested", List.of("x", "y", 1));
        sink.record(event("legacy_tool", "ALLOW", "T2", args));

        List<Map<String, Object>> recent = sink.recent(5);
        assertEquals(1, recent.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> roundTrip = (Map<String, Object>) recent.get(0).get("arguments");
        assertNotNull(roundTrip);
        assertEquals("长字符串 a".repeat(50), roundTrip.get("text"));
        assertEquals(1234.56, roundTrip.get("amount"));
        assertEquals(true, roundTrip.get("flag"));
        assertEquals(List.of("x", "y", 1), roundTrip.get("nested"));
    }

    @Test
    void recentLimitWorks() {
        for (int i = 0; i < 10; i++) {
            sink.record(event("tool_" + i, "ALLOW", "T2", Map.of("i", i)));
        }
        List<Map<String, Object>> limited = sink.recent(3);
        assertEquals(3, limited.size());
        // 倒序：tool_9 在最前
        assertEquals("tool_9", limited.get(0).get("tool"));
    }

    @Test
    void countTracksTotalEvents() {
        assertEquals(0, sink.count());
        sink.record(event("a", "ALLOW", "T2", Map.of()));
        sink.record(event("b", "ALLOW", "T2", Map.of()));
        assertEquals(2, sink.count());
    }

    @Test
    void nullEventIsIgnored() {
        sink.record(null);
        assertEquals(0, sink.count());
    }

    @Test
    void oversizedArgumentsAreTruncatedToValidJson() {
        Map<String, Object> args = Map.of("big", "x".repeat(6000));
        sink.record(event("big_tool", "ALLOW", "T2", args));
        List<Map<String, Object>> recent = sink.recent(5);
        assertEquals(1, recent.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> roundTrip = (Map<String, Object>) recent.get(0).get("arguments");
        // 值级截断到 400 字符且 JSON 合法可反序列化
        assertEquals(400, ((String) roundTrip.get("big")).length());
    }

    @Test
    void recordFailureIsFailSoft() {
        // 建一个指向不存在表的新 sink —— record 应吞掉异常记录 WARN，不抛给上层
        JdbcGovernanceAuditSink brokenSink = new JdbcGovernanceAuditSink(jdbc, "no_such_table_xyz", true);
        brokenSink.record(event("finance_transfer", "APPROVAL_REQUIRED", "T4", Map.of("amount", 1)));
        // 正常 sink 继续可用
        sink.record(event("ok_tool", "ALLOW", "T2", Map.of()));
        assertEquals(1, sink.count());
    }

    // ===== V1.30: search + retention purge =====

    @Test
    void searchFiltersByToolAndDecision() {
        sink.record(event("finance_transfer", "APPROVAL_REQUIRED", "T4", Map.of("amount", 100)));
        sink.record(event("user_query", "ALLOW", "T1", Map.of()));
        sink.record(event("finance_transfer", "ALLOW", "T4", Map.of("amount", 50)));

        List<Map<String, Object>> hits = sink.search(new McpGovernanceAuditSink.Query(
                "finance_transfer", null, "ALLOW", null, null, null, 50));
        assertEquals(1, hits.size());
        assertEquals("finance_transfer", hits.get(0).get("tool"));
        assertEquals("ALLOW", hits.get(0).get("decision"));
    }

    @Test
    void searchFiltersByCallerAndTimeRange() {
        sink.record(event("tool_a", "ALLOW", "T2", Map.of()));
        sink.record(event("tool_b", "DENY", "T2", Map.of()));

        java.time.Instant from = java.time.Instant.now().minusSeconds(60);
        java.time.Instant to = java.time.Instant.now().plusSeconds(60);
        List<Map<String, Object>> hits = sink.search(new McpGovernanceAuditSink.Query(
                null, "caller-a", null, null, from, to, 50));
        assertEquals(2, hits.size());

        java.time.Instant past = java.time.Instant.now().minusSeconds(3600);
        List<Map<String, Object>> none = sink.search(new McpGovernanceAuditSink.Query(
                null, "caller-a", null, null, null, past, 50));
        assertTrue(none.isEmpty());
    }

    @Test
    void searchEmptyQueryEqualsRecent() {
        for (int i = 0; i < 5; i++) {
            sink.record(event("tool_" + i, "ALLOW", "T2", Map.of()));
        }
        List<Map<String, Object>> all = sink.search(new McpGovernanceAuditSink.Query(
                null, null, null, null, null, null, 10));
        assertEquals(5, all.size());
        assertEquals(5, sink.recent(10).size());
    }

    @Test
    void searchLimitCapsAt1000() {
        for (int i = 0; i < 20; i++) {
            sink.record(event("tool_" + i, "ALLOW", "T2", Map.of()));
        }
        List<Map<String, Object>> hits = sink.search(new McpGovernanceAuditSink.Query(
                null, null, null, null, null, null, 99999));
        assertEquals(20, hits.size());
    }

    @Test
    void deleteBeforePurgesOldEventsOnly() {
        java.time.Instant old = java.time.Instant.now().minusSeconds(86400);
        sink.record(new McpGovernanceAuditSink.Event(old, "old_tool", "T2", "caller-a",
                "ALLOW", null, Map.of(), "old event"));
        sink.record(event("fresh_tool", "ALLOW", "T2", Map.of()));

        int deleted = sink.deleteBefore(java.time.Instant.now().minusSeconds(3600));
        assertEquals(1, deleted);

        List<Map<String, Object>> remaining = sink.search(new McpGovernanceAuditSink.Query(
                null, null, null, null, null, null, 10));
        assertEquals(1, remaining.size());
        assertEquals("fresh_tool", remaining.get(0).get("tool"));
    }

    @Test
    void deleteBeforeNullIsNoop() {
        sink.record(event("a", "ALLOW", "T2", Map.of()));
        assertEquals(0, sink.deleteBefore(null));
        assertEquals(1, sink.count());
    }

    // ===== V1.31: trace 关联（OpenTelemetry W3C traceparent）=====

    private McpGovernanceAuditSink.Event tracedEvent(String tool, String traceId, String spanId) {
        return new McpGovernanceAuditSink.Event(Instant.now(), tool, "T2", "caller-a",
                "ALLOW", null, Map.of(), "traced event", traceId, spanId);
    }

    @Test
    void traceColumnsPersistRoundTrip() {
        String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        String spanId = "00f067aa0ba902b7";
        sink.record(tracedEvent("db_query", traceId, spanId));

        List<Map<String, Object>> recent = sink.recent(5);
        assertEquals(1, recent.size());
        assertEquals(traceId, recent.get(0).get("traceId"));
        assertEquals(spanId, recent.get(0).get("spanId"));
    }

    @Test
    void searchFiltersByTraceId() {
        String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        sink.record(tracedEvent("db_query", traceId, "00f067aa0ba902b7"));
        sink.record(tracedEvent("other_tool", "8b1d4f2a9c6e3d7f1a2b3c4d5e6f70819", null));

        List<Map<String, Object>> hits = sink.search(new McpGovernanceAuditSink.Query(
                null, null, null, null, null, null, 50, traceId));
        assertEquals(1, hits.size());
        assertEquals("db_query", hits.get(0).get("tool"));
    }

    @Test
    void searchTraceIdAndToolCombined() {
        String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        sink.record(tracedEvent("db_query", traceId, null));
        sink.record(tracedEvent("http_call", traceId, null));

        List<Map<String, Object>> hits = sink.search(new McpGovernanceAuditSink.Query(
                "db_query", null, null, null, null, null, 50, traceId));
        assertEquals(1, hits.size());
        assertEquals("db_query", hits.get(0).get("tool"));
    }

    @Test
    void upgradeFromOldSchemaAddsTraceColumns() {
        // 模拟 V1.30 老表：无 trace 列
        jdbc.execute("CREATE TABLE legacy_audit (" +
                "id VARCHAR(64) NOT NULL PRIMARY KEY, seq BIGINT NOT NULL, ts BIGINT NOT NULL, " +
                "tool VARCHAR(256) NOT NULL, tier VARCHAR(16), caller VARCHAR(256), " +
                "decision VARCHAR(32) NOT NULL, approval_id VARCHAR(64), " +
                "arguments VARCHAR(4000), message VARCHAR(1024))");
        JdbcGovernanceAuditSink legacy = new JdbcGovernanceAuditSink(jdbc, "legacy_audit", false);
        legacy.initSchema(); // 应幂等补列不报错

        legacy.record(tracedEvent("db_query", "4bf92f3577b34da6a3ce929d0e0e4736", null));
        List<Map<String, Object>> rows = legacy.recent(5);
        assertEquals(1, rows.size());
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", rows.get(0).get("traceId"));
    }
}