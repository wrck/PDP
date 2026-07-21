package com.pdp.persistence.domainconfig.adapter;

import com.pdp.domainconfig.domain.packageversion.RuntimeSnapshot;

import java.time.Instant;
import java.util.UUID;

/**
 * 运行时版本快照持久化行（{@code domain_package_runtime_snapshot}）。
 *
 * <p>{@code snapshotId} 为应用层生成的字符串 ID（VARCHAR(100) 主键，便于跨模块引用）；
 * {@code resolvedObjects} 为三层继承合并后的完整对象图 JSON 字符串。
 *
 * <p>快照不可变：仅 insert，无 update。
 */
public record RuntimeSnapshotRow(
        String snapshotId,
        UUID versionId,
        UUID packageId,
        String snapshotVersion,
        String contentHash,
        String parentSnapshotId,
        String resolvedObjectsJson,
        Instant createdAt) {

    /** 从行还原 {@link RuntimeSnapshot}（不含 resolvedObjects JSON）。 */
    public RuntimeSnapshot toSnapshot() {
        return new RuntimeSnapshot(snapshotId, snapshotVersion, contentHash,
                parentSnapshotId, createdAt);
    }

    /** 从 {@link RuntimeSnapshot} 拆解为行（{@code resolvedObjectsJson} 由调用方提供）。 */
    public static RuntimeSnapshotRow fromSnapshot(RuntimeSnapshot snapshot,
                                                   UUID versionId,
                                                   UUID packageId,
                                                   String resolvedObjectsJson) {
        return new RuntimeSnapshotRow(
                snapshot.snapshotId(),
                versionId,
                packageId,
                snapshot.snapshotVersion(),
                snapshot.contentHash(),
                snapshot.parentSnapshotId(),
                resolvedObjectsJson,
                snapshot.createdAt());
    }
}
