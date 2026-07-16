package com.pdp.workspace.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pdp.shared.context.ActorId;
import com.pdp.workspace.domain.Workspace;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkspaceIsolationGuardTest {
  private static final Instant NOW = Instant.parse("2026-07-17T08:00:00Z");

  @Test
  void 查询搜索导出附件必须分别命中明确授权动作() {
    var store = new WorkspaceGovernanceServiceTest.Store();
    var owner =
        store.save(
            Workspace.draft(
                    UUID.randomUUID(), "A_SPACE", "空间甲", UUID.randomUUID(), "zh-CN", "Asia/Shanghai", NOW)
                .activate(NOW));
    var collaborator =
        store.save(
            Workspace.draft(
                    UUID.randomUUID(), "B_SPACE", "空间乙", UUID.randomUUID(), "zh-CN", "Asia/Shanghai", NOW)
                .activate(NOW));
    var grants =
        new CollaborationGrantService(
            store, store, event -> {}, Clock.fixed(NOW, ZoneOffset.UTC));
    var target = new TargetReference("project", UUID.randomUUID());
    grants.grant(
        owner.id(),
        new ActorId(UUID.randomUUID()),
        new CreateCollaborationGrantCommand(
            collaborator.id(),
            target,
            UUID.randomUUID(),
            Set.of("project.query", "project.search"),
            NOW.plusSeconds(3600),
            "只读协作"));
    var guard = new WorkspaceIsolationGuard(grants);

    assertThatCode(
            () -> guard.requireAccess(owner.id(), collaborator.id(), target, "project.query"))
        .doesNotThrowAnyException();
    assertThatCode(
            () -> guard.requireAccess(owner.id(), collaborator.id(), target, "project.search"))
        .doesNotThrowAnyException();
    assertThatThrownBy(
            () -> guard.requireAccess(owner.id(), collaborator.id(), target, "project.export"))
        .isInstanceOf(WorkspaceIsolationException.class);
    assertThatThrownBy(
            () -> guard.requireAccess(owner.id(), collaborator.id(), target, "attachment.download"))
        .isInstanceOf(WorkspaceIsolationException.class);
  }
}
