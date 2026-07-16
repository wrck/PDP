package com.pdp.workspace.application;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceGovernanceEvent(
    String eventType,
    UUID workspaceId,
    UUID objectId,
    long revision,
    String reason,
    Instant occurredAt) {}
