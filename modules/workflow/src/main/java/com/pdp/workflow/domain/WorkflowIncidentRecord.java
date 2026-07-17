package com.pdp.workflow.domain;

import com.pdp.shared.context.ActorRef;
import com.pdp.shared.context.WorkspaceId;
import com.pdp.workflow.model.WorkflowInstanceId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 工作流异常记录持久化聚合（对应 {@code workflow_incident} 表）。
 *
 * <p>记录 Flowable incident（异步作业失败、定时器异常、死信）的 PDP 侧投影，
 * 供管理动作（重试、人工补偿）决策与审计。状态机由 {@link WorkflowIncidentStatus} 管理。
 *
 * <p>本记录不依赖 {@code org.flowable.*} 类型，仅保存引擎返回的字符串标识与脱敏错误摘要。
 * 错误消息 MUST 脱敏，不暴露内部堆栈；{@code lastErrorDigest} 用于同错误幂等去重。
 *
 * @param id                异常记录 ID（PDP 自有 UUIDv7）
 * @param workspaceId       工作空间边界
 * @param instanceRefId     所属实例引用 ID
 * @param engineJobId       引擎作业 ID（Flowable Job.id，可选）
 * @param activityKey       发生活动节点键
 * @param incidentType      incident 类型（如 async-job-failed、timer-failed、dead-letter）
 * @param attempts          重试次数
 * @param lastErrorCode     最后错误码（可选）
 * @param lastErrorMessage  最后错误消息（脱敏，可选）
 * @param lastErrorDigest   最后错误摘要（用于幂等去重，可选）
 * @param nextRetryAt       下次重试时间（可选）
 * @param status            异常状态机
 * @param resolution        解决说明（可选）
 * @param resolvedBy        解决者（可选）
 * @param resolvedAt        解决时间（可选）
 * @param occurredAt        发生时间
 * @param createdAt         创建时间
 * @param updatedAt         更新时间
 * @param revision          乐观锁版本
 */
public record WorkflowIncidentRecord(
        UUID id,
        WorkspaceId workspaceId,
        WorkflowInstanceId instanceRefId,
        String engineJobId,
        String activityKey,
        String incidentType,
        int attempts,
        String lastErrorCode,
        String lastErrorMessage,
        String lastErrorDigest,
        Instant nextRetryAt,
        WorkflowIncidentStatus status,
        String resolution,
        ActorRef resolvedBy,
        Instant resolvedAt,
        Instant occurredAt,
        Instant createdAt,
        Instant updatedAt,
        int revision) {

    public WorkflowIncidentRecord {
        Objects.requireNonNull(id, "id 不能为 null");
        Objects.requireNonNull(workspaceId, "workspaceId 不能为 null");
        Objects.requireNonNull(instanceRefId, "instanceRefId 不能为 null");
        Objects.requireNonNull(activityKey, "activityKey 不能为 null");
        if (activityKey.isBlank()) {
            throw new IllegalArgumentException("activityKey 不能为空白");
        }
        Objects.requireNonNull(incidentType, "incidentType 不能为 null");
        if (incidentType.isBlank()) {
            throw new IllegalArgumentException("incidentType 不能为空白");
        }
        if (attempts < 0) {
            throw new IllegalArgumentException("attempts 不能为负");
        }
        Objects.requireNonNull(status, "status 不能为 null");
        Objects.requireNonNull(occurredAt, "occurredAt 不能为 null");
        Objects.requireNonNull(createdAt, "createdAt 不能为 null");
        Objects.requireNonNull(updatedAt, "updatedAt 不能为 null");
        if (revision < 0) {
            throw new IllegalArgumentException("revision 不能为负");
        }
    }

    public Optional<String> engineJobIdOptional() {
        return Optional.ofNullable(engineJobId);
    }

    public Optional<String> lastErrorCodeOptional() {
        return Optional.ofNullable(lastErrorCode);
    }

    public Optional<String> lastErrorMessageOptional() {
        return Optional.ofNullable(lastErrorMessage);
    }

    public Optional<String> lastErrorDigestOptional() {
        return Optional.ofNullable(lastErrorDigest);
    }

    public Optional<Instant> nextRetryAtOptional() {
        return Optional.ofNullable(nextRetryAt);
    }

    public Optional<String> resolutionOptional() {
        return Optional.ofNullable(resolution);
    }

    public Optional<ActorRef> resolvedByOptional() {
        return Optional.ofNullable(resolvedBy);
    }

    public Optional<Instant> resolvedAtOptional() {
        return Optional.ofNullable(resolvedAt);
    }

    /** 是否已解决。 */
    public boolean isResolved() {
        return status == WorkflowIncidentStatus.RESOLVED;
    }

    /** 是否未解决（用于计数查询）。 */
    public boolean isUnresolved() {
        return status.isUnresolved();
    }
}
