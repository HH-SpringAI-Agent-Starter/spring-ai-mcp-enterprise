package com.mcp.enterprise.governance;

import java.util.List;

/**
 * V1.26 审批存储 SPI。默认提供内存实现（{@link InMemoryApprovalStore}），
 * 生产可替换为 JDBC/Redis 实现并保持审批状态跨实例可见。
 */
public interface ApprovalStore {

    void save(ApprovalRequest request);

    ApprovalRequest get(String id);

    /** status 为 null 时返回全部（按创建时间倒序）。 */
    List<ApprovalRequest> list(ApprovalRequest.Status status);

    int count();

    /** 替换（用于状态流转后的持久化）。 */
    void update(ApprovalRequest request);
}