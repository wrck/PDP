package com.pdp.contract.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pdp.project.domain.Project;
import com.pdp.project.domain.ProjectMember;
import com.pdp.project.domain.ProjectStage;
import com.pdp.shared.concurrency.Revision;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** 公共 DDL、Mapper 与领域不变量的轻量契约；MySQL 容器矩阵由阶段门禁统一执行。 */
class ProjectLifecycleDatabaseContractTest {
  private static final UUID ID = UUID.fromString("0188d2d1-0000-7000-8000-000000000001");
  private static final UUID WORKSPACE = UUID.fromString("0188d2d1-0000-7000-8000-000000000002");
  private static final UUID ACTOR = UUID.fromString("0188d2d1-0000-7000-8000-000000000003");

  @Test
  void lifecycleDdlAndMappersPreserveWorkspaceIsolationHierarchyAndRevision() throws Exception {
    String changelog = resource("db/changelog/common/040-project-lifecycle.xml");
    String projectMapper = resource("mapper/project/ProjectMapper.xml");
    String stageMapper = resource("mapper/project/ProjectStageMapper.xml");
    String memberMapper = resource("mapper/project/ProjectMemberMapper.xml");

    assertThat(changelog).contains("pdp_project", "pdp_project_stage", "pdp_project_member", "uk_project_workspace_no", "fk_project_parent", "revision");
    assertThat(projectMapper).contains("workspace_id", "WITH RECURSIVE", "AND revision=#{revision}-1").doesNotContain("SELECT *", "OFFSET");
    assertThat(stageMapper + memberMapper).contains("workspace_id", "AND revision=#{revision}-1").doesNotContain("SELECT *");
  }

  @Test
  void domainRejectsSelfParentInvalidMemberValidityAndNegativeStageSequence() {
    Instant now = Instant.parse("2026-07-17T08:00:00Z");
    assertThatThrownBy(() -> new Project(ID, WORKSPACE, ID, "P-001", "项目", "", "", ACTOR,
        Project.Priority.NORMAL, Project.Health.GREEN, Project.LifecycleState.PLANNING, null,
        Project.Status.ACTIVE, new Revision(0), now, now)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ProjectMember(ID, ID, ACTOR, "manager", now, now, "{}",
        ProjectMember.Source.WORKSPACE_MEMBERSHIP, new Revision(0))).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ProjectStage(ID, ID, "plan", "计划", Project.LifecycleState.PLANNING,
        ProjectStage.State.READY, ACTOR, -1, new Revision(0))).isInstanceOf(IllegalArgumentException.class);
  }

  private static String resource(String path) throws Exception {
    return new String(new ClassPathResource(path).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
  }
}
