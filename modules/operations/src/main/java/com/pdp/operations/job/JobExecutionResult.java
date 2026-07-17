package com.pdp.operations.job;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 后台作业执行结果值对象。
 *
 * <p>由 {@link JobHandler#execute(JobContext)} 返回，{@link BackgroundJobCoordinator} 据此将作业转为终态
 * （COMPLETED/FAILED/CANCELLED），持久化结果文件 ID、摘要和失败明细。
 *
 * <p>结果与状态机的对应：
 * <ul>
 *   <li>{@link Status#COMPLETED}：全部条目成功，作业进入 {@link BackgroundJobStatus#COMPLETED}；</li>
 *   <li>{@link Status#COMPLETED_WITH_FAILURES}：完成但有失败条目（如部分导入失败），
 *       作业进入 {@link BackgroundJobStatus#COMPLETED} 但保留失败明细供人工处理；</li>
 *   <li>{@link Status#FAILED}：致命错误或失败超阈值，作业进入 {@link BackgroundJobStatus#FAILED}，
 *       保留检查点和失败明细，可安全重试；</li>
 *   <li>{@link Status#CANCELLED}：作业执行中请求取消，进入 {@link BackgroundJobStatus#CANCELLED}。</li>
 * </ul>
 *
 * @param status         执行结果状态
 * @param resultFileId   结果文件 ID（导出/归档作业的产物，可为空）
 * @param summary        执行摘要（人类可读，不含敏感数据）
 * @param failures       失败明细列表
 * @param finalCheckpoint 最终检查点（用于重试续传）
 * @param completedAt    完成时间
 */
public record JobExecutionResult(
        Status status,
        UUID resultFileId,
        String summary,
        List<JobFailureItem> failures,
        JobCheckpoint finalCheckpoint,
        Instant completedAt) {

    public JobExecutionResult {
        Objects.requireNonNull(status, "status 不能为 null");
        Objects.requireNonNull(completedAt, "completedAt 不能为 null");
        failures = failures == null ? List.of() : List.copyOf(failures);
    }

    /** 成功完成（无失败）。 */
    public static JobExecutionResult completed(
            String summary, JobCheckpoint finalCheckpoint, Instant completedAt) {
        return new JobExecutionResult(Status.COMPLETED, null, summary,
                List.of(), finalCheckpoint, completedAt);
    }

    /** 成功完成但有失败条目（保留失败明细）。 */
    public static JobExecutionResult completedWithFailures(
            String summary, List<JobFailureItem> failures, JobCheckpoint finalCheckpoint, Instant completedAt) {
        return new JobExecutionResult(Status.COMPLETED_WITH_FAILURES, null, summary,
                failures, finalCheckpoint, completedAt);
    }

    /** 失败（致命错误或失败超阈值，保留检查点和失败明细）。 */
    public static JobExecutionResult failed(
            String summary, List<JobFailureItem> failures, JobCheckpoint finalCheckpoint, Instant completedAt) {
        return new JobExecutionResult(Status.FAILED, null, summary,
                failures, finalCheckpoint, completedAt);
    }

    /** 取消。 */
    public static JobExecutionResult cancelled(
            String summary, JobCheckpoint finalCheckpoint, Instant completedAt) {
        return new JobExecutionResult(Status.CANCELLED, null, summary,
                List.of(), finalCheckpoint, completedAt);
    }

    /** 带结果文件的成功完成（导出/归档作业）。 */
    public static JobExecutionResult completedWithResult(
            UUID resultFileId, String summary, JobCheckpoint finalCheckpoint, Instant completedAt) {
        return new JobExecutionResult(Status.COMPLETED, resultFileId, summary,
                List.of(), finalCheckpoint, completedAt);
    }

    public Optional<UUID> resultFileId() {
        return Optional.ofNullable(resultFileId);
    }

    public boolean hasFailures() {
        return !failures.isEmpty();
    }

    public int failureCount() {
        return failures.size();
    }

    /**
     * 执行结果状态。
     */
    public enum Status {
        /** 成功完成（无失败）。 */
        COMPLETED,
        /** 成功完成但有失败条目（保留失败明细）。 */
        COMPLETED_WITH_FAILURES,
        /** 失败（致命错误或失败超阈值）。 */
        FAILED,
        /** 取消。 */
        CANCELLED
    }
}
