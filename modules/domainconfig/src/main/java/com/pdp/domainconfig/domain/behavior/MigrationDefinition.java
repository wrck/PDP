package com.pdp.domainconfig.domain.behavior;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MigrationDefinition(
    String fromVersion,
    String toVersion,
    List<MigrationStep> steps,
    Map<String, Object> rollback) {
  public enum RollbackType {
    RESTORE_SNAPSHOT,
    REVERSE_STEPS,
    CUSTOM_EXTENSION
  }

  public record MigrationStep(String type, Map<String, Object> parameters) {
    public MigrationStep {
      parameters = Map.copyOf(parameters);
    }
  }

  public MigrationDefinition {
    steps = List.copyOf(steps == null ? List.of() : steps);
    rollback = Map.copyOf(rollback == null ? Map.of() : rollback);
  }

  public RollbackType rollbackType() {
    Object type = rollback.get("type");
    return type == null ? null : RollbackType.valueOf(type.toString());
  }
}
