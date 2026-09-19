package com.mcp.enterprise.governance;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * V1.26 MCP 治理模块配置属性（前缀 {@code mcp.enterprise.governance}）。
 *
 * <pre>
 * mcp:
 *   enterprise:
 *     governance:
 *       enabled: true                 # 模块总开关（默认开）
 *       enforce: false                # 运行时强制模式（默认关 = 只登记/审计不拦截，灰度开启）
 *       message-paths:                # 受治理的 MCP JSON-RPC 入口
 *         - /api/mcp/message
 *         - /api/mcp/v2/message
 *         - /mcp
 *       approval-header: X-MCP-Approval-Id   # 客户端回传审批令牌的请求头
 *       default-tier: T2              # 无法归类时的默认等级（安全默认）
 *       require-approval-tiers: [T3, T4]     # 需要人工审批的等级（Fail-closed）
 *       deny-tiers: []                # 直接拒绝的等级（硬闸门）
 *       tool-tiers:                   # 显式覆盖：工具名 -> 等级
 *         finance_transfer: T4
 *       approval:
 *         enabled: true
 *         ttl-seconds: 900            # 审批有效期（15 分钟）
 *         max-pending: 5000           # 挂起审批上限（防滥用/防存储膨胀）
 *         store: memory               # V1.28: memory | jdbc（jdbc 需要数据源，多实例共享审批状态）
 *         table: mcp_approval_requests # V1.28: JDBC 表名（store=jdbc 时生效）
 *         init-schema: true           # V1.28: 启动时幂等建表（store=jdbc 时生效）
 *       redaction:
 *         enabled: true
 *         mask: "***"
 *       audit:
 *         max-events: 2000
 *         log-to-slf4j: true
 * </pre>
 */
@ConfigurationProperties(prefix = "mcp.enterprise.governance")
public class McpGovernanceProperties {

    /** 模块总开关 */
    private boolean enabled = true;

    /** 运行时强制模式：false 时仅登记风险评估与审批请求，不拦截调用（灰度开启） */
    private boolean enforce = false;

    /** 受治理的 MCP 入口路径 */
    private List<String> messagePaths = new ArrayList<>(List.of(
            "/api/mcp/message", "/api/mcp/v2/message", "/mcp"));

    /** 客户端回传审批令牌的请求头 */
    private String approvalHeader = "X-MCP-Approval-Id";

    /** 无法归类时的默认等级（安全默认 T2 敏感读） */
    private String defaultTier = "T2";

    /** 需要人工审批的等级 */
    private List<String> requireApprovalTiers = new ArrayList<>(List.of("T3", "T4"));

    /** 直接拒绝的等级（硬闸门，可空） */
    private List<String> denyTiers = new ArrayList<>();

    /** 显式覆盖：工具名 -> 等级代码 */
    private Map<String, String> toolTiers = new LinkedHashMap<>();

    /** 审批配置 */
    private Approval approval = new Approval();

    /** 敏感数据脱敏配置 */
    private Redaction redaction = new Redaction();

    /** 审计配置 */
    private Audit audit = new Audit();

    // 关键词启发式（仅用于无显式配置时的自动归类）
    private List<String> criticalKeywords = new ArrayList<>(List.of(
            "delete", "drop", "truncate", "purge", "wipe", "shutdown", "reset",
            "revoke", "grant", "admin", "exec", "shell", "deploy", "redeploy", "rollback"));
    private List<String> writeKeywords = new ArrayList<>(List.of(
            "create", "update", "add", "set", "put", "post", "insert", "patch",
            "modify", "refund", "transfer", "pay", "send", "publish", "submit",
            "enable", "disable", "remove", "cancel", "close", "open"));
    private List<String> sensitiveKeywords = new ArrayList<>(List.of(
            "password", "secret", "token", "credential", "key", "pii", "user",
            "account", "payment", "invoice", "salary", "health", "financ", "customer"));
    private List<String> readKeywords = new ArrayList<>(List.of(
            "get", "list", "query", "search", "read", "find", "describe", "fetch",
            "ping", "health", "status", "count", "calc", "compute", "weather", "exist"));

    // ===== Getters & Setters =====

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isEnforce() { return enforce; }
    public void setEnforce(boolean enforce) { this.enforce = enforce; }

    public List<String> getMessagePaths() { return messagePaths; }
    public void setMessagePaths(List<String> messagePaths) { this.messagePaths = messagePaths; }

    public String getApprovalHeader() { return approvalHeader; }
    public void setApprovalHeader(String approvalHeader) { this.approvalHeader = approvalHeader; }

    public String getDefaultTier() { return defaultTier; }
    public void setDefaultTier(String defaultTier) { this.defaultTier = defaultTier; }

    public List<String> getRequireApprovalTiers() { return requireApprovalTiers; }
    public void setRequireApprovalTiers(List<String> requireApprovalTiers) { this.requireApprovalTiers = requireApprovalTiers; }

    public List<String> getDenyTiers() { return denyTiers; }
    public void setDenyTiers(List<String> denyTiers) { this.denyTiers = denyTiers; }

    public Map<String, String> getToolTiers() { return toolTiers; }
    public void setToolTiers(Map<String, String> toolTiers) { this.toolTiers = toolTiers; }

    public Approval getApproval() { return approval; }
    public void setApproval(Approval approval) { this.approval = approval; }

    public Redaction getRedaction() { return redaction; }
    public void setRedaction(Redaction redaction) { this.redaction = redaction; }

    public Audit getAudit() { return audit; }
    public void setAudit(Audit audit) { this.audit = audit; }

    public List<String> getCriticalKeywords() { return criticalKeywords; }
    public void setCriticalKeywords(List<String> criticalKeywords) { this.criticalKeywords = criticalKeywords; }

    public List<String> getWriteKeywords() { return writeKeywords; }
    public void setWriteKeywords(List<String> writeKeywords) { this.writeKeywords = writeKeywords; }

    public List<String> getSensitiveKeywords() { return sensitiveKeywords; }
    public void setSensitiveKeywords(List<String> sensitiveKeywords) { this.sensitiveKeywords = sensitiveKeywords; }

    public List<String> getReadKeywords() { return readKeywords; }
    public void setReadKeywords(List<String> readKeywords) { this.readKeywords = readKeywords; }

    // ===== 嵌套配置 =====

    public static class Approval {
        private boolean enabled = true;
        private long ttlSeconds = 900;
        private int maxPending = 5000;

        /** V1.28: 审批存储后端：memory（默认）| jdbc */
        private String store = "memory";

        /** V1.28: JDBC 表名（store=jdbc 时生效） */
        private String table = "mcp_approval_requests";

        /** V1.28: 启动时幂等建表（store=jdbc 时生效） */
        private boolean initSchema = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public long getTtlSeconds() { return ttlSeconds; }
        public void setTtlSeconds(long ttlSeconds) { this.ttlSeconds = ttlSeconds; }
        public int getMaxPending() { return maxPending; }
        public void setMaxPending(int maxPending) { this.maxPending = maxPending; }
        public String getStore() { return store; }
        public void setStore(String store) { this.store = store; }
        public String getTable() { return table; }
        public void setTable(String table) { this.table = table; }
        public boolean isInitSchema() { return initSchema; }
        public void setInitSchema(boolean initSchema) { this.initSchema = initSchema; }
    }

    public static class Redaction {
        private boolean enabled = true;
        private String mask = "***";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getMask() { return mask; }
        public void setMask(String mask) { this.mask = mask; }
    }

    public static class Audit {
        private int maxEvents = 2000;
        private boolean logToSlf4j = true;

        public int getMaxEvents() { return maxEvents; }
        public void setMaxEvents(int maxEvents) { this.maxEvents = maxEvents; }
        public boolean isLogToSlf4j() { return logToSlf4j; }
        public void setLogToSlf4j(boolean logToSlf4j) { this.logToSlf4j = logToSlf4j; }
    }
}