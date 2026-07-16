package com.pdp.domainconfig.domain.metamodel;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DomainPackageSnapshot(
    UUID id,
    List<String> layers,
    Map<String, ObjectDefinition> objects,
    String contentHash) {
  public DomainPackageSnapshot {
    layers = List.copyOf(layers);
    objects = Map.copyOf(objects);
  }
}
