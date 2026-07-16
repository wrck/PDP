package com.pdp.experience.search;

import java.util.Objects;
import java.util.UUID;

public record SearchObjectRef(UUID workspaceId, String objectType, UUID objectId) {
    public SearchObjectRef {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(objectId, "objectId");
        if (objectType == null || objectType.isBlank()) {
            throw new IllegalArgumentException("objectType 不能为空");
        }
    }
}
