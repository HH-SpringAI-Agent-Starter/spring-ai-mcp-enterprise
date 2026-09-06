package com.mcp.enterprise.registry;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A versioned Skill (tool specification) registered in the Skill Registry.
 *
 * <p>One {@code SkillSpec} instance represents exactly one immutable version of
 * a skill. It carries the JSON-Schema-style {@code inputSchema} describing the
 * tool contract, plus governance metadata (owner, category, lifecycle status)
 * that mirrors the "Skill/Spec Registry" requirement found in enterprise AI
 * gateway job descriptions (e.g. Walmart China AI/MCP gateway, MCP platform
 * engineer).</p>
 */
public class SkillSpec {

    /** Unique skill name (e.g. {@code finance_indicator}). */
    private String name;

    /** Human readable description of what the skill does. */
    private String description;

    /** Semantic version of this spec (e.g. {@code 1.2.0}). */
    private String version;

    /** Business category used for taxonomy / access-policy grouping. */
    private String category;

    /** Owning team or individual (used for audit and approval workflows). */
    private String owner;

    /** Lifecycle status of this version. */
    private SkillStatus status = SkillStatus.DRAFT;

    /** JSON-Schema-style input contract of the skill. */
    private Map<String, Object> inputSchema = new LinkedHashMap<>();

    /** Epoch millis when this version was registered. */
    private long createdAt;

    /** Epoch millis of the last state change. */
    private long updatedAt;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public SkillStatus getStatus() {
        return status;
    }

    public void setStatus(SkillStatus status) {
        this.status = status;
    }

    public Map<String, Object> getInputSchema() {
        return inputSchema;
    }

    public void setInputSchema(Map<String, Object> inputSchema) {
        this.inputSchema = inputSchema;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}