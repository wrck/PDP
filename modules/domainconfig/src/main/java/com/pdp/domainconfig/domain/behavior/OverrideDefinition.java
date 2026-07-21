package com.pdp.domainconfig.domain.behavior;

import com.pdp.domainconfig.domain.packageversion.PrincipalRef;

/**
 * 覆盖定义（domain-package.schema.json overrideDefinition）。
 *
 * <p>FR-013 三层继承：客户包（{@code WORKSPACE_CUSTOMER}）可覆盖行业包
 * （{@code INDUSTRY}）声明的允许覆盖白名单内的属性；不允许覆盖平台核心对象
 * 身份、工作空间归属、基础权限、审计、版本和平台保留动作。
 *
 * <p>{@link #propertyPath} 必须以 {@code /} 开头且在父包声明的允许覆盖白名单内，
 * 由 {@code DomainPackageCompositionService}（T122）发布前校验。
 *
 * <p>{@link #approvedBy} 为覆盖批准人；客户包覆盖行业包 MUST 经批准（FR-168）。
 */
public record OverrideDefinition(
        String targetStableKey,
        String propertyPath,
        Object oldValue,
        Object newValue,
        String reason,
        PrincipalRef approvedBy) {

    public OverrideDefinition {
        if (targetStableKey == null || targetStableKey.isBlank()) {
            throw new IllegalArgumentException("targetStableKey 不能为空");
        }
        if (propertyPath == null || !propertyPath.startsWith("/")) {
            throw new IllegalArgumentException("propertyPath 必须以 / 开头");
        }
        if (reason == null || reason.length() < 2 || reason.length() > 1000) {
            throw new IllegalArgumentException("reason 长度必须在 [2, 1000] 区间");
        }
    }
}
