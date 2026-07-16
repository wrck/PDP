package com.pdp.workflow.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record WorkflowTaskRef(
        UUID id,
        UUID workflowInstanceId,
        String taskKey,
        String name,
        UUID assigneeId,
        Set<UUID> candidateActorIds,
        State state,
        long revision,
        Instant createdAt,
        Instant dueAt) {

    public enum State { CREATED, CLAIMED, COMPLETED, CANCELLED }

    public WorkflowTaskRef {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(workflowInstanceId, "workflowInstanceId");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(createdAt, "createdAt");
        candidateActorIds = Set.copyOf(candidateActorIds == null ? Set.of() : candidateActorIds);
        if (taskKey == null || taskKey.isBlank() || name == null || name.isBlank() || revision < 0) {
            throw new IllegalArgumentException("人工任务字段无效");
        }
    }

    public WorkflowTaskRef claim(UUID actorId, long expectedRevision) {
        requireRevision(expectedRevision);
        if (state != State.CREATED || (!candidateActorIds.isEmpty() && !candidateActorIds.contains(actorId))) {
            throw new IllegalStateException("任务当前不可领取");
        }
        return new WorkflowTaskRef(id, workflowInstanceId, taskKey, name, actorId,
                candidateActorIds, State.CLAIMED, revision + 1, createdAt, dueAt);
    }

    public WorkflowTaskRef complete(UUID actorId, long expectedRevision) {
        requireRevision(expectedRevision);
        if (state != State.CLAIMED || !Objects.equals(assigneeId, actorId)) {
            throw new IllegalStateException("只有当前办理人可以完成任务");
        }
        return new WorkflowTaskRef(id, workflowInstanceId, taskKey, name, assigneeId,
                candidateActorIds, State.COMPLETED, revision + 1, createdAt, dueAt);
    }

    private void requireRevision(long expectedRevision) {
        if (revision != expectedRevision) {
            throw new IllegalStateException("人工任务版本冲突");
        }
    }
}
