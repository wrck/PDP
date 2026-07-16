package com.pdp.workflow.port;

import com.pdp.workflow.domain.WorkflowIncident;
import java.util.List;
import java.util.UUID;

public interface WorkflowAdministrationPort {
    JobAccepted apply(ActionCommand command);
    List<WorkflowIncident> incidents(UUID workflowInstanceId);

    enum Action { PAUSE, RESUME, RETRY, MIGRATE, TERMINATE, MANUAL_COMPENSATE }
    record ActionCommand(UUID workflowInstanceId, Action action, String reason,
                         long expectedRevision, UUID targetDefinitionId,
                         UUID impactPreviewId, String idempotencyKey) {}
    record JobAccepted(UUID jobId, String status) {}
}
