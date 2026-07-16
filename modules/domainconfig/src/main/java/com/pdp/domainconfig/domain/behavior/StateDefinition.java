package com.pdp.domainconfig.domain.behavior;

public record StateDefinition(
    String stableKey,
    Object label,
    TopLifecycleState topLifecycleState,
    boolean initial,
    boolean terminal) {}
