package com.pdp.operations.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 按 FR-165 采集可用性原始 SLI。服务目录键和类别是低基数标签，请求、操作者和工作空间标识不得作为指标标签。
 */
public final class AvailabilitySliCollector {

  private static final Pattern SERVICE_KEY = Pattern.compile("[a-z0-9][a-z0-9_.-]{1,79}");
  private static final String REQUEST_METRIC = "pdp.availability.requests";
  private static final String DURATION_METRIC = "pdp.availability.request.duration";

  private final MeterRegistry meterRegistry;

  public AvailabilitySliCollector(MeterRegistry meterRegistry) {
    this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry 不能为空");
  }

  public AvailabilityDecision record(AvailabilitySample sample) {
    Objects.requireNonNull(sample, "sample 不能为空");
    List<Tag> baseTags =
        List.of(
            Tag.of("service", sample.profile().serviceKey()),
            Tag.of("service_class", sample.profile().serviceClass().metricValue()));

    Timer.builder(DURATION_METRIC)
        .tags(baseTags)
        .publishPercentileHistogram()
        .register(meterRegistry)
        .record(sample.duration());

    if (sample.outcome() == RequestOutcome.FAILURE
        && sample.exclusionReason() != ExclusionReason.NONE) {
      meterRegistry
          .counter(
              REQUEST_METRIC,
              append(
                  baseTags,
                  Tag.of("result", "excluded"),
                  Tag.of("reason", sample.exclusionReason().metricValue())))
          .increment();
      return new AvailabilityDecision(false, false, "请求符合 FR-165 允许的排除条件");
    }

    meterRegistry
        .counter(
            REQUEST_METRIC,
            append(baseTags, Tag.of("result", "eligible"), Tag.of("reason", "none")))
        .increment();

    boolean successful =
        sample.outcome() == RequestOutcome.SUCCESS
            && sample.duration().compareTo(sample.profile().interactionLimit()) <= 0;
    String result =
        successful
            ? "successful"
            : sample.outcome() == RequestOutcome.SUCCESS ? "slow" : "failed";
    meterRegistry
        .counter(
            REQUEST_METRIC,
            append(baseTags, Tag.of("result", result), Tag.of("reason", "none")))
        .increment();
    return new AvailabilityDecision(true, successful, successful ? "合格成功请求" : "合格失败请求");
  }

  private static List<Tag> append(List<Tag> base, Tag... added) {
    java.util.ArrayList<Tag> tags = new java.util.ArrayList<>(base);
    tags.addAll(List.of(added));
    return List.copyOf(tags);
  }

  public enum ServiceClass {
    CORE_BUSINESS("core_business", 0.9995d),
    ONLINE_MANAGEMENT("online_management", 0.999d);

    private final String metricValue;
    private final double monthlyTarget;

    ServiceClass(String metricValue, double monthlyTarget) {
      this.metricValue = metricValue;
      this.monthlyTarget = monthlyTarget;
    }

    public String metricValue() {
      return metricValue;
    }

    public double monthlyTarget() {
      return monthlyTarget;
    }
  }

  public enum RequestOutcome {
    SUCCESS,
    FAILURE
  }

  public enum ExclusionReason {
    NONE("none"),
    CLIENT_CANCELLED("client_cancelled"),
    CUSTOMER_NETWORK_OR_IDENTITY("customer_network_or_identity");

    private final String metricValue;

    ExclusionReason(String metricValue) {
      this.metricValue = metricValue;
    }

    public String metricValue() {
      return metricValue;
    }
  }

  public record ServiceLevelProfile(
      String serviceKey, ServiceClass serviceClass, Duration interactionLimit) {
    public ServiceLevelProfile {
      if (serviceKey == null || !SERVICE_KEY.matcher(serviceKey).matches()) {
        throw new IllegalArgumentException("serviceKey 必须是稳定的低基数服务目录键");
      }
      Objects.requireNonNull(serviceClass, "serviceClass 不能为空");
      Objects.requireNonNull(interactionLimit, "interactionLimit 不能为空");
      if (interactionLimit.isZero() || interactionLimit.isNegative()) {
        throw new IllegalArgumentException("interactionLimit 必须大于 0");
      }
    }
  }

  public record AvailabilitySample(
      ServiceLevelProfile profile,
      RequestOutcome outcome,
      Duration duration,
      ExclusionReason exclusionReason,
      Instant occurredAt) {
    public AvailabilitySample {
      Objects.requireNonNull(profile, "profile 不能为空");
      Objects.requireNonNull(outcome, "outcome 不能为空");
      Objects.requireNonNull(duration, "duration 不能为空");
      Objects.requireNonNull(exclusionReason, "exclusionReason 不能为空");
      Objects.requireNonNull(occurredAt, "occurredAt 不能为空");
      if (duration.isNegative()) {
        throw new IllegalArgumentException("duration 不能为负数");
      }
      if (outcome == RequestOutcome.SUCCESS && exclusionReason != ExclusionReason.NONE) {
        throw new IllegalArgumentException("成功请求不能标记排除原因");
      }
    }
  }

  public record AvailabilityDecision(boolean eligible, boolean successful, String reason) {}
}
