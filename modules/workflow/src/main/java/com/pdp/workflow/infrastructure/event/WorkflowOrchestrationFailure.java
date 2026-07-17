package com.pdp.workflow.infrastructure.event;

import com.pdp.workflow.model.WorkflowEngineException;
import com.pdp.workflow.model.WorkflowInstanceId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 工作流编排失败事件载荷（对应事件 {@code pdp.workflow.orchestration.failed}）。
 *
 * <p>当流程启动、推进或关联达到告警/人工处理条件时，{@link WorkflowOrchestrationEventConsumer}
 * 发布此事件，通知审批、运维和人工补偿消费者介入。
 *
 * <p><strong>触发条件</strong>：
 * <ul>
 *   <li>流程定义不存在或不可启动（{@link WorkflowEngineException.Reason#DEFINITION_NOT_FOUND}）；</li>
 *   <li>状态迁移非法（{@link WorkflowEngineException.Reason#ILLEGAL_STATE_TRANSITION}）；</li>
 *   <li>编排消息关联失败且不可重试（{@link WorkflowEngineException.Reason#ORCHESTRATION_FAILED}）。</li>
 * </ul>
 *
 * <p>引擎暂时不可用（{@link WorkflowEngineException.Reason#ENGINE_UNAVAILABLE}）由 Spring Modulith
 * 自动重试，不触发此事件；仅重试耗尽后进入积压时由运维告警处理。
 *
 * @param eventId           事件 ID（UUIDv7）
 * @param requestEventId    触发失败的原始编排请求事件 ID
 * @param operation         原始操作类型
 * @param instanceId        流程实例 ID（START 失败时可能为 null）
 * @param reason            失败原因分类（稳定键）
 * @param failureMessage    失败消息（脱敏，不含敏感信息）
 * @param occurredAt        发生时间
 */
public record WorkflowOrchestrationFailure(
        UUID eventId,
        UUID requestEventId,
        String operation,
        WorkflowInstanceId instanceId,
        String reason,
        String failureMessage,
        Instant occurredAt) {

    public WorkflowOrchestrationFailure {
        Objects.requireNonNull(eventId, "eventId 不能为 null");
        Objects.requireNonNull(requestEventId, "requestEventId 不能为 null");
        Objects.requireNonNull(operation, "operation 不能为 null");
        Objects.requireNonNull(reason, "reason 不能为 null");
        Objects.requireNonNull(occurredAt, "occurredAt 不能为 null");
    }

    /**
     * 从引擎异常构造失败事件。
     *
     * @param requestEventId 原始请求事件 ID
     * @param operation      原始操作类型
     * @param instanceId     实例 ID（可能为 null）
     * @param exception      引擎异常
     * @return 失败事件
     */
    public static WorkflowOrchestrationFailure of(
            UUID requestEventId,
            String operation,
            WorkflowInstanceId instanceId,
            WorkflowEngineException exception) {
        return new WorkflowOrchestrationFailure(
                UUID.randomUUID(),
                requestEventId,
                operation,
                instanceId,
                exception.workflowReason().stableKey(),
                exception.getMessage(),
                Instant.now());
    }
}
