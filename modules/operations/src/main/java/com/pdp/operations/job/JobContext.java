package com.pdp.operations.job;

import com.pdp.shared.context.OperatorContext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 后台作业执行上下文。
 *
 * <p>由 {@link BackgroundJobCoordinator} 在调用 {@link JobHandler#execute} 前构造，
 * 封装作业当前状态、检查点访问、进度报告、失败收集和取消响应能力。
 *
 * <p>实现 MUST 由协调器提供，{@link JobHandler} 实现通过此上下文与协调器交互，不直接访问仓储。
 * 上下文非线程安全，仅供单个作业执行线程使用。
 */
public final class JobContext {

    private final BackgroundJob job;
    private final OperatorContext operator;
    private final CoordinatorCallbacks callbacks;
    private final List<JobFailureItem> failures = new ArrayList<>();
    private volatile boolean cancelRequested = false;

    public JobContext(BackgroundJob job, OperatorContext operator, CoordinatorCallbacks callbacks) {
        this.job = Objects.requireNonNull(job, "job 不能为 null");
        this.operator = Objects.requireNonNull(operator, "operator 不能为 null");
        this.callbacks = Objects.requireNonNull(callbacks, "callbacks 不能为 null");
    }

    /** 当前作业快照。 */
    public BackgroundJob job() {
        return job;
    }

    /** 当前操作者上下文（权限和审计依据）。 */
    public OperatorContext operator() {
        return operator;
    }

    /** 当前检查点（无检查点返回空检查点）。 */
    public JobCheckpoint checkpoint() {
        return job.checkpoint() != null ? job.checkpoint() : JobCheckpoint.empty();
    }

    /**
     * 持久化检查点并更新进度。
     *
     * <p>实现 SHOULD 在每处理一个批次后调用，确保失败时可从最近检查点恢复。
     * 协调器将检查点写入 {@code background_job.checkpoint} 列。
     *
     * @param newCheckpoint 新检查点
     */
    public void saveCheckpoint(JobCheckpoint newCheckpoint) {
        Objects.requireNonNull(newCheckpoint, "newCheckpoint 不能为 null");
        callbacks.onCheckpoint(job.id(), newCheckpoint, List.copyOf(failures));
    }

    /**
     * 记录单个条目失败。
     *
     * <p>实现 SHOULD 在条目处理失败时调用，作业继续处理后续条目。失败明细在作业终态后
     * 通过 {@link BackgroundJobPort#getJob} 暴露给前端。
     *
     * @param failure 失败明细
     */
    public void recordFailure(JobFailureItem failure) {
        Objects.requireNonNull(failure, "failure 不能为 null");
        failures.add(failure);
        callbacks.onFailure(failure);
    }

    /**
     * 请求取消作业。
     *
     * <p>由协调器在外部取消请求时调用，实现 SHOULD 在处理每个条目前检查 {@link #isCancelRequested}
     * 并及时返回。
     */
    public void requestCancel() {
        this.cancelRequested = true;
    }

    /** 是否已请求取消。 */
    public boolean isCancelRequested() {
        return cancelRequested;
    }

    /**
     * 检查资源预算是否超限。
     *
     * <p>实现 SHOULD 在长循环中周期性调用，超限时抛出 {@link JobExecutionException} 触发暂停或失败。
     *
     * @throws JobExecutionException 资源预算超限
     */
    public void checkResourceBudget() throws JobExecutionException {
        callbacks.onCheckBudget(job);
    }

    /** 当前失败明细快照。 */
    public List<JobFailureItem> failures() {
        return List.copyOf(failures);
    }

    /** 当前失败条目数。 */
    public int failureCount() {
        return failures.size();
    }

    /** 当前时间（用于失败明细时间戳）。 */
    public Instant now() {
        return Instant.now();
    }

    /**
     * 协调器回调接口（由 {@link BackgroundJobCoordinator} 实现）。
     */
    public interface CoordinatorCallbacks {

        /** 检查点持久化回调。 */
        void onCheckpoint(java.util.UUID jobId, JobCheckpoint checkpoint, List<JobFailureItem> failures);

        /** 单条失败记录回调（用于实时监控）。 */
        void onFailure(JobFailureItem failure);

        /** 资源预算检查回调（超限时抛出异常）。 */
        void onCheckBudget(BackgroundJob job) throws JobExecutionException;

        /** 估算剩余时间（基于吞吐量）。 */
        default java.time.Duration estimateRemaining(BackgroundJob job, Supplier<Integer> currentProcessedSupplier) {
            return null;
        }
    }
}
