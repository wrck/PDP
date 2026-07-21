package com.pdp.domainconfig.domain.packageversion;

import java.time.Instant;
import java.util.UUID;

/**
 * 领域包版本核心字段复用声明（FR-134、SC-025）。
 *
 * <p>每个领域包版本 MUST 声明其扩展字段与平台核心字段目录的关系：
 * <ul>
 *   <li>{@link CoreFieldReuseDisposition#REUSE}：直接复用核心字段，不创建新字段；</li>
 *   <li>{@link CoreFieldReuseDisposition#DIFFERENTIATE}：创建语义差异字段，必须声明理由；</li>
 *   <li>{@link CoreFieldReuseDisposition#AUGMENT}：在核心字段基础上增加受控扩展。</li>
 * </ul>
 *
 * <p>发布前由 {@code DomainPackageValidationService}（T121）检测重名、语义、标识和数据来源冲突（SC-025）。
 */
public record DomainPackageCoreFieldReuse(
        UUID id,
        UUID versionId,
        String coreFieldKey,
        String coreObjectType,
        CoreFieldReuseDisposition disposition,
        String reason,
        String extensionFieldKey,
        Instant createdAt) {

    public DomainPackageCoreFieldReuse {
        if (id == null) {
            throw new IllegalArgumentException("id 不能为 null");
        }
        if (versionId == null) {
            throw new IllegalArgumentException("versionId 不能为 null");
        }
        if (coreFieldKey == null || coreFieldKey.isBlank()) {
            throw new IllegalArgumentException("coreFieldKey 不能为空");
        }
        if (coreObjectType == null || coreObjectType.isBlank()) {
            throw new IllegalArgumentException("coreObjectType 不能为空");
        }
        if (disposition == null) {
            throw new IllegalArgumentException("disposition 不能为 null");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt 不能为 null");
        }
        if (disposition == CoreFieldReuseDisposition.DIFFERENTIATE
                && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("DIFFERENTIATE 处置必须声明 reason");
        }
        if (disposition != CoreFieldReuseDisposition.REUSE
                && (extensionFieldKey == null || extensionFieldKey.isBlank())) {
            throw new IllegalArgumentException("非 REUSE 处置必须指定 extensionFieldKey");
        }
    }
}
