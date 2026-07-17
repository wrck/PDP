package com.pdp.persistence.provider;

import java.util.Set;

/**
 * 数据库能力画像。
 *
 * <p>描述认证数据库的能力矩阵：产品、版本、字符集、排序规则、时区、事务引擎、隔离级别与可选能力标签。
 * 启动时实际探测结果必须与认证能力矩阵一致。
 */
public record DatabaseCapabilityProfile(
        DatabaseType databaseType,
        String databaseVersion,
        String characterSet,
        String collation,
        String timezone,
        String transactionEngine,
        String isolationLevel,
        Set<String> capabilities) {

    public DatabaseCapabilityProfile {
        if (databaseType == null) {
            throw new IllegalArgumentException("databaseType 不能为 null");
        }
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
    }

    /** P1 MySQL 8.4 认证基线。 */
    public static DatabaseCapabilityProfile mysql84Baseline() {
        return new DatabaseCapabilityProfile(
                DatabaseType.MYSQL,
                "8.4",
                "utf8mb4",
                "utf8mb4_0900_bin",
                "UTC",
                "InnoDB",
                "READ_COMMITTED",
                Set.of("json", "keyset-pagination", "descending-index", "function-index"));
    }
}
