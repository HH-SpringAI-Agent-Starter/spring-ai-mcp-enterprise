package com.mcp.tool.database;

import com.mcp.enterprise.core.model.ToolDefinition;
import com.mcp.enterprise.core.tool.McpToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * 数据库查询 MCP 工具
 *
 * 允许 AI Agent 通过安全隔离的 SQL 查询数据库。
 * 默认仅支持 SELECT 查询，防止写操作。
 */
@Component
@ConditionalOnProperty(name = "mcp.tool.database.enabled", havingValue = "true", matchIfMissing = true)
public class DatabaseQueryExecutor implements McpToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(DatabaseQueryExecutor.class);

    private final JdbcTemplate jdbcTemplate;
    private final boolean readOnly;

    public DatabaseQueryExecutor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.readOnly = true; // 强制只读
    }

    @Override
    public ToolDefinition getDefinition() {
        ToolDefinition def = new ToolDefinition();
        def.setName("database_query");
        def.setDisplayName("数据库查询");
        def.setDescription("执行只读 SQL SELECT 查询，支持多表联查。默认限制返回 100 行。");
        def.setCategory("database");
        def.setVersion("1.0.0");
        def.setModule("tool-database");
        def.setRequiredRoles("admin,user");
        def.setTimeoutMs(30000);
        def.setRateLimitPerSecond(5);

        Map<String, Object> inputSchema = new LinkedHashMap<>();
        inputSchema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("sql", Map.of(
                "type", "string",
                "description", "要执行的 SQL SELECT 查询语句（仅支持 SELECT）",
                "examples", List.of("SELECT * FROM users LIMIT 10", "SELECT COUNT(*) FROM orders WHERE status = 'active'")
        ));
        properties.put("maxRows", Map.of(
                "type", "integer",
                "description", "最大返回行数（默认 100，最大 1000）",
                "default", 100
        ));
        properties.put("timeoutSeconds", Map.of(
                "type", "integer",
                "description", "查询超时时间（秒，默认 30）",
                "default", 30
        ));

        inputSchema.put("properties", properties);
        inputSchema.put("required", List.of("sql"));

        def.setInputSchema(inputSchema);

        return def;
    }

    @Override
    public Mono<Map<String, Object>> execute(Map<String, Object> params) {
        String sql = (String) params.getOrDefault("sql", "").toString().trim();
        int maxRows = params.containsKey("maxRows") ? ((Number) params.get("maxRows")).intValue() : 100;
        int timeoutSeconds = params.containsKey("timeoutSeconds") ? ((Number) params.get("timeoutSeconds")).intValue() : 30;

        // 安全检查：只允许 SELECT
        if (!sql.toUpperCase().startsWith("SELECT") && !sql.toUpperCase().startsWith("WITH")) {
            return Mono.just(Map.of(
                    "success", false,
                    "error", "只允许 SELECT 或 WITH 查询",
                    "tool", "database_query"
            ));
        }

        // 禁止危险操作
        String upper = sql.toUpperCase();
        if (upper.contains("INTO") || upper.contains("DROP") || upper.contains("DELETE")
                || upper.contains("UPDATE") || upper.contains("INSERT")
                || upper.contains("ALTER") || upper.contains("TRUNCATE")
                || upper.contains("EXEC") || upper.contains("CREATE")) {
            return Mono.just(Map.of(
                    "success", false,
                    "error", "不允许的 SQL 操作：仅允许只读查询",
                    "tool", "database_query"
            ));
        }

        // 限制行数
        if (maxRows > 1000) maxRows = 1000;
        if (maxRows < 1) maxRows = 1;

        long startTime = System.currentTimeMillis();

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            long elapsed = System.currentTimeMillis() - startTime;

            // 限制返回行数
            List<Map<String, Object>> resultRows = rows.size() > maxRows ? rows.subList(0, maxRows) : rows;

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("tool", "database_query");
            result.put("rows", resultRows);
            result.put("totalRows", rows.size());
            result.put("returnedRows", resultRows.size());
            result.put("columns", rows.isEmpty() ? List.of() : new ArrayList<>(rows.get(0).keySet()));
            result.put("elapsedMs", elapsed);
            result.put("truncated", rows.size() > maxRows);

            return Mono.just(result);
        } catch (Exception e) {
            log.warn("数据库查询失败: {}", e.getMessage());
            return Mono.just(Map.of(
                    "success", false,
                    "error", "查询执行失败: " + e.getMessage(),
                    "tool", "database_query",
                    "sql", sql
            ));
        }
    }
}
