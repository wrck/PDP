package com.pdp.domainconfig.port;

import com.pdp.domainconfig.domain.packageversion.DomainPackage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DomainPackageRepository {
  Optional<DomainPackage> findById(UUID id);

  Optional<DomainPackage> findByStableKey(UUID workspaceId, String stableKey);

  List<DomainPackage> findByWorkspace(UUID workspaceId);

  DomainPackage save(DomainPackage domainPackage);
}
