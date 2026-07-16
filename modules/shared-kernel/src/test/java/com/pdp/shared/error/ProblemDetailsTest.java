package com.pdp.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;

class ProblemDetailsTest {

  @Test
  void 应生成稳定的Problem响应并保留链路标识() {
    var exception =
        new PdpException(
            ErrorCode.REVISION_CONFLICT,
            "对象已被其他请求更新",
            URI.create("/api/v1/projects/42"));

    var problem = ProblemDetails.from(exception, "trace-20260717");

    assertThat(problem.contentType()).isEqualTo("application/problem+json");
    assertThat(problem.type()).isEqualTo(URI.create("urn:pdp:problem:revision-conflict"));
    assertThat(problem.status()).isEqualTo(409);
    assertThat(problem.code()).isEqualTo("REVISION_CONFLICT");
    assertThat(problem.traceId()).isEqualTo("trace-20260717");
    assertThat(problem.instance()).isEqualTo(URI.create("/api/v1/projects/42"));
  }
}
