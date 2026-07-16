package com.pdp.shared.error;

import java.net.URI;
import java.util.Map;
import java.util.Objects;

/** 可安全映射为对外 Problem 响应的业务异常。 */
public class PdpException extends RuntimeException {
  private final ErrorCode errorCode;
  private final URI instance;
  private final Map<String, Object> attributes;

  public PdpException(ErrorCode errorCode, String detail) {
    this(errorCode, detail, null, Map.of());
  }

  public PdpException(ErrorCode errorCode, String detail, URI instance) {
    this(errorCode, detail, instance, Map.of());
  }

  public PdpException(
      ErrorCode errorCode, String detail, URI instance, Map<String, Object> attributes) {
    super(Objects.requireNonNull(detail, "detail 不能为空"));
    this.errorCode = Objects.requireNonNull(errorCode, "errorCode 不能为空");
    this.instance = instance;
    this.attributes = Map.copyOf(attributes);
  }

  public ErrorCode errorCode() {
    return errorCode;
  }

  public URI instance() {
    return instance;
  }

  public Map<String, Object> attributes() {
    return attributes;
  }
}
