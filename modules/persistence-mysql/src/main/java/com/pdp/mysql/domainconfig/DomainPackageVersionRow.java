package com.pdp.mysql.domainconfig;

import com.pdp.domainconfig.domain.metamodel.DomainPackageManifest;
import com.pdp.domainconfig.domain.packageversion.DomainPackageVersion;
import com.pdp.persistence.type.JsonDocument;
import com.pdp.shared.concurrency.Revision;
import java.time.Instant;
import java.util.UUID;

public record DomainPackageVersionRow(
    UUID id,
    UUID packageId,
    String semanticVersion,
    JsonDocument manifest,
    String contentHash,
    DomainPackageVersion.Status status,
    boolean frozen,
    UUID createdBy,
    UUID reviewedBy,
    UUID approvedBy,
    long revision,
    Instant createdAt) {

  static DomainPackageVersionRow fromDomain(DomainPackageVersion version) {
    return new DomainPackageVersionRow(
        version.id(),
        version.packageId(),
        version.semanticVersion(),
        DomainConfigJsonCodec.write(version.manifest()),
        version.contentHash(),
        version.status(),
        version.frozen(),
        version.createdBy(),
        version.reviewedBy(),
        version.approvedBy(),
        version.revision().value(),
        version.createdAt());
  }

  DomainPackageVersion toDomain() {
    return new DomainPackageVersion(
        id,
        packageId,
        semanticVersion,
        DomainConfigJsonCodec.read(manifest, DomainPackageManifest.class),
        contentHash,
        status,
        frozen,
        createdBy,
        reviewedBy,
        approvedBy,
        new Revision(revision),
        createdAt);
  }
}
