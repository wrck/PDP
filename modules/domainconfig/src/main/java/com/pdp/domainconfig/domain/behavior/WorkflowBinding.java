package com.pdp.domainconfig.domain.behavior;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkflowBinding(
    String stableKey,
    String processDefinitionKey,
    String businessVersion,
    Trigger trigger,
    String objectTypeKey,
    String eventType,
    String authorizationPolicyKey,
    Map<String, String> variableMappings,
    MigrationPolicy instanceMigrationPolicy) {
  public enum Trigger {
    MANUAL,
    OBJECT_CREATED,
    STATE_TRANSITION,
    DOMAIN_EVENT
  }

  public enum MigrationPolicy {
    PINNED,
    MANUAL_REVIEW,
    BATCH_MIGRATABLE
  }

  public WorkflowBinding {
    variableMappings = Map.copyOf(variableMappings == null ? Map.of() : variableMappings);
  }
}
