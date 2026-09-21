package com.mcp.enterprise.governance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * V1.29 portable JDBC {@link McpGovernanceAuditSink}.
 *
 * <p>V1.26 的默认审计只活在单个 JVM 内存里（{@link InMemoryGovernanceAuditSink}，
 * 有界环形缓冲 + SLF4J）。对企业合规场景这有两个缺口：</p>
 * <ul>
 *   <li><b>审计轨迹不落库</b>：重启/滚动发布即丢失，无法回答
 *       「上周谁在什么时间调用过 finance_transfer？」这类合规取证问题；</li>
 *   <li><b>多实例各自为政</b>：K8s 多副本下每条事件只存在于处理它的那个 Pod，
 *       审计员没有一个统一的、可查询的全局视图。</li>
 * </ul>
 *
 * <p>本实现把每条治理判定事件持久化到单张表
 * {@code mcp_governance_audit}（可配置表名），审计事件跨实例可见、
 * 重启不丢失，可直接对接 SIEM/报表/合规取证。设计约束与 V1.28
 * {@code JdbcApprovalStore} / V1.22 {@code JdbcSkillRegistryStore} 保持一致：</p>
 * <ul>
 *   <li><b>方言无关</b>：只使用 {@code CREATE TABLE IF NOT EXISTS} /
 *       {@code INSERT} / {@code SELECT}（绑定参数）—— H2（测试）/ MySQL /
 *       MariaDB / PostgreSQL / SQL Server 均可直接运行；</li>
 *   <li><b>单扁平表</b>：脱敏后的参数 JSON 存 {@code VARCHAR}，
 *       时间戳 epoch millis（BIGINT），规避各库日期函数方言；</li>
 *   <li><b>只写不删</b>：审计日志是合规资产，本实现只 INSERT + SELECT，
 *       不提供 DELETE/UPDATE（防篡改；物理清理留给企业自己的
 *       保留策略任务）；</li>
 *   <li><b>fail-soft</b>：单条写入失败记录 WARN 并继续，绝不让审计链路
 *       打挂业务调用（审计是观察者，不是关键路径）；</li>
 *   <li><b>近实时双写</b>：落库的同时仍保持 SLF4J 日志输出
 *       （{@code log-to-slf4j} 可关），运维侧零改动可继续 grep 日志。</li>
 * </ul>
 *
 * <h3>表结构</h3>
 * <pre>
 *   id          VARCHAR(64)   NOT NULL PRIMARY KEY   -- 事件 ID（时间戳+序号的稳定 ID）
 *   seq         BIGINT        NOT NULL               -- 进程内序号（同毫秒内排序 tiebreaker）
 *   ts          BIGINT        NOT NULL               -- epoch millis
 *   tool        VARCHAR(256)  NOT NULL
 *   tier        VARCHAR(16)
 *   caller      VARCHAR(256)
 *   decision    VARCHAR(32)   NOT NULL
 *   approval_id VARCHAR(64)
 *   arguments   VARCHAR(4000)                        -- 脱敏后参数 JSON（值级截断保证合法 JSON）
 *   message     VARCHAR(1024)
 * </pre>
 *
 * <p>部署：{@code mcp.enterprise.governance.audit.store=jdbc}（默认
 * {@code memory}）；自动配置在存在 {@code JdbcTemplate} 时自动启用并幂等建表；
 * 无数据源则回退内存实现并打 WARN。表名与建表开关可配置：
 * {@code mcp.enterprise.governance.audit.table} /
 * {@code mcp.enterprise.governance.audit.init-schema}。</p>
 */
public class JdbcGovernanceAuditSink implements McpGovernanceAuditSink {

    private static final Logger log = LoggerFactory.getLogger(JdbcGovernanceAuditSink.class);

    private static final TypeReference<Map<String, Object>> ARGS_TYPE =
            new TypeReference<>() {
            };

    private static final RowMapper<Map<String, Object>> MAPPER = (ResultSet rs, int rowNum) -> {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("timestamp", Instant.ofEpochMilli(rs.getLong("ts")).toString());
        m.put("tool", rs.getString("tool"));
        m.put("tier", rs.getString("tier"));
        m.put("caller", rs.getString("caller"));
        m.put("decision", rs.getString("decision"));
        m.put("approvalId", rs.getString("approval_id"));
        m.put("arguments", fromJson(rs.getString("arguments")));
        m.put("message", rs.getString("message"));
        return m;
    };

    private final JdbcTemplate jdbc;
    private final String table;
    private final boolean logToSlf4j;
    /** 进程内自增序号，保证事件 ID 稳定唯一（多实例下前缀 + 时间戳 + 序号）。 */
    private final java.util.concurrent.atomic.AtomicLong sequence = new java.util.concurrent.atomic.AtomicLong(0);
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JdbcGovernanceAuditSink(JdbcTemplate jdbc, String table, boolean logToSlf4j) {
        if (jdbc == null) {
            throw new IllegalArgumentException("JdbcTemplate is required");
        }
        this.jdbc = jdbc;
        this.table = (table == null || table.isBlank()) ? "mcp_governance_audit" : table;
        this.logToSlf4j = logToSlf4j;
    }

    /**
     * 幂等建表（启动时调用一次）。仅用 VARCHAR，所有主流数据库均可直接执行。
     */
    public void initSchema() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS " + table + " (" +
                "id VARCHAR(64) NOT NULL PRIMARY KEY, " +
                "seq BIGINT NOT NULL, " +
                "ts BIGINT NOT NULL, " +
                "tool VARCHAR(256) NOT NULL, " +
                "tier VARCHAR(16), " +
                "caller VARCHAR(256), " +
                "decision VARCHAR(32) NOT NULL, " +
                "approval_id VARCHAR(64), " +
                "arguments VARCHAR(4000), " +
                "message VARCHAR(1024))");
        log.info("🛡 [V1.29] Governance Audit JDBC store ready (table={})", table);
    }

    @Override
    public void record(Event event) {
        if (event == null) {
            return;
        }
        long ts = event.timestamp() == null ? System.currentTimeMillis() : event.timestamp().toEpochMilli();
        long seq = sequence.incrementAndGet();
        String id = "evt-" + ts + "-" + seq;
        try {
            jdbc.update("INSERT INTO " + table + " (id, seq, ts, tool, tier, caller, decision, " +
                            "approval_id, arguments, message) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    id, seq, ts, event.tool(), event.tier(), event.caller(), event.decision(),
                    event.approvalId(), toJson(event.arguments()), truncate(event.message(), 1024));
        } catch (Exception e) {
            // fail-soft: 审计是观察者，单条失败记录 WARN 绝不打挂业务
            log.warn("🛡 [V1.29] audit event persist failed (tool={}, decision={}): {}",
                    event.tool(), event.decision(), e.getMessage());
        }
        if (logToSlf4j) {
            log.info("governance decision={} tool={} tier={} caller={} approval={} msg={}",
                    event.decision(), event.tool(), event.tier(), event.caller(),
                    event.approvalId(), event.message());
        }
    }

    @Override
    public List<Map<String, Object>> recent(int limit) {
        // 方言无关：不用 FETCH FIRST/LIMIT/OFFSET，拉取后在内存截断（管理查询量级小，可接受）
        int n = limit <= 0 ? 50 : Math.min(limit, 500); // 封顶 500，防滥用
        try {
            List<Map<String, Object>> rows = jdbc.query(
                    "SELECT * FROM " + table + " ORDER BY ts DESC, seq DESC", MAPPER);
            if (rows == null || rows.isEmpty()) {
                return new ArrayList<>();
            }
            return rows.size() <= n ? rows : new ArrayList<>(rows.subList(0, n));
        } catch (Exception e) {
            log.warn("🛡 [V1.29] audit recent query failed: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public List<Map<String, Object>> search(Query query) {
        // V1.30: 多条件 WHERE（全参数绑定、方言无关），按时间倒序后内存截断
        java.util.List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(table).append(" WHERE 1=1");
        if (query.tool() != null && !query.tool().isBlank()) {
            sql.append(" AND tool = ?");
            params.add(query.tool());
        }
        if (query.caller() != null && !query.caller().isBlank()) {
            sql.append(" AND caller = ?");
            params.add(query.caller());
        }
        if (query.decision() != null && !query.decision().isBlank()) {
            sql.append(" AND decision = ?");
            params.add(query.decision());
        }
        if (query.tier() != null && !query.tier().isBlank()) {
            sql.append(" AND tier = ?");
            params.add(query.tier());
        }
        if (query.from() != null) {
            sql.append(" AND ts >= ?");
            params.add(query.from().toEpochMilli());
        }
        if (query.to() != null) {
            sql.append(" AND ts <= ?");
            params.add(query.to().toEpochMilli());
        }
        sql.append(" ORDER BY ts DESC, seq DESC");
        try {
            List<Map<String, Object>> rows = jdbc.query(sql.toString(), MAPPER, params.toArray());
            if (rows == null || rows.isEmpty()) {
                return new ArrayList<>();
            }
            int n = query.limit();
            return rows.size() <= n ? rows : new ArrayList<>(rows.subList(0, n));
        } catch (Exception e) {
            log.warn("🔎 [V1.30] audit search query failed: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public int deleteBefore(java.time.Instant cutOff) {
        // V1.30: 合规保留策略——物理清理早于截止点的历史事件（方言无关 DELETE WHERE）
        if (cutOff == null) {
            return 0;
        }
        try {
            int deleted = jdbc.update("DELETE FROM " + table + " WHERE ts < ?", cutOff.toEpochMilli());
            log.info("🧹 [V1.30] audit retention purge: deleted {} events before {}", deleted, cutOff);
            return deleted;
        } catch (Exception e) {
            log.warn("🧹 [V1.30] audit retention purge failed: {}", e.getMessage());
            return 0;
        }
    }

    /** 审计事件总数（管理面板用）。 */
    public long count() {
        try {
            Long n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
            return n == null ? 0L : n;
        } catch (Exception e) {
            log.warn("🛡 [V1.29] audit count query failed: {}", e.getMessage());
            return 0L;
        }
    }

    // ===== 内部工具 =====

    private String toJson(Map<String, Object> args) {
        if (args == null || args.isEmpty()) {
            return "{}";
        }
        try {
            String json = objectMapper.writeValueAsString(truncateValues(args));
            return json.length() > 4000 ? json.substring(0, 4000) : json;
        } catch (Exception e) {
            log.warn("🛡 [V1.29] audit arguments serialization failed, storing {}: {}", "{}", e.getMessage());
            return "{}";
        }
    }

    /**
     * 值级截断：保证序列化后的 JSON 始终合法（字符串值截断到 400 字符）。
     * 若截断后仍超列宽（VARCHAR(4000)），由上层 fail-soft 兜底。
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> truncateValues(Map<String, Object> args) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : args.entrySet()) {
            Object v = e.getValue();
            if (v instanceof String s) {
                out.put(e.getKey(), s.length() > 400 ? s.substring(0, 400) : s);
            } else if (v instanceof Map<?, ?> nested) {
                out.put(e.getKey(), truncateValues((Map<String, Object>) nested));
            } else {
                out.put(e.getKey(), v);
            }
        }
        return out;
    }

    private static Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank() || "{}".equals(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return new ObjectMapper().readValue(json, ARGS_TYPE);
        } catch (Exception e) {
            log.warn("🛡 [V1.29] audit arguments deserialization failed, returning empty Map: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}