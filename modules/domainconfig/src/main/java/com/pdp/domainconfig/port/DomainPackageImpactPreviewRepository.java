package com.pdp.domainconfig.port;

import com.pdp.domainconfig.domain.packageversion.DomainPackageImpactPreview;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 领域包升级影响预览仓储端口（FR-168 高风险操作框架）。
 *
 * <p>影响预览由 {@code DomainPackageMigrationService}（T124）在发布或迁移前生成，
 * 有效期由 {@link DomainPackageImpactPreview#expiresAt()} 控制。独立发布者确认后
 * （{@link #confirm}）方可执行发布或迁移。
 */
public interface DomainPackageImpactPreviewRepository {

    Optional<DomainPackageImpactPreview> findById(UUID id);

    /** 按候选版本查找最新有效预览（按 generatedAt 降序）。 */
    Optional<DomainPackageImpactPreview> findLatestByCandidateVersion(UUID candidateVersionId);

    /** 按包与当前版本-候选版本对查找最新预览。 */
    Optional<DomainPackageImpactPreview> findLatestByPackageAndVersionPair(UUID packageId,
                                                                           UUID currentVersionId,
                                                                           UUID candidateVersionId);

    /** 插入影响预览。 */
    void save(DomainPackageImpactPreview preview);

    /**
     * 标记预览已被独立发布者确认（FR-168 明确确认要求）。
     *
     * @return {@code true} 成功；{@code false} 不存在或已过期
     */
    boolean confirm(UUID id, String confirmedBy, Instant confirmedAt);

    /**
     * 物理删除已过期的预览记录（清理任务调用）。
     *
     * @param now 当前时间
     * @return 受影响行数
     */
    int deleteExpired(Instant now);
}
