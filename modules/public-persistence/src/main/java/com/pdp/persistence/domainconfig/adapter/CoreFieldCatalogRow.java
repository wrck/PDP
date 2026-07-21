package com.pdp.persistence.domainconfig.adapter;

import com.pdp.domainconfig.domain.metamodel.CoreFieldCatalogEntry;
import com.pdp.domainconfig.domain.metamodel.CoreFieldSource;
import com.pdp.domainconfig.domain.metamodel.DataType;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * 核心字段目录持久化行（{@code core_field_catalog}）。
 *
 * <p>{@code aliases} 列为 JSON，存储 {@code Set<String>}；通过 {@link DomainPackageJsonCodec}
 * 序列化/反序列化。
 */
public record CoreFieldCatalogRow(
        UUID id,
        String stableKey,
        String coreObjectType,
        String label,
        DataType dataType,
        String semantics,
        boolean allowedOverride,
        CoreFieldSource source,
        String aliasesJson,
        int revision,
        Instant createdAt,
        Instant updatedAt) {

    /** 从行还原 {@link CoreFieldCatalogEntry}。 */
    public CoreFieldCatalogEntry toEntry() {
        Set<String> aliases = DomainPackageJsonCodec.readStringSet(aliasesJson);
        return new CoreFieldCatalogEntry(
                id, stableKey, coreObjectType, label, dataType, semantics,
                allowedOverride, source, aliases, revision, createdAt, updatedAt);
    }

    /** 从 {@link CoreFieldCatalogEntry} 拆解为行。 */
    public static CoreFieldCatalogRow fromEntry(CoreFieldCatalogEntry entry) {
        return new CoreFieldCatalogRow(
                entry.id(),
                entry.stableKey(),
                entry.coreObjectType(),
                entry.label(),
                entry.dataType(),
                entry.semantics(),
                entry.allowedOverride(),
                entry.source(),
                DomainPackageJsonCodec.writeStringSet(entry.aliases()),
                entry.revision(),
                entry.createdAt(),
                entry.updatedAt());
    }
}
