package com.mcp.enterprise.governance;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V1.28 {@link JdbcApprovalStore} H2 集成测试：
 * 验证 JDBC 审批存储的完整读写往返（含参数 JSON 序列化与状态流转持久化）。
 */
class JdbcApprovalStoreTest {

    private JdbcTemplate jdbc;
    private JdbcApprovalStore store;

    @BeforeEach
    void setUp() {
        DataSource ds = new DriverManagerDataSource(
                "jdbc:h2:mem:govtest;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds);
        store = new JdbcApprovalStore(jdbc, "mcp_approval_requests_test");
        store.initSchema();
    }

    @AfterEach
    void tearDown() {
        jdbc.execute("DROP TABLE IF EXISTS mcp_approval_requests_test");
    }

    @Test
    void saveThenGetRoundTrip() {
        ApprovalRequest req = new ApprovalRequest(
                "finance_transfer", RiskTier.T4,
                Map.of("amount", 1000, "toAccount", "6222****8888"),
                "alice", "monthly vendor payment", 900);
        store.save(req);

        ApprovalRequest loaded = store.get(req.getId());
        assertNotNull(loaded);
        assertEquals(req.getId(), loaded.getId());
        assertEquals("finance_transfer", loaded.getToolName());
        assertEquals("T4", loaded.getTierCode());
        assertEquals("alice", loaded.getRequestedBy());
        assertEquals(ApprovalRequest.Status.PENDING, loaded.getStatus());
        assertEquals(1000, loaded.getArguments().get("amount"));
        assertEquals("6222****8888", loaded.getArguments().get("toAccount"));
        // 数据库 epoch-millis 精度：与写入方毫秒对齐
        assertEquals(req.getCreatedAt().toEpochMilli(), loaded.getCreatedAt().toEpochMilli());
        assertEquals(req.getExpiresAt().toEpochMilli(), loaded.getExpiresAt().toEpochMilli());
    }

    @Test
    void listFiltersByStatusAndOrdersDesc() throws Exception {
        ApprovalRequest a = new ApprovalRequest("tool_a", RiskTier.T3, Map.of(), "u1", "r1", 900);
        store.save(a);
        // 错开创建时间，确保倒序顺序可断言（数据库 epoch-millis 精度）
        Thread.sleep(5);
        ApprovalRequest b = new ApprovalRequest("tool_b", RiskTier.T4, Map.of(), "u2", "r2", 900);
        store.save(b);
        store.update(approve(b, "admin"));

        List<ApprovalRequest> pending = store.list(ApprovalRequest.Status.PENDING);
        assertEquals(1, pending.size());
        assertEquals(a.getId(), pending.get(0).getId());

        List<ApprovalRequest> approved = store.list(ApprovalRequest.Status.APPROVED);
        assertEquals(1, approved.size());
        assertEquals(b.getId(), approved.get(0).getId());

        List<ApprovalRequest> all = store.list(null);
        assertEquals(2, all.size());
        // 倒序：后创建的在前面
        assertEquals(b.getId(), all.get(0).getId());
        assertEquals(a.getId(), all.get(1).getId());
    }

    @Test
    void updatePersistsStatusTransition() {
        ApprovalRequest req = new ApprovalRequest("tool_c", RiskTier.T4, Map.of(), "u3", "r3", 900);
        store.save(req);

        store.update(approve(req, "admin"));
        ApprovalRequest loaded = store.get(req.getId());
        assertEquals(ApprovalRequest.Status.APPROVED, loaded.getStatus());
        assertNotNull(loaded.getDecidedAt());
        assertEquals("admin", loaded.getDecidedBy());
    }

    @Test
    void countReflectsRows() {
        assertEquals(0, store.count());
        store.save(new ApprovalRequest("tool_d", RiskTier.T1, Map.of(), "u4", "r4", 900));
        store.save(new ApprovalRequest("tool_e", RiskTier.T2, Map.of("q", "x"), "u5", "r5", 900));
        assertEquals(2, store.count());
    }

    @Test
    void getMissingReturnsNull() {
        assertNull(store.get("no-such-id"));
    }

    @Test
    void argumentsRoundTripWithSpecialValues() {
        String longValue = "x".repeat(500);
        ApprovalRequest req = new ApprovalRequest("tool_f", RiskTier.T3,
                Map.of("s", longValue, "n", 3.14, "ok", true, "list", List.of("a", "b")),
                "u6", "r6", 900);
        store.save(req);

        ApprovalRequest loaded = store.get(req.getId());
        assertEquals(longValue, loaded.getArguments().get("s"));
        assertEquals(3.14, ((Number) loaded.getArguments().get("n")).doubleValue(), 0.0001);
        assertEquals(Boolean.TRUE, loaded.getArguments().get("ok"));
        assertEquals(List.of("a", "b"), loaded.getArguments().get("list"));
    }

    @Test
    void expiryRoundTrip() {
        ApprovalRequest req = new ApprovalRequest("tool_g", RiskTier.T2, Map.of(), "u7", "r7", 60);
        store.save(req);
        ApprovalRequest loaded = store.get(req.getId());
        assertEquals(60, loaded.getExpiresAt().getEpochSecond() - loaded.getCreatedAt().getEpochSecond());
    }

    private static ApprovalRequest approve(ApprovalRequest req, String by) {
        req.setStatus(ApprovalRequest.Status.APPROVED);
        req.setDecidedAt(Instant.now());
        req.setDecidedBy(by);
        return req;
    }
}