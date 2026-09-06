package com.mcp.enterprise.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * V1.21 Skill Registry admin API - Skill/Spec 注册、版本治理、灰度、回滚.
 *
 * <pre>
 *   GET    /api/admin/skills              - list active skills, sorted by name
 *   POST   /api/admin/skills              - register a new version (activates it)
 *   GET    /api/admin/skills/{name}       - active spec of one skill
 *   GET    /api/admin/skills/{name}/versions - full version history (newest first)
 *   POST   /api/admin/skills/{name}/activate - activate {version}
 *   POST   /api/admin/skills/{name}/rollback - revert to previous version
 *   POST   /api/admin/skills/{name}/gray     - gray routing {version, weight}
 *   DELETE /api/admin/skills/{name}       - remove skill entirely
 * </pre>
 *
 * <p>Security: like all {@code /api/admin/*} endpoints this controller must be
 * protected by the same admin authentication / network policy as the rest of the
 * admin surface (see mcp-auth). It must never be exposed publicly - it grants
 * runtime control over which skill version is served to clients.</p>
 */
@RestController
@RequestMapping("/api/admin/skills")
public class SkillAdminController {

    private static final Logger log = LoggerFactory.getLogger(SkillAdminController.class);

    private final SkillRegistry registry;

    public SkillAdminController(SkillRegistry registry) {
        this.registry = registry;
    }

    /** List all active skills (one version each), sorted by name. */
    @GetMapping
    public Map<String, Object> listSkills() {
        List<SkillSpec> skills = registry.list();
        Map<String, Object> body = new HashMap<>();
        body.put("count", skills.size());
        body.put("skills", skills);
        return body;
    }

    /** Register a new version; activates it. */
    @PostMapping
    public ResponseEntity<Object> registerSkill(@RequestBody SkillSpec spec) {
        try {
            SkillSpec registered = registry.register(spec);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "registered", true,
                    "skill", registered));
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "registered", false,
                    "error", e.getMessage()));
        }
    }

    /** Active spec of one skill. */
    @GetMapping("/{name}")
    public ResponseEntity<Object> getSkill(@PathVariable String name) {
        try {
            return ResponseEntity.ok(registry.get(name));
        } catch (IllegalArgumentException e) {
            return notFound(e);
        }
    }

    /** Full version history (newest first) plus the active version marker. */
    @GetMapping("/{name}/versions")
    public ResponseEntity<Object> listVersions(@PathVariable String name) {
        try {
            List<SkillSpec> versions = registry.versions(name);
            SkillSpec active = registry.get(name);
            return ResponseEntity.ok(Map.of(
                    "name", name,
                    "activeVersion", active.getVersion(),
                    "versionCount", versions.size(),
                    "versions", versions));
        } catch (IllegalArgumentException e) {
            return notFound(e);
        }
    }

    /** Explicitly activate a historical version. */
    @PostMapping("/{name}/activate")
    public ResponseEntity<Object> activateVersion(@PathVariable String name,
                                                  @RequestBody Map<String, String> body) {
        String version = body == null ? null : body.get("version");
        if (version == null || version.isBlank()) {
            return badRequest(new IllegalArgumentException("version is required"));
        }
        try {
            SkillSpec activated = registry.activate(name, version);
            return ResponseEntity.ok(Map.of("activated", true, "skill", activated));
        } catch (IllegalArgumentException e) {
            return notFound(e);
        }
    }

    /** Fault-recovery rollback to the previous version. */
    @PostMapping("/{name}/rollback")
    public ResponseEntity<Object> rollback(@PathVariable String name) {
        try {
            SkillSpec rolledBack = registry.rollback(name);
            return ResponseEntity.ok(Map.of("rolledBack", true, "skill", rolledBack));
        } catch (IllegalArgumentException e) {
            return notFound(e);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "rolledBack", false,
                    "error", e.getMessage()));
        }
    }

    /** Configure gray routing weight (0-100) for a version. */
    @PostMapping("/{name}/gray")
    public ResponseEntity<Object> gray(@PathVariable String name,
                                       @RequestBody Map<String, Object> body) {
        String version = body == null ? null : String.valueOf(body.get("version"));
        Object weightObj = body == null ? null : body.get("weight");
        if (version == null || version.isBlank() || weightObj == null) {
            return badRequest(new IllegalArgumentException("version and weight are required"));
        }
        int weight;
        try {
            weight = Integer.parseInt(String.valueOf(weightObj));
        } catch (NumberFormatException e) {
            return badRequest(new IllegalArgumentException("weight must be an integer (0-100)"));
        }
        try {
            registry.gray(name, version, weight);
            return ResponseEntity.ok(Map.of(
                    "configured", true,
                    "name", name,
                    "version", version,
                    "weight", weight));
        } catch (IllegalArgumentException e) {
            return notFound(e);
        }
    }

    /** Remove a skill (all versions + gray config). */
    @DeleteMapping("/{name}")
    public ResponseEntity<Object> removeSkill(@PathVariable String name) {
        try {
            registry.remove(name);
            return ResponseEntity.ok(Map.of("removed", true, "name", name));
        } catch (IllegalArgumentException e) {
            return notFound(e);
        }
    }

    private ResponseEntity<Object> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    private ResponseEntity<Object> notFound(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }
}