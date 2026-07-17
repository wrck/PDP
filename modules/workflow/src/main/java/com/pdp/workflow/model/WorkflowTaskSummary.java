package com.pdp.workflow.model;

import com.pdp.shared.context.WorkspaceId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 平台人工任务摘要值对象（FR-174）。
 *
 * <p>不暴露 Flowable {@code Task} 对象。候选人由 PDP 实时计算并复核，
 * <strong>绝不依赖</strong> Flowable 引擎内置身份数据作授权（ADR-0005 第 6 节）。
 *
 * @param id              任务 ID（PDP 自有 UUIDv7）
 * @param instanceId      所属流程实例 ID
 * @param workspaceId     工作空间边界
 * @param businessObjectRef 关联业务对象（用于权限范围校验）
 * @param activityKey     BPMN 活动节点键
 * @param taskName        任务名称（展示用）
 * @param status          任务状态机
 * @param assigneeId      当前办理人 ID（已分配时非空）
 * @param candidateUserIds 候选用户 ID 列表（PDP 实时计算）
 * @param candidateGroupKeys 候选组键列表（PDP 实时计算）
 * @param dueAt           截止时间（可选）
 * @param priority        优先级（0-100，越高越紧急）
 * @param formKey         表单键（领域包定义的表单标识，可选）
 * @param createdAt       创建时间
 * @param revision        乐观锁版本
 */
public record WorkflowTaskSummary(
        WorkflowTaskId id,
        WorkflowInstanceId instanceId,
        WorkspaceId workspaceId,
        BusinessObjectRef businessObjectRef,
        String activityKey,
        String taskName,
        WorkflowTaskStatus status,
        UUID assigneeId,
        List<UUID> candidateUserIds,
        List<String> candidateGroupKeys,
        Instant dueAt,
        int priority,
        String formKey,
        Instant createdAt,
        int revision) {

    public WorkflowTaskSummary {
        Objects.requireNonNull(id, "id 不能为 null");
        Objects.requireNonNull(instanceId, "instanceId 不能为 null");
        Objects.requireNonNull(workspaceId, "workspaceId 不能为 null");
        Objects.requireNonNull(businessObjectRef, "businessObjectRef 不能为 null");
        Objects.requireNonNull(activityKey, "activityKey 不能为 null");
        if (activityKey.isBlank()) {
            throw new IllegalArgumentException("activityKey 不能为空白");
        }
        Objects.requireNonNull(status, "status 不能为 null");
        candidateUserIds = candidateUserIds == null
                ? List.of() : List.copyOf(candidateUserIds);
        candidateGroupKeys = candidateGroupKeys == null
                ? List.of() : List.copyOf(candidateGroupKeys);
        if (priority < 0 || priority > 100) {
            throw new IllegalArgumentException("priority 必须在 0-100 之间");
        }
        if (revision < 0) {
            throw new IllegalArgumentException("revision 不能为负");
        }
        Objects.requireNonNull(createdAt, "createdAt 不能为 null");
    }

    public boolean isTerminal() {
        return status.isTerminal();
    }

    /**
     * 用户是否为候选人或办理人。
     *
     * @param userId 用户 ID
     * @return true 表示可认领或办理
     */
    public boolean isAssignableTo(UUID userId) {
        Objects.requireNonNull(userId, "userId 不能为 null");
        if (assigneeId != null) {
            return assigneeId.equals(userId);
        }
        return candidateUserIds.contains(userId);
    }
}
