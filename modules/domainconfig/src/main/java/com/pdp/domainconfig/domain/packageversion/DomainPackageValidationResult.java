package com.pdp.domainconfig.domain.packageversion;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 领域包版本校验结果（FR-167、SC-013）。
 *
 * <p>由 {@code DomainPackageValidationService}（T121）产出，作为版本发布的强制前置条件。
 * 仅当 {@link #passed} 为 {@code true} 且无 BLOCKER 级别项时才允许提交审核或发布。
 */
public record DomainPackageValidationResult(
        UUID id,
        UUID versionId,
        UUID jobId,
        ValidationResultStatus status,
        boolean passed,
        List<ValidationItem> items,
        Instant validatedAt) {

    public DomainPackageValidationResult {
        if (id == null) {
            throw new IllegalArgumentException("id 不能为 null");
        }
        if (versionId == null) {
            throw new IllegalArgumentException("versionId 不能为 null");
        }
        if (jobId == null) {
            throw new IllegalArgumentException("jobId 不能为 null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status 不能为 null");
        }
        items = items == null ? List.of() : List.copyOf(items);
    }

    /** 是否包含阻断项（BLOCKER）。 */
    public boolean hasBlocker() {
        return items.stream().anyMatch(ValidationItem::isBlocker);
    }
}
