package com.pdp.domainconfig.domain.behavior;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RuleDefinition(
    String stableKey,
    String event,
    Map<String, Object> condition,
    List<ActionDefinition> actions,
    ExecutionIdentity executionIdentity,
    Mode mode) {
  public enum ExecutionIdentity {
    CURRENT_USER,
    PACKAGE_SERVICE_ACCOUNT
  }

  public enum Mode {
    SYNCHRONOUS,
    ASYNCHRONOUS
  }

  public RuleDefinition {
    condition = Map.copyOf(condition == null ? Map.of() : condition);
    actions = List.copyOf(actions == null ? List.of() : actions);
  }
}
