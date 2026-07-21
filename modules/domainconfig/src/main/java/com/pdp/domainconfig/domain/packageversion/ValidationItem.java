package com.pdp.domainconfig.domain.packageversion;

/**
 * 领域包版本校验项（openapi DomainPackageValidationResult.items[]）。
 *
 * <p>{@link #severity} 为 {@link ValidationItemSeverity#BLOCKER} 时必须阻断发布；
 * 由 {@code DomainPackageValidationService}（T121）在校验过程中产出。
 */
public record ValidationItem(
        String code,
        ValidationItemSeverity severity,
        ValidationItemCategory category,
        String message,
        String objectKey,
        String fieldKey) {

    public ValidationItem {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code 不能为空");
        }
        if (severity == null) {
            throw new IllegalArgumentException("severity 不能为 null");
        }
        if (category == null) {
            throw new IllegalArgumentException("category 不能为 null");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message 不能为空");
        }
    }

    /** 是否为阻断项。 */
    public boolean isBlocker() {
        return severity == ValidationItemSeverity.BLOCKER;
    }
}
