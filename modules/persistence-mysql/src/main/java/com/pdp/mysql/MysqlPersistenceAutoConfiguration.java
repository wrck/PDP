package com.pdp.mysql;

import com.pdp.persistence.provider.PersistenceProvider;
import com.pdp.persistence.provider.PersistenceProviderRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MySQL 持久化适配器自动装配。
 *
 * <p>注册 {@link MysqlPersistenceProvider} 为 P1 唯一激活适配器。
 */
@Configuration
public class MysqlPersistenceAutoConfiguration {

    @Bean
    public PersistenceProvider mysqlPersistenceProvider(PersistenceProviderRegistry registry) {
        MysqlPersistenceProvider provider = new MysqlPersistenceProvider();
        registry.register(provider);
        return provider;
    }

    @Bean
    public MysqlDialectSupport mysqlDialectSupport() {
        return new MysqlDialectSupport();
    }
}
