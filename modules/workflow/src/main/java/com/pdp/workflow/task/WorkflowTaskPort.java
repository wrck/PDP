package com.pdp.workflow.task;

import com.pdp.shared.context.ActorRef;
import com.pdp.shared.context.IdempotencyKey;
import com.pdp.shared.page.PageRequest;
import com.pdp.shared.page.PageResult;
import com.pdp.workflow.model.BusinessObjectRef;
import com.pdp.workflow.model.TaskCompletionRequest;
import com.pdp.workflow.model.WorkflowEngineException;
import com.pdp.workflow.model.WorkflowInstanceId;
import com.pdp.workflow.model.WorkflowTaskId;
import com.pdp.workflow.model.WorkflowTaskStatus;
import com.pdp.workflow.model.WorkflowTaskSummary;

import java.util.Optional;
import java.util.UUID;

/**
 * 平台人工任务端口（FR-174、FR-044、ADR-0005 第 6 节）。
 *
 * <p>负责用户任务查询、认领、办理、委派与回写；平台待办由该端口投影；
 * 候选人和办理权限由 PDP 实时计算并复核，办理前再次校验当前授权，
 * <strong>绝不依赖</strong>引擎内置身份数据作授权。
 *
 * <p><strong>核心契约（FR-174 / ADR-0005）</strong>：
 * <ol>
 *   <li>权限实时复核：每次查询与办理 MUST 复核 PDP 当前权限，权限撤销后办理 MUST 失败
 *       （{@link WorkflowEngineException.Reason#PERMISSION_REVOKED}）；</li>
 *   <li>非引擎身份：候选人投影 MUST 由 PDP 计算并写入投影表，不查询 Flowable
 *       {@code IdentityLink} 表；</li>
 *   <li>幂等办理：相同 {@code (taskId, action, idempotencyKey)} 重复办理 MUST 返回已有结果，
 *       不重复推进流程；</li>
 *   <li>办理回写：办理结果通过 {@link TaskCompletionRequest#variables()} 携带，
 *       仅限编排所需标识；业务结论由业务聚合决定，不在流程变量中持久化；</li>
 *   <li>跨空间隔离：任务查询 MUST 限制在工作空间边界内，禁止跨空间访问。</li>
 * </ol>
 *
 * <p>端口实现位于 {@code workflow} 模块，内部完成 Flowable {@code TaskService} 调用、
 * 候选人投影维护、权限复核委托（调用 {@code AuthorizationPort}）与异常翻译。
 */
public interface WorkflowTaskPort {

    /**
     * 查询任务详情。
     *
     * <p>查询时 MUST 复核当前用户对该任务关联业务对象的权限，无权时返回 empty（不泄露存在性）。
     *
     * @param taskId    任务 ID
     * @param requester 请求者（用于权限复核）
     * @return 任务摘要，不存在或无权时返回 empty
     */
    Optional<WorkflowTaskSummary> findById(WorkflowTaskId taskId, ActorRef requester);

    /**
     * 分页查询用户的待办任务。
     *
     * <p>候选人投影由 PDP 实时计算，不依赖引擎身份数据。结果 MUST 限制在工作空间边界内。
     *
     * @param workspaceId 工作空间
     * @param userId      用户 ID
     * @param status      状态过滤（可选，null 表示非终态）
     * @param pageRequest 分页请求
     * @return 分页结果
     */
    PageResult<WorkflowTaskSummary> listUserTasks(
            com.pdp.shared.context.WorkspaceId workspaceId,
            UUID userId,
            WorkflowTaskStatus status,
            PageRequest pageRequest);

    /**
     * 分页查询业务对象关联的任务（用于业务详情页展示流程进度）。
     *
     * @param businessObjectRef 业务对象引用
     * @param requester         请求者（用于权限复核）
     * @param pageRequest       分页请求
     * @return 分页结果
     */
    PageResult<WorkflowTaskSummary> listTasksByBusinessObject(
            BusinessObjectRef businessObjectRef,
            ActorRef requester,
            PageRequest pageRequest);

    /**
     * 认领任务（候选人转为办理人）。
     *
     * <p>认领前 MUST 复核当前用户是候选人且仍有权限。任务已被认领时返回冲突错误。
     *
     * @param taskId       任务 ID
     * @param userId       认领用户 ID
     * @param idempotencyKey 幂等键
     * @return 认领后的任务摘要
     * @throws WorkflowEngineException 任务不存在、已被认领或权限复核未通过
     */
    WorkflowTaskSummary claim(WorkflowTaskId taskId, UUID userId, IdempotencyKey idempotencyKey);

    /**
     * 委派任务（将办理权转交其他用户）。
     *
     * <p>委派前 MUST 复核当前办理人有权限且目标用户在工作空间内有相应权限。
     * 委派后任务状态为 {@link WorkflowTaskStatus#DELEGATED}，目标用户可继续办理。
     *
     * @param taskId       任务 ID
     * @param delegateTo   目标用户 ID
     * @param delegatedBy  委派者
     * @param idempotencyKey 幂等键
     * @return 委派后的任务摘要
     * @throws WorkflowEngineException 任务不存在、状态非法或权限复核未通过
     */
    WorkflowTaskSummary delegate(
            WorkflowTaskId taskId,
            UUID delegateTo,
            ActorRef delegatedBy,
            IdempotencyKey idempotencyKey);

    /**
     * 办理任务（完成任务并推进流程）。
     *
     * <p>办理前 MUST 实时复核 PDP 当前权限：
     * <ul>
     *   <li>办理者是当前 assignee 或候选人；</li>
     *   <li>办理者对关联业务对象仍有操作权限（权限撤销后办理 MUST 失败）；</li>
     *   <li>任务非终态。</li>
     * </ul>
     *
     * <p>幂等：相同 {@code (taskId, action, idempotencyKey)} 重复办理返回已有结果。
     *
     * @param request 办理请求
     * @return 办理后的任务摘要（{@link WorkflowTaskStatus#COMPLETED}）
     * @throws WorkflowEngineException 任务不存在、状态非法或权限复核未通过
     */
    WorkflowTaskSummary complete(TaskCompletionRequest request);

    /**
     * 取消任务（流程终止时级联取消运行中任务）。
     *
     * <p>仅由 {@code WorkflowAdministrationPort} 终止流程实例时调用，
     * 业务模块 MUST NOT 直接调用。
     *
     * @param taskId    任务 ID
     * @param reason    取消原因（审计）
     * @param cancelledBy 取消者
     * @return 取消后的任务摘要
     * @throws WorkflowEngineException 任务不存在或已终态
     */
    WorkflowTaskSummary cancel(WorkflowTaskId taskId, String reason, ActorRef cancelledBy);

    /**
     * 查询流程实例的所有任务（用于实例诊断）。
     *
     * @param instanceId 实例 ID
     * @param requester  请求者（用于权限复核）
     * @param pageRequest 分页请求
     * @return 分页结果
     */
    PageResult<WorkflowTaskSummary> listTasksByInstance(
            WorkflowInstanceId instanceId,
            ActorRef requester,
            PageRequest pageRequest);
}
