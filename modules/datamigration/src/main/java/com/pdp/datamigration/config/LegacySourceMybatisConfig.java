package com.pdp.datamigration.config;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.pdp.persistence.config.DataSourceKeys;
import com.pdp.persistence.typehandler.StringEnumTypeHandler;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * 历史 MySQL 源库专用 MyBatis 配置（T049）。
 *
 * <p>落实 persistence-design.md 第 9 节：迁移源库使用独立 {@link SqlSessionFactory}、Mapper 扫描包、
 * 事务管理器和 HikariCP 池；业务 Mapper 不能注入迁移数据源，迁移 Mapper 不能使用业务管理权限。
 *
 * <p>关键约束：
 * <ul>
 *   <li>源库 <strong>只读</strong>：JDBC 连接强制 {@code readOnly=true}，事务管理器声明只读语义。</li>
 *   <li>独立 {@code SqlSessionFactory}：扫描 {@code com.pdp.datamigration.legacy.mapper} 包，
 *       不与业务 Mapper 共用工厂或扫描路径。</li>
 *   <li>独立事务管理器 {@code legacySourceTransactionManager}：仅用于读源批次；与目标库事务管理器隔离，
 *       不参与 XA / Seata。</li>
 *   <li>DataSource 从 {@link DynamicRoutingDataSource} 按 {@code migrationSource} 键解析底层 HikariCP，
 *       保持连接池配置单一事实来源（HikariCP 已声明 {@code read-only: true}）。</li>
 *   <li>仅在迁移计划批准后通过 {@code pdp.migration.enabled=true} 激活；迁移结束后置 false 即卸载，
 *       避免长期持有源库连接和凭据。</li>
 * </ul>
 *
 * <p>本配置不通过 {@code @DS} 路由——{@code @DS} 仅允许用于 persistence/datamigration 基础设施，
 * 由 {@link com.pdp.persistence.routing.DataSourceRoutingGuard} 守护事务内切换拒绝语义。
 */
@Configuration
@ConditionalOnProperty(name = "pdp.migration.enabled", havingValue = "true")
@MapperScan(
        basePackages = "com.pdp.datamigration.legacy.mapper",
        sqlSessionFactoryRef = "legacySourceSqlSessionFactory",
        sqlSessionTemplateRef = "legacySourceSqlSessionTemplate")
public class LegacySourceMybatisConfig {

    /**
     * 从动态数据源路由器解析 {@code migrationSource} 底层 {@link DataSource}。
     *
     * <p>不创建新的 HikariCP 池，保持 {@code application-datasource.yml} 中连接预算、超时和存活检测
     * 单一来源。{@code migrationSource} 池已声明 {@code read-only: true}，由 HikariCP 在物理连接层强制。
     *
     * @param dynamicRouting 动态数据源路由器（注入 primary DataSource）
     * @return 源库底层 DataSource（HikariDataSource）
     */
    @Bean("legacySourceDataSource")
    public DataSource legacySourceDataSource(DynamicRoutingDataSource dynamicRouting) {
        DataSource resolved = dynamicRouting.getDataSource(DataSourceKeys.MIGRATION_SOURCE);
        if (resolved == null) {
            throw new IllegalStateException(
                    "migrationSource 数据源未注册；检查 application-datasource.yml 或 pdp.migration.enabled");
        }
        return resolved;
    }

    /**
     * 源库专用 {@link SqlSessionFactory}。
     *
     * <p>不引入 MyBatis-Plus 拦截器（迁移只读、不参与乐观锁/分页插件）；
     * TypeHandler 注册复用公共 {@code com.pdp.persistence.typehandler} 包。
     * Mapper XML 路径限定在 {@code legacy/} 子目录，防止与业务 Mapper XML 冲突。
     */
    @Bean("legacySourceSqlSessionFactory")
    public SqlSessionFactory legacySourceSqlSessionFactory(
            @Qualifier("legacySourceDataSource") DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setTypeHandlersPackage("com.pdp.persistence.typehandler");
        factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:/mapper/legacy/**/*.xml"));
        org.apache.ibatis.session.Configuration config = new org.apache.ibatis.session.Configuration();
        config.setCacheEnabled(false);
        config.setLazyLoadingEnabled(false);
        config.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT);
        config.setMapUnderscoreToCamelCase(true);
        config.setDefaultEnumTypeHandler(StringEnumTypeHandler.class);
        factory.setConfiguration(config);
        return factory.getObject();
    }

    /**
     * 源库专用 {@link org.mybatis.spring.SqlSessionTemplate}。
     *
     * <p>使用 {@code REUSE} 执行器类型，单批次内复用 PreparedStatement；
     * 只读语义由 {@code @Transactional(readOnly = true)} 在服务层声明，并由 HikariCP 连接层强制。
     */
    @Bean("legacySourceSqlSessionTemplate")
    public org.mybatis.spring.SqlSessionTemplate legacySourceSqlSessionTemplate(
            @Qualifier("legacySourceSqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new org.mybatis.spring.SqlSessionTemplate(
                sqlSessionFactory, org.apache.ibatis.session.ExecutorType.REUSE);
    }

    /**
     * 源库专用事务管理器（只读）。
     *
     * <p>用于声明 {@code @Transactional("legacySourceTransactionManager", readOnly = true)}，
     * 由 JDBC 驱动和 InnoDB 协同强制只读语义，拒绝隐式写入。
     * 与 {@code migrationTargetTransactionManager} 隔离，禁止跨源 XA。
     */
    @Bean("legacySourceTransactionManager")
    public PlatformTransactionManager legacySourceTransactionManager(
            @Qualifier("legacySourceDataSource") DataSource dataSource) {
        DataSourceTransactionManager tm = new DataSourceTransactionManager(dataSource);
        tm.setNestedTransactionAllowed(false);
        tm.setValidateExistingTransaction(true);
        tm.setDefaultTimeout(60);
        return tm;
    }
}
