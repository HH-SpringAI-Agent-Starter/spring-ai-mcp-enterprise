package com.mcp.enterprise.governance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * V1.26 内存审批存储（有界：超过上限逐出最旧记录，防止无界增长）。
 */
public class InMemoryApprovalStore implements ApprovalStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryApprovalStore.class);

    private final Map<String, ApprovalRequest> store = new ConcurrentHashMap<>();
    private final int maxEntries;

    public InMemoryApprovalStore(int maxEntries) {
        this.maxEntries = maxEntries > 0 ? maxEntries : 5000;
    }

    @Override
    public synchronized void save(ApprovalRequest request) {
        store.put(request.getId(), request);
        if (store.size() > maxEntries) {
            // 逐出最旧的已结束记录；若全是 PENDING（异常积压）则逐出最旧 PENDING
            store.values().stream()
                    .filter(r -> r.getStatus() != ApprovalRequest.Status.PENDING)
                    .min(Comparator.comparing(ApprovalRequest::getCreatedAt))
                    .ifPresentOrElse(r -> store.remove(r.getId()),
                            () -> store.values().stream()
                                    .min(Comparator.comparing(ApprovalRequest::getCreatedAt))
                                    .ifPresent(r -> store.remove(r.getId())));
            log.warn("🗃️ [V1.26] 审批存储达到上限 {}，已逐出最旧记录", maxEntries);
        }
    }

    @Override
    public ApprovalRequest get(String id) {
        return id == null ? null : store.get(id);
    }

    @Override
    public List<ApprovalRequest> list(ApprovalRequest.Status status) {
        List<ApprovalRequest> result = new ArrayList<>();
        for (ApprovalRequest r : store.values()) {
            if (status == null || r.getStatus() == status) {
                result.add(r);
            }
        }
        result.sort(Comparator.comparing(ApprovalRequest::getCreatedAt).reversed());
        return result;
    }

    @Override
    public int count() {
        return store.size();
    }

    @Override
    public void update(ApprovalRequest request) {
        store.put(request.getId(), request);
    }
}