package com.mcp.enterprise.registry;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the V1.21 Skill Registry module.
 *
 * <pre>
 * mcp:
 *   enterprise:
 *     registry:
 *       skill:
 *         enabled: true            # master switch (default true)
 *         max-versions-per-skill: 20
 *         store: memory            # V1.22: memory | jdbc
 *         table: mcp_skill_registry# V1.22: backing table when store=jdbc
 *         init-schema: true        # V1.22: auto CREATE TABLE IF NOT EXISTS
 * </pre>
 */
@ConfigurationProperties(prefix = "mcp.enterprise.registry.skill")
public class SkillRegistryProperties {

    /** Master switch; defaults to enabled. */
    private boolean enabled = true;

    /** Maximum number of historical versions kept per skill (oldest trimmed). */
    private int maxVersionsPerSkill = 20;

    /**
     * V1.22 persistence backend: {@code memory} (default, V1.21 behaviour) or
     * {@code jdbc}. When {@code jdbc} is selected but no {@code DataSource} is
     * present the registry transparently falls back to in-memory mode.
     */
    private String store = "memory";

    /** V1.22 backing table name used when {@code store=jdbc}. */
    private String table = "mcp_skill_registry";

    /**
     * V1.22 whether to run {@code CREATE TABLE IF NOT EXISTS} on startup. Set to
     * {@code false} when the schema is managed by a migration tool (Flyway /
     * Liquibase) or provisioned by DBAs.
     */
    private boolean initSchema = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxVersionsPerSkill() {
        return maxVersionsPerSkill;
    }

    public void setMaxVersionsPerSkill(int maxVersionsPerSkill) {
        this.maxVersionsPerSkill = maxVersionsPerSkill;
    }

    public String getStore() {
        return store;
    }

    public void setStore(String store) {
        this.store = store;
    }

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public boolean isInitSchema() {
        return initSchema;
    }

    public void setInitSchema(boolean initSchema) {
        this.initSchema = initSchema;
    }
}