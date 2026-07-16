package com.pdp.workflow.port;

import com.pdp.workflow.domain.WorkflowBusinessRef;
import com.pdp.workflow.domain.WorkflowInstanceRef;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowRuntimePort {
    WorkflowInstanceRef start(StartCommand command);
    void advance(AdvanceCommand command);
    void correlateMessage(MessageCommand command);
    Optional<WorkflowInstanceRef> find(UUID workflowInstanceId);

    record StartCommand(UUID definitionId, WorkflowBusinessRef businessObjectRef,
                        String idempotencyKey, Map<String, String> variables) {}
    record AdvanceCommand(UUID workflowInstanceId, String activityKey,
                          String idempotencyKey, Map<String, String> variables) {}
    record MessageCommand(UUID workflowInstanceId, String messageKey,
                          String idempotencyKey, Map<String, String> variables) {}
}
