package com.pdp.mysql.workspace;

import com.pdp.shared.concurrency.Revision;
import com.pdp.workspace.domain.OrganizationUnit;
import java.util.UUID;

public record OrganizationUnitRow(
    UUID id,
    UUID workspaceId,
    UUID parentId,
    String code,
    String name,
    OrganizationUnit.Type type,
    String regionCode,
    String path,
    OrganizationUnit.Status status,
    long revision) {

  static OrganizationUnitRow fromDomain(OrganizationUnit organization) {
    return new OrganizationUnitRow(
        organization.id(),
        organization.workspaceId(),
        organization.parentId(),
        organization.code(),
        organization.name(),
        organization.type(),
        organization.regionCode(),
        organization.path(),
        organization.status(),
        organization.revision().value());
  }

  OrganizationUnit toDomain() {
    return new OrganizationUnit(
        id,
        workspaceId,
        parentId,
        code,
        name,
        type,
        regionCode,
        path,
        status,
        new Revision(revision));
  }
}
