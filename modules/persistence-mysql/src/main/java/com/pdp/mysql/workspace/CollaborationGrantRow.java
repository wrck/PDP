package com.pdp.mysql.workspace;

import com.pdp.persistence.type.JsonDocument;
import com.pdp.shared.concurrency.Revision;
import com.pdp.workspace.domain.CollaborationGrant;
import java.time.Instant;
import java.util.UUID;

public record CollaborationGrantRow(
    UUID id,
    UUID ownerWorkspaceId,
    UUID collaboratorWorkspaceId,
    String targetType,
    UUID targetId,
    UUID roleId,
    JsonDocument allowedActions,
    Instant validFrom,
    Instant validUntil,
    UUID grantedBy,
    CollaborationGrant.Status status,
    Instant revokedAt,
    String revocationReason,
    long revision) {

  static CollaborationGrantRow fromDomain(CollaborationGrant grant) {
    return new CollaborationGrantRow(
        grant.id(),
        grant.ownerWorkspaceId(),
        grant.collaboratorWorkspaceId(),
        grant.targetType(),
        grant.targetId(),
        grant.roleId(),
        WorkspaceJsonCodec.stringSet(grant.allowedActions()),
        grant.validFrom(),
        grant.validUntil(),
        grant.grantedBy(),
        grant.status(),
        grant.revokedAt(),
        grant.revocationReason(),
        grant.revision().value());
  }

  CollaborationGrant toDomain() {
    return new CollaborationGrant(
        id,
        ownerWorkspaceId,
        collaboratorWorkspaceId,
        targetType,
        targetId,
        roleId,
        WorkspaceJsonCodec.stringSet(allowedActions),
        validFrom,
        validUntil,
        grantedBy,
        status,
        revokedAt,
        revocationReason,
        new Revision(revision));
  }
}
