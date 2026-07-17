package com.pdp.api;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * PDP API 应用入口。
 *
 * <p>聚合 19 个业务模块、公共持久化适配器与 MySQL 方言适配器。
 * 模块化单体架构（ADR 0001）：所有业务模块以 jar 形式聚合到本应用，
 * 模块边界由 Spring Modulith 与 ArchUnit 共同守护。
 *
 * <p>{@code @ComponentScan("com.pdp")} 扫描各业务模块与基础设施适配器的
 * {@code @Component}/{@code @Configuration}/{@code @Service}/{@code @Repository}；
 * {@code @MapperScan} 注册各业务模块 MyBatis Mapper 接口
 * （Mapper 仅位于 {@code com.pdp.persistence} 与 {@code com.pdp.datamigration} 基础设施边界，
 * 由 {@code DependencyPolicyTest} 守护）。
 */
@SpringBootApplication
@ComponentScan("com.pdp")
@MapperScan(basePackages = {"com.pdp.persistence", "com.pdp.datamigration"})
public class ApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiApplication.class, args);
    }
}
