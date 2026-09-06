package com.mcp.enterprise.registry;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * V1.21 Skill Registry discovery API - read-only endpoint for MCP clients
 * (mirrors the tool-discovery shape of {@code /api/mcp/tools}).
 *
 * <pre>
 *   GET /api/mcp/skills       - active skills with version + input schema
 *   GET /api/mcp/skills/{name} - active spec of one skill
 * </pre>
 *
 * <p>Clients use this to discover which skill versions are currently served,
 * including the active version stamp - useful for agents that log which version
 * of a skill produced a result (auditability).</p>
 */
@RestController
@RequestMapping("/api/mcp/skills")
public class SkillDiscoveryController {

    private final SkillRegistry registry;

    public SkillDiscoveryController(SkillRegistry registry) {
        this.registry = registry;
    }

    /** Active skills in MCP-tool-compatible discovery shape. */
    @GetMapping
    public Map<String, Object> discoverSkills() {
        List<SkillSpec> skills = registry.list();
        List<Map<String, Object>> result = skills.stream()
                .map(this::toDiscoveryShape)
                .toList();
        Map<String, Object> body = new HashMap<>();
        body.put("count", result.size());
        body.put("skills", result);
        return body;
    }

    /** Active spec of one skill. */
    @GetMapping("/{name}")
    public ResponseEntity<Object> getSkill(@PathVariable String name) {
        try {
            return ResponseEntity.ok(toDiscoveryShape(registry.get(name)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    private Map<String, Object> toDiscoveryShape(SkillSpec spec) {
        Map<String, Object> shape = new LinkedHashMap<>();
        shape.put("name", spec.getName());
        shape.put("description", spec.getDescription());
        shape.put("version", spec.getVersion());
        shape.put("status", spec.getStatus().name());
        shape.put("category", spec.getCategory());
        shape.put("inputSchema", spec.getInputSchema());
        return shape;
    }
}