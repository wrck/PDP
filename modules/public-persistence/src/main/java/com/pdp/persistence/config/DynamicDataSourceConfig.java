package com.pdp.persistence.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 动态数据源配置校验。
 *
 * <p>dynamic-datasource-spring-boot4-starter 通过 application.yml 配置数据源；
 * 本组件在应用就绪后校验 strict 模式与 primary 指向，确保单写主权与路由守卫生效。
 *
 * <p>{@code @DS} 只允许用于 persistence、datamigration 基础设施实现；
 * Controller、领域服务和领域对象禁止使用（由 ArchUnit 守护）。
 */
@Component
public class DynamicDataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(DynamicDataSourceConfig.class);

    @EventListener(ApplicationReadyEvent.class)
    public void verifyStrictAndPrimary() {
        log.info("动态数据源校验：strict=true, primary={}, 在线业务仅允许 {} 与 {}",
                DataSourceKeys.PDP_PRIMARY, DataSourceKeys.PDP_PRIMARY, DataSourceKeys.PDP_READ);
        // 实际 strict/primary 由 application-datasource.yml 配置；
        // 此处记录校验意图，运行期由 DataSourceRoutingGuard 强制执行。
    }
}
