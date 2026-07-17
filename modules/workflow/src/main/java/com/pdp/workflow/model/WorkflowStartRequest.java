package com.pdp.workflow.model;

import com.pdp.shared.context.ActorRef;
import com.pdp.shared.context.IdempotencyKey;

import java.util.Map;
import java.util.Objects;

/**
 * 流程实例启动请求值对象（FR-174）。
 *
 * <p>启动请求携带业务对象引用（最小编排标识）与初始流程变量。流程变量 MUST NOT
 * 包含权威业务对象、完整附件、凭据或最终权限结论（ADR-0005 第 7 节）。
 *
 * @param definitionId      流程定义 ID（启动后固定为此版本）
 * @param businessObjectRef 关联业务对象引用
 * @param variables         初始流程变量（仅编排所需稳定标识与非敏感快照）
 * @param idempotencyKey    幂等键（相同键 + businessObjectRef 已启动时返回已有实例）
 * @param startedBy         启动者
 */
public record WorkflowStartRequest(
        WorkflowDefinitionId definitionId,
        BusinessObjectRef businessObjectRef,
        Map<String, Object> variables,
        IdempotencyKey idempotencyKey,
        ActorRef startedBy) {

    public WorkflowStartRequest {
        Objects.requireNonNull(definitionId, "definitionId 不能为 null");
        Objects.requireNonNull(businessObjectRef, "businessObjectRef 不能为 null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey 不能为 null");
        Objects.requireNonNull(startedBy, "startedBy 不能为 null");
        variables = variables == null ? Map.of() : Map.copyOf(variables);
    }

    public static WorkflowStartRequest of(
            WorkflowDefinitionId definitionId,
            BusinessObjectRef businessObjectRef,
            IdempotencyKey idempotencyKey,
            ActorRef startedBy) {
        return new WorkflowStartRequest(definitionId, businessObjectRef, Map.of(),
                idempotencyKey, startedBy);
    }
}
