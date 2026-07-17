package com.pdp.workflow.application;

import com.pdp.shared.context.IdempotencyKey;
import com.pdp.shared.context.WorkspaceId;
import com.pdp.shared.error.ResourceNotFoundException;
import com.pdp.workflow.domain.WorkflowIncidentRepository;
import com.pdp.workflow.domain.WorkflowInstanceRefRecord;
import com.pdp.workflow.domain.WorkflowInstanceRefRepository;
import com.pdp.workflow.model.BusinessObjectRef;
import com.pdp.workflow.model.MessageCorrelation;
import com.pdp.workflow.model.WorkflowEngineException;
import com.pdp.workflow.model.WorkflowInstanceId;
import com.pdp.workflow.model.WorkflowInstanceSummary;
import com.pdp.workflow.model.WorkflowResultEvent;
import com.pdp.workflow.model.WorkflowStartRequest;
import com.pdp.workflow.runtime.WorkflowRuntimePort;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 平台流程运行时应用服务（FR-174、ADR-0005 第 6-8 节）。
 *
 * <p>编排 {@link WorkflowRuntimePort}（Flowable 引擎操作）与
 * {@link WorkflowInstanceRefRepository}（PDP 实例引用持久化），对外提供
 * 流程实例启动、推进、消息关联、查询与结果事件桥接的粗粒度应用 API。
 *
 * <p><strong>核心契约（FR-174 / ADR-0005）</strong>：
 * <ol>
 *   <li>版本固定：实例启动时固定为请求中 {@link WorkflowStartRequest#definitionId()}
 *       指向的定义版本，新版本部署不影响运行中实例（由 Flowable 引擎保证）；</li>
 *   <li>幂等启动：相同 {@code (workspace, businessObject, idempotencyKey)} 重复启动
 *       MUST 返回已有实例，不创建重复实例；预查通过
 *       {@link WorkflowInstanceRefRepository#findByIdempotencyKey} 实现；</li>
 *   <li>消息关联幂等：相同 {@code (messageName, businessObject, idempotencyKey)}
 *       重复投递不重复推进流程；实例终态时消息被忽略并返回当前摘要；</li>
 *   <li>结果桥接：流程完成或异常终止时通过 {@link WorkflowResultEvent} 桥接到业务模块，
 *       业务聚合决定最终业务状态变化；结果事件由引擎适配器写入后由本服务查询返回；</li>
 *   <li>禁止 XA：编排消费与流程启动不在同一事务（ADR-0005 第 8 节），
 *       {@link WorkflowRuntimePort} 操作使用 {@code workflowEngine} 事务管理器，
 *       {@link WorkflowInstanceRefRepository} 操作使用 {@code pdpPrimary} 事务管理器；</li>
 *   <li>工作空间边界：所有查询 MUST 在指定工作空间内，跨工作空间访问返回 404 语义。</li>
 * </ol>
 *
 * <p><strong>事务边界与重试安全</strong>：
 * <ul>
 *   <li>启动流程：① 幂等预查（读 pdpPrimary）→ ② 委托端口启动 Flowable（写 workflowEngine）
 *       → ③ 端口实现持久化实例引用（写 pdpPrimary）。②③ 不在同一事务，重试时①命中幂等返回；</li>
 *   <li>消息关联/推进：① 加载实例并校验工作空间与终态（读 pdpPrimary）
 *       → ② 委托端口操作 Flowable（写 workflowEngine）。读写在独立事务中完成。</li>
 * </ul>
 *
 * <p><strong>异常翻译</strong>：
 * <ul>
 *   <li>资源不存在/跨工作空间 → {@link ResourceNotFoundException}（HTTP 404）；</li>
 *   <li>引擎层故障 → {@link WorkflowEngineException}（由端口抛出，服务不捕获）。</li>
 * </ul>
 */
@Service
public class WorkflowRuntimeService {

    private final WorkflowRuntimePort runtimePort;
    private final WorkflowInstanceRefRepository instanceRepository;
    private final WorkflowIncidentRepository incidentRepository;

    public WorkflowRuntimeService(
            WorkflowRuntimePort runtimePort,
            WorkflowInstanceRefRepository instanceRepository,
            WorkflowIncidentRepository incidentRepository) {
        this.runtimePort = runtimePort;
        this.instanceRepository = instanceRepository;
        this.incidentRepository = incidentRepository;
    }

    // ============================================================
    // 流程实例启动（幂等）
    // ============================================================

    /**
     * 启动流程实例。
     *
     * <p>幂等规则：相同 {@code (workspace, businessObject, idempotencyKey)} 重复启动
     * 返回已有实例摘要，不创建重复实例。实例固定为请求中 {@code definitionId} 指向的版本。
     *
     * <p>流程：① 预查 PDP 实例引用（幂等快速路径）→ ② 委托端口启动 Flowable 并持久化实例引用。
     * 若①未命中而②已完成但因网络等原因重试，端口通过 Flowable 业务键实现二次幂等。
     *
     * @param request 启动请求（含定义 ID、业务对象、变量、幂等键、启动者）
     * @return 流程实例摘要
     * @throws WorkflowEngineException 定义不存在、不可启动或引擎故障
     */
    public WorkflowInstanceSummary start(WorkflowStartRequest request) {
        WorkspaceId workspaceId = request.businessObjectRef().workspaceId();
        String idempotencyKeyValue = request.idempotencyKey().value();

        // 幂等预查：同 (workspace, businessObject, idempotencyKey) 已存在时返回已有实例
        Optional<WorkflowInstanceRefRecord> existing = instanceRepository.findByIdempotencyKey(
                workspaceId, request.businessObjectRef(), idempotencyKeyValue);
        if (existing.isPresent()) {
            return toSummary(existing.get());
        }

        // 委托端口启动 Flowable 实例并持久化 PDP 实例引用
        return runtimePort.start(request);
    }

    // ============================================================
    // 查询（工作空间边界隔离）
    // ============================================================

    /**
     * 按 ID 查询流程实例诊断摘要。
     *
     * <p>委托端口查询 Flowable 引擎当前状态（比 PDP 投影更实时）。
     * 跨工作空间访问返回 404，不泄露存在性。
     *
     * @param workspaceId 工作空间边界
     * @param instanceId  实例 ID
     * @return 实例摘要
     * @throws ResourceNotFoundException 不存在或跨工作空间访问
     */
    public WorkflowInstanceSummary getById(WorkspaceId workspaceId, WorkflowInstanceId instanceId) {
        WorkflowInstanceSummary summary = runtimePort.findById(instanceId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkflowInstance", instanceId.value()));
        enforceWorkspaceBoundary(workspaceId, summary.workspaceId(), instanceId.value());
        return summary;
    }

    /**
     * 按业务对象查询关联的流程实例。
     *
     * <p>用于业务模块回查编排状态（如审批详情页展示流程进度）。
     * 跨工作空间返回 empty，不泄露存在性。
     *
     * @param workspaceId      工作空间边界
     * @param businessObjectRef 业务对象引用
     * @return 关联实例摘要，不存在或跨工作空间时返回 empty
     */
    public Optional<WorkflowInstanceSummary> getByBusinessObject(
            WorkspaceId workspaceId, BusinessObjectRef businessObjectRef) {
        return runtimePort.findByBusinessObject(businessObjectRef)
                .filter(summary -> summary.workspaceId().equals(workspaceId));
    }

    // ============================================================
    // 消息关联（幂等）
    // ============================================================

    /**
     * 向运行中流程实例投递消息事件。
     *
     * <p>消息关联 MUST 幂等：相同 {@code (messageName, businessObject, idempotencyKey)}
     * 重复投递不重复推进流程。实例终态时消息被忽略并返回当前实例摘要。
     *
     * <p>流程：① 加载 PDP 实例引用并校验工作空间与终态 → ② 委托端口关联消息。
     *
     * @param workspaceId  工作空间边界
     * @param correlation  消息关联请求
     * @return 关联后的实例摘要
     * @throws ResourceNotFoundException  实例不存在或跨工作空间访问
     * @throws WorkflowEngineException     引擎故障
     */
    public WorkflowInstanceSummary correlateMessage(
            WorkspaceId workspaceId, MessageCorrelation correlation) {
        WorkflowInstanceRefRecord record = loadWithinWorkspaceByBusinessObject(
                workspaceId, correlation.businessObjectRef());

        // 终态实例忽略消息，返回当前摘要
        if (record.isTerminal()) {
            return toSummary(record);
        }

        return runtimePort.correlateMessage(correlation);
    }

    // ============================================================
    // 信号推进（等待态推进）
    // ============================================================

    /**
     * 信号流程继续执行（用于等待态推进）。
     *
     * <p>仅适用于显式等待态（如 receive task、user task 完成后）。
     * 非等待态调用无效，返回当前实例摘要。终态实例返回当前摘要。
     *
     * @param workspaceId    工作空间边界
     * @param instanceId     实例 ID
     * @param idempotencyKey 幂等键
     * @return 推进后的实例摘要
     * @throws ResourceNotFoundException  实例不存在或跨工作空间访问
     * @throws WorkflowEngineException     引擎故障
     */
    public WorkflowInstanceSummary signal(
            WorkspaceId workspaceId, WorkflowInstanceId instanceId, IdempotencyKey idempotencyKey) {
        WorkflowInstanceRefRecord record = loadWithinWorkspace(workspaceId, instanceId);

        // 终态实例忽略信号，返回当前摘要
        if (record.isTerminal()) {
            return toSummary(record);
        }

        return runtimePort.signal(instanceId, idempotencyKey);
    }

    // ============================================================
    // 结果事件桥接
    // ============================================================

    /**
     * 查询流程实例结果事件（终态时桥接到业务模块的结果）。
     *
     * <p>结果事件由引擎适配器在流程完成或异常终止时写入
     * {@code workflow_result_event} 表，业务聚合消费后决定最终业务状态变化。
     *
     * @param workspaceId 工作空间边界
     * @param instanceId  实例 ID
     * @return 结果事件，未终态时返回 empty
     * @throws ResourceNotFoundException 实例不存在或跨工作空间访问
     */
    public Optional<WorkflowResultEvent> findResultEvent(
            WorkspaceId workspaceId, WorkflowInstanceId instanceId) {
        loadWithinWorkspace(workspaceId, instanceId);
        return instanceRepository.findResultEvent(instanceId);
    }

    // ============================================================
    // 健康检查
    // ============================================================

    /**
     * 引擎是否健康（用于就绪检查与降级判断）。
     *
     * <p>当引擎不可用时，编排消费者应将消息保留在 Outbox 等待重试，
     * 而非直接失败（FR-174 编排失败支持幂等恢复）。
     *
     * @return true 表示引擎可用
     */
    public boolean isHealthy() {
        return runtimePort.isHealthy();
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
     * 按业务对象加载实例引用并校验工作空间边界。
     *
     * @param workspaceId      工作空间边界
     * @param businessObjectRef 业务对象引用
     * @return 实例引用聚合
     * @throws ResourceNotFoundException 不存在或跨工作空间访问（统一 404 语义）
     */
    private WorkflowInstanceRefRecord loadWithinWorkspaceByBusinessObject(
            WorkspaceId workspaceId, BusinessObjectRef businessObjectRef) {
        WorkflowInstanceRefRecord record = instanceRepository.findByBusinessObject(businessObjectRef)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "WorkflowInstance", businessObjectRef.objectId()));
        enforceWorkspaceBoundary(workspaceId, record.workspaceId(), businessObjectRef.objectId());
        return record;
    }

    /**
     * 校验工作空间边界，不匹配时抛 404（不泄露存在性）。
     *
     * @param expected 期望工作空间
     * @param actual   实际工作空间
     * @param objectId 对象 ID（用于异常上下文）
     */
    private void enforceWorkspaceBoundary(WorkspaceId expected, WorkspaceId actual, java.util.UUID objectId) {
        if (!expected.equals(actual)) {
            throw new ResourceNotFoundException("WorkflowInstance", objectId);
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
}
