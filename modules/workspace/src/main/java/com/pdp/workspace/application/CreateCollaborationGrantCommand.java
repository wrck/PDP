package com.pdp.workspace.application;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record CreateCollaborationGrantCommand(
    UUID collaboratorWorkspaceId,
    TargetReference target,
    UUID roleId,
    Set<String> allowedActions,
    Instant validUntil,
    String reason) {
  public CreateCollaborationGrantCommand {
    allowedActions = Set.copyOf(allowedActions);
  }
}
