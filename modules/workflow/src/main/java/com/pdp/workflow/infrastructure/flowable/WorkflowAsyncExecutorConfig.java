package com.pdp.workflow.infrastructure.flowable;

import com.pdp.shared.context.WorkspaceId;
import com.pdp.workflow.domain.WorkflowIncidentRecord;
import com.pdp.workflow.domain.WorkflowIncidentRepository;
import com.pdp.workflow.domain.WorkflowIncidentStatus;
import com.pdp.workflow.domain.WorkflowInstanceRefRecord;
import com.pdp.workflow.domain.WorkflowInstanceRefRepository;
import com.pdp.workflow.model.WorkflowInstanceId;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.flowable.common.spring.EngineConfigurationConfigurer;
import org.flowable.engine.delegate.event.FlowableEngineEntityEvent;
import org.flowable.engine.delegate.event.FlowableEngineEventType;
import org.flowable.engine.delegate.event.FlowableEvent;
import org.flowable.engine.delegate.event.FlowableEventListener;
import org.flowable.engine.runtime.Incident;
import org.flowable.engine.spring.SpringProcessEngineConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Flowable 异步执行器配置（T085）。
 *
 * <p>落实 ADR-0005 § 8 与 persistence-design.md § 2-3 关于异步执行器平台化的要求：
 * <ul>
 *   <li><strong>独立线程池、队列与连接预算</strong>：通过 {@link EngineConfigurationConfigurer}
 *       与 {@code application-workflow.yml} 双重锁定
 *       {@code async-executor-core-pool-size=4}、{@code async-executor-max-pool-size=8}、
 *       {@code async-executor-keep-alive-seconds=60}、{@code async-executor-thread-pool-queue-size=2000}。
 *       线程池上限 MUST ≤ {@code workflowEngine.hikari.maximum-pool-size=10}，否则超额线程会因
 *       获取不到连接而排队，触发 acquire timeout。连接预算计入“所有副本池上限之和不超过
 *       数据库可用连接 70%”的总预算。</li>
 *   <li><strong>重试策略</strong>：{@code async-executor-number-of-retries=3}（含原始执行），
 *       失败后等待 {@code async-failed-job-wait-time=10} 秒进入下一次重试，按
 *       {@code async-executor-reset-expired-job-wait-time=1000} 毫秒重置过期作业。
 *       重试耗尽由引擎自动生成可检索 incident/dead-letter。</li>
 *   <li><strong>死信与 incident 同步</strong>：通过 {@link #asyncExecutorIncidentListener}
 *       监听 {@link FlowableEngineEventType#JOB_EXECUTION_FAILURE} 与
 *       {@link FlowableEngineEventType#JOB_RETRIES_DECREMENTED}，将 incident 投影到
 *       {@link WorkflowIncidentRepository}，便于运维检索、安全重放与人工补偿。
 *       <strong>MUST NOT</strong> 产生重复审批结论或业务状态变化（ADR-0005 § 7、§ 8）。</li>
 *   <li><strong>指标</strong>：通过 {@link MeterRegistry} 暴露 {@code flowable.async.executor.*}
 *       度量，包括线程池活跃线程数、队列积压、活动任务数、incident 计数与重试耗尽计数，
 *       供 Prometheus 采集与 Grafana 可视化。</li>
 *   <li><strong>告警</strong>：重试耗尽（incident 类型为 {@code deadletter} 或
 *       {@code failedJob} 且重试次数为 0）时通过 {@link ApplicationEventPublisher} 发布
 *       {@link WorkflowAsyncExecutorAlert}，由平台告警通道订阅并触发人工补偿流程。</li>
 * </ul>
 *
 * <p><strong>线程池生命周期</strong>：不替换 Flowable 内置 {@link org.flowable.engine.impl.asyncexecutor.DefaultAsyncJobExecutor}，
 * 避免破坏引擎作业获取、消息队列模式与重试内部状态机；仅在 configurer 中显式设置所有参数，
 * 与 YAML 双重锁定，保证生产配置不可通过误配切换为 Flowable 默认值（core=2、max=10、queue=2000）。
 *
 * <p><strong>事务边界</strong>：incident 同步监听器在 Flowable 引擎事务内执行（事件由
 * {@link SpringProcessEngineConfiguration#getEventListeners()} 在事务提交前触发）；
 * PDP 侧 {@link WorkflowIncidentRecord} 持久化使用 {@code workflowTransactionManager}，
 * 与引擎处于同一 workflowEngine 数据源，避免跨库事务。任何重试不得生成重复审批动作或业务结果。
 */
@Configuration
public class WorkflowAsyncExecutorConfig {

    private static final Logger LOG = LoggerFactory.getLogger(WorkflowAsyncExecutorConfig.class);

    /**
     * Flowable Process Engine 异步执行器配置器。
     *
     * <p>与 {@code application-workflow.yml} 中 {@code flowable.process.*} 参数双重锁定：
     * 即使 YAML 被误删或环境覆盖，本配置器仍保证生产参数不可降级为 Flowable 默认值。
     *
     * <p>配置项：
     * <ul>
     *   <li>{@code corePoolSize=4}：核心线程数，与 {@code workflowEngine.hikari.maximum-pool-size=10}
     *       留出 6 个连接给人工任务办理与流程推进同步调用；</li>
     *   <li>{@code maxPoolSize=8}：上限 ≤ HikariCP 上限 10，避免超额线程获取不到连接；</li>
     *   <li>{@code keepAliveTimeMillis=60000}：空闲线程保活 60 秒；</li>
     *   <li>{@code threadPoolQueueSize=2000}：任务队列容量，超出后由 Flowable 内部拒绝策略处理；</li>
     *   <li>{@code numberOfRetries=3}：含原始执行，耗尽后形成 incident/dead-letter；</li>
     *   <li>{@code asyncFailedJobWaitTime=10} 秒：失败后等待 10 秒再重试，避免雪崩；</li>
     *   <li>{@code resetExpiredJobWaitTimeMillis=1000}：过期作业重置等待 1 秒；</li>
     *   <li>{@code defaultTimerJobAcquireWaitTimeMillis=10000}：定时器作业获取间隔；</li>
     *   <li>{@code defaultAsyncJobAcquireWaitTimeMillis=10000}：异步作业获取间隔。</li>
     * </ul>
     *
     * @return SpringProcessEngineConfiguration 的 EngineConfigurationConfigurer
     */
    @Bean
    public EngineConfigurationConfigurer<SpringProcessEngineConfiguration>
            workflowAsyncExecutorConfigurer() {
        return config -> {
            // 1. 线程池核心大小（与 YAML 双重锁定）
            config.setAsyncExecutorCorePoolSize(4);
            // 2. 线程池上限（≤ HikariCP max-pool-size=10，留 2 个连接给同步调用）
            config.setAsyncExecutorMaxPoolSize(8);
            // 3. 空闲线程保活时长（毫秒）
            config.setAsyncExecutorKeepAliveTime(60_000L);
            // 4. 任务队列容量：超出后由 Flowable 内部拒绝策略处理（记录死信/incident）
            config.setAsyncExecutorThreadPoolQueueSize(2000);
            // 5. 异步任务重试次数（含原始执行）：耗尽后形成 incident/dead-letter
            config.setAsyncExecutorNumberOfRetries(3);
            // 6. 失败任务重试等待时间（秒）：避免雪崩式重试压垮依赖
            config.setAsyncFailedJobWaitTime(10);
            // 7. 过期作业重置等待时间（毫秒）：长时间未 ACK 的作业被重新调度
            config.setAsyncExecutorResetExpiredJobWaitTime(1_000L);
            // 8. 定时器作业获取间隔（毫秒）
            config.setAsyncExecutorDefaultTimerJobAcquireWaitTime(10_000L);
            // 9. 异步作业获取间隔（毫秒）
            config.setAsyncExecutorDefaultAsyncJobAcquireWaitTime(10_000L);
            // 10. 确保异步执行器激活（与 YAML async-executor-activate: true 双重锁定）
            config.setAsyncExecutorActivate(true);

            LOG.info("Flowable 异步执行器配置已锁定：corePool=4, maxPool=8, keepAliveMs=60000, "
                    + "queueSize=2000, retries=3, failedJobWaitSec=10");
        };
    }

    /**
     * 异步执行器 incident 事件监听器。
     *
     * <p>监听 Flowable 引擎 incident 相关事件，将引擎 incident 投影到 PDP
     * {@link WorkflowIncidentRepository}，便于运维检索、安全重放与人工补偿。
     *
     * <p><strong>监听事件</strong>：
     * <ul>
     *   <li>{@link FlowableEngineEventType#JOB_EXECUTION_FAILURE}：作业执行失败，记录首次异常；</li>
     *   <li>{@link FlowableEngineEventType#JOB_RETRIES_DECREMENTED}：重试次数递减，更新重试信息；</li>
     *   <li>{@link FlowableEngineEventType#INCIDENT_CREATED}：incident 创建（重试耗尽或不可重试），登记死信。</li>
     * </ul>
     *
     * <p><strong>幂等保证</strong>：通过 {@code engineJobId} 查询现有 incident 记录，
     * 存在则更新重试信息（乐观锁），不存在则插入新记录。同一引擎作业的多次失败事件
     * 不会形成重复 PDP incident 记录。
     *
     * <p><strong>告警触发</strong>：当 incident 状态进入 {@link WorkflowIncidentStatus#DEAD_LETTER}
     * 或 {@link WorkflowIncidentStatus#MANUAL_ACTION} 时，发布 {@link WorkflowAsyncExecutorAlert}
     * 事件供平台告警通道订阅。
     *
     * @param incidentRepository PDP incident 仓储
     * @param instanceRepository 实例引用仓储（用于解析 workspace 边界）
     * @param publisher          Spring 事件发布器（发布告警事件）
     * @param clock              时钟（测试可注入）
     * @return Flowable 事件监听器
     */
    @Bean("workflowAsyncExecutorIncidentListener")
    public FlowableEventListener asyncExecutorIncidentListener(
            WorkflowIncidentRepository incidentRepository,
            WorkflowInstanceRefRepository instanceRepository,
            ApplicationEventPublisher publisher,
            Clock clock) {
        return new AsyncExecutorIncidentListener(
                incidentRepository, instanceRepository, publisher, clock);
    }

    /**
     * 异步执行器指标注册器。
     *
     * <p>在 Spring 容器初始化完成后，通过 {@link MeterRegistry} 注册以下度量：
     * <ul>
     *   <li>{@code flowable.async.executor.incident.total}（Counter，按 type/status 标签）：
     *       incident 累计计数；</li>
     *   <li>{@code flowable.async.executor.deadletter.total}（Counter）：死信累计计数；</li>
     *   <li>{@code flowable.async.executor.alert.total}（Counter，按 severity 标签）：
     *       告警累计计数。</li>
     * </ul>
     *
     * <p>线程池与队列指标通过 Flowable 内置 micrometer 集成暴露，本注册器仅补充 PDP 侧
     * 业务级度量。所有度量命名遵循 Prometheus 命名规范，使用下划线分隔。
     *
     * @param meterRegistry micrometer 注册中心
     * @param incidentCounters incident 计数器
     * @return 异步执行器指标注册器
     */
    @Bean
    public AsyncExecutorMetricsInitializer workflowAsyncExecutorMetricsInitializer(
            MeterRegistry meterRegistry,
            AsyncExecutorIncidentCounters incidentCounters) {
        return new AsyncExecutorMetricsInitializer(meterRegistry, incidentCounters);
    }

    /**
     * 异步执行器 incident 计数器（线程安全）。
     *
     * <p>由 {@link AsyncExecutorIncidentListener} 递增，由
     * {@link AsyncExecutorMetricsInitializer} 暴露为 Gauge。本计数器为进程内易失状态，
     * 重启后归零；持久化计数以 {@link WorkflowIncidentRepository} 为准。
     *
     * @return incident 计数器 Bean
     */
    @Bean
    public AsyncExecutorIncidentCounters workflowAsyncExecutorIncidentCounters() {
        return new AsyncExecutorIncidentCounters();
    }

    // ====================================================================
    // 内部组件：incident 监听器、指标注册器、计数器、告警事件
    // ====================================================================

    /**
     * 异步执行器 incident 监听器实现。
     *
     * <p>本类为 Flowable 引擎事件监听器，监听作业失败、重试递减与 incident 创建事件，
     * 同步到 PDP {@link WorkflowIncidentRepository}。
     *
     * <p><strong>事务边界</strong>：监听器在 Flowable 引擎事务内执行；PDP 侧
     * {@link WorkflowIncidentRecord} 持久化使用 {@code workflowTransactionManager}，
     * 与引擎处于同一 workflowEngine 数据源，无跨库事务。如 PDP 侧持久化失败，
     * 仅记录告警日志，不阻塞引擎事务（避免引擎作业卡死）。
     */
    static final class AsyncExecutorIncidentListener implements FlowableEventListener {

        private static final Logger LOG = LoggerFactory.getLogger(AsyncExecutorIncidentListener.class);

        /** incident 类型映射：deadletter → DEAD_LETTER，其余 → MANUAL_ACTION */
        private static final String DEAD_LETTER_TYPE = "deadletter";

        private final WorkflowIncidentRepository incidentRepository;
        private final WorkflowInstanceRefRepository instanceRepository;
        private final ApplicationEventPublisher publisher;
        private final Clock clock;
        private final AsyncExecutorIncidentCounters counters;

        AsyncExecutorIncidentListener(
                WorkflowIncidentRepository incidentRepository,
                WorkflowInstanceRefRepository instanceRepository,
                ApplicationEventPublisher publisher,
                Clock clock) {
            this(incidentRepository, instanceRepository, publisher, clock, new AsyncExecutorIncidentCounters());
        }

        AsyncExecutorIncidentListener(
                WorkflowIncidentRepository incidentRepository,
                WorkflowInstanceRefRepository instanceRepository,
                ApplicationEventPublisher publisher,
                Clock clock,
                AsyncExecutorIncidentCounters counters) {
            this.incidentRepository = Objects.requireNonNull(incidentRepository);
            this.instanceRepository = Objects.requireNonNull(instanceRepository);
            this.publisher = Objects.requireNonNull(publisher);
            this.clock = Objects.requireNonNull(clock);
            this.counters = Objects.requireNonNull(counters);
        }

        @Override
        public void onEvent(FlowableEvent event) {
            if (!(event instanceof FlowableEngineEntityEvent entityEvent)) {
                return;
            }
            FlowableEngineEventType type = (FlowableEngineEventType) event.getType();
            switch (type) {
                case JOB_EXECUTION_FAILURE, JOB_RETRIES_DECREMENTED, INCIDENT_CREATED ->
                        handleIncidentEvent(entityEvent, type);
                default -> { /* 忽略其他事件 */ }
            }
        }

        private void handleIncidentEvent(FlowableEngineEntityEvent event, FlowableEngineEventType type) {
            Object entity = event.getEntity();
            if (!(entity instanceof Incident incident)) {
                LOG.debug("忽略非 Incident 实体事件：type={}, entityClass={}",
                        type, entity == null ? "null" : entity.getClass().getName());
                return;
            }

            try {
                WorkflowIncidentContext ctx = resolveContext(incident);
                if (ctx == null) {
                    LOG.warn("无法解析 incident 上下文：incidentId={}, processInstanceId={}",
                            incident.getId(), incident.getProcessInstanceId());
                    return;
                }

                WorkflowIncidentStatus targetStatus = determineTargetStatus(incident, type);
                upsertIncident(incident, ctx, targetStatus);
                counters.increment(targetStatus);

                if (targetStatus == WorkflowIncidentStatus.DEAD_LETTER
                        || targetStatus == WorkflowIncidentStatus.MANUAL_ACTION) {
                    publisher.publishEvent(WorkflowAsyncExecutorAlert.of(
                            incident.getId(),
                            incident.getProcessInstanceId(),
                            incident.getActivityId(),
                            incident.getIncidentType(),
                            targetStatus,
                            clock.instant()));
                }
            } catch (RuntimeException ex) {
                // PDP 侧持久化失败不得阻塞引擎事务；仅记录告警日志，由运维通过引擎表兜底
                LOG.error("同步 Flowable incident 到 PDP 失败：incidentId={}, type={}；"
                        + "引擎事务继续，需人工核对 ACT_RU_INCIDENT 与 workflow_incident 表",
                        incident.getId(), type, ex);
            }
        }

        @Nullable
        private WorkflowIncidentContext resolveContext(Incident incident) {
            String engineProcessInstanceId = incident.getProcessInstanceId();
            if (engineProcessInstanceId == null || engineProcessInstanceId.isBlank()) {
                return null;
            }
            Optional<WorkflowInstanceRefRecord> refOpt =
                    instanceRepository.findByEngineProcessInstanceId(engineProcessInstanceId);
            if (refOpt.isEmpty()) {
                return null;
            }
            WorkflowInstanceRefRecord ref = refOpt.get();
            return new WorkflowIncidentContext(
                    ref.workspaceId(),
                    ref.id(),
                    incident.getActivityId(),
                    incident.getIncidentType(),
                    incident.getExecutionId());
        }

        private WorkflowIncidentStatus determineTargetStatus(Incident incident, FlowableEngineEventType type) {
            // deadletter 类型直接进入死信终态
            if (DEAD_LETTER_TYPE.equalsIgnoreCase(incident.getIncidentType())) {
                return WorkflowIncidentStatus.DEAD_LETTER;
            }
            // INCIDENT_CREATED 事件且无重试次数 → 进入 MANUAL_ACTION
            if (type == FlowableEngineEventType.INCIDENT_CREATED) {
                return WorkflowIncidentStatus.MANUAL_ACTION;
            }
            // 其余视为 RETRYING（重试中）
            return WorkflowIncidentStatus.RETRYING;
        }

        private void upsertIncident(
                Incident incident,
                WorkflowIncidentContext ctx,
                WorkflowIncidentStatus targetStatus) {
            String engineJobId = incident.getConfiguration(); // Flowable 将 job id 存于 configuration
            Instant now = clock.instant();

            Optional<WorkflowIncidentRecord> existing =
                    engineJobId == null ? Optional.empty()
                            : incidentRepository.findByEngineJobId(engineJobId);

            if (existing.isPresent()) {
                WorkflowIncidentRecord rec = existing.get();
                int newAttempts = rec.attempts() + 1;
                incidentRepository.updateRetry(
                        rec.id(),
                        newAttempts,
                        incident.getCauseIncidentId(),
                        sanitizeMessage(incident.getIncidentMessage()),
                        computeDigest(incident),
                        targetStatus == WorkflowIncidentStatus.RETRYING
                                ? now.plus(Duration.ofSeconds(10)) : null,
                        rec.revision());
                if (targetStatus.isTerminal() || targetStatus == WorkflowIncidentStatus.MANUAL_ACTION) {
                    incidentRepository.markManualAction(rec.id(), rec.revision() + 1);
                }
            } else {
                WorkflowIncidentRecord fresh = new WorkflowIncidentRecord(
                        UUID.randomUUID(),
                        ctx.workspaceId(),
                        ctx.instanceRefId(),
                        engineJobId,
                        ctx.activityKey(),
                        ctx.incidentType(),
                        1,
                        incident.getCauseIncidentId(),
                        sanitizeMessage(incident.getIncidentMessage()),
                        computeDigest(incident),
                        targetStatus == WorkflowIncidentStatus.RETRYING
                                ? now.plus(Duration.ofSeconds(10)) : null,
                        targetStatus,
                        null,
                        null,
                        null,
                        now,
                        now,
                        now,
                        0);
                incidentRepository.save(fresh);
            }
        }

        /** 脱敏错误消息：截断 500 字符，移除可能包含的堆栈信息。 */
        private static String sanitizeMessage(String raw) {
            if (raw == null) {
                return null;
            }
            String trimmed = raw.replaceAll("\\s+", " ").trim();
            return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
        }

        /** 计算错误摘要：使用 incident id + type 作为幂等键，避免暴露内部堆栈。 */
        private static String computeDigest(Incident incident) {
            return Integer.toHexString(Objects.hash(
                    incident.getIncidentType(),
                    incident.getActivityId(),
                    incident.getIncidentMessage()));
        }

        @Override
        public boolean isFailOnException() {
            // PDP 侧同步失败不得阻塞引擎事务（见 handleIncidentEvent 的 try/catch）
            return false;
        }

        @Override
        public boolean isFireOnTransactionLifecycleEvent() {
            return false;
        }

        @Override
        public String getOnTransaction() {
            return null;
        }
    }

    /** Incident 上下文：从实例引用解析出的工作空间与实例信息。 */
    private record WorkflowIncidentContext(
            WorkspaceId workspaceId,
            WorkflowInstanceId instanceRefId,
            String activityKey,
            String incidentType,
            String executionId) {
    }

    /**
     * 异步执行器 incident 计数器（线程安全，进程内易失）。
     *
     * <p>仅用于实时指标暴露；持久化计数以 {@link WorkflowIncidentRepository} 为准。
     */
    static final class AsyncExecutorIncidentCounters {

        private final AtomicLong openCount = new AtomicLong();
        private final AtomicLong retryingCount = new AtomicLong();
        private final AtomicLong manualActionCount = new AtomicLong();
        private final AtomicLong deadLetterCount = new AtomicLong();
        private final AtomicLong alertCount = new AtomicLong();

        void increment(WorkflowIncidentStatus status) {
            switch (status) {
                case OPEN -> openCount.incrementAndGet();
                case RETRYING -> retryingCount.incrementAndGet();
                case MANUAL_ACTION -> manualActionCount.incrementAndGet();
                case DEAD_LETTER -> deadLetterCount.incrementAndGet();
                default -> { /* RESOLVED 不计入告警指标 */ }
            }
        }

        void incrementAlert() {
            alertCount.incrementAndGet();
        }

        long openCount() { return openCount.get(); }
        long retryingCount() { return retryingCount.get(); }
        long manualActionCount() { return manualActionCount.get(); }
        long deadLetterCount() { return deadLetterCount.get(); }
        long alertCount() { return alertCount.get(); }
    }

    /**
     * 异步执行器指标注册器。
     *
     * <p>实现 {@link org.springframework.beans.factory.SmartInitializingSingleton}
     * 在所有单例 Bean 初始化完成后注册 micrometer 指标。
     */
    static final class AsyncExecutorMetricsInitializer
            implements org.springframework.beans.factory.SmartInitializingSingleton {

        private static final Logger LOG = LoggerFactory.getLogger(AsyncExecutorMetricsInitializer.class);

        private static final String METRIC_INCIDENT_TOTAL = "flowable.async.executor.incident.total";
        private static final String METRIC_DEADLETTER_TOTAL = "flowable.async.executor.deadletter.total";
        private static final String METRIC_ALERT_TOTAL = "flowable.async.executor.alert.total";

        private final MeterRegistry meterRegistry;
        private final AsyncExecutorIncidentCounters counters;

        AsyncExecutorMetricsInitializer(MeterRegistry meterRegistry, AsyncExecutorIncidentCounters counters) {
            this.meterRegistry = Objects.requireNonNull(meterRegistry);
            this.counters = Objects.requireNonNull(counters);
        }

        @Override
        public void afterSingletonsInstantiated() {
            Tags common = Tags.of("component", "workflow-async-executor");

            meterRegistry.gauge(METRIC_INCIDENT_TOTAL,
                    common.and("status", "open"),
                    counters,
                    AsyncExecutorIncidentCounters::openCount);
            meterRegistry.gauge(METRIC_INCIDENT_TOTAL,
                    common.and("status", "retrying"),
                    counters,
                    AsyncExecutorIncidentCounters::retryingCount);
            meterRegistry.gauge(METRIC_INCIDENT_TOTAL,
                    common.and("status", "manual_action"),
                    counters,
                    AsyncExecutorIncidentCounters::manualActionCount);
            meterRegistry.gauge(METRIC_DEADLETTER_TOTAL,
                    common,
                    counters,
                    AsyncExecutorIncidentCounters::deadLetterCount);
            meterRegistry.gauge(METRIC_ALERT_TOTAL,
                    common,
                    counters,
                    AsyncExecutorIncidentCounters::alertCount);

            LOG.info("Flowable 异步执行器指标已注册：{}", List.of(
                    METRIC_INCIDENT_TOTAL, METRIC_DEADLETTER_TOTAL, METRIC_ALERT_TOTAL));
        }
    }

    /**
     * 异步执行器告警事件。
     *
     * <p>当 incident 进入 {@link WorkflowIncidentStatus#DEAD_LETTER} 或
     * {@link WorkflowIncidentStatus#MANUAL_ACTION} 时发布，供平台告警通道订阅
     * （如邮件、IM、PagerDuty），并触发人工补偿流程。
     *
     * @param incidentId        Flowable incident ID
     * @param processInstanceId Flowable 流程实例 ID（引擎侧）
     * @param activityKey       发生活动节点键
     * @param incidentType      incident 类型（如 deadletter、failedJob）
     * @param status            PDP 侧 incident 状态
     * @param occurredAt        发生时间
     */
    public static final class WorkflowAsyncExecutorAlert extends ApplicationEvent {

        private final String flowableIncidentId;
        private final String engineProcessInstanceId;
        private final String activityKey;
        private final String incidentType;
        private final WorkflowIncidentStatus status;
        private final Instant occurredAt;

        private WorkflowAsyncExecutorAlert(
                Object source,
                String flowableIncidentId,
                String engineProcessInstanceId,
                String activityKey,
                String incidentType,
                WorkflowIncidentStatus status,
                Instant occurredAt) {
            super(source);
            this.flowableIncidentId = flowableIncidentId;
            this.engineProcessInstanceId = engineProcessInstanceId;
            this.activityKey = activityKey;
            this.incidentType = incidentType;
            this.status = status;
            this.occurredAt = occurredAt;
        }

        public static WorkflowAsyncExecutorAlert of(
                String flowableIncidentId,
                String engineProcessInstanceId,
                String activityKey,
                String incidentType,
                WorkflowIncidentStatus status,
                Instant occurredAt) {
            return new WorkflowAsyncExecutorAlert(
                    "workflow-async-executor",
                    flowableIncidentId,
                    engineProcessInstanceId,
                    activityKey,
                    incidentType,
                    status,
                    occurredAt);
        }

        public String flowableIncidentId() { return flowableIncidentId; }
        public String engineProcessInstanceId() { return engineProcessInstanceId; }
        public String activityKey() { return activityKey; }
        public String incidentType() { return incidentType; }
        public WorkflowIncidentStatus status() { return status; }
        public Instant occurredAt() { return occurredAt; }

        /** 严重程度：DEAD_LETTER 为 CRITICAL，MANUAL_ACTION 为 WARNING。 */
        public String severity() {
            return status == WorkflowIncidentStatus.DEAD_LETTER ? "CRITICAL" : "WARNING";
        }
    }
}
