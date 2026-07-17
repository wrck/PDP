package com.pdp.shared.operation;

import com.pdp.shared.context.WorkspaceId;

import java.util.Optional;
import java.util.UUID;

/**
 * 高风险操作仓储端口（持久化层实现）。
 *
 * <p>由 persistence 基础设施实现（如 MySQL 适配器），持久化 {@link HighRiskOperationRecord}。
 * 业务模块和应用层通过 {@link HighRiskOperationPort} 调用，仓储端口仅用于持久化。
 *
 * <p><strong>乐观锁契约</strong>：{@link #save} 使用 revision 乐观锁，并发更新冲突时返回 false
 * 或抛出 {@link com.pdp.shared.concurrency.OptimisticLockException}。
 */
public interface HighRiskOperationRepository {

    /**
     * 保存或更新操作记录。
     *
     * <p>新增时 revision=1；更新时 MUST 校验 revision 一致后递增。
     *
     * @param record 操作记录
     * @return 保存后的记录（revision 已递增）
     * @throws com.pdp.shared.concurrency.OptimisticLockException revision 不匹配
     */
    HighRiskOperationRecord save(HighRiskOperationRecord record);

    /**
     * 按操作 ID 查询。
     *
     * @param operationId 操作 ID
     * @return 操作记录，不存在返回 empty
     */
    Optional<HighRiskOperationRecord> findById(UUID operationId);

    /**
     * 按预览 ID 查询关联的操作记录。
     *
     * <p>预览版本链存储在操作记录中，此方法遍历查找包含指定 previewId 的记录。
     *
     * @param previewId 预览 ID
     * @return 操作记录，不存在返回 empty
     */
    Optional<HighRiskOperationRecord> findByPreviewId(UUID previewId);

    /**
     * 按确认记录 ID 查询关联的操作记录。
     *
     * @param confirmationId 确认记录 ID
     * @return 操作记录，不存在返回 empty
     */
    Optional<HighRiskOperationRecord> findByConfirmationId(UUID confirmationId);

    /**
     * 按工作空间和操作类型查询操作记录（用于审计列表）。
     *
     * @param workspaceId   工作空间 ID
     * @param operationType 操作类型（null 表示所有类型）
     * @param offset        偏移量
     * @param limit         最大返回数
     * @return 操作记录列表（按 createdAt 降序）
     */
    java.util.List<HighRiskOperationRecord> findByWorkspace(
            WorkspaceId workspaceId,
            HighRiskOperationType operationType,
            int offset,
            int limit);

    /**
     * 统计工作空间内指定类型的操作记录数。
     *
     * @param workspaceId   工作空间 ID
     * @param operationType 操作类型（null 表示所有类型）
     * @return 记录数
     */
    long countByWorkspace(WorkspaceId workspaceId, HighRiskOperationType operationType);
}
