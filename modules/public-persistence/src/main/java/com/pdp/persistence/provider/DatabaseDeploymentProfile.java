package com.pdp.persistence.provider;

import java.time.Instant;
import java.util.Set;

/**
 * 数据库部署事实。
 *
 * <p>配置只保存非敏感部署事实，连接凭据使用密钥引用。
 * 启动时实际探测结果必须与认证能力矩阵一致。
 */
public record DatabaseDeploymentProfile(
        DatabaseType databaseType,
        String databaseVersion,
        String jdbcDriverVersion,
        String schemaVersion,
        String characterSet,
        String collation,
        String timezone,
        String transactionEngine,
        String isolationLevel,
        Set<String> capabilities,
        ValidationStatus validationStatus,
        Instant validatedAt) {

    public DatabaseDeploymentProfile {
        if (databaseType == null) {
            throw new IllegalArgumentException("databaseType 不能为 null");
        }
        if (validationStatus == null) {
            throw new IllegalArgumentException("validationStatus 不能为 null");
        }
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
    }
}
