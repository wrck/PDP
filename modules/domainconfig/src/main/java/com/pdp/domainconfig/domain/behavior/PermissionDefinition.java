package com.pdp.domainconfig.domain.behavior;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PermissionDefinition(
    String capabilityKey, String objectKey, Set<String> operations, Set<String> fieldKeys) {
  public PermissionDefinition {
    operations = Set.copyOf(operations == null ? Set.of() : operations);
    fieldKeys = Set.copyOf(fieldKeys == null ? Set.of() : fieldKeys);
  }
}
