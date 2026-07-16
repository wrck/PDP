package com.pdp.workspace.domain;

import com.pdp.shared.concurrency.Revision;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record CollaborationGrant(
    UUID id,
    UUID ownerWorkspaceId,
    UUID collaboratorWorkspaceId,
    String targetType,
    UUID targetId,
    UUID roleId,
    Set<String> allowedActions,
    Instant validFrom,
    Instant validUntil,
    UUID grantedBy,
    Status status,
    Instant revokedAt,
    String revocationReason,
    Revision revision) {

  public enum Status {
    DRAFT,
    ACTIVE,
    EXPIRED,
    REVOKED
  }

  public CollaborationGrant {
    allowedActions = Set.copyOf(allowedActions);
    if (ownerWorkspaceId.equals(collaboratorWorkspaceId)) {
      throw new IllegalArgumentException("协作工作空间不能与主工作空间相同");
    }
    if (allowedActions.isEmpty()) {
      throw new IllegalArgumentException("协作授权至少需要一个动作");
    }
    if (!validUntil.isAfter(validFrom)) {
      throw new IllegalArgumentException("授权到期时间必须晚于生效时间");
    }
  }

  public static CollaborationGrant draft(
      UUID ownerWorkspaceId,
      UUID collaboratorWorkspaceId,
      String targetType,
      UUID targetId,
      UUID roleId,
      Set<String> allowedActions,
      Instant validFrom,
      Instant validUntil,
      UUID grantedBy) {
    return new CollaborationGrant(
        UUID.randomUUID(),
        ownerWorkspaceId,
        collaboratorWorkspaceId,
        targetType,
        targetId,
        roleId,
        allowedActions,
        validFrom,
        validUntil,
        grantedBy,
        Status.DRAFT,
        null,
        null,
        new Revision(0));
  }

  public CollaborationGrant activate() {
    if (status != Status.DRAFT) {
      throw new IllegalStateException("仅草稿协作授权可以激活");
    }
    return withStatus(Status.ACTIVE, null, null);
  }

  public CollaborationGrant expire(Instant at) {
    if (status != Status.ACTIVE || at.isBefore(validUntil)) {
      throw new IllegalStateException("仅已到期的活动授权可以标记为过期");
    }
    return withStatus(Status.EXPIRED, null, null);
  }

  public CollaborationGrant revoke(Instant at, String reason) {
    if (status == Status.REVOKED) {
      return this;
    }
    return withStatus(Status.REVOKED, at, reason);
  }

  private CollaborationGrant withStatus(
      Status nextStatus, Instant nextRevokedAt, String nextRevocationReason) {
    return new CollaborationGrant(
        id,
        ownerWorkspaceId,
        collaboratorWorkspaceId,
        targetType,
        targetId,
        roleId,
        allowedActions,
        validFrom,
        validUntil,
        grantedBy,
        nextStatus,
        nextRevokedAt,
        nextRevocationReason,
        revision.next());
  }

  public boolean isEffective(UUID owner, UUID collaborator, Instant at) {
    return ownerWorkspaceId.equals(owner)
        && collaboratorWorkspaceId.equals(collaborator)
        && status == Status.ACTIVE
        && !at.isBefore(validFrom)
        && at.isBefore(validUntil);
  }
}
