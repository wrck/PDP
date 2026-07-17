package com.pdp.workflow.model;

import com.pdp.shared.context.IdempotencyKey;

import java.util.Map;
import java.util.Objects;

/**
 * 消息关联请求值对象（FR-174）。
 *
 * <p>用于向运行中流程实例投递消息事件（如审批通过、阶段切换、外部回调）。
 * 消息关联 MUST 幂等：相同 {@code (messageName, businessKey, idempotencyKey)}
 * 重复投递不重复推进流程。
 *
 * @param messageName       消息名称（BPMN message event 定义）
 * @param businessObjectRef 关联业务对象（用于定位流程实例）
 * @param variables         消息变量（仅编排所需标识）
 * @param idempotencyKey    幂等键
 */
public record MessageCorrelation(
        String messageName,
        BusinessObjectRef businessObjectRef,
        Map<String, Object> variables,
        IdempotencyKey idempotencyKey) {

    public MessageCorrelation {
        Objects.requireNonNull(messageName, "messageName 不能为 null");
        if (messageName.isBlank()) {
            throw new IllegalArgumentException("messageName 不能为空白");
        }
        Objects.requireNonNull(businessObjectRef, "businessObjectRef 不能为 null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey 不能为 null");
        variables = variables == null ? Map.of() : Map.copyOf(variables);
    }

    public static MessageCorrelation of(
            String messageName,
            BusinessObjectRef businessObjectRef,
            IdempotencyKey idempotencyKey) {
        return new MessageCorrelation(messageName, businessObjectRef, Map.of(), idempotencyKey);
    }
}
