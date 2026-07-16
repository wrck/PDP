package com.pdp.workflow.port;

import com.pdp.workflow.domain.WorkflowTaskRef;
import java.util.UUID;

public interface WorkflowAuthorizationPort {
    boolean canView(UUID workspaceId, UUID actorId, WorkflowTaskRef task);
    boolean canClaim(UUID actorId, WorkflowTaskRef task);
    boolean canComplete(UUID actorId, WorkflowTaskRef task);
}
