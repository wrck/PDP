package com.pdp.shared.error;

import java.net.URI;
import java.util.Map;

/** RFC 9457 风格的统一错误响应模型。 */
public record ProblemDetails(
    String contentType,
    URI type,
    String title,
    int status,
    String detail,
    String code,
    URI instance,
    String traceId,
    Map<String, Object> attributes) {

  public ProblemDetails {
    contentType = "application/problem+json";
    attributes = Map.copyOf(attributes);
  }

  public static ProblemDetails from(PdpException exception, String traceId) {
    var code = exception.errorCode();
    return new ProblemDetails(
        "application/problem+json",
        code.type(),
        code.title(),
        code.status(),
        exception.getMessage(),
        code.name(),
        exception.instance(),
        traceId,
        exception.attributes());
  }
}
