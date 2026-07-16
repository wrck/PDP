package com.pdp.mysql.workspace;

import com.pdp.workspace.domain.WorkspaceRole;
import com.pdp.workspace.port.WorkspaceRoleRepository;
import com.pdp.shared.concurrency.ConcurrencyConflictException;
import com.pdp.shared.concurrency.Revision;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public final class MysqlWorkspaceRoleRepository implements WorkspaceRoleRepository {

  private final WorkspaceRoleMapper mapper;

  public MysqlWorkspaceRoleRepository(WorkspaceRoleMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<WorkspaceRole> findRoleById(UUID id) {
    return Optional.ofNullable(mapper.findById(id)).map(WorkspaceRoleRow::toDomain);
  }

  @Override
  public Optional<WorkspaceRole> findRoleByStableKey(UUID workspaceId, String stableKey) {
    return Optional.ofNullable(mapper.findByStableKey(workspaceId, stableKey))
        .map(WorkspaceRoleRow::toDomain);
  }

  @Override
  public List<WorkspaceRole> findRolesByWorkspaceId(UUID workspaceId) {
    return mapper.findByWorkspaceId(workspaceId).stream()
        .map(WorkspaceRoleRow::toDomain)
        .toList();
  }

  @Override
  public WorkspaceRole save(WorkspaceRole role) {
    WorkspaceRoleRow row = WorkspaceRoleRow.fromDomain(role);
    if (mapper.update(row) == 0) {
      WorkspaceRoleRow current = mapper.findById(role.id());
      if (current != null) {
        throw conflict(row.revision(), current.revision());
      }
      mapper.insert(row);
    }
    return mapper.findById(role.id()).toDomain();
  }

  private static ConcurrencyConflictException conflict(long attempted, long actual) {
    return new ConcurrencyConflictException(new Revision(Math.max(0, attempted - 1)), new Revision(actual));
  }
}
