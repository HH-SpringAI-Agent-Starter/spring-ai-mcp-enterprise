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
 * </pre>
 */
@ConfigurationProperties(prefix = "mcp.enterprise.registry.skill")
public class SkillRegistryProperties {

    /** Master switch; defaults to enabled. */
    private boolean enabled = true;

    /** Maximum number of historical versions kept per skill (oldest trimmed). */
    private int maxVersionsPerSkill = 20;

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
}