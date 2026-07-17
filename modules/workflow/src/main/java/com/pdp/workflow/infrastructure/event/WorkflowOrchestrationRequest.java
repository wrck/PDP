package com.pdp.workflow.infrastructure.event;

import com.pdp.shared.context.ActorRef;
import com.pdp.shared.context.IdempotencyKey;
import com.pdp.shared.context.WorkspaceId;
import com.pdp.workflow.model.BusinessObjectRef;
import com.pdp.workflow.model.WorkflowDefinitionId;
import com.pdp.workflow.model.WorkflowInstanceId;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 工作流编排请求事件载荷（对应事件 {@code pdp.workflow.orchestration.requested}）。
 *
 * <p>权威业务事务提交后，业务模块（审批、项目、交付件等）通过 Spring Modulith
 * 事件发布 {@code pdp.workflow.orchestration.requested} 事件，请求 {@code workflow}
 * 模块启动、推进或关联 BPMN 流程。事件由 {@link WorkflowOrchestrationEventConsumer}
 * 异步消费，调用 {@link com.pdp.workflow.application.WorkflowRuntimeService} 执行操作。
 *
 * <p><strong>事件信封</strong>由 Spring Modulith JDBC 事件发布存储管理
 * （eventId、occurredAt、aggregateType 等），本记录仅承载 {@code data} 载荷。
 *
 * <p><strong>操作类型</strong>：
 * <ul>
 *   <li>{@link Operation#START}：启动流程实例，需提供 definitionId、businessObjectRef、startedBy；</li>
 *   <li>{@link Operation#CORRELATE}：向运行中实例投递消息，需提供 messageName、businessObjectRef；</li>
 *   <li>{@link Operation#SIGNAL}：信号推进等待态实例，需提供 instanceId。</li>
 * </ul>
 *
 * <p><strong>幂等保证</strong>：消费者通过 Spring Modulith eventId 去重，
 * 运行时服务通过 idempotencyKey + businessObjectRef 二次幂等。
 *
 * @param eventId           事件 ID（UUIDv7，由 Spring Modulith 生成，消费者幂等依据）
 * @param operation         操作类型
 * @param workspaceId       工作空间边界
 * @param definitionId      流程定义 ID（仅 START 操作需要）
 * @param instanceId        流程实例 ID（仅 SIGNAL 操作需要）
 * @param businessObjectRef 业务对象引用（START、CORRELATE 操作需要）
 * @param messageName       消息名称（仅 CORRELATE 操作需要）
 * @param variables         流程变量（仅编排所需稳定标识与非敏感快照）
 * @param idempotencyKey    幂等键
 * @param actor             操作者（启动者或触发者）
 */
public record WorkflowOrchestrationRequest(
        UUID eventId,
        Operation operation,
        WorkspaceId workspaceId,
        WorkflowDefinitionId definitionId,
        WorkflowInstanceId instanceId,
        BusinessObjectRef businessObjectRef,
        String messageName,
        Map<String, Object> variables,
        IdempotencyKey idempotencyKey,
        ActorRef actor) {

    /** 编排操作类型。 */
    public enum Operation {
        START,
        CORRELATE,
        SIGNAL;

        public String stableKey() {
            return name();
        }
    }

    public WorkflowOrchestrationRequest {
        Objects.requireNonNull(eventId, "eventId 不能为 null");
        Objects.requireNonNull(operation, "operation 不能为 null");
        Objects.requireNonNull(workspaceId, "workspaceId 不能为 null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey 不能为 null");
        Objects.requireNonNull(actor, "actor 不能为 null");
        variables = variables == null ? Map.of() : Map.copyOf(variables);

        // 操作特定字段校验
        switch (operation) {
            case START -> {
                Objects.requireNonNull(definitionId, "START 操作需要 definitionId");
                Objects.requireNonNull(businessObjectRef, "START 操作需要 businessObjectRef");
            }
            case CORRELATE -> {
                Objects.requireNonNull(businessObjectRef, "CORRELATE 操作需要 businessObjectRef");
                if (messageName == null || messageName.isBlank()) {
                    throw new IllegalArgumentException("CORRELATE 操作需要 messageName");
                }
            }
            case SIGNAL -> {
                Objects.requireNonNull(instanceId, "SIGNAL 操作需要 instanceId");
            }
        }
    }

    /**
     * 创建 START 操作请求。
     *
     * @param eventId           事件 ID
     * @param workspaceId       工作空间
     * @param definitionId      流程定义 ID
     * @param businessObjectRef 业务对象引用
     * @param variables         流程变量
     * @param idempotencyKey    幂等键
     * @param actor             启动者
     * @return 请求实例
     */
    public static WorkflowOrchestrationRequest start(
            UUID eventId,
            WorkspaceId workspaceId,
            WorkflowDefinitionId definitionId,
            BusinessObjectRef businessObjectRef,
            Map<String, Object> variables,
            IdempotencyKey idempotencyKey,
            ActorRef actor) {
        return new WorkflowOrchestrationRequest(
                eventId, Operation.START, workspaceId,
                definitionId, null, businessObjectRef,
                null, variables, idempotencyKey, actor);
    }

    /**
     * 创建 CORRELATE 操作请求。
     *
     * @param eventId           事件 ID
     * @param workspaceId       工作空间
     * @param businessObjectRef 业务对象引用
     * @param messageName       消息名称
     * @param variables         消息变量
     * @param idempotencyKey    幂等键
     * @param actor             触发者
     * @return 请求实例
     */
    public static WorkflowOrchestrationRequest correlate(
            UUID eventId,
            WorkspaceId workspaceId,
            BusinessObjectRef businessObjectRef,
            String messageName,
            Map<String, Object> variables,
            IdempotencyKey idempotencyKey,
            ActorRef actor) {
        return new WorkflowOrchestrationRequest(
                eventId, Operation.CORRELATE, workspaceId,
                null, null, businessObjectRef,
                messageName, variables, idempotencyKey, actor);
    }

    /**
     * 创建 SIGNAL 操作请求。
     *
     * @param eventId        事件 ID
     * @param workspaceId    工作空间
     * @param instanceId     流程实例 ID
     * @param idempotencyKey 幂等键
     * @param actor          触发者
     * @return 请求实例
     */
    public static WorkflowOrchestrationRequest signal(
            UUID eventId,
            WorkspaceId workspaceId,
            WorkflowInstanceId instanceId,
            IdempotencyKey idempotencyKey,
            ActorRef actor) {
        return new WorkflowOrchestrationRequest(
                eventId, Operation.SIGNAL, workspaceId,
                null, instanceId, null,
                null, Map.of(), idempotencyKey, actor);
    }
}
