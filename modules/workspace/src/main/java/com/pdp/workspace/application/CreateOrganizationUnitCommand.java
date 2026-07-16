package com.pdp.workspace.application;

import com.pdp.workspace.domain.OrganizationUnit;
import java.util.UUID;

public record CreateOrganizationUnitCommand(
    UUID parentId, String code, String name, OrganizationUnit.Type type, String regionCode) {}
