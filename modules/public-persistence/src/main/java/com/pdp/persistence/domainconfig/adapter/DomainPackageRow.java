package com.pdp.persistence.domainconfig.adapter;

import com.pdp.domainconfig.domain.packageversion.DomainPackageLayer;
import com.pdp.domainconfig.domain.packageversion.DomainPackageStatus;
import com.pdp.domainconfig.domain.packageversion.PrincipalRef;
import com.pdp.domainconfig.domain.packageversion.PrincipalType;

import java.time.Instant;
import java.util.UUID;

/**
 * 领域包持久化行。
 *
 * <p>{@link com.pdp.domainconfig.domain.packageversion.DomainPackage#designer()} 与
 * {@code publisher()} 是 {@link PrincipalRef} 值对象，需扁平化存储为
 * {@code designer_principal_type / designer_principal_id / designer_display_label}
 * 与对应的 publisher 三列。
 */
public record DomainPackageRow(
        UUID id,
        UUID workspaceId,
        String stableKey,
        String name,
        String description,
        DomainPackageLayer layer,
        UUID parentPackageId,
        DomainPackageStatus status,
        PrincipalType designerPrincipalType,
        String designerPrincipalId,
        String designerDisplayLabel,
        PrincipalType publisherPrincipalType,
        String publisherPrincipalId,
        String publisherDisplayLabel,
        UUID currentPublishedVersionId,
        int revision,
        Instant createdAt,
        Instant updatedAt) {

    public PrincipalRef designer() {
        return new PrincipalRef(designerPrincipalType, designerPrincipalId, designerDisplayLabel);
    }

    public PrincipalRef publisher() {
        return new PrincipalRef(publisherPrincipalType, publisherPrincipalId, publisherDisplayLabel);
    }
}
