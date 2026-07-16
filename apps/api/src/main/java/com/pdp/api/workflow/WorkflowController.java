package com.pdp.api.workflow;

import com.pdp.workflow.domain.WorkflowBusinessRef;
import com.pdp.workflow.domain.WorkflowDefinition;
import com.pdp.workflow.domain.WorkflowIncident;
import com.pdp.workflow.domain.WorkflowInstanceRef;
import com.pdp.workflow.port.WorkflowAdministrationPort;
import com.pdp.workflow.port.WorkflowDefinitionPort;
import com.pdp.workflow.port.WorkflowRuntimePort;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public final class WorkflowController {
  private final Optional<WorkflowDefinitionPort> definitions;
  private final Optional<WorkflowRuntimePort> runtime;
  private final Optional<WorkflowAdministrationPort> administration;

  public WorkflowController(
      Optional<WorkflowDefinitionPort> definitions,
      Optional<WorkflowRuntimePort> runtime,
      Optional<WorkflowAdministrationPort> administration) {
    this.definitions = definitions;
    this.runtime = runtime;
    this.administration = administration;
  }

  @PostMapping("/workflow-definitions/validate")
  public ValidationResponse validate(@Valid @RequestBody ValidationRequest request) {
    var result =
        definitions()
            .validate(
                new WorkflowDefinitionPort.ValidateCommand(
                    request.processDefinitionKey(),
                    request.businessVersion(),
                    request.domainPackageVersionId(),
                    request.bpmnXml()));
    return new ValidationResponse(
        result.valid(),
        result.contentHash(),
        result.findings().stream()
            .map(finding -> new FindingResponse(
                finding.severity().name(), finding.code(), finding.message()))
            .toList());
  }

  @PostMapping("/workflow-definitions/deploy")
  public ResponseEntity<WorkflowDefinitionSummary> deploy(
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody DeploymentRequest request) {
    try {
      WorkflowDefinition deployed =
          definitions()
              .deploy(
                  new WorkflowDefinitionPort.DeployCommand(
                      request.processDefinitionKey(),
                      request.businessVersion(),
                      request.domainPackageVersionId(),
                      request.bpmnResource(),
                      request.contentHash(),
                      idempotencyKey));
      return ResponseEntity.status(HttpStatus.CREATED)
          .body(WorkflowDefinitionSummary.from(deployed));
    } catch (IllegalArgumentException | IllegalStateException exception) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
    }
  }

  @GetMapping("/workflow-instances/{workflowInstanceId}")
  public WorkflowInstanceSummary instance(@PathVariable UUID workflowInstanceId) {
    WorkflowInstanceRef instance =
        runtime()
            .find(workflowInstanceId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "流程实例不存在"));
    List<WorkflowIncident> incidents =
        administration.map(port -> port.incidents(workflowInstanceId)).orElseGet(List::of);
    return WorkflowInstanceSummary.from(instance, incidents);
  }

  @PostMapping("/workflow-instances/{workflowInstanceId}/actions")
  public ResponseEntity<JobAcceptedResponse> applyAction(
      @PathVariable UUID workflowInstanceId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody WorkflowAdminActionRequest request) {
    if (runtime().find(workflowInstanceId).isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "流程实例不存在");
    }
    try {
      var accepted =
          administration()
              .apply(
                  new WorkflowAdministrationPort.ActionCommand(
                      workflowInstanceId,
                      request.action(),
                      request.reason(),
                      request.expectedRevision(),
                      request.targetDefinitionId(),
                      request.impactPreviewId(),
                      idempotencyKey));
      return ResponseEntity.accepted()
          .body(new JobAcceptedResponse(accepted.jobId(), accepted.status()));
    } catch (IllegalArgumentException | IllegalStateException exception) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
    }
  }

  private WorkflowDefinitionPort definitions() {
    return definitions.orElseThrow(WorkflowController::unavailable);
  }

  private WorkflowRuntimePort runtime() {
    return runtime.orElseThrow(WorkflowController::unavailable);
  }

  private WorkflowAdministrationPort administration() {
    return administration.orElseThrow(WorkflowController::unavailable);
  }

  private static ResponseStatusException unavailable() {
    return new ResponseStatusException(
        HttpStatus.SERVICE_UNAVAILABLE, "工作流持久化适配器尚未启用");
  }

  public record ValidationRequest(
      @NotBlank @Pattern(regexp = "^[a-z][a-z0-9.-]{2,99}$") String processDefinitionKey,
      @NotBlank @Pattern(regexp = "^[0-9]+\\.[0-9]+\\.[0-9]+$") String businessVersion,
      UUID domainPackageVersionId,
      @NotBlank @Size(min = 50) String bpmnXml) {}

  public record DeploymentRequest(
      @NotBlank String processDefinitionKey,
      @NotBlank String businessVersion,
      @NotBlank String contentHash,
      @NotBlank String bpmnResource,
      UUID domainPackageVersionId) {}

  public record WorkflowAdminActionRequest(
      @NotNull WorkflowAdministrationPort.Action action,
      @NotBlank @Size(min = 5, max = 2000) String reason,
      @Min(0) long expectedRevision,
      UUID targetDefinitionId,
      UUID impactPreviewId) {}

  public record FindingResponse(String severity, String code, String message) {}

  public record ValidationResponse(
      boolean valid, String contentHash, List<FindingResponse> findings) {}

  public record WorkflowDefinitionSummary(
      UUID id,
      String processDefinitionKey,
      String businessVersion,
      String contentHash,
      UUID domainPackageVersionId,
      String status,
      Instant deployedAt) {
    static WorkflowDefinitionSummary from(WorkflowDefinition definition) {
      return new WorkflowDefinitionSummary(
          definition.id(),
          definition.processDefinitionKey(),
          definition.businessVersion(),
          definition.contentHash(),
          definition.domainPackageVersionId(),
          definition.status().name(),
          definition.deployedAt());
    }
  }

  public record BusinessObjectRefResponse(
      UUID workspaceId, String objectType, UUID objectId) {
    static BusinessObjectRefResponse from(WorkflowBusinessRef ref) {
      return new BusinessObjectRefResponse(ref.workspaceId(), ref.objectType(), ref.objectId());
    }
  }

  public record IncidentResponse(
      UUID id,
      String code,
      String message,
      String status,
      int attempts,
      Instant occurredAt,
      Instant resolvedAt) {
    static IncidentResponse from(WorkflowIncident incident) {
      return new IncidentResponse(
          incident.id(),
          incident.code(),
          incident.message(),
          incident.status().name(),
          incident.attempts(),
          incident.occurredAt(),
          incident.resolvedAt());
    }
  }

  public record WorkflowInstanceSummary(
      UUID id,
      UUID definitionId,
      Map<String, Object> businessObjectRef,
      String state,
      List<String> currentActivityKeys,
      int incidentCount,
      long revision,
      List<IncidentResponse> incidents) {
    static WorkflowInstanceSummary from(
        WorkflowInstanceRef instance, List<WorkflowIncident> incidents) {
      BusinessObjectRefResponse ref = BusinessObjectRefResponse.from(instance.businessObjectRef());
      return new WorkflowInstanceSummary(
          instance.id(),
          instance.definitionId(),
          Map.of(
              "workspaceId", ref.workspaceId(),
              "objectType", ref.objectType(),
              "objectId", ref.objectId()),
          instance.state().name(),
          instance.currentActivityKeys().stream().sorted().toList(),
          instance.incidentCount(),
          instance.revision(),
          incidents.stream().map(IncidentResponse::from).toList());
    }
  }

  public record JobAcceptedResponse(UUID jobId, String status) {}
}
