package com.pdp.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pdp.mysql.MysqlPersistenceProvider;
import com.pdp.persistence.provider.DatabaseCapabilityProfile;
import com.pdp.persistence.provider.DatabaseDialect;
import com.pdp.persistence.provider.DatabaseProduct;
import com.pdp.persistence.provider.DatabaseSwitchCapability;
import com.pdp.persistence.provider.DatabaseVersion;
import com.pdp.persistence.provider.PersistenceProvider;
import com.pdp.persistence.provider.PersistenceProviderRegistry;
import com.pdp.persistence.provider.UnsupportedDatabaseCapabilityException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PersistenceProviderExtensionContractTest {

    @Test
    void registersExactlyOneProviderForEachDatabaseProduct() {
        PersistenceProviderRegistry registry =
                new PersistenceProviderRegistry(List.of(new MysqlPersistenceProvider()));

        assertThat(registry.require(DatabaseProduct.MYSQL).providerKey())
                .isEqualTo("pdp.persistence.mysql");
        assertThat(new DatabaseVersion("8.4").compareTo(new DatabaseVersion("8.4.0")))
                .isZero();
    }

    @Test
    void rejectsDuplicateActiveProvidersForOneProduct() {
        MysqlPersistenceProvider delegate = new MysqlPersistenceProvider();
        PersistenceProvider secondMysqlProvider = new PersistenceProvider() {
            public String providerKey() {
                return "pdp.persistence.mysql.alternative";
            }

            public DatabaseProduct databaseProduct() {
                return delegate.databaseProduct();
            }

            public DatabaseCapabilityProfile certifiedBaseline() {
                return delegate.certifiedBaseline();
            }

            public Set<DatabaseSwitchCapability> switchCapabilities() {
                return delegate.switchCapabilities();
            }

            public DatabaseDialect dialect() {
                return delegate.dialect();
            }
        };

        assertThatThrownBy(() -> new PersistenceProviderRegistry(List.of(
                        delegate, secondMysqlProvider)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("只能激活一个");
    }

    @Test
    void rejectsUnknownDatabaseProducts() {
        PersistenceProviderRegistry registry =
                new PersistenceProviderRegistry(List.of(new MysqlPersistenceProvider()));

        assertThatThrownBy(() -> registry.require(new DatabaseProduct("POSTGRESQL")))
                .isInstanceOf(UnsupportedDatabaseCapabilityException.class)
                .hasMessageContaining("没有已激活");
    }

    @Test
    void certifiesMysqlToMysqlAndRejectsUnregisteredCombination() {
        PersistenceProviderRegistry registry =
                new PersistenceProviderRegistry(List.of(new MysqlPersistenceProvider()));
        DatabaseCapabilityProfile mysql = new MysqlPersistenceProvider().certifiedBaseline();

        DatabaseSwitchCapability capability =
                registry.requireSwitchCapability(mysql, mysql);

        assertThat(capability.sourceDatabaseProduct()).isEqualTo(DatabaseProduct.MYSQL);
        assertThat(capability.targetDatabaseProduct()).isEqualTo(DatabaseProduct.MYSQL);

        DatabaseCapabilityProfile postgresql = new DatabaseCapabilityProfile(
                new DatabaseProduct("POSTGRESQL"),
                new DatabaseVersion("16.0"),
                "TEST",
                Set.of());
        assertThatThrownBy(() -> registry.requireSwitchCapability(mysql, postgresql))
                .isInstanceOf(UnsupportedDatabaseCapabilityException.class);
    }

    @Test
    void acceptsAContractCompatibleMockProviderWithoutMysqlTypes() {
        DatabaseProduct mockProduct = new DatabaseProduct("MOCKDB");
        PersistenceProvider mockProvider = new PersistenceProvider() {
            @Override
            public String providerKey() {
                return "test.persistence.mock";
            }

            @Override
            public DatabaseProduct databaseProduct() {
                return mockProduct;
            }

            @Override
            public DatabaseCapabilityProfile certifiedBaseline() {
                return new DatabaseCapabilityProfile(
                        mockProduct, new DatabaseVersion("1.0"), "TEST", Set.of("TRANSACTIONS"));
            }

            @Override
            public Set<DatabaseSwitchCapability> switchCapabilities() {
                return Set.of();
            }

            @Override
            public DatabaseDialect dialect() {
                return new DatabaseDialect() {
                    public String quoteIdentifier(String identifier) {
                        return '"' + identifier + '"';
                    }

                    public String booleanLiteral(boolean value) {
                        return Boolean.toString(value);
                    }

                    public String jsonColumnType() {
                        return "JSON";
                    }

                    public String uuidColumnType() {
                        return "UUID";
                    }
                };
            }
        };

        PersistenceProviderRegistry registry =
                new PersistenceProviderRegistry(List.of(mockProvider));

        assertThat(registry.require(mockProduct)).isSameAs(mockProvider);
    }
}
