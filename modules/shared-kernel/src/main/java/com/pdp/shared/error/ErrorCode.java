package com.pdp.shared.error;

import java.net.URI;

/** 跨模块稳定错误码。 */
public enum ErrorCode {
  INVALID_REQUEST(400, "请求无效", "invalid-request"),
  AUTHENTICATION_REQUIRED(401, "需要身份认证", "authentication-required"),
  ACCESS_DENIED(403, "访问被拒绝", "access-denied"),
  CURSOR_INVALID(400, "分页游标无效", "cursor-invalid"),
  REVISION_CONFLICT(409, "对象版本冲突", "revision-conflict"),
  OPERATION_NOT_CERTIFIED(422, "操作未认证", "operation-not-certified"),
  OPERATION_CONFIRMATION_INVALID(409, "操作确认无效", "operation-confirmation-invalid"),
  INTERNAL_ERROR(500, "系统内部错误", "internal-error");

  private final int status;
  private final String title;
  private final URI type;

  ErrorCode(int status, String title, String type) {
    this.status = status;
    this.title = title;
    this.type = URI.create("urn:pdp:problem:" + type);
  }

  public int status() {
    return status;
  }

  public String title() {
    return title;
  }

  public URI type() {
    return type;
  }
}
