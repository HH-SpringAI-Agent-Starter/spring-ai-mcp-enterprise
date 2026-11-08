package com.example.mcp.hardening;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * MCP 网关前置防护过滤器 —— 参考实现（REFERENCE ONLY）。
 *
 * 背景（2026-11-03 ~ 11-06 公开实证，见 docs/earnings-report-2026-11-06.md）：
 *   - Wiz.io 90 天蜜罐：LiteLLM MCP Gateway 接受一个字符的 bearer token
 *     (CVE-2026-59822)、测试端点命令注入 (CVE-2026-42271)、host-header 绕过
 *     (CVE-2026-48710) 串成未认证 RCE；攻击者从运行进程内存中直接取走代理主密钥。
 *   - CVE-2026-108395：官方 Java SDK mcp-core 全 SDK 无界请求体 DoS，各家统一修 4 MiB。
 *   - CVE-2026-111769：官方 TS SDK 跨源重定向保留自定义头与 mcp-session-id。
 *   - CVE-2026-121419：SurrealDB MCP 会话指纹按 access method 名而非用户 → 会话劫持。
 *   - CVE-2026-121458：Instatic MCP 工具跳过主 API 的对象级授权。
 *   - CVE-2026-123272 / 123271：mcp-go / ChatCLI OAuth resource confusion（RFC 9728 §3.3）。
 *
 * 本过滤器把「鉴权 / 体积 / 同源 / 会话主体 / 工具准入 / 写操作 HITL / 审计」
 * 七项收敛到同一入口，任何一项失败一律 fail-closed。
 *
 * ⚠️ 这是示例片段，位于 examples/ 下，不在 Maven reactor 中，不参与 CI 构建。
 *    集成时应把 TokenVerifier / CallerPolicy / AuditSink 换成项目内
 *    mcp-auth / mcp-governance / mcp-audit 的实际 Bean，详见 README.md。
 *
 * V1.66（2026-11-08）：接入 {@link McpSecurityMetrics}，把 V1.65
 * {@code examples/monitor-alert-rules/} 定义的 {@code mcp_security_*} 埋点契约
 * 落到每个拒绝分支 —— 告警规则从「约定」变成「可运行」。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class McpGatewayGuardFilter extends OncePerRequestFilter {

    /** 对齐官方 SDK 各家统一修复口径（CVE-2026-108395）。 */
    static final long MAX_BODY_BYTES = 4L * 1024 * 1024; // 4 MiB

    private final TokenVerifier verifier;
    private final CallerPolicy policy;
    private final AuditSink audit;
    private final McpSecurityMetrics metrics;

    public McpGatewayGuardFilter(TokenVerifier verifier, CallerPolicy policy, AuditSink audit) {
        this(verifier, policy, audit, new McpSecurityMetrics());
    }

    /** 传入应用级单例，便于把 {@link #getMetrics()} 挂到 Prometheus 导出端点。 */
    public McpGatewayGuardFilter(TokenVerifier verifier, CallerPolicy policy, AuditSink audit,
                                 McpSecurityMetrics metrics) {
        this.verifier = verifier;
        this.policy = policy;
        this.audit = audit;
        this.metrics = metrics;
    }

    public McpSecurityMetrics getMetrics() { return metrics; }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp,
                                    FilterChain chain) throws ServletException, IOException {

        final String tenant = tenantOf(req);
        final String authHeader = req.getHeader("Authorization");

        // (0) 短 bearer：CVE-2026-59822 一个字符 token 就过了 LiteLLM MCP Gateway
        if (authHeader != null && authHeader.length() >= 7
                && authHeader.regionMatches(true, 0, "Bearer ", 0, 7)
                && authHeader.substring(7).trim().length() < 8) {
            metrics.shortBearer(tenant);
            reject(resp, 401, req, "SHORT_BEARER", "bearer length < 8");
            return;
        }

        // (1) 请求体硬上限：声明值先拦，实际流式读取由 BoundedRequestWrapper 二次校验
        if (req.getContentLengthLong() > MAX_BODY_BYTES) {
            metrics.bodyTooLarge(tenant);
            reject(resp, 413, req, "BODY_LIMIT", "declared=" + req.getContentLengthLong());
            return;
        }

        // (2) 同源：Host / X-Forwarded-Host 必须一致，禁止跟随跨源重定向
        if (!sameOrigin(req)) {
            metrics.hostMismatch(tenant);
            reject(resp, 403, req, "CROSS_ORIGIN", "host=" + req.getHeader("Host"));
            return;
        }

        // (2b) 注册期：伪造配置 command 启动矿机且握手仍正常（CVE-2026-42271）
        if (req.getParameter("command") != null || req.getHeader("X-MCP-Command") != null) {
            metrics.registrationRejected("command_field", tenant);
            reject(resp, 403, req, "REGISTRATION_REJECTED", "command field not allowed");
            return;
        }

        // (3) 鉴权 fail-closed：完整校验 signature / aud / iss / exp / scope，异常绝不降级放行
        //     audience / resource 不匹配单列 reason，供 McpAudienceMismatch 规则精确告警
        final Caller caller;
        try {
            caller = verifier.verify(authHeader);
        } catch (AuthException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            String code = (msg.contains("aud") || msg.contains("resource"))
                    ? "AUDIENCE_MISMATCH" : "AUTH_FAIL";
            reject(resp, 401, req, code, e.getMessage());
            return;
        }

        // (4) 会话指纹绑定「租户 + 用户主体 + 客户端」，绝不用 access method 名
        String sessionKey = caller.tenantId() + ":" + caller.subject() + ":" + caller.clientId();

        // (5) 工具准入：新 server/tool 默认 disabled（对标 Uber 800+ servers 生产实践），
        //     工具级 ACL 必须复用主 API 的对象级授权，不另写一套宽松逻辑
        String tool = req.getHeader("X-MCP-Tool");
        if (tool == null || !policy.isToolEnabled(tool)
                || !policy.hasScope(caller.subject(), tool)) {
            reject(resp, 403, req, "TOOL_DENIED", "tool=" + tool);
            return;
        }

        // (6) 写操作强制人工确认：先落审批单，不直接执行
        if (policy.isWrite(tool) && !policy.approvalGranted(req)) {
            String ticket = audit.pending(req, tool, caller);
            metrics.pendingApproval("high", +1);          // 审批结单时须 -1
            metrics.rejection("PENDING_APPROVAL", tool, caller.tenantId());
            resp.setStatus(HttpServletResponse.SC_ACCEPTED);
            resp.setContentType("application/json;charset=UTF-8");
            resp.getWriter().write("{\"status\":\"PENDING_APPROVAL\",\"ticket\":\"" + ticket + "\"}");
            return;
        }

        // (7) 全程留痕：提示词 / 检索片段 / 工具入参出参 / 决策依据
        //     凭据代理类工具单独计数，供 McpCredentialAccessAnomaly 规则检测短时突增
        if (tool.endsWith("_credential_proxy")) {
            metrics.credentialAccess(caller.subject(), tool);
        }
        try {
            chain.doFilter(new BoundedRequestWrapper(req, MAX_BODY_BYTES, sessionKey), resp);
        } finally {
            audit.record(req, tool, caller, resp.getStatus());
        }
    }

    private boolean sameOrigin(HttpServletRequest req) {
        String origin = req.getHeader("Origin");
        String host = req.getHeader("Host");
        String forwarded = req.getHeader("X-Forwarded-Host");
        if (forwarded != null && !forwarded.equals(host)) {
            return false;                        // 代理链不一致一律拒绝
        }
        if (origin == null) {
            return host != null;                 // 非浏览器客户端只校验 Host 存在
        }
        return origin.equals(host) || origin.endsWith("://" + host);
    }

    private void reject(HttpServletResponse resp, int status, HttpServletRequest req,
                        String code, String detail) throws IOException {
        // 所有 fail-closed 拒绝统一落 mcp_security_rejections_total，告警规则才有单一真源
        metrics.rejection(code, toolOf(req), tenantOf(req));
        audit.reject(req, code, detail);
        resp.setStatus(status);
        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().write("{\"error\":\"" + code + "\",\"detail\":\"" + detail + "\"}");
    }

    private static String toolOf(HttpServletRequest req) {
        String t = req.getHeader("X-MCP-Tool");
        return t == null ? "" : t;
    }

    private static String tenantOf(HttpServletRequest req) {
        String t = req.getHeader("X-Tenant-Id");
        return t == null ? "unknown" : t;
    }

    // ---- 以下为示例契约：集成时替换为项目内实现 ----

    public record Caller(String tenantId, String subject, String clientId) {}

    public static class AuthException extends RuntimeException {
        public AuthException(String message) { super(message); }
    }

    public interface TokenVerifier {
        /** 必须校验 signature / audience / issuer / expiry / scope；任何失败抛 AuthException。 */
        Caller verify(String authorizationHeader) throws AuthException;
    }

    public interface CallerPolicy {
        boolean isToolEnabled(String tool);
        boolean hasScope(String subject, String tool);
        boolean isWrite(String tool);
        boolean approvalGranted(HttpServletRequest req);
    }

    public interface AuditSink {
        void reject(HttpServletRequest req, String code, String detail);
        String pending(HttpServletRequest req, String tool, Caller caller);
        void record(HttpServletRequest req, String tool, Caller caller, int status);
    }
}
