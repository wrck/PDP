package com.pdp.persistence;

import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.OfflineConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;

class LiquibaseChangelogValidationTest {

    @Test
    void parsesAndValidatesCommonAndMysqlChangelogTrees() throws Exception {
        validate("db/changelog/db.changelog-master.xml");
        validate("db/changelog/db.changelog-mysql.xml");
    }

    private static void validate(String changelog) throws Exception {
        var resourceAccessor =
                new ClassLoaderResourceAccessor(LiquibaseChangelogValidationTest.class.getClassLoader());
        var connection = new OfflineConnection("offline:mysql?version=8.4", resourceAccessor);
        var database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(connection);
        try (Liquibase liquibase = new Liquibase(changelog, resourceAccessor, database)) {
            liquibase.validate();
        }
    }
}
