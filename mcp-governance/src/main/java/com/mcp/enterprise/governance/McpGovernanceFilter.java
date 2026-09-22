package com.mcp.enterprise.governance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * V1.26 治理过滤器：在 MCP JSON-RPC 入口处对 {@code tools/call} 做风险判定与人工审批拦截。
 *
 * <p>接入方式：仅当 {@code mcp.enterprise.governance.enforce=true} 时挂载到
 * {@code message-paths} 指定的入口（默认 {@code /api/mcp/message}、{@code /api/mcp/v2/message}、{@code /mcp}）。
 * 非 {@code tools/call} 报文（initialize / tools/list / ping）直接透传。</p>
 *
 * <p>对需审批的工具返回 JSON-RPC 错误 {@code -32092 approval_required}（HTTP 200，协议正确），
 * 客户端拿到 {@code approvalId} 后由管理员审批，再在原请求携带
 * {@code X-MCP-Approval-Id: <id>} 重试即可执行。</p>
 */
public class McpGovernanceFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(McpGovernanceFilter.class);

    /** 请求属性键：本次调用的风险等级（供下游审计复用） */
    public static final String ATTR_TIER = "mcp.governance.tier";
    public static final String ATTR_APPROVAL_ID = "mcp.governance.approvalId";

    public static final int CODE_APPROVAL_REQUIRED = -32092;
    public static final int CODE_GOVERNANCE_DENIED = -32093;

    private final McpGovernanceProperties properties;
    private final McpGovernanceGuard guard;
    private final McpGovernanceAuditSink auditSink;
    private final SensitiveDataRedactor redactor;
    private final ObjectMapper objectMapper;

    public McpGovernanceFilter(McpGovernanceProperties properties, McpGovernanceGuard guard,
                               McpGovernanceAuditSink auditSink, SensitiveDataRedactor redactor,
                               ObjectMapper objectMapper) {
        this.properties = properties;
        this.guard = guard;
        this.auditSink = auditSink;
        this.redactor = redactor;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled() || !properties.isEnforce()) {
            return true;
        }
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        if (uri == null) {
            return true;
        }
        String ctx = request.getContextPath();
        String path = (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx)) ? uri.substring(ctx.length()) : uri;
        for (String p : properties.getMessagePaths()) {
            if (path.equals(p) || path.endsWith(p)) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        CachedBodyHttpServletRequest cached = new CachedBodyHttpServletRequest(request);
        JsonNode root;
        try {
            root = objectMapper.readTree(cached.getBody());
        } catch (Exception e) {
            // 非 JSON 或空体：不干预，交回下游
            filterChain.doFilter(cached, response);
            return;
        }
        if (root == null || !root.path("method").asText("").equals("tools/call")) {
            filterChain.doFilter(cached, response);
            return;
        }

        String toolName = root.path("params").path("name").asText("");
        JsonNode idNode = root.get("id");
        Map<String, Object> arguments = toArgumentsMap(root.path("params").path("arguments"));
        String caller = resolveCaller(request);
        String approvalId = request.getHeader(properties.getApprovalHeader());

        // V1.31: 解析 W3C traceparent / X-Request-Id / MDC，审计事件与调用链关联
        TraceContext.Parsed trace = TraceContext.parse(
                request.getHeader(TraceContext.HEADER_TRACEPARENT),
                request.getHeader(TraceContext.HEADER_X_REQUEST_ID),
                org.slf4j.MDC.get("traceId"));
        request.setAttribute(TraceContext.ATTR_TRACE_ID, trace.traceId());
        if (trace.spanId() != null) {
            request.setAttribute(TraceContext.ATTR_SPAN_ID, trace.spanId());
        }

        GovernanceDecision decision = guard.evaluate(toolName, caller, arguments, approvalId);

        auditSink.record(new McpGovernanceAuditSink.Event(Instant.now(), toolName,
                decision.tier() == null ? null : decision.tier().getCode(), caller,
                decision.outcome().name(), decision.approvalId(),
                redactor.redactMap(arguments), decision.message(), trace.traceId(), trace.spanId()));

        switch (decision.outcome()) {
            case ALLOW -> {
                request.setAttribute(ATTR_TIER, decision.tier() == null ? null : decision.tier().getCode());
                if (decision.approvalId() != null) {
                    request.setAttribute(ATTR_APPROVAL_ID, decision.approvalId());
                }
                filterChain.doFilter(cached, response);
            }
            case REQUIRE_APPROVAL -> writeJsonRpcError(response, idNode, CODE_APPROVAL_REQUIRED,
                    decision.message(), Map.of(
                            "approvalId", decision.approvalId(),
                            "tier", decision.tier() == null ? null : decision.tier().getCode(),
                            "status", "PENDING",
                            "expiresAt", decision.approvalExpiresAt() == null ? null : decision.approvalExpiresAt().toString(),
                            "approvalHeader", properties.getApprovalHeader()));
            case DENY -> writeJsonRpcError(response, idNode, CODE_GOVERNANCE_DENIED,
                    decision.message(), Map.of(
                            "errorCode", decision.errorCode(),
                            "tier", decision.tier() == null ? null : decision.tier().getCode()));
        }
    }

    private String resolveCaller(HttpServletRequest request) {
        String caller = request.getHeader("X-MCP-Caller");
        if (caller == null || caller.isBlank()) {
            caller = request.getHeader("X-Client-Id");
        }
        if (caller == null || caller.isBlank()) {
            caller = request.getHeader("X-Tenant-Id");
        }
        if (caller == null || caller.isBlank()) {
            String auth = request.getHeader("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) {
                caller = "bearer:" + Integer.toHexString(auth.substring(7).hashCode());
            }
        }
        return (caller == null || caller.isBlank()) ? "anonymous" : caller;
    }

    private Map<String, Object> toArgumentsMap(JsonNode argumentsNode) {
        if (argumentsNode == null || argumentsNode.isMissingNode() || argumentsNode.isNull()) {
            return Map.of();
        }
        try {
            return objectMapper.convertValue(argumentsNode,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private void writeJsonRpcError(HttpServletResponse response, JsonNode idNode, int code,
                                   String message, Map<String, Object> data) throws IOException {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        error.put("data", data);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", idNode == null || idNode.isNull() ? null : objectMapper.convertValue(idNode, Object.class));
        body.put("error", error);

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
        response.getWriter().flush();
        log.info("🛡️ [V1.26] 拦截 tools/call (code={}): {}", code, data);
    }
}