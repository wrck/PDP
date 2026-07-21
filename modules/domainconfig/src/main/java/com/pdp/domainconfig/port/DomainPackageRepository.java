package com.pdp.domainconfig.port;

import com.pdp.domainconfig.domain.packageversion.DomainPackage;
import com.pdp.domainconfig.domain.packageversion.DomainPackageStatus;
import com.pdp.shared.page.PageRequest;
import com.pdp.shared.page.PageResult;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 领域包聚合根仓储端口（FR-007、FR-013、FR-167）。
 *
 * <p>领域/应用层依赖此端口，不依赖 MyBatis 或 MySQL 专有实现。
 * 乐观锁更新方法返回 {@code false} 表示版本冲突或不存在，调用方抛出 {@code ConflictException}。
 */
public interface DomainPackageRepository {

    Optional<DomainPackage> findById(UUID id);

    /**
     * 按工作空间与稳定键查找。
     *
     * <p>{@code workspaceId} 为 null 时仅匹配 PLATFORM_STANDARD 层级（平台共享包）。
     */
    Optional<DomainPackage> findByWorkspaceAndKey(UUID workspaceId, String stableKey);

    /** 按父包查询直接子包列表（用于三层继承校验与遍历）。 */
    PageResult<DomainPackage> findByParentPackage(UUID parentPackageId, PageRequest pageRequest);

    /** 按过滤条件分页查询。 */
    PageResult<DomainPackage> findByFilter(DomainPackageQueryFilter filter, PageRequest pageRequest);

    /** 插入新领域包；stableKey 唯一性由 uniq_domain_package_ws_key 索引保证。 */
    void save(DomainPackage domainPackage);

    /**
     * 更新基本信息（名称、描述）并递增 revision。
     *
     * @return {@code true} 成功；{@code false} 版本冲突或不存在
     */
    boolean updateBasicInfo(UUID id, String name, String description,
                            int expectedRevision, Instant now);

    /**
     * 更新包状态（DRAFT → ACTIVE → DEPRECATED → RETIRED）并递增 revision。
     *
     * @return {@code true} 成功；{@code false} 版本冲突或不存在
     */
    boolean updateStatus(UUID id, DomainPackageStatus newStatus,
                         int expectedRevision, Instant now);

    /**
     * 设置当前发布版本并递增 revision（首个版本发布后激活包）。
     *
     * @return {@code true} 成功；{@code false} 版本冲突或不存在
     */
    boolean updateCurrentPublishedVersion(UUID id, UUID currentPublishedVersionId,
                                          int expectedRevision, Instant now);
}
