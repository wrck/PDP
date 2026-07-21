package com.pdp.domainconfig.domain.packageversion;

import java.time.Instant;
import java.util.Objects;

/**
 * 兼容性声明值对象（FR-172）。
 *
 * <p>记录本版本相对父包或基线版本的兼容性级别、消费者影响描述与弃用窗口。
 * 用于平台契约覆盖矩阵与自动化兼容校验。
 */
public record CompatibilityStatement(
        CompatibilityLevel compatibilityLevel,
        String consumerImpact,
        Instant deprecatedAt,
        Instant sunsetAt) {

    public CompatibilityStatement {
        if (compatibilityLevel == null) {
            throw new IllegalArgumentException("compatibilityLevel 不能为 null");
        }
        if (consumerImpact == null || consumerImpact.isBlank()) {
            throw new IllegalArgumentException("consumerImpact 不能为空");
        }
        if (consumerImpact.length() > 2000) {
            throw new IllegalArgumentException("consumerImpact 不能超过 2000 字符");
        }
        if (sunsetAt != null && deprecatedAt != null && sunsetAt.isBefore(deprecatedAt)) {
            throw new IllegalArgumentException("sunsetAt 不能早于 deprecatedAt");
        }
    }

    public boolean isBreaking() {
        return compatibilityLevel == CompatibilityLevel.MAJOR_BREAKING
                || compatibilityLevel == CompatibilityLevel.MINOR_BREAKING;
    }

    public boolean isWithinDeprecationWindow(Instant now) {
        if (deprecatedAt == null || sunsetAt == null) {
            return false;
        }
        return !now.isBefore(deprecatedAt) && !now.isAfter(sunsetAt);
    }
}
