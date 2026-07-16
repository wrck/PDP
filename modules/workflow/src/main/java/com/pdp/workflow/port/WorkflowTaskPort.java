package com.pdp.workflow.port;

import com.pdp.workflow.domain.WorkflowTaskRef;
import java.util.List;
import java.util.UUID;

public interface WorkflowTaskPort {
    List<WorkflowTaskRef> query(TaskQuery query);
    WorkflowTaskRef claim(ClaimCommand command);
    WorkflowTaskRef complete(CompleteCommand command);

    record TaskQuery(UUID workspaceId, UUID actorId, UUID workflowInstanceId) {}
    record ClaimCommand(UUID taskId, UUID actorId, long expectedRevision, String idempotencyKey) {}
    record CompleteCommand(UUID taskId, UUID actorId, long expectedRevision,
                           String actionKey, String idempotencyKey) {}
}
