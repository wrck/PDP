package com.pdp.identity.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 用户账户领域模型。
 *
 * <p>对应 ISO 21500 系列术语治理中的"参与者"基线，PDP 扩展为可分配、可跟踪的执行身份。
 * 账户跨工作空间共享，但权限与数据范围按工作空间隔离。
 */
public record UserAccount(
        UUID id,
        String username,
        String displayName,
        String email,
        UserStatus status,
        Instant createdAt,
        Instant updatedAt,
        int revision) {

    public UserAccount {
        if (id == null) {
            throw new IllegalArgumentException("id 不能为 null");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username 不能为空");
        }
        if (status == null) {
            throw new IllegalArgumentException("status 不能为 null");
        }
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }
}
