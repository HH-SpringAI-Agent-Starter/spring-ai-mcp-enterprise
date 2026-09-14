package com.mcp.enterprise.gateway.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * V1.25 MCP Federation Gateway 管理 API。
 *
 * <pre>
 *   GET    /api/admin/gateway/upstreams                 - 所有上游状态（健康/工具数/上次同步）
 *   POST   /api/admin/gateway/sync                      - 全量刷新联邦工具
 *   POST   /api/admin/gateway/upstreams/{name}/sync     - 刷新单个上游
 *   DELETE /api/admin/gateway/upstreams/{name}/tools    - 移除该上游全部联邦工具（下线）
 *   GET    /api/admin/gateway/health                    - 聚合健康视图
 * </pre>
 *
 * <p>安全：与其它 {@code /api/admin/*} 一样，必须置于 mcp-auth / 网关鉴权之后，
 * 绝不能公网裸奔——它能在运行时增删工具、影响服务面。</p>
 */
@RestController
@RequestMapping("/api/admin/gateway")
public class McpGatewayAdminController {

    private static final Logger log = LoggerFactory.getLogger(McpGatewayAdminController.class);

    private final McpFederationManager manager;

    public McpGatewayAdminController(McpFederationManager manager) {
        this.manager = manager;
    }

    @GetMapping("/upstreams")
    public Map<String, Object> listUpstreams() {
        var upstreams = manager.listUpstreams();
        return Map.of("count", upstreams.size(), "upstreams", upstreams);
    }

    @PostMapping("/sync")
    public Mono<Map<String, Object>> syncAll() {
        log.info("🔄 [V1.25] 手动触发全量联邦同步");
        return manager.syncAll();
    }

    @PostMapping("/upstreams/{name}/sync")
    public Mono<Map<String, Object>> sync(@PathVariable String name) {
        log.info("🔄 [V1.25] 手动刷新上游 {}", name);
        return manager.sync(name);
    }

    @DeleteMapping("/upstreams/{name}/tools")
    public Mono<Map<String, Object>> removeTools(@PathVariable String name) {
        log.info("🗑️ [V1.25] 手动下线上游 {}", name);
        return manager.removeTools(name);
    }

    @GetMapping("/health")
    public Mono<Map<String, Object>> health() {
        return manager.health();
    }
}