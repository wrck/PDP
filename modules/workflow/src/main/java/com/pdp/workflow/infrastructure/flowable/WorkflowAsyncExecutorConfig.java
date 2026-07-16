package com.pdp.workflow.infrastructure.flowable;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Flowable 适配层使用的独立异步执行预算。该类型不得出现在 workflow 公开端口中。
 */
public final class WorkflowAsyncExecutorConfig {
    private final Policy policy;

    public WorkflowAsyncExecutorConfig(Policy policy) {
        this.policy = Objects.requireNonNull(policy);
    }

    public ThreadPoolExecutor createExecutor() {
        AtomicInteger sequence = new AtomicInteger();
        return new ThreadPoolExecutor(policy.corePoolSize(), policy.maxPoolSize(),
                policy.keepAlive().toMillis(), TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(policy.queueCapacity()),
                runnable -> {
                    Thread thread = new Thread(runnable, "pdp-workflow-" + sequence.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    public Policy policy() {
        return policy;
    }

    public record Policy(int corePoolSize, int maxPoolSize, int queueCapacity,
                         int maxRetries, Duration retryDelay, Duration keepAlive,
                         int connectionBudget, int deadLetterThreshold) {
        public Policy {
            Objects.requireNonNull(retryDelay, "retryDelay");
            Objects.requireNonNull(keepAlive, "keepAlive");
            if (corePoolSize < 1 || maxPoolSize < corePoolSize || queueCapacity < 1
                    || maxRetries < 0 || connectionBudget < maxPoolSize
                    || deadLetterThreshold < 1 || retryDelay.isNegative() || keepAlive.isNegative()) {
                throw new IllegalArgumentException("工作流异步执行预算无效");
            }
        }
    }
}
