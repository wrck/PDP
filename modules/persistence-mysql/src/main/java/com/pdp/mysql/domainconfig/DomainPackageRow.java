package com.pdp.mysql.domainconfig;

import com.pdp.domainconfig.domain.packageversion.DomainPackage;
import com.pdp.domainconfig.domain.packageversion.PackageLayer;
import com.pdp.shared.concurrency.Revision;
import java.util.UUID;

public record DomainPackageRow(
    UUID id,
    UUID workspaceId,
    String stableKey,
    String name,
    PackageLayer layer,
    UUID parentPackageId,
    DomainPackage.Status status,
    long revision) {

  static DomainPackageRow fromDomain(DomainPackage domainPackage) {
    return new DomainPackageRow(
        domainPackage.id(),
        domainPackage.workspaceId(),
        domainPackage.stableKey(),
        domainPackage.name(),
        domainPackage.layer(),
        domainPackage.parentPackageId(),
        domainPackage.status(),
        domainPackage.revision().value());
  }

  DomainPackage toDomain() {
    return new DomainPackage(
        id,
        workspaceId,
        stableKey,
        name,
        layer,
        parentPackageId,
        status,
        new Revision(revision));
  }
}
