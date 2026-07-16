package com.pdp.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pdp.shared.context.ActorId;
import com.pdp.workspace.domain.CollaborationGrant;
import com.pdp.workspace.domain.Workspace;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CollaborationGrantServiceTest {
  private static final Instant NOW = Instant.parse("2026-07-17T08:00:00Z");

  @Test
  void 授权应限制平台保留动作并在撤销后立即失效() {
    var store = new WorkspaceGovernanceServiceTest.Store();
    var owner =
        store.save(
            Workspace.draft(
                    UUID.randomUUID(), "OWNER", "主空间", UUID.randomUUID(), "zh-CN", "Asia/Shanghai", NOW)
                .activate(NOW));
    var collaborator =
        store.save(
            Workspace.draft(
                    UUID.randomUUID(), "PARTNER", "协作空间", UUID.randomUUID(), "zh-CN", "Asia/Shanghai", NOW)
                .activate(NOW));
    var service =
        new CollaborationGrantService(
            store,
            store,
            event -> {},
            Clock.fixed(NOW, ZoneOffset.UTC));

    assertThatThrownBy(
            () ->
                service.grant(
                    owner.id(),
                    new ActorId(UUID.randomUUID()),
                    new CreateCollaborationGrantCommand(
                        collaborator.id(),
                        new TargetReference("project", UUID.randomUUID()),
                        UUID.randomUUID(),
                        Set.of("workspace.owner.change"),
                        NOW.plusSeconds(3600),
                        "跨空间合作")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("保留");

    var grant =
        service.grant(
            owner.id(),
            new ActorId(UUID.randomUUID()),
            new CreateCollaborationGrantCommand(
                collaborator.id(),
                new TargetReference("project", UUID.randomUUID()),
                UUID.randomUUID(),
                Set.of("project.read"),
                NOW.plusSeconds(3600),
                "跨空间查看"));
    var target = new TargetReference(grant.targetType(), grant.targetId());
    assertThat(service.isAllowed(owner.id(), collaborator.id(), target, "project.read")).isTrue();

    assertThatThrownBy(
            () ->
                service.revoke(
                    collaborator.id(),
                    grant.id(),
                    grant.revision(),
                    new ActorId(UUID.randomUUID()),
                    "错误空间撤销"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("不属于");

    service.revoke(
        owner.id(),
        grant.id(),
        grant.revision(),
        new ActorId(UUID.randomUUID()),
        "合作结束");
    assertThat(service.isAllowed(owner.id(), collaborator.id(), target, "project.read")).isFalse();

    var expiring =
        service.grant(
            owner.id(),
            new ActorId(UUID.randomUUID()),
            new CreateCollaborationGrantCommand(
                collaborator.id(),
                new TargetReference("project", UUID.randomUUID()),
                UUID.randomUUID(),
                Set.of("project.read"),
                NOW.plusSeconds(10),
                "短期协作"));
    var laterService =
        new CollaborationGrantService(
            store,
            store,
            event -> {},
            Clock.fixed(NOW.plusSeconds(20), ZoneOffset.UTC));

    assertThat(laterService.expireDue(owner.id())).isEqualTo(1);
    assertThat(store.findGrantById(expiring.id()).orElseThrow().status())
        .isEqualTo(CollaborationGrant.Status.EXPIRED);
  }
}
