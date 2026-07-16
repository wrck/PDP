package com.pdp.domainconfig.domain.metamodel;

import java.util.Set;

public record CoreFieldDefinition(
    String stableKey,
    String objectType,
    String semanticName,
    String dataType,
    String dataSource,
    Set<String> aliases,
    boolean extensible) {
  public CoreFieldDefinition {
    aliases = Set.copyOf(aliases);
  }
}
