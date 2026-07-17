package com.pdp.operations.job;

import com.pdp.shared.error.ErrorCode;
import com.pdp.shared.error.PdpException;

/**
 * 后台作业执行异常。
 *
 * <p>{@link JobHandler} 实现遇到致命错误时抛出，{@link BackgroundJobCoordinator} 据此将作业转为
 * FAILED 状态，保留检查点和已收集的失败明细，提供"可安全重试/人工补偿入口"（spec.md 状态机表）。
 *
 * <p>异常携带稳定原因分类和是否可重试标记，符合 spec.md "所有非法迁移、并发冲突和补偿操作必须具有
 * 稳定原因分类、下一步建议和关联证据"。
 */
public class JobExecutionException extends PdpException {

    private static final long serialVersionUID = 1L;

    private final Reason reason;
    private final boolean retryable;
    private final String nextStep;

    /**
     * 异常原因分类（稳定键）。
     */
    public enum Reason {
        /** 资源预算超限（超时、连接超限、失败超阈值）。 */
        RESOURCE_BUDGET_EXCEEDED("JOB.RESOURCE_BUDGET_EXCEEDED"),
        /** 致命业务错误（数据校验失败、业务规则违反）。 */
        FATAL_BUSINESS_ERROR("JOB.FATAL_BUSINESS_ERROR"),
        /** 外部依赖不可用（对象存储、外部集成）。 */
        EXTERNAL_DEPENDENCY_UNAVAILABLE("JOB.EXTERNAL_DEPENDENCY_UNAVAILABLE"),
        /** 并发冲突重试耗尽。 */
        CONCURRENCY_EXHAUSTED("JOB.CONCURRENCY_EXHAUSTED"),
        /** 检查点损坏或版本不兼容。 */
        CHECKPOINT_CORRUPTED("JOB.CHECKPOINT_CORRUPTED"),
        /** 作业类型未注册处理器。 */
        HANDLER_NOT_REGISTERED("JOB.HANDLER_NOT_REGISTERED"),
        /** 作业被取消（协调器主动终止）。 */
        CANCELLED("JOB.CANCELLED");

        private final String stableKey;

        Reason(String stableKey) {
            this.stableKey = stableKey;
        }

        public String stableKey() {
            return stableKey;
        }
    }

    public JobExecutionException(Reason reason, boolean retryable, String message, String nextStep) {
        super(ErrorCode.INTERNAL_ERROR, message);
        this.reason = reason;
        this.retryable = retryable;
        this.nextStep = nextStep;
    }

    public JobExecutionException(Reason reason, boolean retryable, String message, String nextStep, Throwable cause) {
        super(ErrorCode.INTERNAL_ERROR, message, cause);
        this.reason = reason;
        this.retryable = retryable;
        this.nextStep = nextStep;
    }

    public Reason reason() {
        return reason;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public String nextStep() {
        return nextStep;
    }

    @Override
    public String getMessage() {
        return "[" + reason.stableKey() + "] " + super.getMessage()
                + " | 下一步: " + nextStep
                + " | 可重试: " + retryable;
    }

    /**
     * 资源预算超限异常（可重试，自动暂停）。
     */
    public static JobExecutionException resourceBudgetExceeded(String message, String nextStep) {
        return new JobExecutionException(Reason.RESOURCE_BUDGET_EXCEEDED, true, message, nextStep);
    }

    /**
     * 致命业务错误（不可重试，需人工修复）。
     */
    public static JobExecutionException fatalBusinessError(String message, String nextStep) {
        return new JobExecutionException(Reason.FATAL_BUSINESS_ERROR, false, message, nextStep);
    }

    /**
     * 外部依赖不可用（可重试，指数退避）。
     */
    public static JobExecutionException externalDependencyUnavailable(String message, String nextStep, Throwable cause) {
        return new JobExecutionException(Reason.EXTERNAL_DEPENDENCY_UNAVAILABLE, true, message, nextStep, cause);
    }

    /**
     * 并发冲突重试耗尽（可重试，从检查点继续）。
     */
    public static JobExecutionException concurrencyExhausted(String message, String nextStep) {
        return new JobExecutionException(Reason.CONCURRENCY_EXHAUSTED, true, message, nextStep);
    }

    /**
     * 处理器未注册。
     */
    public static JobExecutionException handlerNotRegistered(BackgroundJobType type) {
        return new JobExecutionException(Reason.HANDLER_NOT_REGISTERED, false,
                "作业类型 " + type.stableKey() + " 未注册处理器",
                "联系平台管理员注册对应 JobHandler 实现");
    }

    /**
     * 作业被取消。
     */
    public static JobExecutionException cancelled(String message) {
        return new JobExecutionException(Reason.CANCELLED, false, message, "作业已取消，无需重试");
    }
}
