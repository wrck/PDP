package com.pdp.workspace.application;

import com.pdp.shared.concurrency.OptimisticConcurrencyGuard;
import com.pdp.shared.concurrency.Revision;
import com.pdp.workspace.domain.DataScope;
import com.pdp.workspace.domain.OrganizationUnit;
import com.pdp.workspace.domain.Workspace;
import com.pdp.workspace.domain.WorkspaceMembership;
import com.pdp.workspace.domain.WorkspaceRole;
import com.pdp.workspace.port.DataScopeRepository;
import com.pdp.workspace.port.OrganizationUnitRepository;
import com.pdp.workspace.port.WorkspaceArchiveReadinessPort;
import com.pdp.workspace.port.WorkspaceMembershipRepository;
import com.pdp.workspace.port.WorkspaceRepository;
import com.pdp.workspace.port.WorkspaceRoleRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** 工作空间、组织、成员、角色和数据范围的统一治理服务。 */
@Service
public final class WorkspaceGovernanceService {
  private final WorkspaceRepository workspaces;
  private final OrganizationUnitRepository organizations;
  private final WorkspaceMembershipRepository memberships;
  private final WorkspaceRoleRepository roles;
  private final DataScopeRepository dataScopes;
  private final WorkspaceArchiveReadinessPort archiveReadiness;
  private final Clock clock;

  public WorkspaceGovernanceService(
      WorkspaceRepository workspaces,
      OrganizationUnitRepository organizations,
      WorkspaceMembershipRepository memberships,
      WorkspaceRoleRepository roles,
      DataScopeRepository dataScopes,
      WorkspaceArchiveReadinessPort archiveReadiness,
      Clock clock) {
    this.workspaces = workspaces;
    this.organizations = organizations;
    this.memberships = memberships;
    this.roles = roles;
    this.dataScopes = dataScopes;
    this.archiveReadiness = archiveReadiness;
    this.clock = clock;
  }

  public Workspace createWorkspace(CreateWorkspaceCommand command) {
    if (workspaces.findByCode(command.code()).isPresent()) {
      throw new IllegalStateException("工作空间编码已存在");
    }
    String locale = command.defaultLocale() == null ? "zh-CN" : command.defaultLocale();
    String timezone =
        command.defaultTimezone() == null ? "Asia/Shanghai" : command.defaultTimezone();
    var now = clock.instant();
    return workspaces.save(
        Workspace.draft(
            UUID.randomUUID(),
            command.code(),
            command.name(),
            command.ownerUserId(),
            locale,
            timezone,
            now));
  }

  public Workspace requireWorkspace(UUID workspaceId) {
    return workspaces
        .findById(workspaceId)
        .orElseThrow(() -> new IllegalArgumentException("工作空间不存在"));
  }

  public List<Workspace> listAccessible(UUID userId) {
    return workspaces.findAccessibleByUserId(userId);
  }

  public Workspace applyAction(
      UUID workspaceId, WorkspaceAction action, String reason, Revision expectedRevision) {
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("状态迁移原因不能为空");
    }
    var current = requireWorkspace(workspaceId);
    OptimisticConcurrencyGuard.requireMatch(expectedRevision, current.revision());
    var next =
        switch (action) {
          case ACTIVATE -> current.activate(clock.instant());
          case SUSPEND -> current.suspend(clock.instant());
          case ARCHIVE -> {
            if (!archiveReadiness.canArchive(workspaceId)) {
              throw new IllegalStateException("存在活动项目、迁移或协作授权，归档被阻断");
            }
            yield current.archive(clock.instant());
          }
        };
    return workspaces.save(next);
  }

  public OrganizationUnit createOrganizationUnit(
      UUID workspaceId, CreateOrganizationUnitCommand command) {
    requireActiveWorkspace(workspaceId);
    if (organizations.findByWorkspaceAndCode(workspaceId, command.code()).isPresent()) {
      throw new IllegalStateException("组织编码在工作空间内已存在");
    }
    OrganizationUnit parent =
        command.parentId() == null
            ? null
            : organizations
                .findOrganizationById(command.parentId())
                .orElseThrow(() -> new IllegalArgumentException("父组织不存在"));
    return organizations.save(
        OrganizationUnit.create(
            workspaceId,
            parent,
            command.code(),
            command.name(),
            command.type(),
            command.regionCode()));
  }

  public List<OrganizationUnit> listOrganizationUnits(UUID workspaceId) {
    requireWorkspace(workspaceId);
    return organizations.findOrganizationsByWorkspaceId(workspaceId);
  }

  public WorkspaceRole createRole(UUID workspaceId, CreateWorkspaceRoleCommand command) {
    requireActiveOrDraftWorkspace(workspaceId);
    if (roles.findRoleByStableKey(workspaceId, command.stableKey()).isPresent()) {
      throw new IllegalStateException("角色稳定键已存在");
    }
    return roles.save(
        WorkspaceRole.create(
            workspaceId, command.stableKey(), command.name(), command.allowedActions()));
  }

  public List<WorkspaceRole> listRoles(UUID workspaceId) {
    requireWorkspace(workspaceId);
    return roles.findRolesByWorkspaceId(workspaceId);
  }

  public DataScope createDataScope(UUID workspaceId, CreateDataScopeCommand command) {
    requireActiveOrDraftWorkspace(workspaceId);
    if (dataScopes.findDataScopeByStableKey(workspaceId, command.stableKey()).isPresent()) {
      throw new IllegalStateException("数据范围稳定键已存在");
    }
    return dataScopes.save(
        DataScope.create(
            workspaceId,
            command.stableKey(),
            command.name(),
            command.resourceTypes(),
            command.condition()));
  }

  public WorkspaceMembership addMember(UUID workspaceId, AddWorkspaceMemberCommand command) {
    requireActiveWorkspace(workspaceId);
    if (memberships.findByWorkspaceAndUser(workspaceId, command.userId()).isPresent()) {
      throw new IllegalStateException("用户已是工作空间成员");
    }
    if (command.organizationUnitId() != null) {
      var organization =
          organizations
              .findOrganizationById(command.organizationUnitId())
              .orElseThrow(() -> new IllegalArgumentException("组织不存在"));
      if (!organization.workspaceId().equals(workspaceId)) {
        throw new IllegalArgumentException("成员组织必须属于当前工作空间");
      }
    }
    command.roleIds().forEach(
        roleId -> {
          var role =
              roles.findRoleById(roleId).orElseThrow(() -> new IllegalArgumentException("角色不存在"));
          if (!role.workspaceId().equals(workspaceId)) {
            throw new IllegalArgumentException("角色必须属于当前工作空间");
          }
        });
    command.dataScopeIds().forEach(
        scopeId -> {
          var scope =
              dataScopes
                  .findDataScopeById(scopeId)
                  .orElseThrow(() -> new IllegalArgumentException("数据范围不存在"));
          if (!scope.workspaceId().equals(workspaceId)) {
            throw new IllegalArgumentException("数据范围必须属于当前工作空间");
          }
        });
    return memberships.save(
        WorkspaceMembership.active(
            workspaceId,
            command.userId(),
            command.organizationUnitId(),
            command.membershipType(),
            clock.instant(),
            command.validUntil(),
            command.roleIds(),
            command.dataScopeIds()));
  }

  public List<WorkspaceMembership> listMembers(UUID workspaceId) {
    requireWorkspace(workspaceId);
    return memberships.findMembershipsByWorkspaceId(workspaceId);
  }

  private void requireActiveWorkspace(UUID workspaceId) {
    if (requireWorkspace(workspaceId).status() != Workspace.Status.ACTIVE) {
      throw new IllegalStateException("工作空间不是活动状态");
    }
  }

  private void requireActiveOrDraftWorkspace(UUID workspaceId) {
    var status = requireWorkspace(workspaceId).status();
    if (status != Workspace.Status.ACTIVE && status != Workspace.Status.DRAFT) {
      throw new IllegalStateException("当前工作空间状态不允许配置治理对象");
    }
  }
}
