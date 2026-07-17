package com.pdp.domainconfig.domain.metamodel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FieldDefinition(
    String stableKey,
    Object label,
    String dataType,
    Boolean required,
    Boolean sensitive,
    String semanticName,
    String dataSource) {

  public FieldDefinition {
    required = Boolean.TRUE.equals(required);
    sensitive = Boolean.TRUE.equals(sensitive);
    semanticName =
        semanticName == null || semanticName.isBlank()
            ? (label == null ? stableKey : label.toString())
            : semanticName;
    dataSource = dataSource == null || dataSource.isBlank() ? stableKey : dataSource;
  }

  public FieldDefinition(
      String stableKey,
      String semanticName,
      String dataType,
      String dataSource,
      boolean required,
      boolean sensitive) {
    this(stableKey, semanticName, dataType, required, sensitive, semanticName, dataSource);
  }
}
