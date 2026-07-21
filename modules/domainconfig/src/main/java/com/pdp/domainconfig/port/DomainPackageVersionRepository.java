package com.pdp.domainconfig.port;

import com.pdp.domainconfig.domain.packageversion.CompatibilityStatement;
import com.pdp.domainconfig.domain.packageversion.DomainPackageVersion;
import com.pdp.domainconfig.domain.packageversion.DomainPackageVersionStatus;
import com.pdp.shared.page.PageRequest;
import com.pdp.shared.page.PageResult;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 领域包版本仓储端口（FR-007、FR-018、FR-167、FR-168）。
 *
 * <p>承载版本草稿、校验、审核、发布、冻结、回滚、弃用、退役等完整生命周期状态迁移。
 * 所有状态迁移方法均通过乐观锁校验，由应用服务（{@code DomainPackageLifecycleService}，T123）
 * 在调用前校验状态机前置条件（{@link DomainPackageVersion#canSubmitForReview()} 等）。
 */
public interface DomainPackageVersionRepository {

    Optional<DomainPackageVersion> findById(UUID id);

    /** 按领域包与语义化版本查找。 */
    Optional<DomainPackageVersion> findByPackageAndSemanticVersion(UUID packageId, String semanticVersion);

    /** 按过滤条件分页查询。 */
    PageResult<DomainPackageVersion> findByFilter(DomainPackageVersionQueryFilter filter, PageRequest pageRequest);

    /** 查找领域包当前已发布版本（status=PUBLISHED 或 FROZEN）。 */
    Optional<DomainPackageVersion> findCurrentPublished(UUID packageId);

    /** 查找领域包最新草稿（status=DRAFT 或 REJECTED）。 */
    Optional<DomainPackageVersion> findLatestDraft(UUID packageId);

    /** 插入新版本草稿；语义化版本唯一性由 uniq_domain_package_version 索引保证。 */
    void save(DomainPackageVersion version);

    /**
     * 更新草稿 manifest、contentHash、兼容性声明与扩展版本范围。
     *
     * <p>仅 DRAFT/REJECTED 状态可调用，由应用服务前置校验。
     *
     * @return {@code true} 成功；{@code false} 版本冲突或不存在
     */
    boolean updateDraft(UUID id, String manifestJson, String contentHash,
                        CompatibilityStatement compatibilityStatement,
                        String extendsVersionRange,
                        int expectedRevision, Instant now);

    /**
     * 通用状态迁移：仅更新 status 字段并递增 revision。
     *
     * <p>用于 VALIDATING/PAUSED 等中间状态。终态迁移（PUBLISHED/REJECTED/FROZEN/DEPRECATED/RETIRED）
     * 优先使用下方专用方法以同时更新审计字段。
     *
     * @return {@code true} 成功；{@code false} 版本冲突或不存在
     */
    boolean updateStatus(UUID id, DomainPackageVersionStatus newStatus,
                         int expectedRevision, Instant now);

    /**
     * 提交审核（DRAFT/REJECTED → REVIEW_PENDING）并记录提交人与时间。
     *
     * @return {@code true} 成功；{@code false} 版本冲突或不存在
     */
    boolean submitForReview(UUID id, String submittedBy, Instant submittedAt,
                            int expectedRevision);

    /**
     * 审核拒绝（REVIEW_PENDING → REJECTED）并记录拒绝人、时间与原因。
     *
     * @return {@code true} 成功；{@code false} 版本冲突或不存在
     */
    boolean reject(UUID id, String rejectedBy, Instant rejectedAt, String rejectReason,
                   int expectedRevision);

    /**
     * 发布版本（REVIEW_PENDING → PUBLISHED）并记录发布人、时间、运行时快照与父快照引用。
     *
     * <p>独立发布者必须与设计者为不同主体（FR-167、US2 验收场景 3），
     * 由 {@code DomainPackageLifecycleService}（T123）在调用前校验。
     *
     * @return {@code true} 成功；{@code false} 版本冲突或不存在
     */
    boolean publish(UUID id, String publishedBy, Instant publishedAt,
                    String runtimeSnapshotId, String parentSnapshotId,
                    int expectedRevision);

    /**
     * 冻结版本（PUBLISHED → FROZEN），阻止运行实例升级到此版本。
     *
     * @return {@code true} 成功；{@code false} 版本冲突或不存在
     */
    boolean freeze(UUID id, int expectedRevision, Instant now);

    /**
     * 弃用版本（PUBLISHED/FROZEN → DEPRECATED）。
     *
     * @return {@code true} 成功；{@code false} 版本冲突或不存在
     */
    boolean deprecate(UUID id, int expectedRevision, Instant now);

    /**
     * 退役版本（DEPRECATED → RETIRED）；运行实例必须已全部迁移完毕。
     *
     * @return {@code true} 成功；{@code false} 版本冲突或不存在
     */
    boolean retire(UUID id, int expectedRevision, Instant now);

    /**
     * 回滚已发布版本到上一个稳定状态（FR-168 高风险操作）。
     *
     * <p>由应用服务通过迁移计划与影响预览严格控制；本方法仅持久化状态与审计字段。
     *
     * @return {@code true} 成功；{@code false} 版本冲突或不存在
     */
    boolean rollback(UUID id, String rolledBackBy, Instant rolledBackAt,
                     String rollbackReason, int expectedRevision);
}
