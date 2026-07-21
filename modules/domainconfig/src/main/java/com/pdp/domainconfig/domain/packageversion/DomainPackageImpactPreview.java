package com.pdp.domainconfig.domain.packageversion;

import java.time.Instant;
import java.util.UUID;

/**
 * 领域包升级影响预览（FR-168 高风险操作框架）。
 *
 * <p>记录候选版本相对当前生产版本的影响分析结果。{@link #resultJson} 包含 affectedObjects、
 * breakingChanges、irreversibleChanges、migrationBatches 等字段，结构由
 * {@code DomainPackageMigrationService}（T124）生成。
 *
 * <p>{@link #expiresAt} 为预览有效期；超过后必须重新生成。{@link #confirmedBy}/{@code confirmedAt}
 * 在独立发布者确认后填充，作为发布的强制前置条件。
 */
public record DomainPackageImpactPreview(
        UUID id,
        UUID packageId,
        UUID candidateVersionId,
        UUID currentVersionId,
        String resultJson,
        Instant generatedAt,
        Instant expiresAt,
        String confirmedBy,
        Instant confirmedAt) {

    public DomainPackageImpactPreview {
        if (id == null) {
            throw new IllegalArgumentException("id 不能为 null");
        }
        if (packageId == null) {
            throw new IllegalArgumentException("packageId 不能为 null");
        }
        if (candidateVersionId == null) {
            throw new IllegalArgumentException("candidateVersionId 不能为 null");
        }
        if (currentVersionId == null) {
            throw new IllegalArgumentException("currentVersionId 不能为 null");
        }
        if (resultJson == null || resultJson.isBlank()) {
            throw new IllegalArgumentException("resultJson 不能为空");
        }
        if (generatedAt == null) {
            throw new IllegalArgumentException("generatedAt 不能为 null");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("expiresAt 不能为 null");
        }
        if (expiresAt.isBefore(generatedAt)) {
            throw new IllegalArgumentException("expiresAt 不能早于 generatedAt");
        }
    }

    /** 预览是否已过期。 */
    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    /** 是否已由独立发布者确认。 */
    public boolean isConfirmed() {
        return confirmedBy != null && confirmedAt != null;
    }
}
