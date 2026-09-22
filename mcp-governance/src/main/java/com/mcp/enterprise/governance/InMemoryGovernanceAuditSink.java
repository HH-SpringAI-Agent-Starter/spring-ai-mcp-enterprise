package com.mcp.enterprise.governance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * V1.26 默认治理审计实现：有界环形缓冲 + SLF4J 输出。
 */
public class InMemoryGovernanceAuditSink implements McpGovernanceAuditSink {

    private static final Logger log = LoggerFactory.getLogger("MCP_GOVERNANCE_AUDIT");

    private final Deque<Event> events = new ArrayDeque<>();
    private final int maxEvents;
    private final boolean logToSlf4j;

    public InMemoryGovernanceAuditSink(int maxEvents, boolean logToSlf4j) {
        this.maxEvents = maxEvents > 0 ? maxEvents : 2000;
        this.logToSlf4j = logToSlf4j;
    }

    @Override
    public synchronized void record(Event event) {
        events.addFirst(event);
        while (events.size() > maxEvents) {
            events.removeLast();
        }
        if (logToSlf4j) {
            log.info("governance decision={} tool={} tier={} caller={} approval={} msg={}",
                    event.decision(), event.tool(), event.tier(), event.caller(),
                    event.approvalId(), event.message());
        }
    }

    @Override
    public synchronized List<Map<String, Object>> recent(int limit) {
        int n = limit <= 0 ? 50 : limit;
        List<Map<String, Object>> out = new ArrayList<>(Math.min(n, events.size()));
        int i = 0;
        for (Event e : events) {
            if (i++ >= n) {
                break;
            }
            out.add(e.toMap());
        }
        return out;
    }

    @Override
    public synchronized List<Map<String, Object>> search(Query query) {
        // V1.30: 内存过滤（与 JDBC 实现同样的语义：全条件 AND、时间倒序）
        List<Map<String, Object>> out = new ArrayList<>();
        int n = query.limit();
        int i = 0;
        for (Event e : events) {
            if (i++ >= n) {
                break;
            }
            if (matches(e, query)) {
                out.add(e.toMap());
            }
        }
        return out;
    }

    @Override
    public synchronized int deleteBefore(java.time.Instant cutOff) {
        if (cutOff == null) {
            return 0;
        }
        int before = events.size();
        events.removeIf(e -> e.timestamp() != null && e.timestamp().isBefore(cutOff));
        return before - events.size();
    }

    private static boolean matches(Event e, Query q) {
        if (q.tool() != null && !q.tool().isBlank() && !q.tool().equals(e.tool())) {
            return false;
        }
        if (q.caller() != null && !q.caller().isBlank() && !q.caller().equals(e.caller())) {
            return false;
        }
        if (q.decision() != null && !q.decision().isBlank() && !q.decision().equals(e.decision())) {
            return false;
        }
        if (q.tier() != null && !q.tier().isBlank() && !q.tier().equals(e.tier())) {
            return false;
        }
        if (q.from() != null && (e.timestamp() == null || e.timestamp().isBefore(q.from()))) {
            return false;
        }
        if (q.to() != null && (e.timestamp() == null || e.timestamp().isAfter(q.to()))) {
            return false;
        }
        // V1.31: traceId 过滤（审计事件与调用链关联后可按 trace 回溯）
        if (q.traceId() != null && !q.traceId().isBlank()
                && !q.traceId().equalsIgnoreCase(e.traceId())) {
            return false;
        }
        return true;
    }

    public synchronized int size() {
        return events.size();
    }
}