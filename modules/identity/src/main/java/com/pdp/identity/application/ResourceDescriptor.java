package com.pdp.identity.application;

import com.pdp.shared.context.WorkspaceId;
import java.util.Map;
import java.util.UUID;

public record ResourceDescriptor(
    String resourceType,
    UUID resourceId,
    WorkspaceId ownerWorkspaceId,
    Map<String, Object> attributes) {
  public ResourceDescriptor {
    if (resourceType == null || resourceType.isBlank()) {
      throw new IllegalArgumentException("资源类型不能为空");
    }
    if (resourceId == null || ownerWorkspaceId == null) {
      throw new IllegalArgumentException("资源标识和所属工作空间不能为空");
    }
    attributes = Map.copyOf(attributes);
  }
}
