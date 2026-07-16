package com.pdp.mysql;

import com.pdp.persistence.provider.DatabaseCapabilityProfile;
import com.pdp.persistence.provider.DatabaseProduct;
import com.pdp.persistence.provider.DatabaseVersion;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Set;

public final class MysqlDatabaseCapabilityProbe {

    public DatabaseCapabilityProfile probe(Connection connection) throws SQLException {
        var metadata = connection.getMetaData();
        String productName = metadata.getDatabaseProductName();
        if (productName == null || !productName.toLowerCase(Locale.ROOT).contains("mysql")) {
            throw new IllegalArgumentException("连接的数据库产品不是 MySQL");
        }
        DatabaseVersion version = new DatabaseVersion(metadata.getDatabaseProductVersion());
        DatabaseCapabilityProfile baseline = new MysqlPersistenceProvider().certifiedBaseline();
        if (version.compareTo(baseline.databaseVersion()) < 0) {
            throw new IllegalArgumentException("MySQL 版本低于认证基线: " + version.value());
        }
        return new DatabaseCapabilityProfile(
                DatabaseProduct.MYSQL,
                version,
                baseline.certificationBaseline(),
                Set.copyOf(baseline.capabilities()));
    }
}
