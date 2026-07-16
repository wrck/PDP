package com.pdp.workflow.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record WorkflowOrchestrationCommand(
        UUID id,
        Type type,
        UUID workflowInstanceId,
        String idempotencyKey,
        Map<String, String> data,
        Instant occurredAt) {

    public enum Type {
        START, ADVANCE, CORRELATE_MESSAGE, COMPLETE_TASK,
        PAUSE, RESUME, RETRY, MIGRATE, TERMINATE, MANUAL_COMPENSATE
    }

    public WorkflowOrchestrationCommand {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(workflowInstanceId, "workflowInstanceId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("幂等键不能为空");
        }
        data = Map.copyOf(data == null ? Map.of() : data);
    }
}
