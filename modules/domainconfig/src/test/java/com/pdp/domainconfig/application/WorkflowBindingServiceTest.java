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
}
