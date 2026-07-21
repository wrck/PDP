package com.pdp.persistence.domainconfig.adapter;

import com.pdp.domainconfig.domain.packageversion.CompatibilityStatement;
import com.pdp.domainconfig.domain.packageversion.DomainPackageVersionStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * 领域包版本持久化行。
 *
 * <p>{@link com.pdp.domainconfig.domain.packageversion.DomainPackageVersion#compatibilityStatement()}
 * 是 {@link CompatibilityStatement} 值对象，序列化为 JSON 存储在 {@code compatibility_statement_json} 列。
 *
 * <p>状态机审计字段（submittedBy/submittedAt/publishedBy/publishedAt/rejectedBy/rejectedAt/rejectReason）
 * 直接映射到列，允许 null。
 */
public record DomainPackageVersionRow(
        UUID id,
        UUID packageId,
        String semanticVersion,
        String contentHash,
        DomainPackageVersionStatus status,
        String parentSnapshotId,
        String runtimeSnapshotId,
        String manifestJson,
        String compatibilityStatementJson,
        String extendsVersionRange,
        String submittedBy,
        Instant submittedAt,
        String publishedBy,
        Instant publishedAt,
        String rejectedBy,
        Instant rejectedAt,
        String rejectReason,
        int revision,
        Instant createdAt,
        Instant updatedAt) {

    public CompatibilityStatement compatibilityStatement() {
        return DomainPackageJsonCodec.readCompatibilityStatement(compatibilityStatementJson);
    }
}
