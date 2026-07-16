package com.pdp.workspace.port;

import com.pdp.workspace.domain.OrganizationUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationUnitRepository {
  Optional<OrganizationUnit> findOrganizationById(UUID id);

  Optional<OrganizationUnit> findByWorkspaceAndCode(UUID workspaceId, String code);

  List<OrganizationUnit> findOrganizationsByWorkspaceId(UUID workspaceId);

  OrganizationUnit save(OrganizationUnit organizationUnit);
}
