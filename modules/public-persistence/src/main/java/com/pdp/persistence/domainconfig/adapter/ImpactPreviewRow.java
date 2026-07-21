package com.pdp.persistence.domainconfig.adapter;

import com.pdp.domainconfig.domain.packageversion.DomainPackageImpactPreview;

import java.time.Instant;
import java.util.UUID;

/**
 * 影响预览持久化行（{@code domain_package_impact_preview}）。
 *
 * <p>{@code resultJson} 为完整的影响分析 JSON 字符串（affectedObjects、breakingChanges、
 * irreversibleChanges、migrationBatches 等）。
 */
public record ImpactPreviewRow(
        UUID id,
        UUID packageId,
        UUID candidateVersionId,
        UUID currentVersionId,
        String resultJson,
        Instant generatedAt,
        Instant expiresAt,
        String confirmedBy,
        Instant confirmedAt) {

    /** 从行还原 {@link DomainPackageImpactPreview}。 */
    public DomainPackageImpactPreview toPreview() {
        return new DomainPackageImpactPreview(
                id, packageId, candidateVersionId, currentVersionId,
                resultJson, generatedAt, expiresAt, confirmedBy, confirmedAt);
    }

    /** 从 {@link DomainPackageImpactPreview} 拆解为行。 */
    public static ImpactPreviewRow fromPreview(DomainPackageImpactPreview preview) {
        return new ImpactPreviewRow(
                preview.id(),
                preview.packageId(),
                preview.candidateVersionId(),
                preview.currentVersionId(),
                preview.resultJson(),
                preview.generatedAt(),
                preview.expiresAt(),
                preview.confirmedBy(),
                preview.confirmedAt());
    }
}
