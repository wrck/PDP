package com.pdp.mysql.config;

import com.pdp.mysql.MysqlDialectAdapter;
import com.pdp.mysql.MysqlPersistenceProvider;
import com.pdp.persistence.provider.PersistenceProvider;
import com.pdp.persistence.provider.PersistenceProviderRegistry;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MysqlPersistenceProviderConfiguration {

    @Bean
    MysqlDialectAdapter mysqlDialectAdapter() {
        return new MysqlDialectAdapter();
    }

    @Bean
    MysqlPersistenceProvider mysqlPersistenceProvider() {
        return new MysqlPersistenceProvider();
    }

    @Bean
    PersistenceProviderRegistry persistenceProviderRegistry(
            List<PersistenceProvider> providers) {
        return new PersistenceProviderRegistry(providers);
    }
}
