package com.pdp.workflow.port;

import com.pdp.workflow.domain.WorkflowDefinition;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowDefinitionPort {
    ValidationResult validate(ValidateCommand command);
    WorkflowDefinition deploy(DeployCommand command);
    Optional<WorkflowDefinition> find(UUID definitionId);

    record ValidateCommand(String processDefinitionKey, String businessVersion,
                           UUID domainPackageVersionId, String bpmnXml) {}
    record DeployCommand(String processDefinitionKey, String businessVersion,
                         UUID domainPackageVersionId, String bpmnXml, String contentHash,
                         String idempotencyKey) {}
    record Finding(Severity severity, String code, String message) {}
    enum Severity { ERROR, WARNING }
    record ValidationResult(boolean valid, String contentHash, List<Finding> findings) {
        public ValidationResult {
            findings = List.copyOf(findings);
        }
    }
}
