package com.pdp.workflow.infrastructure.flowable;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pdp.workflow.engine")
public class WorkflowEngineProperties {
    private boolean enabled;
    private String schema = "pdp_workflow";
    private String tablePrefix = "pdp_workflow.";
    private boolean tablePrefixIsSchema = true;
    private boolean asyncExecutorActivate = true;
    private int asyncCorePoolSize = 2;
    private int asyncMaxPoolSize = 8;
    private int asyncQueueCapacity = 500;
    private int asyncRetries = 3;
    private final DataSource datasource = new DataSource();

    public void validate() {
        if (!enabled) {
            return;
        }
        if (blank(schema) || blank(tablePrefix) || blank(datasource.jdbcUrl)
                || blank(datasource.username) || blank(datasource.password)) {
            throw new IllegalStateException("工作流引擎启用时必须配置独立 schema、表前缀和数据库凭据");
        }
        if (!tablePrefix.endsWith(".") || asyncCorePoolSize < 1
                || asyncMaxPoolSize < asyncCorePoolSize || asyncQueueCapacity < 1
                || asyncRetries < 0 || datasource.maximumPoolSize < asyncMaxPoolSize) {
            throw new IllegalStateException("工作流引擎连接池或异步执行预算无效");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }
    public String getTablePrefix() { return tablePrefix; }
    public void setTablePrefix(String tablePrefix) { this.tablePrefix = tablePrefix; }
    public boolean isTablePrefixIsSchema() { return tablePrefixIsSchema; }
    public void setTablePrefixIsSchema(boolean value) { this.tablePrefixIsSchema = value; }
    public boolean isAsyncExecutorActivate() { return asyncExecutorActivate; }
    public void setAsyncExecutorActivate(boolean value) { this.asyncExecutorActivate = value; }
    public int getAsyncCorePoolSize() { return asyncCorePoolSize; }
    public void setAsyncCorePoolSize(int value) { this.asyncCorePoolSize = value; }
    public int getAsyncMaxPoolSize() { return asyncMaxPoolSize; }
    public void setAsyncMaxPoolSize(int value) { this.asyncMaxPoolSize = value; }
    public int getAsyncQueueCapacity() { return asyncQueueCapacity; }
    public void setAsyncQueueCapacity(int value) { this.asyncQueueCapacity = value; }
    public int getAsyncRetries() { return asyncRetries; }
    public void setAsyncRetries(int value) { this.asyncRetries = value; }
    public DataSource getDatasource() { return datasource; }

    public static class DataSource {
        private String jdbcUrl;
        private String username;
        private String password;
        private String driverClassName = "com.mysql.cj.jdbc.Driver";
        private String poolName = "pdp-workflow-engine";
        private int minimumIdle = 1;
        private int maximumPoolSize = 10;
        private long connectionTimeoutMs = 3000;
        private long validationTimeoutMs = 2000;
        private long maxLifetimeMs = 1_740_000;

        public String getJdbcUrl() { return jdbcUrl; }
        public void setJdbcUrl(String value) { this.jdbcUrl = value; }
        public String getUsername() { return username; }
        public void setUsername(String value) { this.username = value; }
        public String getPassword() { return password; }
        public void setPassword(String value) { this.password = value; }
        public String getDriverClassName() { return driverClassName; }
        public void setDriverClassName(String value) { this.driverClassName = value; }
        public String getPoolName() { return poolName; }
        public void setPoolName(String value) { this.poolName = value; }
        public int getMinimumIdle() { return minimumIdle; }
        public void setMinimumIdle(int value) { this.minimumIdle = value; }
        public int getMaximumPoolSize() { return maximumPoolSize; }
        public void setMaximumPoolSize(int value) { this.maximumPoolSize = value; }
        public long getConnectionTimeoutMs() { return connectionTimeoutMs; }
        public void setConnectionTimeoutMs(long value) { this.connectionTimeoutMs = value; }
        public long getValidationTimeoutMs() { return validationTimeoutMs; }
        public void setValidationTimeoutMs(long value) { this.validationTimeoutMs = value; }
        public long getMaxLifetimeMs() { return maxLifetimeMs; }
        public void setMaxLifetimeMs(long value) { this.maxLifetimeMs = value; }
    }
}
