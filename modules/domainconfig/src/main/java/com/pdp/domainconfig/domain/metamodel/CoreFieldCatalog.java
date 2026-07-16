package com.pdp.domainconfig.domain.metamodel;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class CoreFieldCatalog {
  private final List<CoreFieldDefinition> fields;

  public CoreFieldCatalog(Collection<CoreFieldDefinition> fields) {
    this.fields = List.copyOf(fields);
  }

  public Optional<CoreFieldDefinition> semanticConflict(FieldDefinition candidate) {
    return fields.stream()
        .filter(
            core ->
                core.stableKey().equals(candidate.stableKey())
                    || core.dataSource().equals(candidate.dataSource())
                    || core.semanticName().equalsIgnoreCase(candidate.semanticName())
                    || core.aliases().stream()
                        .anyMatch(alias -> alias.equalsIgnoreCase(candidate.semanticName())))
        .findFirst();
  }

  public List<CoreFieldDefinition> fields() {
    return fields;
  }
}
