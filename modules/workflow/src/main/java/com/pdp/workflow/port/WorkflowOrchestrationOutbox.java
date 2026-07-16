package com.pdp.workflow.port;

import com.pdp.workflow.domain.WorkflowOrchestrationCommand;

public interface WorkflowOrchestrationOutbox {
    void append(WorkflowOrchestrationCommand command);
}
