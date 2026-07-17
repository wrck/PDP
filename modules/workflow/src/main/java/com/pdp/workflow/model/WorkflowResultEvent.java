package com.pdp.workflow.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 流程实例结果事件值对象（FR-174、ADR-0005 第 7 节）。
 *
 * <p>流程完成或异常终止时，结果通过 {@code WorkflowRuntimePort} 桥接到业务模块，
 * 由聚合决定最终业务状态变化。结果事件 MUST NOT 直接修改业务对象，
 * 仅携带编排结果标识供业务聚合回查验证。
 *
 * <p>对应事件 {@code pdp.workflow.orchestration.failed} 在编排失败时触发。
 *
 * @param eventId           事件 ID（UUIDv7）
 * @param instanceId        流程实例 ID
 * @param businessObjectRef 关联业务对象
 * @param outcome           结果类型
 * @param resultVariables   结果变量（仅编排结果标识，非权威业务对象）
 * @param occurredAt        发生时间
 * @param failureReason     失败原因（仅 FAILED 时非空）
 */
public record WorkflowResultEvent(
        UUID eventId,
        WorkflowInstanceId instanceId,
        BusinessObjectRef businessObjectRef,
        Outcome outcome,
        Map<String, Object> resultVariables,
        Instant occurredAt,
        String failureReason) {

    /** 流程结果类型。 */
    public enum Outcome {
        COMPLETED,
        TERMINATED,
        FAILED,
        INCIDENT;

        public String stableKey() {
            return name();
        }

        public boolean isFailure() {
            return this != COMPLETED;
        }
    }

    public WorkflowResultEvent {
        Objects.requireNonNull(eventId, "eventId 不能为 null");
        Objects.requireNonNull(instanceId, "instanceId 不能为 null");
        Objects.requireNonNull(businessObjectRef, "businessObjectRef 不能为 null");
        Objects.requireNonNull(outcome, "outcome 不能为 null");
        Objects.requireNonNull(occurredAt, "occurredAt 不能为 null");
        resultVariables = resultVariables == null ? Map.of() : Map.copyOf(resultVariables);
    }

    public static WorkflowResultEvent completed(
            WorkflowInstanceId instanceId,
            BusinessObjectRef businessObjectRef,
            Map<String, Object> resultVariables) {
        return new WorkflowResultEvent(
                UUID.randomUUID(), instanceId, businessObjectRef,
                Outcome.COMPLETED, resultVariables, Instant.now(), null);
    }

    public static WorkflowResultEvent failed(
            WorkflowInstanceId instanceId,
            BusinessObjectRef businessObjectRef,
            String failureReason) {
        return new WorkflowResultEvent(
                UUID.randomUUID(), instanceId, businessObjectRef,
                Outcome.FAILED, Map.of(), Instant.now(), failureReason);
    }

    public boolean isFailure() {
        return outcome.isFailure();
    }
}
