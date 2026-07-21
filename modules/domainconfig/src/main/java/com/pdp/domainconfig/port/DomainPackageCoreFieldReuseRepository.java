package com.pdp.domainconfig.port;

import com.pdp.domainconfig.domain.packageversion.DomainPackageCoreFieldReuse;

import java.util.List;
import java.util.UUID;

/**
 * 领域包版本核心字段复用声明仓储端口（FR-134、SC-025）。
 *
 * <p>声明由领域包设计者在版本草稿中维护，发布前由 {@code DomainPackageValidationService}
 * （T121）检测与核心字段目录的冲突。声明写入后仅在草稿状态下可删除（{@link #deleteByVersion}）。
 */
public interface DomainPackageCoreFieldReuseRepository {

    /** 按版本 ID 查询全部复用声明。 */
    List<DomainPackageCoreFieldReuse> findByVersion(UUID versionId);

    /** 按核心字段键与对象类型查询所有引用（用于核心字段变更影响分析）。 */
    List<DomainPackageCoreFieldReuse> findByCoreField(String coreFieldKey, String coreObjectType);

    /** 插入复用声明。 */
    void save(DomainPackageCoreFieldReuse reuse);

    /** 批量插入复用声明（版本草稿提交时使用）。 */
    void saveAll(List<DomainPackageCoreFieldReuse> reuses);

    /**
     * 删除指定版本的全部复用声明（草稿被丢弃或版本回滚时调用）。
     *
     * @return 受影响行数
     */
    int deleteByVersion(UUID versionId);
}
