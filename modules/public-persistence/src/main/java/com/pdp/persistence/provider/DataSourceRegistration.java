package com.pdp.persistence.provider;

import java.time.Instant;
import java.util.UUID;

/**
 * 数据源注册记录。
 *
 * <p>记录受控运行事实，不保存明文连接信息（{@code connectionSecretRef} 为外部密钥引用）。
 * {@code role} 为 {@link DataSourceRole#PDP_PRIMARY} 与 {@link DataSourceRole#WORKFLOW_ENGINE}
 * 唯一且始终存在；迁移源/目标必须绑定 {@code migrationProgramId} 和 {@code expiresAt}。
 */
public record DataSourceRegistration(
        String dataSourceKey,
        DataSourceRole role,
        DatabaseType databaseType,
        String connectionSecretRef,
        String poolProfile,
        String sessionFactoryKey,
        String transactionManagerKey,
        String mapperPackage,
        boolean readOnly,
        RegistrationStatus status,
        Instant loadedAt,
        Instant expiresAt,
        UUID migrationProgramId) {

    public DataSourceRegistration {
        if (dataSourceKey == null || dataSourceKey.isBlank()) {
            throw new IllegalArgumentException("dataSourceKey 不能为空");
        }
        if (role == null) {
            throw new IllegalArgumentException("role 不能为 null");
        }
        if (databaseType == null) {
            throw new IllegalArgumentException("databaseType 不能为 null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status 不能为 null");
        }
    }
}
