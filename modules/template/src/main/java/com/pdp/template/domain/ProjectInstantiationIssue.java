package com.pdp.template.domain;

import java.util.Objects;

/** 项目实例化预览中的稳定校验问题。 */
public record ProjectInstantiationIssue(
    String code, Severity severity, String path, String message) {
  public enum Severity {
    ERROR,
    WARNING
  }

  public ProjectInstantiationIssue {
    code = requireText(code, "校验问题代码");
    Objects.requireNonNull(severity, "校验问题级别不能为空");
    path = requireText(path, "校验问题路径");
    message = requireText(message, "校验问题说明");
  }

  private static String requireText(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + "不能为空");
    }
    return value.trim();
  }
}
