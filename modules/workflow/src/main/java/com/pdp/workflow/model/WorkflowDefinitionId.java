package com.pdp.workflow.model;

import com.pdp.shared.id.UuidV7Generator;

import java.util.Objects;
import java.util.UUID;

/**
 * 流程定义标识值对象。
 *
 * <p>PDP 自有稳定标识，对应数据库 {@code workflow_definition} 表主键（UUIDv7），
 * 不暴露 Flowable {@code ProcessDefinition.id}（引擎内部 ID）。
 */
public record WorkflowDefinitionId(UUID value) {

    public WorkflowDefinitionId {
        Objects.requireNonNull(value, "WorkflowDefinitionId 不能为 null");
    }

    public static WorkflowDefinitionId next() {
        return new WorkflowDefinitionId(UuidV7Generator.next());
    }

    public static WorkflowDefinitionId of(UUID value) {
        return new WorkflowDefinitionId(value);
    }

    public static WorkflowDefinitionId of(String value) {
        return new WorkflowDefinitionId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
