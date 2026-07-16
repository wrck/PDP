package com.pdp.identity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** PDP 平台账户；授权版本在停用或撤权时单调递增。 */
public record UserAccount(
    UUID id,
    String externalSubject,
    String displayName,
    String email,
    Status status,
    String locale,
    String timezone,
    Instant lastLoginAt,
    long authorizationVersion) {

  public enum Status {
    INVITED,
    ACTIVE,
    SUSPENDED,
    DISABLED
  }

  public UserAccount {
    Objects.requireNonNull(id, "用户标识不能为空");
    externalSubject = requireText(externalSubject, "外部身份 subject");
    displayName = requireText(displayName, "显示名称");
    email = requireText(email, "邮箱");
    Objects.requireNonNull(status, "用户状态不能为空");
    locale = requireText(locale, "语言");
    timezone = requireText(timezone, "时区");
    if (authorizationVersion < 0) {
      throw new IllegalArgumentException("授权版本不能小于 0");
    }
  }

  public static UserAccount invited(
      UUID id, String externalSubject, String displayName, String email) {
    return new UserAccount(
        id, externalSubject, displayName, email, Status.INVITED, "zh-CN", "Asia/Shanghai", null, 0);
  }

  public UserAccount activate(Instant loginAt) {
    if (status == Status.DISABLED) {
      throw new IllegalStateException("已停用用户不能直接启用");
    }
    return new UserAccount(
        id,
        externalSubject,
        displayName,
        email,
        Status.ACTIVE,
        locale,
        timezone,
        Objects.requireNonNull(loginAt),
        authorizationVersion);
  }

  public UserAccount synchronize(String displayName, String email, Instant loginAt) {
    if (status == Status.DISABLED) {
      throw new IllegalStateException("已停用用户不能通过身份同步重新启用");
    }
    return new UserAccount(
        id,
        externalSubject,
        displayName,
        email,
        Status.ACTIVE,
        locale,
        timezone,
        loginAt,
        authorizationVersion);
  }

  public UserAccount suspend(Instant at, String reason) {
    requireText(reason, "暂停原因");
    if (status != Status.ACTIVE) {
      throw new IllegalStateException("仅活动用户可以暂停");
    }
    return withStatus(Status.SUSPENDED, Math.addExact(authorizationVersion, 1));
  }

  public UserAccount disable(Instant at, String reason) {
    Objects.requireNonNull(at, "停用时间不能为空");
    requireText(reason, "停用原因");
    if (status == Status.DISABLED) {
      return this;
    }
    return withStatus(Status.DISABLED, Math.addExact(authorizationVersion, 1));
  }

  public UserAccount incrementAuthorizationVersion() {
    return withStatus(status, Math.addExact(authorizationVersion, 1));
  }

  private UserAccount withStatus(Status nextStatus, long version) {
    return new UserAccount(
        id,
        externalSubject,
        displayName,
        email,
        nextStatus,
        locale,
        timezone,
        lastLoginAt,
        version);
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + "不能为空");
    }
    return value;
  }
}
