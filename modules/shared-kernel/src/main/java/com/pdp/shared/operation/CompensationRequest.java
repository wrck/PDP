package com.pdp.shared.operation;

import java.util.UUID;

public record CompensationRequest(
    UUID operationId, String operationType, String reason, String evidenceReference) {}
