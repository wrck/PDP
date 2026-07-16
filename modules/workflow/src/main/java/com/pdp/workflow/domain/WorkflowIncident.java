package com.pdp.workflow.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record WorkflowIncident(
        UUID id,
        UUID workflowInstanceId,
        String code,
        String message,
        Status status,
        int attempts,
        Instant occurredAt,
        Instant resolvedAt) {

    public enum Status { OPEN, RETRYING, RESOLVED, DEAD_LETTER }

    public WorkflowIncident {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(workflowInstanceId, "workflowInstanceId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (code == null || code.isBlank() || message == null || message.isBlank() || attempts < 0) {
            throw new IllegalArgumentException("异常记录字段无效");
        }
    }
}
