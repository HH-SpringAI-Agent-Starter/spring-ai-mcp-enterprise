package com.mcp.enterprise.governance;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * V1.26 治理审计出口 SPI（对齐 OWASP MCP Governance「No logging = no production use」）。
 *
 * <p>默认实现 {@link InMemoryGovernanceAuditSink} 保留最近 N 条事件并通过 SLF4J 输出；
 * 生产可替换为 Kafka/JDBC/OpenTelemetry 导出实现。</p>
 */
public interface McpGovernanceAuditSink {

    /** 记录一次治理判定事件。 */
    void record(Event event);

    /** 最近事件（按时间倒序，最多 limit 条）。 */
    List<Map<String, Object>> recent(int limit);

    /**
     * V1.30 多条件检索审计事件（按时间倒序，最多 query.limit() 条）。
     * 全部条件为空时等价于 recent(limit)，供 SIEM/取证/管理面板过滤查询。
     */
    List<Map<String, Object>> search(Query query);

    /**
     * V1.30 保留策略：删除所有早于 cutOff 的事件（合规 TTL 清理）。
     *
     * @return 删除的事件数；0 = 无删除或实现不支持
     */
    int deleteBefore(java.time.Instant cutOff);

    /** V1.30 审计检索条件（所有字段可 null/blank = 不过滤）。 */
    record Query(String tool, String caller, String decision, String tier,
                 java.time.Instant from, java.time.Instant to, int limit, String traceId) {

        public Query {
            limit = limit <= 0 ? 50 : Math.min(limit, 1000);
        }

        /** V1.30 兼容构造器（无 traceId 过滤）。 */
        public Query(String tool, String caller, String decision, String tier,
                     java.time.Instant from, java.time.Instant to, int limit) {
            this(tool, caller, decision, tier, from, to, limit, null);
        }
    }

    /**
     * 治理审计事件。{@code arguments} 已在过滤器中脱敏。
     *
     * <p>V1.31 新增 {@code traceId}/{@code spanId} 让审计事件与 OpenTelemetry/
     * Micrometer Tracing 调用链（W3C traceparent）关联，事故复盘时既能从审计事件
     * 打到对应 trace，也能从 trace 找到对应审计事件。</p>
     */
    record Event(Instant timestamp, String tool, String tier, String caller, String decision,
                 String approvalId, Map<String, Object> arguments, String message,
                 String traceId, String spanId) {

        /** V1.26~V1.30 兼容构造器（无 trace 信息）。 */
        public Event(Instant timestamp, String tool, String tier, String caller, String decision,
                     String approvalId, Map<String, Object> arguments, String message) {
            this(timestamp, tool, tier, caller, decision, approvalId, arguments, message, null, null);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("timestamp", timestamp.toString());
            m.put("tool", tool);
            m.put("tier", tier);
            m.put("caller", caller);
            m.put("decision", decision);
            m.put("approvalId", approvalId);
            m.put("arguments", arguments);
            m.put("message", message);
            if (traceId != null) {
                m.put("traceId", traceId);
            }
            if (spanId != null) {
                m.put("spanId", spanId);
            }
            return m;
        }
    }
}