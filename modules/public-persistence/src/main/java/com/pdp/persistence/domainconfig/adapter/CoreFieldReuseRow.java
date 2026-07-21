package com.pdp.persistence.domainconfig.adapter;

import com.pdp.domainconfig.domain.packageversion.CoreFieldReuseDisposition;
import com.pdp.domainconfig.domain.packageversion.DomainPackageCoreFieldReuse;

import java.time.Instant;
import java.util.UUID;

/**
 * 核心字段复用声明持久化行（{@code domain_package_core_field_reuse}）。
 *
 * <p>FR-134、SC-025：领域包版本与核心字段目录的复用/差异/扩展关系声明。
 */
public record CoreFieldReuseRow(
        UUID id,
        UUID versionId,
        String coreFieldKey,
        String coreObjectType,
        CoreFieldReuseDisposition disposition,
        String reason,
        String extensionFieldKey,
        Instant createdAt) {

    /** 从行还原 {@link DomainPackageCoreFieldReuse}。 */
    public DomainPackageCoreFieldReuse toReuse() {
        return new DomainPackageCoreFieldReuse(
                id, versionId, coreFieldKey, coreObjectType,
                disposition, reason, extensionFieldKey, createdAt);
    }

    /** 从 {@link DomainPackageCoreFieldReuse} 拆解为行。 */
    public static CoreFieldReuseRow fromReuse(DomainPackageCoreFieldReuse reuse) {
        return new CoreFieldReuseRow(
                reuse.id(),
                reuse.versionId(),
                reuse.coreFieldKey(),
                reuse.coreObjectType(),
                reuse.disposition(),
                reuse.reason(),
                reuse.extensionFieldKey(),
                reuse.createdAt());
    }
}
