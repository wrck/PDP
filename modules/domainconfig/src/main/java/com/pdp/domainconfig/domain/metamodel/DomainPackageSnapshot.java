package com.pdp.domainconfig.domain.metamodel;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record DomainPackageSnapshot(
    UUID id,
    UUID packageVersionId,
    List<String> layers,
    Map<String, ObjectDefinition> objects,
    String contentHash) {
  public DomainPackageSnapshot {
    Objects.requireNonNull(id, "id 不能为空");
    Objects.requireNonNull(packageVersionId, "packageVersionId 不能为空");
    layers = List.copyOf(layers);
    objects = Map.copyOf(objects);
  }
}
