package com.pdp.domainconfig.port;

import java.util.UUID;

@FunctionalInterface
public interface DomainPackageInstanceInventoryPort {
  long countInstances(UUID sourceVersionId, Object scope);
}
