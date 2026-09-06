package com.mcp.enterprise.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * V1.21 Skill Registry - Skill/Spec 注册表 + 语义化版本治理 + 灰度路由 + 故障回滚.
 *
 * <p>Addresses the "Skill / Spec Registry + 版本管理 + 灰度发布 + 故障回滚" requirement
 * found in current enterprise AI gateway job descriptions (Walmart China
 * "Skill、Spec Registry 服务", Shanghai MCP platform engineer "Skill 注册、版本管理、
 * 灰度发布、故障回滚"). It is intentionally autonomous: no database required, all
 * state is kept in-process with thread-safe snapshots, so it can be embedded in
 * any Spring Boot application via {@code mcp.enterprise.registry.skill.enabled}.</p>
 *
 * <pre>
 *   register(spec)            - register a new version (activates it)
 *   list() / get(name)        - read the currently active specs
 *   versions(name)            - full version history (newest first)
 *   activate(name, version)   - switch active version explicitly
 *   rollback(name)            - revert to the previous version (fault recovery)
 *   gray(name, version, wt)   - weight-based gray routing between versions
 *   route(name)               - pick the version to serve for one call
 * </pre>
 */
public class SkillRegistry {

    private static final Logger log = LoggerFactory.getLogger(SkillRegistry.class);

    private final int maxVersionsPerSkill;

    /** skill name -&gt; currently active spec. */
    private final Map<String, SkillSpec> activeVersions = new ConcurrentHashMap<>();

    /** skill name -&gt; version history snapshot (newest first). */
    private final Map<String, List<SkillSpec>> versionHistory = new ConcurrentHashMap<>();

    /** skill name -&gt; (version -&gt; relative weight) for gray routing. */
    private final Map<String, Map<String, Integer>> grayWeights = new ConcurrentHashMap<>();

    public SkillRegistry(SkillRegistryProperties properties) {
        this.maxVersionsPerSkill = Math.max(1, properties.getMaxVersionsPerSkill());
    }

    /**
     * Register a new version of a skill. The first version of a skill becomes
     * ACTIVE; every later version is activated on registration (deploy = activate).
     *
     * @param spec the version to register (name and version are mandatory)
     * @return the registered (now ACTIVE) spec
     * @throws IllegalArgumentException if name/version are blank
     * @throws IllegalStateException    if the exact version is already registered
     */
    public synchronized SkillSpec register(SkillSpec spec) {
        if (spec == null || spec.getName() == null || spec.getName().isBlank()) {
            throw new IllegalArgumentException("skill name is required");
        }
        if (spec.getVersion() == null || spec.getVersion().isBlank()) {
            throw new IllegalArgumentException("skill version is required");
        }
        String name = spec.getName();
        List<SkillSpec> versions = versionHistory.computeIfAbsent(name, k -> new ArrayList<>());
        boolean duplicate = versions.stream().anyMatch(v -> v.getVersion().equals(spec.getVersion()));
        if (duplicate) {
            throw new IllegalStateException("version already registered: " + name + "@" + spec.getVersion());
        }
        long now = System.currentTimeMillis();
        spec.setStatus(SkillStatus.ACTIVE);
        spec.setCreatedAt(now);
        spec.setUpdatedAt(now);
        if (spec.getInputSchema() == null) {
            spec.setInputSchema(Map.of());
        }
        versions.add(0, spec);
        trimHistory(name, versions);
        activeVersions.put(name, spec);
        grayWeights.remove(name);
        log.info("🛠️ [V1.21] Skill registered & activated: {}@{}{}", name, spec.getVersion(),
                spec.getCategory() == null ? "" : " (category=" + spec.getCategory() + ")");
        return spec;
    }

    /**
     * List all currently active skills (one spec per skill), sorted by name.
     */
    public List<SkillSpec> list() {
        return activeVersions.values().stream()
                .sorted((a, b) -> a.getName().compareTo(b.getName()))
                .toList();
    }

    /**
     * Return the currently active spec of a skill.
     *
     * @throws IllegalArgumentException if the skill is unknown
     */
    public SkillSpec get(String name) {
        SkillSpec spec = activeVersions.get(name);
        if (spec == null) {
            throw new IllegalArgumentException("skill not found: " + name);
        }
        return spec;
    }

    /**
     * Full version history of a skill (newest first).
     *
     * @throws IllegalArgumentException if the skill is unknown
     */
    public List<SkillSpec> versions(String name) {
        List<SkillSpec> history = versionHistory.get(name);
        if (history == null || history.isEmpty()) {
            throw new IllegalArgumentException("skill not found: " + name);
        }
        return List.copyOf(history);
    }

    /**
     * Explicitly activate a specific historical version.
     *
     * @throws IllegalArgumentException if the skill or version is unknown
     */
    public synchronized SkillSpec activate(String name, String version) {
        SkillSpec target = findVersion(name, version);
        target.setStatus(SkillStatus.ACTIVE);
        target.setUpdatedAt(System.currentTimeMillis());
        activeVersions.put(name, target);
        grayWeights.remove(name);
        log.info("🔄 [V1.21] Skill activated: {}@{}", name, version);
        return target;
    }

    /**
     * Fault-recovery rollback: switch the active version back to the previous one
     * in history (the version deployed right before the current active one).
     *
     * @return the spec now active after rollback
     * @throws IllegalArgumentException if the skill is unknown
     * @throws IllegalStateException    if there is no previous version to roll back to
     */
    public synchronized SkillSpec rollback(String name) {
        List<SkillSpec> history = versionHistory.get(name);
        if (history == null || history.isEmpty()) {
            throw new IllegalArgumentException("skill not found: " + name);
        }
        SkillSpec current = activeVersions.get(name);
        int currentIdx = -1;
        for (int i = 0; i < history.size(); i++) {
            if (history.get(i).getVersion().equals(current.getVersion())) {
                currentIdx = i;
                break;
            }
        }
        if (currentIdx < 0 || currentIdx >= history.size() - 1) {
            throw new IllegalStateException("no previous version to roll back to: " + name);
        }
        SkillSpec previous = history.get(currentIdx + 1);
        previous.setStatus(SkillStatus.ACTIVE);
        previous.setUpdatedAt(System.currentTimeMillis());
        activeVersions.put(name, previous);
        grayWeights.remove(name);
        log.info("↩️ [V1.21] Skill rolled back: {}: {}@{} -&gt; {}@{}", name,
                current.getVersion(), current.getDescription(),
                previous.getVersion(), previous.getDescription());
        return previous;
    }

    /**
     * Configure gray routing: {@code weight} (0-100) of traffic directed to the
     * given version. Remaining weight implicitly falls back to the active version.
     * Multiple versions may be weighted; weights are relative to each other.
     *
     * @throws IllegalArgumentException if the skill/version is unknown or weight invalid
     */
    public synchronized void gray(String name, String version, int weight) {
        if (weight < 0 || weight > 100) {
            throw new IllegalArgumentException("weight must be between 0 and 100");
        }
        findVersion(name, version);
        grayWeights.computeIfAbsent(name, k -> new LinkedHashMap<>()).put(version, weight);
        log.info("🎚️ [V1.21] Skill gray routing: {}@{} weight={}%", name, version, weight);
    }

    /**
     * Pick the version to serve for a single invocation: gray weights if
     * configured, otherwise the active version.
     */
    public SkillSpec route(String name) {
        SkillSpec active = get(name);
        Map<String, Integer> weights = grayWeights.get(name);
        if (weights == null || weights.isEmpty()) {
            return active;
        }
        int total = 0;
        for (Integer w : weights.values()) {
            total += w;
        }
        if (total <= 0) {
            return active;
        }
        int roll = ThreadLocalRandom.current().nextInt(total);
        int cumulative = 0;
        List<Map.Entry<String, Integer>> sorted = weights.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();
        for (Map.Entry<String, Integer> entry : sorted) {
            cumulative += entry.getValue();
            if (roll < cumulative) {
                SkillSpec candidate = findVersionQuietly(name, entry.getKey());
                if (candidate != null) {
                    return candidate;
                }
                break;
            }
        }
        return active;
    }

    /**
     * Remove a skill (all versions, gray config and active mapping).
     */
    public synchronized void remove(String name) {
        versionHistory.remove(name);
        activeVersions.remove(name);
        grayWeights.remove(name);
        log.info("🗑️ [V1.21] Skill removed: {}", name);
    }

    /**
     * Number of distinct skills currently registered.
     */
    public int count() {
        return activeVersions.size();
    }

    // ===== helpers =====

    private void trimHistory(String name, List<SkillSpec> versions) {
        while (versions.size() > maxVersionsPerSkill) {
            SkillSpec trimmed = versions.remove(versions.size() - 1);
            log.info("✂️ [V1.21] Skill history trimmed (max={}): {}@{}", maxVersionsPerSkill, name,
                    trimmed.getVersion());
        }
    }

    private SkillSpec findVersion(String name, String version) {
        SkillSpec found = findVersionQuietly(name, version);
        if (found == null) {
            throw new IllegalArgumentException("version not found: " + name + "@" + version);
        }
        return found;
    }

    private SkillSpec findVersionQuietly(String name, String version) {
        List<SkillSpec> history = versionHistory.get(name);
        if (history == null) {
            return null;
        }
        for (SkillSpec v : history) {
            if (v.getVersion().equals(version)) {
                return v;
            }
        }
        return null;
    }
}