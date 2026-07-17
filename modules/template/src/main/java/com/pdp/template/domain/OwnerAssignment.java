package com.pdp.template.domain;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** 将模板负责人规则绑定到当前工作空间内的主体。 */
public record OwnerAssignment(String ruleKey, UUID principalId) {
  private static final Pattern KEY_PATTERN = Pattern.compile("[a-z][a-z0-9._-]{1,99}");

  public OwnerAssignment {
    Objects.requireNonNull(principalId, "负责人主体不能为空");
    if (ruleKey == null || !KEY_PATTERN.matcher(ruleKey).matches()) {
      throw new IllegalArgumentException("负责人规则键格式非法");
    }
  }
}
