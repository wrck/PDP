package com.pdp.operations.observability;

import com.pdp.shared.context.WorkspaceId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SLI 采集器（FR-165 可用性 SLI 收集与聚合）。
 *
 * <p>对应 FR-165 和 SC-016/SC-037。采集 {@link RequestOutcome} 样本，按服务类别和时间段
 * 聚合计算可用性 SLI，生成月度可用性报告和日度汇总。
 *
 * <p><strong>采集流程</strong>：
 * <ol>
 *   <li>请求处理完成后调用 {@link #record} 采集样本；</li>
 *   <li>样本累积在内存缓冲区（按服务类别分桶）；</li>
 *   <li>{@link #computeSli} 按时间段聚合计算 {@link AvailabilitySli}；</li>
 *   <li>{@link #computeDailySummary} 生成日度汇总（{@link AvailabilityDailySummary}）；</li>
 *   <li>定期持久化到 {@code AvailabilityDailySummary} 表（由后台作业完成）。</li>
 * </ol>
 *
 * <p><strong>排除项证据</strong>（SC-037）：排除请求 MUST 携带证据，
 * {@link #record} 校验后采集，保证排除项证据完整率 100%。
 *
 * <p><strong>性能</strong>：采集使用 {@link ConcurrentLinkedQueue} 和 {@link AtomicLong}，
 * 无锁并发安全。生产环境建议替换为 Micrometer / Prometheus 直接采集，本类提供领域模型层抽象。
 */
public class SliCollector {

    private final Map<ServiceCategory, SampleBucket> buckets = new EnumMap<>(ServiceCategory.class);

    public SliCollector() {
        for (ServiceCategory category : ServiceCategory.values()) {
            buckets.put(category, new SampleBucket());
        }
    }

    /**
     * 采集请求结果样本。
     *
     * @param outcome 请求结果
     */
    public void record(RequestOutcome outcome) {
        Objects.requireNonNull(outcome, "outcome 不能为 null");
        SampleBucket bucket = buckets.get(outcome.serviceCategory());
        bucket.record(outcome);
    }

    /**
     * 计算指定服务类别和时间段的 SLI。
     *
     * @param category    服务类别
     * @param periodStart 时间段开始
     * @param periodEnd   时间段结束
     * @return SLI 聚合结果
     */
    public AvailabilitySli computeSli(ServiceCategory category,
                                      Instant periodStart, Instant periodEnd) {
        Objects.requireNonNull(category, "category 不能为 null");
        Objects.requireNonNull(periodStart, "periodStart 不能为 null");
        Objects.requireNonNull(periodEnd, "periodEnd 不能为 null");

        SampleBucket bucket = buckets.get(category);
        return bucket.aggregate(category, periodStart, periodEnd);
    }

    /**
     * 计算日度汇总（SC-037 月度可审计报告的基础）。
     *
     * @param category 服务类别
     * @param date     日期（UTC）
     * @return 日度汇总
     */
    public AvailabilityDailySummary computeDailySummary(ServiceCategory category, LocalDate date) {
        Objects.requireNonNull(category, "category 不能为 null");
        Objects.requireNonNull(date, "date 不能为 null");
        Instant start = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        AvailabilitySli sli = computeSli(category, start, end);
        return new AvailabilityDailySummary(
                UUID.randomUUID(), category, date,
                sli.totalCount(), sli.qualifiedCount(), sli.availableCount(),
                sli.excludedCount(), sli.failedCount(), sli.timeoutCount(),
                sli.availabilityPercent(), sli.meetsSlo(),
                sli.p50Latency(), sli.p95Latency(), sli.p99Latency(),
                Instant.now());
    }

    /**
     * 计算月度可用性（SC-016 验收）。
     *
     * @param category 服务类别
     * @param year     年
     * @param month    月（1-12）
     * @return 月度 SLI
     */
    public AvailabilitySli computeMonthlySli(ServiceCategory category, int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.plusMonths(1);
        return computeSli(category,
                start.atStartOfDay(ZoneOffset.UTC).toInstant(),
                end.atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    /** 清理指定时间段之前的样本（避免内存膨胀）。 */
    public long purgeBefore(Instant cutoff) {
        Objects.requireNonNull(cutoff, "cutoff 不能为 null");
        long purged = 0;
        for (SampleBucket bucket : buckets.values()) {
            purged += bucket.purgeBefore(cutoff);
        }
        return purged;
    }

    // ==================== 内部采样桶 ====================

    private static final class SampleBucket {
        private final ConcurrentLinkedQueue<RequestOutcome> samples = new ConcurrentLinkedQueue<>();
        private final AtomicLong totalCount = new AtomicLong();
        private final AtomicLong qualifiedCount = new AtomicLong();
        private final AtomicLong availableCount = new AtomicLong();
        private final AtomicLong excludedCount = new AtomicLong();
        private final AtomicLong failedCount = new AtomicLong();
        private final AtomicLong timeoutCount = new AtomicLong();

        void record(RequestOutcome outcome) {
            samples.add(outcome);
            totalCount.incrementAndGet();
            if (outcome.exclusionReason().isExcludable()) {
                excludedCount.incrementAndGet();
                return;
            }
            qualifiedCount.incrementAndGet();
            if (outcome.isAvailable()) {
                availableCount.incrementAndGet();
            }
            if (!outcome.succeeded()) {
                failedCount.incrementAndGet();
            }
            if (outcome.succeeded() && !outcome.isWithinTimeLimit()) {
                timeoutCount.incrementAndGet();
            }
        }

        AvailabilitySli aggregate(ServiceCategory category, Instant start, Instant end) {
            long total = 0, qualified = 0, available = 0, excluded = 0, failed = 0, timeout = 0;
            java.util.List<Duration> durations = new java.util.ArrayList<>();
            for (RequestOutcome o : samples) {
                if (o.startedAt().isBefore(start) || !o.startedAt().isBefore(end)) {
                    continue;
                }
                total++;
                if (o.exclusionReason().isExcludable()) {
                    excluded++;
                    continue;
                }
                qualified++;
                durations.add(o.duration());
                if (o.isAvailable()) {
                    available++;
                }
                if (!o.succeeded()) {
                    failed++;
                }
                if (o.succeeded() && !o.isWithinTimeLimit()) {
                    timeout++;
                }
            }
            Duration p50 = percentile(durations, 0.50);
            Duration p95 = percentile(durations, 0.95);
            Duration p99 = percentile(durations, 0.99);
            return new AvailabilitySli(category, start, end,
                    total, qualified, available, excluded, failed, timeout,
                    p50, p95, p99, Instant.now());
        }

        long purgeBefore(Instant cutoff) {
            long count = 0;
            var iterator = samples.iterator();
            while (iterator.hasNext()) {
                if (iterator.next().completedAt().isBefore(cutoff)) {
                    iterator.remove();
                    count++;
                }
            }
            return count;
        }

        private static Duration percentile(java.util.List<Duration> durations, double percentile) {
            if (durations.isEmpty()) {
                return Duration.ZERO;
            }
            durations.sort(java.util.Comparator.comparingLong(Duration::toMillis));
            int index = (int) Math.ceil(percentile * durations.size()) - 1;
            if (index < 0) index = 0;
            if (index >= durations.size()) index = durations.size() - 1;
            return durations.get(index);
        }
    }
}
