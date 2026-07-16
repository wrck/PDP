package com.pdp.workspace.application;

import com.pdp.workspace.domain.WorkspaceMembership;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record AddWorkspaceMemberCommand(
    UUID userId,
    UUID organizationUnitId,
    Set<UUID> roleIds,
    Set<UUID> dataScopeIds,
    WorkspaceMembership.Type membershipType,
    Instant validUntil) {
  public AddWorkspaceMemberCommand {
    roleIds = Set.copyOf(roleIds);
    dataScopeIds = Set.copyOf(dataScopeIds);
  }
}
