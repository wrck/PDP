package com.pdp.api.workflow;

import com.pdp.workflow.application.WorkflowAdministrationService;
import com.pdp.workflow.application.WorkflowDefinitionService;
import com.pdp.workflow.application.WorkflowRuntimeService;
import com.pdp.workflow.port.WorkflowAdministrationPort;
import com.pdp.workflow.port.WorkflowDefinitionPort;
import com.pdp.workflow.port.WorkflowOrchestrationOutbox;
import com.pdp.workflow.port.WorkflowRepository;
import com.pdp.workflow.port.WorkflowRuntimePort;
import java.time.Clock;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class WorkflowApplicationConfiguration {

  @Bean
  @ConditionalOnBean(WorkflowRepository.class)
  @ConditionalOnMissingBean(WorkflowDefinitionPort.class)
  WorkflowDefinitionPort workflowDefinitionPort(WorkflowRepository repository) {
    return new WorkflowDefinitionService(repository, Clock.systemUTC(), UUID::randomUUID);
  }

  @Bean
  @ConditionalOnBean({WorkflowRepository.class, WorkflowOrchestrationOutbox.class})
  @ConditionalOnMissingBean(WorkflowRuntimePort.class)
  WorkflowRuntimePort workflowRuntimePort(
      WorkflowRepository repository, WorkflowOrchestrationOutbox outbox) {
    return new WorkflowRuntimeService(repository, outbox, Clock.systemUTC(), UUID::randomUUID);
  }

  @Bean
  @ConditionalOnBean({WorkflowRepository.class, WorkflowOrchestrationOutbox.class})
  @ConditionalOnMissingBean(WorkflowAdministrationPort.class)
  WorkflowAdministrationPort workflowAdministrationPort(
      WorkflowRepository repository, WorkflowOrchestrationOutbox outbox) {
    return new WorkflowAdministrationService(
        repository, outbox, Clock.systemUTC(), UUID::randomUUID);
  }
}
