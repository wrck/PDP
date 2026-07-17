package com.pdp.workflow.domain;

import com.pdp.shared.context.WorkspaceId;
import com.pdp.shared.page.PageRequest;
import com.pdp.shared.page.PageResult;
import com.pdp.workflow.model.BusinessObjectRef;
import com.pdp.workflow.model.WorkflowInstanceId;
import com.pdp.workflow.model.WorkflowInstanceState;
import com.pdp.workflow.model.WorkflowResultEvent;

import java.util.List;
import java.util.Optional;

/**
 * 流程实例引用仓储端口（FR-174、ADR-0005 第 7 节）。
 *
 * <p>持久化 {@link WorkflowInstanceRefRecord} 聚合到 {@code workflow_instance_ref} 表，
 * 同时承载结果事件桥接（{@code workflow_result_event}）与迁移历史
 * （{@code workflow_migration_record}）的持久化职责，因为这三者共同构成实例生命周期。
 *
 * <p><strong>核心契约</strong>：
 * <ul>
 *   <li>幂等启动：相同 (workspace, businessObject, idempotencyKey) 重复保存时由唯一约束保证，
 *       应用层通过 {@link #findByIdempotencyKey} 预查实现幂等返回；</li>
 *   <li>版本固定：实例 definitionId/deploymentId 启动后不变，迁移通过迁移记录体现；</li>
 *   <li>状态投影：{@link #updateState} 使用乐观锁，返回 boolean 表达冲突；</li>
 *   <li>结果事件：终态时通过 {@link #saveResultEvent} 桥接到业务模块；</li>
 *   <li>查询返回 {@link Optional}，端口签名不抛业务异常。</li>
 * </ul>
 */
public interface WorkflowInstanceRefRepository {

    /**
     * 保存流程实例引用（插入或按 id 更新）。
     *
     * @param record 实例引用聚合
     */
    void save(WorkflowInstanceRefRecord record);

    /**
     * 按 ID 查询流程实例引用。
     *
     * @param instanceId 实例 ID
     * @return 实例引用聚合，不存在时返回 empty
     */
    Optional<WorkflowInstanceRefRecord> findById(WorkflowInstanceId instanceId);

    /**
     * 按业务对象查询关联的流程实例引用（业务模块回查编排状态）。
     *
     * @param businessObjectRef 业务对象引用
     * @return 关联实例引用聚合，不存在时返回 empty
     */
    Optional<WorkflowInstanceRefRecord> findByBusinessObject(BusinessObjectRef businessObjectRef);

    /**
     * 按引擎流程实例 ID 查询（引擎同步回写定位）。
     *
     * @param engineProcessInstanceId 引擎流程实例 ID
     * @return 实例引用聚合，不存在时返回 empty
     */
    Optional<WorkflowInstanceRefRecord> findByEngineProcessInstanceId(String engineProcessInstanceId);

    /**
     * 按幂等键查询实例引用（幂等启动预查）。
     *
     * @param workspaceId       工作空间边界
     * @param businessObjectRef 业务对象引用
     * @param idempotencyKey    幂等键
     * @return 已有实例引用聚合，不存在时返回 empty
     */
    Optional<WorkflowInstanceRefRecord> findByIdempotencyKey(
            WorkspaceId workspaceId,
            BusinessObjectRef businessObjectRef,
            String idempotencyKey);

    /**
     * 更新实例状态并递增 revision（乐观并发控制）。
     *
     * @param instanceId       实例 ID
     * @param targetState      目标状态
     * @param expectedRevision 期望版本（乐观锁）
     * @return 是否成功（true=成功，false=版本冲突或不存在）
     */
    boolean updateState(
            WorkflowInstanceId instanceId,
            WorkflowInstanceState targetState,
            int expectedRevision);

    /**
     * 更新实例当前活动节点键与最后同步时间（引擎同步回写）。
     *
     * @param instanceId           实例 ID
     * @param currentActivityKeys  当前活动节点键列表
     * @param expectedRevision     期望版本（乐观锁）
     * @return 是否成功（true=成功，false=版本冲突或不存在）
     */
    boolean updateActivityKeys(
            WorkflowInstanceId instanceId,
            List<String> currentActivityKeys,
            int expectedRevision);

    /**
     * 分页查询工作空间内有 incident 的实例（运维监控）。
     *
     * @param workspaceId 工作空间边界
     * @param pageRequest 分页请求
     * @return 分页结果
     */
    PageResult<WorkflowInstanceRefRecord> listInstancesWithIncidents(
            WorkspaceId workspaceId,
            PageRequest pageRequest);

    // ============================================================
    // 结果事件桥接（workflow_result_event 表）
    // ============================================================

    /**
     * 保存流程结果事件（终态时桥接到业务模块）。
     *
     * @param resultEvent 结果事件
     */
    void saveResultEvent(WorkflowResultEvent resultEvent);

    /**
     * 查询流程实例结果事件（终态时桥接到业务模块的结果）。
     *
     * @param instanceId 实例 ID
     * @return 结果事件，未终态时返回 empty
     */
    Optional<WorkflowResultEvent> findResultEvent(WorkflowInstanceId instanceId);

    // ============================================================
    // 迁移历史（workflow_migration_record 表）
    // ============================================================

    /**
     * 保存迁移历史记录（受控迁移审计）。
     *
     * @param record 迁移历史聚合
     */
    void saveMigrationRecord(WorkflowMigrationRecord record);

    /**
     * 查询实例迁移历史（按时间倒序，审计回查）。
     *
     * @param instanceId 实例 ID
     * @return 迁移记录列表（按 migratedAt 倒序），无迁移时返回空列表
     */
    List<WorkflowMigrationRecord> listMigrationHistory(WorkflowInstanceId instanceId);
}
