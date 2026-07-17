package com.pdp.workflow.application;

import com.pdp.shared.context.ActorRef;
import com.pdp.shared.context.IdempotencyKey;
import com.pdp.shared.context.WorkspaceId;
import com.pdp.shared.error.ResourceNotFoundException;
import com.pdp.shared.page.PageRequest;
import com.pdp.shared.page.PageResult;
import com.pdp.workflow.domain.WorkflowInstanceRefRepository;
import com.pdp.workflow.model.BusinessObjectRef;
import com.pdp.workflow.model.TaskCompletionRequest;
import com.pdp.workflow.model.WorkflowEngineException;
import com.pdp.workflow.model.WorkflowInstanceId;
import com.pdp.workflow.model.WorkflowTaskId;
import com.pdp.workflow.model.WorkflowTaskStatus;
import com.pdp.workflow.model.WorkflowTaskSummary;
import com.pdp.workflow.task.WorkflowTaskPort;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * 平台人工任务应用服务（FR-174、FR-044、ADR-0005 第 6 节）。
 *
 * <p>编排 {@link WorkflowTaskPort}（Flowable TaskService + 候选人投影 + 权限复核委托），
 * 对外提供任务查询、认领、委派、办理与取消的粗粒度应用 API。
 *
 * <p><strong>核心契约（FR-174 / ADR-0005）</strong>：
 * <ol>
 *   <li>权限实时复核：每次查询与办理 MUST 复核 PDP 当前权限。端口实现内部调用
 *       {@code AuthorizationPort} 完成权限复核，权限撤销后办理 MUST 失败
 *       （{@link WorkflowEngineException.Reason#PERMISSION_REVOKED}）。
 *       本服务在委托端口前做工作空间边界与终态前置校验；</li>
 *   <li>非引擎身份：候选人投影由 PDP 计算并写入投影表，不查询 Flowable
 *       {@code IdentityLink} 表（端口实现保证）；</li>
 *   <li>幂等办理：相同 {@code (taskId, action, idempotencyKey)} 重复办理 MUST 返回已有结果，
 *       不重复推进流程（端口实现保证）；</li>
 *   <li>办理回写：办理结果通过 {@link TaskCompletionRequest#variables()} 携带，
 *       仅限编排所需标识；业务结论由业务聚合决定；</li>
 *   <li>跨空间隔离：任务查询 MUST 限制在工作空间边界内，禁止跨空间访问（本服务强制）。</li>
 * </ol>
 *
 * <p><strong>分层职责</strong>：
 * <ul>
 *   <li>本服务（application）：工作空间边界隔离、终态前置校验、实例归属校验；</li>
 *   <li>端口实现（infrastructure）：Flowable TaskService 调用、候选人投影维护、
 *       AuthorizationPort 权限复核委托、异常翻译。</li>
 * </ul>
 *
 * <p><strong>异常翻译</strong>：
 * <ul>
 *   <li>资源不存在/跨工作空间 → {@link ResourceNotFoundException}（HTTP 404）；</li>
 *   <li>权限撤销 → {@link WorkflowEngineException}（{@code Reason.PERMISSION_REVOKED}，HTTP 403）；</li>
 *   <li>状态非法/已被认领 → {@link WorkflowEngineException}（端口抛出，服务不捕获）。</li>
 * </ul>
 */
@Service
public class WorkflowTaskService {

    private final WorkflowTaskPort taskPort;
    private final WorkflowInstanceRefRepository instanceRepository;

    public WorkflowTaskService(
            WorkflowTaskPort taskPort,
            WorkflowInstanceRefRepository instanceRepository) {
        this.taskPort = taskPort;
        this.instanceRepository = instanceRepository;
    }

    // ============================================================
    // 查询（工作空间边界隔离 + 权限复核由端口处理）
    // ============================================================

    /**
     * 查询任务详情。
     *
     * <p>委托端口查询并复核权限（端口内部调用 AuthorizationPort）。
     * 跨工作空间访问返回 404，不泄露存在性。
     *
     * @param workspaceId 工作空间边界
     * @param taskId      任务 ID
     * @param requester   请求者（用于权限复核）
     * @return 任务摘要
     * @throws ResourceNotFoundException 不存在、无权或跨工作空间访问
     */
    public WorkflowTaskSummary getById(WorkspaceId workspaceId, WorkflowTaskId taskId, ActorRef requester) {
        WorkflowTaskSummary summary = taskPort.findById(taskId, requester)
                .orElseThrow(() -> new ResourceNotFoundException("WorkflowTask", taskId.value()));
        enforceWorkspaceBoundary(workspaceId, summary.workspaceId(), taskId.value());
        return summary;
    }

    /**
     * 分页查询用户的待办任务。
     *
     * <p>候选人投影由 PDP 实时计算（端口实现保证）。结果限制在工作空间边界内。
     *
     * @param workspaceId 工作空间
     * @param userId      用户 ID
     * @param status      状态过滤（可选，null 表示非终态）
     * @param pageRequest 分页请求
     * @return 分页结果
     */
    public PageResult<WorkflowTaskSummary> listUserTasks(
            WorkspaceId workspaceId,
            UUID userId,
            WorkflowTaskStatus status,
            PageRequest pageRequest) {
        return taskPort.listUserTasks(workspaceId, userId, status, pageRequest);
    }

    /**
     * 分页查询业务对象关联的任务。
     *
     * <p>用于业务详情页展示流程进度。委托端口查询并复核权限。
     * 工作空间边界由 businessObjectRef 携带，本服务校验一致性。
     *
     * @param workspaceId      工作空间边界
     * @param businessObjectRef 业务对象引用
     * @param requester         请求者
     * @param pageRequest       分页请求
     * @return 分页结果
     * @throws ResourceNotFoundException 跨工作空间访问
     */
    public PageResult<WorkflowTaskSummary> listTasksByBusinessObject(
            WorkspaceId workspaceId,
            BusinessObjectRef businessObjectRef,
            ActorRef requester,
            PageRequest pageRequest) {
        enforceWorkspaceBoundary(workspaceId, businessObjectRef.workspaceId(), businessObjectRef.objectId());
        return taskPort.listTasksByBusinessObject(businessObjectRef, requester, pageRequest);
    }

    /**
     * 查询流程实例的所有任务（实例诊断）。
     *
     * <p>先校验实例存在且在工作空间内，再委托端口查询任务。
     *
     * @param workspaceId 工作空间边界
     * @param instanceId  实例 ID
     * @param requester   请求者
     * @param pageRequest 分页请求
     * @return 分页结果
     * @throws ResourceNotFoundException 实例不存在或跨工作空间访问
     */
    public PageResult<WorkflowTaskSummary> listTasksByInstance(
            WorkspaceId workspaceId,
            WorkflowInstanceId instanceId,
            ActorRef requester,
            PageRequest pageRequest) {
        // 校验实例存在且在工作空间内
        instanceRepository.findById(instanceId)
                .filter(record -> record.workspaceId().equals(workspaceId))
                .orElseThrow(() -> new ResourceNotFoundException("WorkflowInstance", instanceId.value()));
        return taskPort.listTasksByInstance(instanceId, requester, pageRequest);
    }

    // ============================================================
    // 认领（候选人转办理人）
    // ============================================================

    /**
     * 认领任务。
     *
     * <p>前置校验：任务存在、工作空间匹配、非终态。
     * 认领前 MUST 复核当前用户是候选人且仍有权限（端口实现保证）。
     *
     * @param workspaceId   工作空间边界
     * @param taskId        任务 ID
     * @param userId        认领用户 ID
     * @param idempotencyKey 幂等键
     * @return 认领后的任务摘要
     * @throws ResourceNotFoundException  任务不存在或跨工作空间访问
     * @throws WorkflowEngineException     已被认领或权限复核未通过
     */
    public WorkflowTaskSummary claim(
            WorkspaceId workspaceId, WorkflowTaskId taskId, UUID userId, IdempotencyKey idempotencyKey) {
        // 前置校验：任务存在、工作空间匹配、非终态
        WorkflowTaskSummary existing = loadNonTerminalWithinWorkspace(workspaceId, taskId, userId);

        return taskPort.claim(taskId, userId, idempotencyKey);
    }

    // ============================================================
    // 委派（转交办理权）
    // ============================================================

    /**
     * 委派任务。
     *
     * <p>前置校验：任务存在、工作空间匹配、非终态。
     * 委派前 MUST 复核当前办理人有权限且目标用户在工作空间内有相应权限（端口实现保证）。
     *
     * @param workspaceId   工作空间边界
     * @param taskId        任务 ID
     * @param delegateTo    目标用户 ID
     * @param delegatedBy   委派者
     * @param idempotencyKey 幂等键
     * @return 委派后的任务摘要
     * @throws ResourceNotFoundException  任务不存在或跨工作空间访问
     * @throws WorkflowEngineException     状态非法或权限复核未通过
     */
    public WorkflowTaskSummary delegate(
            WorkspaceId workspaceId,
            WorkflowTaskId taskId,
            UUID delegateTo,
            ActorRef delegatedBy,
            IdempotencyKey idempotencyKey) {
        // 前置校验：任务存在、工作空间匹配、非终态
        loadNonTerminalWithinWorkspaceByActor(workspaceId, taskId, delegatedBy);

        return taskPort.delegate(taskId, delegateTo, delegatedBy, idempotencyKey);
    }

    // ============================================================
    // 办理（完成任务并推进流程）
    // ============================================================

    /**
     * 办理任务。
     *
     * <p>前置校验：任务存在、工作空间匹配、非终态。
     * 办理前 MUST 实时复核 PDP 当前权限（端口实现保证）：
     * <ul>
     *   <li>办理者是当前 assignee 或候选人；</li>
     *   <li>办理者对关联业务对象仍有操作权限（权限撤销后办理 MUST 失败）；</li>
     *   <li>任务非终态。</li>
     * </ul>
     *
     * <p>幂等：相同 {@code (taskId, action, idempotencyKey)} 重复办理返回已有结果。
     *
     * @param workspaceId 工作空间边界
     * @param request     办理请求
     * @return 办理后的任务摘要（COMPLETED）
     * @throws ResourceNotFoundException  任务不存在或跨工作空间访问
     * @throws WorkflowEngineException     状态非法或权限复核未通过（PERMISSION_REVOKED）
     */
    public WorkflowTaskSummary complete(WorkspaceId workspaceId, TaskCompletionRequest request) {
        // 前置校验：任务存在、工作空间匹配、非终态
        loadNonTerminalWithinWorkspaceByActor(workspaceId, request.taskId(), request.completedBy());

        // 委托端口办理（端口内部实时复核权限）
        return taskPort.complete(request);
    }

    // ============================================================
    // 私有辅助方法
    // ============================================================

    /**
     * 加载非终态任务并校验工作空间边界（按 userId 作为请求者）。
     *
     * @param workspaceId 工作空间边界
     * @param taskId      任务 ID
     * @param userId      请求用户 ID（用于端口权限复核）
     * @return 非终态任务摘要
     * @throws ResourceNotFoundException  不存在、无权或跨工作空间访问
     * @throws WorkflowEngineException     任务已终态
     */
    private WorkflowTaskSummary loadNonTerminalWithinWorkspace(
            WorkspaceId workspaceId, WorkflowTaskId taskId, UUID userId) {
        ActorRef requester = ActorRef.user(userId, "");
        WorkflowTaskSummary summary = taskPort.findById(taskId, requester)
                .orElseThrow(() -> new ResourceNotFoundException("WorkflowTask", taskId.value()));
        enforceWorkspaceBoundary(workspaceId, summary.workspaceId(), taskId.value());
        ensureNonTerminal(summary);
        return summary;
    }

    /**
     * 加载非终态任务并校验工作空间边界（按 ActorRef 作为请求者）。
     *
     * @param workspaceId 工作空间边界
     * @param taskId      任务 ID
     * @param actor       请求者
     * @return 非终态任务摘要
     * @throws ResourceNotFoundException  不存在、无权或跨工作空间访问
     * @throws WorkflowEngineException     任务已终态
     */
    private WorkflowTaskSummary loadNonTerminalWithinWorkspaceByActor(
            WorkspaceId workspaceId, WorkflowTaskId taskId, ActorRef actor) {
        WorkflowTaskSummary summary = taskPort.findById(taskId, actor)
                .orElseThrow(() -> new ResourceNotFoundException("WorkflowTask", taskId.value()));
        enforceWorkspaceBoundary(workspaceId, summary.workspaceId(), taskId.value());
        ensureNonTerminal(summary);
        return summary;
    }

    /**
     * 校验任务非终态，终态时抛引擎异常。
     *
     * @param summary 任务摘要
     * @throws WorkflowEngineException 任务已终态
     */
    private void ensureNonTerminal(WorkflowTaskSummary summary) {
        if (summary.isTerminal()) {
            throw new WorkflowEngineException(
                    WorkflowEngineException.Reason.ILLEGAL_STATE_TRANSITION,
                    "任务 " + summary.id() + " 已终态（" + summary.status() + "），不可操作");
        }
    }

    /**
     * 校验工作空间边界，不匹配时抛 404（不泄露存在性）。
     *
     * @param expected 期望工作空间
     * @param actual   实际工作空间
     * @param objectId 对象 ID（用于异常上下文）
     */
    private void enforceWorkspaceBoundary(WorkspaceId expected, WorkspaceId actual, UUID objectId) {
        if (!expected.equals(actual)) {
            throw new ResourceNotFoundException("WorkflowTask", objectId);
        }
    }
}
