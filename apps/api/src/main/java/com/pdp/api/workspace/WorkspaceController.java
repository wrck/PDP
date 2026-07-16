package com.pdp.api.workspace;

import com.pdp.identity.application.AuthenticatedActor;
import com.pdp.shared.concurrency.EntityTag;
import com.pdp.shared.context.ActorId;
import com.pdp.workspace.application.AddWorkspaceMemberCommand;
import com.pdp.workspace.application.CollaborationGrantService;
import com.pdp.workspace.application.CreateCollaborationGrantCommand;
import com.pdp.workspace.application.CreateDataScopeCommand;
import com.pdp.workspace.application.CreateOrganizationUnitCommand;
import com.pdp.workspace.application.CreateWorkspaceCommand;
import com.pdp.workspace.application.CreateWorkspaceRoleCommand;
import com.pdp.workspace.application.TargetReference;
import com.pdp.workspace.application.WorkspaceAction;
import com.pdp.workspace.application.WorkspaceGovernanceService;
import com.pdp.workspace.domain.CollaborationGrant;
import com.pdp.workspace.domain.DataScope;
import com.pdp.workspace.domain.OrganizationUnit;
import com.pdp.workspace.domain.Workspace;
import com.pdp.workspace.domain.WorkspaceMembership;
import com.pdp.workspace.domain.WorkspaceRole;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces")
public final class WorkspaceController {
  private final WorkspaceGovernanceService governance;
  private final CollaborationGrantService collaborationGrants;

  public WorkspaceController(
      WorkspaceGovernanceService governance, CollaborationGrantService collaborationGrants) {
    this.governance = governance;
    this.collaborationGrants = collaborationGrants;
  }

  @GetMapping
  public List<Workspace> list(Authentication authentication) {
    return governance.listAccessible(actorId(authentication).value());
  }

  @PostMapping
  public ResponseEntity<Workspace> create(@RequestBody CreateWorkspaceRequest request) {
    Workspace created =
        governance.createWorkspace(
            new CreateWorkspaceCommand(
                request.code(),
                request.name(),
                request.ownerUserId(),
                request.defaultLocale(),
                request.defaultTimezone()));
    return ResponseEntity.created(URI.create("/api/v1/workspaces/" + created.id()))
        .eTag(EntityTag.from(created.revision()).value())
        .body(created);
  }

  @GetMapping("/{workspaceId}")
  public ResponseEntity<Workspace> get(@PathVariable UUID workspaceId) {
    Workspace workspace = governance.requireWorkspace(workspaceId);
    return ResponseEntity.ok()
        .eTag(EntityTag.from(workspace.revision()).value())
        .body(workspace);
  }

  @PostMapping("/{workspaceId}/actions")
  public ResponseEntity<Workspace> action(
      @PathVariable UUID workspaceId,
      @RequestHeader("If-Match") String ifMatch,
      @RequestBody WorkspaceActionRequest request) {
    Workspace workspace =
        governance.applyAction(
            workspaceId,
            request.action(),
            request.reason(),
            EntityTag.parse(ifMatch).revision());
    return ResponseEntity.ok()
        .eTag(EntityTag.from(workspace.revision()).value())
        .body(workspace);
  }

  @GetMapping("/{workspaceId}/organizations")
  public List<OrganizationUnit> organizations(@PathVariable UUID workspaceId) {
    return governance.listOrganizationUnits(workspaceId);
  }

  @PostMapping("/{workspaceId}/organizations")
  public ResponseEntity<OrganizationUnit> createOrganization(
      @PathVariable UUID workspaceId, @RequestBody CreateOrganizationUnitRequest request) {
    var created =
        governance.createOrganizationUnit(
            workspaceId,
            new CreateOrganizationUnitCommand(
                request.parentId(),
                request.code(),
                request.name(),
                request.type(),
                request.regionCode()));
    return ResponseEntity.status(201).body(created);
  }

  @GetMapping("/{workspaceId}/members")
  public List<WorkspaceMembership> members(@PathVariable UUID workspaceId) {
    return governance.listMembers(workspaceId);
  }

  @PostMapping("/{workspaceId}/members")
  public ResponseEntity<WorkspaceMembership> addMember(
      @PathVariable UUID workspaceId, @RequestBody AddWorkspaceMemberRequest request) {
    var created =
        governance.addMember(
            workspaceId,
            new AddWorkspaceMemberCommand(
                request.userId(),
                request.organizationUnitId(),
                request.roleIds(),
                request.dataScopeIds(),
                request.membershipType(),
                request.validUntil()));
    return ResponseEntity.status(201).body(created);
  }

  @GetMapping("/{workspaceId}/roles")
  public List<WorkspaceRole> roles(@PathVariable UUID workspaceId) {
    return governance.listRoles(workspaceId);
  }

  @PostMapping("/{workspaceId}/roles")
  public ResponseEntity<WorkspaceRole> createRole(
      @PathVariable UUID workspaceId, @RequestBody CreateWorkspaceRoleRequest request) {
    return ResponseEntity.status(201)
        .body(
            governance.createRole(
                workspaceId,
                new CreateWorkspaceRoleCommand(
                    request.stableKey(), request.name(), request.allowedActions())));
  }

  @PostMapping("/{workspaceId}/data-scopes")
  public ResponseEntity<DataScope> createDataScope(
      @PathVariable UUID workspaceId, @RequestBody CreateDataScopeRequest request) {
    return ResponseEntity.status(201)
        .body(
            governance.createDataScope(
                workspaceId,
                new CreateDataScopeCommand(
                    request.stableKey(),
                    request.name(),
                    request.resourceTypes(),
                    request.condition())));
  }

  @GetMapping("/{workspaceId}/collaboration-grants")
  public List<CollaborationGrant> grants(@PathVariable UUID workspaceId) {
    return collaborationGrants.list(workspaceId);
  }

  @PostMapping("/{workspaceId}/collaboration-grants")
  public ResponseEntity<CollaborationGrant> grant(
      @PathVariable UUID workspaceId,
      Authentication authentication,
      @RequestBody CreateCollaborationGrantRequest request) {
    return ResponseEntity.status(201)
        .body(
            collaborationGrants.grant(
                workspaceId,
                actorId(authentication),
                new CreateCollaborationGrantCommand(
                    request.collaboratorWorkspaceId(),
                    request.target(),
                    request.roleId(),
                    request.allowedActions(),
                    request.validUntil(),
                    request.reason())));
  }

  @PostMapping("/{workspaceId}/collaboration-grants/{grantId}/revoke")
  public ResponseEntity<Void> revoke(
      @PathVariable UUID workspaceId,
      @PathVariable UUID grantId,
      @RequestHeader("If-Match") String ifMatch,
      Authentication authentication,
      @RequestBody ReasonRequest request) {
    collaborationGrants.revoke(
        workspaceId,
        grantId,
        EntityTag.parse(ifMatch).revision(),
        actorId(authentication),
        request.reason());
    return ResponseEntity.noContent().build();
  }

  private static ActorId actorId(Authentication authentication) {
    Object principal = authentication.getPrincipal();
    if (principal instanceof AuthenticatedActor actor) {
      return new ActorId(actor.userId());
    }
    return new ActorId(UUID.fromString(authentication.getName()));
  }

  public record CreateWorkspaceRequest(
      String code,
      String name,
      UUID ownerUserId,
      String defaultLocale,
      String defaultTimezone) {}

  public record WorkspaceActionRequest(WorkspaceAction action, String reason) {}

  public record CreateOrganizationUnitRequest(
      UUID parentId,
      String code,
      String name,
      OrganizationUnit.Type type,
      String regionCode) {}

  public record AddWorkspaceMemberRequest(
      UUID userId,
      UUID organizationUnitId,
      Set<UUID> roleIds,
      Set<UUID> dataScopeIds,
      WorkspaceMembership.Type membershipType,
      Instant validUntil) {}

  public record CreateWorkspaceRoleRequest(
      String stableKey, String name, Set<String> allowedActions) {}

  public record CreateDataScopeRequest(
      String stableKey,
      String name,
      Set<String> resourceTypes,
      Map<String, Object> condition) {}

  public record CreateCollaborationGrantRequest(
      UUID collaboratorWorkspaceId,
      TargetReference target,
      UUID roleId,
      Set<String> allowedActions,
      Instant validUntil,
      String reason) {}

  public record ReasonRequest(String reason) {}
}
