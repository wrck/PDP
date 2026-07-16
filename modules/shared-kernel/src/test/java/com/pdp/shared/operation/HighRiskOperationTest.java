package com.pdp.shared.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HighRiskOperationTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-17T08:00:00Z"), ZoneOffset.UTC);

  @Test
  void P1应注册数据库切换并认证Mysql84同源组合() {
    var catalog = HighRiskOperationCatalog.p1Defaults();
    var policy = DatabaseSwitchCertificationPolicy.p1Mysql84();

    assertThat(catalog.require(HighRiskOperationType.DATABASE_SWITCH).compensationRequired())
        .isTrue();
    assertThat(policy.evaluate("MYSQL", "8.4.3", "MYSQL", "8.4.4").enabled()).isTrue();
  }

  @Test
  void 应为未认证产品版本和组合返回稳定禁用原因() {
    var policy = DatabaseSwitchCertificationPolicy.p1Mysql84();

    assertThat(policy.evaluate("POSTGRESQL", "17", "MYSQL", "8.4").reasonCode())
        .isEqualTo("SOURCE_PRODUCT_NOT_CERTIFIED");
    assertThat(policy.evaluate("MYSQL", "8.0", "MYSQL", "8.4").reasonCode())
        .isEqualTo("SOURCE_VERSION_NOT_CERTIFIED");
    assertThat(policy.evaluate("MYSQL", "8.4", "POSTGRESQL", "17").reasonCode())
        .isEqualTo("TARGET_PRODUCT_NOT_CERTIFIED");
  }

  @Test
  void 确认令牌必须绑定预览版本命令摘要和有效期() {
    var service =
        new SignedOperationConfirmationService(
            "operation-2026-01",
            Map.of(
                "operation-2026-01",
                "a-32-byte-operation-secret-key".getBytes(StandardCharsets.UTF_8)),
            Duration.ofMinutes(10),
            CLOCK);
    var previewId = UUID.randomUUID();
    var confirmation =
        new OperationConfirmation(
            previewId,
            HighRiskOperationType.DATABASE_SWITCH,
            "command-sha256",
            "revision-sha256",
            CLOCK.instant());
    var token = service.issue(confirmation);

    assertThat(service.verify(token, confirmation)).isEqualTo(confirmation);
    assertThatThrownBy(
            () ->
                service.verify(
                    token,
                    new OperationConfirmation(
                        previewId,
                        HighRiskOperationType.DATABASE_SWITCH,
                        "changed-command",
                        "revision-sha256",
                        CLOCK.instant())))
        .isInstanceOf(OperationConfirmationException.class)
        .hasMessageContaining("不匹配");
  }
}
