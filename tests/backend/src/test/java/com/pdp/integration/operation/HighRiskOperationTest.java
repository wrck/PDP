package com.pdp.integration.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pdp.shared.operation.CompensationPort;
import com.pdp.shared.operation.CompensationRequest;
import com.pdp.shared.operation.CompensationResult;
import com.pdp.shared.operation.HighRiskOperationCatalog;
import com.pdp.shared.operation.HighRiskOperationType;
import com.pdp.shared.operation.OperationConfirmation;
import com.pdp.shared.operation.OperationConfirmationException;
import com.pdp.shared.operation.OperationImpactPreview;
import com.pdp.shared.operation.SignedOperationConfirmationService;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class HighRiskOperationTest {
  private static final Instant PREVIEWED_AT = Instant.parse("2026-07-17T00:00:00Z");
  private static final Duration PREVIEW_TTL = Duration.ofSeconds(30);
  private static final String KEY_ID = "test-key-1";
  private static final Map<String, byte[]> KEYSET =
      Map.of(KEY_ID, "high-risk-operation-test-key".getBytes(StandardCharsets.UTF_8));

  @Test
  void 预览过期后确认令牌必须拒绝() {
    OperationConfirmation expected = confirmation("command-v1", "revision-7");
    SignedOperationConfirmationService issuer = service(PREVIEWED_AT);
    String token = issuer.issue(expected);
    OperationImpactPreview preview =
        new OperationImpactPreview(
            expected.previewId(),
            expected.operationType(),
            Map.of("projects", 1L, "activeJobs", 2L),
            List.of("切换期间写入将短暂停止"),
            "目标部署开放写入后",
            "关闭目标写入并执行受控回退",
            token,
            PREVIEWED_AT.plus(PREVIEW_TTL));
    SignedOperationConfirmationService expiredVerifier =
        service(preview.expiresAt().plusSeconds(1));

    assertThat(preview.expiresAt()).isBefore(expiredVerifierClock());
    assertThatThrownBy(() -> expiredVerifier.verify(preview.confirmationToken(), expected))
        .isInstanceOf(OperationConfirmationException.class)
        .hasMessageContaining("已过期");
  }

  @Test
  void 命令或对象版本变化后旧确认令牌必须失效() {
    OperationConfirmation previewed = confirmation("command-v1", "revision-7");
    SignedOperationConfirmationService service = service(PREVIEWED_AT);
    String token = service.issue(previewed);
    OperationConfirmation changedCommand =
        new OperationConfirmation(
            previewed.previewId(),
            previewed.operationType(),
            "command-v2",
            previewed.revisionDigest(),
            previewed.previewedAt());
    OperationConfirmation changedRevision =
        new OperationConfirmation(
            previewed.previewId(),
            previewed.operationType(),
            previewed.commandDigest(),
            "revision-8",
            previewed.previewedAt());

    assertThatThrownBy(() -> service.verify(token, changedCommand))
        .isInstanceOf(OperationConfirmationException.class)
        .hasMessageContaining("当前预览或版本不匹配");
    assertThatThrownBy(() -> service.verify(token, changedRevision))
        .isInstanceOf(OperationConfirmationException.class)
        .hasMessageContaining("当前预览或版本不匹配");
  }

  @Test
  void 未变化的预览可确认且确认令牌不能被篡改() {
    OperationConfirmation expected = confirmation("command-v1", "revision-7");
    SignedOperationConfirmationService service = service(PREVIEWED_AT);
    String token = service.issue(expected);

    assertThat(service.verify(token, expected)).isEqualTo(expected);

    char replacement = token.charAt(token.length() - 1) == 'A' ? 'B' : 'A';
    String tampered = token.substring(0, token.length() - 1) + replacement;
    assertThatThrownBy(() -> service.verify(tampered, expected))
        .isInstanceOf(OperationConfirmationException.class)
        .hasMessageContaining("签名无效");
  }

  @Test
  void 高风险操作必须声明并执行可审计补偿契约() {
    var definition =
        HighRiskOperationCatalog.p1Defaults().require(HighRiskOperationType.DATABASE_SWITCH);
    AtomicReference<CompensationRequest> observed = new AtomicReference<>();
    Instant completedAt = PREVIEWED_AT.plusSeconds(45);
    CompensationPort compensation =
        request -> {
          observed.set(request);
          return new CompensationResult(
              request.operationId(),
              CompensationResult.Status.COMPLETED,
              request.evidenceReference(),
              completedAt);
        };
    CompensationRequest request =
        new CompensationRequest(
            UUID.randomUUID(),
            HighRiskOperationType.DATABASE_SWITCH,
            "目标部署健康检查失败，执行受控回退",
            "evidence://database-switch/rollback-001");

    CompensationResult result = compensation.compensate(request);

    assertThat(definition.compensationRequired()).isTrue();
    assertThat(definition.compensationSummary()).contains("受控回退", "审计证据");
    assertThat(observed.get()).isEqualTo(request);
    assertThat(result.status()).isEqualTo(CompensationResult.Status.COMPLETED);
    assertThat(result.evidenceReference()).isEqualTo(request.evidenceReference());
    assertThat(result.completedAt()).isEqualTo(completedAt);
  }

  private static OperationConfirmation confirmation(
      String commandDigest, String revisionDigest) {
    return new OperationConfirmation(
        UUID.randomUUID(),
        HighRiskOperationType.DATABASE_SWITCH,
        commandDigest,
        revisionDigest,
        PREVIEWED_AT);
  }

  private static SignedOperationConfirmationService service(Instant now) {
    return new SignedOperationConfirmationService(
        KEY_ID,
        KEYSET,
        PREVIEW_TTL,
        Clock.fixed(now, ZoneOffset.UTC));
  }

  private static Instant expiredVerifierClock() {
    return PREVIEWED_AT.plus(PREVIEW_TTL).plusSeconds(1);
  }
}
