package com.pdp.domainconfig.domain.behavior;

public record StateDefinition(
    String stableKey,
    Object label,
    TopLifecycleState topLifecycleState,
    Boolean initial,
    Boolean terminal) {

  public StateDefinition {
    initial = Boolean.TRUE.equals(initial);
    terminal = Boolean.TRUE.equals(terminal);
  }
}
