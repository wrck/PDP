package com.pdp.workflow.application;

import com.pdp.workflow.domain.WorkflowInstanceRef;
import com.pdp.workflow.domain.WorkflowOrchestrationCommand;
import com.pdp.workflow.port.*;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class WorkflowAdministrationService implements WorkflowAdministrationPort {
    private final WorkflowRepository repository;
    private final WorkflowOrchestrationOutbox outbox;
    private final Clock clock;
    private final Supplier<UUID> ids;

    public WorkflowAdministrationService(
            WorkflowRepository repository, WorkflowOrchestrationOutbox outbox, Clock clock, Supplier<UUID> ids) {
        this.repository = Objects.requireNonNull(repository);
        this.outbox = Objects.requireNonNull(outbox);
        this.clock = Objects.requireNonNull(clock);
        this.ids = Objects.requireNonNull(ids);
    }

    @Override
    public JobAccepted apply(ActionCommand command) {
        Objects.requireNonNull(command, "command");
        WorkflowInstanceRef instance = repository.findInstance(command.workflowInstanceId())
                .orElseThrow(() -> new IllegalArgumentException("流程实例不存在"));
        if (instance.revision() != command.expectedRevision()) {
            throw new IllegalStateException("流程实例版本冲突");
        }
        if (command.reason() == null || command.reason().strip().length() < 5) {
            throw new IllegalArgumentException("管理动作必须提供明确原因");
        }
        validateAction(instance, command);
        UUID jobId = ids.get();
        Map<String, String> data = new java.util.LinkedHashMap<>();
        data.put("reason", command.reason());
        data.put("jobId", jobId.toString());
        if (command.targetDefinitionId() != null) {
            data.put("targetDefinitionId", command.targetDefinitionId().toString());
        }
        if (command.impactPreviewId() != null) {
            data.put("impactPreviewId", command.impactPreviewId().toString());
        }
        outbox.append(new WorkflowOrchestrationCommand(ids.get(),
                WorkflowOrchestrationCommand.Type.valueOf(command.action().name()), instance.id(),
                command.idempotencyKey(), data, clock.instant()));
        return new JobAccepted(jobId, "QUEUED");
    }

    @Override
    public List<com.pdp.workflow.domain.WorkflowIncident> incidents(UUID workflowInstanceId) {
        return repository.findIncidents(workflowInstanceId);
    }

    private static void validateAction(WorkflowInstanceRef instance, ActionCommand command) {
        boolean allowed = switch (command.action()) {
            case PAUSE -> instance.state() == WorkflowInstanceRef.State.ACTIVE;
            case RESUME -> instance.state() == WorkflowInstanceRef.State.SUSPENDED;
            case RETRY -> instance.state() == WorkflowInstanceRef.State.INCIDENT;
            case MIGRATE -> (instance.state() == WorkflowInstanceRef.State.ACTIVE
                    || instance.state() == WorkflowInstanceRef.State.SUSPENDED)
                    && command.targetDefinitionId() != null && command.impactPreviewId() != null;
            case TERMINATE -> instance.state() != WorkflowInstanceRef.State.COMPLETED
                    && instance.state() != WorkflowInstanceRef.State.TERMINATED;
            case MANUAL_COMPENSATE -> instance.state() == WorkflowInstanceRef.State.INCIDENT
                    || instance.state() == WorkflowInstanceRef.State.TERMINATED;
        };
        if (!allowed) {
            throw new IllegalStateException("当前状态不允许执行管理动作 " + command.action());
        }
    }
}
