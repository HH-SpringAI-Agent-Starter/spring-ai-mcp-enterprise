package com.mcp.enterprise.registry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates V1.21 Skill Registry: registration, semantic versioning, explicit
 * activation, fault rollback, gray routing and removal.
 */
class SkillRegistryTest {

    private SkillRegistry registry;

    @BeforeEach
    void setUp() {
        SkillRegistryProperties props = new SkillRegistryProperties();
        props.setMaxVersionsPerSkill(20);
        registry = new SkillRegistry(props);
    }

    private SkillSpec spec(String name, String version, String description) {
        SkillSpec s = new SkillSpec();
        s.setName(name);
        s.setVersion(version);
        s.setDescription(description);
        s.setCategory("finance");
        s.setOwner("platform-team");
        s.setInputSchema(Map.of("type", "object", "properties", Map.of("symbol", Map.of("type", "string"))));
        return s;
    }

    @Test
    void firstRegistrationBecomesActive() {
        SkillSpec registered = registry.register(spec("finance_indicator", "1.0.0", "indicator v1"));
        assertEquals(SkillStatus.ACTIVE, registered.getStatus());
        assertEquals(1, registry.count());
        assertEquals("1.0.0", registry.get("finance_indicator").getVersion());
    }

    @Test
    void registeringSameVersionTwiceIsRejected() {
        registry.register(spec("search_tool", "1.1.0", "v1.1"));
        assertThrows(IllegalStateException.class,
                () -> registry.register(spec("search_tool", "1.1.0", "duplicate")));
    }

    @Test
    void laterVersionBecomesActiveAndHistoryIsKept() {
        registry.register(spec("search_tool", "1.0.0", "v1"));
        registry.register(spec("search_tool", "1.1.0", "v1.1"));
        registry.register(spec("search_tool", "2.0.0", "v2"));
        assertEquals("2.0.0", registry.get("search_tool").getVersion());
        assertEquals(3, registry.versions("search_tool").size());
        assertEquals("2.0.0", registry.versions("search_tool").get(0).getVersion());
    }

    @Test
    void explicitActivateSwitchesActiveVersion() {
        registry.register(spec("weather_tool", "1.0.0", "v1"));
        registry.register(spec("weather_tool", "1.1.0", "v1.1"));
        SkillSpec activated = registry.activate("weather_tool", "1.0.0");
        assertEquals("1.0.0", activated.getVersion());
        assertEquals("1.0.0", registry.get("weather_tool").getVersion());
    }

    @Test
    void rollbackRevertsToPreviousDeployedVersion() {
        registry.register(spec("http_tool", "1.0.0", "v1"));
        registry.register(spec("http_tool", "1.1.0", "v1.1 - breaks prod"));
        SkillSpec rolledBack = registry.rollback("http_tool");
        assertEquals("1.0.0", rolledBack.getVersion());
        assertEquals("1.0.0", registry.get("http_tool").getVersion());
    }

    @Test
    void rollbackWithoutPreviousVersionFails() {
        registry.register(spec("sys_tool", "1.0.0", "only version"));
        assertThrows(IllegalStateException.class, () -> registry.rollback("sys_tool"));
    }

    @Test
    void grayWeightHundredRoutesAlwaysToGrayVersion() {
        registry.register(spec("calc_tool", "1.0.0", "stable"));
        registry.register(spec("calc_tool", "1.1.0", "canary"));
        registry.gray("calc_tool", "1.1.0", 100);
        for (int i = 0; i < 50; i++) {
            assertEquals("1.1.0", registry.route("calc_tool").getVersion());
        }
    }

    @Test
    void grayWeightZeroFallsBackToActiveVersion() {
        registry.register(spec("calc_tool", "1.0.0", "stable"));
        registry.register(spec("calc_tool", "1.1.0", "canary"));
        // 显式把 active 切回 1.0.0，再对 canary 配 0 权重 -> 路由永远回退到 active
        registry.activate("calc_tool", "1.0.0");
        registry.gray("calc_tool", "1.1.0", 0);
        for (int i = 0; i < 50; i++) {
            assertEquals("1.0.0", registry.route("calc_tool").getVersion());
        }
    }

    @Test
    void grayRoutingOnlyServesRegisteredVersions() {
        registry.register(spec("calc_tool", "1.0.0", "stable"));
        registry.register(spec("calc_tool", "1.1.0", "canary"));
        registry.gray("calc_tool", "1.1.0", 30);
        for (int i = 0; i < 100; i++) {
            String version = registry.route("calc_tool").getVersion();
            assertTrue("1.0.0".equals(version) || "1.1.0".equals(version));
        }
    }

    @Test
    void activateClearsGrayConfig() {
        registry.register(spec("calc_tool", "1.0.0", "stable"));
        registry.register(spec("calc_tool", "1.1.0", "canary"));
        registry.gray("calc_tool", "1.1.0", 100);
        registry.activate("calc_tool", "1.0.0");
        for (int i = 0; i < 10; i++) {
            assertEquals("1.0.0", registry.route("calc_tool").getVersion());
        }
    }

    @Test
    void historyIsTrimmedToConfiguredMaximum() {
        SkillRegistryProperties props = new SkillRegistryProperties();
        props.setMaxVersionsPerSkill(3);
        SkillRegistry small = new SkillRegistry(props);
        for (int i = 1; i <= 5; i++) {
            small.register(spec("trim_tool", "1.0." + i, "v" + i));
        }
        assertEquals(3, small.versions("trim_tool").size());
        assertEquals("1.0.5", small.get("trim_tool").getVersion());
        assertNotNull(small.versions("trim_tool"));
    }

    @Test
    void removeCleansAllState() {
        registry.register(spec("del_tool", "1.0.0", "v1"));
        registry.remove("del_tool");
        assertEquals(0, registry.count());
        assertThrows(IllegalArgumentException.class, () -> registry.get("del_tool"));
        assertThrows(IllegalArgumentException.class, () -> registry.versions("del_tool"));
    }
}