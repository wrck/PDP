package com.pdp.datamigration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 迁移专用数据源配置。凭据由外部密钥系统或环境注入，不持久化到迁移业务记录。
 */
@ConfigurationProperties(prefix = "pdp.migration")
public class MigrationDataSourceProperties {

    private final Endpoint source = new Endpoint();
    private final Endpoint target = new Endpoint();

    public Endpoint getSource() {
        return source;
    }

    public Endpoint getTarget() {
        return target;
    }

    public static final class Endpoint {

        private String jdbcUrl;
        private String username;
        private String password;
        private String driverClassName = "com.mysql.cj.jdbc.Driver";
        private String poolName;
        private int minimumIdle = 0;
        private int maximumPoolSize = 4;
        private long connectionTimeoutMs = 10_000;
        private long validationTimeoutMs = 1_000;
        private long idleTimeoutMs = 600_000;
        private long maxLifetimeMs = 1_800_000;
        private long keepaliveTimeMs = 300_000;

        void validate(String role) {
            if (jdbcUrl == null || jdbcUrl.isBlank()) {
                throw new IllegalStateException(role + " JDBC URL 不能为空");
            }
            if (username == null || username.isBlank()) {
                throw new IllegalStateException(role + " 用户名不能为空");
            }
            if (poolName == null || poolName.isBlank()) {
                throw new IllegalStateException(role + " 连接池名称不能为空");
            }
            if (maximumPoolSize < 1 || minimumIdle < 0 || minimumIdle > maximumPoolSize) {
                throw new IllegalStateException(role + " 连接池容量配置非法");
            }
            if (connectionTimeoutMs < 250 || validationTimeoutMs < 250) {
                throw new IllegalStateException(role + " 连接超时不得小于 250ms");
            }
            if (maxLifetimeMs > 0
                    && keepaliveTimeMs > 0
                    && keepaliveTimeMs >= maxLifetimeMs) {
                throw new IllegalStateException(role + " keepaliveTime 必须小于 maxLifetime");
            }
        }

        public String getJdbcUrl() {
            return jdbcUrl;
        }

        public void setJdbcUrl(String jdbcUrl) {
            this.jdbcUrl = jdbcUrl;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDriverClassName() {
            return driverClassName;
        }

        public void setDriverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
        }

        public String getPoolName() {
            return poolName;
        }

        public void setPoolName(String poolName) {
            this.poolName = poolName;
        }

        public int getMinimumIdle() {
            return minimumIdle;
        }

        public void setMinimumIdle(int minimumIdle) {
            this.minimumIdle = minimumIdle;
        }

        public int getMaximumPoolSize() {
            return maximumPoolSize;
        }

        public void setMaximumPoolSize(int maximumPoolSize) {
            this.maximumPoolSize = maximumPoolSize;
        }

        public long getConnectionTimeoutMs() {
            return connectionTimeoutMs;
        }

        public void setConnectionTimeoutMs(long connectionTimeoutMs) {
            this.connectionTimeoutMs = connectionTimeoutMs;
        }

        public long getValidationTimeoutMs() {
            return validationTimeoutMs;
        }

        public void setValidationTimeoutMs(long validationTimeoutMs) {
            this.validationTimeoutMs = validationTimeoutMs;
        }

        public long getIdleTimeoutMs() {
            return idleTimeoutMs;
        }

        public void setIdleTimeoutMs(long idleTimeoutMs) {
            this.idleTimeoutMs = idleTimeoutMs;
        }

        public long getMaxLifetimeMs() {
            return maxLifetimeMs;
        }

        public void setMaxLifetimeMs(long maxLifetimeMs) {
            this.maxLifetimeMs = maxLifetimeMs;
        }

        public long getKeepaliveTimeMs() {
            return keepaliveTimeMs;
        }

        public void setKeepaliveTimeMs(long keepaliveTimeMs) {
            this.keepaliveTimeMs = keepaliveTimeMs;
        }
    }
}
