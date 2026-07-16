package com.pdp.persistence.provider;

import java.util.List;
import java.util.Objects;

/**
 * 统一数据库切换组合契约。P1 认证 MYSQL 到 MYSQL，P2 只增加新的组合实例。
 */
public record DatabaseSwitchCapability(
        DatabaseProduct sourceDatabaseProduct,
        DatabaseProduct targetDatabaseProduct,
        DatabaseVersion minimumSourceVersion,
        DatabaseVersion minimumTargetVersion,
        CapabilityStatus status,
        List<String> constraints) {

    public DatabaseSwitchCapability {
        sourceDatabaseProduct = Objects.requireNonNull(sourceDatabaseProduct, "sourceDatabaseProduct");
        targetDatabaseProduct = Objects.requireNonNull(targetDatabaseProduct, "targetDatabaseProduct");
        minimumSourceVersion = Objects.requireNonNull(minimumSourceVersion, "minimumSourceVersion");
        minimumTargetVersion = Objects.requireNonNull(minimumTargetVersion, "minimumTargetVersion");
        status = Objects.requireNonNull(status, "status");
        constraints = List.copyOf(Objects.requireNonNull(constraints, "constraints"));
    }

    public boolean supports(
            DatabaseCapabilityProfile source,
            DatabaseCapabilityProfile target) {
        return status == CapabilityStatus.CERTIFIED
                && source.databaseProduct().equals(sourceDatabaseProduct)
                && target.databaseProduct().equals(targetDatabaseProduct)
                && source.databaseVersion().compareTo(minimumSourceVersion) >= 0
                && target.databaseVersion().compareTo(minimumTargetVersion) >= 0;
    }

    public enum CapabilityStatus {
        CERTIFIED,
        SUSPENDED,
        UNSUPPORTED
    }
}
