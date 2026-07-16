package com.pdp.mysql.workspace;

import com.pdp.shared.concurrency.Revision;
import com.pdp.workspace.domain.Workspace;
import java.time.Instant;
import java.util.UUID;

public record WorkspaceRow(
    UUID id,
    String code,
    String name,
    UUID ownerUserId,
    Workspace.Status status,
    String defaultLocale,
    String defaultTimezone,
    UUID dataClassificationPolicyId,
    long revision,
    Instant createdAt,
    Instant updatedAt) {

  static WorkspaceRow fromDomain(Workspace workspace) {
    return new WorkspaceRow(
        workspace.id(),
        workspace.code(),
        workspace.name(),
        workspace.ownerUserId(),
        workspace.status(),
        workspace.defaultLocale(),
        workspace.defaultTimezone(),
        workspace.dataClassificationPolicyId(),
        workspace.revision().value(),
        workspace.createdAt(),
        workspace.updatedAt());
  }

  Workspace toDomain() {
    return new Workspace(
        id,
        code,
        name,
        ownerUserId,
        status,
        defaultLocale,
        defaultTimezone,
        dataClassificationPolicyId,
        new Revision(revision),
        createdAt,
        updatedAt);
  }
}
