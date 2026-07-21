package com.pdp.domainconfig.domain.packageversion;

import java.time.Instant;
import java.util.UUID;

/**
 * 运行时版本快照（FR-018）。
 *
 * <p>发布时生成的不可变版本快照；运行实例默认绑定此快照，升级通过分批迁移完成。
 * 快照记录三层继承合并后的 resolvedObjects 内容哈希，确保运行时配置可追溯。
 */
public record RuntimeSnapshot(
        String snapshotId,
        String snapshotVersion,
        String contentHash,
        String parentSnapshotId,
        Instant createdAt) {

    public RuntimeSnapshot {
        if (snapshotId == null || snapshotId.isBlank()) {
            throw new IllegalArgumentException("snapshotId 不能为空");
        }
        if (snapshotVersion == null || snapshotVersion.isBlank()) {
            throw new IllegalArgumentException("snapshotVersion 不能为空");
        }
        if (contentHash == null || contentHash.isBlank()) {
            throw new IllegalArgumentException("contentHash 不能为空");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt 不能为 null");
        }
    }
}
