package com.mcp.enterprise.registry;

/**
 * Lifecycle status of a registered Skill (V1.21 Skill Registry).
 *
 * <ul>
 *   <li>{@link #DRAFT}     - registered but not activated yet (reserved for future use)</li>
 *   <li>{@link #ACTIVE}    - the version currently served to clients via routing</li>
 *   <li>{@link #DEPRECATED}- still routable, scheduled for removal</li>
 *   <li>{@link #RETIRED}   - kept in version history only, never routed</li>
 * </ul>
 */
public enum SkillStatus {

    /** Registered but not activated yet. */
    DRAFT,

    /** The version currently served to clients via routing. */
    ACTIVE,

    /** Still routable, scheduled for removal. */
    DEPRECATED,

    /** Kept in version history only, never routed. */
    RETIRED
}