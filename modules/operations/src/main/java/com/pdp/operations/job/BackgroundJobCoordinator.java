package com.pdp.operations.job;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * 统一协调批量导入、导出、归档、统计、投影重建和补偿类后台作业。
 *
 * <p>协调器只管理稳定作业语义，不依赖调度框架、数据库实现或具体业务模块。处理器每次最多推进一个
 * 受资源预算约束的批次，暂停和取消在批次边界生效；检查点必须由处理器设计为可安全重放。
 */
public final class BackgroundJobCoordinator {
    private final JobRepository repository;
    private final Map<JobType, JobProcessor> processors;
    private final ResourceBudgetController resourceBudgets;
    private final Clock clock;
    private final Supplier<UUID> ids;
    private final Map<UUID, Object> jobLocks = new ConcurrentHashMap<>();

    public BackgroundJobCoordinator(
            JobRepository repository,
            Collection<? extends JobProcessor> processors,
            ResourceBudgetController resourceBudgets,
            Clock clock,
            Supplier<UUID> ids) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.resourceBudgets = Objects.requireNonNull(resourceBudgets, "resourceBudgets");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ids = Objects.requireNonNull(ids, "ids");
        Objects.requireNonNull(processors, "processors");
        Map<JobType, JobProcessor> registered = new EnumMap<>(JobType.class);
        for (JobProcessor processor : processors) {
            Objects.requireNonNull(processor, "processor");
            if (registered.putIfAbsent(processor.jobType(), processor) != null) {
                throw new IllegalArgumentException("同一后台作业类型只能注册一个处理器: " + processor.jobType());
            }
        }
        this.processors = Map.copyOf(registered);
    }

    /** 幂等接受作业请求，调用方可在五秒交互时限内取得稳定作业标识。 */
    public BackgroundJob submit(SubmitCommand command) {
        Objects.requireNonNull(command, "command");
        JobProcessor processor = processors.get(command.jobType());
        if (processor == null) {
            throw new IllegalArgumentException("后台作业类型尚未注册处理器: " + command.jobType());
        }
        resourceBudgets.validate(command.resourceBudget());
        if (command.totalItems() > command.resourceBudget().maxItems()) {
            throw new ResourceBudgetExceededException(
                    "作业预计条目数超过资源预算: "
                            + command.totalItems()
                            + " > "
                            + command.resourceBudget().maxItems());
        }
        Optional<BackgroundJob> repeated =
                repository.findByIdempotencyKey(command.workspaceId(), command.idempotencyKey());
        if (repeated.isPresent()) {
            if (!repeated.get().matches(command)) {
                throw new IllegalStateException("相同幂等键不能用于不同的后台作业请求");
            }
            return repeated.get();
        }
        Instant now = clock.instant();
        BackgroundJob created =
                new BackgroundJob(
                        ids.get(),
                        command.workspaceId(),
                        command.jobType(),
                        command.scope(),
                        command.requestedBy(),
                        command.idempotencyKey(),
                        command.totalItems(),
                        command.resourceBudget(),
                        Status.QUEUED,
                        0.0d,
                        Checkpoint.initial(now),
                        List.of(),
                        0,
                        null,
                        now,
                        now,
                        null,
                        null,
                        0);
        repository.insert(created);
        return created;
    }

    /**
     * 推进一个批次。
     *
     * <p>调度器可重复调用此方法。已暂停、取消、完成或失败的作业不会被隐式继续，失败作业必须显式恢复。
     */
    public BackgroundJob runNextBatch(UUID jobId) {
        Objects.requireNonNull(jobId, "jobId");
        synchronized (lockFor(jobId)) {
            BackgroundJob current = requireJob(jobId);
            if (current.status() == Status.PAUSED
                    || current.status() == Status.CANCELLED
                    || current.status() == Status.COMPLETED
                    || current.status() == Status.FAILED) {
                return current;
            }
            JobProcessor processor = processors.get(current.jobType());
            if (processor == null) {
                return fail(
                        current,
                        new FailureItem(
                                "job:" + current.id(),
                                "JOB_PROCESSOR_MISSING",
                                "后台作业处理器未注册",
                                false,
                                clock.instant()));
            }
            try (ResourceLease ignored =
                    resourceBudgets.acquire(current.id(), current.resourceBudget())) {
                BackgroundJob running = startIfQueued(current);
                BatchResult result;
                try {
                    result =
                            Objects.requireNonNull(
                                    processor.process(
                                            new BatchContext(
                                                    running.id(),
                                                    running.workspaceId(),
                                                    running.scope(),
                                                    running.checkpoint(),
                                                    running.resourceBudget().batchSize(),
                                                    Math.max(
                                                            0,
                                                            running.totalItems()
                                                                    - running
                                                                            .checkpoint()
                                                                            .processedItems()))),
                                    "处理器不得返回 null");
                } catch (RuntimeException exception) {
                    return fail(
                            running,
                            new FailureItem(
                                    "batch:" + running.checkpoint().completedBatches(),
                                    "JOB_BATCH_EXECUTION_FAILED",
                                    "后台批次执行异常: " + exception.getClass().getSimpleName(),
                                    true,
                                    clock.instant()));
                }
                return applyBatchResult(running, result);
            }
        }
    }

    /** 在单次调度时间片内最多推进资源预算允许的批次数，防止单个作业独占执行器。 */
    public BackgroundJob runUntilYield(UUID jobId) {
        BackgroundJob current = requireJob(jobId);
        int maxBatches = current.resourceBudget().maxBatchesPerRun();
        for (int index = 0; index < maxBatches; index++) {
            current = runNextBatch(jobId);
            if (current.status() != Status.RUNNING) {
                break;
            }
        }
        return current;
    }

    public BackgroundJob pause(UUID jobId) {
        return transition(
                jobId,
                current -> {
                    if (current.status() == Status.PAUSED) {
                        return current;
                    }
                    requireStatus(current, Status.QUEUED, Status.RUNNING);
                    return current.withStatus(Status.PAUSED, clock.instant(), false);
                });
    }

    /** 从原检查点恢复暂停或失败的作业，历史失败明细继续保留。 */
    public BackgroundJob resume(UUID jobId) {
        return transition(
                jobId,
                current -> {
                    if (current.status() == Status.QUEUED || current.status() == Status.RUNNING) {
                        return current;
                    }
                    requireStatus(current, Status.PAUSED, Status.FAILED);
                    return current.withStatus(Status.QUEUED, clock.instant(), false);
                });
    }

    public BackgroundJob cancel(UUID jobId) {
        return transition(
                jobId,
                current -> {
                    if (current.status() == Status.CANCELLED) {
                        return current;
                    }
                    if (current.status() == Status.COMPLETED) {
                        throw new IllegalStateException("已完成作业不能取消");
                    }
                    return current.withStatus(Status.CANCELLED, clock.instant(), true);
                });
    }

    public Optional<BackgroundJob> find(UUID jobId) {
        return repository.findById(jobId);
    }

    private BackgroundJob applyBatchResult(BackgroundJob current, BatchResult result) {
        validateBatchResult(current, result);
        if (result.halt()) {
            FailureItem failure =
                    result.failures().isEmpty()
                            ? new FailureItem(
                                    "batch:" + current.checkpoint().completedBatches(),
                                    "JOB_BATCH_HALTED",
                                    "后台批次要求停止",
                                    true,
                                    clock.instant())
                            : result.failures().getFirst();
            return fail(current, failure, result.failures());
        }
        long failureCount = current.failureCount() + result.failures().size();
        List<FailureItem> failureItems =
                appendFailureDetails(
                        current.failureItems(),
                        result.failures(),
                        current.resourceBudget().maxFailureDetails());
        Instant now = clock.instant();
        Checkpoint checkpoint =
                current.checkpoint()
                        .advance(
                                result.nextCursor(),
                                result.processedItems(),
                                result.successfulItems(),
                                result.failures().size(),
                                now);
        boolean failureBudgetExceeded =
                failureCount > current.resourceBudget().maxFailureItems();
        Status nextStatus =
                failureBudgetExceeded
                        ? Status.FAILED
                        : result.complete() ? Status.COMPLETED : Status.RUNNING;
        double progress = progress(checkpoint.processedItems(), current.totalItems(), result.complete());
        BackgroundJob updated =
                new BackgroundJob(
                        current.id(),
                        current.workspaceId(),
                        current.jobType(),
                        current.scope(),
                        current.requestedBy(),
                        current.idempotencyKey(),
                        current.totalItems(),
                        current.resourceBudget(),
                        nextStatus,
                        progress,
                        checkpoint,
                        failureItems,
                        failureCount,
                        result.resultFileId() == null
                                ? current.resultFileId()
                                : result.resultFileId(),
                        current.createdAt(),
                        now,
                        current.startedAt(),
                        nextStatus == Status.COMPLETED || nextStatus == Status.FAILED ? now : null,
                        current.revision() + 1);
        repository.update(updated, current.revision());
        return updated;
    }

    private BackgroundJob fail(BackgroundJob current, FailureItem failure) {
        return fail(current, failure, List.of(failure));
    }

    private BackgroundJob fail(
            BackgroundJob current, FailureItem representative, List<FailureItem> failures) {
        List<FailureItem> recorded = failures.isEmpty() ? List.of(representative) : failures;
        Instant now = clock.instant();
        BackgroundJob failed =
                new BackgroundJob(
                        current.id(),
                        current.workspaceId(),
                        current.jobType(),
                        current.scope(),
                        current.requestedBy(),
                        current.idempotencyKey(),
                        current.totalItems(),
                        current.resourceBudget(),
                        Status.FAILED,
                        current.progress(),
                        current.checkpoint(),
                        appendFailureDetails(
                                current.failureItems(),
                                recorded,
                                current.resourceBudget().maxFailureDetails()),
                        current.failureCount() + recorded.size(),
                        current.resultFileId(),
                        current.createdAt(),
                        now,
                        current.startedAt(),
                        now,
                        current.revision() + 1);
        repository.update(failed, current.revision());
        return failed;
    }

    private BackgroundJob startIfQueued(BackgroundJob current) {
        if (current.status() == Status.RUNNING) {
            return current;
        }
        Instant now = clock.instant();
        BackgroundJob running =
                new BackgroundJob(
                        current.id(),
                        current.workspaceId(),
                        current.jobType(),
                        current.scope(),
                        current.requestedBy(),
                        current.idempotencyKey(),
                        current.totalItems(),
                        current.resourceBudget(),
                        Status.RUNNING,
                        current.progress(),
                        current.checkpoint(),
                        current.failureItems(),
                        current.failureCount(),
                        current.resultFileId(),
                        current.createdAt(),
                        now,
                        current.startedAt() == null ? now : current.startedAt(),
                        null,
                        current.revision() + 1);
        repository.update(running, current.revision());
        return running;
    }

    private BackgroundJob transition(
            UUID jobId, java.util.function.UnaryOperator<BackgroundJob> transition) {
        Objects.requireNonNull(jobId, "jobId");
        synchronized (lockFor(jobId)) {
            BackgroundJob current = requireJob(jobId);
            BackgroundJob updated = transition.apply(current);
            if (updated == current) {
                return current;
            }
            repository.update(updated, current.revision());
            return updated;
        }
    }

    private Object lockFor(UUID jobId) {
        return jobLocks.computeIfAbsent(jobId, ignored -> new Object());
    }

    private BackgroundJob requireJob(UUID jobId) {
        return repository
                .findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("后台作业不存在: " + jobId));
    }

    private static void requireStatus(BackgroundJob job, Status... allowed) {
        for (Status status : allowed) {
            if (job.status() == status) {
                return;
            }
        }
        throw new IllegalStateException("后台作业状态不允许该操作: " + job.status());
    }

    private static void validateBatchResult(BackgroundJob job, BatchResult result) {
        if (result.processedItems() > job.resourceBudget().batchSize()) {
            throw new IllegalStateException("处理器返回的条目数超过批次预算");
        }
        if (result.processedItems() > job.totalItems() - job.checkpoint().processedItems()) {
            throw new IllegalStateException("处理器返回的条目数超过剩余工作量");
        }
        if (result.complete()
                && job.checkpoint().processedItems() + result.processedItems()
                        != job.totalItems()) {
            throw new IllegalStateException("完成批次的累计处理条目必须等于作业总条目");
        }
        if (!result.complete() && !result.halt() && result.processedItems() == 0) {
            throw new IllegalStateException("未完成批次必须推进检查点或显式停止");
        }
        if (!result.complete()
                && Objects.equals(result.nextCursor(), job.checkpoint().cursor())
                && result.processedItems() > 0) {
            throw new IllegalStateException("未完成批次必须推进游标");
        }
        if (!result.complete() && result.resultFileId() != null) {
            throw new IllegalStateException("结果文件只能在作业完成时登记");
        }
    }

    private static List<FailureItem> appendFailureDetails(
            List<FailureItem> existing, List<FailureItem> added, int limit) {
        if (limit == 0 || added.isEmpty()) {
            return existing;
        }
        ArrayList<FailureItem> merged = new ArrayList<>(Math.min(limit, existing.size() + added.size()));
        merged.addAll(existing.stream().limit(limit).toList());
        int remaining = limit - merged.size();
        if (remaining > 0) {
            merged.addAll(added.stream().limit(remaining).toList());
        }
        return List.copyOf(merged);
    }

    private static double progress(long processedItems, long totalItems, boolean complete) {
        if (complete) {
            return 100.0d;
        }
        if (totalItems == 0) {
            return 0.0d;
        }
        double value = Math.min(100.0d, processedItems * 100.0d / totalItems);
        return Math.round(value * 100.0d) / 100.0d;
    }

    public enum JobType {
        BULK_IMPORT,
        BULK_EXPORT,
        ARCHIVE,
        STATISTICS,
        PROJECTION_REBUILD,
        DOMAIN_MIGRATION,
        INTEGRATION_COMPENSATION
    }

    public enum Status {
        QUEUED,
        RUNNING,
        PAUSED,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    public record SubmitCommand(
            UUID workspaceId,
            JobType jobType,
            Map<String, Object> scope,
            UUID requestedBy,
            String idempotencyKey,
            long totalItems,
            ResourceBudget resourceBudget) {
        public SubmitCommand {
            Objects.requireNonNull(jobType, "jobType");
            Objects.requireNonNull(requestedBy, "requestedBy");
            idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
            scope = immutableScope(scope);
            Objects.requireNonNull(resourceBudget, "resourceBudget");
            if (totalItems < 0) {
                throw new IllegalArgumentException("totalItems 不得为负数");
            }
        }
    }

    /**
     * 单个作业的资源预算。批次大小限制暂停/取消响应延迟；每次调度批次数限制执行器独占；并发单元由
     * 全局资源控制器租用；失败明细上限限制内存和持久化膨胀。
     */
    public record ResourceBudget(
            int batchSize,
            int maxBatchesPerRun,
            int concurrencyUnits,
            long maxItems,
            int maxFailureItems,
            int maxFailureDetails) {
        public ResourceBudget {
            if (batchSize < 1) {
                throw new IllegalArgumentException("batchSize 必须大于 0");
            }
            if (maxBatchesPerRun < 1) {
                throw new IllegalArgumentException("maxBatchesPerRun 必须大于 0");
            }
            if (concurrencyUnits < 1) {
                throw new IllegalArgumentException("concurrencyUnits 必须大于 0");
            }
            if (maxItems < 0) {
                throw new IllegalArgumentException("maxItems 不得为负数");
            }
            if (maxFailureItems < 0 || maxFailureDetails < 0) {
                throw new IllegalArgumentException("失败预算不得为负数");
            }
            if (maxFailureDetails > maxFailureItems && maxFailureItems > 0) {
                throw new IllegalArgumentException("失败明细上限不得超过失败条目预算");
            }
        }
    }

    /** 平台执行器可提供的硬容量，用于在作业进入队列前拒绝不可能满足的预算。 */
    public record ResourceCapacity(
            int concurrencyUnits, int maxBatchSize, long maxItemsPerJob, int maxFailureDetailsPerJob) {
        public ResourceCapacity {
            if (concurrencyUnits < 1
                    || maxBatchSize < 1
                    || maxItemsPerJob < 0
                    || maxFailureDetailsPerJob < 0) {
                throw new IllegalArgumentException("资源容量配置无效");
            }
        }
    }

    public record Checkpoint(
            String cursor,
            long processedItems,
            long successfulItems,
            long failedItems,
            long completedBatches,
            Instant updatedAt) {
        public Checkpoint {
            if (processedItems < 0
                    || successfulItems < 0
                    || failedItems < 0
                    || completedBatches < 0) {
                throw new IllegalArgumentException("检查点计数不得为负数");
            }
            if (successfulItems + failedItems > processedItems) {
                throw new IllegalArgumentException("成功与失败计数不能超过已处理条目");
            }
            Objects.requireNonNull(updatedAt, "updatedAt");
        }

        static Checkpoint initial(Instant now) {
            return new Checkpoint(null, 0, 0, 0, 0, now);
        }

        Checkpoint advance(
                String nextCursor,
                long processedDelta,
                long successfulDelta,
                long failedDelta,
                Instant now) {
            return new Checkpoint(
                    nextCursor,
                    processedItems + processedDelta,
                    successfulItems + successfulDelta,
                    failedItems + failedDelta,
                    completedBatches + 1,
                    now);
        }
    }

    public record FailureItem(
            String itemKey, String code, String message, boolean retryable, Instant occurredAt) {
        public FailureItem {
            itemKey = requireText(itemKey, "itemKey");
            code = requireText(code, "code");
            message = requireText(message, "message");
            Objects.requireNonNull(occurredAt, "occurredAt");
        }
    }

    public record BackgroundJob(
            UUID id,
            UUID workspaceId,
            JobType jobType,
            Map<String, Object> scope,
            UUID requestedBy,
            String idempotencyKey,
            long totalItems,
            ResourceBudget resourceBudget,
            Status status,
            double progress,
            Checkpoint checkpoint,
            List<FailureItem> failureItems,
            long failureCount,
            UUID resultFileId,
            Instant createdAt,
            Instant updatedAt,
            Instant startedAt,
            Instant finishedAt,
            long revision) {
        public BackgroundJob {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(jobType, "jobType");
            scope = immutableScope(scope);
            Objects.requireNonNull(requestedBy, "requestedBy");
            idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
            Objects.requireNonNull(resourceBudget, "resourceBudget");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(checkpoint, "checkpoint");
            failureItems = List.copyOf(failureItems == null ? List.of() : failureItems);
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(updatedAt, "updatedAt");
            if (totalItems < 0 || failureCount < 0 || revision < 0) {
                throw new IllegalArgumentException("作业计数和版本不得为负数");
            }
            if (progress < 0.0d || progress > 100.0d) {
                throw new IllegalArgumentException("progress 必须在 0 到 100 之间");
            }
        }

        boolean matches(SubmitCommand command) {
            return Objects.equals(workspaceId, command.workspaceId())
                    && jobType == command.jobType()
                    && scope.equals(command.scope())
                    && requestedBy.equals(command.requestedBy())
                    && totalItems == command.totalItems()
                    && resourceBudget.equals(command.resourceBudget());
        }

        BackgroundJob withStatus(Status nextStatus, Instant now, boolean terminal) {
            return new BackgroundJob(
                    id,
                    workspaceId,
                    jobType,
                    scope,
                    requestedBy,
                    idempotencyKey,
                    totalItems,
                    resourceBudget,
                    nextStatus,
                    progress,
                    checkpoint,
                    failureItems,
                    failureCount,
                    resultFileId,
                    createdAt,
                    now,
                    startedAt,
                    terminal ? now : null,
                    revision + 1);
        }
    }

    public record BatchContext(
            UUID jobId,
            UUID workspaceId,
            Map<String, Object> scope,
            Checkpoint checkpoint,
            int batchSize,
            long remainingItems) {
        public BatchContext {
            Objects.requireNonNull(jobId, "jobId");
            scope = immutableScope(scope);
            Objects.requireNonNull(checkpoint, "checkpoint");
            if (batchSize < 1 || remainingItems < 0) {
                throw new IllegalArgumentException("批次上下文无效");
            }
        }
    }

    public record BatchResult(
            String nextCursor,
            long processedItems,
            long successfulItems,
            List<FailureItem> failures,
            boolean complete,
            boolean halt,
            UUID resultFileId) {
        public BatchResult {
            failures = List.copyOf(failures == null ? List.of() : failures);
            if (processedItems < 0 || successfulItems < 0 || successfulItems > processedItems) {
                throw new IllegalArgumentException("批次处理计数无效");
            }
            if (failures.size() > processedItems && !halt) {
                throw new IllegalArgumentException("失败明细不能超过批次处理条目数");
            }
            if (complete && halt) {
                throw new IllegalArgumentException("批次不能同时完成和停止");
            }
        }

        public static BatchResult advanced(
                String nextCursor,
                long processedItems,
                long successfulItems,
                List<FailureItem> failures) {
            return new BatchResult(
                    nextCursor, processedItems, successfulItems, failures, false, false, null);
        }

        public static BatchResult completed(
                String nextCursor,
                long processedItems,
                long successfulItems,
                List<FailureItem> failures,
                UUID resultFileId) {
            return new BatchResult(
                    nextCursor,
                    processedItems,
                    successfulItems,
                    failures,
                    true,
                    false,
                    resultFileId);
        }

        public static BatchResult failed(String currentCursor, FailureItem failure) {
            return new BatchResult(
                    currentCursor, 0, 0, List.of(failure), false, true, null);
        }
    }

    public interface JobProcessor {
        JobType jobType();

        BatchResult process(BatchContext context);
    }

    /**
     * 仓储实现必须以 expectedRevision 执行乐观并发更新，并保证 workspaceId + idempotencyKey 唯一。
     */
    public interface JobRepository {
        Optional<BackgroundJob> findById(UUID jobId);

        Optional<BackgroundJob> findByIdempotencyKey(UUID workspaceId, String idempotencyKey);

        void insert(BackgroundJob job);

        void update(BackgroundJob job, long expectedRevision);
    }

    public interface ResourceBudgetController {
        void validate(ResourceBudget budget);

        ResourceLease acquire(UUID jobId, ResourceBudget budget);
    }

    @FunctionalInterface
    public interface ResourceLease extends AutoCloseable {
        @Override
        void close();
    }

    public static ResourceBudgetController permitAllResources() {
        return new ResourceBudgetController() {
            @Override
            public void validate(ResourceBudget budget) {
                Objects.requireNonNull(budget, "budget");
            }

            @Override
            public ResourceLease acquire(UUID jobId, ResourceBudget budget) {
                validate(budget);
                return () -> {};
            }
        };
    }

    public static ResourceBudgetController boundedResources(ResourceCapacity capacity) {
        return new BoundedResourceBudgetController(capacity);
    }

    public static final class ResourceBudgetExceededException extends IllegalStateException {
        public ResourceBudgetExceededException(String message) {
            super(message);
        }
    }

    private static final class BoundedResourceBudgetController
            implements ResourceBudgetController {
        private final ResourceCapacity capacity;
        private final AtomicInteger leasedUnits = new AtomicInteger();

        private BoundedResourceBudgetController(ResourceCapacity capacity) {
            this.capacity = Objects.requireNonNull(capacity, "capacity");
        }

        @Override
        public void validate(ResourceBudget budget) {
            Objects.requireNonNull(budget, "budget");
            if (budget.concurrencyUnits() > capacity.concurrencyUnits()) {
                throw new ResourceBudgetExceededException("作业并发单元超过平台容量");
            }
            if (budget.batchSize() > capacity.maxBatchSize()) {
                throw new ResourceBudgetExceededException("作业批次大小超过平台容量");
            }
            if (budget.maxItems() > capacity.maxItemsPerJob()) {
                throw new ResourceBudgetExceededException("作业最大条目数超过平台容量");
            }
            if (budget.maxFailureDetails() > capacity.maxFailureDetailsPerJob()) {
                throw new ResourceBudgetExceededException("作业失败明细预算超过平台容量");
            }
        }

        @Override
        public ResourceLease acquire(UUID jobId, ResourceBudget budget) {
            Objects.requireNonNull(jobId, "jobId");
            validate(budget);
            int requested = budget.concurrencyUnits();
            while (true) {
                int current = leasedUnits.get();
                if (current + requested > capacity.concurrencyUnits()) {
                    throw new ResourceBudgetExceededException("后台作业执行资源暂时不足");
                }
                if (leasedUnits.compareAndSet(current, current + requested)) {
                    return () -> leasedUnits.addAndGet(-requested);
                }
            }
        }
    }

    private static Map<String, Object> immutableScope(Map<String, ?> scope) {
        if (scope == null || scope.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> copied = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : scope.entrySet()) {
            String key = requireText(entry.getKey(), "scope key");
            Object value = Objects.requireNonNull(entry.getValue(), "scope value");
            if (copied.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("scope key 规范化后重复: " + key);
            }
        }
        return Collections.unmodifiableMap(copied);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return value.strip();
    }
}
