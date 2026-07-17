package com.pdp.persistence.provider;

/**
 * 持久化适配器契约。
 *
 * <p>由具体数据库适配器（P1 为 MySQL 8.4）实现。通过 {@link PersistenceProviderRegistry}
 * 选择唯一启用的认证适配器。P1 使用模拟适配器验证注册、能力拒绝和边界稳定性，
 * 但不提供第二种生产数据库实现。
 */
public interface PersistenceProvider {

    /** 数据库产品名，如 "MySQL"。 */
    String databaseProduct();

    /** 适配器声明的认证能力画像。 */
    DatabaseCapabilityProfile capabilityProfile();

    /** 判断本适配器是否满足所需能力画像。 */
    boolean supports(DatabaseCapabilityProfile required);
}
