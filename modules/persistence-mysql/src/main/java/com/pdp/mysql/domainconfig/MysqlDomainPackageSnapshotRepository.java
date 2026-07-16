package com.pdp.mysql.domainconfig;

import com.pdp.domainconfig.domain.metamodel.DomainPackageSnapshot;
import com.pdp.domainconfig.port.DomainPackageSnapshotRepository;
import java.time.Instant;
import org.springframework.stereotype.Repository;

@Repository
public final class MysqlDomainPackageSnapshotRepository
    implements DomainPackageSnapshotRepository {

  private final DomainPackageSnapshotMapper mapper;

  public MysqlDomainPackageSnapshotRepository(DomainPackageSnapshotMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public DomainPackageSnapshot save(DomainPackageSnapshot snapshot) {
    mapper.insert(DomainPackageSnapshotRow.fromDomain(snapshot, Instant.now()));
    return snapshot;
  }
}
