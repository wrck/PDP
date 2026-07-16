package com.pdp.mysql.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import java.time.Instant;
import org.apache.ibatis.reflection.MetaObject;

final class MybatisAuditMetaObjectHandler implements MetaObjectHandler {

    private final AuditActorProvider actorProvider;

    MybatisAuditMetaObjectHandler(AuditActorProvider actorProvider) {
        this.actorProvider = actorProvider;
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        Instant now = Instant.now();
        strictInsertFill(metaObject, "createdAt", Instant.class, now);
        strictInsertFill(metaObject, "updatedAt", Instant.class, now);
        strictInsertFill(metaObject, "revision", Long.class, 0L);
        actorProvider.currentActorId().ifPresent(actorId -> {
            strictInsertFill(metaObject, "createdBy", java.util.UUID.class, actorId);
            strictInsertFill(metaObject, "updatedBy", java.util.UUID.class, actorId);
        });
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updatedAt", Instant.class, Instant.now());
        actorProvider.currentActorId()
                .ifPresent(actorId -> strictUpdateFill(
                        metaObject, "updatedBy", java.util.UUID.class, actorId));
    }
}
