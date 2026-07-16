package com.pdp.mysql.workspace;

import com.pdp.workspace.domain.WorkspaceMembership;
import com.pdp.workspace.port.WorkspaceMembershipRepository;
import com.pdp.shared.concurrency.ConcurrencyConflictException;
import com.pdp.shared.concurrency.Revision;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public final class MysqlWorkspaceMembershipRepository implements WorkspaceMembershipRepository {

  private final WorkspaceMembershipMapper mapper;

  public MysqlWorkspaceMembershipRepository(WorkspaceMembershipMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<WorkspaceMembership> findMembershipById(UUID id) {
    return Optional.ofNullable(mapper.findById(id)).map(WorkspaceMembershipRow::toDomain);
  }

  @Override
  public Optional<WorkspaceMembership> findByWorkspaceAndUser(UUID workspaceId, UUID userId) {
    return Optional.ofNullable(mapper.findByWorkspaceAndUser(workspaceId, userId))
        .map(WorkspaceMembershipRow::toDomain);
  }

  @Override
  public List<WorkspaceMembership> findMembershipsByWorkspaceId(UUID workspaceId) {
    return mapper.findByWorkspaceId(workspaceId).stream()
        .map(WorkspaceMembershipRow::toDomain)
        .toList();
  }

  @Override
  public WorkspaceMembership save(WorkspaceMembership membership) {
    WorkspaceMembershipRow row = WorkspaceMembershipRow.fromDomain(membership);
    if (mapper.update(row) == 0) {
      WorkspaceMembershipRow current = mapper.findById(membership.id());
      if (current != null) {
        throw conflict(row.revision(), current.revision());
      }
      mapper.insert(row);
    }
    return mapper.findById(membership.id()).toDomain();
  }

  private static ConcurrencyConflictException conflict(long attempted, long actual) {
    return new ConcurrencyConflictException(new Revision(Math.max(0, attempted - 1)), new Revision(actual));
  }
}
