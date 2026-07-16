package com.pdp.domainconfig.port;

import com.pdp.domainconfig.domain.packageversion.DomainPackageVersion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DomainPackageVersionRepository {
  Optional<DomainPackageVersion> findVersionById(UUID id);

  Optional<DomainPackageVersion> findByPackageAndSemanticVersion(
      UUID packageId, String semanticVersion);

  List<DomainPackageVersion> findVersionsByPackageId(UUID packageId);

  DomainPackageVersion save(DomainPackageVersion version);
}
