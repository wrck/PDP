package com.pdp.domainconfig.domain.metamodel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ViewDefinition(
    String stableKey, String objectKey, @JsonProperty("viewType") Type viewType, List<String> columns) {
  public enum Type {
    LIST,
    BOARD,
    CALENDAR,
    TIMELINE,
    DETAIL
  }

  public ViewDefinition {
    columns = List.copyOf(columns == null ? List.of() : columns);
  }
}
