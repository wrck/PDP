package com.pdp.mysql.workspace;

import com.pdp.persistence.type.JsonDocument;
import com.pdp.shared.concurrency.Revision;
import com.pdp.workspace.domain.WorkspaceRole;
import java.util.UUID;

public record WorkspaceRoleRow(
    UUID id,
    UUID workspaceId,
    String stableKey,
    String name,
    JsonDocument allowedActions,
    WorkspaceRole.Status status,
    long revision) {

  static WorkspaceRoleRow fromDomain(WorkspaceRole role) {
    return new WorkspaceRoleRow(
        role.id(),
        role.workspaceId(),
        role.stableKey(),
        role.name(),
        WorkspaceJsonCodec.stringSet(role.allowedActions()),
        role.status(),
        role.revision().value());
  }

  WorkspaceRole toDomain() {
    return new WorkspaceRole(
        id,
        workspaceId,
        stableKey,
        name,
        WorkspaceJsonCodec.stringSet(allowedActions),
        status,
        new Revision(revision));
  }
}
