package com.pdp.shared.context;

import java.util.UUID;

/**
 * 参与者引用值对象。
 *
 * <p>对应数据模型 {@code ActorRef}：{@code actor_type}、{@code actor_id}、{@code display_snapshot}。
 * 用于审计、操作者上下文和状态迁移记录，支持用户、组织、角色、外部参与者和系统执行身份。
 *
 * <p>{@code displaySnapshot} 保存参与者展示名的脱敏快照，不依赖实时查询。
 */
public record ActorRef(ActorType actorType, UUID actorId, String displaySnapshot) {

    public ActorRef {
        if (actorType == null) {
            throw new IllegalArgumentException("actorType 不能为 null");
        }
        if (actorId == null) {
            throw new IllegalArgumentException("actorId 不能为 null");
        }
    }

    public static ActorRef user(UUID userId, String displayName) {
        return new ActorRef(ActorType.USER, userId, displayName);
    }

    public static ActorRef system(UUID systemId, String displayName) {
        return new ActorRef(ActorType.SYSTEM, systemId, displayName);
    }
}
