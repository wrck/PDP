package com.pdp.persistence.config;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.annotation.DbType;
import com.pdp.shared.context.RequestContext;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;

/**
 * MyBatis-Plus 配置。
 *
 * <p>分页与乐观锁拦截器；全局配置关闭二级缓存与懒加载，localCacheScope=STATEMENT，
 * 未知列映射失败。审计字段自动填充从 {@link RequestContext} 获取操作者。
 *
 * <p>注意：MyBatis-Plus 乐观锁插件只负责受支持的内置更新；
 * 所有自定义 Mapper SQL 必须显式递增 revision（见 persistence-design.md）。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 分页：P1 仅 MySQL；Page/IPage 仅用于后台小数据集或适配器内部，不得作为外部分页契约
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(500L);
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);
        // 乐观锁：仅受支持的内置更新；自定义 SQL 必须显式校验并递增 revision
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }

    @Bean
    public MetaObjectHandler auditMetaObjectHandler() {
        return new AuditMetaObjectHandler();
    }

    /**
     * 审计字段自动填充：created_at/updated_at 填充 Instant.now()，
     * created_by/updated_by 从 RequestContext 获取操作者标识。
     */
    static class AuditMetaObjectHandler implements MetaObjectHandler {

        @Override
        public void insertFill(MetaObject metaObject) {
            Instant now = Instant.now();
            strictInsertFill(metaObject, "createdAt", Instant.class, now);
            strictInsertFill(metaObject, "updatedAt", Instant.class, now);
            strictInsertFill(metaObject, "revision", Integer.class, 1);
            fillActor(metaObject, "createdBy", true);
            fillActor(metaObject, "updatedBy", true);
        }

        @Override
        public void updateFill(MetaObject metaObject) {
            strictUpdateFill(metaObject, "updatedAt", Instant.class, Instant.now());
            fillActor(metaObject, "updatedBy", false);
        }

        private void fillActor(MetaObject metaObject, String field, boolean isInsert) {
            if (!metaObject.hasSetter(field)) {
                return;
            }
            RequestContext.getIfPresent().ifPresent(ctx -> {
                Object existing = isInsert ? null : getField(metaObject, field);
                if (existing == null) {
                    metaObject.setValue(field, ctx.operator().actor().actorId().toString());
                }
            });
        }

        private static Object getField(MetaObject metaObject, String field) {
            return metaObject.hasGetter(field) ? metaObject.getValue(field) : null;
        }
    }
}
