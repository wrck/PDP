package com.pdp.datamigration.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

final class MigrationDataSourceFactory {

    private MigrationDataSourceFactory() {}

    static HikariDataSource create(
            MigrationDataSourceProperties.Endpoint endpoint, String role, boolean readOnly) {
        endpoint.validate(role);
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(endpoint.getJdbcUrl());
        config.setUsername(endpoint.getUsername());
        config.setPassword(endpoint.getPassword());
        config.setDriverClassName(endpoint.getDriverClassName());
        config.setPoolName(endpoint.getPoolName());
        config.setReadOnly(readOnly);
        config.setAutoCommit(false);
        config.setMinimumIdle(endpoint.getMinimumIdle());
        config.setMaximumPoolSize(endpoint.getMaximumPoolSize());
        config.setConnectionTimeout(endpoint.getConnectionTimeoutMs());
        config.setValidationTimeout(endpoint.getValidationTimeoutMs());
        config.setIdleTimeout(endpoint.getIdleTimeoutMs());
        config.setMaxLifetime(endpoint.getMaxLifetimeMs());
        config.setKeepaliveTime(endpoint.getKeepaliveTimeMs());
        config.setTransactionIsolation("TRANSACTION_READ_COMMITTED");
        config.setRegisterMbeans(true);
        config.addDataSourceProperty("connectionTimeZone", "UTC");
        config.addDataSourceProperty("forceConnectionTimeZoneToSession", "true");
        config.addDataSourceProperty("useUnicode", "true");
        config.addDataSourceProperty("characterEncoding", "UTF-8");
        return new HikariDataSource(config);
    }
}
