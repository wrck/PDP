package com.pdp.workspace.application;

import java.util.Map;
import java.util.Set;

public record CreateDataScopeCommand(
    String stableKey, String name, Set<String> resourceTypes, Map<String, Object> condition) {
  public CreateDataScopeCommand {
    resourceTypes = Set.copyOf(resourceTypes);
    condition = Map.copyOf(condition);
  }
}
