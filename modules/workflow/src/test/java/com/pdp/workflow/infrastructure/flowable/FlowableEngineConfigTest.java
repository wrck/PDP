package com.pdp.workflow.infrastructure.flowable;

import static org.assertj.core.api.Assertions.*;

import java.lang.reflect.Proxy;
import javax.sql.DataSource;
import org.flowable.engine.ProcessEngineConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;

class FlowableEngineConfigTest {

    @Test
    void createsProductionSafeEngineConfiguration() {
        WorkflowEngineProperties properties = validProperties();
        DataSource dataSource = (DataSource) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {DataSource.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("toString")) return "workflowDataSource";
                    throw new UnsupportedOperationException(method.getName());
                });
        var transactionManager = new DataSourceTransactionManager(dataSource);
        var configuration = new FlowableEngineConfig().workflowProcessEngineConfiguration(
                dataSource, transactionManager, properties);

        assertThat(configuration.getDataSource()).isInstanceOf(TransactionAwareDataSourceProxy.class);
        assertThat(((TransactionAwareDataSourceProxy) configuration.getDataSource()).getTargetDataSource())
                .isSameAs(dataSource);
        assertThat(configuration.getTransactionManager()).isSameAs(transactionManager);
        assertThat(configuration.getDatabaseSchema()).isEqualTo("pdp_workflow");
        assertThat(configuration.getDatabaseTablePrefix()).isEqualTo("pdp_workflow.");
        assertThat(configuration.isTablePrefixIsSchema()).isTrue();
        assertThat(configuration.getDatabaseSchemaUpdate())
                .isEqualTo(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_FALSE);
        assertThat(configuration.getAsyncExecutorCorePoolSize()).isEqualTo(2);
        assertThat(configuration.getAsyncExecutorMaxPoolSize()).isEqualTo(8);
        assertThat(configuration.getAsyncExecutorThreadPoolQueueSize()).isEqualTo(500);
        assertThat(configuration.getJpaEntityManagerFactory()).isNull();
    }

    @Test
    void rejectsSharedOrMissingDatabaseCredentials() {
        WorkflowEngineProperties properties = validProperties();
        properties.getDatasource().setPassword("");
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("独立 schema");
    }

    @Test
    void loadsOnlyCertifiedMySqlSchemaPaths() {
        FlowableSchemaManifest manifest = FlowableSchemaManifest.load();
        assertThat(manifest.engineVersion()).isEqualTo("8.0.0");
        assertThat(manifest.databaseProduct()).isEqualTo("MYSQL");
        assertThat(manifest.createScripts()).hasSize(3);
        assertThat(manifest.upgradeScriptsFrom("7.2.2")).hasSize(2);
    }

    private static WorkflowEngineProperties validProperties() {
        WorkflowEngineProperties properties = new WorkflowEngineProperties();
        properties.setEnabled(true);
        properties.getDatasource().setJdbcUrl("jdbc:mysql://db/pdp_workflow");
        properties.getDatasource().setUsername("pdp_workflow");
        properties.getDatasource().setPassword("secret-ref-resolved");
        return properties;
    }
}
