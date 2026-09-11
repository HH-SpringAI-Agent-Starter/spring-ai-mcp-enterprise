package com.mcp.enterprise.registry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V1.22: verifies that Skill Registry state (versions, active version, gray
 * weights, input schema) survives a "restart" - i.e. a brand new
 * {@link SkillRegistry} instance reloading from the same JDBC store.
 *
 * <p>Uses an isolated in-memory H2 database per test method, so it exercises the
 * exact SQL that runs in production MySQL/PostgreSQL without any external
 * dependency.</p>
 */
class SkillRegistryPersistenceTest {

    private JdbcTemplate jdbc;
    private JdbcSkillRegistryStore store;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:skillreg_" + UUID.randomUUID().toString().replace("-", "")
                + ";DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        store = new JdbcSkillRegistryStore(jdbc, "mcp_skill_registry");
        store.initSchema();
    }

    /** Simulates an application restart: fresh registry, same persistent store. */
    private SkillRegistry restarted() {
        SkillRegistryProperties props = new SkillRegistryProperties();
        props.setMaxVersionsPerSkill(20);
        SkillRegistry registry = new SkillRegistry(props, store);
        registry.reload();
        return registry;
    }

    private SkillSpec spec(String name, String version, String description) {
        SkillSpec s = new SkillSpec();
        s.setName(name);
        s.setVersion(version);
        s.setDescription(description);
        s.setCategory("finance");
        s.setOwner("platform-team");
        s.setInputSchema(Map.of("type", "object",
                "properties", Map.of("symbol", Map.of("type", "string"))));
        return s;
    }

    @Test
    void jdbcStoreIsPersistentInMemoryStoreIsNot() {
        assertTrue(store.isPersistent());
        assertFalse(new InMemorySkillRegistryStore().isPersistent());
    }

    @Test
    void schemaInitialisationIsIdempotent() {
        store.initSchema();
        store.initSchema();
        assertTrue(store.isPersistent());
    }

    @Test
    void versionsAndActiveVersionSurviveRestart() {
        SkillRegistry first = restarted();
        first.register(spec("finance_indicator", "1.0.0", "v1"));
        first.register(spec("finance_indicator", "1.1.0", "v1.1"));

        SkillRegistry second = restarted();
        assertEquals(1, second.count());
        assertEquals("1.1.0", second.get("finance_indicator").getVersion());
        assertEquals(2, second.versions("finance_indicator").size());
    }

    @Test
    void explicitActivationSurvivesRestart() {
        SkillRegistry first = restarted();
        first.register(spec("weather_tool", "1.0.0", "v1"));
        first.register(spec("weather_tool", "1.1.0", "v1.1"));
        first.activate("weather_tool", "1.0.0");

        SkillRegistry second = restarted();
        assertEquals("1.0.0", second.get("weather_tool").getVersion());
    }

    @Test
    void rollbackSurvivesRestart() {
        SkillRegistry first = restarted();
        first.register(spec("http_tool", "1.0.0", "v1"));
        first.register(spec("http_tool", "1.1.0", "breaks prod"));
        first.rollback("http_tool");

        SkillRegistry second = restarted();
        assertEquals("1.0.0", second.get("http_tool").getVersion());
    }

    @Test
    void grayWeightsSurviveRestart() {
        SkillRegistry first = restarted();
        first.register(spec("calc_tool", "1.0.0", "stable"));
        first.register(spec("calc_tool", "1.1.0", "canary"));
        first.gray("calc_tool", "1.1.0", 100);

        SkillRegistry second = restarted();
        for (int i = 0; i < 50; i++) {
            assertEquals("1.1.0", second.route("calc_tool").getVersion());
        }
    }

    @Test
    void activationClearsPersistedGrayWeight() {
        SkillRegistry first = restarted();
        first.register(spec("g_tool", "1.0.0", "stable"));
        first.register(spec("g_tool", "1.1.0", "canary"));
        first.gray("g_tool", "1.1.0", 100);
        first.activate("g_tool", "1.0.0");

        SkillRegistry second = restarted();
        for (int i = 0; i < 20; i++) {
            assertEquals("1.0.0", second.route("g_tool").getVersion());
        }
    }

    @Test
    void inputSchemaRoundTripsThroughDatabase() {
        restarted().register(spec("schema_tool", "2.0.0", "schema check"));

        Map<String, Object> schema = restarted().get("schema_tool").getInputSchema();
        assertEquals("object", schema.get("type"));
        assertTrue(schema.containsKey("properties"));
    }

    @Test
    void removeClearsPersistedState() {
        SkillRegistry first = restarted();
        first.register(spec("del_tool", "1.0.0", "v1"));
        first.remove("del_tool");

        SkillRegistry second = restarted();
        assertEquals(0, second.count());
        assertThrows(IllegalArgumentException.class, () -> second.get("del_tool"));
    }
}
