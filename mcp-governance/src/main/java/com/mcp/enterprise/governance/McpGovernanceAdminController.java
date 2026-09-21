package com.mcp.enterprise.governance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * V1.26 治理管理 REST API：人工审批 + 审计查询 + 策略视图。
 *
 * <pre>
 *   GET    /api/admin/governance/approvals?status=PENDING      - 审批列表（可按状态过滤）
 *   GET    /api/admin/governance/approvals/{id}                - 审批详情
 *   POST   /api/admin/governance/approvals/{id}/approve        - 批准 {decidedBy, reason}
 *   POST   /api/admin/governance/approvals/{id}/reject         - 拒绝 {decidedBy, reason}
 *   GET    /api/admin/governance/stats                         - 审批统计
 *   GET    /api/admin/governance/audit?limit=50                - 最近治理审计事件
 *   GET    /api/admin/governance/policy                        - 当前生效策略视图（等级/关键词/通道）
 * </pre>
 *
 * <p>安全：与其它 {@code /api/admin/*} 一样必须置于 mcp-auth / 网关鉴权之后。
 * 批准即授权执行高等级工具，绝不能公网裸奔。</p>
 */
@RestController
@RequestMapping("/api/admin/governance")
public class McpGovernanceAdminController {

    private static final Logger log = LoggerFactory.getLogger(McpGovernanceAdminController.class);

    private final McpApprovalService approvalService;
    private final McpGovernanceAuditSink auditSink;
    private final McpGovernanceGuard guard;
    private final SensitiveDataRedactor redactor;
    private final McpGovernanceProperties properties;

    public McpGovernanceAdminController(McpApprovalService approvalService,
                                        McpGovernanceAuditSink auditSink,
                                        McpGovernanceGuard guard,
                                        SensitiveDataRedactor redactor,
                                        McpGovernanceProperties properties) {
        this.approvalService = approvalService;
        this.auditSink = auditSink;
        this.guard = guard;
        this.redactor = redactor;
        this.properties = properties;
    }

    @GetMapping("/approvals")
    public Map<String, Object> listApprovals(@RequestParam(required = false) String status) {
        ApprovalRequest.Status st = parseStatus(status);
        List<Map<String, Object>> items = approvalService.list(st).stream()
                .map(ApprovalRequest::toMap)
                .toList();
        return Map.of("count", items.size(), "approvals", items);
    }

    @GetMapping("/approvals/{id}")
    public Map<String, Object> getApproval(@PathVariable String id) {
        ApprovalRequest req = approvalService.get(id);
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "审批请求不存在: " + id);
        }
        return req.toMap();
    }

    @PostMapping("/approvals/{id}/approve")
    public Map<String, Object> approve(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        log.info("🛡 [V1.26] 审批通过: id={} by={}", id, body == null ? null : body.get("decidedBy"));
        ApprovalRequest req = approvalService.approve(id,
                body == null ? null : body.get("decidedBy"),
                body == null ? null : body.get("reason"));
        return req.toMap();
    }

    @PostMapping("/approvals/{id}/reject")
    public Map<String, Object> reject(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        log.info("🛡 [V1.26] 审批拒绝: id={} by={}", id, body == null ? null : body.get("decidedBy"));
        ApprovalRequest req = approvalService.reject(id,
                body == null ? null : body.get("decidedBy"),
                body == null ? null : body.get("reason"));
        return req.toMap();
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Object> stats = approvalService.stats();
        stats.put("auditEvents", auditSink.recent(0).size());
        return stats;
    }

    @GetMapping("/audit")
    public Map<String, Object> audit(@RequestParam(defaultValue = "50") int limit,
                                     @RequestParam(required = false) String tool,
                                     @RequestParam(required = false) String caller,
                                     @RequestParam(required = false) String decision,
                                     @RequestParam(required = false) String tier,
                                     @RequestParam(required = false) String from,
                                     @RequestParam(required = false) String to) {
        // V1.30: 多条件过滤检索（SIEM/取证/管理面板）
        McpGovernanceAuditSink.Query q = new McpGovernanceAuditSink.Query(
                tool, caller, decision, tier, parseInstant(from), parseInstant(to), limit);
        List<Map<String, Object>> events = auditSink.search(q);
        return Map.of("count", events.size(), "events", events);
    }

    @GetMapping(value = "/audit/export", produces = "text/csv;charset=UTF-8")
    public String exportCsv(@RequestParam(defaultValue = "1000") int limit,
                            @RequestParam(required = false) String tool,
                            @RequestParam(required = false) String caller,
                            @RequestParam(required = false) String decision,
                            @RequestParam(required = false) String tier,
                            @RequestParam(required = false) String from,
                            @RequestParam(required = false) String to) {
        // V1.30: CSV 导出（SIEM 导入 / Excel 取证 / 监管报送）；字段含逗号/引号/换行时按 RFC 4180 转义
        McpGovernanceAuditSink.Query q = new McpGovernanceAuditSink.Query(
                tool, caller, decision, tier, parseInstant(from), parseInstant(to), limit);
        List<Map<String, Object>> events = auditSink.search(q);
        StringBuilder sb = new StringBuilder();
        sb.append("\uFEFF"); // UTF-8 BOM，Excel 直接打开中文不乱码
        sb.append("timestamp,tool,tier,caller,decision,approvalId,message\r\n");
        for (Map<String, Object> e : events) {
            sb.append(csv(e.get("timestamp"))).append(',')
              .append(csv(e.get("tool"))).append(',')
              .append(csv(e.get("tier"))).append(',')
              .append(csv(e.get("caller"))).append(',')
              .append(csv(e.get("decision"))).append(',')
              .append(csv(e.get("approvalId"))).append(',')
              .append(csv(e.get("message"))).append("\r\n");
        }
        return sb.toString();
    }

    @PostMapping("/audit/prune")
    public Map<String, Object> prune(@RequestParam(defaultValue = "90") int retentionDays) {
        // V1.30: 保留策略——清理早于 retentionDays 天的历史审计事件（合规 TTL，可配 cron 定期触发）
        java.time.Instant cutOff = java.time.Instant.now().minus(java.time.Duration.ofDays(Math.max(1, retentionDays)));
        int deleted = auditSink.deleteBefore(cutOff);
        log.info("🧹 [V1.30] audit prune: retentionDays={}, deleted={}", retentionDays, deleted);
        return Map.of("retentionDays", retentionDays, "cutOff", cutOff.toString(), "deleted", deleted);
    }

    private static java.time.Instant parseInstant(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return java.time.Instant.parse(s);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "非法时间格式(需 ISO-8601，如 2026-09-01T00:00:00Z): " + s);
        }
    }

    private static String csv(Object v) {
        if (v == null) {
            return "";
        }
        String s = String.valueOf(v);
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    @GetMapping("/policy")
    public Map<String, Object> policy() {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("enabled", properties.isEnabled());
        result.put("enforce", properties.isEnforce());
        result.put("approvalHeader", properties.getApprovalHeader());
        result.put("messagePaths", properties.getMessagePaths());
        result.put("defaultTier", properties.getDefaultTier());
        result.put("requireApprovalTiers", properties.getRequireApprovalTiers());
        result.put("denyTiers", properties.getDenyTiers());
        result.put("toolTiers", properties.getToolTiers());
        result.put("approval", Map.of(
                "enabled", properties.getApproval().isEnabled(),
                "ttlSeconds", properties.getApproval().getTtlSeconds(),
                "maxPending", properties.getApproval().getMaxPending()));
        result.put("redaction", Map.of(
                "enabled", properties.getRedaction().isEnabled(),
                "mask", properties.getRedaction().getMask(),
                "rules", redactor.ruleNames()));
        result.put("audit", Map.of(
                "maxEvents", properties.getAudit().getMaxEvents(),
                "logToSlf4j", properties.getAudit().isLogToSlf4j()));
        result.put("guard", Map.of(
                "requireApprovalTiers", guard.getRequireApprovalTiers().stream().map(RiskTier::getCode).toList(),
                "denyTiers", guard.getDenyTiers().stream().map(RiskTier::getCode).toList()));
        return result;
    }

    private static ApprovalRequest.Status parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ApprovalRequest.Status.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "非法状态: " + status + "（可选: PENDING/APPROVED/REJECTED/EXPIRED/CONSUMED）");
        }
    }
}