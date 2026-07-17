package com.pdp.template.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pdp.shared.concurrency.Revision;
import com.pdp.shared.context.IdempotencyKey;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProjectTemplateDomainTest {
  private static final Instant CREATED_AT = Instant.parse("2026-07-17T00:00:00Z");

  @Test
  void 规范化哈希不受集合顺序持久化标识和配置键顺序影响() {
    Map<String, Object> firstConfiguration = new LinkedHashMap<>();
    firstConfiguration.put("duration", 5.0);
    firstConfiguration.put("required", true);
    Map<String, Object> secondConfiguration = new LinkedHashMap<>();
    secondConfiguration.put("required", true);
    secondConfiguration.put("duration", 5);

    TemplateComponent firstStage =
        component("stage-design", TemplateComponentType.STAGE, 20, null, firstConfiguration);
    TemplateComponent firstTask =
        component("task-survey", TemplateComponentType.TASK, 10, "stage-design", Map.of());
    TemplateDefinition first = new TemplateDefinition(List.of(firstStage, firstTask));

    TemplateComponent secondTask =
        new TemplateComponent(
            UUID.randomUUID(),
            TemplateComponentType.TASK,
            "task-survey",
            "task-survey",
            10,
            "stage-design",
            Map.of(),
            new Revision(9));
    TemplateComponent secondStage =
        new TemplateComponent(
            UUID.randomUUID(),
            TemplateComponentType.STAGE,
            "stage-design",
            "stage-design",
            20,
            null,
            secondConfiguration,
            new Revision(3));
    TemplateDefinition second = new TemplateDefinition(List.of(secondTask, secondStage));

    assertThat(TemplateContentHash.from(first)).isEqualTo(TemplateContentHash.from(second));
  }

  @Test
  void 模板定义拒绝重复键悬空引用非法父类型和引用环() {
    TemplateComponent stage = component("stage-a", TemplateComponentType.STAGE, 0, null, Map.of());
    TemplateComponent duplicate = component("stage-a", TemplateComponentType.STAGE, 1, null, Map.of());
    TemplateComponent missingParent =
        component("task-a", TemplateComponentType.TASK, 0, "stage-missing", Map.of());
    TemplateComponent illegalParent =
        component("view-a", TemplateComponentType.VIEW, 0, "stage-a", Map.of());
    TemplateComponent cycleA =
        component("stage-cycle-a", TemplateComponentType.STAGE, 0, "stage-cycle-b", Map.of());
    TemplateComponent cycleB =
        component("stage-cycle-b", TemplateComponentType.STAGE, 1, "stage-cycle-a", Map.of());

    assertThatThrownBy(() -> new TemplateDefinition(List.of(stage, duplicate)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("重复");
    assertThatThrownBy(() -> new TemplateDefinition(List.of(stage, missingParent)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("不存在");
    assertThatThrownBy(() -> new TemplateDefinition(List.of(stage, illegalParent)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("类型非法");
    assertThatThrownBy(() -> new TemplateDefinition(List.of(cycleA, cycleB)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("存在环");
  }

  @Test
  void 模板版本只能按草稿冻结发布退役前进且冻结后内容不可替换() {
    UUID actor = UUID.randomUUID();
    TemplateDefinition definition =
        new TemplateDefinition(
            List.of(component("stage-design", TemplateComponentType.STAGE, 0, null, Map.of())));
    ProjectTemplateVersion draft =
        ProjectTemplateVersion.draft(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new SemanticVersion("1.0.0"),
            null,
            UUID.randomUUID(),
            definition,
            "首个标准版本",
            actor,
            CREATED_AT);

    assertThatThrownBy(() -> draft.publish(actor, CREATED_AT.plusSeconds(1)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("仅已冻结");

    ProjectTemplateVersion frozen = draft.freeze(actor, CREATED_AT.plusSeconds(1));
    ProjectTemplateVersion published = frozen.publish(actor, CREATED_AT.plusSeconds(2));
    ProjectTemplateVersion retired = published.retire(actor, CREATED_AT.plusSeconds(3));

    assertThat(frozen.contentHash()).isEqualTo(TemplateContentHash.from(definition));
    assertThat(published.contentHash()).isEqualTo(frozen.contentHash());
    assertThat(retired.status()).isEqualTo(ProjectTemplateVersion.Status.RETIRED);
    assertThat(retired.revision()).isEqualTo(new Revision(3));
    assertThatThrownBy(
            () ->
                frozen.revise(
                    UUID.randomUUID(), definition, "尝试覆盖冻结内容", CREATED_AT.plusSeconds(2)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("仅草稿");
    assertThatThrownBy(retired::requireInstantiable)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("只有已发布");
  }

  @Test
  void 可创建计划必须覆盖全部组件且完成记录固定计划快照和幂等请求() {
    ProjectTemplateVersion published = publishedVersion();
    ProjectInstantiationInput input =
        new ProjectInstantiationInput(
            "核心网割接",
            "在维护窗口内完成割接",
            UUID.randomUUID(),
            LocalDate.parse("2026-08-01"),
            LocalDate.parse("2026-08-10"),
            null,
            Map.of(),
            List.of(),
            Map.of());
    List<ProjectObjectBlueprint> completeBlueprints =
        published.definition().components().stream()
            .filter(component -> component.type().producesProjectObject())
            .map(
                component ->
                    new ProjectObjectBlueprint(
                        UUID.randomUUID(),
                        component.componentKey(),
                        component.type(),
                        component.configuration()))
            .toList();

    assertThatThrownBy(
            () ->
                ProjectInstantiationPlan.create(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    published,
                    input,
                    completeBlueprints.subList(0, completeBlueprints.size() - 1),
                    List.of(),
                    CREATED_AT.plusSeconds(5)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("缺少模板组件");

    UUID workspaceId = UUID.randomUUID();
    ProjectInstantiationPlan plan =
        ProjectInstantiationPlan.create(
            UUID.randomUUID(),
            workspaceId,
            published,
            input,
            completeBlueprints,
            List.of(),
            CREATED_AT.plusSeconds(5));
    List<GeneratedProjectObjectRef> generated =
        completeBlueprints.stream()
            .map(
                blueprint ->
                    new GeneratedProjectObjectRef(
                        blueprint.componentKey(), blueprint.objectType(), blueprint.objectId()))
            .toList();
    IdempotencyKey idempotencyKey = new IdempotencyKey("project-create-000001");
    ProjectInstantiationRecord record =
        ProjectInstantiationRecord.completedFromTemplate(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            idempotencyKey,
            plan,
            generated,
            CREATED_AT.plusSeconds(6));

    assertThat(record.workspaceId()).isEqualTo(workspaceId);
    assertThat(record.templateVersionId()).isEqualTo(published.id());
    assertThat(record.templateContentHash()).isEqualTo(published.contentHash());
    assertThat(record.generatedSummary()).isEqualTo(plan.generatedSummary());
    record.requireSameRequest(plan.requestDigest());
    assertThatThrownBy(
            () ->
                record.requireSameRequest(
                    new RequestDigest(
                        "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")))
        .isInstanceOf(IdempotencyConflictException.class);

    List<GeneratedProjectObjectRef> changed = new ArrayList<>(generated);
    GeneratedProjectObjectRef first = changed.getFirst();
    changed.set(
        0,
        new GeneratedProjectObjectRef(
            first.componentKey(), first.objectType(), UUID.randomUUID()));
    assertThatThrownBy(
            () ->
                ProjectInstantiationRecord.completedFromTemplate(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    idempotencyKey,
                    plan,
                    changed,
                    CREATED_AT.plusSeconds(7)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("已批准计划不一致");
  }

  @Test
  void 实例化输入拒绝反向日期重复负责人及未声明参数() {
    assertThatThrownBy(
            () ->
                new ProjectInstantiationInput(
                    "项目",
                    "",
                    UUID.randomUUID(),
                    LocalDate.parse("2026-08-10"),
                    LocalDate.parse("2026-08-01"),
                    null,
                    Map.of(),
                    List.of(),
                    Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("结束日期");

    OwnerAssignment assignment = new OwnerAssignment("owner.manager", UUID.randomUUID());
    assertThatThrownBy(
            () ->
                new ProjectInstantiationInput(
                    "项目",
                    "",
                    UUID.randomUUID(),
                    LocalDate.parse("2026-08-01"),
                    LocalDate.parse("2026-08-10"),
                    null,
                    Map.of(),
                    List.of(assignment, assignment),
                    Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("重复绑定");
  }

  private static ProjectTemplateVersion publishedVersion() {
    TemplateDefinition definition =
        new TemplateDefinition(
            List.of(
                component("stage-design", TemplateComponentType.STAGE, 0, null, Map.of()),
                component(
                    "task-survey",
                    TemplateComponentType.TASK,
                    0,
                    "stage-design",
                    Map.of()),
                component(
                    "checklist-ready",
                    TemplateComponentType.CHECKLIST_ITEM,
                    0,
                    "task-survey",
                    Map.of())));
    UUID actor = UUID.randomUUID();
    return ProjectTemplateVersion.draft(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new SemanticVersion("1.0.0"),
            null,
            UUID.randomUUID(),
            definition,
            "发布测试版本",
            actor,
            CREATED_AT)
        .freeze(actor, CREATED_AT.plusSeconds(1))
        .publish(actor, CREATED_AT.plusSeconds(2));
  }

  private static TemplateComponent component(
      String key,
      TemplateComponentType type,
      int sequence,
      String parentKey,
      Map<String, Object> configuration) {
    return new TemplateComponent(
        UUID.randomUUID(),
        type,
        key,
        key,
        sequence,
        parentKey,
        configuration,
        new Revision(0));
  }
}
