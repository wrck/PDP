package com.pdp.workflow.model;

import com.pdp.shared.context.ActorRef;
import com.pdp.shared.context.IdempotencyKey;

import java.util.Map;
import java.util.Objects;

/**
 * 任务办理请求值对象（FR-174、FR-044）。
 *
 * <p>办理时 MUST 实时复核 PDP 当前权限，绝不依赖引擎内置身份数据作授权
 * （ADR-0005 第 6 节）。办理结果通过 {@code variables} 携带，仅限编排所需稳定标识，
 * 非权威业务对象。
 *
 * <p>对应 OpenAPI 审批动作命令 {@code ApprovalActionCommand}（通过、退回、拒绝、撤回、
 * 转交、委托、加签、抄送）在人工任务办理层的稳定抽象。
 *
 * @param taskId        任务 ID
 * @param action        办理动作（业务语义，如 approve、reject、delegate）
 * @param variables     办理变量（编排所需标识）
 * @param comment       办理意见（审计）
 * @param idempotencyKey 幂等键
 * @param completedBy   办理者（MUST 实时复核权限）
 */
public record TaskCompletionRequest(
        WorkflowTaskId taskId,
        String action,
        Map<String, Object> variables,
        String comment,
        IdempotencyKey idempotencyKey,
        ActorRef completedBy) {

    public TaskCompletionRequest {
        Objects.requireNonNull(taskId, "taskId 不能为 null");
        Objects.requireNonNull(action, "action 不能为 null");
        if (action.isBlank()) {
            throw new IllegalArgumentException("action 不能为空白");
        }
        Objects.requireNonNull(idempotencyKey, "idempotencyKey 不能为 null");
        Objects.requireNonNull(completedBy, "completedBy 不能为 null");
        variables = variables == null ? Map.of() : Map.copyOf(variables);
    }

    public static TaskCompletionRequest of(
            WorkflowTaskId taskId,
            String action,
            IdempotencyKey idempotencyKey,
            ActorRef completedBy) {
        return new TaskCompletionRequest(taskId, action, Map.of(), null,
                idempotencyKey, completedBy);
    }
}
