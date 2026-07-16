package com.pdp.workflow.application;

import com.pdp.workflow.domain.WorkflowOrchestrationCommand;
import com.pdp.workflow.domain.WorkflowTaskRef;
import com.pdp.workflow.port.*;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class WorkflowTaskService implements WorkflowTaskPort {
    private final WorkflowRepository repository;
    private final WorkflowAuthorizationPort authorization;
    private final WorkflowOrchestrationOutbox outbox;
    private final Clock clock;
    private final Supplier<UUID> ids;

    public WorkflowTaskService(WorkflowRepository repository, WorkflowAuthorizationPort authorization,
                               WorkflowOrchestrationOutbox outbox, Clock clock, Supplier<UUID> ids) {
        this.repository = Objects.requireNonNull(repository);
        this.authorization = Objects.requireNonNull(authorization);
        this.outbox = Objects.requireNonNull(outbox);
        this.clock = Objects.requireNonNull(clock);
        this.ids = Objects.requireNonNull(ids);
    }

    @Override
    public List<WorkflowTaskRef> query(TaskQuery query) {
        return repository.findTasks(query.workflowInstanceId()).stream()
                .filter(task -> authorization.canView(query.workspaceId(), query.actorId(), task))
                .toList();
    }

    @Override
    public WorkflowTaskRef claim(ClaimCommand command) {
        WorkflowTaskRef task = task(command.taskId());
        if (!authorization.canClaim(command.actorId(), task)) {
            throw new SecurityException("无权领取人工任务");
        }
        return repository.saveTask(task.claim(command.actorId(), command.expectedRevision()));
    }

    @Override
    public WorkflowTaskRef complete(CompleteCommand command) {
        WorkflowTaskRef task = task(command.taskId());
        if (!authorization.canComplete(command.actorId(), task)) {
            throw new SecurityException("无权办理人工任务");
        }
        WorkflowTaskRef completed =
                repository.saveTask(task.complete(command.actorId(), command.expectedRevision()));
        outbox.append(new WorkflowOrchestrationCommand(ids.get(),
                WorkflowOrchestrationCommand.Type.COMPLETE_TASK, task.workflowInstanceId(),
                command.idempotencyKey(), Map.of("taskId", task.id().toString(),
                "actionKey", command.actionKey()), clock.instant()));
        return completed;
    }

    private WorkflowTaskRef task(UUID id) {
        return repository.findTask(id).orElseThrow(() -> new IllegalArgumentException("人工任务不存在"));
    }
}
