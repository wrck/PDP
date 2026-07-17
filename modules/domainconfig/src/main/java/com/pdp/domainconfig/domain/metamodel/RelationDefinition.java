package com.pdp.domainconfig.domain.metamodel;

public record RelationDefinition(
    String stableKey, String targetObjectKey, Cardinality cardinality, Boolean required, Ownership ownership) {
  public enum Cardinality {
    ONE_TO_ONE,
    ONE_TO_MANY,
    MANY_TO_ONE,
    MANY_TO_MANY
  }

  public enum Ownership {
    REFERENCE,
    AGGREGATE_CHILD
  }

  public RelationDefinition {
    required = Boolean.TRUE.equals(required);
  }
}
