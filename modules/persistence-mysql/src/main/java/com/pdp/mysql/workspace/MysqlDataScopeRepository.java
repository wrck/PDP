package com.pdp.mysql.workspace;

import com.pdp.workspace.domain.DataScope;
import com.pdp.workspace.port.DataScopeRepository;
import com.pdp.shared.concurrency.ConcurrencyConflictException;
import com.pdp.shared.concurrency.Revision;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public final class MysqlDataScopeRepository implements DataScopeRepository {

  private final DataScopeMapper mapper;

  public MysqlDataScopeRepository(DataScopeMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<DataScope> findDataScopeById(UUID id) {
    return Optional.ofNullable(mapper.findById(id)).map(DataScopeRow::toDomain);
  }

  @Override
  public Optional<DataScope> findDataScopeByStableKey(UUID workspaceId, String stableKey) {
    return Optional.ofNullable(mapper.findByStableKey(workspaceId, stableKey))
        .map(DataScopeRow::toDomain);
  }

  @Override
  public DataScope save(DataScope dataScope) {
    DataScopeRow row = DataScopeRow.fromDomain(dataScope);
    if (mapper.update(row) == 0) {
      DataScopeRow current = mapper.findById(dataScope.id());
      if (current != null) {
        throw conflict(row.revision(), current.revision());
      }
      mapper.insert(row);
    }
    return mapper.findById(dataScope.id()).toDomain();
  }

  private static ConcurrencyConflictException conflict(long attempted, long actual) {
    return new ConcurrencyConflictException(new Revision(Math.max(0, attempted - 1)), new Revision(actual));
  }
}
