package com.pdp.mysql.workspace;

import com.pdp.workspace.domain.CollaborationGrant;
import com.pdp.workspace.port.CollaborationGrantRepository;
import com.pdp.shared.concurrency.ConcurrencyConflictException;
import com.pdp.shared.concurrency.Revision;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public final class MysqlCollaborationGrantRepository implements CollaborationGrantRepository {

  private final CollaborationGrantMapper mapper;

  public MysqlCollaborationGrantRepository(CollaborationGrantMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<CollaborationGrant> findGrantById(UUID id) {
    return Optional.ofNullable(mapper.findById(id)).map(CollaborationGrantRow::toDomain);
  }

  @Override
  public List<CollaborationGrant> findGrantsByOwnerWorkspaceId(UUID workspaceId) {
    return mapper.findByOwnerWorkspaceId(workspaceId).stream()
        .map(CollaborationGrantRow::toDomain)
        .toList();
  }

  @Override
  public List<CollaborationGrant> findActiveGrants(
      UUID ownerWorkspaceId, UUID collaboratorWorkspaceId, Instant at) {
    return mapper.findActive(ownerWorkspaceId, collaboratorWorkspaceId, at).stream()
        .map(CollaborationGrantRow::toDomain)
        .toList();
  }

  @Override
  public CollaborationGrant save(CollaborationGrant grant) {
    CollaborationGrantRow row = CollaborationGrantRow.fromDomain(grant);
    if (mapper.update(row) == 0) {
      CollaborationGrantRow current = mapper.findById(grant.id());
      if (current != null) {
        throw conflict(row.revision(), current.revision());
      }
      mapper.insert(row);
    }
    return mapper.findById(grant.id()).toDomain();
  }

  private static ConcurrencyConflictException conflict(long attempted, long actual) {
    return new ConcurrencyConflictException(new Revision(Math.max(0, attempted - 1)), new Revision(actual));
  }
}
