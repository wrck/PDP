package com.pdp.workspace.domain;

import com.pdp.shared.concurrency.Revision;
import java.util.Set;
import java.util.UUID;

public record WorkspaceRole(
    UUID id,
    UUID workspaceId,
    String stableKey,
    String name,
    Set<String> allowedActions,
    Status status,
    Revision revision) {
  public enum Status {
    ACTIVE,
    RETIRED
  }

  public WorkspaceRole {
    allowedActions = Set.copyOf(allowedActions);
    if (stableKey == null || stableKey.isBlank() || name == null || name.isBlank()) {
      throw new IllegalArgumentException("角色键和名称不能为空");
    }
    if (allowedActions.isEmpty()) {
      throw new IllegalArgumentException("角色至少需要一个允许动作");
    }
  }

  public static WorkspaceRole create(
      UUID workspaceId, String stableKey, String name, Set<String> allowedActions) {
    return new WorkspaceRole(
        UUID.randomUUID(),
        workspaceId,
        stableKey,
        name,
        allowedActions,
        Status.ACTIVE,
        new Revision(0));
  }
}
