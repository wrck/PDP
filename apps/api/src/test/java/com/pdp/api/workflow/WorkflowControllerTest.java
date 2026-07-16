package com.pdp.api.workflow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pdp.workflow.domain.WorkflowBusinessRef;
import com.pdp.workflow.domain.WorkflowDefinition;
import com.pdp.workflow.domain.WorkflowIncident;
import com.pdp.workflow.domain.WorkflowInstanceRef;
import com.pdp.workflow.port.WorkflowAdministrationPort;
import com.pdp.workflow.port.WorkflowDefinitionPort;
import com.pdp.workflow.port.WorkflowRuntimePort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class WorkflowControllerTest {

  @Test
  void 校验和部署必须保持OpenApi字段与状态码() throws Exception {
    WorkflowDefinitionPort definitions = org.mockito.Mockito.mock(WorkflowDefinitionPort.class);
    when(definitions.validate(any()))
        .thenReturn(
            new WorkflowDefinitionPort.ValidationResult(
                true,
                "hash-1",
                List.of(
                    new WorkflowDefinitionPort.Finding(
                        WorkflowDefinitionPort.Severity.WARNING,
                        "ASYNC_RECOMMENDED",
                        "建议为外部调用配置异步边界"))));
    UUID definitionId = UUID.randomUUID();
    when(definitions.deploy(any()))
        .thenReturn(
            new WorkflowDefinition(
                definitionId,
                "approval.flow",
                "1.0.0",
                "hash-1",
                null,
                WorkflowDefinition.Status.DEPLOYED,
                Instant.parse("2026-07-17T03:00:00Z")));
    MockMvc mvc =
        mvc(new WorkflowController(Optional.of(definitions), Optional.empty(), Optional.empty()));

    mvc.perform(
            post("/api/v1/workflow-definitions/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "processDefinitionKey":"approval.flow",
                      "businessVersion":"1.0.0",
                      "bpmnXml":"<definitions xmlns=\\"http://www.omg.org/spec/BPMN/20100524/MODEL\\"><process id=\\"approval.flow\\"/></definitions>"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.valid").value(true))
        .andExpect(jsonPath("$.contentHash").value("hash-1"))
        .andExpect(jsonPath("$.findings[0].severity").value("WARNING"));

    mvc.perform(
            post("/api/v1/workflow-definitions/deploy")
                .header("Idempotency-Key", "deploy-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "processDefinitionKey":"approval.flow",
                      "businessVersion":"1.0.0",
                      "contentHash":"hash-1",
                      "bpmnResource":"<definitions xmlns=\\"http://www.omg.org/spec/BPMN/20100524/MODEL\\"><process id=\\"approval.flow\\"/></definitions>"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(definitionId.toString()))
        .andExpect(jsonPath("$.status").value("DEPLOYED"));

    ArgumentCaptor<WorkflowDefinitionPort.DeployCommand> command =
        ArgumentCaptor.forClass(WorkflowDefinitionPort.DeployCommand.class);
    verify(definitions).deploy(command.capture());
    org.assertj.core.api.Assertions.assertThat(command.getValue().idempotencyKey())
        .isEqualTo("deploy-1");
  }

  @Test
  void 实例诊断应返回Incident并接受受控动作() throws Exception {
    WorkflowRuntimePort runtime = org.mockito.Mockito.mock(WorkflowRuntimePort.class);
    WorkflowAdministrationPort administration =
        org.mockito.Mockito.mock(WorkflowAdministrationPort.class);
    UUID instanceId = UUID.randomUUID();
    WorkflowInstanceRef instance =
        new WorkflowInstanceRef(
            instanceId,
            UUID.randomUUID(),
            new WorkflowBusinessRef(UUID.randomUUID(), "approval", UUID.randomUUID()),
            WorkflowInstanceRef.State.INCIDENT,
            Set.of("externalCall"),
            1,
            3,
            Instant.parse("2026-07-17T03:00:00Z"));
    when(runtime.find(instanceId)).thenReturn(Optional.of(instance));
    when(administration.incidents(instanceId))
        .thenReturn(
            List.of(
                new WorkflowIncident(
                    UUID.randomUUID(),
                    instanceId,
                    "ENGINE_TIMEOUT",
                    "引擎调用超时",
                    WorkflowIncident.Status.DEAD_LETTER,
                    3,
                    Instant.parse("2026-07-17T03:00:00Z"),
                    null)));
    UUID jobId = UUID.randomUUID();
    when(administration.apply(any()))
        .thenReturn(new WorkflowAdministrationPort.JobAccepted(jobId, "QUEUED"));
    MockMvc mvc =
        mvc(
            new WorkflowController(
                Optional.empty(), Optional.of(runtime), Optional.of(administration)));

    mvc.perform(get("/api/v1/workflow-instances/{id}", instanceId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("INCIDENT"))
        .andExpect(jsonPath("$.incidentCount").value(1))
        .andExpect(jsonPath("$.incidents[0].status").value("DEAD_LETTER"));

    mvc.perform(
            post("/api/v1/workflow-instances/{id}/actions", instanceId)
                .header("Idempotency-Key", "retry-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "action":"RETRY",
                      "reason":"修复下游服务后重新执行",
                      "expectedRevision":3
                    }
                    """))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.jobId").value(jobId.toString()))
        .andExpect(jsonPath("$.status").value("QUEUED"));
  }

  @Test
  void 缺少生产仓储适配器时上下文可创建且接口明确返回503() throws Exception {
    try (var context = new AnnotationConfigApplicationContext()) {
      context.register(WorkflowApplicationConfiguration.class, WorkflowController.class);
      context.refresh();
      MockMvc mvc = mvc(context.getBean(WorkflowController.class));

      mvc.perform(
              post("/api/v1/workflow-definitions/validate")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {
                        "processDefinitionKey":"approval.flow",
                        "businessVersion":"1.0.0",
                        "bpmnXml":"<definitions xmlns=\\"http://www.omg.org/spec/BPMN/20100524/MODEL\\"><process id=\\"approval.flow\\"/></definitions>"
                      }
                      """))
          .andExpect(status().isServiceUnavailable());
    }
  }

  private static MockMvc mvc(WorkflowController controller) {
    return MockMvcBuilders.standaloneSetup(controller).build();
  }
}
