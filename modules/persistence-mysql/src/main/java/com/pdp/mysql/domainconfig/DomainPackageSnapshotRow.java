package com.pdp.mysql.domainconfig;

import com.pdp.domainconfig.domain.metamodel.DomainPackageSnapshot;
import com.pdp.persistence.type.JsonDocument;
import java.time.Instant;
import java.util.UUID;

public record DomainPackageSnapshotRow(
    UUID id,
    UUID packageVersionId,
    JsonDocument layerChain,
    JsonDocument snapshot,
    String contentHash,
    Instant createdAt) {

  static DomainPackageSnapshotRow fromDomain(DomainPackageSnapshot snapshot, Instant createdAt) {
    return new DomainPackageSnapshotRow(
        snapshot.id(),
        snapshot.packageVersionId(),
        DomainConfigJsonCodec.write(snapshot.layers()),
        DomainConfigJsonCodec.write(snapshot),
        snapshot.contentHash(),
        createdAt);
  }
}
