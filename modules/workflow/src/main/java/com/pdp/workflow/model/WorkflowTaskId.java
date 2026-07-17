package com.pdp.workflow.model;

import com.pdp.shared.id.UuidV7Generator;

import java.util.Objects;
import java.util.UUID;

/**
 * 平台人工任务标识值对象（FR-174）。
 *
 * <p>PDP 自有稳定标识，对应数据库 {@code workflow_task} 投影表主键（UUIDv7），
 * 不暴露 Flowable {@code Task.id}（引擎内部 ID）。
 */
public record WorkflowTaskId(UUID value) {

    public WorkflowTaskId {
        Objects.requireNonNull(value, "WorkflowTaskId 不能为 null");
    }

    public static WorkflowTaskId next() {
        return new WorkflowTaskId(UuidV7Generator.next());
    }

    public static WorkflowTaskId of(UUID value) {
        return new WorkflowTaskId(value);
    }

    public static WorkflowTaskId of(String value) {
        return new WorkflowTaskId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
