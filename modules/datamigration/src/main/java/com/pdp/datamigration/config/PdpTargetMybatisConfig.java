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
 * 迁移目标库专用 MyBatis 配置（T050）。
 *
 * <p>落实 persistence-design.md 第 9 节：目标库使用独立 {@link SqlSessionFactory}、Mapper 扫描包、
 * 事务管理器和 HikariCP 池；目标库仅接受迁移执行器写入，开放业务写入前不得作为 {@code pdpPrimary}。
 *
 * <p>关键约束：
 * <ul>
 *   <li>独立 {@code SqlSessionFactory}：扫描 {@code com.pdp.datamigration.target.mapper} 包，
 *       与业务 Mapper 扫描路径完全隔离。</li>
 *   <li>独立事务管理器 {@code migrationTargetTransactionManager}：每个批次在单一目标库本地事务内提交，
 *       跨源/目标不启用 XA / Seata。</li>
 *   <li>DataSource 从 {@link DynamicRoutingDataSource} 按 {@code migrationTarget} 键解析底层 HikariCP，
 *       保持连接池配置单一来源。</li>
 *   <li>仅在迁移计划批准后通过 {@code pdp.migration.enabled=true} 激活。</li>
 *   <li>目标库写入由迁移执行器独占；普通业务 Mapper 不得访问，由 ArchUnit 守护包边界。</li>
 * </ul>
 *
 * <p><strong>主权转换约束</strong>：目标库在 Go 决策完成前不得作为 {@code pdpPrimary}；
 * 切换数据库仍执行全量、增量、冻结、核对和主权转换，不通过把 {@code pdpPrimary} 路由到
 * {@code migrationTarget} 直接完成（见 persistence-design.md 第 9 节末段）。
 */
@Configuration
@ConditionalOnProperty(name = "pdp.migration.enabled", havingValue = "true")
@MapperScan(
        basePackages = "com.pdp.datamigration.target.mapper",
        sqlSessionFactoryRef = "migrationTargetSqlSessionFactory",
        sqlSessionTemplateRef = "migrationTargetSqlSessionTemplate")
public class PdpTargetMybatisConfig {

    /**
     * 从动态数据源路由器解析 {@code migrationTarget} 底层 {@link DataSource}。
     *
     * <p>不创建新的 HikariCP 池，保持 {@code application-datasource.yml} 连接预算单一来源。
     * 目标池已声明 {@code read-only: false}，仅迁移执行器写入。
     */
    @Bean("migrationTargetDataSource")
    public DataSource migrationTargetDataSource(DynamicRoutingDataSource dynamicRouting) {
        DataSource resolved = dynamicRouting.getDataSource(DataSourceKeys.MIGRATION_TARGET);
        if (resolved == null) {
            throw new IllegalStateException(
                    "migrationTarget 数据源未注册；检查 application-datasource.yml 或 pdp.migration.enabled");
        }
        return resolved;
    }

    /**
     * 目标库专用 {@link SqlSessionFactory}。
     *
     * <p>不引入 MyBatis-Plus 拦截器（迁移批次由 {@link MigrationBatchContext} 管理边界，
     * 不参与在线乐观锁/分页插件）；TypeHandler 注册复用公共包。
     * Mapper XML 路径限定在 {@code migration-target/} 子目录。
     */
    @Bean("migrationTargetSqlSessionFactory")
    public SqlSessionFactory migrationTargetSqlSessionFactory(
            @Qualifier("migrationTargetDataSource") DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setTypeHandlersPackage("com.pdp.persistence.typehandler");
        factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:/mapper/migration-target/**/*.xml"));
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
     * 目标库专用 {@link org.mybatis.spring.SqlSessionTemplate}。
     *
     * <p>使用 {@code BATCH} 执行器类型，单批次内积累 PreparedStatement、缓冲 JDBC 操作，
     * 在目标事务提交时一次性 flush，配合检查点实现幂等批次写入。
     */
    @Bean("migrationTargetSqlSessionTemplate")
    public org.mybatis.spring.SqlSessionTemplate migrationTargetSqlSessionTemplate(
            @Qualifier("migrationTargetSqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new org.mybatis.spring.SqlSessionTemplate(
                sqlSessionFactory, org.apache.ibatis.session.ExecutorType.BATCH);
    }

    /**
     * 目标库专用事务管理器（读写）。
     *
     * <p>每个批次在单一目标库本地事务内提交并保存检查点；
     * 与 {@code legacySourceTransactionManager} 隔离，禁止跨源 XA。
     * 默认超时 600 秒（单批次可较长），由 {@code MigrationBatchContext} 显式管理提交/回滚。
     */
    @Bean("migrationTargetTransactionManager")
    public PlatformTransactionManager migrationTargetTransactionManager(
            @Qualifier("migrationTargetDataSource") DataSource dataSource) {
        DataSourceTransactionManager tm = new DataSourceTransactionManager(dataSource);
        tm.setNestedTransactionAllowed(false);
        tm.setValidateExistingTransaction(true);
        tm.setDefaultTimeout(600);
        return tm;
    }
}
