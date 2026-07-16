package com.pdp.domainconfig.port;

import java.time.Instant;
import java.util.UUID;

@FunctionalInterface
public interface DomainPackageEventPublisher {
  void publish(DomainPackageEvent event);

  static DomainPackageEventPublisher noop() {
    return event -> {};
  }

  record DomainPackageEvent(
      String eventType, UUID packageId, UUID versionId, long revision, String reason, Instant at) {}
}
