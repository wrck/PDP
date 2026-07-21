package com.pdp.domainconfig.port;

import com.pdp.domainconfig.domain.packageversion.RuntimeSnapshot;

import java.util.Optional;
import java.util.UUID;

/**
 * 领域包运行时版本快照仓储端口（FR-018）。
 *
 * <p>发布时生成不可变快照；运行实例默认绑定快照 ID，升级通过分批迁移完成。
 * 快照记录三层继承合并后的 resolvedObjects 内容哈希，确保运行时配置可追溯。
 * 快照写入后不可修改，本端口不提供 update 方法。
 */
public interface DomainPackageRuntimeSnapshotRepository {

    /** 按快照 ID 查找（snapshotId 为字符串形式，便于跨模块引用）。 */
    Optional<RuntimeSnapshot> findById(String snapshotId);

    /** 按版本 ID 查找其发布时生成的快照。 */
    Optional<RuntimeSnapshot> findByVersionId(UUID versionId);

    /** 按领域包 ID 查找最新发布的快照（按 createdAt 降序）。 */
    Optional<RuntimeSnapshot> findLatestByPackageId(UUID packageId);

    /**
     * 插入新运行时快照。
     *
     * <p>快照 ID 由应用层生成；同 ID 重复插入由主键约束拒绝。
     * {@code resolvedObjectsJson} 为三层继承合并后的完整对象图 JSON。
     * {@code versionId} 与 {@code packageId} 为快照关联的领域包版本与包，
     * 由持久化层写入 {@code version_id}/{@code package_id} 列以支持反向查询。
     */
    void save(RuntimeSnapshot snapshot, UUID versionId, UUID packageId, String resolvedObjectsJson);

    /** 加载完整 resolvedObjects JSON（用于运行时与迁移服务）。 */
    Optional<String> findResolvedObjectsJson(String snapshotId);
}
