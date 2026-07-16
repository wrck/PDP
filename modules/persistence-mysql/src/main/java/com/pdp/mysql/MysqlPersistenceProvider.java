package com.pdp.mysql;

import com.pdp.persistence.provider.DatabaseCapabilityProfile;
import com.pdp.persistence.provider.DatabaseDialect;
import com.pdp.persistence.provider.DatabaseProduct;
import com.pdp.persistence.provider.DatabaseSwitchCapability;
import com.pdp.persistence.provider.DatabaseVersion;
import com.pdp.persistence.provider.PersistenceProvider;
import java.util.List;
import java.util.Set;

public final class MysqlPersistenceProvider implements PersistenceProvider {

    private static final DatabaseVersion MINIMUM_VERSION = new DatabaseVersion("8.4.0");
    private static final DatabaseCapabilityProfile BASELINE = new DatabaseCapabilityProfile(
            DatabaseProduct.MYSQL,
            MINIMUM_VERSION,
            "PDP-P1-MYSQL-8.4",
            Set.of(
                    "TRANSACTIONS",
                    "OPTIMISTIC_LOCKING",
                    "JSON",
                    "UUID_BINARY",
                    "DATABASE_SWITCH"));

    private final MysqlDialectAdapter dialect = new MysqlDialectAdapter();

    @Override
    public String providerKey() {
        return "pdp.persistence.mysql";
    }

    @Override
    public DatabaseProduct databaseProduct() {
        return DatabaseProduct.MYSQL;
    }

    @Override
    public DatabaseCapabilityProfile certifiedBaseline() {
        return BASELINE;
    }

    @Override
    public Set<DatabaseSwitchCapability> switchCapabilities() {
        return Set.of(new DatabaseSwitchCapability(
                DatabaseProduct.MYSQL,
                DatabaseProduct.MYSQL,
                MINIMUM_VERSION,
                MINIMUM_VERSION,
                DatabaseSwitchCapability.CapabilityStatus.CERTIFIED,
                List.of("源目标必须是不同受管部署", "任一时刻只能存在一个写入主权")));
    }

    @Override
    public DatabaseDialect dialect() {
        return dialect;
    }
}
