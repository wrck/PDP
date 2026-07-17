package com.pdp.workflow.model;

import com.pdp.shared.context.WorkspaceId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 流程实例摘要值对象（对应 OpenAPI {@code WorkflowInstanceSummary}）。
 *
 * <p>不暴露 Flowable {@code ProcessInstance}/{@code Execution} 对象。
 * 用于实例诊断、管理动作与回查。
 *
 * @param id                   流程实例 ID（PDP 自有 UUIDv7）
 * @param definitionId         流程定义 ID
 * @param workspaceId          工作空间边界
 * @param businessObjectRef    关联业务对象引用（最小编排标识，非权威业务对象）
 * @param state                实例状态机
 * @param currentActivityKeys  当前活动节点键列表（用于诊断）
 * @param incidentCount        当前 incident 数量（死信/告警诊断）
 * @param revision             乐观锁版本
 * @param startedAt            启动时间
 * @param completedAt          完成时间（终态时非空）
 */
public record WorkflowInstanceSummary(
        WorkflowInstanceId id,
        WorkflowDefinitionId definitionId,
        WorkspaceId workspaceId,
        BusinessObjectRef businessObjectRef,
        WorkflowInstanceState state,
        List<String> currentActivityKeys,
        int incidentCount,
        int revision,
        Instant startedAt,
        Instant completedAt) {

    public WorkflowInstanceSummary {
        Objects.requireNonNull(id, "id 不能为 null");
        Objects.requireNonNull(definitionId, "definitionId 不能为 null");
        Objects.requireNonNull(workspaceId, "workspaceId 不能为 null");
        Objects.requireNonNull(businessObjectRef, "businessObjectRef 不能为 null");
        Objects.requireNonNull(state, "state 不能为 null");
        currentActivityKeys = currentActivityKeys == null
                ? List.of() : List.copyOf(currentActivityKeys);
        if (incidentCount < 0) {
            throw new IllegalArgumentException("incidentCount 不能为负");
        }
        if (revision < 0) {
            throw new IllegalArgumentException("revision 不能为负");
        }
        Objects.requireNonNull(startedAt, "startedAt 不能为 null");
    }

    /**
     * 是否为终态（COMPLETED 或 TERMINATED）。
     *
     * @return true 表示终态
     */
    public boolean isTerminal() {
        return state.isTerminal();
    }

    /**
     * 是否处于需要人工处理的 incident 状态。
     *
     * @return true 表示存在未处理 incident
     */
    public boolean hasIncidents() {
        return incidentCount > 0 || state == WorkflowInstanceState.INCIDENT;
    }
}
