package com.pdp.domainconfig.application;

import com.pdp.domainconfig.domain.behavior.ActionDefinition;
import com.pdp.domainconfig.domain.metamodel.CoreFieldCatalog;
import com.pdp.domainconfig.domain.metamodel.DomainPackageManifest;
import com.pdp.domainconfig.domain.metamodel.ObjectDefinition;
import com.pdp.domainconfig.domain.packageversion.PackageLayer;
import com.pdp.domainconfig.port.WorkflowDefinitionCatalogPort;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public final class DomainPackageValidationService {
  private static final Set<String> PLATFORM_RESERVED_ACTIONS =
      Set.of(
          "workspace.owner.change",
          "workspace.archive",
          "security.policy.change",
          "retention.policy.change");

  private final CoreFieldCatalog coreFields;
  private final WorkflowDefinitionCatalogPort workflowDefinitions;

  public DomainPackageValidationService(
      CoreFieldCatalog coreFields, WorkflowDefinitionCatalogPort workflowDefinitions) {
    this.coreFields = coreFields;
    this.workflowDefinitions = workflowDefinitions;
  }

  public ValidationReport validate(DomainPackageManifest manifest) {
    var errors = new ArrayList<String>();
    var warnings = new ArrayList<String>();
    if (manifest.layer() != PackageLayer.PLATFORM_STANDARD
        && (manifest.parentPackageKey() == null || manifest.parentPackageKey().isBlank())) {
      errors.add("行业包和工作空间客户包必须继承上层领域包");
    }

    var objectKeys = new HashSet<String>();
    for (ObjectDefinition object : manifest.objects()) {
      if (!objectKeys.add(object.stableKey())) {
        errors.add("对象稳定标识重复: " + object.stableKey());
      }
      validateObject(object, manifest, errors, warnings);
    }
    manifest
        .pages()
        .forEach(
            page -> {
              if (!objectKeys.contains(page.objectKey())) {
                errors.add("页面引用不存在的对象: " + page.stableKey());
              }
            });
    manifest
        .views()
        .forEach(
            view -> {
              if (!objectKeys.contains(view.objectKey())) {
                errors.add("视图引用不存在的对象: " + view.stableKey());
              }
            });
    manifest
        .overrides()
        .forEach(
            override -> {
              if (override.reason() == null
                  || override.reason().isBlank()
                  || override.responsibleActor() == null
                  || override.responsibleActor().isBlank()
                  || override.applicableVersion() == null
                  || override.applicableVersion().isBlank()) {
                errors.add("领域包覆盖必须记录原因、责任人和适用版本: " + override.targetStableKey());
              }
            });
    manifest.workflowBindings().forEach(binding -> errors.addAll(workflowDefinitions.validate(binding)));
    manifest
        .extensions()
        .forEach(
            extension -> {
              if (extension.signature() == null || extension.signature().isBlank()) {
                errors.add("受治理扩展必须具有签名: " + extension.stableKey());
              }
              if (extension.timeoutMs() < 100 || extension.timeoutMs() > 30000) {
                errors.add("受治理扩展超时时间越界: " + extension.stableKey());
              }
            });
    manifest
        .migrations()
        .forEach(
            migration -> {
              if (migration.steps().isEmpty() || migration.rollbackType() == null) {
                errors.add("迁移计划必须包含步骤和回滚方案");
              }
            });
    return new ValidationReport(
        errors.isEmpty(),
        List.copyOf(errors),
        List.copyOf(warnings),
        errors.isEmpty() ? Compatibility.COMPATIBLE : Compatibility.BREAKING);
  }

  private void validateObject(
      ObjectDefinition object,
      DomainPackageManifest manifest,
      List<String> errors,
      List<String> warnings) {
    var fieldKeys = new HashSet<String>();
    object
        .fields()
        .forEach(
            field -> {
              if (!fieldKeys.add(field.stableKey())) {
                errors.add("字段稳定标识重复: " + field.stableKey());
              }
              coreFields
                  .semanticConflict(field)
                  .ifPresent(
                      conflict ->
                          errors.add(
                              "扩展字段与核心字段语义冲突，应复用核心字段 "
                                  + conflict.stableKey()
                                  + ": "
                                  + field.stableKey()));
            });
    if (!object.states().isEmpty()) {
      long initial = object.states().stream().filter(state -> state.initial()).count();
      if (initial != 1) {
        errors.add("每个可运行对象必须且只能有一个初始状态: " + object.stableKey());
      }
      object
          .states()
          .forEach(
              state -> {
                if (state.topLifecycleState() == null) {
                  errors.add("领域状态必须映射顶层生命周期: " + state.stableKey());
                }
              });
      var states =
          object.states().stream().map(state -> state.stableKey()).collect(java.util.stream.Collectors.toSet());
      object
          .transitions()
          .forEach(
              transition -> {
                if (!states.contains(transition.from()) || !states.contains(transition.to())) {
                  errors.add("状态迁移引用不存在的状态: " + transition.stableKey());
                }
                if (PLATFORM_RESERVED_ACTIONS.contains(transition.requiredPermission())) {
                  errors.add("领域包不得声明平台保留动作: " + transition.requiredPermission());
                }
              });
    }
    var permissionKeys =
        manifest.permissions().stream()
            .map(permission -> permission.capabilityKey())
            .collect(java.util.stream.Collectors.toSet());
    object
        .transitions()
        .forEach(
            transition -> {
              if (!permissionKeys.contains(transition.requiredPermission())) {
                warnings.add("状态迁移权限未在领域包内声明: " + transition.requiredPermission());
              }
            });
    manifest
        .rules()
        .forEach(
            rule ->
                rule.actions().stream()
                    .filter(action -> action.type() == ActionDefinition.Type.CALL_EXTENSION)
                    .forEach(
                        action -> {
                          Object extension = action.parameters().get("extensionKey");
                          if (extension == null) {
                            errors.add("调用扩展动作必须声明 extensionKey: " + rule.stableKey());
                          }
                        }));
  }

  public enum Compatibility {
    COMPATIBLE,
    MIGRATION_REQUIRED,
    BREAKING
  }

  public record ValidationReport(
      boolean valid, List<String> errors, List<String> warnings, Compatibility compatibility) {
    public ValidationReport {
      errors = List.copyOf(errors);
      warnings = List.copyOf(warnings);
    }
  }
}
