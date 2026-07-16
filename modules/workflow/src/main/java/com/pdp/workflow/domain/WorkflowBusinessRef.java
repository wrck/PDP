package com.pdp.workflow.domain;

import java.util.Objects;
import java.util.UUID;

public record WorkflowBusinessRef(UUID workspaceId, String objectType, UUID objectId) {
    public WorkflowBusinessRef {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(objectId, "objectId");
        if (objectType == null || objectType.isBlank()) {
            throw new IllegalArgumentException("objectType 不能为空");
        }
    }
}
