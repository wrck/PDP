package com.pdp.workspace.application;

import com.pdp.shared.concurrency.OptimisticConcurrencyGuard;
import com.pdp.shared.concurrency.Revision;
import com.pdp.shared.context.ActorId;
import com.pdp.workspace.domain.CollaborationGrant;
import com.pdp.workspace.domain.Workspace;
import com.pdp.workspace.port.CollaborationGrantRepository;
import com.pdp.workspace.port.WorkspaceGovernanceEventPublisher;
import com.pdp.workspace.port.WorkspaceRepository;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** 显式、限时且可撤销的跨工作空间协作授权服务。 */
@Service
public final class CollaborationGrantService {
  private static final Set<String> RESERVED_ACTIONS =
      Set.of(
          "workspace.owner.change",
          "workspace.archive",
          "retention.policy.change",
          "template.core.transfer",
          "security.policy.change");

  private final WorkspaceRepository workspaces;
  private final CollaborationGrantRepository grants;
  private final WorkspaceGovernanceEventPublisher events;
  private final Clock clock;

  public CollaborationGrantService(
      WorkspaceRepository workspaces,
      CollaborationGrantRepository grants,
      WorkspaceGovernanceEventPublisher events,
      Clock clock) {
    this.workspaces = workspaces;
    this.grants = grants;
    this.events = events;
    this.clock = clock;
  }

  public CollaborationGrant grant(
      UUID ownerWorkspaceId, ActorId actorId, CreateCollaborationGrantCommand command) {
    Workspace owner = requireActiveWorkspace(ownerWorkspaceId);
    Workspace collaborator = requireActiveWorkspace(command.collaboratorWorkspaceId());
    if (!java.util.Collections.disjoint(command.allowedActions(), RESERVED_ACTIONS)) {
      throw new IllegalArgumentException("跨空间授权不得包含平台保留动作");
    }
    if (!command.validUntil().isAfter(clock.instant())) {
      throw new IllegalArgumentException("跨空间授权到期时间必须晚于当前时间");
    }
    var grant =
        CollaborationGrant.draft(
                owner.id(),
                collaborator.id(),
                command.target().objectType(),
                command.target().objectId(),
                command.roleId(),
                command.allowedActions(),
                clock.instant(),
                command.validUntil(),
                actorId.value())
            .activate();
    grant = grants.save(grant);
    events.publish(
        new WorkspaceGovernanceEvent(
            "pdp.workspace.collaboration-granted",
            ownerWorkspaceId,
            grant.id(),
            grant.revision().value(),
            command.reason(),
            clock.instant()));
    return grant;
  }

  public CollaborationGrant revoke(
      UUID ownerWorkspaceId,
      UUID grantId,
      Revision expectedRevision,
      ActorId actorId,
      String reason) {
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("撤销原因不能为空");
    }
    var current =
        grants
            .findGrantById(grantId)
            .orElseThrow(() -> new IllegalArgumentException("协作授权不存在"));
    if (!current.ownerWorkspaceId().equals(ownerWorkspaceId)) {
      throw new IllegalArgumentException("协作授权不属于当前工作空间");
    }
    OptimisticConcurrencyGuard.requireMatch(expectedRevision, current.revision());
    var revoked = grants.save(current.revoke(clock.instant(), reason));
    events.publish(
        new WorkspaceGovernanceEvent(
            "pdp.workspace.collaboration-revoked",
            revoked.ownerWorkspaceId(),
            revoked.id(),
            revoked.revision().value(),
            reason,
            clock.instant()));
    return revoked;
  }

  public List<CollaborationGrant> list(UUID ownerWorkspaceId) {
    workspaces.findById(ownerWorkspaceId).orElseThrow(() -> new IllegalArgumentException("工作空间不存在"));
    return grants.findGrantsByOwnerWorkspaceId(ownerWorkspaceId);
  }

  /** 供调度作业调用，将到期的活动授权固化为 EXPIRED 状态。 */
  public int expireDue(UUID ownerWorkspaceId) {
    requireActiveWorkspace(ownerWorkspaceId);
    int expiredCount = 0;
    for (var grant : grants.findGrantsByOwnerWorkspaceId(ownerWorkspaceId)) {
      if (grant.status() == CollaborationGrant.Status.ACTIVE
          && !clock.instant().isBefore(grant.validUntil())) {
        var expired = grants.save(grant.expire(clock.instant()));
        events.publish(
            new WorkspaceGovernanceEvent(
                "pdp.workspace.collaboration-expired",
                expired.ownerWorkspaceId(),
                expired.id(),
                expired.revision().value(),
                "协作授权到期",
                clock.instant()));
        expiredCount++;
      }
    }
    return expiredCount;
  }

  public boolean isAllowed(
      UUID ownerWorkspaceId,
      UUID collaboratorWorkspaceId,
      TargetReference target,
      String action) {
    return grants
        .findActiveGrants(ownerWorkspaceId, collaboratorWorkspaceId, clock.instant())
        .stream()
        .anyMatch(
            grant ->
                grant.targetType().equals(target.objectType())
                    && grant.targetId().equals(target.objectId())
                    && grant.allowedActions().contains(action));
  }

  private Workspace requireActiveWorkspace(UUID workspaceId) {
    var workspace =
        workspaces
            .findById(workspaceId)
            .orElseThrow(() -> new IllegalArgumentException("工作空间不存在"));
    if (workspace.status() != Workspace.Status.ACTIVE) {
      throw new IllegalStateException("工作空间不是活动状态");
    }
    return workspace;
  }
}
