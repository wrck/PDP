package com.pdp.domainconfig.domain.behavior;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ActionDefinition(Type type, Map<String, Object> parameters) {
  public enum Type {
    SET_FIELD,
    CREATE_OBJECT,
    TRANSITION,
    SUBMIT_APPROVAL,
    NOTIFY,
    CALL_EXTENSION,
    EMIT_EVENT
  }

  public ActionDefinition {
    parameters = Map.copyOf(parameters == null ? Map.of() : parameters);
  }
}
