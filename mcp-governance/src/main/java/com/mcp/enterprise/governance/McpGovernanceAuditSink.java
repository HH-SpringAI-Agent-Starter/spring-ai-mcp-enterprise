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
                 java.time.Instant from, java.time.Instant to, int limit) {

        public Query {
            limit = limit <= 0 ? 50 : Math.min(limit, 1000);
        }
    }

    /**
     * 治理审计事件。{@code arguments} 已在过滤器中脱敏。
     */
    record Event(Instant timestamp, String tool, String tier, String caller, String decision,
                 String approvalId, Map<String, Object> arguments, String message) {

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
            return m;
        }
    }
}