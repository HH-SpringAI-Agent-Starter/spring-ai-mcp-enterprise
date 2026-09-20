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
}