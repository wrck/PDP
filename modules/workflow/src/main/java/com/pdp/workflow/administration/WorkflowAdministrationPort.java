package com.pdp.workflow.administration;

import com.pdp.shared.context.ActorRef;
import com.pdp.shared.context.WorkspaceId;
import com.pdp.shared.page.PageRequest;
import com.pdp.shared.page.PageResult;
import com.pdp.workflow.model.MigrationPlan;
import com.pdp.workflow.model.WorkflowAdminAction;
import com.pdp.workflow.model.WorkflowDefinitionId;
import com.pdp.workflow.model.WorkflowEngineException;
import com.pdp.workflow.model.WorkflowIncident;
import com.pdp.workflow.model.WorkflowInstanceId;
import com.pdp.workflow.model.WorkflowInstanceSummary;

import java.util.List;

/**
 * 平台流程实例受控管理端口（FR-174、FR-168、ADR-0005 第 6 节）。
 *
 * <p>负责流程实例迁移、暂停、恢复、终止与人工补偿管理。迁移 MUST 预览、分批、可暂停
 * 并保留证据；补偿操作 MUST NOT 重复形成审批结论或业务状态变化（ADR-0005 第 6 节）。
 *
 * <p><strong>核心契约（FR-174 / FR-168 / ADR-0005）</strong>：
 * <ol>
 *   <li>高风险动作治理：MIGRATE/TERMINATE/MANUAL_COMPENSATE 动作 MUST 通过
 *       {@link com.pdp.shared.operation.HighRiskOperationPort} 完成影响预览、明确确认与审计，
 *       {@link WorkflowAdminAction#confirmation()} MUST 非 null；</li>
 *   <li>迁移预览：MIGRATE 动作 MUST 先生成 {@link MigrationPlan}，包含活动节点映射、
 *       不可逆点与补偿计划；操作者基于预览确认后执行；</li>
 *   <li>乐观并发控制：动作 MUST 携带 {@code expectedRevision}，版本冲突时返回
 *       {@link WorkflowEngineException.Reason#ILLEGAL_STATE_TRANSITION}；</li>
 *   <li>幂等：相同 {@code (instanceId, action, idempotencyKey)} 重复执行返回已有结果；</li>
 *   <li>状态机：动作 MUST 遵循 {@link WorkflowInstanceSummary#state()} 状态机约束；</li>
 *   <li>补偿不重复：MANUAL_COMPENSATE MUST NOT 重复形成审批结论或业务状态变化，
 *       仅恢复流程编排状态；</li>
 *   <li>级联取消：TERMINATE MUST 级联取消运行中人工任务（通过 {@code WorkflowTaskPort}）。</li>
 * </ol>
 *
 * <p>端口实现位于 {@code workflow} 模块，内部完成 Flowable
 * {@code ProcessMigrationService}/{@code ManagementService} 调用、异常翻译与审计回写。
 */
public interface WorkflowAdministrationPort {

    /**
     * 预览流程实例迁移影响。
     *
     * <p>MIGRATE 动作 MUST 先调用此方法生成迁移计划。计划包含活动节点映射、
     * 不可逆点与补偿计划。操作者基于计划评估风险后通过
     * {@link com.pdp.shared.operation.HighRiskOperationPort#confirm} 确认。
     *
     * @param instanceId           实例 ID
     * @param targetDefinitionId   目标流程定义 ID
     * @param requestedBy          请求者
     * @return 迁移计划（含活动映射、不可逆点、补偿计划）
     * @throws WorkflowEngineException 实例不存在、目标定义不存在或版本不兼容
     */
    MigrationPlan previewMigration(
            WorkflowInstanceId instanceId,
            WorkflowDefinitionId targetDefinitionId,
            ActorRef requestedBy);

    /**
     * 执行受控管理动作。
     *
     * <p>动作 MUST 遵循状态机约束与高风险操作治理。MIGRATE/TERMINATE/MANUAL_COMPENSATE
     * MUST 携带 {@link WorkflowAdminAction#confirmation()}。
     *
     * @param action 管理动作命令
     * @return 执行后的实例摘要
     * @throws WorkflowEngineException 实例不存在、状态非法、版本冲突或确认记录无效
     */
    WorkflowInstanceSummary applyAction(WorkflowAdminAction action);

    /**
     * 查询实例的 incident 列表（运行诊断）。
     *
     * @param instanceId 实例 ID
     * @param includeResolved 是否包含已解决 incident
     * @return incident 列表
     * @throws WorkflowEngineException 实例不存在
     */
    List<WorkflowIncident> listIncidents(WorkflowInstanceId instanceId, boolean includeResolved);

    /**
     * 分页查询工作空间内有 incident 的实例（运维监控）。
     *
     * @param workspaceId 工作空间边界
     * @param pageRequest 分页请求
     * @return 分页结果
     */
    PageResult<WorkflowInstanceSummary> listInstancesWithIncidents(
            WorkspaceId workspaceId,
            PageRequest pageRequest);

    /**
     * 查询迁移历史（审计回查）。
     *
     * @param instanceId 实例 ID
     * @return 迁移记录列表（按时间倒序），无迁移时返回空列表
     */
    List<MigrationRecord> listMigrationHistory(WorkflowInstanceId instanceId);

    /**
     * 迁移历史记录值对象。
     *
     * @param migrationId      迁移 ID
     * @param instanceId       实例 ID
     * @param sourceDefinitionId 源定义 ID
     * @param targetDefinitionId 目标定义 ID
     * @param triggeredBy      触发者
     * @param migratedAt       迁移时间
     * @param batchSize        批次大小（0 表示不分批）
     * @param successful       是否成功
     * @param failureReason    失败原因（成功时为 null）
     */
    record MigrationRecord(
            String migrationId,
            WorkflowInstanceId instanceId,
            WorkflowDefinitionId sourceDefinitionId,
            WorkflowDefinitionId targetDefinitionId,
            ActorRef triggeredBy,
            java.time.Instant migratedAt,
            int batchSize,
            boolean successful,
            String failureReason) {
    }
}
