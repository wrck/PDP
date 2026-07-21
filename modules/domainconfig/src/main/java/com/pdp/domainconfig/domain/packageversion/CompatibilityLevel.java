package com.pdp.domainconfig.domain.packageversion;

import java.time.Instant;
import java.util.Objects;

/**
 * 兼容性级别（FR-172 外部接口版本化契约、消费者与兼容影响记录）。
 */
public enum CompatibilityLevel {
    MAJOR_BREAKING,
    MINOR_BREAKING,
    ADDITIVE,
    PATCH_ONLY
}
