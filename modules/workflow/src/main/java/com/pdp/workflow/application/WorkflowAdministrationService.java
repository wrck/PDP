package com.pdp.workflow.application;

import com.pdp.shared.context.WorkspaceId;
import com.pdp.shared.error.ResourceNotFoundException;
import com.pdp.shared.page.PageRequest;
import com.pdp.shared.page.PageResult;
import com.pdp.workflow.administration.WorkflowAdministrationPort;
import com.pdp.workflow.administration.WorkflowAdministrationPort.MigrationRecord;
import com.pdp.workflow.domain.WorkflowIncidentRepository;
import com.pdp.workflow.domain.WorkflowIncidentRecord;
import com.pdp.workflow.domain.WorkflowInstanceRefRecord;
import com.pdp.workflow.domain.WorkflowInstanceRefRepository;
import com.pdp.workflow.model.MigrationPlan;
import com.pdp.workflow.model.WorkflowAdminAction;
import com.pdp.workflow.model.WorkflowDefinitionId;
import com.pdp.workflow.model.WorkflowEngineException;
import com.pdp.workflow.model.WorkflowIncident;
import com.pdp.workflow.model.WorkflowInstanceId;
import com.pdp.workflow.model.WorkflowInstanceSummary;
import com.pdp.workflow.model.WorkflowInstanceState;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 平台流程实例受控管理应用服务（FR-174、FR-168、ADR-0005 第 6 节）。
 *
 * <p>编排 {@link WorkflowAdministrationPort}（Flowable ProcessMigration /
 * ManagementService 操作）与 {@link WorkflowInstanceRefRepository} /
 * {@link WorkflowIncidentRepository}（PDP 实例引用与 incident 持久化），
 * 对外提供流程实例迁移、暂停、恢复、重试、终止、人工补偿、incident 诊断与
 * 迁移历史审计的粗粒度应用 API。
 *
 * <p><strong>核心契约（FR-174 / FR-168 / ADR-0005）</strong>：
 * <ol>
 *   <li><strong>工作空间边界隔离</strong>：所有动作 MUST 在指定工作空间内执行，
 *       跨工作空间访问统一返回 404，不泄露存在性；</li>
 *   <li><strong>状态机前置校验</strong>：管理动作 MUST 遵循
 *       {@link WorkflowInstanceState#canTransitionTo} 状态机约束，终态实例
 *       不可执行任何动作，非法转换返回 409
 *       {@link WorkflowEngineException.Reason#ILLEGAL_STATE_TRANSITION}；</li>
 *   <li><strong>乐观并发控制</strong>：动作 MUST 携带 {@code expectedRevision}，
 *       版本冲突时端口返回 409，由调用方基于最新摘要重试；</li>
 *   <li><strong>高风险操作治理（FR-168）</strong>：MIGRATE / TERMINATE /
 *       MANUAL_COMPENSATE 动作 MUST 携带 {@link WorkflowAdminAction#confirmation()}，
 *       由 {@code HighRiskOperationPort} 在控制器层完成影响预览与确认；</li>
 *   <li><strong>迁移预览、分批、可暂停、可回退</strong>（ADR-0005 § 5）：
 *       MIGRATE 动作 MUST 先调用 {@link #previewMigration} 生成
 *       {@link MigrationPlan}，操作者基于预览确认后通过 {@link #applyAction}
 *       执行；</li>
 *   <li><strong>补偿不重复</strong>：MANUAL_COMPENSATE MUST NOT 重复形成审批结论
 *       或业务状态变化，仅恢复流程编排状态；</li>
 *   <li><strong>幂等</strong>：相同 {@code (instanceId, action, idempotencyKey)}
 *       重复执行返回已有结果，由端口实现内部保证；</li>
 *   <li><strong>禁止 XA</strong>：端口操作使用 {@code workflowEngine} 事务管理器，
 *       实例引用 / incident 持久化使用 {@code pdpPrimary} 事务管理器，二者不在同一事务；
 *       重试时通过 {@code idempotencyKey} 保证安全。</li>
 * </ol>
 *
 * <p><strong>异常翻译</strong>：
 * <ul>
 *   <li>实例不存在 / 跨工作空间 → {@link ResourceNotFoundException}（HTTP 404）；</li>
 *   <li>状态非法 / 版本冲突 / 迁移计划无效 → {@link WorkflowEngineException}（HTTP 409/422）；</li>
 *   <li>引擎层故障 → {@link WorkflowEngineException}（HTTP 503/500）。</li>
 * </ul>
 *
 * <p><strong>分层职责</strong>：本服务负责工作空间边界隔离与状态机前置校验，
 * 引擎调用、异常翻译、迁移计划执行、审计回写由 {@link WorkflowAdministrationPort}
 * 实现完成。权限复核（操作者是否可执行管理动作）由控制器层
 * {@code AuthorizationPort} 完成，本服务不直接依赖 identity 模块。
 */
@Service
public class WorkflowAdministrationService {

    private final WorkflowAdministrationPort administrationPort;
    private final WorkflowInstanceRefRepository instanceRepository;
    private final WorkflowIncidentRepository incidentRepository;

    public WorkflowAdministrationService(
            WorkflowAdministrationPort administrationPort,
            WorkflowInstanceRefRepository instanceRepository,
            WorkflowIncidentRepository incidentRepository) {
        this.administrationPort = Objects.requireNonNull(administrationPort);
        this.instanceRepository = Objects.requireNonNull(instanceRepository);
        this.incidentRepository = Objects.requireNonNull(incidentRepository);
    }

    // ============================================================
    // 迁移预览
    // ============================================================

    /**
     * 预览流程实例迁移影响。
     *
     * <p>MIGRATE 动作 MUST 先调用此方法生成 {@link MigrationPlan}。计划包含活动节点映射、
     * 不可逆点与补偿计划。操作者基于计划评估风险后通过 {@code HighRiskOperationPort}
     * 确认，再调用 {@link #applyAction} 执行迁移。
     *
     * <p><strong>前置校验</strong>：
     * <ul>
     *   <li>实例 MUST 存在且属于指定工作空间（否则 404）；</li>
     *   <li>实例 MUST 非 {@link WorkflowInstanceState#COMPLETED} /
     *       {@link WorkflowInstanceState#TERMINATED} 终态（否则 409）；</li>
     *   <li>目标定义 MUST 与源定义不同（否则抛 {@link WorkflowEngineException}
     *       {@code MIGRATION_PLAN_INVALID}）。</li>
     * </ul>
     *
     * @param workspaceId        工作空间边界
     * @param instanceId         实例 ID
     * @param targetDefinitionId 目标流程定义 ID
     * @param requestedBy        请求者
     * @return 迁移计划（含活动映射、不可逆点、补偿计划）
     * @throws ResourceNotFoundException  实例不存在或跨工作空间访问
     * @throws WorkflowEngineException    实例终态、目标定义不存在或版本不兼容
     */
    public MigrationPlan previewMigration(
            WorkspaceId workspaceId,
            WorkflowInstanceId instanceId,
            WorkflowDefinitionId targetDefinitionId,
            com.pdp.shared.context.ActorRef requestedBy) {
        Objects.requireNonNull(workspaceId, "workspaceId 不能为 null");
        Objects.requireNonNull(instanceId, "instanceId 不能为 null");
        Objects.requireNonNull(targetDefinitionId, "targetDefinitionId 不能为 null");
        Objects.requireNonNull(requestedBy, "requestedBy 不能为 null");

        WorkflowInstanceRefRecord record = loadWithinWorkspace(workspaceId, instanceId);
        ensureNonTerminalForMigration(record.state(), instanceId);
        if (record.definitionId().equals(targetDefinitionId)) {
            throw new WorkflowEngineException(
                    WorkflowEngineException.Reason.MIGRATION_PLAN_INVALID,
                    "目标定义与源定义相同，迁移无意义：instanceId=" + instanceId
                            + ", definitionId=" + targetDefinitionId);
        }
        return administrationPort.previewMigration(instanceId, targetDefinitionId, requestedBy);
    }

    // ============================================================
    // 执行受控管理动作
    // ============================================================

    /**
     * 执行受控管理动作。
     *
     * <p>动作 MUST 遵循状态机约束与高风险操作治理。MIGRATE / TERMINATE /
     * MANUAL_COMPENSATE MUST 携带 {@link WorkflowAdminAction#confirmation()}。
     *
     * <p><strong>前置校验</strong>：
     * <ul>
     *   <li>实例 MUST 存在且属于指定工作空间（否则 404）；</li>
     *   <li>动作 MUST 与实例当前状态兼容（由 {@link #ensureActionAllowed} 校验）；</li>
     *   <li>{@code expectedRevision} MUST 与实例当前 revision 一致（端口实现乐观锁）；</li>
     *   <li>高风险动作 MUST 携带 confirmation（由 {@link WorkflowAdminAction} 构造器保证）。</li>
     * </ul>
     *
     * <p><strong>补偿不重复</strong>：MANUAL_COMPENSATE 仅恢复流程编排状态，
     * MUST NOT 重复形成审批结论或业务状态变化（ADR-0005 § 6、§ 7）。
     *
     * @param workspaceId 工作空间边界
     * @param action      管理动作命令
     * @return 执行后的实例摘要
     * @throws ResourceNotFoundException 实例不存在或跨工作空间访问
     * @throws WorkflowEngineException   状态非法、版本冲突或确认记录无效
     */
    public WorkflowInstanceSummary applyAction(
            WorkspaceId workspaceId, WorkflowAdminAction action) {
        Objects.requireNonNull(workspaceId, "workspaceId 不能为 null");
        Objects.requireNonNull(action, "action 不能为 null");

        WorkflowInstanceRefRecord record = loadWithinWorkspace(workspaceId, action.instanceId());
        ensureActionAllowed(action.action(), record.state(), action.instanceId());

        WorkflowInstanceSummary summary = administrationPort.applyAction(action);

        // 端口执行后再次校验工作空间边界（防止端口实现返回跨工作空间数据）
        enforceWorkspaceBoundary(workspaceId, summary.workspaceId(), action.instanceId().value());
        return summary;
    }

    // ============================================================
    // Incident 诊断查询
    // ============================================================

    /**
     * 查询实例的 incident 列表（运行诊断）。
     *
     * <p>从 {@link WorkflowIncidentRepository} 加载 PDP 侧 incident 投影，
     * 转换为公开 {@link WorkflowIncident} 读模型。错误消息已脱敏。
     *
     * @param workspaceId      工作空间边界
     * @param instanceId       实例 ID
     * @param includeResolved  是否包含已解决 incident
     * @return incident 列表（按 occurredAt 倒序）
     * @throws ResourceNotFoundException 实例不存在或跨工作空间访问
     */
    public List<WorkflowIncident> listIncidents(
            WorkspaceId workspaceId, WorkflowInstanceId instanceId, boolean includeResolved) {
        Objects.requireNonNull(workspaceId, "workspaceId 不能为 null");
        Objects.requireNonNull(instanceId, "instanceId 不能为 null");

        loadWithinWorkspace(workspaceId, instanceId);

        return incidentRepository.listByInstance(instanceId, includeResolved).stream()
                .map(WorkflowAdministrationService::toIncidentReadModel)
                .toList();
    }

    // ============================================================
    // 工作空间运维监控
    // ============================================================

    /**
     * 分页查询工作空间内有 incident 的实例（运维监控）。
     *
     * <p>委托 {@link WorkflowInstanceRefRepository#listInstancesWithIncidents}
     * 直接按工作空间边界过滤，并合成 incidentCount。
     *
     * @param workspaceId 工作空间边界
     * @param pageRequest 分页请求
     * @return 分页结果
     */
    public PageResult<WorkflowInstanceSummary> listInstancesWithIncidents(
            WorkspaceId workspaceId, PageRequest pageRequest) {
        Objects.requireNonNull(workspaceId, "workspaceId 不能为 null");
        Objects.requireNonNull(pageRequest, "pageRequest 不能为 null");

        PageResult<WorkflowInstanceRefRecord> records =
                instanceRepository.listInstancesWithIncidents(workspaceId, pageRequest);
        List<WorkflowInstanceSummary> summaries = records.data().stream()
                .map(this::toSummary)
                .toList();
        return new PageResult<>(
                summaries,
                records.nextCursor(),
                records.hasMore(),
                records.total().orElse(null));
    }

    // ============================================================
    // 迁移历史审计
    // ============================================================

    /**
     * 查询实例迁移历史（审计回查）。
     *
     * <p>委托 {@link WorkflowAdministrationPort#listMigrationHistory} 返回端口侧
     * 迁移记录；端口实现内部从 {@code workflow_migration_record} 表读取。
     *
     * @param workspaceId 工作空间边界
     * @param instanceId  实例 ID
     * @return 迁移记录列表（按时间倒序），无迁移时返回空列表
     * @throws ResourceNotFoundException 实例不存在或跨工作空间访问
     */
    public List<MigrationRecord> listMigrationHistory(
            WorkspaceId workspaceId, WorkflowInstanceId instanceId) {
        Objects.requireNonNull(workspaceId, "workspaceId 不能为 null");
        Objects.requireNonNull(instanceId, "instanceId 不能为 null");

        loadWithinWorkspace(workspaceId, instanceId);
        return administrationPort.listMigrationHistory(instanceId);
    }

    // ============================================================
    // 私有辅助方法
    // ============================================================

    /**
     * 按 ID 加载实例引用并校验工作空间边界。
     *
     * @param workspaceId 工作空间边界
     * @param instanceId  实例 ID
     * @return 实例引用聚合
     * @throws ResourceNotFoundException 不存在或跨工作空间访问（统一 404 语义）
     */
    private WorkflowInstanceRefRecord loadWithinWorkspace(
            WorkspaceId workspaceId, WorkflowInstanceId instanceId) {
        WorkflowInstanceRefRecord record = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkflowInstance", instanceId.value()));
        enforceWorkspaceBoundary(workspaceId, record.workspaceId(), instanceId.value());
        return record;
    }

    /**
     * 校验工作空间边界，不匹配时抛 404（不泄露存在性）。
     *
     * @param expected 期望工作空间
     * @param actual   实际工作空间
     * @param objectId 对象 ID（用于异常上下文）
     */
    private void enforceWorkspaceBoundary(
            WorkspaceId expected, WorkspaceId actual, UUID objectId) {
        if (!expected.equals(actual)) {
            throw new ResourceNotFoundException("WorkflowInstance", objectId);
        }
    }

    /**
     * 校验实例非终态（迁移前置条件）。
     *
     * <p>迁移不允许对 {@link WorkflowInstanceState#COMPLETED} /
     * {@link WorkflowInstanceState#TERMINATED} 终态实例执行。
     *
     * @param state      实例当前状态
     * @param instanceId 实例 ID（用于异常上下文）
     * @throws WorkflowEngineException 实例为终态时抛 ILLEGAL_STATE_TRANSITION
     */
    private void ensureNonTerminalForMigration(WorkflowInstanceState state, WorkflowInstanceId instanceId) {
        if (state.isTerminal()) {
            throw new WorkflowEngineException(
                    WorkflowEngineException.Reason.ILLEGAL_STATE_TRANSITION,
                    "终态实例不可迁移：instanceId=" + instanceId + ", state=" + state);
        }
    }

    /**
     * 校验动作与实例当前状态兼容。
     *
     * <p>状态机规则：
     * <ul>
     *   <li>PAUSE：仅 ACTIVE / INCIDENT 可暂停；SUSPENDED / 终态拒绝；</li>
     *   <li>RESUME：仅 SUSPENDED 可恢复；其余拒绝；</li>
     *   <li>RETRY：仅 INCIDENT 可重试；其余拒绝；</li>
     *   <li>MIGRATE：非终态可迁移；终态拒绝（见 {@link #ensureNonTerminalForMigration}）；</li>
     *   <li>TERMINATE：非终态可终止；终态拒绝；</li>
     *   <li>MANUAL_COMPENSATE：仅 INCIDENT 可人工补偿；其余拒绝。</li>
     * </ul>
     *
     * @param action     动作类型
     * @param state      实例当前状态
     * @param instanceId 实例 ID（用于异常上下文）
     * @throws WorkflowEngineException 状态非法时抛 ILLEGAL_STATE_TRANSITION
     */
    private void ensureActionAllowed(
            WorkflowAdminAction.Action action, WorkflowInstanceState state, WorkflowInstanceId instanceId) {
        boolean allowed = switch (action) {
            case PAUSE -> state == WorkflowInstanceState.ACTIVE
                    || state == WorkflowInstanceState.INCIDENT;
            case RESUME -> state == WorkflowInstanceState.SUSPENDED;
            case RETRY -> state == WorkflowInstanceState.INCIDENT;
            case MIGRATE -> !state.isTerminal();
            case TERMINATE -> !state.isTerminal();
            case MANUAL_COMPENSATE -> state == WorkflowInstanceState.INCIDENT;
        };
        if (!allowed) {
            throw new WorkflowEngineException(
                    WorkflowEngineException.Reason.ILLEGAL_STATE_TRANSITION,
                    "动作 " + action + " 在状态 " + state + " 下不允许：instanceId=" + instanceId);
        }
    }

    /**
     * 将持久化聚合投影为公开读模型摘要。
     *
     * <p>从 PDP 实例引用记录与 incident 仓储合成公开摘要。
     * incidentCount 从 incident 仓储实时查询未解决数量。
     *
     * @param record 实例引用聚合
     * @return 实例摘要
     */
    private WorkflowInstanceSummary toSummary(WorkflowInstanceRefRecord record) {
        int incidentCount = incidentRepository.countUnresolvedByInstance(record.id());
        return new WorkflowInstanceSummary(
                record.id(),
                record.definitionId(),
                record.workspaceId(),
                record.businessObjectRef(),
                record.state(),
                record.currentActivityKeys(),
                incidentCount,
                record.revision(),
                record.startedAt(),
                record.endedAt());
    }

    /**
     * 将持久化 incident 记录投影为公开读模型。
     *
     * <p>错误消息已由 incident 同步监听器（{@code AsyncExecutorIncidentListener}）
     * 脱敏处理，直接传递。{@code resolvedAt} 仅在 {@link WorkflowIncidentRecord#isResolved()}
     * 时非空。
     *
     * @param record incident 持久化记录
     * @return incident 公开读模型
     */
    private static WorkflowIncident toIncidentReadModel(WorkflowIncidentRecord record) {
        return new WorkflowIncident(
                record.id().toString(),
                record.instanceRefId(),
                record.activityKey(),
                record.incidentType(),
                record.lastErrorMessageOptional().orElse(""),
                record.occurredAt(),
                record.resolvedAtOptional().orElse(null),
                record.attempts());
    }
}
