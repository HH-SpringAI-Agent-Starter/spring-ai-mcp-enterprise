package com.mcp.enterprise.registry;

import java.util.List;
import java.util.Map;

/**
 * V1.22 default no-op {@link SkillRegistryStore}.
 *
 * <p>Used when {@code mcp.enterprise.registry.skill.store=memory} (the default)
 * or when no {@code DataSource} is available. It persists nothing: the registry
 * keeps all state in process memory, exactly like V1.21. This is what makes the
 * JDBC persistence an opt-in feature rather than a hard requirement.</p>
 */
public class InMemorySkillRegistryStore implements SkillRegistryStore {

    @Override
    public boolean isPersistent() {
        return false;
    }

    @Override
    public List<SkillSpec> loadVersions() {
        return List.of();
    }

    @Override
    public Map<String, String> loadActiveVersions() {
        return Map.of();
    }

    @Override
    public Map<String, Map<String, Integer>> loadGrayWeights() {
        return Map.of();
    }

    @Override
    public void upsert(SkillSpec spec) {
        // no-op: pure in-memory mode
    }

    @Override
    public void setActive(String name, String version) {
        // no-op: pure in-memory mode
    }

    @Override
    public void setGrayWeight(String name, String version, int weight) {
        // no-op: pure in-memory mode
    }

    @Override
    public void clearGray(String name) {
        // no-op: pure in-memory mode
    }

    @Override
    public void removeSkill(String name) {
        // no-op: pure in-memory mode
    }
}
