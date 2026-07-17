package com.pdp.shared.operation;

import com.pdp.shared.context.ActorRef;
import com.pdp.shared.id.UuidV7Generator;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 补偿执行记录（FR-168 补偿路径证据）。
 *
 * <p>每次调用 {@link HighRiskOperationPort#compensate} 生成一条记录，累积到
 * {@link HighRiskOperationRecord#compensationRecords}。多次补偿时按时间序追加。
 *
 * @param compensationId     补偿记录 ID（UUIDv7）
 * @param operationId        关联操作 ID
 * @param strategy           使用的补偿策略
 * @param triggeredBy        触发者
 * @param triggeredAt        触发时间
 * @param stepsExecuted      已执行步骤
 * @param result             补偿结果描述
 * @param succeeded          是否成功
 * @param failureReason      失败原因（succeeded=false 时非空）
 */
public record CompensationRecord(
        UUID compensationId,
        UUID operationId,
        CompensationStrategy strategy,
        ActorRef triggeredBy,
        Instant triggeredAt,
        List<String> stepsExecuted,
        String result,
        boolean succeeded,
        String failureReason) {

    public CompensationRecord {
        Objects.requireNonNull(compensationId, "compensationId 不能为空");
        Objects.requireNonNull(operationId, "operationId 不能为空");
        Objects.requireNonNull(strategy, "strategy 不能为空");
        Objects.requireNonNull(triggeredBy, "triggeredBy 不能为空");
        Objects.requireNonNull(triggeredAt, "triggeredAt 不能为空");
        stepsExecuted = stepsExecuted == null ? List.of() : List.copyOf(stepsExecuted);
        Objects.requireNonNull(result, "result 不能为空");
        if (!succeeded && (failureReason == null || failureReason.isBlank())) {
            throw new IllegalArgumentException("补偿失败时 failureReason 不能为空");
        }
    }

    /**
     * 创建成功补偿记录。
     */
    public static CompensationRecord success(
            UUID operationId, CompensationStrategy strategy, ActorRef triggeredBy,
            List<String> stepsExecuted, String result) {
        return new CompensationRecord(
                UuidV7Generator.next(), operationId, strategy, triggeredBy, Instant.now(),
                stepsExecuted, result, true, null);
    }

    /**
     * 创建失败补偿记录（需人工介入或重试）。
     */
    public static CompensationRecord failure(
            UUID operationId, CompensationStrategy strategy, ActorRef triggeredBy,
            List<String> stepsExecuted, String failureReason) {
        return new CompensationRecord(
                UuidV7Generator.next(), operationId, strategy, triggeredBy, Instant.now(),
                stepsExecuted, "补偿失败: " + failureReason, false, failureReason);
    }
}
