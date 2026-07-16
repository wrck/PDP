package com.pdp.workspace.port;

import com.pdp.workspace.application.WorkspaceGovernanceEvent;

@FunctionalInterface
public interface WorkspaceGovernanceEventPublisher {
  void publish(WorkspaceGovernanceEvent event);
}
