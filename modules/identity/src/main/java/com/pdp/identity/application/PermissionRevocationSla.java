package com.pdp.identity.application;

import java.time.Duration;

/** FR-068/FR-164 的权限撤销传播时限基线。 */
public record PermissionRevocationSla(
    Duration localCache,
    Duration searchProjection,
    Duration realtimeSession,
    Duration activeSession) {
  public static PermissionRevocationSla p1() {
    return new PermissionRevocationSla(
        Duration.ofSeconds(5),
        Duration.ofSeconds(30),
        Duration.ofSeconds(30),
        Duration.ofMinutes(1));
  }
}
