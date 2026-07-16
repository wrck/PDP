package com.pdp.workflow.infrastructure.event;

import com.pdp.workflow.domain.WorkflowIncident;
import com.pdp.workflow.domain.WorkflowInstanceRef;
import com.pdp.workflow.port.WorkflowRepository;
import java.time.Clock;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public final class WorkflowResultEventBridge {
    private final WorkflowRepository repository;
    private final Clock clock;
    private final Supplier<UUID> ids;

    public WorkflowResultEventBridge(WorkflowRepository repository, Clock clock, Supplier<UUID> ids) {
        this.repository = Objects.requireNonNull(repository);
        this.clock = Objects.requireNonNull(clock);
        this.ids = Objects.requireNonNull(ids);
    }

    public void handle(ResultEvent event) {
        WorkflowInstanceRef instance = repository.findInstance(event.workflowInstanceId())
                .orElseThrow(() -> new IllegalArgumentException("流程实例不存在"));
        if (event.success()) {
            WorkflowInstanceRef.State target = event.targetState() == null
                    ? WorkflowInstanceRef.State.ACTIVE : event.targetState();
            repository.saveInstance(instance.transition(target, event.activityKeys(), clock.instant()), null);
        } else {
            WorkflowInstanceRef failed = instance.state() == WorkflowInstanceRef.State.INCIDENT
                    ? instance : instance.transition(WorkflowInstanceRef.State.INCIDENT, event.activityKeys(), clock.instant());
            repository.saveInstance(failed, null);
            repository.saveIncident(new WorkflowIncident(ids.get(), instance.id(), event.code(),
                    event.message(), WorkflowIncident.Status.OPEN, event.attempts(), clock.instant(), null));
        }
    }

    public record ResultEvent(UUID workflowInstanceId, boolean success,
                              WorkflowInstanceRef.State targetState, Set<String> activityKeys,
                              String code, String message, int attempts) {
        public ResultEvent {
            activityKeys = Set.copyOf(activityKeys == null ? Set.of() : activityKeys);
        }
    }
}
