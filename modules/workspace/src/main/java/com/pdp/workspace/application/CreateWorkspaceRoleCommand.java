package com.pdp.workspace.application;

import java.util.Set;

public record CreateWorkspaceRoleCommand(
    String stableKey, String name, Set<String> allowedActions) {
  public CreateWorkspaceRoleCommand {
    allowedActions = Set.copyOf(allowedActions);
  }
}
