package com.pdp.workflow.application;

import static org.assertj.core.api.Assertions.*;

import com.pdp.workflow.domain.*;
import com.pdp.workflow.infrastructure.flowable.WorkflowAsyncExecutorConfig;
import com.pdp.workflow.port.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class WorkflowFoundationTest {
    private static final Instant NOW = Instant.parse("2026-07-17T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String BPMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         targetNamespace="urn:pdp:test">
              <process id="approval.flow" isExecutable="true">
                <startEvent id="start"/>
              </process>
            </definitions>
            """;

    @Test
    void validatesAndDeploysStableDefinitionIdempotently() {
        MemoryStore store = new MemoryStore();
        WorkflowDefinitionService service = new WorkflowDefinitionService(store, CLOCK, UUID::randomUUID);
        var validation = service.validate(new WorkflowDefinitionPort.ValidateCommand(
                "approval.flow", "1.0.0", null, BPMN));
        assertThat(validation.valid()).isTrue();

        var command = new WorkflowDefinitionPort.DeployCommand(
                "approval.flow", "1.0.0", null, BPMN, validation.contentHash(), "deploy-1");
        WorkflowDefinition first = service.deploy(command);
        WorkflowDefinition repeated = service.deploy(command);
        assertThat(first.status()).isEqualTo(WorkflowDefinition.Status.DEPLOYED);
        assertThat(repeated.id()).isEqualTo(first.id());
        assertThat(store.deployments).hasSize(1);
    }

    @Test
    void startsThroughOutboxAndDeduplicatesByIdempotencyKey() {
        MemoryStore store = new MemoryStore();
        WorkflowDefinition definition = deployed();
        store.saveDefinition(definition);
        WorkflowRuntimeService service =
                new WorkflowRuntimeService(store, store, CLOCK, UUID::randomUUID);
        var command = new WorkflowRuntimePort.StartCommand(definition.id(),
                new WorkflowBusinessRef(UUID.randomUUID(), "approval", UUID.randomUUID()),
                "start-1", Map.of("approvalId", "A-1"));
        WorkflowInstanceRef first = service.start(command);
        WorkflowInstanceRef repeated = service.start(command);
        assertThat(repeated.id()).isEqualTo(first.id());
        assertThat(store.commands).hasSize(1);
        assertThat(store.commands.getFirst().type()).isEqualTo(WorkflowOrchestrationCommand.Type.START);
    }

    @Test
    void rechecksAuthorizationForClaimAndComplete() {
        MemoryStore store = new MemoryStore();
        UUID actor = UUID.randomUUID();
        WorkflowTaskRef task = new WorkflowTaskRef(UUID.randomUUID(), UUID.randomUUID(), "review",
                "复核", null, Set.of(actor), WorkflowTaskRef.State.CREATED, 0, NOW, null);
        store.saveTask(task);
        WorkflowAuthorizationPort authorization = new WorkflowAuthorizationPort() {
            public boolean canView(UUID workspaceId, UUID actorId, WorkflowTaskRef value) { return true; }
            public boolean canClaim(UUID actorId, WorkflowTaskRef value) { return actor.equals(actorId); }
            public boolean canComplete(UUID actorId, WorkflowTaskRef value) { return false; }
        };
        WorkflowTaskService service =
                new WorkflowTaskService(store, authorization, store, CLOCK, UUID::randomUUID);
        WorkflowTaskRef claimed = service.claim(
                new WorkflowTaskPort.ClaimCommand(task.id(), actor, 0, "claim-1"));
        assertThat(claimed.state()).isEqualTo(WorkflowTaskRef.State.CLAIMED);
        assertThatThrownBy(() -> service.complete(
                new WorkflowTaskPort.CompleteCommand(task.id(), actor, 1, "approve", "complete-1")))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void enforcesAdministrationStateAndMigrationPreview() {
        MemoryStore store = new MemoryStore();
        WorkflowInstanceRef instance = new WorkflowInstanceRef(UUID.randomUUID(), UUID.randomUUID(),
                new WorkflowBusinessRef(UUID.randomUUID(), "project", UUID.randomUUID()),
                WorkflowInstanceRef.State.ACTIVE, Set.of("review"), 0, 3, NOW);
        store.saveInstance(instance, "instance-1");
        WorkflowAdministrationService service =
                new WorkflowAdministrationService(store, store, CLOCK, UUID::randomUUID);
        assertThatThrownBy(() -> service.apply(new WorkflowAdministrationPort.ActionCommand(
                instance.id(), WorkflowAdministrationPort.Action.MIGRATE, "迁移到新版本",
                3, UUID.randomUUID(), null, "migrate-1"))).isInstanceOf(IllegalStateException.class);
        var accepted = service.apply(new WorkflowAdministrationPort.ActionCommand(
                instance.id(), WorkflowAdministrationPort.Action.PAUSE, "计划维护暂停",
                3, null, UUID.randomUUID(), "pause-1"));
        assertThat(accepted.status()).isEqualTo("QUEUED");
        assertThat(store.commands.getLast().type()).isEqualTo(WorkflowOrchestrationCommand.Type.PAUSE);
    }

    @Test
    void validatesIndependentAsyncExecutorBudget() {
        var policy = new WorkflowAsyncExecutorConfig.Policy(
                2, 4, 100, 3, Duration.ofSeconds(5), Duration.ofSeconds(30), 8, 3);
        var executor = new WorkflowAsyncExecutorConfig(policy).createExecutor();
        assertThat(executor.getCorePoolSize()).isEqualTo(2);
        assertThat(executor.getQueue().remainingCapacity()).isEqualTo(100);
        executor.shutdownNow();
        assertThatThrownBy(() -> new WorkflowAsyncExecutorConfig.Policy(
                4, 2, 0, -1, Duration.ZERO, Duration.ZERO, 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static WorkflowDefinition deployed() {
        return new WorkflowDefinition(UUID.randomUUID(), "approval.flow", "1.0.0", "hash",
                null, WorkflowDefinition.Status.DEPLOYED, NOW);
    }

    private static final class MemoryStore implements WorkflowRepository, WorkflowOrchestrationOutbox {
        final Map<UUID, WorkflowDefinition> definitions = new HashMap<>();
        final Map<String, WorkflowDeployment> deployments = new HashMap<>();
        final Map<UUID, WorkflowInstanceRef> instances = new HashMap<>();
        final Map<String, UUID> instanceKeys = new HashMap<>();
        final Map<UUID, WorkflowTaskRef> tasks = new HashMap<>();
        final List<WorkflowIncident> incidents = new ArrayList<>();
        final List<WorkflowOrchestrationCommand> commands = new ArrayList<>();

        public Optional<WorkflowDefinition> findDefinition(UUID id) { return Optional.ofNullable(definitions.get(id)); }
        public Optional<WorkflowDefinition> findDefinition(String key, String version) {
            return definitions.values().stream().filter(v -> v.processDefinitionKey().equals(key)
                    && v.businessVersion().equals(version)).findFirst();
        }
        public WorkflowDefinition saveDefinition(WorkflowDefinition value) { definitions.put(value.id(), value); return value; }
        public Optional<WorkflowDeployment> findDeploymentByIdempotencyKey(String key) {
            return Optional.ofNullable(deployments.get(key));
        }
        public WorkflowDeployment saveDeployment(WorkflowDeployment value) {
            deployments.put(value.idempotencyKey(), value); return value;
        }
        public Optional<WorkflowInstanceRef> findInstance(UUID id) { return Optional.ofNullable(instances.get(id)); }
        public Optional<WorkflowInstanceRef> findInstanceByIdempotencyKey(String key) {
            return Optional.ofNullable(instanceKeys.get(key)).map(instances::get);
        }
        public WorkflowInstanceRef saveInstance(WorkflowInstanceRef value, String key) {
            instances.put(value.id(), value); if (key != null) instanceKeys.putIfAbsent(key, value.id()); return value;
        }
        public Optional<WorkflowTaskRef> findTask(UUID id) { return Optional.ofNullable(tasks.get(id)); }
        public List<WorkflowTaskRef> findTasks(UUID instanceId) {
            return tasks.values().stream().filter(v -> instanceId == null || v.workflowInstanceId().equals(instanceId)).toList();
        }
        public WorkflowTaskRef saveTask(WorkflowTaskRef value) { tasks.put(value.id(), value); return value; }
        public List<WorkflowIncident> findIncidents(UUID instanceId) {
            return incidents.stream().filter(v -> v.workflowInstanceId().equals(instanceId)).toList();
        }
        public WorkflowIncident saveIncident(WorkflowIncident value) { incidents.add(value); return value; }
        public void append(WorkflowOrchestrationCommand command) { commands.add(command); }
    }
}
