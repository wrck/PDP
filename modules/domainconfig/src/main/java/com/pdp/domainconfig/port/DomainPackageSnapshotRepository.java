package com.pdp.domainconfig.port;

import com.pdp.domainconfig.domain.metamodel.DomainPackageSnapshot;

@FunctionalInterface
public interface DomainPackageSnapshotRepository {
  DomainPackageSnapshot save(DomainPackageSnapshot snapshot);
}
