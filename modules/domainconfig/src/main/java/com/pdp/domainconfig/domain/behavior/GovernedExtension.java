package com.pdp.domainconfig.domain.behavior;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GovernedExtension(
    String stableKey,
    String artifact,
    String entrypoint,
    Set<String> permissions,
    int timeoutMs,
    Isolation isolation,
    String signature) {
  public enum Isolation {
    PROCESS,
    CONTAINER,
    REMOTE_SERVICE
  }

  public GovernedExtension {
    permissions = Set.copyOf(permissions == null ? Set.of() : permissions);
  }
}
