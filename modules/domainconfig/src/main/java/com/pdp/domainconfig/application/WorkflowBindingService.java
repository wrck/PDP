package com.pdp.domainconfig.application;

import com.pdp.domainconfig.domain.behavior.WorkflowBinding;
import com.pdp.domainconfig.port.WorkflowDefinitionCatalogPort;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * 领域包只依赖平台工作流目录契约，不接触 Flowable RepositoryService、RuntimeService 等专有 API。
 */
@Service
public final class WorkflowBindingService {
  private static final Pattern STABLE_KEY = Pattern.compile("^[a-z][a-z0-9.-]{2,99}$");
  private static final Pattern BUSINESS_VERSION = Pattern.compile("^[0-9]+\\.[0-9]+\\.[0-9]+$");
  private static final Pattern EVENT_TYPE = Pattern.compile("^pdp\\.[a-z0-9.-]+$");
  private static final Pattern VARIABLE_NAME = Pattern.compile("^[A-Za-z_][A-Za-z0-9_.-]{0,99}$");
  private static final Set<String> SENSITIVE_VARIABLE_MARKERS =
      Set.of("attachment", "credential", "signature", "secret", "authorizationDecision");

  private final WorkflowDefinitionCatalogPort workflows;

  public WorkflowBindingService(WorkflowDefinitionCatalogPort workflows) {
    this.workflows = Objects.requireNonNull(workflows);
  }

  public List<String> validate(WorkflowBinding binding) {
    var errors = new ArrayList<String>();
    if (binding == null) {
      return List.of("工作流绑定不能为空");
    }
    requireStableKey(binding.stableKey(), "工作流绑定稳定标识", errors);
    requireStableKey(binding.processDefinitionKey(), "平台流程定义键", errors);
    if (!matches(BUSINESS_VERSION, binding.businessVersion())) {
      errors.add("平台流程业务版本必须使用 major.minor.patch 语义化版本");
    }
    requireStableKey(binding.authorizationPolicyKey(), "授权策略键", errors);
    if (binding.trigger() == null) {
      errors.add("工作流绑定必须声明启动触发器");
    }
    if (binding.instanceMigrationPolicy() == null) {
      errors.add("工作流绑定必须声明存量实例迁移策略");
    }
    if (binding.authorizationPolicyKey() == null || binding.authorizationPolicyKey().isBlank()) {
      errors.add("工作流绑定必须声明授权策略");
    }
    if (binding.trigger() == WorkflowBinding.Trigger.DOMAIN_EVENT
        && !matches(EVENT_TYPE, binding.eventType())) {
      errors.add("领域事件触发器必须声明合法的 pdp.* 事件类型");
    }
    if (binding.trigger() != null
        && binding.trigger() != WorkflowBinding.Trigger.DOMAIN_EVENT
        && binding.eventType() != null
        && !binding.eventType().isBlank()) {
      errors.add("仅领域事件触发器允许声明 eventType");
    }
    if ((binding.trigger() == WorkflowBinding.Trigger.OBJECT_CREATED
            || binding.trigger() == WorkflowBinding.Trigger.STATE_TRANSITION)
        && !matches(STABLE_KEY, binding.objectTypeKey())) {
      errors.add("对象创建或状态迁移触发器必须声明合法的 objectTypeKey");
    }
    binding
        .variableMappings()
        .forEach(
            (variable, source) -> {
              if (!matches(VARIABLE_NAME, variable)) {
                errors.add("工作流变量名格式无效: " + variable);
              }
              if (source == null || source.isBlank() || source.length() > 200) {
                errors.add("工作流变量来源不能为空且长度不得超过 200: " + variable);
                return;
              }
              String normalized = (variable + " " + source).toLowerCase(Locale.ROOT);
              if (SENSITIVE_VARIABLE_MARKERS.stream()
                  .map(value -> value.toLowerCase(Locale.ROOT))
                  .anyMatch(normalized::contains)) {
                errors.add("工作流变量不得复制附件、凭据、签名或最终授权结论: " + variable);
              }
            });
    List<String> catalogErrors = workflows.validate(binding);
    if (catalogErrors == null) {
      errors.add("平台工作流目录未返回校验结果");
    } else {
      errors.addAll(catalogErrors);
    }
    return List.copyOf(errors);
  }

  /**
   * 校验存量实例从当前绑定迁移到目标绑定；只表达平台契约，不调用 Flowable 专有迁移 API。
   */
  public List<String> validateMigration(
      WorkflowBinding current,
      WorkflowBinding target,
      long runningInstances,
      boolean impactPreviewApproved) {
    if (current == null || target == null) {
      return List.of("工作流迁移的当前绑定和目标绑定不能为空");
    }
    if (runningInstances < 0) {
      throw new IllegalArgumentException("运行中实例数不能为负数");
    }
    var errors = new ArrayList<>(validate(target));
    if (!Objects.equals(current.stableKey(), target.stableKey())) {
      errors.add("工作流迁移不得改变绑定稳定标识");
    }
    if (!Objects.equals(current.processDefinitionKey(), target.processDefinitionKey())) {
      errors.add("工作流迁移不得跨平台流程定义键");
    }
    if (!isHigherVersion(target.businessVersion(), current.businessVersion())) {
      errors.add("目标平台流程业务版本必须高于当前版本");
    }
    if (runningInstances == 0) {
      return List.copyOf(errors);
    }
    if (!impactPreviewApproved) {
      errors.add("迁移运行中工作流实例前必须完成并批准影响预览");
    }
    if (current.instanceMigrationPolicy() == WorkflowBinding.MigrationPolicy.PINNED) {
      errors.add("当前绑定策略为 PINNED，运行中实例必须固定原流程版本");
    }
    if (!Objects.equals(current.authorizationPolicyKey(), target.authorizationPolicyKey())) {
      errors.add("运行中实例迁移不得改变授权策略");
    }
    if (current.trigger() != target.trigger()
        || !Objects.equals(current.objectTypeKey(), target.objectTypeKey())
        || !Objects.equals(current.eventType(), target.eventType())) {
      errors.add("运行中实例迁移不得改变启动条件");
    }
    current
        .variableMappings()
        .forEach(
            (variable, source) -> {
              if (!target.variableMappings().containsKey(variable)) {
                errors.add("目标绑定缺少运行中实例变量映射: " + variable);
              }
            });
    return List.copyOf(errors);
  }

  private static void requireStableKey(String value, String label, List<String> errors) {
    if (!matches(STABLE_KEY, value)) {
      errors.add(label + "格式无效");
    }
  }

  private static boolean matches(Pattern pattern, String value) {
    return value != null && pattern.matcher(value).matches();
  }

  private static boolean isHigherVersion(String target, String current) {
    if (!matches(BUSINESS_VERSION, target) || !matches(BUSINESS_VERSION, current)) {
      return false;
    }
    String[] targetParts = target.split("\\.");
    String[] currentParts = current.split("\\.");
    for (int index = 0; index < 3; index++) {
      int compared = new BigInteger(targetParts[index]).compareTo(new BigInteger(currentParts[index]));
      if (compared != 0) {
        return compared > 0;
      }
    }
    return false;
  }
}
