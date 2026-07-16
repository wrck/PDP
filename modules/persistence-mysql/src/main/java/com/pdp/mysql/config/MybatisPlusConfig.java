package com.pdp.mysql.config;

import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.pdp.mysql.typehandler.InstantUtcTypeHandler;
import com.pdp.mysql.typehandler.JsonDocumentTypeHandler;
import com.pdp.mysql.typehandler.UuidBinaryTypeHandler;
import com.pdp.persistence.type.JsonDocument;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.type.JdbcType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MybatisPlusConfig {

    @Bean
    MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(
                new PaginationInnerInterceptor(com.baomidou.mybatisplus.annotation.DbType.MYSQL));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }

    @Bean
    ConfigurationCustomizer pdpMybatisConfigurationCustomizer() {
        return configuration -> {
            configuration.setCacheEnabled(false);
            configuration.setLazyLoadingEnabled(false);
            configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
            configuration.setAutoMappingUnknownColumnBehavior(
                    AutoMappingUnknownColumnBehavior.FAILING);
            configuration.getTypeHandlerRegistry()
                    .register(UUID.class, JdbcType.BINARY, UuidBinaryTypeHandler.class);
            configuration.getTypeHandlerRegistry()
                    .register(Instant.class, JdbcType.TIMESTAMP, InstantUtcTypeHandler.class);
            configuration.getTypeHandlerRegistry()
                    .register(JsonDocument.class, JdbcType.OTHER, JsonDocumentTypeHandler.class);
        };
    }

    @Bean
    @ConditionalOnMissingBean
    AuditActorProvider auditActorProvider() {
        return Optional::empty;
    }

    @Bean
    MetaObjectHandler auditMetaObjectHandler(AuditActorProvider actorProvider) {
        return new MybatisAuditMetaObjectHandler(actorProvider);
    }
}
