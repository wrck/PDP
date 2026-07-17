package com.pdp.workflow.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;


/**
 * 流程实例 incident 诊断值对象（FR-174 运行诊断）。
 *
 * <p>对应 Flowable incident（异步作业失败、定时器异常、死信），
 * 但不暴露 Flowable {@code Incident} 对象。用于管理动作（重试、人工补偿）决策。
 *
 * @param incidentId    incident ID（PDP 自有标识）
 * @param instanceId    所属实例 ID
 * @param activityKey   发生活动节点键
 * @param incidentType  incident 类型（如 async-job-failed、timer-failed、dead-letter）
 * @param errorMessage  错误消息（脱敏，不暴露内部堆栈）
 * @param occurredAt    发生时间
 * @param resolvedAt    解决时间（未解决时为 null）
 * @param retryCount    重试次数
 */
public record WorkflowIncident(
        String incidentId,
        WorkflowInstanceId instanceId,
        String activityKey,
        String incidentType,
        String errorMessage,
        Instant occurredAt,
        Instant resolvedAt,
        int retryCount) {

    public WorkflowIncident {
        Objects.requireNonNull(incidentId, "incidentId 不能为 null");
        if (incidentId.isBlank()) {
            throw new IllegalArgumentException("incidentId 不能为空白");
        }
        Objects.requireNonNull(instanceId, "instanceId 不能为 null");
        Objects.requireNonNull(activityKey, "activityKey 不能为 null");
        if (activityKey.isBlank()) {
            throw new IllegalArgumentException("activityKey 不能为空白");
        }
        Objects.requireNonNull(incidentType, "incidentType 不能为 null");
        if (incidentType.isBlank()) {
            throw new IllegalArgumentException("incidentType 不能为空白");
        }
        Objects.requireNonNull(errorMessage, "errorMessage 不能为 null");
        Objects.requireNonNull(occurredAt, "occurredAt 不能为 null");
        if (retryCount < 0) {
            throw new IllegalArgumentException("retryCount 不能为负");
        }
    }

    public boolean isResolved() {
        return resolvedAt != null;
    }

    /** 解决时间（未解决时为 empty）。 */
    public Optional<Instant> resolvedAtOptional() {
        return Optional.ofNullable(resolvedAt);
    }
}
