package com.pdp.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class LiquibaseMySqlMatrixTest {

    @Container
    private static final MySQLContainer MYSQL =
            new MySQLContainer("mysql:8.4").withDatabaseName("pdp_liquibase");

    @Test
    void installsEmptyDatabaseSupportsIncrementalUpgradeAndReappliesAfterRollback()
            throws Exception {
        try (Connection connection = MYSQL.createConnection("")) {
            apply(connection, "db/changelog/db.changelog-master.xml");
            assertThat(tableExists(connection, "pdp_audit_digest")).isTrue();
            assertThat(indexExists(connection, "idx_audit_workspace_recorded")).isFalse();

            apply(connection, "db/changelog/db.changelog-mysql.xml");
            assertThat(indexExists(connection, "idx_audit_workspace_recorded")).isTrue();

            apply(connection, "db/changelog/db.changelog-mysql.xml");
            assertThat(executedChangeSetCount(connection)).isGreaterThanOrEqualTo(6);

            try (Liquibase liquibase = liquibase(connection, "db/changelog/db.changelog-mysql.xml")) {
                liquibase.rollback(1, "");
            }
            assertThat(indexExists(connection, "idx_audit_workspace_recorded")).isFalse();

            apply(connection, "db/changelog/db.changelog-mysql.xml");
            assertThat(indexExists(connection, "idx_audit_workspace_recorded")).isTrue();
        }
    }

    private static void apply(Connection connection, String changelog) throws Exception {
        try (Liquibase liquibase = liquibase(connection, changelog)) {
            liquibase.update(new Contexts());
        }
    }

    private static Liquibase liquibase(Connection connection, String changelog) throws Exception {
        return new Liquibase(
                changelog,
                new ClassLoaderResourceAccessor(LiquibaseMySqlMatrixTest.class.getClassLoader()),
                new JdbcConnection(connection));
    }

    private static boolean tableExists(Connection connection, String tableName) throws Exception {
        try (var resultSet = connection.getMetaData()
                .getTables(connection.getCatalog(), null, tableName, new String[] {"TABLE"})) {
            return resultSet.next();
        }
    }

    private static boolean indexExists(Connection connection, String indexName) throws Exception {
        try (var statement = connection.prepareStatement("""
                SELECT COUNT(*)
                  FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND index_name = ?
                """)) {
            statement.setString(1, indexName);
            try (var resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0;
            }
        }
    }

    private static int executedChangeSetCount(Connection connection) throws Exception {
        try (var resultSet = connection.createStatement()
                .executeQuery("SELECT COUNT(*) FROM DATABASECHANGELOG")) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }
}
