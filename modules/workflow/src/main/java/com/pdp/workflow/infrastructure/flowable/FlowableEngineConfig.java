package com.pdp.workflow.infrastructure.flowable;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@AutoConfiguration
@EnableConfigurationProperties(WorkflowEngineProperties.class)
@ConditionalOnProperty(prefix = "pdp.workflow.engine", name = "enabled", havingValue = "true")
public class FlowableEngineConfig {

    @Bean(name = "workflowEngineDataSource", destroyMethod = "close")
    DataSource workflowEngineDataSource(WorkflowEngineProperties properties) {
        properties.validate();
        WorkflowEngineProperties.DataSource source = properties.getDatasource();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(source.getJdbcUrl());
        config.setUsername(source.getUsername());
        config.setPassword(source.getPassword());
        config.setDriverClassName(source.getDriverClassName());
        config.setPoolName(source.getPoolName());
        config.setMinimumIdle(source.getMinimumIdle());
        config.setMaximumPoolSize(source.getMaximumPoolSize());
        config.setConnectionTimeout(source.getConnectionTimeoutMs());
        config.setValidationTimeout(source.getValidationTimeoutMs());
        config.setMaxLifetime(source.getMaxLifetimeMs());
        config.setAutoCommit(false);
        config.setTransactionIsolation("TRANSACTION_READ_COMMITTED");
        config.addDataSourceProperty("connectionTimeZone", "UTC");
        config.addDataSourceProperty("forceConnectionTimeZoneToSession", "true");
        config.addDataSourceProperty("useUnicode", "true");
        config.addDataSourceProperty("characterEncoding", "UTF-8");
        return new HikariDataSource(config);
    }

    @Bean(name = "workflowEngineTransactionManager")
    PlatformTransactionManager workflowEngineTransactionManager(
            @Qualifier("workflowEngineDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean(name = "workflowProcessEngineConfiguration")
    SpringProcessEngineConfiguration workflowProcessEngineConfiguration(
            @Qualifier("workflowEngineDataSource") DataSource dataSource,
            @Qualifier("workflowEngineTransactionManager") PlatformTransactionManager transactionManager,
            WorkflowEngineProperties properties) {
        SpringProcessEngineConfiguration configuration = new SpringProcessEngineConfiguration();
        configuration.setEngineName("pdp-workflow");
        configuration.setDataSource(dataSource);
        configuration.setTransactionManager(transactionManager);
        configuration.setTransactionsExternallyManaged(false);
        configuration.setDatabaseSchema(properties.getSchema());
        configuration.setDatabaseTablePrefix(properties.getTablePrefix());
        configuration.setTablePrefixIsSchema(properties.isTablePrefixIsSchema());
        configuration.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_FALSE);
        configuration.setAsyncExecutorActivate(properties.isAsyncExecutorActivate());
        configuration.setAsyncExecutorCorePoolSize(properties.getAsyncCorePoolSize());
        configuration.setAsyncExecutorMaxPoolSize(properties.getAsyncMaxPoolSize());
        configuration.setAsyncExecutorThreadPoolQueueSize(properties.getAsyncQueueCapacity());
        configuration.setAsyncExecutorNumberOfRetries(properties.getAsyncRetries());
        configuration.setJpaEntityManagerFactory(null);
        configuration.setJpaHandleTransaction(false);
        configuration.setJpaCloseEntityManager(false);
        configuration.setCreateDiagramOnDeploy(false);
        return configuration;
    }

    @Bean(name = "workflowProcessEngine", destroyMethod = "close")
    ProcessEngine workflowProcessEngine(
            @Qualifier("workflowProcessEngineConfiguration")
            SpringProcessEngineConfiguration configuration) {
        return configuration.buildProcessEngine();
    }
}
