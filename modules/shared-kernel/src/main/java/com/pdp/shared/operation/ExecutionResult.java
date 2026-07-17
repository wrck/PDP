package com.pdp.shared.operation;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 操作执行结果（FR-168）。
 *
 * <p>封装 {@link HighRiskOperationPort#execute} 的返回值。执行后操作进入终态
 * （{@link OperationState#COMPLETED}、{@link OperationState#COMPENSATED} 或
 * {@link OperationState#FAILED}）或禁用拒绝（{@link OperationState#CANCELLED} + 禁用原因）。
 *
 * @param operationId       操作 ID（UUIDv7，贯穿预览-确认-执行-审计全生命周期）
 * @param finalState        最终状态
 * @param completedAt       完成时间（终态时非空）
 * @param actualOutcome     实际结果描述（COMPLETED 时非空）
 * @param failureReason     失败原因（FAILED 时非空）
 * @param compensationApplied 已执行的补偿描述（COMPENSATED 时非空）
 * @param disabledReason    禁用原因（操作禁用被拒绝执行时非空，finalState=CANCELLED）
 */
public record ExecutionResult(
        UUID operationId,
        OperationState finalState,
        Instant completedAt,
        String actualOutcome,
        String failureReason,
        String compensationApplied,
        DisabledReason disabledReason) {

    /**
     * 创建成功执行结果。
     */
    public static ExecutionResult completed(UUID operationId, String actualOutcome) {
        return new ExecutionResult(
                operationId, OperationState.COMPLETED, Instant.now(),
                actualOutcome, null, null, null);
    }

    /**
     * 创建补偿完成结果。
     */
    public static ExecutionResult compensated(UUID operationId, String compensationApplied) {
        return new ExecutionResult(
                operationId, OperationState.COMPENSATED, Instant.now(),
                null, null, compensationApplied, null);
    }

    /**
     * 创建失败结果（需人工介入）。
     */
    public static ExecutionResult failed(UUID operationId, String failureReason) {
        return new ExecutionResult(
                operationId, OperationState.FAILED, Instant.now(),
                null, failureReason, null, null);
    }

    /**
     * 创建禁用拒绝结果（操作类型在当前阶段禁用，拒绝执行）。
     */
    public static ExecutionResult disabled(UUID operationId, DisabledReason reason) {
        return new ExecutionResult(
                operationId, OperationState.CANCELLED, Instant.now(),
                null, null, null, reason);
    }

    public Optional<String> actualOutcome() {
        return Optional.ofNullable(actualOutcome);
    }

    public Optional<String> failureReason() {
        return Optional.ofNullable(failureReason);
    }

    public Optional<String> compensationApplied() {
        return Optional.ofNullable(compensationApplied);
    }

    public Optional<DisabledReason> disabledReason() {
        return Optional.ofNullable(disabledReason);
    }
}
