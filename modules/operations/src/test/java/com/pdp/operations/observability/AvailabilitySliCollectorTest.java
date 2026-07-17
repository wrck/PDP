package com.pdp.operations.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pdp.operations.observability.AvailabilitySliCollector.AvailabilitySample;
import com.pdp.operations.observability.AvailabilitySliCollector.ExclusionReason;
import com.pdp.operations.observability.AvailabilitySliCollector.RequestOutcome;
import com.pdp.operations.observability.AvailabilitySliCollector.ServiceClass;
import com.pdp.operations.observability.AvailabilitySliCollector.ServiceLevelProfile;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AvailabilitySliCollectorTest {

  @Test
  void shouldApplyFr165EligibilityAndInteractionLimit() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    AvailabilitySliCollector collector = new AvailabilitySliCollector(registry);
    ServiceLevelProfile profile =
        new ServiceLevelProfile("project.command", ServiceClass.CORE_BUSINESS, Duration.ofSeconds(2));

    var success =
        collector.record(
            new AvailabilitySample(
                profile,
                RequestOutcome.SUCCESS,
                Duration.ofMillis(500),
                ExclusionReason.NONE,
                Instant.EPOCH));
    var slow =
        collector.record(
            new AvailabilitySample(
                profile,
                RequestOutcome.SUCCESS,
                Duration.ofSeconds(3),
                ExclusionReason.NONE,
                Instant.EPOCH));
    var excluded =
        collector.record(
            new AvailabilitySample(
                profile,
                RequestOutcome.FAILURE,
                Duration.ofMillis(20),
                ExclusionReason.CLIENT_CANCELLED,
                Instant.EPOCH));

    assertThat(success.eligible()).isTrue();
    assertThat(success.successful()).isTrue();
    assertThat(slow.eligible()).isTrue();
    assertThat(slow.successful()).isFalse();
    assertThat(excluded.eligible()).isFalse();
    assertThat(
            registry
                .get("pdp.availability.requests")
                .tag("service", "project.command")
                .tag("result", "eligible")
                .counter()
                .count())
        .isEqualTo(2d);
    assertThat(
            registry
                .get("pdp.availability.requests")
                .tag("service", "project.command")
                .tag("result", "successful")
                .counter()
                .count())
        .isEqualTo(1d);
  }

  @Test
  void shouldRejectExclusionOnSuccessfulRequest() {
    ServiceLevelProfile profile =
        new ServiceLevelProfile(
            "workspace.query", ServiceClass.ONLINE_MANAGEMENT, Duration.ofSeconds(3));

    assertThatThrownBy(
            () ->
                new AvailabilitySample(
                    profile,
                    RequestOutcome.SUCCESS,
                    Duration.ZERO,
                    ExclusionReason.CUSTOMER_NETWORK_OR_IDENTITY,
                    Instant.EPOCH))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
