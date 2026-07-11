package com.mcp.tool.database;

import com.mcp.enterprise.core.model.ToolDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DatabaseQueryExecutor 单元测试
 *
 * 使用嵌入式 H2 数据库模拟真实查询场景
 */
class DatabaseQueryExecutorTest {

    private DatabaseQueryExecutor executor;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // 每次测试重建嵌入式数据库（包含 schema + 测试数据）
        DataSource ds = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("testdb_" + System.nanoTime())
                .addScript("classpath:test-schema.sql")
                .build();
        jdbcTemplate = new JdbcTemplate(ds);
        executor = new DatabaseQueryExecutor(jdbcTemplate);
    }

    @Test
    void testDefinition() {
        ToolDefinition def = executor.getDefinition();
        assertEquals("database_query", def.getName());
        assertEquals("database", def.getCategory());
        assertNotNull(def.getInputSchema());
        assertTrue(def.getRequiredRoles().contains("admin"));
    }

    @Test
    void testBasicSelect() {
        Map<String, Object> result = executor.execute(Map.of("sql", "SELECT * FROM users ORDER BY id"))
                .block();

        assertNotNull(result);
        assertEquals(true, result.get("success"));
        assertEquals(3, result.get("totalRows"));
        assertEquals(3, result.get("returnedRows"));
    }

    @Test
    void testLimit() {
        Map<String, Object> result = executor.execute(Map.of("sql", "SELECT * FROM users ORDER BY id", "maxRows", 1))
                .block();

        assertNotNull(result);
        assertEquals(true, result.get("success"));
        assertEquals(3, result.get("totalRows"));
        assertEquals(1, result.get("returnedRows"));
        assertEquals(true, result.get("truncated"));
    }

    @Test
    void testRejectWriteOperations() {
        assertRejected("INSERT INTO users VALUES (4, 'Dave', 'dave@test.com', 'active')");
        assertRejected("UPDATE users SET name = 'test' WHERE id = 1");
        assertRejected("DELETE FROM users WHERE id = 1");
        assertRejected("DROP TABLE users");
        assertRejected("ALTER TABLE users ADD COLUMN age INT");
        assertRejected("TRUNCATE TABLE users");
        assertRejected("CREATE TABLE temp (id INT)");
    }

    private void assertRejected(String sql) {
        Map<String, Object> result = executor.execute(Map.of("sql", sql)).block();
        assertNotNull(result);
        assertEquals(false, result.get("success"));
        assertTrue(((String) result.get("error")).contains("不允许") || ((String) result.get("error")).contains("只允许"),
                "SQL '" + sql + "' 应该被拒绝, 但错误信息是: " + result.get("error"));
    }

    @Test
    void testEmptySql() {
        Map<String, Object> result = executor.execute(Map.of("sql", "  ")).block();
        assertNotNull(result);
        assertEquals(false, result.get("success"));
    }

    @Test
    void testMaxRowsLimit() {
        Map<String, Object> result = executor.execute(Map.of("sql", "SELECT * FROM users", "maxRows", 999999))
                .block();
        assertNotNull(result);
        assertEquals(true, result.get("success"));
    }

    @Test
    void testInvalidSql() {
        Map<String, Object> result = executor.execute(Map.of("sql", "SELECT * FROM nonexistent_table"))
                .block();
        assertNotNull(result);
        assertEquals(false, result.get("success"));
        assertNotNull(result.get("error"));
    }

    @Test
    void testWithClause() {
        Map<String, Object> result = executor.execute(Map.of(
                "sql", "WITH active_users AS (SELECT * FROM users WHERE status = 'active') SELECT * FROM active_users"))
                .block();
        assertNotNull(result);
        assertEquals(true, result.get("success"));
    }

    @Test
    void testColumnsMetadata() {
        Map<String, Object> result = executor.execute(Map.of("sql", "SELECT * FROM users"))
                .block();
        assertNotNull(result);
        List<String> columns = (List<String>) result.get("columns");
        assertTrue(columns.contains("ID"));
        assertTrue(columns.contains("NAME"));
        assertTrue(columns.contains("STATUS"));
    }

    @Test
    void testJoinQuery() {
        Map<String, Object> result = executor.execute(Map.of("sql",
                "SELECT u.name, o.product, o.amount FROM users u JOIN orders o ON u.id = o.user_id"))
                .block();
        assertNotNull(result);
        assertEquals(true, result.get("success"));
        assertEquals(3, result.get("totalRows"));
    }
}
