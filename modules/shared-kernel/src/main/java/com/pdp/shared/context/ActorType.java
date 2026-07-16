package com.pdp.shared.context;

/**
 * 参与者类型。支持用户、组织、角色、外部参与者和系统执行身份。
 *
 * <p>对应 {@code ActorRef.actor_type}，使用稳定键，禁止依赖枚举序号持久化。
 */
public enum ActorType {
    USER,
    ORGANIZATION,
    ROLE,
    EXTERNAL_PARTICIPANT,
    SYSTEM;

    /** 稳定键，用于持久化与跨系统交换。 */
    public String stableKey() {
        return name();
    }
}
