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

    public synchronized int size() {
        return events.size();
    }
}