package com.pdp.domainconfig.application;

import com.pdp.domainconfig.domain.behavior.WorkflowBinding;
import com.pdp.domainconfig.port.WorkflowDefinitionCatalogPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 领域包只依赖平台工作流目录契约，不接触 Flowable RepositoryService、RuntimeService 等专有 API。
 */
@Service
public final class WorkflowBindingService {
  private static final Set<String> SENSITIVE_VARIABLE_MARKERS =
      Set.of("attachment", "credential", "signature", "secret", "authorizationDecision");

  private final WorkflowDefinitionCatalogPort workflows;

  public WorkflowBindingService(WorkflowDefinitionCatalogPort workflows) {
    this.workflows = workflows;
  }

  public List<String> validate(WorkflowBinding binding) {
    var errors = new ArrayList<String>();
    if (binding.authorizationPolicyKey() == null || binding.authorizationPolicyKey().isBlank()) {
      errors.add("工作流绑定必须声明授权策略");
    }
    if (binding.trigger() == WorkflowBinding.Trigger.DOMAIN_EVENT
        && (binding.eventType() == null || !binding.eventType().startsWith("pdp."))) {
      errors.add("领域事件触发器必须声明 pdp.* 事件类型");
    }
    binding
        .variableMappings()
        .forEach(
            (variable, source) -> {
              String normalized = (variable + " " + source).toLowerCase();
              if (SENSITIVE_VARIABLE_MARKERS.stream()
                  .map(String::toLowerCase)
                  .anyMatch(normalized::contains)) {
                errors.add("工作流变量不得复制附件、凭据、签名或最终授权结论: " + variable);
              }
            });
    errors.addAll(workflows.validate(binding));
    return List.copyOf(errors);
  }
}
