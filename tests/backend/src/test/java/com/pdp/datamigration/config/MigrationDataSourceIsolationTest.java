package com.pdp.datamigration.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.Statement;
import org.mybatis.spring.annotation.MapperScan;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class MigrationDataSourceIsolationTest {

    @Test
    void createsIndependentPoolsSessionFactoriesCredentialsAndMapperScopes() throws Exception {
        MigrationDataSourceProperties properties = properties();
        LegacySourceMybatisConfig sourceConfig = new LegacySourceMybatisConfig();
        PdpTargetMybatisConfig targetConfig = new PdpTargetMybatisConfig();

        try (HikariDataSource source =
                        (HikariDataSource) sourceConfig.migrationSourceDataSource(properties);
                HikariDataSource target =
                        (HikariDataSource) targetConfig.migrationTargetDataSource(properties)) {
            var sourceFactory = sourceConfig.migrationSourceSqlSessionFactory(source);
            var targetFactory = targetConfig.migrationTargetSqlSessionFactory(target);

            assertThat(source).isNotSameAs(target);
            assertThat(source.getPoolName()).isEqualTo("migration-source-test");
            assertThat(target.getPoolName()).isEqualTo("migration-target-test");
            assertThat(source.isReadOnly()).isTrue();
            assertThat(target.isReadOnly()).isFalse();
            assertThat(source.getUsername()).isEqualTo("legacy_reader");
            assertThat(target.getUsername()).isEqualTo("pdp_migration_writer");
            assertThat(sourceFactory).isNotSameAs(targetFactory);
            assertThat(sourceFactory.getConfiguration().getEnvironment().getDataSource())
                    .isSameAs(source);
            assertThat(targetFactory.getConfiguration().getEnvironment().getDataSource())
                    .isSameAs(target);
        }

        MapperScan sourceScan = LegacySourceMybatisConfig.class.getAnnotation(MapperScan.class);
        MapperScan targetScan = PdpTargetMybatisConfig.class.getAnnotation(MapperScan.class);
        assertThat(sourceScan.basePackages())
                .containsExactly("com.pdp.datamigration.mapper.source");
        assertThat(sourceScan.sqlSessionFactoryRef())
                .isEqualTo("migrationSourceSqlSessionFactory");
        assertThat(targetScan.basePackages())
                .containsExactly("com.pdp.datamigration.mapper.target");
        assertThat(targetScan.sqlSessionFactoryRef())
                .isEqualTo("migrationTargetSqlSessionFactory");
    }

    @Test
    void finishesSourceReadBeforeStartingIndependentTargetBatchTransaction() throws Exception {
        MigrationDataSourceProperties properties = properties();
        LegacySourceMybatisConfig sourceConfig = new LegacySourceMybatisConfig();
        PdpTargetMybatisConfig targetConfig = new PdpTargetMybatisConfig();
        MigrationTransactionConfig transactionConfig = new MigrationTransactionConfig();

        try (HikariDataSource source =
                        (HikariDataSource) sourceConfig.migrationSourceDataSource(properties);
                HikariDataSource target =
                        (HikariDataSource) targetConfig.migrationTargetDataSource(properties)) {
            JdbcTemplate sourceJdbc = new JdbcTemplate(source);
            JdbcTemplate targetJdbc = new JdbcTemplate(target);
            executeCommitted(
                    source,
                    "create table legacy_item(id int primary key, payload varchar(30))",
                    "insert into legacy_item(id, payload) values (1, 'source')");
            executeCommitted(
                    target,
                    "create table target_item(id int primary key, payload varchar(30))");

            var sourceManager =
                    transactionConfig.migrationSourceTransactionManager(source);
            var targetManager =
                    transactionConfig.migrationTargetTransactionManager(target);
            MigrationBatchBoundary boundary =
                    transactionConfig.migrationBatchBoundary(sourceManager, targetManager);

            assertThatThrownBy(() -> boundary.execute(
                            () -> {
                                assertThat(
                                                TransactionSynchronizationManager
                                                        .isCurrentTransactionReadOnly())
                                        .isTrue();
                                return sourceJdbc.queryForObject(
                                        "select payload from legacy_item where id = 1",
                                        String.class);
                            },
                            payload -> {
                                assertThat(
                                                TransactionSynchronizationManager
                                                        .isCurrentTransactionReadOnly())
                                        .isFalse();
                                targetJdbc.update(
                                        "insert into target_item(id, payload) values (1, ?)",
                                        payload);
                                throw new IllegalStateException("模拟目标批次失败");
                            }))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("目标批次失败");

            assertThat(sourceJdbc.queryForObject(
                            "select payload from legacy_item where id = 1", String.class))
                    .isEqualTo("source");
            assertThat(targetJdbc.queryForObject(
                            "select count(*) from target_item", Integer.class))
                    .isZero();
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        }
    }

    private static void executeCommitted(HikariDataSource dataSource, String... statements)
            throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
            connection.commit();
        }
    }

    private static MigrationDataSourceProperties properties() {
        MigrationDataSourceProperties properties = new MigrationDataSourceProperties();
        configure(
                properties.getSource(),
                "jdbc:h2:mem:migration-source;DB_CLOSE_DELAY=-1",
                "legacy_reader",
                "source-secret",
                "migration-source-test");
        configure(
                properties.getTarget(),
                "jdbc:h2:mem:migration-target;DB_CLOSE_DELAY=-1",
                "pdp_migration_writer",
                "target-secret",
                "migration-target-test");
        return properties;
    }

    private static void configure(
            MigrationDataSourceProperties.Endpoint endpoint,
            String jdbcUrl,
            String username,
            String password,
            String poolName) {
        endpoint.setJdbcUrl(jdbcUrl);
        endpoint.setUsername(username);
        endpoint.setPassword(password);
        endpoint.setDriverClassName("org.h2.Driver");
        endpoint.setPoolName(poolName);
        endpoint.setMinimumIdle(0);
        endpoint.setMaximumPoolSize(2);
        endpoint.setConnectionTimeoutMs(500);
        endpoint.setValidationTimeoutMs(250);
        endpoint.setKeepaliveTimeMs(0);
        endpoint.setMaxLifetimeMs(60_000);
    }
}
