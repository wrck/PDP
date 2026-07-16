package com.pdp.domainconfig.domain.behavior;

public record OverrideDefinition(
    String targetStableKey,
    String propertyPath,
    Object oldValue,
    Object newValue,
    String reason,
    String responsibleActor,
    String applicableVersion) {}
