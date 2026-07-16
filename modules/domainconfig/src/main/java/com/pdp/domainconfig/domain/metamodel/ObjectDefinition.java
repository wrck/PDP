package com.pdp.domainconfig.domain.metamodel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pdp.domainconfig.domain.behavior.StateDefinition;
import com.pdp.domainconfig.domain.behavior.TransitionDefinition;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ObjectDefinition(
    String stableKey,
    Kind kind,
    String coreObjectType,
    List<FieldDefinition> fields,
    List<RelationDefinition> relations,
    List<PageDefinition> pages,
    List<StateDefinition> states,
    List<TransitionDefinition> transitions) {
  public enum Kind {
    CORE_EXTENSION,
    NEW_OBJECT
  }

  public ObjectDefinition {
    fields = List.copyOf(fields == null ? List.of() : fields);
    relations = List.copyOf(relations == null ? List.of() : relations);
    pages = List.copyOf(pages == null ? List.of() : pages);
    states = List.copyOf(states == null ? List.of() : states);
    transitions = List.copyOf(transitions == null ? List.of() : transitions);
  }
}
