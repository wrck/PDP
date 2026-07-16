package com.pdp.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pdp.workspace.application.CollaborationGrantService;
import com.pdp.workspace.application.TargetReference;
import com.pdp.workspace.application.WorkspaceIsolationException;
import com.pdp.workspace.application.WorkspaceIsolationGuard;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkspaceIsolationSecurityTest {

  @Test
  void 工作空间隔离守卫必须统一覆盖查询搜索导出和附件动作() {
    var ownerWorkspaceId = UUID.randomUUID();
    var collaboratorWorkspaceId = UUID.randomUUID();
    var target = new TargetReference("project", UUID.randomUUID());
    var grants = mock(CollaborationGrantService.class);
    var guard = new WorkspaceIsolationGuard(grants);

    when(grants.isAllowed(ownerWorkspaceId, collaboratorWorkspaceId, target, "project.query"))
        .thenReturn(true);
    when(grants.isAllowed(ownerWorkspaceId, collaboratorWorkspaceId, target, "project.search"))
        .thenReturn(true);

    assertThatCode(
            () ->
                guard.requireAccess(
                    ownerWorkspaceId, collaboratorWorkspaceId, target, "project.query"))
        .doesNotThrowAnyException();
    assertThatCode(
            () ->
                guard.requireAccess(
                    ownerWorkspaceId, collaboratorWorkspaceId, target, "project.search"))
        .doesNotThrowAnyException();

    assertThatThrownBy(
            () ->
                guard.requireAccess(
                    ownerWorkspaceId, collaboratorWorkspaceId, target, "project.export"))
        .isInstanceOf(WorkspaceIsolationException.class);
    assertThatThrownBy(
            () ->
                guard.requireAccess(
                    ownerWorkspaceId,
                    collaboratorWorkspaceId,
                    target,
                    "attachment.download"))
        .isInstanceOf(WorkspaceIsolationException.class);
  }
}
