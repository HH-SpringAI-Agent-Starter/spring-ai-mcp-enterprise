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
 * V1.28 portable JDBC {@link ApprovalStore}.
 *
 * <p>V1.26 的审批队列只活在单个 JVM 内存里（{@link InMemoryApprovalStore}）。
 * 单实例演示没问题，但企业多实例拓扑（K8s 多副本 / 滚动发布）下，
 * 审批人批准了一条请求，消费方请求可能落在另一个实例上 —— 状态不可见，
 * HITL 闭环断裂。本实现把审批请求持久化到单张表，让「创建 → 审批 → 消费」
 * 跨实例可见，且重启/发布后审批记录不丢失（合规审计要求）。</p>
 *
 * <p>设计约束与 {@code JdbcSkillRegistryStore}（V1.22）保持一致：</p>
 * <ul>
 *   <li><b>方言无关</b>：只使用 {@code CREATE TABLE IF NOT EXISTS} /
 *       {@code INSERT} / {@code SELECT} / {@code UPDATE} 绑定参数，
 *       避免 {@code MERGE} 等跨库差异最大的语句 —— H2（测试）/ MySQL /
 *       MariaDB / PostgreSQL / SQL Server 均可直接运行；</li>
 *   <li><b>单扁平表</b> {@code mcp_approval_requests}（可配置表名），
 *       审批参数以 JSON 文本存 {@code VARCHAR}；</li>
 *   <li><b>时间戳 epoch millis</b>（BIGINT），规避各库日期函数方言；</li>
 *   <li><b>fail-soft</b>：单条读/写失败记录 WARN 并抛给上层，但绝不静默吞掉
 *       状态流转（审批是 Fail-closed 语义，宁可报错不可错放）。</li>
 * </ul>
 *
 * <h3>表结构</h3>
 * <pre>
 *   id              VARCHAR(64)   NOT NULL PRIMARY KEY
 *   tool_name       VARCHAR(256)  NOT NULL
 *   tier_code       VARCHAR(16)   NOT NULL
 *   arguments       VARCHAR(4000)            -- 脱敏后参数 JSON
 *   requested_by    VARCHAR(256)
 *   reason          VARCHAR(1024)
 *   status          VARCHAR(16)   NOT NULL   -- PENDING/APPROVED/REJECTED/EXPIRED/CONSUMED
 *   created_at      BIGINT        NOT NULL   -- epoch millis
 *   expires_at      BIGINT        NOT NULL
 *   decided_at      BIGINT                   -- 可空
 *   decided_by      VARCHAR(256)
 *   decision_reason VARCHAR(1024)
 * </pre>
 *
 * <p>部署：{@code mcp.enterprise.governance.store=jdbc}（默认 {@code memory}）；
 * 自动配置在存在 {@code JdbcTemplate} 时自动启用并幂等建表；无数据源则回退内存实现
 * 并打 WARN。表名与建表开关可配置：
 * {@code mcp.enterprise.governance.approval.table} /
 * {@code mcp.enterprise.governance.approval.init-schema}。</p>
 */
public class JdbcApprovalStore implements ApprovalStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcApprovalStore.class);

    private static final TypeReference<Map<String, Object>> ARGS_TYPE =
            new TypeReference<>() {
            };

    /** 从结果集还原审批请求（使用包私有还原构造器，保持数据库身份字段）。 */
    private static final RowMapper<ApprovalRequest> MAPPER = (ResultSet rs, int rowNum) -> {
        RiskTier tier = RiskTier.fromCode(rs.getString("tier_code"));
        ApprovalRequest req = new ApprovalRequest(
                rs.getString("id"),
                rs.getString("tool_name"),
                tier,
                fromJson(rs.getString("arguments")),
                rs.getString("requested_by"),
                rs.getString("reason"),
                fromEpoch(rs.getLong("created_at")),
                fromEpoch(rs.getLong("expires_at")));
        req.setStatus(ApprovalRequest.Status.valueOf(rs.getString("status")));
        req.setDecidedAt(fromEpoch(rs.getLong("decided_at")));
        req.setDecidedBy(rs.getString("decided_by"));
        req.setDecisionReason(rs.getString("decision_reason"));
        return req;
    };

    private final JdbcTemplate jdbc;
    private final String table;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JdbcApprovalStore(JdbcTemplate jdbc) {
        this(jdbc, "mcp_approval_requests");
    }

    public JdbcApprovalStore(JdbcTemplate jdbc, String table) {
        if (jdbc == null) {
            throw new IllegalArgumentException("JdbcTemplate is required");
        }
        this.jdbc = jdbc;
        this.table = (table == null || table.isBlank()) ? "mcp_approval_requests" : table;
    }

    /**
     * 幂等建表（启动时调用一次；使用方也可在首次使用前显式调用）。
     * 仅用 VARCHAR，所有主流数据库均可直接执行。
     */
    public void initSchema() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS " + table + " (" +
                "id VARCHAR(64) NOT NULL PRIMARY KEY, " +
                "tool_name VARCHAR(256) NOT NULL, " +
                "tier_code VARCHAR(16) NOT NULL, " +
                "arguments VARCHAR(4000), " +
                "requested_by VARCHAR(256), " +
                "reason VARCHAR(1024), " +
                "status VARCHAR(16) NOT NULL, " +
                "created_at BIGINT NOT NULL, " +
                "expires_at BIGINT NOT NULL, " +
                "decided_at BIGINT, " +
                "decided_by VARCHAR(256), " +
                "decision_reason VARCHAR(1024))");
        log.info("?? [V1.28] Approval JDBC store ready (table={})", table);
    }

    @Override
    public void save(ApprovalRequest request) {
        jdbc.update("INSERT INTO " + table + " (id, tool_name, tier_code, arguments, requested_by, " +
                        "reason, status, created_at, expires_at, decided_at, decided_by, decision_reason) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                request.getId(),
                request.getToolName(),
                request.getTierCode(),
                toJson(request.getArguments()),
                request.getRequestedBy(),
                request.getReason(),
                request.getStatus().name(),
                toEpoch(request.getCreatedAt()),
                toEpoch(request.getExpiresAt()),
                request.getDecidedAt() == null ? null : toEpoch(request.getDecidedAt()),
                request.getDecidedBy(),
                request.getDecisionReason());
    }

    @Override
    public ApprovalRequest get(String id) {
        if (id == null) {
            return null;
        }
        List<ApprovalRequest> rows = jdbc.query(
                "SELECT * FROM " + table + " WHERE id = ?", MAPPER, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public List<ApprovalRequest> list(ApprovalRequest.Status status) {
        List<ApprovalRequest> result = new ArrayList<>();
        List<ApprovalRequest> rows;
        if (status == null) {
            rows = jdbc.query("SELECT * FROM " + table + " ORDER BY created_at DESC", MAPPER);
        } else {
            rows = jdbc.query("SELECT * FROM " + table + " WHERE status = ? ORDER BY created_at DESC",
                    MAPPER, status.name());
        }
        result.addAll(rows);
        return result;
    }

    @Override
    public int count() {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return n == null ? 0 : n;
    }

    @Override
    public void update(ApprovalRequest request) {
        jdbc.update("UPDATE " + table + " SET status = ?, decided_at = ?, decided_by = ?, " +
                        "decision_reason = ? WHERE id = ?",
                request.getStatus().name(),
                request.getDecidedAt() == null ? null : toEpoch(request.getDecidedAt()),
                request.getDecidedBy(),
                request.getDecisionReason(),
                request.getId());
    }

    // ===== 内部工具 =====

    private String toJson(Map<String, Object> args) {
        if (args == null || args.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(args);
        } catch (Exception e) {
            log.warn("?? [V1.28] 审批参数序列化失败，落库为空对象: {}", e.getMessage());
            return "{}";
        }
    }

    private static Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank() || "{}".equals(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return new ObjectMapper().readValue(json, ARGS_TYPE);
        } catch (Exception e) {
            log.warn("?? [V1.28] 审批参数反序列化失败，返回空 Map: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private static long toEpoch(Instant instant) {
        return instant == null ? 0L : instant.toEpochMilli();
    }

    private static Instant fromEpoch(long epochMillis) {
        return epochMillis <= 0L ? null : Instant.ofEpochMilli(epochMillis);
    }
}