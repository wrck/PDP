package com.pdp.workspace.domain;

import com.pdp.shared.concurrency.Revision;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record WorkspaceMembership(
    UUID id,
    UUID workspaceId,
    UUID userId,
    UUID organizationId,
    Type membershipType,
    Status status,
    Instant validFrom,
    Instant validUntil,
    Set<UUID> roleIds,
    Set<UUID> dataScopeIds,
    Revision revision) {

  public enum Type {
    INTERNAL,
    EXTERNAL
  }

  public enum Status {
    INVITED,
    ACTIVE,
    SUSPENDED,
    EXPIRED,
    REVOKED
  }

  public WorkspaceMembership {
    roleIds = Set.copyOf(roleIds);
    dataScopeIds = Set.copyOf(dataScopeIds);
    if (membershipType == Type.EXTERNAL && validUntil == null) {
      throw new IllegalArgumentException("外部成员必须设置到期时间");
    }
    if (validUntil != null && !validUntil.isAfter(validFrom)) {
      throw new IllegalArgumentException("成员到期时间必须晚于生效时间");
    }
  }

  public static WorkspaceMembership active(
      UUID workspaceId,
      UUID userId,
      UUID organizationId,
      Type membershipType,
      Instant validFrom,
      Instant validUntil,
      Set<UUID> roleIds,
      Set<UUID> dataScopeIds) {
    if (roleIds.isEmpty()) {
      throw new IllegalArgumentException("成员至少需要一个角色");
    }
    return new WorkspaceMembership(
        UUID.randomUUID(),
        workspaceId,
        userId,
        organizationId,
        membershipType,
        Status.ACTIVE,
        validFrom,
        validUntil,
        roleIds,
        dataScopeIds,
        new Revision(0));
  }

  public boolean isEffectiveAt(Instant at) {
    return status == Status.ACTIVE
        && !at.isBefore(validFrom)
        && (validUntil == null || at.isBefore(validUntil));
  }
}
