package com.pdp.domainconfig.domain.packageversion;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 领域包版本聚合内实体（FR-007、FR-018、FR-167）。
 *
 * <p>每个领域包可发布多个语义化版本（semanticVersion）；版本 manifest 内容存储为 JSON，
 * 由 {@link DomainPackageValidationService}（T121）负责结构化校验。版本状态机由
 * {@link DomainPackageVersionStatus} 文档定义。
 *
 * <p><strong>职责分离</strong>（FR-167、US2 验收场景 3）：
 * <ul>
 *   <li>{@code submittedBy}：设计者提交审核；</li>
 *   <li>{@code publishedBy}：独立发布者完成测试和影响审核后发布生产版本；</li>
 *   <li>两者必须为不同主体，由 {@link DomainPackage#ensureDesignerPublisherSeparation} 校验。</li>
 * </ul>
 *
 * <p><strong>乐观锁</strong>：{@code revision} 字段用于 If-Match 头并发控制。
 */
public record DomainPackageVersion(
        UUID id,
        UUID packageId,
        String semanticVersion,
        String contentHash,
        DomainPackageVersionStatus status,
        String parentSnapshotId,
        String runtimeSnapshotId,
        String manifestJson,
        CompatibilityStatement compatibilityStatement,
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

    public DomainPackageVersion {
        if (id == null) {
            throw new IllegalArgumentException("id 不能为 null");
        }
        if (packageId == null) {
            throw new IllegalArgumentException("packageId 不能为 null");
        }
        if (semanticVersion == null || semanticVersion.isBlank()) {
            throw new IllegalArgumentException("semanticVersion 不能为空");
        }
        if (contentHash == null || contentHash.isBlank()) {
            throw new IllegalArgumentException("contentHash 不能为空");
        }
        if (status == null) {
            throw new IllegalArgumentException("status 不能为 null");
        }
        if (manifestJson == null || manifestJson.isBlank()) {
            throw new IllegalArgumentException("manifestJson 不能为空");
        }
    }

    public boolean isDraft() {
        return status == DomainPackageVersionStatus.DRAFT
                || status == DomainPackageVersionStatus.REJECTED;
    }

    public boolean isPublished() {
        return status == DomainPackageVersionStatus.PUBLISHED;
    }

    public boolean isFrozen() {
        return status == DomainPackageVersionStatus.FROZEN;
    }

    public boolean isRetired() {
        return status == DomainPackageVersionStatus.RETIRED;
    }

    /** DRAFT/REJECTED → REVIEW_PENDING 是否合法（提交审核前置条件）。 */
    public boolean canSubmitForReview() {
        return status == DomainPackageVersionStatus.DRAFT
                || status == DomainPackageVersionStatus.REJECTED;
    }

    /** REVIEW_PENDING → PUBLISHED 是否合法（发布前置条件）。 */
    public boolean canPublish() {
        return status == DomainPackageVersionStatus.REVIEW_PENDING;
    }

    /** REVIEW_PENDING → REJECTED 是否合法（审核拒绝前置条件）。 */
    public boolean canReject() {
        return status == DomainPackageVersionStatus.REVIEW_PENDING;
    }

    /** PUBLISHED → FROZEN 是否合法。 */
    public boolean canFreeze() {
        return status == DomainPackageVersionStatus.PUBLISHED;
    }

    /** PUBLISHED → DEPRECATED 是否合法。 */
    public boolean canDeprecate() {
        return status == DomainPackageVersionStatus.PUBLISHED
                || status == DomainPackageVersionStatus.FROZEN;
    }

    /** DEPRECATED → RETIRED 是否合法（运行实例必须先迁移完毕）。 */
    public boolean canRetire() {
        return status == DomainPackageVersionStatus.DEPRECATED;
    }

    /** 校验设计者与发布者是否为不同主体（FR-167、US2 验收场景 3）。 */
    public boolean isDesignerPublisherSeparationSatisfied() {
        if (submittedBy == null || publishedBy == null) {
            return true;
        }
        return !Objects.equals(submittedBy, publishedBy);
    }
}
