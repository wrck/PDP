package com.pdp.mysql;

import com.pdp.persistence.provider.DatabaseCapabilityProfile;
import com.pdp.persistence.provider.DatabaseType;
import com.pdp.persistence.provider.PersistenceProvider;

/**
 * MySQL 8.4 持久化适配器。
 *
 * <p>P1 唯一认证适配器。实现 MySQL 差异化 DDL、索引、查询和运维检查。
 * 通过 {@link PersistenceProviderRegistry} 注册为唯一激活适配器。
 */
public class MysqlPersistenceProvider implements PersistenceProvider {

    @Override
    public String databaseProduct() {
        return "MySQL";
    }

    @Override
    public DatabaseCapabilityProfile capabilityProfile() {
        return DatabaseCapabilityProfile.mysql84Baseline();
    }

    @Override
    public boolean supports(DatabaseCapabilityProfile required) {
        if (required == null || required.databaseType() != DatabaseType.MYSQL) {
            return false;
        }
        DatabaseCapabilityProfile certified = capabilityProfile();
        return certified.databaseVersion().equals(required.databaseVersion())
                && certified.characterSet().equalsIgnoreCase(required.characterSet())
                && certified.transactionEngine().equalsIgnoreCase(required.transactionEngine());
    }
}
