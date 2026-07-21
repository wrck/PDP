package com.pdp.persistence.domainconfig.adapter;

import com.pdp.domainconfig.domain.packageversion.DomainPackageValidationResult;
import com.pdp.domainconfig.domain.packageversion.ValidationItem;
import com.pdp.domainconfig.domain.packageversion.ValidationResultStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 校验结果持久化行（{@code domain_package_validation_result}）。
 *
 * <p>{@code itemsJson} 为 {@code List<ValidationItem>} 的 JSON 序列化字符串，
 * 通过 {@link DomainPackageJsonCodec} 处理。
 */
public record ValidationResultRow(
        UUID id,
        UUID versionId,
        UUID jobId,
        ValidationResultStatus status,
        boolean passed,
        String itemsJson,
        Instant validatedAt) {

    /** 从行还原 {@link DomainPackageValidationResult}。 */
    public DomainPackageValidationResult toResult() {
        List<ValidationItem> items = DomainPackageJsonCodec.readValidationItemList(itemsJson);
        return new DomainPackageValidationResult(
                id, versionId, jobId, status, passed, items, validatedAt);
    }

    /** 从 {@link DomainPackageValidationResult} 拆解为行。 */
    public static ValidationResultRow fromResult(DomainPackageValidationResult result) {
        return new ValidationResultRow(
                result.id(),
                result.versionId(),
                result.jobId(),
                result.status(),
                result.passed(),
                DomainPackageJsonCodec.writeValidationItemList(result.items()),
                result.validatedAt());
    }
}
