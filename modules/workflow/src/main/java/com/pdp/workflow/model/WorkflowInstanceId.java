package com.pdp.workflow.model;

import com.pdp.shared.id.UuidV7Generator;

import java.util.Objects;
import java.util.UUID;

/**
 * 流程实例标识值对象。
 *
 * <p>PDP 自有稳定标识，对应数据库 {@code workflow_instance} 表主键（UUIDv7），
 * 不暴露 Flowable {@code Execution.id}/{@code ProcessInstance.id}（引擎内部 ID）。
 */
public record WorkflowInstanceId(UUID value) {

    public WorkflowInstanceId {
        Objects.requireNonNull(value, "WorkflowInstanceId 不能为 null");
    }

    public static WorkflowInstanceId next() {
        return new WorkflowInstanceId(UuidV7Generator.next());
    }

    public static WorkflowInstanceId of(UUID value) {
        return new WorkflowInstanceId(value);
    }

    public static WorkflowInstanceId of(String value) {
        return new WorkflowInstanceId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
