package com.pdp.domainconfig.port;

import com.pdp.domainconfig.domain.packageversion.DomainPackageMigrationPlan;
import com.pdp.domainconfig.domain.packageversion.MigrationPlanStatus;
import com.pdp.shared.page.PageRequest;
import com.pdp.shared.page.PageResult;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 领域包迁移计划仓储端口（FR-168 高风险操作框架）。
 *
 * <p>承载迁移计划从 DRAFT 到 ROLLED_BACK 的完整生命周期。{@link #updateProgress}
 * 由 {@code DomainPackageMigrationService}（T124）在分批执行过程中递增；
 * {@link #updateStatus} 用于状态迁移；{@link #updateRollbackWindow} 设置可回滚时间窗。
 */
public interface DomainPackageMigrationPlanRepository {

    Optional<DomainPackageMigrationPlan> findById(UUID id);

    /** 按过滤条件分页查询。 */
    PageResult<DomainPackageMigrationPlan> findByFilter(MigrationPlanQueryFilter filter, PageRequest pageRequest);

    /** 查找包下活跃的迁移计划（status=READY/RUNNING/PAUSED）。 */
    Optional<DomainPackageMigrationPlan> findActiveByPackage(UUID packageId);

    /** 查找源版本-目标版本对下活跃的迁移计划。 */
    Optional<DomainPackageMigrationPlan> findActiveByVersionPair(UUID fromVersionId, UUID toVersionId);

    /** 插入新迁移计划。 */
    void save(DomainPackageMigrationPlan plan);

    /**
     * 更新迁移计划状态并递增 revision。
     *
     * <p>状态机由 {@link MigrationPlanStatus} 文档定义；应用服务在调用前校验前置条件
     * （如 READY → RUNNING、RUNNING → COMPLETED/FAILED、COMPLETED/FAILED → ROLLED_BACK）。
     *
     * @return {@code true} 成功；{@code false} 版本冲突或不存在
     */
    boolean updateStatus(UUID id, MigrationPlanStatus newStatus, int expectedRevision, Instant now);

    /**
     * 更新分批执行进度（completedBatches、failedInstances）并递增 revision。
     *
     * <p>由 {@code DomainPackageMigrationService} 在每批次完成后递增；
     * {@code completedBatches} 不能超过 {@code totalBatches}，由应用层校验。
     *
     * @return {@code true} 成功；{@code false} 版本冲突或不存在
     */
    boolean updateProgress(UUID id, int completedBatches, int failedInstances,
                           int expectedRevision, Instant now);

    /**
     * 绑定影响预览与作业 ID，并将状态从 DRAFT 迁移到 READY。
     *
     * @return {@code true} 成功；{@code false} 版本冲突或不存在
     */
    boolean markReady(UUID id, UUID impactPreviewId, UUID jobId, int expectedRevision, Instant now);

    /**
     * 设置可回滚时间窗截止时间（迁移完成时调用）。
     *
     * @return {@code true} 成功；{@code false} 版本冲突或不存在
     */
    boolean updateRollbackWindow(UUID id, Instant rollbackWindowExpiresAt,
                                 int expectedRevision, Instant now);
}
