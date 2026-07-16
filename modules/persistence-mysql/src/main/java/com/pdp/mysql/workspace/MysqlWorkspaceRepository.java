package com.pdp.mysql.workspace;

import com.pdp.workspace.domain.Workspace;
import com.pdp.workspace.port.WorkspaceRepository;
import com.pdp.shared.concurrency.ConcurrencyConflictException;
import com.pdp.shared.concurrency.Revision;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public final class MysqlWorkspaceRepository implements WorkspaceRepository {

  private final WorkspaceMapper mapper;

  public MysqlWorkspaceRepository(WorkspaceMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<Workspace> findById(UUID id) {
    return Optional.ofNullable(mapper.findById(id)).map(WorkspaceRow::toDomain);
  }

  @Override
  public Optional<Workspace> findByCode(String code) {
    return Optional.ofNullable(mapper.findByCode(code)).map(WorkspaceRow::toDomain);
  }

  @Override
  public List<Workspace> findAccessibleByUserId(UUID userId) {
    return mapper.findAccessibleByUserId(userId).stream().map(WorkspaceRow::toDomain).toList();
  }

  @Override
  public Workspace save(Workspace workspace) {
    WorkspaceRow row = WorkspaceRow.fromDomain(workspace);
    if (mapper.update(row) == 0) {
      WorkspaceRow current = mapper.findById(workspace.id());
      if (current != null) {
        throw conflict(row.revision(), current.revision());
      }
      mapper.insert(row);
    }
    return mapper.findById(workspace.id()).toDomain();
  }

  private static ConcurrencyConflictException conflict(long attempted, long actual) {
    return new ConcurrencyConflictException(new Revision(Math.max(0, attempted - 1)), new Revision(actual));
  }
}
