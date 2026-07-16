package com.pdp.mysql.domainconfig;

import com.pdp.domainconfig.domain.packageversion.DomainPackage;
import com.pdp.domainconfig.port.DomainPackageRepository;
import com.pdp.shared.concurrency.ConcurrencyConflictException;
import com.pdp.shared.concurrency.Revision;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public final class MysqlDomainPackageRepository implements DomainPackageRepository {

  private final DomainPackageMapper mapper;

  public MysqlDomainPackageRepository(DomainPackageMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<DomainPackage> findById(UUID id) {
    return Optional.ofNullable(mapper.findById(id)).map(DomainPackageRow::toDomain);
  }

  @Override
  public Optional<DomainPackage> findByStableKey(UUID workspaceId, String stableKey) {
    return Optional.ofNullable(mapper.findByStableKey(workspaceId, stableKey))
        .map(DomainPackageRow::toDomain);
  }

  @Override
  public List<DomainPackage> findByWorkspace(UUID workspaceId) {
    return mapper.findByWorkspace(workspaceId).stream().map(DomainPackageRow::toDomain).toList();
  }

  @Override
  public DomainPackage save(DomainPackage domainPackage) {
    DomainPackageRow row = DomainPackageRow.fromDomain(domainPackage);
    if (mapper.update(row) == 0) {
      DomainPackageRow current = mapper.findById(row.id());
      if (current != null) {
        throw new ConcurrencyConflictException(
            new Revision(Math.max(0, row.revision() - 1)), new Revision(current.revision()));
      }
      mapper.insert(row);
    }
    return mapper.findById(row.id()).toDomain();
  }
}
