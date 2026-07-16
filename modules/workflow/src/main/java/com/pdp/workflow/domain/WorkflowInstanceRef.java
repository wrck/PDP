package com.pdp.workflow.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record WorkflowInstanceRef(
        UUID id,
        UUID definitionId,
        WorkflowBusinessRef businessObjectRef,
        State state,
        Set<String> currentActivityKeys,
        int incidentCount,
        long revision,
        Instant updatedAt) {

    public enum State { STARTING, ACTIVE, SUSPENDED, COMPLETED, TERMINATED, INCIDENT }

    public WorkflowInstanceRef {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(businessObjectRef, "businessObjectRef");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(updatedAt, "updatedAt");
        currentActivityKeys = Set.copyOf(currentActivityKeys == null ? Set.of() : currentActivityKeys);
        if (incidentCount < 0 || revision < 0) {
            throw new IllegalArgumentException("计数与版本不得为负数");
        }
    }

    public WorkflowInstanceRef transition(State target, Set<String> activities, Instant at) {
        boolean allowed = switch (state) {
            case STARTING -> target == State.ACTIVE || target == State.INCIDENT || target == State.TERMINATED;
            case ACTIVE -> Set.of(State.SUSPENDED, State.COMPLETED, State.TERMINATED, State.INCIDENT).contains(target);
            case SUSPENDED -> target == State.ACTIVE || target == State.TERMINATED || target == State.INCIDENT;
            case INCIDENT -> target == State.ACTIVE || target == State.SUSPENDED || target == State.TERMINATED;
            case COMPLETED, TERMINATED -> false;
        };
        if (!allowed) {
            throw new IllegalStateException("非法流程实例状态迁移: " + state + " -> " + target);
        }
        int incidents = target == State.INCIDENT ? incidentCount + 1 : incidentCount;
        return new WorkflowInstanceRef(id, definitionId, businessObjectRef, target, activities,
                incidents, revision + 1, Objects.requireNonNull(at));
    }
}
