package com.mcp.enterprise.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RestController;

/**
 * Auto-configuration for the V1.21 Skill Registry module.
 *
 * <p>Enabled by default; can be turned off with
 * {@code mcp.enterprise.registry.skill.enabled=false}. Controllers are registered
 * as {@code @Bean}s (same pattern as mcp-a2a), so they are available in any
 * Spring Boot application that brings this module onto the classpath:</p>
 *
 * <pre>
 *   GET    /api/admin/skills             - list active skills (admin)
 *   POST   /api/admin/skills             - register a new version (admin)
 *   GET    /api/admin/skills/{name}      - active spec (admin)
 *   GET    /api/admin/skills/{name}/versions
 *   POST   /api/admin/skills/{name}/activate
 *   POST   /api/admin/skills/{name}/rollback
 *   POST   /api/admin/skills/{name}/gray
 *   DELETE /api/admin/skills/{name}
 *   GET    /api/mcp/skills               - discovery for MCP clients
 *   GET    /api/mcp/skills/{name}        - active spec for MCP clients
 * </pre>
 */
@AutoConfiguration
@ConditionalOnClass(RestController.class)
@ConditionalOnProperty(prefix = "mcp.enterprise.registry.skill", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SkillRegistryProperties.class)
public class SkillRegistryAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SkillRegistryAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public SkillRegistry skillRegistry(SkillRegistryProperties properties) {
        log.info("🛠️ [V1.21] Skill Registry enabled (max-versions-per-skill={})",
                properties.getMaxVersionsPerSkill());
        return new SkillRegistry(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public SkillAdminController skillAdminController(SkillRegistry skillRegistry) {
        return new SkillAdminController(skillRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public SkillDiscoveryController skillDiscoveryController(SkillRegistry skillRegistry) {
        return new SkillDiscoveryController(skillRegistry);
    }
}