package com.pdp.workflow.port;

import com.pdp.workflow.domain.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowRepository {
    Optional<WorkflowDefinition> findDefinition(UUID id);
    Optional<WorkflowDefinition> findDefinition(String processKey, String businessVersion);
    WorkflowDefinition saveDefinition(WorkflowDefinition definition);
    Optional<WorkflowDeployment> findDeploymentByIdempotencyKey(String idempotencyKey);
    WorkflowDeployment saveDeployment(WorkflowDeployment deployment);
    Optional<WorkflowInstanceRef> findInstance(UUID id);
    Optional<WorkflowInstanceRef> findInstanceByIdempotencyKey(String idempotencyKey);
    WorkflowInstanceRef saveInstance(WorkflowInstanceRef instance, String idempotencyKey);
    Optional<WorkflowTaskRef> findTask(UUID id);
    List<WorkflowTaskRef> findTasks(UUID workflowInstanceId);
    WorkflowTaskRef saveTask(WorkflowTaskRef task);
    List<WorkflowIncident> findIncidents(UUID workflowInstanceId);
    WorkflowIncident saveIncident(WorkflowIncident incident);
}
