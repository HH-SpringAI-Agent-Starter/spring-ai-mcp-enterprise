package com.mcp.enterprise.registry;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * V1.22 portable JDBC {@link SkillRegistryStore}.
 *
 * <p>Persists Skill Registry state in a single flat table so the governance
 * layer survives restarts and rolling deploys. The implementation is
 * intentionally dialect-agnostic: it only uses {@code CREATE TABLE IF NOT
 * EXISTS}, {@code INSERT}, {@code UPDATE} and {@code DELETE} with bound
 * parameters, so it runs unchanged on H2 (tests), MySQL/MariaDB, PostgreSQL and
 * SQL Server (see {@code docs/skill-registry-persistence-guide.md} for the
 * per-database notes).</p>
 *
 * <h3>Table layout ({@code mcp_skill_registry})</h3>
 * <pre>
 *   skill_name   VARCHAR(128)  NOT NULL
 *   version      VARCHAR(64)   NOT NULL
 *   description  VARCHAR(1024)
 *   category     VARCHAR(128)
 *   owner        VARCHAR(128)
 *   status       VARCHAR(32)
 *   input_schema VARCHAR(4000)            -- JSON of the SkillSpec schema
 *   active       INT NOT NULL DEFAULT 0   -- 1 = currently served version
 *   gray_weight  INT                      -- NULL = not gray-routed
 *   created_at   BIGINT
 *   updated_at   BIGINT
 *   PRIMARY KEY (skill_name, version)
 * </pre>
 *
 * <p>Writes are "delete-then-insert" (upsert) rather than {@code MERGE}, because
 * {@code MERGE} is the one statement whose syntax differs the most across
 * databases. The table is small (tens to thousands of rows) so the extra
 * statement is irrelevant.</p>
 */
public class JdbcSkillRegistryStore implements SkillRegistryStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcSkillRegistryStore.class);

    private static final TypeReference<Map<String, Object>> SCHEMA_TYPE =
            new TypeReference<>() {
            };

    private final JdbcTemplate jdbc;
    private final String table;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JdbcSkillRegistryStore(JdbcTemplate jdbc) {
        this(jdbc, "mcp_skill_registry");
    }

    public JdbcSkillRegistryStore(JdbcTemplate jdbc, String table) {
        if (jdbc == null) {
            throw new IllegalArgumentException("JdbcTemplate is required");
        }
        this.jdbc = jdbc;
        this.table = (table == null || table.isBlank()) ? "mcp_skill_registry" : table;
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    /**
     * Create the backing table when it does not exist yet. Safe to call on every
     * startup (idempotent). Only uses {@code VARCHAR}, which every supported
     * database accepts; widen {@code input_schema} to {@code TEXT}/{@code CLOB}
     * in production if skill schemas may exceed 4000 characters.
     */
    public void initSchema() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS " + table + " ("
                + "skill_name VARCHAR(128) NOT NULL, "
                + "version VARCHAR(64) NOT NULL, "
                + "description VARCHAR(1024), "
                + "category VARCHAR(128), "
                + "owner VARCHAR(128), "
                + "status VARCHAR(32), "
                + "input_schema VARCHAR(4000), "
                + "active INT NOT NULL DEFAULT 0, "
                + "gray_weight INT, "
                + "created_at BIGINT, "
                + "updated_at BIGINT, "
                + "PRIMARY KEY (skill_name, version))");
        log.info("🗄️ [V1.22] Skill Registry table ready: {}", table);
    }

    @Override
    public List<SkillSpec> loadVersions() {
        List<SkillSpec> specs = new ArrayList<>();
        jdbc.query("SELECT skill_name, version, description, category, owner, status, "
                        + "input_schema, created_at, updated_at FROM " + table,
                rs -> {
                    SkillSpec spec = new SkillSpec();
                    spec.setName(rs.getString("skill_name"));
                    spec.setVersion(rs.getString("version"));
                    spec.setDescription(rs.getString("description"));
                    spec.setCategory(rs.getString("category"));
                    spec.setOwner(rs.getString("owner"));
                    spec.setStatus(parseStatus(rs.getString("status")));
                    spec.setInputSchema(parseSchema(rs.getString("input_schema")));
                    spec.setCreatedAt(rs.getLong("created_at"));
                    spec.setUpdatedAt(rs.getLong("updated_at"));
                    specs.add(spec);
                });
        return specs;
    }

    @Override
    public Map<String, String> loadActiveVersions() {
        Map<String, String> active = new LinkedHashMap<>();
        jdbc.query("SELECT skill_name, version FROM " + table + " WHERE active = 1",
                rs -> {
                    active.put(rs.getString("skill_name"), rs.getString("version"));
                });
        return active;
    }

    @Override
    public Map<String, Map<String, Integer>> loadGrayWeights() {
        Map<String, Map<String, Integer>> weights = new LinkedHashMap<>();
        jdbc.query("SELECT skill_name, version, gray_weight FROM " + table
                        + " WHERE gray_weight IS NOT NULL",
                rs -> {
                    weights.computeIfAbsent(rs.getString("skill_name"), k -> new LinkedHashMap<>())
                            .put(rs.getString("version"), rs.getInt("gray_weight"));
                });
        return weights;
    }

    @Override
    public void upsert(SkillSpec spec) {
        // Preserve the active flag of an existing row; a brand-new row starts inactive
        // (the registry explicitly calls setActive afterwards on deploy).
        Integer existingActive = queryInt("SELECT active FROM " + table
                + " WHERE skill_name = ? AND version = ?", spec.getName(), spec.getVersion());
        jdbc.update("DELETE FROM " + table + " WHERE skill_name = ? AND version = ?",
                spec.getName(), spec.getVersion());
        jdbc.update("INSERT INTO " + table + " (skill_name, version, description, category, owner, "
                        + "status, input_schema, active, gray_weight, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?)",
                spec.getName(),
                spec.getVersion(),
                spec.getDescription(),
                spec.getCategory(),
                spec.getOwner(),
                spec.getStatus() == null ? SkillStatus.ACTIVE.name() : spec.getStatus().name(),
                writeSchema(spec.getInputSchema()),
                existingActive == null ? 0 : existingActive,
                spec.getCreatedAt(),
                spec.getUpdatedAt());
    }

    @Override
    public void setActive(String name, String version) {
        jdbc.update("UPDATE " + table + " SET active = 0 WHERE skill_name = ?", name);
        jdbc.update("UPDATE " + table + " SET active = 1 WHERE skill_name = ? AND version = ?",
                name, version);
    }

    @Override
    public void setGrayWeight(String name, String version, int weight) {
        jdbc.update("UPDATE " + table + " SET gray_weight = ? WHERE skill_name = ? AND version = ?",
                weight, name, version);
    }

    @Override
    public void clearGray(String name) {
        jdbc.update("UPDATE " + table + " SET gray_weight = NULL WHERE skill_name = ?", name);
    }

    @Override
    public void removeSkill(String name) {
        jdbc.update("DELETE FROM " + table + " WHERE skill_name = ?", name);
    }

    // ===== helpers =====

    private Integer queryInt(String sql, Object... args) {
        List<Integer> rows = jdbc.query(sql, (rs, rowNum) -> rs.getInt(1), args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private SkillStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return SkillStatus.ACTIVE;
        }
        try {
            return SkillStatus.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return SkillStatus.ACTIVE;
        }
    }

    private Map<String, Object> parseSchema(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, SCHEMA_TYPE);
            return parsed == null ? new LinkedHashMap<>() : parsed;
        } catch (Exception e) {
            log.warn("⚠️ [V1.22] could not parse stored input_schema, returning empty: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private String writeSchema(Map<String, Object> schema) {
        if (schema == null || schema.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(schema);
        } catch (Exception e) {
            log.warn("⚠️ [V1.22] could not serialise input_schema: {}", e.getMessage());
            return null;
        }
    }
}
