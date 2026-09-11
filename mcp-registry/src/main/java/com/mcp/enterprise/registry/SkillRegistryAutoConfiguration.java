package com.mcp.enterprise.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
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
 *
 * <p>V1.22 adds opt-in JDBC persistence: with
 * {@code mcp.enterprise.registry.skill.store=jdbc} the registry is backed by a
 * {@link JdbcSkillRegistryStore} and reloads its state on startup, so the active
 * version and gray weights survive restarts and rolling deploys.</p>
 */
@AutoConfiguration
@ConditionalOnClass(RestController.class)
@ConditionalOnProperty(prefix = "mcp.enterprise.registry.skill", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SkillRegistryProperties.class)
public class SkillRegistryAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SkillRegistryAutoConfiguration.class);

    /**
     * V1.22 store selection: JDBC when requested and a {@link JdbcTemplate} is
     * available, otherwise the in-memory no-op store.
     */
    @Bean
    @ConditionalOnMissingBean
    public SkillRegistryStore skillRegistryStore(SkillRegistryProperties properties,
                                                 ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        if ("jdbc".equalsIgnoreCase(properties.getStore())) {
            JdbcTemplate jdbc = jdbcTemplateProvider.getIfAvailable();
            if (jdbc == null) {
                log.warn("⚠️ [V1.22] store=jdbc requested but no JdbcTemplate/DataSource found; "
                        + "using in-memory Skill Registry");
                return new InMemorySkillRegistryStore();
            }
            JdbcSkillRegistryStore store = new JdbcSkillRegistryStore(jdbc, properties.getTable());
            if (properties.isInitSchema()) {
                store.initSchema();
            }
            log.info("🗄️ [V1.22] Skill Registry JDBC persistence enabled (table={})",
                    properties.getTable());
            return store;
        }
        return new InMemorySkillRegistryStore();
    }

    @Bean
    @ConditionalOnMissingBean
    public SkillRegistry skillRegistry(SkillRegistryProperties properties, SkillRegistryStore store) {
        log.info("🛠️ [V1.21] Skill Registry enabled (max-versions-per-skill={}, store={})",
                properties.getMaxVersionsPerSkill(), store.isPersistent() ? "jdbc" : "memory");
        SkillRegistry registry = new SkillRegistry(properties, store);
        if (store.isPersistent()) {
            registry.reload();
        }
        return registry;
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