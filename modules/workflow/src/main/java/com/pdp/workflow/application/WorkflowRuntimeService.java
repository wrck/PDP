package com.pdp.workflow.application;

import com.pdp.workflow.domain.*;
import com.pdp.workflow.port.*;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public final class WorkflowRuntimeService implements WorkflowRuntimePort {
    private final WorkflowRepository repository;
    private final WorkflowOrchestrationOutbox outbox;
    private final Clock clock;
    private final Supplier<UUID> ids;

    public WorkflowRuntimeService(
            WorkflowRepository repository, WorkflowOrchestrationOutbox outbox, Clock clock, Supplier<UUID> ids) {
        this.repository = Objects.requireNonNull(repository);
        this.outbox = Objects.requireNonNull(outbox);
        this.clock = Objects.requireNonNull(clock);
        this.ids = Objects.requireNonNull(ids);
    }

    @Override
    public WorkflowInstanceRef start(StartCommand command) {
        Objects.requireNonNull(command, "command");
        Optional<WorkflowInstanceRef> existing = repository.findInstanceByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent()) {
            return existing.get();
        }
        WorkflowDefinition definition = repository.findDefinition(command.definitionId())
                .filter(value -> value.status() == WorkflowDefinition.Status.DEPLOYED)
                .orElseThrow(() -> new IllegalStateException("流程定义未部署"));
        WorkflowInstanceRef instance = new WorkflowInstanceRef(ids.get(), definition.id(),
                command.businessObjectRef(), WorkflowInstanceRef.State.STARTING, Set.of(), 0, 0, clock.instant());
        instance = repository.saveInstance(instance, command.idempotencyKey());
        Map<String, String> data = new LinkedHashMap<>(safe(command.variables()));
        data.put("definitionId", definition.id().toString());
        outbox.append(new WorkflowOrchestrationCommand(ids.get(), WorkflowOrchestrationCommand.Type.START,
                instance.id(), command.idempotencyKey(), data, clock.instant()));
        return instance;
    }

    @Override
    public void advance(AdvanceCommand command) {
        requireActive(command.workflowInstanceId());
        Map<String, String> data = new LinkedHashMap<>(safe(command.variables()));
        data.put("activityKey", command.activityKey());
        append(command.workflowInstanceId(), WorkflowOrchestrationCommand.Type.ADVANCE,
                command.idempotencyKey(), data);
    }

    @Override
    public void correlateMessage(MessageCommand command) {
        requireActive(command.workflowInstanceId());
        Map<String, String> data = new LinkedHashMap<>(safe(command.variables()));
        data.put("messageKey", command.messageKey());
        append(command.workflowInstanceId(), WorkflowOrchestrationCommand.Type.CORRELATE_MESSAGE,
                command.idempotencyKey(), data);
    }

    @Override
    public Optional<WorkflowInstanceRef> find(UUID workflowInstanceId) {
        return repository.findInstance(workflowInstanceId);
    }

    private void requireActive(UUID id) {
        WorkflowInstanceRef instance = repository.findInstance(id)
                .orElseThrow(() -> new IllegalArgumentException("流程实例不存在"));
        if (instance.state() != WorkflowInstanceRef.State.ACTIVE) {
            throw new IllegalStateException("流程实例当前不可推进");
        }
    }

    private void append(UUID id, WorkflowOrchestrationCommand.Type type, String key, Map<String, String> data) {
        outbox.append(new WorkflowOrchestrationCommand(ids.get(), type, id, key, data, clock.instant()));
    }

    private static Map<String, String> safe(Map<String, String> values) {
        return values == null ? Map.of() : values;
    }
}
