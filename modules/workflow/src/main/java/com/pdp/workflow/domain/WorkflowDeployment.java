package com.pdp.workflow.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record WorkflowDeployment(
        UUID id, UUID definitionId, String adapterDeploymentRef, String idempotencyKey, Instant deployedAt) {
    public WorkflowDeployment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(deployedAt, "deployedAt");
        if (adapterDeploymentRef == null || adapterDeploymentRef.isBlank()
                || idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("部署引用和幂等键不能为空");
        }
    }
}
