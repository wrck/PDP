package com.pdp.workflow.domain;

import com.pdp.shared.context.ActorRef;
import com.pdp.shared.context.IdempotencyKey;
import com.pdp.shared.context.WorkspaceId;
import com.pdp.workflow.model.BusinessObjectRef;
import com.pdp.workflow.model.WorkflowDefinitionId;
import com.pdp.workflow.model.WorkflowInstanceId;
import com.pdp.workflow.model.WorkflowInstanceState;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 流程实例引用持久化聚合（对应 {@code workflow_instance_ref} 表）。
 *
 * <p>仅保存引擎与权威业务对象之间的关联与编排标识，不复制项目、任务、交付件、权限或
 * 审批结论（ADR-0005 第 7 节）。引擎不可用时仍可从业务对象判断当前权威状态。
 *
 * <p><strong>不变量</strong>：
 * <ul>
 *   <li>实例固定为启动时的定义版本（definitionId + deploymentId 不变）；</li>
 *   <li>相同 (workspace, businessObject, idempotencyKey) 唯一，保证幂等启动；</li>
 *   <li>状态投影由引擎同步，业务结论不依赖本记录。</li>
 * </ul>
 *
 * @param id                       实例引用 ID（PDP 自有 UUIDv7，即 WorkflowInstanceId）
 * @param workspaceId              工作空间边界
 * @param definitionId             流程定义 ID（启动时固定）
 * @param deploymentId             部署记录 ID
 * @param businessObjectRef        关联业务对象引用
 * @param approvalInstanceId       关联审批实例 ID（可选）
 * @param engineProcessInstanceId  引擎流程实例 ID（Flowable ProcessInstance.id，可选）
 * @param engineCorrelationId      引擎关联 ID（消息关联用，可选）
 * @param idempotencyKey           幂等键
 * @param state                    实例状态投影
 * @param currentActivityKeys      当前活动节点键列表（诊断用）
 * @param startedBy                启动者
 * @param startedAt                启动时间
 * @param endedAt                  结束时间（终态时非空）
 * @param lastSyncedAt             最后同步时间（引擎同步回写）
 * @param updatedAt                更新时间
 * @param revision                 乐观锁版本
 */
public record WorkflowInstanceRefRecord(
        WorkflowInstanceId id,
        WorkspaceId workspaceId,
        WorkflowDefinitionId definitionId,
        UUID deploymentId,
        BusinessObjectRef businessObjectRef,
        UUID approvalInstanceId,
        String engineProcessInstanceId,
        String engineCorrelationId,
        IdempotencyKey idempotencyKey,
        WorkflowInstanceState state,
        List<String> currentActivityKeys,
        ActorRef startedBy,
        Instant startedAt,
        Instant endedAt,
        Instant lastSyncedAt,
        Instant updatedAt,
        int revision) {

    public WorkflowInstanceRefRecord {
        Objects.requireNonNull(id, "id 不能为 null");
        Objects.requireNonNull(workspaceId, "workspaceId 不能为 null");
        Objects.requireNonNull(definitionId, "definitionId 不能为 null");
        Objects.requireNonNull(deploymentId, "deploymentId 不能为 null");
        Objects.requireNonNull(businessObjectRef, "businessObjectRef 不能为 null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey 不能为 null");
        Objects.requireNonNull(state, "state 不能为 null");
        currentActivityKeys = currentActivityKeys == null
                ? List.of() : List.copyOf(currentActivityKeys);
        Objects.requireNonNull(startedBy, "startedBy 不能为 null");
        Objects.requireNonNull(startedAt, "startedAt 不能为 null");
        Objects.requireNonNull(updatedAt, "updatedAt 不能为 null");
        if (revision < 0) {
            throw new IllegalArgumentException("revision 不能为负");
        }
    }

    public Optional<UUID> approvalInstanceIdOptional() {
        return Optional.ofNullable(approvalInstanceId);
    }

    public Optional<String> engineProcessInstanceIdOptional() {
        return Optional.ofNullable(engineProcessInstanceId);
    }

    public Optional<String> engineCorrelationIdOptional() {
        return Optional.ofNullable(engineCorrelationId);
    }

    public Optional<Instant> endedAtOptional() {
        return Optional.ofNullable(endedAt);
    }

    public Optional<Instant> lastSyncedAtOptional() {
        return Optional.ofNullable(lastSyncedAt);
    }

    /** 是否为终态。 */
    public boolean isTerminal() {
        return state.isTerminal();
    }

    /** 是否存在 incident。 */
    public boolean hasIncidents() {
        return state == WorkflowInstanceState.INCIDENT;
    }
}
