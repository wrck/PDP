package com.pdp.operations.job;

import com.pdp.shared.context.ActorRef;
import com.pdp.shared.context.WorkspaceId;
import com.pdp.shared.id.UuidV7Generator;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 后台作业领域记录。
 *
 * <p>对应表 {@code background_job}（{@code 002-platform-foundation.xml}）。聚合作业的全生命周期状态：
 * 提交、调度、执行、检查点、进度、失败明细和终态。{@link BackgroundJobCoordinator} 通过
 * {@link BackgroundJobRepository} 持久化，{@link BackgroundJobPort} 查询接口对外暴露。
 *
 * <p><strong>幂等键</strong>：{@code idempotencyKey} 由调用方提供，防止重复提交产生重复作业
 * （spec.md 状态机前置条件"幂等键有效"）。{@code id} 为 UUIDv7（应用生成）。
 *
 * <p><strong>乐观锁</strong>：{@code revision} 用于并发更新冲突检测（如调度器更新状态时校验）。
 *
 * @param id              作业 ID（UUIDv7，应用生成）
 * @param idempotencyKey  幂等键（调用方提供，工作空间内唯一）
 * @param workspaceId     工作空间
 * @param jobType         作业类型
 * @param scope           作业范围（如项目 ID、迁移批次键、投影版本）
 * @param requestedBy     请求者
 * @param status          当前状态
 * @param progress        进度百分比 [0, 100]
 * @param checkpoint      当前检查点（可为空，作业启动前为空）
 * @param failures        失败明细列表
 * @param resultFileId    结果文件 ID（导出/归档作业产物，可为空）
 * @param errorMessage    错误消息（FAILED 状态时填写，不含敏感数据）
 * @param resourceBudget  资源预算
 * @param startedAt       启动时间
 * @param finishedAt      完成时间
 * @param createdAt       创建时间
 * @param updatedAt       更新时间
 * @param revision        乐观锁版本
 */
public record BackgroundJob(
        UUID id,
        String idempotencyKey,
        WorkspaceId workspaceId,
        BackgroundJobType jobType,
        String scope,
        ActorRef requestedBy,
        BackgroundJobStatus status,
        int progress,
        JobCheckpoint checkpoint,
        List<JobFailureItem> failures,
        UUID resultFileId,
        String errorMessage,
        JobResourceBudget resourceBudget,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt,
        Instant updatedAt,
        int revision) {

    public BackgroundJob {
        Objects.requireNonNull(id, "id 不能为 null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey 不能为 null");
        if (idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey 不能为空白");
        }
        Objects.requireNonNull(workspaceId, "workspaceId 不能为 null");
        Objects.requireNonNull(jobType, "jobType 不能为 null");
        Objects.requireNonNull(requestedBy, "requestedBy 不能为 null");
        Objects.requireNonNull(status, "status 不能为 null");
        if (progress < 0 || progress > 100) {
            throw new IllegalArgumentException("progress 必须在 [0, 100]: " + progress);
        }
        Objects.requireNonNull(resourceBudget, "resourceBudget 不能为 null");
        Objects.requireNonNull(createdAt, "createdAt 不能为 null");
        Objects.requireNonNull(updatedAt, "updatedAt 不能为 null");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt 不能早于 createdAt");
        }
        if (revision < 1) {
            throw new IllegalArgumentException("revision 必须 >= 1: " + revision);
        }
        failures = failures == null ? List.of() : List.copyOf(failures);
    }

    /**
     * 创建新作业（QUEUED 状态）。
     *
     * @param idempotencyKey 幂等键
     * @param workspaceId    工作空间
     * @param jobType        作业类型
     * @param scope          作业范围
     * @param requestedBy    请求者
     * @param resourceBudget 资源预算
     * @return 新作业（QUEUED，progress=0，revision=1）
     */
    public static BackgroundJob create(
            String idempotencyKey,
            WorkspaceId workspaceId,
            BackgroundJobType jobType,
            String scope,
            ActorRef requestedBy,
            JobResourceBudget resourceBudget) {
        Instant now = Instant.now();
        return new BackgroundJob(
                UuidV7Generator.next(), idempotencyKey, workspaceId, jobType, scope,
                requestedBy, BackgroundJobStatus.QUEUED, 0, null, List.of(),
                null, null, resourceBudget, null, null, now, now, 1);
    }

    /**
     * 启动作业（QUEUED → RUNNING）。
     *
     * @return 新状态作业
     * @throws IllegalStateException 非 QUEUED 状态
     */
    public BackgroundJob start() {
        requireTransition(BackgroundJobStatus.RUNNING);
        Instant now = Instant.now();
        JobCheckpoint initial = checkpoint == null ? JobCheckpoint.empty() : checkpoint;
        return new BackgroundJob(id, idempotencyKey, workspaceId, jobType, scope, requestedBy,
                BackgroundJobStatus.RUNNING, 0, initial, failures, resultFileId, errorMessage,
                resourceBudget, now, null, createdAt, now, revision + 1);
    }

    /**
     * 更新进度（RUNNING 状态）。
     *
     * @param newCheckpoint 新检查点
     * @param failures      当前失败明细
     * @return 新状态作业
     */
    public BackgroundJob updateProgress(JobCheckpoint newCheckpoint, List<JobFailureItem> failures) {
        if (status != BackgroundJobStatus.RUNNING) {
            throw new IllegalStateException("仅 RUNNING 状态可更新进度，当前: " + status);
        }
        Objects.requireNonNull(newCheckpoint, "newCheckpoint 不能为 null");
        Instant now = Instant.now();
        int progress = newCheckpoint.progressPercent();
        return new BackgroundJob(id, idempotencyKey, workspaceId, jobType, scope, requestedBy,
                status, progress, newCheckpoint, failures, resultFileId, errorMessage,
                resourceBudget, startedAt, null, createdAt, now, revision + 1);
    }

    /**
     * 暂停作业（RUNNING → PAUSED）。
     */
    public BackgroundJob pause(String reason) {
        requireTransition(BackgroundJobStatus.PAUSED);
        Instant now = Instant.now();
        return new BackgroundJob(id, idempotencyKey, workspaceId, jobType, scope, requestedBy,
                BackgroundJobStatus.PAUSED, progress, checkpoint, failures, resultFileId,
                reason, resourceBudget, startedAt, null, createdAt, now, revision + 1);
    }

    /**
     * 恢复作业（PAUSED → RUNNING）。
     */
    public BackgroundJob resume() {
        requireTransition(BackgroundJobStatus.RUNNING);
        Instant now = Instant.now();
        return new BackgroundJob(id, idempotencyKey, workspaceId, jobType, scope, requestedBy,
                BackgroundJobStatus.RUNNING, progress, checkpoint, failures, resultFileId,
                null, resourceBudget, startedAt, null, createdAt, now, revision + 1);
    }

    /**
     * 完成作业（RUNNING → COMPLETED）。
     */
    public BackgroundJob complete(JobExecutionResult result) {
        Objects.requireNonNull(result, "result 不能为 null");
        requireTransition(BackgroundJobStatus.COMPLETED);
        Instant now = Instant.now();
        int finalProgress = result.finalCheckpoint() != null ? result.finalCheckpoint().progressPercent() : 100;
        return new BackgroundJob(id, idempotencyKey, workspaceId, jobType, scope, requestedBy,
                BackgroundJobStatus.COMPLETED, finalProgress,
                result.finalCheckpoint() != null ? result.finalCheckpoint() : checkpoint,
                result.failures(), result.resultFileId().orElse(null),
                result.summary(), resourceBudget, startedAt, now, createdAt, now, revision + 1);
    }

    /**
     * 标记失败（RUNNING → FAILED）。
     */
    public BackgroundJob fail(JobExecutionResult result) {
        Objects.requireNonNull(result, "result 不能为 null");
        requireTransition(BackgroundJobStatus.FAILED);
        Instant now = Instant.now();
        return new BackgroundJob(id, idempotencyKey, workspaceId, jobType, scope, requestedBy,
                BackgroundJobStatus.FAILED, progress,
                result.finalCheckpoint() != null ? result.finalCheckpoint() : checkpoint,
                result.failures(), result.resultFileId().orElse(null),
                result.summary(), resourceBudget, startedAt, now, createdAt, now, revision + 1);
    }

    /**
     * 取消作业（QUEUED/RUNNING/PAUSED → CANCELLED）。
     */
    public BackgroundJob cancel(String reason) {
        requireTransition(BackgroundJobStatus.CANCELLED);
        Instant now = Instant.now();
        return new BackgroundJob(id, idempotencyKey, workspaceId, jobType, scope, requestedBy,
                BackgroundJobStatus.CANCELLED, progress, checkpoint, failures, resultFileId,
                reason, resourceBudget, startedAt, now, createdAt, now, revision + 1);
    }

    /**
     * 重新入队（FAILED → QUEUED，人工重试）。
     */
    public BackgroundJob requeue() {
        requireTransition(BackgroundJobStatus.QUEUED);
        Instant now = Instant.now();
        return new BackgroundJob(id, idempotencyKey, workspaceId, jobType, scope, requestedBy,
                BackgroundJobStatus.QUEUED, progress, checkpoint, failures, resultFileId,
                null, resourceBudget, null, null, createdAt, now, revision + 1);
    }

    public boolean isTerminal() {
        return status.isTerminal();
    }

    public boolean isCancellable() {
        return status.isCancellable();
    }

    public Optional<JobCheckpoint> checkpoint() {
        return Optional.ofNullable(checkpoint);
    }

    public Optional<Instant> startedAt() {
        return Optional.ofNullable(startedAt);
    }

    public Optional<Instant> finishedAt() {
        return Optional.ofNullable(finishedAt);
    }

    public Optional<UUID> resultFileId() {
        return Optional.ofNullable(resultFileId);
    }

    public int failureCount() {
        return failures.size();
    }

    private void requireTransition(BackgroundJobStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "非法状态迁移: " + status + " → " + target + "（作业 " + id + "）");
        }
    }
}
