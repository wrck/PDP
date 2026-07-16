package com.pdp.mysql.domainconfig;

import com.pdp.domainconfig.domain.packageversion.DomainPackageVersion;
import com.pdp.domainconfig.port.DomainPackageVersionRepository;
import com.pdp.shared.concurrency.ConcurrencyConflictException;
import com.pdp.shared.concurrency.Revision;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public final class MysqlDomainPackageVersionRepository
    implements DomainPackageVersionRepository {

  private final DomainPackageVersionMapper mapper;

  public MysqlDomainPackageVersionRepository(DomainPackageVersionMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<DomainPackageVersion> findVersionById(UUID id) {
    return Optional.ofNullable(mapper.findById(id)).map(DomainPackageVersionRow::toDomain);
  }

  @Override
  public Optional<DomainPackageVersion> findByPackageAndSemanticVersion(
      UUID packageId, String semanticVersion) {
    return Optional.ofNullable(mapper.findByPackageAndSemanticVersion(packageId, semanticVersion))
        .map(DomainPackageVersionRow::toDomain);
  }

  @Override
  public List<DomainPackageVersion> findVersionsByPackageId(UUID packageId) {
    return mapper.findByPackageId(packageId).stream()
        .map(DomainPackageVersionRow::toDomain)
        .toList();
  }

  @Override
  public DomainPackageVersion save(DomainPackageVersion version) {
    DomainPackageVersionRow row = DomainPackageVersionRow.fromDomain(version);
    if (mapper.update(row) == 0) {
      DomainPackageVersionRow current = mapper.findById(row.id());
      if (current != null) {
        throw new ConcurrencyConflictException(
            new Revision(Math.max(0, row.revision() - 1)), new Revision(current.revision()));
      }
      mapper.insert(row);
    }
    return mapper.findById(row.id()).toDomain();
  }
}
