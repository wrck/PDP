package com.pdp.operations.observability;

import com.pdp.operations.observability.AvailabilitySliCollector.AvailabilitySample;
import com.pdp.operations.observability.AvailabilitySliCollector.ExclusionReason;
import com.pdp.operations.observability.AvailabilitySliCollector.RequestOutcome;
import com.pdp.operations.observability.AvailabilitySliCollector.ServiceLevelProfile;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 统一关联结构化日志、Micrometer 指标与 Observation/链路追踪。 */
public final class OperationTelemetry {

  private static final Logger LOGGER = LoggerFactory.getLogger(OperationTelemetry.class);

  private final ObservationRegistry observationRegistry;
  private final AvailabilitySliCollector availabilitySliCollector;
  private final Clock clock;

  public OperationTelemetry(
      ObservationRegistry observationRegistry,
      AvailabilitySliCollector availabilitySliCollector,
      Clock clock) {
    this.observationRegistry =
        Objects.requireNonNull(observationRegistry, "observationRegistry 不能为空");
    this.availabilitySliCollector =
        Objects.requireNonNull(availabilitySliCollector, "availabilitySliCollector 不能为空");
    this.clock = Objects.requireNonNull(clock, "clock 不能为空");
  }

  public <T> T observe(OperationRequest request, CheckedSupplier<T> action) throws Exception {
    Objects.requireNonNull(request, "request 不能为空");
    Objects.requireNonNull(action, "action 不能为空");

    Observation observation =
        Observation.createNotStarted("pdp.operation", observationRegistry)
            .lowCardinalityKeyValue(KeyValue.of("pdp.operation", request.operation()))
            .lowCardinalityKeyValue(
                KeyValue.of("pdp.service", request.profile().serviceKey()))
            .highCardinalityKeyValue(KeyValue.of("pdp.request_id", request.requestId()))
            .highCardinalityKeyValue(KeyValue.of("pdp.trace_id", request.traceId()))
            .start();
    long startedAt = System.nanoTime();
    Instant occurredAt = clock.instant();
    try (Observation.Scope ignored = observation.openScope()) {
      T result = action.get();
      Duration duration = Duration.ofNanos(System.nanoTime() - startedAt);
      availabilitySliCollector.record(
          new AvailabilitySample(
              request.profile(),
              RequestOutcome.SUCCESS,
              duration,
              ExclusionReason.NONE,
              occurredAt));
      LOGGER
          .atInfo()
          .addKeyValue("event", "operation.completed")
          .addKeyValue("operation", request.operation())
          .addKeyValue("service", request.profile().serviceKey())
          .addKeyValue("requestId", request.requestId())
          .addKeyValue("traceId", request.traceId())
          .addKeyValue("durationMs", duration.toMillis())
          .log("平台操作完成");
      return result;
    } catch (Exception exception) {
      observation.error(exception);
      Duration duration = Duration.ofNanos(System.nanoTime() - startedAt);
      availabilitySliCollector.record(
          new AvailabilitySample(
              request.profile(),
              RequestOutcome.FAILURE,
              duration,
              request.exclusionOnFailure(),
              occurredAt));
      LOGGER
          .atError()
          .addKeyValue("event", "operation.failed")
          .addKeyValue("operation", request.operation())
          .addKeyValue("service", request.profile().serviceKey())
          .addKeyValue("requestId", request.requestId())
          .addKeyValue("traceId", request.traceId())
          .addKeyValue("durationMs", duration.toMillis())
          .addKeyValue("errorType", exception.getClass().getSimpleName())
          .setCause(exception)
          .log("平台操作失败");
      throw exception;
    } finally {
      observation.stop();
    }
  }

  public record OperationRequest(
      ServiceLevelProfile profile,
      String operation,
      String requestId,
      String traceId,
      ExclusionReason exclusionOnFailure) {
    public OperationRequest {
      Objects.requireNonNull(profile, "profile 不能为空");
      operation = requireText(operation, "operation");
      requestId = requireText(requestId, "requestId");
      traceId = requireText(traceId, "traceId");
      Objects.requireNonNull(exclusionOnFailure, "exclusionOnFailure 不能为空");
    }

    public static OperationRequest eligible(
        ServiceLevelProfile profile, String operation, String requestId, String traceId) {
      return new OperationRequest(
          profile, operation, requestId, traceId, ExclusionReason.NONE);
    }

    public static OperationRequest eligible(
        ServiceLevelProfile profile, String operation, UUID requestId, UUID traceId) {
      return eligible(profile, operation, requestId.toString(), traceId.toString());
    }
  }

  @FunctionalInterface
  public interface CheckedSupplier<T> {
    T get() throws Exception;
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " 不能为空");
    }
    return value;
  }
}
