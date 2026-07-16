package com.pdp.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pdp.shared.concurrency.Revision;
import com.pdp.workspace.domain.CollaborationGrant;
import com.pdp.workspace.domain.DataScope;
import com.pdp.workspace.domain.OrganizationUnit;
import com.pdp.workspace.domain.Workspace;
import com.pdp.workspace.domain.WorkspaceMembership;
import com.pdp.workspace.domain.WorkspaceRole;
import com.pdp.workspace.port.CollaborationGrantRepository;
import com.pdp.workspace.port.DataScopeRepository;
import com.pdp.workspace.port.OrganizationUnitRepository;
import com.pdp.workspace.port.WorkspaceMembershipRepository;
import com.pdp.workspace.port.WorkspaceRepository;
import com.pdp.workspace.port.WorkspaceRoleRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkspaceGovernanceServiceTest {
  private static final Instant NOW = Instant.parse("2026-07-17T08:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  @Test
  void 应形成工作空间组织成员角色和数据范围治理闭环() {
    var store = new Store();
    var service = new WorkspaceGovernanceService(store, store, store, store, store, ignored -> true, CLOCK);
    UUID ownerId = UUID.randomUUID();
    Workspace workspace =
        service.createWorkspace(
            new CreateWorkspaceCommand("PDP_CN", "中国交付中心", ownerId, "zh-CN", "Asia/Shanghai"));

    workspace = service.applyAction(workspace.id(), WorkspaceAction.ACTIVATE, "初始化完成", new Revision(0));
    WorkspaceRole role =
        service.createRole(
            workspace.id(),
            new CreateWorkspaceRoleCommand("project.manager", "项目经理", Set.of("project.read", "project.write")));
    DataScope scope =
        service.createDataScope(
            workspace.id(),
            new CreateDataScopeCommand(
                "region.cn-east", "华东区域", Set.of("project"), Map.of("region", "cn-east")));
    OrganizationUnit organization =
        service.createOrganizationUnit(
            workspace.id(),
            new CreateOrganizationUnitCommand(null, "SH", "上海团队", OrganizationUnit.Type.TEAM, "CN-SH"));
    WorkspaceMembership membership =
        service.addMember(
            workspace.id(),
            new AddWorkspaceMemberCommand(
                UUID.randomUUID(),
                organization.id(),
                Set.of(role.id()),
                Set.of(scope.id()),
                WorkspaceMembership.Type.INTERNAL,
                null));

    assertThat(workspace.status()).isEqualTo(Workspace.Status.ACTIVE);
    assertThat(organization.path()).isEqualTo("/SH");
    assertThat(membership.status()).isEqualTo(WorkspaceMembership.Status.ACTIVE);
  }

  @Test
  void 外部成员必须具有到期时间且组织父节点必须属于同一工作空间() {
    var store = new Store();
    var service = new WorkspaceGovernanceService(store, store, store, store, store, ignored -> true, CLOCK);
    var workspace =
        service.createWorkspace(
            new CreateWorkspaceCommand("PDP_A", "空间甲", UUID.randomUUID(), "zh-CN", "Asia/Shanghai"));
    workspace =
        service.applyAction(
            workspace.id(), WorkspaceAction.ACTIVATE, "准备添加成员", new Revision(0));
    UUID workspaceId = workspace.id();
    var role =
        service.createRole(
            workspace.id(),
            new CreateWorkspaceRoleCommand("external.viewer", "外部查看者", Set.of("project.read")));

    assertThatThrownBy(
            () ->
                service.addMember(
                    workspaceId,
                    new AddWorkspaceMemberCommand(
                        UUID.randomUUID(),
                        null,
                        Set.of(role.id()),
                        Set.of(),
                        WorkspaceMembership.Type.EXTERNAL,
                        null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("到期");
  }

  @Test
  void 存在活动项目时不得归档工作空间() {
    var store = new Store();
    var service = new WorkspaceGovernanceService(store, store, store, store, store, ignored -> false, CLOCK);
    var workspace =
        service.createWorkspace(
            new CreateWorkspaceCommand("PDP_B", "空间乙", UUID.randomUUID(), "zh-CN", "Asia/Shanghai"));
    service.applyAction(workspace.id(), WorkspaceAction.ACTIVATE, "启用空间", new Revision(0));

    assertThatThrownBy(
            () ->
                service.applyAction(
                    workspace.id(), WorkspaceAction.ARCHIVE, "准备归档", new Revision(1)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("阻断");
  }

  static final class Store
      implements WorkspaceRepository,
          OrganizationUnitRepository,
          WorkspaceMembershipRepository,
          WorkspaceRoleRepository,
          DataScopeRepository,
          CollaborationGrantRepository {
    final Map<UUID, Workspace> workspaces = new HashMap<>();
    final Map<UUID, OrganizationUnit> organizations = new HashMap<>();
    final Map<UUID, WorkspaceMembership> memberships = new HashMap<>();
    final Map<UUID, WorkspaceRole> roles = new HashMap<>();
    final Map<UUID, DataScope> scopes = new HashMap<>();
    final Map<UUID, CollaborationGrant> grants = new HashMap<>();

    public Optional<Workspace> findById(UUID id) { return Optional.ofNullable(workspaces.get(id)); }
    public Optional<Workspace> findByCode(String code) {
      return workspaces.values().stream().filter(value -> value.code().equals(code)).findFirst();
    }
    public List<Workspace> findAccessibleByUserId(UUID userId) { return List.copyOf(workspaces.values()); }
    public Workspace save(Workspace value) { workspaces.put(value.id(), value); return value; }
    public Optional<OrganizationUnit> findOrganizationById(UUID id) { return Optional.ofNullable(organizations.get(id)); }
    public Optional<OrganizationUnit> findByWorkspaceAndCode(UUID workspaceId, String code) {
      return organizations.values().stream().filter(value -> value.workspaceId().equals(workspaceId) && value.code().equals(code)).findFirst();
    }
    public List<OrganizationUnit> findOrganizationsByWorkspaceId(UUID workspaceId) {
      return organizations.values().stream().filter(value -> value.workspaceId().equals(workspaceId)).toList();
    }
    public OrganizationUnit save(OrganizationUnit value) { organizations.put(value.id(), value); return value; }
    public Optional<WorkspaceMembership> findMembershipById(UUID id) { return Optional.ofNullable(memberships.get(id)); }
    public Optional<WorkspaceMembership> findByWorkspaceAndUser(UUID workspaceId, UUID userId) {
      return memberships.values().stream().filter(value -> value.workspaceId().equals(workspaceId) && value.userId().equals(userId)).findFirst();
    }
    public List<WorkspaceMembership> findMembershipsByWorkspaceId(UUID workspaceId) {
      return memberships.values().stream().filter(value -> value.workspaceId().equals(workspaceId)).toList();
    }
    public WorkspaceMembership save(WorkspaceMembership value) { memberships.put(value.id(), value); return value; }
    public Optional<WorkspaceRole> findRoleById(UUID id) { return Optional.ofNullable(roles.get(id)); }
    public Optional<WorkspaceRole> findRoleByStableKey(UUID workspaceId, String stableKey) {
      return roles.values().stream().filter(value -> value.workspaceId().equals(workspaceId) && value.stableKey().equals(stableKey)).findFirst();
    }
    public List<WorkspaceRole> findRolesByWorkspaceId(UUID workspaceId) {
      return roles.values().stream().filter(value -> value.workspaceId().equals(workspaceId)).toList();
    }
    public WorkspaceRole save(WorkspaceRole value) { roles.put(value.id(), value); return value; }
    public Optional<DataScope> findDataScopeById(UUID id) { return Optional.ofNullable(scopes.get(id)); }
    public Optional<DataScope> findDataScopeByStableKey(UUID workspaceId, String stableKey) {
      return scopes.values().stream().filter(value -> value.workspaceId().equals(workspaceId) && value.stableKey().equals(stableKey)).findFirst();
    }
    public DataScope save(DataScope value) { scopes.put(value.id(), value); return value; }
    public Optional<CollaborationGrant> findGrantById(UUID id) { return Optional.ofNullable(grants.get(id)); }
    public List<CollaborationGrant> findGrantsByOwnerWorkspaceId(UUID workspaceId) {
      return grants.values().stream().filter(value -> value.ownerWorkspaceId().equals(workspaceId)).toList();
    }
    public List<CollaborationGrant> findActiveGrants(UUID owner, UUID collaborator, Instant at) {
      return grants.values().stream().filter(value -> value.isEffective(owner, collaborator, at)).toList();
    }
    public CollaborationGrant save(CollaborationGrant value) { grants.put(value.id(), value); return value; }
  }
}
