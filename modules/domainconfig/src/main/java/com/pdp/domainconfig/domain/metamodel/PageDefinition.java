package com.pdp.domainconfig.domain.metamodel;

import java.util.Map;

public record PageDefinition(
    String stableKey, String objectKey, Map<String, Object> layout, String visibilityRuleKey) {
  public PageDefinition {
    layout = Map.copyOf(layout);
  }
}
