package com.pdp.workflow.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record WorkflowDefinition(
        UUID id,
        String processDefinitionKey,
        String businessVersion,
        String contentHash,
        UUID domainPackageVersionId,
        Status status,
        Instant deployedAt) {

    public enum Status { VALIDATED, DEPLOYED, DEPRECATED, RETIRED }

    public WorkflowDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(processDefinitionKey, "processDefinitionKey");
        Objects.requireNonNull(businessVersion, "businessVersion");
        Objects.requireNonNull(contentHash, "contentHash");
        Objects.requireNonNull(status, "status");
        if (status == Status.DEPLOYED && deployedAt == null) {
            throw new IllegalArgumentException("已部署定义必须记录 deployedAt");
        }
    }

    public WorkflowDefinition deploy(Instant at) {
        if (status != Status.VALIDATED) {
            throw new IllegalStateException("只有已校验定义可以部署");
        }
        return new WorkflowDefinition(id, processDefinitionKey, businessVersion, contentHash,
                domainPackageVersionId, Status.DEPLOYED, Objects.requireNonNull(at));
    }
}
