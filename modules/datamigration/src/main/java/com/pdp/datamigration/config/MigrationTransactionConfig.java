package com.pdp.datamigration.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
public class MigrationTransactionConfig {

    @Bean(name = "migrationSourceTransactionManager")
    PlatformTransactionManager migrationSourceTransactionManager(
            @Qualifier("migrationSourceDataSource") DataSource dataSource) {
        // The source pool and transaction template both set JDBC read-only. Production must also
        // use a database account that has no write grants. Avoid setEnforceReadOnly(true) here:
        // it emits dialect-specific SQL and would bind this public migration boundary to MySQL.
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean(name = "migrationTargetTransactionManager")
    PlatformTransactionManager migrationTargetTransactionManager(
            @Qualifier("migrationTargetDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    MigrationBatchBoundary migrationBatchBoundary(
            @Qualifier("migrationSourceTransactionManager")
                    PlatformTransactionManager sourceManager,
            @Qualifier("migrationTargetTransactionManager")
                    PlatformTransactionManager targetManager) {
        TransactionTemplate source = new TransactionTemplate(sourceManager);
        source.setReadOnly(true);
        source.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);

        TransactionTemplate target = new TransactionTemplate(targetManager);
        target.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        target.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return new MigrationBatchBoundary(source, target);
    }
}
