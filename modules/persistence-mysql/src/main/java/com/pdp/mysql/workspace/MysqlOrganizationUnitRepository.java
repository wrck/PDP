package com.pdp.mysql.workspace;

import com.pdp.workspace.domain.OrganizationUnit;
import com.pdp.workspace.port.OrganizationUnitRepository;
import com.pdp.shared.concurrency.ConcurrencyConflictException;
import com.pdp.shared.concurrency.Revision;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public final class MysqlOrganizationUnitRepository implements OrganizationUnitRepository {

  private final OrganizationUnitMapper mapper;

  public MysqlOrganizationUnitRepository(OrganizationUnitMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<OrganizationUnit> findOrganizationById(UUID id) {
    return Optional.ofNullable(mapper.findById(id)).map(OrganizationUnitRow::toDomain);
  }

  @Override
  public Optional<OrganizationUnit> findByWorkspaceAndCode(UUID workspaceId, String code) {
    return Optional.ofNullable(mapper.findByWorkspaceAndCode(workspaceId, code))
        .map(OrganizationUnitRow::toDomain);
  }

  @Override
  public List<OrganizationUnit> findOrganizationsByWorkspaceId(UUID workspaceId) {
    return mapper.findByWorkspaceId(workspaceId).stream()
        .map(OrganizationUnitRow::toDomain)
        .toList();
  }

  @Override
  public OrganizationUnit save(OrganizationUnit organizationUnit) {
    OrganizationUnitRow row = OrganizationUnitRow.fromDomain(organizationUnit);
    if (mapper.update(row) == 0) {
      OrganizationUnitRow current = mapper.findById(organizationUnit.id());
      if (current != null) {
        throw conflict(row.revision(), current.revision());
      }
      mapper.insert(row);
    }
    return mapper.findById(organizationUnit.id()).toDomain();
  }

  private static ConcurrencyConflictException conflict(long attempted, long actual) {
    return new ConcurrencyConflictException(new Revision(Math.max(0, attempted - 1)), new Revision(actual));
  }
}
