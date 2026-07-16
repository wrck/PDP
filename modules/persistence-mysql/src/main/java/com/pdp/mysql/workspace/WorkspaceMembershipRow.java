package com.pdp.mysql.workspace;

import com.pdp.persistence.type.JsonDocument;
import com.pdp.shared.concurrency.Revision;
import com.pdp.workspace.domain.WorkspaceMembership;
import java.time.Instant;
import java.util.UUID;

public record WorkspaceMembershipRow(
    UUID id,
    UUID workspaceId,
    UUID userId,
    UUID organizationId,
    WorkspaceMembership.Type membershipType,
    WorkspaceMembership.Status status,
    Instant validFrom,
    Instant validUntil,
    JsonDocument roleIds,
    JsonDocument dataScopeIds,
    long revision) {

  static WorkspaceMembershipRow fromDomain(WorkspaceMembership membership) {
    return new WorkspaceMembershipRow(
        membership.id(),
        membership.workspaceId(),
        membership.userId(),
        membership.organizationId(),
        membership.membershipType(),
        membership.status(),
        membership.validFrom(),
        membership.validUntil(),
        WorkspaceJsonCodec.uuidSet(membership.roleIds()),
        WorkspaceJsonCodec.uuidSet(membership.dataScopeIds()),
        membership.revision().value());
  }

  WorkspaceMembership toDomain() {
    return new WorkspaceMembership(
        id,
        workspaceId,
        userId,
        organizationId,
        membershipType,
        status,
        validFrom,
        validUntil,
        WorkspaceJsonCodec.uuidSet(roleIds),
        WorkspaceJsonCodec.uuidSet(dataScopeIds),
        new Revision(revision));
  }
}
