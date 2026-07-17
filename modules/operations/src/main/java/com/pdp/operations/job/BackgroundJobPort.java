package com.pdp.operations.job;

import com.pdp.shared.context.ActorRef;
import com.pdp.shared.context.WorkspaceId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 后台作业应用端口（应用层与 API 调用）。
 *
 * <p>统一后台作业的提交、暂停、恢复、取消、重试和查询入口。对应 OpenAPI {@code /jobs/{jobId}} 接口和
 * spec.md US18 业务连续性、US20 历史迁移中的批次执行能力。
 *
 * <p>核心契约（spec.md 状态机表"后台作业"行）：
 * <ol>
 *   <li>提交时校验幂等键、执行身份、范围、资源预算和检查点有效性；</li>
 *   <li>暂停/取消 MUST 保留检查点和失败明细，提供"可安全重试/人工补偿入口"；</li>
 *   <li>查询返回进度百分比、失败条目数和结果文件 ID（OpenAPI BackgroundJob schema）。</li>
 * </ol>
 *
 * <p>端口实现由 {@link BackgroundJobCoordinator} 提供。
 */
public interface BackgroundJobPort {

    /**
     * 提交作业。
     *
     * <p>校验幂等键（{@code (workspace_id, idempotency_key)} 唯一），创建 QUEUED 状态作业。
     * 协调器异步调度执行。
     *
     * @param idempotencyKey 幂等键（工作空间内唯一）
     * @param workspaceId    工作空间
     * @param jobType        作业类型
     * @param scope          作业范围（如项目 ID、迁移批次键、投影版本）
     * @param requestedBy    请求者
     * @param resourceBudget 资源预算
     * @return 创建的作业（QUEUED 状态）
     * @throws com.pdp.shared.error.ConflictException 幂等键已存在
     */
    BackgroundJob submit(String idempotencyKey,
                         WorkspaceId workspaceId,
                         BackgroundJobType jobType,
                         String scope,
                         ActorRef requestedBy,
                         JobResourceBudget resourceBudget);

    /**
     * 暂停作业（RUNNING → PAUSED）。
     *
     * @param jobId       作业 ID
     * @param requestedBy 请求者
     * @param reason      暂停原因
     * @return 暂停后的作业
     * @throws IllegalStateException 非 RUNNING 状态
     */
    BackgroundJob pause(UUID jobId, ActorRef requestedBy, String reason);

    /**
     * 恢复作业（PAUSED → RUNNING）。
     *
     * @param jobId       作业 ID
     * @param requestedBy 请求者
     * @return 恢复后的作业
     * @throws IllegalStateException 非 PAUSED 状态
     */
    BackgroundJob resume(UUID jobId, ActorRef requestedBy);

    /**
     * 取消作业（QUEUED/RUNNING/PAUSED → CANCELLED）。
     *
     * @param jobId       作业 ID
     * @param requestedBy 请求者
     * @param reason      取消原因
     * @return 取消后的作业
     * @throws IllegalStateException 不可取消状态
     */
    BackgroundJob cancel(UUID jobId, ActorRef requestedBy, String reason);

    /**
     * 重试失败作业（FAILED → QUEUED，从检查点继续）。
     *
     * @param jobId       作业 ID
     * @param requestedBy 请求者
     * @return 重新入队的作业
     * @throws IllegalStateException 非 FAILED 状态
     */
    BackgroundJob retry(UUID jobId, ActorRef requestedBy);

    /**
     * 查询作业详情（含进度和失败明细）。
     *
     * @param jobId 作业 ID
     * @return 作业记录，不存在返回 empty
     */
    Optional<BackgroundJob> getJob(UUID jobId);

    /**
     * 查询工作空间内作业列表。
     *
     * @param workspaceId 工作空间
     * @param status      作业状态（null 表示所有状态）
     * @param jobType     作业类型（null 表示所有类型）
     * @param offset      偏移量
     * @param limit       最大返回数
     * @return 作业列表
     */
    List<BackgroundJob> listJobs(WorkspaceId workspaceId,
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
    long countJobs(WorkspaceId workspaceId, BackgroundJobStatus status, BackgroundJobType jobType);
}
