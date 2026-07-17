package com.pdp.domainconfig.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pdp.domainconfig.domain.behavior.WorkflowBinding;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkflowBindingServiceTest {

  @Test
  void 工作流绑定不得复制敏感事实且必须通过平台目录校验() {
    var service = new WorkflowBindingService(binding -> List.of("流程版本未部署"));
    var binding =
        new WorkflowBinding(
            "cutover.approval",
            "network.cutover.approval",
            "1.0.0",
            WorkflowBinding.Trigger.DOMAIN_EVENT,
            "cutover.plan",
            "cutover.started",
            "cutover.approve",
            Map.of("credentialSnapshot", "object.deviceCredential"),
            WorkflowBinding.MigrationPolicy.PINNED);

    assertThat(service.validate(binding))
        .anyMatch(value -> value.contains("pdp.*"))
        .anyMatch(value -> value.contains("凭据"))
        .contains("流程版本未部署");
  }

  @Test
  void 版本化绑定必须声明完整启动条件和合法变量映射() {
    var service = new WorkflowBindingService(binding -> List.of());
    var binding =
        new WorkflowBinding(
            "cutover.approval",
            "network.cutover.approval",
            "1.2.0",
            WorkflowBinding.Trigger.STATE_TRANSITION,
            "network.cutover",
            null,
            "cutover.approve",
            Map.of("projectId", "object.projectId"),
            WorkflowBinding.MigrationPolicy.BATCH_MIGRATABLE);

    assertThat(service.validate(binding)).isEmpty();
  }

  @Test
  void 运行中实例迁移必须升级同一流程并遵守固定策略和影响预览() {
    var service = new WorkflowBindingService(binding -> List.of());
    var current =
        binding(
            "1.0.0",
            "network.cutover.approval",
            "cutover.approve",
            WorkflowBinding.MigrationPolicy.PINNED,
            Map.of("projectId", "object.projectId", "siteId", "object.siteId"));
    var incompatible =
        binding(
            "1.0.0",
            "network.other.approval",
            "cutover.admin",
            WorkflowBinding.MigrationPolicy.BATCH_MIGRATABLE,
            Map.of("projectId", "object.projectId"));

    assertThat(service.validateMigration(current, incompatible, 10, false))
        .anyMatch(value -> value.contains("定义键"))
        .anyMatch(value -> value.contains("高于当前版本"))
        .anyMatch(value -> value.contains("影响预览"))
        .anyMatch(value -> value.contains("PINNED"))
        .anyMatch(value -> value.contains("授权策略"))
        .anyMatch(value -> value.contains("siteId"));
  }

  @Test
  void 批量可迁移绑定在批准预览且兼容时应通过() {
    var service = new WorkflowBindingService(binding -> List.of());
    var current =
        binding(
            "1.0.0",
            "network.cutover.approval",
            "cutover.approve",
            WorkflowBinding.MigrationPolicy.BATCH_MIGRATABLE,
            Map.of("projectId", "object.projectId"));
    var target =
        binding(
            "1.1.0",
            "network.cutover.approval",
            "cutover.approve",
            WorkflowBinding.MigrationPolicy.BATCH_MIGRATABLE,
            Map.of(
                "projectId", "object.projectId",
                "planId", "object.planId"));

    assertThat(service.validateMigration(current, target, 10, true)).isEmpty();
  }

  private static WorkflowBinding binding(
      String version,
      String processKey,
      String authorizationPolicy,
      WorkflowBinding.MigrationPolicy migrationPolicy,
      Map<String, String> variables) {
    return new WorkflowBinding(
        "cutover.approval",
        processKey,
        version,
        WorkflowBinding.Trigger.STATE_TRANSITION,
        "network.cutover",
        null,
        authorizationPolicy,
        variables,
        migrationPolicy);
  }
}
