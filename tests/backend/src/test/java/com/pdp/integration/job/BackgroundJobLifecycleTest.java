package com.pdp.integration.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pdp.operations.job.BackgroundJobCoordinator;
import com.pdp.operations.job.BackgroundJobCoordinator.BackgroundJob;
import com.pdp.operations.job.BackgroundJobCoordinator.BatchContext;
import com.pdp.operations.job.BackgroundJobCoordinator.BatchResult;
import com.pdp.operations.job.BackgroundJobCoordinator.FailureItem;
import com.pdp.operations.job.BackgroundJobCoordinator.JobProcessor;
import com.pdp.operations.job.BackgroundJobCoordinator.JobRepository;
import com.pdp.operations.job.BackgroundJobCoordinator.JobType;
import com.pdp.operations.job.BackgroundJobCoordinator.ResourceBudget;
import com.pdp.operations.job.BackgroundJobCoordinator.ResourceBudgetExceededException;
import com.pdp.operations.job.BackgroundJobCoordinator.ResourceCapacity;
import com.pdp.operations.job.BackgroundJobCoordinator.Status;
import com.pdp.operations.job.BackgroundJobCoordinator.SubmitCommand;
import com.pdp.operations.projection.ProjectionRebuildJob;
import com.pdp.operations.projection.ProjectionRebuildJob.Operation;
import com.pdp.operations.projection.ProjectionRebuildJob.ProjectionPage;
import com.pdp.operations.projection.ProjectionRebuildJob.ProjectionRecord;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BackgroundJobLifecycleTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-17T00:00:00Z"), ZoneOffset.UTC);

  @Test
  void 批量作业应暂停且从原检查点恢复直到完成() {
    InMemoryJobRepository repository = new InMemoryJobRepository();
    ScriptedProcessor processor =
        new ScriptedProcessor(
            JobType.BULK_IMPORT,
            BatchResult.advanced("cursor-2", 2, 2, List.of()),
            BatchResult.completed("cursor-4", 2, 2, List.of(), null));
    BackgroundJobCoordinator coordinator = coordinator(repository, processor);
    UUID workspaceId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    SubmitCommand command =
        new SubmitCommand(
            workspaceId,
            JobType.BULK_IMPORT,
            Map.of("objectType", "task", "ids", List.of("1", "2", "3", "4")),
            actorId,
            "import-task-001",
            4,
            new ResourceBudget(2, 1, 1, 4, 2, 2));

    BackgroundJob submitted = coordinator.submit(command);
    BackgroundJob repeated = coordinator.submit(command);
    BackgroundJob firstBatch = coordinator.runNextBatch(submitted.id());

    assertThat(repeated.id()).isEqualTo(submitted.id());
    assertThat(firstBatch.status()).isEqualTo(Status.RUNNING);
    assertThat(firstBatch.progress()).isEqualTo(50.0d);
    assertThat(firstBatch.checkpoint().cursor()).isEqualTo("cursor-2");
    assertThat(firstBatch.checkpoint().processedItems()).isEqualTo(2);

    BackgroundJob paused = coordinator.pause(submitted.id());
    BackgroundJob ignoredWhilePaused = coordinator.runNextBatch(submitted.id());

    assertThat(paused.status()).isEqualTo(Status.PAUSED);
    assertThat(ignoredWhilePaused.checkpoint()).isEqualTo(firstBatch.checkpoint());
    assertThat(processor.calls()).isEqualTo(1);

    BackgroundJob resumed = coordinator.resume(submitted.id());
    BackgroundJob completed = coordinator.runUntilYield(submitted.id());

    assertThat(resumed.status()).isEqualTo(Status.QUEUED);
    assertThat(resumed.checkpoint()).isEqualTo(firstBatch.checkpoint());
    assertThat(completed.status()).isEqualTo(Status.COMPLETED);
    assertThat(completed.progress()).isEqualTo(100.0d);
    assertThat(completed.checkpoint().processedItems()).isEqualTo(4);
    assertThat(completed.checkpoint().successfulItems()).isEqualTo(4);
    assertThat(completed.finishedAt()).isEqualTo(CLOCK.instant());

    SubmitCommand conflicting =
        new SubmitCommand(
            workspaceId,
            JobType.BULK_IMPORT,
            Map.of("objectType", "deliverable"),
            actorId,
            "import-task-001",
            4,
            command.resourceBudget());
    assertThatThrownBy(() -> coordinator.submit(conflicting))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("幂等键");
  }

  @Test
  void 取消作业后不得继续处理且已完成作业不得取消() {
    InMemoryJobRepository repository = new InMemoryJobRepository();
    ScriptedProcessor processor =
        new ScriptedProcessor(
            JobType.BULK_EXPORT,
            BatchResult.completed("cursor-1", 1, 1, List.of(), UUID.randomUUID()));
    BackgroundJobCoordinator coordinator = coordinator(repository, processor);

    BackgroundJob cancelled =
        coordinator.cancel(
            coordinator
                .submit(command(JobType.BULK_EXPORT, "export-cancel", 1, budget(1, 1, 1)))
                .id());
    BackgroundJob afterDispatch = coordinator.runNextBatch(cancelled.id());

    assertThat(afterDispatch.status()).isEqualTo(Status.CANCELLED);
    assertThat(afterDispatch.finishedAt()).isEqualTo(CLOCK.instant());
    assertThat(processor.calls()).isZero();

    BackgroundJob completed =
        coordinator.runNextBatch(
            coordinator
                .submit(command(JobType.BULK_EXPORT, "export-complete", 1, budget(1, 1, 1)))
                .id());
    assertThat(completed.status()).isEqualTo(Status.COMPLETED);
    assertThat(completed.resultFileId()).isNotNull();
    assertThatThrownBy(() -> coordinator.cancel(completed.id()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("已完成");
  }

  @Test
  void 投影重建失败应保留明细和原检查点并可安全重放() {
    InMemoryJobRepository repository = new InMemoryJobRepository();
    AtomicBoolean failFirstWrite = new AtomicBoolean(true);
    List<String> applied = new ArrayList<>();
    ProjectionRebuildJob projection =
        new ProjectionRebuildJob(
            (workspaceId, projectionKey, checkpoint, batchSize) ->
                new ProjectionPage(
                    List.of(
                        new ProjectionRecord("project-1", 1, Operation.UPSERT, Map.of()),
                        new ProjectionRecord("project-2", 1, Operation.UPSERT, Map.of())),
                    "cursor-2",
                    true),
            (workspaceId, projectionKey, record) -> {
              if ("project-2".equals(record.itemKey()) && failFirstWrite.getAndSet(false)) {
                throw new IllegalStateException("projection unavailable");
              }
              applied.add(record.itemKey());
            },
            CLOCK);
    BackgroundJobCoordinator coordinator = coordinator(repository, projection);
    BackgroundJob submitted =
        coordinator.submit(
            new SubmitCommand(
                UUID.randomUUID(),
                JobType.PROJECTION_REBUILD,
                Map.of("projectionKey", "search"),
                UUID.randomUUID(),
                "search-rebuild-001",
                2,
                new ResourceBudget(2, 1, 1, 2, 1, 1)));

    BackgroundJob failed = coordinator.runNextBatch(submitted.id());

    assertThat(failed.status()).isEqualTo(Status.FAILED);
    assertThat(failed.progress()).isZero();
    assertThat(failed.checkpoint().cursor()).isNull();
    assertThat(failed.checkpoint().processedItems()).isZero();
    assertThat(failed.failureCount()).isEqualTo(1);
    assertThat(failed.failureItems())
        .singleElement()
        .satisfies(
            failure -> {
              assertThat(failure.itemKey()).isEqualTo("project-2");
              assertThat(failure.code()).isEqualTo("PROJECTION_REBUILD_ITEM_FAILED");
              assertThat(failure.retryable()).isTrue();
            });

    BackgroundJob resumed = coordinator.resume(submitted.id());
    BackgroundJob completed = coordinator.runNextBatch(submitted.id());

    assertThat(resumed.checkpoint()).isEqualTo(failed.checkpoint());
    assertThat(completed.status()).isEqualTo(Status.COMPLETED);
    assertThat(completed.progress()).isEqualTo(100.0d);
    assertThat(completed.checkpoint().processedItems()).isEqualTo(2);
    assertThat(completed.failureCount()).isEqualTo(1);
    assertThat(completed.failureItems()).hasSize(1);
    assertThat(applied).containsExactly("project-1", "project-1", "project-2");
  }

  @Test
  void 资源预算应在入队和执行租约阶段拒绝超配() {
    InMemoryJobRepository repository = new InMemoryJobRepository();
    ScriptedProcessor processor =
        new ScriptedProcessor(
            JobType.STATISTICS, BatchResult.completed("cursor-1", 1, 1, List.of(), null));
    ResourceCapacity capacity = new ResourceCapacity(1, 2, 5, 2);
    var resources = BackgroundJobCoordinator.boundedResources(capacity);
    BackgroundJobCoordinator coordinator =
        new BackgroundJobCoordinator(
            repository, List.of(processor), resources, CLOCK, UUID::randomUUID);

    assertThatThrownBy(
            () ->
                coordinator.submit(
                    command(
                        JobType.STATISTICS,
                        "statistics-batch-too-large",
                        1,
                        new ResourceBudget(3, 1, 1, 1, 0, 1))))
        .isInstanceOf(ResourceBudgetExceededException.class)
        .hasMessageContaining("批次");

    assertThatThrownBy(
            () ->
                coordinator.submit(
                    command(
                        JobType.STATISTICS,
                        "statistics-items-too-large",
                        6,
                        new ResourceBudget(2, 1, 1, 5, 0, 1))))
        .isInstanceOf(ResourceBudgetExceededException.class)
        .hasMessageContaining("预计条目数");

    ResourceBudget singleUnit = new ResourceBudget(1, 1, 1, 1, 0, 1);
    try (var firstLease = resources.acquire(UUID.randomUUID(), singleUnit)) {
      assertThatThrownBy(() -> resources.acquire(UUID.randomUUID(), singleUnit))
          .isInstanceOf(ResourceBudgetExceededException.class)
          .hasMessageContaining("暂时不足");
    }
    try (var releasedLease = resources.acquire(UUID.randomUUID(), singleUnit)) {
      assertThat(releasedLease).isNotNull();
    }
  }

  @Test
  void 失败明细应按预算截断但保留准确失败总数() {
    InMemoryJobRepository repository = new InMemoryJobRepository();
    List<FailureItem> failures =
        List.of(
            failure("task-1", "ROW_INVALID"),
            failure("task-2", "ROW_INVALID"),
            failure("task-3", "ROW_INVALID"));
    ScriptedProcessor processor =
        new ScriptedProcessor(
            JobType.ARCHIVE, BatchResult.completed("cursor-3", 3, 0, failures, null));
    BackgroundJobCoordinator coordinator = coordinator(repository, processor);
    BackgroundJob completed =
        coordinator.runNextBatch(
            coordinator
                .submit(
                    command(
                        JobType.ARCHIVE,
                        "archive-with-failures",
                        3,
                        new ResourceBudget(3, 1, 1, 3, 3, 2)))
                .id());

    assertThat(completed.status()).isEqualTo(Status.COMPLETED);
    assertThat(completed.progress()).isEqualTo(100.0d);
    assertThat(completed.failureCount()).isEqualTo(3);
    assertThat(completed.checkpoint().failedItems()).isEqualTo(3);
    assertThat(completed.failureItems())
        .extracting(FailureItem::itemKey)
        .containsExactly("task-1", "task-2");
  }

  private static BackgroundJobCoordinator coordinator(
      InMemoryJobRepository repository, JobProcessor processor) {
    return new BackgroundJobCoordinator(
        repository,
        List.of(processor),
        BackgroundJobCoordinator.permitAllResources(),
        CLOCK,
        UUID::randomUUID);
  }

  private static SubmitCommand command(
      JobType jobType, String idempotencyKey, long totalItems, ResourceBudget budget) {
    return new SubmitCommand(
        UUID.randomUUID(),
        jobType,
        Map.of("objectType", "task"),
        UUID.randomUUID(),
        idempotencyKey,
        totalItems,
        budget);
  }

  private static ResourceBudget budget(
      int batchSize, long maxItems, int maxFailureDetails) {
    return new ResourceBudget(batchSize, 1, 1, maxItems, maxFailureDetails, maxFailureDetails);
  }

  private static FailureItem failure(String itemKey, String code) {
    return new FailureItem(itemKey, code, "测试失败", false, CLOCK.instant());
  }

  private static final class ScriptedProcessor implements JobProcessor {
    private final JobType jobType;
    private final ArrayDeque<BatchResult> results;
    private final AtomicInteger calls = new AtomicInteger();

    private ScriptedProcessor(JobType jobType, BatchResult... results) {
      this.jobType = jobType;
      this.results = new ArrayDeque<>(List.of(results));
    }

    @Override
    public JobType jobType() {
      return jobType;
    }

    @Override
    public BatchResult process(BatchContext context) {
      calls.incrementAndGet();
      BatchResult result = results.pollFirst();
      if (result == null) {
        throw new IllegalStateException("未配置批次结果");
      }
      return result;
    }

    int calls() {
      return calls.get();
    }
  }

  private static final class InMemoryJobRepository implements JobRepository {
    private final Map<UUID, BackgroundJob> jobs = new ConcurrentHashMap<>();
    private final Map<IdempotencyKey, UUID> idempotencyKeys = new ConcurrentHashMap<>();

    @Override
    public Optional<BackgroundJob> findById(UUID jobId) {
      return Optional.ofNullable(jobs.get(jobId));
    }

    @Override
    public Optional<BackgroundJob> findByIdempotencyKey(
        UUID workspaceId, String idempotencyKey) {
      return Optional.ofNullable(idempotencyKeys.get(new IdempotencyKey(workspaceId, idempotencyKey)))
          .map(jobs::get);
    }

    @Override
    public void insert(BackgroundJob job) {
      IdempotencyKey key = new IdempotencyKey(job.workspaceId(), job.idempotencyKey());
      if (idempotencyKeys.putIfAbsent(key, job.id()) != null
          || jobs.putIfAbsent(job.id(), job) != null) {
        throw new IllegalStateException("重复后台作业");
      }
    }

    @Override
    public void update(BackgroundJob job, long expectedRevision) {
      jobs.compute(
          job.id(),
          (jobId, current) -> {
            if (current == null || current.revision() != expectedRevision) {
              throw new IllegalStateException("后台作业版本冲突");
            }
            return job;
          });
    }
  }

  private record IdempotencyKey(UUID workspaceId, String value) {}
}
