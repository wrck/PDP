package com.pdp.mysql.config;

import java.util.Optional;
import java.util.UUID;

/**
 * 将应用层审计身份适配为 MyBatis 自动填充值。
 */
@FunctionalInterface
public interface AuditActorProvider {

    Optional<UUID> currentActorId();
}
