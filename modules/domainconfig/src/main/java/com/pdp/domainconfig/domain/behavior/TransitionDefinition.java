package com.pdp.domainconfig.domain.behavior;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TransitionDefinition(
    String stableKey,
    String from,
    String to,
    String requiredPermission,
    String guardRuleKey,
    String workflowBindingKey,
    boolean controlledRollback) {}
