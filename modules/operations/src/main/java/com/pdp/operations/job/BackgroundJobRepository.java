package com.pdp.operations.job;

import com.pdp.shared.context.WorkspaceId;
import com.pdp.shared.concurrency.OptimisticLockException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 后台作业仓储端口（持久化层实现）。
 *
 * <p>由 persistence 基础设施实现（如 MySQL 适配器），持久化 {@link BackgroundJob} 到 {@code background_job} 表。
 * 业务模块和应用层通过 {@link BackgroundJobPort} 调用，仓储端口仅用于持久化。
 *
 * <p><strong>乐观锁契约</strong>：{@link #save} 使用 {@code revision} 乐观锁，并发更新冲突时抛出
 * {@link OptimisticLockException}。协调器和操作接口 MUST 处理冲突并重试或返回冲突响应。
 *
 * <p><strong>幂等键契约</strong>：{@code (workspace_id, idempotency_key)} 唯一约束防止重复提交；
 * {@link #findByIdempotencyKey} 用于幂等检查。
 */
public interface BackgroundJobRepository {

    /**
     * 保存或更新作业。
     *
     * <p>新增时 revision=1；更新时 MUST 校验 revision 一致后递增。
     *
     * @param job 作业记录
     * @return 保存后的作业（revision 已递增）
     * @throws OptimisticLockException revision 不匹配
     */
    BackgroundJob save(BackgroundJob job);

    /**
     * 按 ID 查询作业。
     *
     * @param jobId 作业 ID
     * @return 作业记录，不存在返回 empty
     */
    Optional<BackgroundJob> findById(UUID jobId);

    /**
     * 按幂等键查询作业（幂等检查）。
     *
     * @param workspaceId    工作空间
     * @param idempotencyKey 幂等键
     * @return 作业记录，不存在返回 empty
     */
    Optional<BackgroundJob> findByIdempotencyKey(WorkspaceId workspaceId, String idempotencyKey);

    /**
     * 按状态查询可调度的作业（QUEUED 状态，按创建时间升序）。
     *
     * @param limit 最大返回数
     * @return 可调度作业列表
     */
    List<BackgroundJob> findDispatchable(int limit);

    /**
     * 按工作空间和状态查询作业（用于作业列表接口）。
     *
     * @param workspaceId 工作空间（null 表示跨空间管理员查询）
     * @param status      作业状态（null 表示所有状态）
     * @param jobType     作业类型（null 表示所有类型）
     * @param offset      偏移量
     * @param limit       最大返回数
     * @return 作业列表（按 createdAt 降序）
     */
    List<BackgroundJob> findByWorkspace(WorkspaceId workspaceId,
                                        BackgroundJobStatus status,
                                        BackgroundJobType jobType,
                                        int offset,
                                        int limit);

    /**
     * 统计工作空间内作业数。
     *
     * @param workspaceId 工作空间
     * @param status      作业状态（null 表示所有状态）
     * @param jobType     作业类型（null 表示所有类型）
     * @return 作业数
     */
    long countByWorkspace(WorkspaceId workspaceId, BackgroundJobStatus status, BackgroundJobType jobType);

    /**
     * 查询超时作业（RUNNING 状态且启动时间早于阈值，用于超时检测）。
     *
     * @param threshold 超时阈值
     * @param limit     最大返回数
     * @return 超时作业列表
     */
    List<BackgroundJob> findTimedOutJobs(java.time.Instant threshold, int limit);
}
