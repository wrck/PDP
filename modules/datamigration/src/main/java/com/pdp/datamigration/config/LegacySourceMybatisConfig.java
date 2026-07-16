package com.pdp.datamigration.config;

import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MigrationDataSourceProperties.class)
@MapperScan(
        basePackages = "com.pdp.datamigration.mapper.source",
        sqlSessionFactoryRef = "migrationSourceSqlSessionFactory")
public class LegacySourceMybatisConfig {

    @Bean(name = "migrationSourceDataSource", destroyMethod = "close")
    DataSource migrationSourceDataSource(MigrationDataSourceProperties properties) {
        return MigrationDataSourceFactory.create(
                properties.getSource(), "migrationSource", true);
    }

    @Bean(name = "migrationSourceSqlSessionFactory")
    SqlSessionFactory migrationSourceSqlSessionFactory(
            @Qualifier("migrationSourceDataSource") DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        org.apache.ibatis.session.Configuration configuration =
                new org.apache.ibatis.session.Configuration();
        configuration.setCacheEnabled(false);
        configuration.setLazyLoadingEnabled(false);
        configuration.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT);
        factory.setConfiguration(configuration);
        return factory.getObject();
    }
}
