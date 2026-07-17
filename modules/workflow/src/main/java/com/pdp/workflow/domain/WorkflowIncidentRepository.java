package com.pdp.workflow.domain;

import com.pdp.shared.context.ActorRef;
import com.pdp.shared.context.WorkspaceId;
import com.pdp.workflow.model.WorkflowInstanceId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 工作流异常记录仓储端口（FR-174、ADR-0005 第 6 节）。
 *
 * <p>持久化 {@link WorkflowIncidentRecord} 聚合到 {@code workflow_incident} 表。
 * 供管理动作（重试、人工补偿）决策与审计回查。
 *
 * <p><strong>约定</strong>：
 * <ul>
 *   <li>查询返回 {@link Optional} 或 {@link List}，不存在时返回 empty/空列表；</li>
 *   <li>状态更新使用乐观锁，返回 boolean 表达冲突；</li>
 *   <li>{@link #markResolved} 与 {@link #markManualAction} 携带操作者与说明，满足审计要求；</li>
 *   <li>端口签名不抛业务异常。</li>
 * </ul>
 */
public interface WorkflowIncidentRepository {

    /**
     * 保存异常记录（插入或按 id 更新）。
     *
     * @param record 异常记录聚合
     */
    void save(WorkflowIncidentRecord record);

    /**
     * 按 ID 查询异常记录。
     *
     * @param id 异常记录 ID
     * @return 异常记录聚合，不存在时返回 empty
     */
    Optional<WorkflowIncidentRecord> findById(UUID id);

    /**
     * 按实例查询异常记录列表。
     *
     * @param instanceRefId   实例引用 ID
     * @param includeResolved 是否包含已解决记录
     * @return 异常记录列表（按 occurredAt 倒序）
     */
    List<WorkflowIncidentRecord> listByInstance(
            WorkflowInstanceId instanceRefId,
            boolean includeResolved);

    /**
     * 更新重试信息（attempts 递增、最后错误、下次重试时间）并递增 revision。
     *
     * @param id               异常记录 ID
     * @param attempts         新的重试次数
     * @param lastErrorCode    最后错误码（可选）
     * @param lastErrorMessage 最后错误消息（脱敏，可选）
     * @param lastErrorDigest  最后错误摘要（可选）
     * @param nextRetryAt      下次重试时间（可选）
     * @param expectedRevision 期望版本（乐观锁）
     * @return 是否成功（true=成功，false=版本冲突或不存在）
     */
    boolean updateRetry(
            UUID id,
            int attempts,
            String lastErrorCode,
            String lastErrorMessage,
            String lastErrorDigest,
            java.time.Instant nextRetryAt,
            int expectedRevision);

    /**
     * 标记异常为已解决（终态）。
     *
     * @param id               异常记录 ID
     * @param resolution       解决说明
     * @param resolvedBy       解决者
     * @param resolvedAt       解决时间
     * @param expectedRevision 期望版本（乐观锁）
     * @return 是否成功（true=成功，false=版本冲突或不存在）
     */
    boolean markResolved(
            UUID id,
            String resolution,
            ActorRef resolvedBy,
            java.time.Instant resolvedAt,
            int expectedRevision);

    /**
     * 标记异常为人工处理（MANUAL_ACTION）。
     *
     * @param id               异常记录 ID
     * @param expectedRevision 期望版本（乐观锁）
     * @return 是否成功（true=成功，false=版本冲突或不存在）
     */
    boolean markManualAction(UUID id, int expectedRevision);

    /**
     * 统计实例未解决的异常数量（用于诊断与运维监控）。
     *
     * @param instanceRefId 实例引用 ID
     * @return 未解决异常数量
     */
    int countUnresolvedByInstance(WorkflowInstanceId instanceRefId);

    /**
     * 按引擎作业 ID 查询异常记录（引擎同步定位）。
     *
     * @param engineJobId 引擎作业 ID
     * @return 异常记录聚合，不存在时返回 empty
     */
    Optional<WorkflowIncidentRecord> findByEngineJobId(String engineJobId);

    /**
     * 分页查询工作空间内未解决的异常（运维监控）。
     *
     * @param workspaceId 工作空间边界
     * @param pageRequest 分页请求
     * @return 分页结果
     */
    com.pdp.shared.page.PageResult<WorkflowIncidentRecord> listUnresolved(
            WorkspaceId workspaceId,
            com.pdp.shared.page.PageRequest pageRequest);
}
