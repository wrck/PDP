package com.pdp.operations.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pdp.operations.observability.AvailabilitySliCollector.ServiceClass;
import com.pdp.operations.observability.AvailabilitySliCollector.ServiceLevelProfile;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class OperationTelemetryTest {

  @Test
  void shouldCorrelateObservationAndAvailabilityMetric() throws Exception {
    SimpleMeterRegistry meters = new SimpleMeterRegistry();
    ObservationRegistry observations = ObservationRegistry.create();
    AtomicInteger stopped = new AtomicInteger();
    observations
        .observationConfig()
        .observationHandler(
            new ObservationHandler<Observation.Context>() {
              @Override
              public boolean supportsContext(Observation.Context context) {
                return true;
              }

              @Override
              public void onStop(Observation.Context context) {
                stopped.incrementAndGet();
              }
            });
    OperationTelemetry telemetry =
        new OperationTelemetry(
            observations,
            new AvailabilitySliCollector(meters),
            Clock.fixed(Instant.parse("2026-07-17T00:00:00Z"), ZoneOffset.UTC));
    var profile =
        new ServiceLevelProfile("task.command", ServiceClass.CORE_BUSINESS, Duration.ofSeconds(2));

    String result =
        telemetry.observe(
            OperationTelemetry.OperationRequest.eligible(profile, "task.complete", "req-1", "tr-1"),
            () -> "ok");

    assertThat(result).isEqualTo("ok");
    assertThat(stopped).hasValue(1);
    assertThat(
            meters
                .get("pdp.availability.requests")
                .tag("service", "task.command")
                .tag("result", "eligible")
                .counter()
                .count())
        .isEqualTo(1d);
  }

  @Test
  void shouldRecordInternalFailureAsEligibleFailure() {
    SimpleMeterRegistry meters = new SimpleMeterRegistry();
    OperationTelemetry telemetry =
        new OperationTelemetry(
            ObservationRegistry.create(),
            new AvailabilitySliCollector(meters),
            Clock.systemUTC());
    var profile =
        new ServiceLevelProfile("approval.command", ServiceClass.CORE_BUSINESS, Duration.ofSeconds(2));

    assertThatThrownBy(
            () ->
                telemetry.observe(
                    OperationTelemetry.OperationRequest.eligible(
                        profile, "approval.decide", "req-2", "tr-2"),
                    () -> {
                      throw new IllegalStateException("database unavailable");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(
            meters
                .get("pdp.availability.requests")
                .tag("service", "approval.command")
                .tag("result", "failed")
                .counter()
                .count())
        .isEqualTo(1d);
  }
}
