package com.mcp.enterprise.registry;

import java.util.List;
import java.util.Map;

/**
 * V1.22 Skill Registry persistence SPI.
 *
 * <p>V1.21 kept the whole Skill Registry in process memory (zero external
 * dependency, "注册即生效"). That is perfect for a demo, but an enterprise
 * platform must survive a restart / rolling deploy: after the JVM comes back
 * the registry should still know which version of a skill is ACTIVE and how the
 * gray traffic split was configured. This SPI is the seam that makes that
 * possible without forcing a database on every user.</p>
 *
 * <p>Two implementations ship with the module:</p>
 * <ul>
 *   <li>{@link InMemorySkillRegistryStore} - the default, a no-op store. When it
 *       is used {@link #isPersistent()} returns {@code false} and the registry
 *       behaves exactly like V1.21 (pure in-process state).</li>
 *   <li>{@link JdbcSkillRegistryStore} - a portable JDBC implementation backed
 *       by a single table ({@code mcp_skill_registry} by default).</li>
 * </ul>
 *
 * <p>All mutation methods are invoked <em>after</em> the in-memory registry has
 * already accepted the change, and failures are swallowed + logged by
 * {@link SkillRegistry}. This "availability over durability" trade-off keeps
 * governance working even when the backing store is briefly down.</p>
 */
public interface SkillRegistryStore {

    /**
     * Whether this store actually persists anything. In-memory stores return
     * {@code false}, which lets {@link SkillRegistry} skip useless work and skip
     * the startup {@code reload()}.
     */
    default boolean isPersistent() {
        return false;
    }

    /** Load every version of every skill (order is not significant). */
    List<SkillSpec> loadVersions();

    /** Load the active version per skill: {@code skillName -> version}. */
    Map<String, String> loadActiveVersions();

    /** Load gray routing weights: {@code skillName -> (version -> weight)}. */
    Map<String, Map<String, Integer>> loadGrayWeights();

    /** Insert or replace one version row. */
    void upsert(SkillSpec spec);

    /** Mark {@code version} as the active version of {@code name} (others inactive). */
    void setActive(String name, String version);

    /** Set the gray weight of one version of a skill. */
    void setGrayWeight(String name, String version, int weight);

    /** Clear all gray weights of a skill. */
    void clearGray(String name);

    /** Delete every version of a skill. */
    void removeSkill(String name);
}
