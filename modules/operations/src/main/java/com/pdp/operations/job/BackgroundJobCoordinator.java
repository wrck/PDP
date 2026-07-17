package com.pdp.operations.job;

import com.pdp.shared.context.ActorRef;
import com.pdp.shared.context.WorkspaceId;
import com.pdp.shared.error.ConflictException;
import com.pdp.shared.error.ErrorCode;
import com.pdp.shared.error.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 后台作业协调器（{@link BackgroundJobPort} 实现，spec.md 状态机表"后台作业"行）。
 *
 * <p>核心职责：
 * <ol>
 *   <li><b>提交</b>：校验幂等键、创建 QUEUED 状态作业；</li>
 *   <li><b>调度</b>：定时拉取 QUEUED 作业，校验资源预算后转为 RUNNING 并分派给 {@link JobHandler}；</li>
 *   <li><b>执行监控</b>：通过 {@link JobContext.CoordinatorCallbacks} 接收检查点、失败和预算检查回调；</li>
 *   <li><b>超时检测</b>：定时检测 RUNNING 超时作业，自动暂停（保留检查点）；</li>
 *   <li><b>终态处理</b>：根据 {@link JobExecutionResult} 将作业转为 COMPLETED/FAILED/CANCELLED，
 *       持久化结果文件 ID、摘要和失败明细；</li>
 *   <li><b>失败重试</b>：FAILED 状态作业可由 {@link #retry} 重新入队，从检查点继续。</li>
 * </ol>
 *
 * <p><strong>幂等性</strong>：{@code (workspace_id, idempotency_key)} 唯一约束防止重复提交；
 * {@link JobHandler} 实现负责单条目幂等。
 *
 * <p><strong>资源预算</strong>：超时自动暂停（{@link JobResourceBudget#maxDuration()}）；
 * 失败超阈值自动失败（{@link JobResourceBudget#maxFailureCount()}）。
 *
 * <p><strong>线程模型</strong>：作业在独立线程池执行（{@code virtualThreadExecutor}），
 * 主调度循环单线程拉取，避免阻塞。Spring Boot 4.1 + Java 21 虚拟线程适配 IO 密集作业；
 * CPU 密集作业（如统计）由 {@link JobResourceBudget} 限制并发。
 */
@Service
public class BackgroundJobCoordinator implements BackgroundJobPort, JobContext.CoordinatorCallbacks {

    private static final Logger LOG = LoggerFactory.getLogger(BackgroundJobCoordinator.class);
    private static final int DISPATCH_BATCH_SIZE = 10;
    private static final int TIMEOUT_CHECK_BATCH_SIZE = 50;

    private final BackgroundJobRepository repository;
    private final Map<BackgroundJobType, JobHandler> handlersByType;
    private final ExecutorService virtualThreadExecutor;
    private final Map<UUID, Future<?>> runningJobs = new ConcurrentHashMap<>();
    private final Map<UUID, JobContext> runningContexts = new ConcurrentHashMap<>();
    private final AtomicInteger concurrentDbConnections = new AtomicInteger(0);

    public BackgroundJobCoordinator(BackgroundJobRepository repository,
                                    ObjectProvider<JobHandler> handlersProvider) {
        this.repository = Objects.requireNonNull(repository, "repository 不能为 null");
        this.handlersByType = handlersProvider.stream()
                .collect(Collectors.toUnmodifiableMap(
                        JobHandler::supportedType,
                        h -> h));
        // Java 21 虚拟线程适配 IO 密集后台作业
        this.virtualThreadExecutor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("bg-job-", 0).factory());
        LOG.info("后台作业协调器初始化完成，已注册 {} 个 JobHandler: {}",
                handlersByType.size(), handlersByType.keySet());
    }

    // ============= BackgroundJobPort 实现 =============

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BackgroundJob submit(String idempotencyKey,
                                WorkspaceId workspaceId,
                                BackgroundJobType jobType,
                                String scope,
                                ActorRef requestedBy,
                                JobResourceBudget resourceBudget) {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey 不能为 null");
        Objects.requireNonNull(workspaceId, "workspaceId 不能为 null");
        Objects.requireNonNull(jobType, "jobType 不能为 null");
        Objects.requireNonNull(requestedBy, "requestedBy 不能为 null");
        Objects.requireNonNull(resourceBudget, "resourceBudget 不能为 null");

        // 幂等检查
        Optional<BackgroundJob> existing = repository.findByIdempotencyKey(workspaceId, idempotencyKey);
        if (existing.isPresent()) {
            ConflictException conflict = new ConflictException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "幂等键已存在: " + idempotencyKey);
            conflict.reason("IDEMPOTENCY_KEY_REUSED")
                    .nextStep("使用相同幂等键查询已存在作业状态");
            throw conflict;
        }

        // 校验作业类型有注册处理器（提前失败，避免 QUEUED 后无法调度）
        if (!handlersByType.containsKey(jobType)) {
            throw JobExecutionException.handlerNotRegistered(jobType);
        }

        BackgroundJob job = BackgroundJob.create(idempotencyKey, workspaceId, jobType,
                scope, requestedBy, resourceBudget);
        return repository.save(job);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BackgroundJob pause(UUID jobId, ActorRef requestedBy, String reason) {
        Objects.requireNonNull(jobId, "jobId 不能为 null");
        BackgroundJob job = loadJob(jobId);
        // 标记取消请求，让执行线程主动退出
        JobContext ctx = runningContexts.get(jobId);
        if (ctx != null) {
            ctx.requestCancel();
        }
        BackgroundJob paused = job.pause(reason);
        return repository.save(paused);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BackgroundJob resume(UUID jobId, ActorRef requestedBy) {
        Objects.requireNonNull(jobId, "jobId 不能为 null");
        BackgroundJob job = loadJob(jobId);
        BackgroundJob resumed = job.resume();
        return repository.save(resumed);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BackgroundJob cancel(UUID jobId, ActorRef requestedBy, String reason) {
        Objects.requireNonNull(jobId, "jobId 不能为 null");
        BackgroundJob job = loadJob(jobId);
        // 标记取消请求
        JobContext ctx = runningContexts.get(jobId);
        if (ctx != null) {
            ctx.requestCancel();
        }
        BackgroundJob cancelled = job.cancel(reason);
        return repository.save(cancelled);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BackgroundJob retry(UUID jobId, ActorRef requestedBy) {
        Objects.requireNonNull(jobId, "jobId 不能为 null");
        BackgroundJob job = loadJob(jobId);
        BackgroundJob requeued = job.requeue();
        return repository.save(requeued);
    }

    @Override
    public Optional<BackgroundJob> getJob(UUID jobId) {
        Objects.requireNonNull(jobId, "jobId 不能为 null");
        return repository.findById(jobId);
    }

    @Override
    public List<BackgroundJob> listJobs(WorkspaceId workspaceId,
                                        BackgroundJobStatus status,
                                        BackgroundJobType jobType,
                                        int offset,
                                        int limit) {
        return repository.findByWorkspace(workspaceId, status, jobType, offset, limit);
    }

    @Override
    public long countJobs(WorkspaceId workspaceId, BackgroundJobStatus status, BackgroundJobType jobType) {
        return repository.countByWorkspace(workspaceId, status, jobType);
    }

    // ============= 调度循环 =============

    /**
     * 定时拉取并分派 QUEUED 作业（每 5 秒）。
     * <p>由 Spring {@code @Scheduled} 触发，单线程顺序拉取避免重复分派。
     */
    @Scheduled(fixedDelayString = "${pdp.jobs.dispatch-interval-ms:5000}")
    public void dispatchQueuedJobs() {
        try {
            List<BackgroundJob> queued = repository.findDispatchable(DISPATCH_BATCH_SIZE);
            for (BackgroundJob job : queued) {
                dispatch(job);
            }
        } catch (Exception e) {
            LOG.error("调度 QUEUED 作业失败", e);
        }
    }

    /**
     * 定时检测超时作业（每 30 秒）。
     * <p>RUNNING 状态且启动时间早于 {@code maxDuration} 阈值的作业自动暂停。
     */
    @Scheduled(fixedDelayString = "${pdp.jobs.timeout-check-interval-ms:30000}")
    public void detectTimedOutJobs() {
        try {
            Instant threshold = Instant.now();
            List<BackgroundJob> timedOut = repository.findTimedOutJobs(threshold, TIMEOUT_CHECK_BATCH_SIZE);
            for (BackgroundJob job : timedOut) {
                try {
                    Duration elapsed = job.startedAt()
                            .map(start -> Duration.between(start, Instant.now()))
                            .orElse(Duration.ZERO);
                    if (elapsed.compareTo(job.resourceBudget().maxDuration()) > 0) {
                        pauseDueToTimeout(job);
                    }
                } catch (Exception e) {
                    LOG.error("超时暂停作业 {} 失败", job.id(), e);
                }
            }
        } catch (Exception e) {
            LOG.error("超时检测失败", e);
        }
    }

    // ============= 分派与执行 =============

    private void dispatch(BackgroundJob job) {
        JobHandler handler = handlersByType.get(job.jobType());
        if (handler == null) {
            LOG.error("作业 {} 类型 {} 未注册处理器，标记为失败", job.id(), job.jobType());
            failJobWithException(job, JobExecutionException.handlerNotRegistered(job.jobType()));
            return;
        }

        // 校验数据库连接预算
        int requiredConnections = job.resourceBudget().maxConcurrentDbConnections();
        while (concurrentDbConnections.get() + requiredConnections > maxGlobalDbConnections()) {
            LOG.warn("数据库连接预算不足 (current={}, required={})，作业 {} 等待",
                    concurrentDbConnections.get(), requiredConnections, job.id());
            return; // 下次调度再试
        }

        try {
            BackgroundJob started = job.start();
            started = repository.save(started);
            concurrentDbConnections.addAndGet(requiredConnections);

            JobContext context = new JobContext(started, buildOperatorFromJob(started), this);
            runningContexts.put(started.id(), context);

            Future<?> future = virtualThreadExecutor.submit(() -> executeSafely(started, handler, context));
            runningJobs.put(started.id(), future);
        } catch (Exception e) {
            LOG.error("启动作业 {} 失败", job.id(), e);
            concurrentDbConnections.addAndGet(-requiredConnections);
        }
    }

    private void executeSafely(BackgroundJob job, JobHandler handler, JobContext context) {
        try {
            LOG.info("作业 {} 开始执行 (type={}, scope={})", job.id(), job.jobType(), job.scope());
            JobExecutionResult result = handler.execute(context);
            handleExecutionResult(job, result);
        } catch (JobExecutionException e) {
            LOG.warn("作业 {} 执行异常: {} (可重试={})", job.id(), e.getMessage(), e.isRetryable(), e);
            failJobWithException(job, e);
        } catch (Exception e) {
            LOG.error("作业 {} 执行未预期异常", job.id(), e);
            failJobWithException(job, JobExecutionException.fatalBusinessError(
                    "未预期异常: " + e.getMessage(), "联系平台管理员排查日志"));
        } finally {
            runningJobs.remove(job.id());
            runningContexts.remove(job.id());
            concurrentDbConnections.addAndGet(-job.resourceBudget().maxConcurrentDbConnections());
        }
    }

    private void handleExecutionResult(BackgroundJob job, JobExecutionResult result) {
        try {
            BackgroundJob updated = switch (result.status()) {
                case COMPLETED, COMPLETED_WITH_FAILURES -> job.complete(result);
                case FAILED -> job.fail(result);
                case CANCELLED -> job.cancel(result.summary());
            };
            repository.save(updated);
            LOG.info("作业 {} 终态: {} (failures={})", job.id(), updated.status(), updated.failureCount());
        } catch (Exception e) {
            LOG.error("作业 {} 终态持久化失败", job.id(), e);
        }
    }

    private void failJobWithException(BackgroundJob job, JobExecutionException e) {
        try {
            JobExecutionResult result = JobExecutionResult.failed(
                    e.getMessage(), List.of(), job.checkpoint() != null ? job.checkpoint() : JobCheckpoint.empty(),
                    Instant.now());
            BackgroundJob failed = job.fail(result);
            repository.save(failed);
        } catch (Exception persistError) {
            LOG.error("作业 {} 失败状态持久化失败", job.id(), persistError);
        }
    }

    private void pauseDueToTimeout(BackgroundJob job) {
        try {
            // 标记取消请求
            JobContext ctx = runningContexts.get(job.id());
            if (ctx != null) {
                ctx.requestCancel();
            }
            BackgroundJob paused = job.pause("超时自动暂停 (maxDuration=" + job.resourceBudget().maxDuration() + ")");
            repository.save(paused);
            LOG.warn("作业 {} 超时自动暂停", job.id());
        } catch (Exception e) {
            LOG.error("作业 {} 超时暂停失败", job.id(), e);
        }
    }

    // ============= CoordinatorCallbacks 实现 =============

    @Override
    public void onCheckpoint(UUID jobId, JobCheckpoint checkpoint, List<JobFailureItem> failures) {
        // 由执行线程调用，委托给 @Transactional persistProgress 方法在新事务中持久化进度
        try {
            persistProgress(jobId, checkpoint, failures);
        } catch (Exception e) {
            LOG.warn("作业 {} 检查点持久化失败（不影响执行线程，下次检查点重试）: {}",
                    jobId, e.getMessage());
        }
    }

    @Override
    public void onFailure(JobFailureItem failure) {
        // 实时监控钩子，可扩展为发送告警
        LOG.debug("作业失败条目: itemKey={} reason={} retryable={}",
                failure.itemKey(), failure.reasonCode(), failure.retryable());
    }

    @Override
    public void onCheckBudget(BackgroundJob job) throws JobExecutionException {
        JobResourceBudget budget = job.resourceBudget();
        JobContext ctx = runningContexts.get(job.id());
        int currentFailures = ctx != null ? ctx.failureCount() : 0;
        int processed = job.checkpoint() != null ? job.checkpoint().processedItems() : 0;

        if (budget.isFailureCountExceeded(currentFailures)) {
            throw JobExecutionException.resourceBudgetExceeded(
                    "失败条目数超阈值: " + currentFailures + " >= " + budget.maxFailureCount(),
                    "检查失败明细，修复后从检查点重试");
        }
        if (budget.isFailureRateExceeded(processed, currentFailures)) {
            throw JobExecutionException.resourceBudgetExceeded(
                    "失败率超阈值: " + currentFailures + "/" + processed,
                    "检查数据质量或外部依赖，修复后从检查点重试");
        }
        if (ctx != null && ctx.isCancelRequested()) {
            throw JobExecutionException.cancelled("作业被请求取消");
        }
    }

    // ============= 辅助方法 =============

    private BackgroundJob loadJob(UUID jobId) {
        return repository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("BackgroundJob", jobId));
    }

    /**
     * 持久化作业进度（检查点 + 失败明细）。
     * <p>执行线程通过 {@link JobContext#saveCheckpoint} 间接调用，在新事务中执行，
     * 避免污染主作业事务。
     *
     * @param jobId      作业 ID
     * @param checkpoint 新检查点
     * @param failures   当前失败明细
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistProgress(UUID jobId, JobCheckpoint checkpoint, List<JobFailureItem> failures) {
        BackgroundJob job = loadJob(jobId);
        BackgroundJob updated = job.updateProgress(checkpoint, failures);
        repository.save(updated);
    }

    private com.pdp.shared.context.OperatorContext buildOperatorFromJob(BackgroundJob job) {
        return new com.pdp.shared.context.OperatorContext(
                job.requestedBy(),
                job.workspaceId(),
                java.util.Set.of(),
                java.util.Set.of());
    }

    /**
     * 全局数据库连接预算（所有运行中作业的连接总和上限）。
     * <p>对应 research.md 第 5 节：所有应用副本、Flowable 异步执行器、后台执行器和临时迁移池的
     * 连接上限之和不超过数据库可用连接的 70%。
     * <p>P1 默认 8（单实例），生产环境通过配置覆盖。
     */
    private int maxGlobalDbConnections() {
        return Integer.getInteger("pdp.jobs.max-global-db-connections", 8);
    }
}
