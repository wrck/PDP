package com.pdp.workflow.infrastructure.flowable;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.pdp.persistence.config.DataSourceKeys;
import org.flowable.common.spring.EngineConfigurationConfigurer;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * Flowable Process Engine 配置（T079）。
 *
 * <p>落实 ADR-0005 § 8（事务与 MySQL schema）与 persistence-design.md § 2-3：
 * <ul>
 *   <li><strong>独立 HikariCP 池与数据库账号</strong>：从 {@link DynamicRoutingDataSource} 按
 *       {@link DataSourceKeys#WORKFLOW_ENGINE} 键解析底层 HikariCP {@link DataSource}，
 *       不创建新池，保持 {@code application-datasource.yml} 连接预算单一来源。</li>
 *   <li><strong>独立事务管理器</strong>：基于 workflowEngine 数据源的 {@link DataSourceTransactionManager}，
 *       与 {@code pdpPrimary} 事务完全隔离；不启用 XA / Seata；禁止嵌套事务；
 *       业务事实先在 {@code pdpPrimary} 本地事务提交并登记 outbox，再由幂等编排消费者启动或推进流程。</li>
 *   <li><strong>关闭自动建表与升级</strong>：YAML {@code flowable.database-schema-update: false}
 *       与本配置中的 {@link SpringProcessEngineConfiguration#setDatabaseSchemaUpdate(String)} 双重锁定；
 *       引擎 DDL/升级脚本版本化管理由 T080 落地。</li>
 *   <li><strong>不暴露内置 REST/IDM</strong>：仅 {@code flowable-spring-boot-starter-process} 在 classpath，
 *       REST/IDM/CMMN/DMN/JPA starter 由 T031 依赖治理排除；
 *       Spring Process Engine 默认不启用 IDM，本配置不再额外引入 IDM 配置。</li>
 *   <li><strong>表前缀</strong>：Flowable 默认 {@code ACT_} / {@code FLW_} 前缀与 PDP 业务表
 *       {@code pdp_} 前缀天然分离；如需进一步隔离可通过
 *       {@link SpringProcessEngineConfiguration#setDatabaseTablePrefix(String)} 配置。</li>
 * </ul>
 *
 * <p><strong>事实权威边界</strong>（ADR-0005 § 7）：Flowable 仅承担编排协调职责，
 * 不保存业务事实；业务事实由 {@code pdpPrimary} 上的领域聚合权威持有。
 * 流程实例和人工任务不得成为项目、任务、交付件、审批、权限或审计事实的唯一存储。
 *
 * <p><strong>覆盖默认 DataSource</strong>：Flowable Spring Boot starter 默认使用主 {@link DataSource}
 * （即 {@link DynamicRoutingDataSource}）；本配置通过 {@link EngineConfigurationConfigurer}
 * 将引擎数据源替换为 workflowEngine 解析的 HikariCP，保证业务 Mapper 不会路由到 Flowable 表，
 * Flowable 也不会通过主路由访问 PDP 业务表。
 */
@Configuration
public class FlowableEngineConfig {

    /**
     * 从动态数据源路由器解析 {@code workflowEngine} 底层 {@link DataSource}。
     *
     * <p>不创建新的 HikariCP 池，保持 {@code application-datasource.yml} 连接预算单一来源。
     * 解析失败时立即抛出 {@link IllegalStateException}，使应用启动失败
     * （配合 {@code workflowEngine.hikari.initialization-fail-timeout=1}）。
     *
     * @param dynamicRouting 平台动态数据源路由器
     * @return workflowEngine 键对应的底层 HikariCP 数据源
     * @throws IllegalStateException workflowEngine 数据源未注册
     */
    @Bean("workflowEngineDataSource")
    public DataSource workflowEngineDataSource(DynamicRoutingDataSource dynamicRouting) {
        DataSource resolved = dynamicRouting.getDataSource(DataSourceKeys.WORKFLOW_ENGINE);
        if (resolved == null) {
            throw new IllegalStateException(
                    "workflowEngine 数据源未注册；检查 application-datasource.yml 中 workflowEngine 配置");
        }
        return resolved;
    }

    /**
     * Flowable 引擎独立事务管理器。
     *
     * <p>基于 {@code workflowEngineDataSource} 的 {@link DataSourceTransactionManager}：
     * <ul>
     *   <li>禁止嵌套事务：{@link DataSourceTransactionManager#setNestedTransactionAllowed(boolean)} false。</li>
     *   <li>校验已存在事务：防止业务事务隐式包装 Flowable 事务导致边界泄漏。</li>
     *   <li>默认超时 60 秒：人工任务办理与流程推进不应长时间占用连接。</li>
     * </ul>
     *
     * <p><strong>XA 禁用</strong>：业务事实先在 {@code pdpPrimary} 本地事务提交并登记 outbox，
     * 再由幂等编排消费者启动或推进流程实例；任何重试不得生成重复审批动作或业务结果
     * （ADR-0005 § 7、§ 8）。
     *
     * <p>该 Bean 为非 primary：业务调用默认走动态数据源主事务管理器，Flowable 引擎通过
     * {@link #workflowProcessEngineConfigurer(DataSource, PlatformTransactionManager)} 显式绑定本事务管理器。
     *
     * @param dataSource workflowEngine 数据源
     * @return 独立事务管理器 Bean
     */
    @Bean("workflowTransactionManager")
    public PlatformTransactionManager workflowTransactionManager(
            @Qualifier("workflowEngineDataSource") DataSource dataSource) {
        DataSourceTransactionManager tm = new DataSourceTransactionManager(dataSource);
        tm.setNestedTransactionAllowed(false);
        tm.setValidateExistingTransaction(true);
        tm.setDefaultTimeout(60);
        return tm;
    }

    /**
     * Flowable Process Engine 配置器：将 {@link SpringProcessEngineConfiguration} 绑定到
     * workflowEngine 数据源与事务管理器，并锁定生产配置。
     *
     * <p><strong>关键作用</strong>：Flowable Spring Boot starter 默认使用主 {@link DataSource}
     * （即 {@link DynamicRoutingDataSource}），这会导致：
     * <ol>
     *   <li>Flowable 引擎操作可能被错误路由到 PDP 业务表；</li>
     *   <li>业务 Mapper 通过 {@code @DS} 可能误访问 Flowable 表。</li>
     * </ol>
     * 通过 configurer 强制将引擎数据源替换为 {@code workflowEngineDataSource}，
     * 确保 Flowable 始终直接持有 HikariCP，绕过动态路由。
     *
     * <p><strong>双重锁定 schema 自动更新</strong>：除 YAML {@code flowable.database-schema-update: false}
     * 外，这里再次显式 {@link SpringProcessEngineConfiguration#setDatabaseSchemaUpdate(String)
     * setDatabaseSchemaUpdate("false")}，防止误配触发生产自动建表或升级。
     * 引擎 DDL/升级脚本版本化管理由 T080 落地。
     *
     * <p><strong>MySQL 方言显式声明</strong>：避免 Flowable 通过 JDBC metadata 推断数据库类型时
     * 受 HikariCP 连接工厂包装或读取副本影响，统一锁定为 {@code mysql}。
     *
     * @param workflowDataSource workflowEngine 数据源（HikariCP）
     * @param workflowTm         workflowEngine 独立事务管理器
     * @return SpringProcessEngineConfiguration 的 EngineConfigurationConfigurer
     */
    @Bean
    public EngineConfigurationConfigurer<SpringProcessEngineConfiguration>
            workflowProcessEngineConfigurer(
                    @Qualifier("workflowEngineDataSource") DataSource workflowDataSource,
                    @Qualifier("workflowTransactionManager") PlatformTransactionManager workflowTm) {
        return config -> {
            // 1. 绑定独立数据源：绕过 DynamicRoutingDataSource，直接持有 HikariCP
            config.setDataSource(workflowDataSource);
            // 2. 绑定独立事务管理器：与 pdpPrimary 事务隔离
            config.setTransactionManager(workflowTm);
            // 3. 双重锁定：关闭自动建表与升级（生产）
            config.setDatabaseSchemaUpdate("false");
            // 4. 显式声明 MySQL 方言，避免 JDBC metadata 推断在分库环境下失败
            config.setDatabaseType("mysql");
        };
    }
}
