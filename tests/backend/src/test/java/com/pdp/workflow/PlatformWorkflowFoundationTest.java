package com.pdp.workflow;

import static org.assertj.core.api.Assertions.*;

import com.pdp.workflow.application.WorkflowTaskService;
import com.pdp.workflow.domain.*;
import com.pdp.workflow.infrastructure.event.WorkflowResultEventBridge;
import com.pdp.workflow.port.*;
import java.time.*;
import java.util.*;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.junit.jupiter.api.Test;

class PlatformWorkflowFoundationTest {
    private static final String BPMN_V1 = waitingProcess("approval.flow", "等待消息");
    private static final String BPMN_V2 = waitingProcess("approval.flow", "等待消息 V2");

    @Test
    void deploysBpmnPinsRunningVersionAndSupportsSuspendResume() {
        ProcessEngine engine = newEngine("version-" + UUID.randomUUID(),
                ProcessEngineConfiguration.DB_SCHEMA_UPDATE_CREATE);
        try {
            var repository = engine.getRepositoryService();
            var runtime = engine.getRuntimeService();
            var v1 = repository.createDeployment().name("approval-1.0.0")
                    .addString("approval-v1.bpmn20.xml", BPMN_V1).deploy();
            var definitionV1 = repository.createProcessDefinitionQuery()
                    .deploymentId(v1.getId()).singleResult();
            var instance = runtime.startProcessInstanceById(definitionV1.getId(), "approval:A-1");
            var v2 = repository.createDeployment().name("approval-2.0.0")
                    .addString("approval-v2.bpmn20.xml", BPMN_V2).deploy();

            assertThat(repository.createProcessDefinitionQuery().deploymentId(v2.getId())
                    .singleResult().getVersion()).isEqualTo(2);
            assertThat(instance.getProcessDefinitionId()).isEqualTo(definitionV1.getId());
            runtime.suspendProcessInstanceById(instance.getId());
            assertThat(runtime.createProcessInstanceQuery().processInstanceId(instance.getId())
                    .singleResult().isSuspended()).isTrue();
            runtime.activateProcessInstanceById(instance.getId());
            assertThat(runtime.createProcessInstanceQuery().processInstanceId(instance.getId())
                    .singleResult().isSuspended()).isFalse();
        } finally {
            engine.close();
        }
    }

    @Test
    void joinsParallelTimerAndCorrelatedMessageBranches() {
        ProcessEngine engine = newEngine("parallel-" + UUID.randomUUID(),
                ProcessEngineConfiguration.DB_SCHEMA_UPDATE_CREATE);
        try {
            engine.getRepositoryService().createDeployment()
                    .addString("parallel.bpmn20.xml", parallelTimerAndMessageProcess()).deploy();
            var runtime = engine.getRuntimeService();
            var instance = runtime.startProcessInstanceByKey("parallel.flow", "approval:A-2");

            var timer = engine.getManagementService().createTimerJobQuery()
                    .processInstanceId(instance.getId()).singleResult();
            var messageExecution = runtime.createExecutionQuery()
                    .processInstanceId(instance.getId())
                    .messageEventSubscriptionName("approval.continue")
                    .singleResult();
            assertThat(timer).isNotNull();
            assertThat(messageExecution).isNotNull();

            engine.getManagementService().moveTimerToExecutableJob(timer.getId());
            var executableTimer = engine.getManagementService().createJobQuery()
                    .processInstanceId(instance.getId()).singleResult();
            engine.getManagementService().executeJob(executableTimer.getId());
            runtime.messageEventReceived("approval.continue", messageExecution.getId());

            assertThat(runtime.createProcessInstanceQuery().processInstanceId(instance.getId())
                    .singleResult()).isNull();
        } finally {
            engine.close();
        }
    }

    @Test
    void retriesAsyncFailureAndMovesItToDeadLetter() {
        ProcessEngine engine = newEngine("deadletter-" + UUID.randomUUID(),
                ProcessEngineConfiguration.DB_SCHEMA_UPDATE_CREATE);
        try {
            engine.getRepositoryService().createDeployment()
                    .addString("failure.bpmn20.xml", failingAsyncProcess()).deploy();
            var instance = engine.getRuntimeService()
                    .startProcessInstanceByKey("failure.flow", "approval:A-3");
            var management = engine.getManagementService();

            for (int attempt = 0; attempt < 3; attempt++) {
                var job = management.createJobQuery()
                        .processInstanceId(instance.getId()).singleResult();
                if (job == null) {
                    var retryTimer = management.createTimerJobQuery()
                            .processInstanceId(instance.getId()).singleResult();
                    assertThat(retryTimer)
                            .as("第 %s 次重试前应存在重试定时作业", attempt + 1)
                            .isNotNull();
                    management.moveTimerToExecutableJob(retryTimer.getId());
                    job = management.createJobQuery()
                            .processInstanceId(instance.getId()).singleResult();
                }
                assertThat(job).as("第 %s 次重试前应存在可执行作业", attempt + 1).isNotNull();
                String jobId = job.getId();
                assertThatThrownBy(() -> management.executeJob(jobId))
                        .isInstanceOf(RuntimeException.class)
                        .hasRootCauseMessage("预期的异步失败");
            }

            assertThat(management.createDeadLetterJobQuery()
                    .processInstanceId(instance.getId()).singleResult()).isNotNull();
        } finally {
            engine.close();
        }
    }

    @Test
    void recoversRunningInstanceAfterEngineRestart() {
        String databaseName = "recovery-" + UUID.randomUUID();
        ProcessEngine first = newEngine(databaseName, ProcessEngineConfiguration.DB_SCHEMA_UPDATE_CREATE);
        String instanceId;
        try {
            first.getRepositoryService().createDeployment()
                    .addString("recovery.bpmn20.xml", waitingProcess("recovery.flow", "等待恢复"))
                    .deploy();
            instanceId = first.getRuntimeService()
                    .startProcessInstanceByKey("recovery.flow", "approval:A-4").getId();
        } finally {
            first.close();
        }

        ProcessEngine recovered = newEngine(databaseName, ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        try {
            assertThat(recovered.getRuntimeService().createProcessInstanceQuery()
                    .processInstanceId(instanceId).singleResult()).isNotNull();
        } finally {
            recovered.close();
        }
    }

    @Test
    void rechecksAuthorizationWhenTaskIsClaimed() {
        MemoryRepository repository = new MemoryRepository();
        UUID actor = UUID.randomUUID();
        WorkflowTaskRef task = new WorkflowTaskRef(
                UUID.randomUUID(), UUID.randomUUID(), "review", "复核", null,
                Set.of(actor), WorkflowTaskRef.State.CREATED, 0, Instant.EPOCH, null);
        repository.tasks.put(task.id(), task);
        WorkflowAuthorizationPort authorization = new WorkflowAuthorizationPort() {
            public boolean canView(UUID workspaceId, UUID actorId, WorkflowTaskRef value) {
                return true;
            }

            public boolean canClaim(UUID actorId, WorkflowTaskRef value) {
                return false;
            }

            public boolean canComplete(UUID actorId, WorkflowTaskRef value) {
                return false;
            }
        };
        WorkflowTaskService service = new WorkflowTaskService(
                repository, authorization, command -> {}, Clock.systemUTC(), UUID::randomUUID);

        assertThatThrownBy(() -> service.claim(
                new WorkflowTaskPort.ClaimCommand(task.id(), actor, 0, "claim-1")))
                .isInstanceOf(SecurityException.class);
        assertThat(repository.tasks.get(task.id()).state()).isEqualTo(WorkflowTaskRef.State.CREATED);
    }

    @Test
    void recordsRecoverableIncidentWithoutLeakingEngineFailure() {
        MemoryRepository repository = new MemoryRepository();
        WorkflowInstanceRef instance = new WorkflowInstanceRef(
                UUID.randomUUID(), UUID.randomUUID(),
                new WorkflowBusinessRef(UUID.randomUUID(), "approval", UUID.randomUUID()),
                WorkflowInstanceRef.State.ACTIVE, Set.of("wait"), 0, 0, Instant.EPOCH);
        repository.instances.put(instance.id(), instance);
        new WorkflowResultEventBridge(repository, Clock.systemUTC(), UUID::randomUUID)
                .handle(new WorkflowResultEventBridge.ResultEvent(
                        instance.id(), false, null, Set.of("wait"),
                        "ENGINE_UNAVAILABLE", "引擎不可用", 3));

        assertThat(repository.instances.get(instance.id()).state())
                .isEqualTo(WorkflowInstanceRef.State.INCIDENT);
        assertThat(repository.incidents).singleElement()
                .extracting(WorkflowIncident::status).isEqualTo(WorkflowIncident.Status.OPEN);
    }

    private static ProcessEngine newEngine(String databaseName, String schemaUpdate) {
        return ProcessEngineConfiguration.createStandaloneProcessEngineConfiguration()
                .setJdbcUrl("jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1")
                .setJdbcDriver("org.h2.Driver")
                .setJdbcUsername("sa")
                .setJdbcPassword("")
                .setDatabaseSchemaUpdate(schemaUpdate)
                .setAsyncExecutorActivate(false)
                .buildProcessEngine();
    }

    private static String waitingProcess(String key, String taskName) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             targetNamespace="urn:pdp:test">
                  <process id="%s" name="%s" isExecutable="true">
                    <startEvent id="start"/>
                    <sequenceFlow id="toWait" sourceRef="start" targetRef="wait"/>
                    <receiveTask id="wait" name="%s"/>
                  </process>
                </definitions>
                """.formatted(key, taskName, taskName);
    }

    private static String parallelTimerAndMessageProcess() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             targetNamespace="urn:pdp:test">
                  <message id="continueMessage" name="approval.continue"/>
                  <process id="parallel.flow" isExecutable="true">
                    <startEvent id="start"/>
                    <parallelGateway id="split"/>
                    <intermediateCatchEvent id="timerWait">
                      <timerEventDefinition><timeDuration>PT0.01S</timeDuration></timerEventDefinition>
                    </intermediateCatchEvent>
                    <intermediateCatchEvent id="messageWait">
                      <messageEventDefinition messageRef="continueMessage"/>
                    </intermediateCatchEvent>
                    <parallelGateway id="join"/>
                    <endEvent id="end"/>
                    <sequenceFlow id="f1" sourceRef="start" targetRef="split"/>
                    <sequenceFlow id="f2" sourceRef="split" targetRef="timerWait"/>
                    <sequenceFlow id="f3" sourceRef="split" targetRef="messageWait"/>
                    <sequenceFlow id="f4" sourceRef="timerWait" targetRef="join"/>
                    <sequenceFlow id="f5" sourceRef="messageWait" targetRef="join"/>
                    <sequenceFlow id="f6" sourceRef="join" targetRef="end"/>
                  </process>
                </definitions>
                """;
    }

    private static String failingAsyncProcess() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn"
                             targetNamespace="urn:pdp:test">
                  <process id="failure.flow" isExecutable="true">
                    <startEvent id="start"/>
                    <serviceTask id="alwaysFail" flowable:async="true"
                                 flowable:class="com.pdp.workflow.PlatformWorkflowFoundationTest$AlwaysFailDelegate"/>
                    <endEvent id="end"/>
                    <sequenceFlow id="f1" sourceRef="start" targetRef="alwaysFail"/>
                    <sequenceFlow id="f2" sourceRef="alwaysFail" targetRef="end"/>
                  </process>
                </definitions>
                """;
    }

    public static final class AlwaysFailDelegate implements JavaDelegate {
        @Override
        public void execute(DelegateExecution execution) {
            throw new IllegalStateException("预期的异步失败");
        }
    }

    private static final class MemoryRepository implements WorkflowRepository {
        final Map<UUID, WorkflowInstanceRef> instances = new HashMap<>();
        final Map<UUID, WorkflowTaskRef> tasks = new HashMap<>();
        final List<WorkflowIncident> incidents = new ArrayList<>();

        public Optional<WorkflowDefinition> findDefinition(UUID id) { return Optional.empty(); }
        public Optional<WorkflowDefinition> findDefinition(String key, String version) { return Optional.empty(); }
        public WorkflowDefinition saveDefinition(WorkflowDefinition value) { return value; }
        public Optional<WorkflowDeployment> findDeploymentByIdempotencyKey(String key) { return Optional.empty(); }
        public WorkflowDeployment saveDeployment(WorkflowDeployment value) { return value; }
        public Optional<WorkflowInstanceRef> findInstance(UUID id) { return Optional.ofNullable(instances.get(id)); }
        public Optional<WorkflowInstanceRef> findInstanceByIdempotencyKey(String key) { return Optional.empty(); }
        public WorkflowInstanceRef saveInstance(WorkflowInstanceRef value, String key) {
            instances.put(value.id(), value);
            return value;
        }
        public Optional<WorkflowTaskRef> findTask(UUID id) { return Optional.ofNullable(tasks.get(id)); }
        public List<WorkflowTaskRef> findTasks(UUID id) { return List.copyOf(tasks.values()); }
        public WorkflowTaskRef saveTask(WorkflowTaskRef value) {
            tasks.put(value.id(), value);
            return value;
        }
        public List<WorkflowIncident> findIncidents(UUID id) { return List.copyOf(incidents); }
        public WorkflowIncident saveIncident(WorkflowIncident value) {
            incidents.add(value);
            return value;
        }
    }
}
