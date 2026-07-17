package com.pdp.operations.job;

import java.time.Instant;
import java.util.Objects;

/**
 * 后台作业失败明细值对象。
 *
 * <p>对应表 {@code background_job.failure_items}（JSON 数组）中的单条记录。作业处理条目失败时记录，
 * 作业终态后由 {@link BackgroundJobPort} 查询接口暴露给前端，用于人工排查、重试或补偿
 * （spec.md："保留检查点、失败明细和可安全重试/人工补偿入口"）。
 *
 * <p>失败明细 MUST 携带稳定原因分类（{@code reasonCode}），符合 spec.md "所有非法迁移、并发冲突和
 * 补偿操作必须具有稳定原因分类、下一步建议和关联证据"。
 *
 * @param itemKey      失败条目稳定键（业务对象 ID、行号、批次键等）
 * @param reasonCode   稳定原因分类（如 {@code VALIDATION.FAILED}、{@code CONCURRENCY.OPTIMISTIC_LOCK}）
 * @param errorMessage 人类可读错误消息（不含敏感数据）
 * @param retryable    是否可安全重试（true 表示人工或自动重试可恢复；false 表示需人工补偿或修复）
 * @param nextStep     下一步建议（如"修正数据后重试"、"联系数据负责人"、"检查外部系统连通性"）
 * @param occurredAt   失败发生时间
 */
public record JobFailureItem(
        String itemKey,
        String reasonCode,
        String errorMessage,
        boolean retryable,
        String nextStep,
        Instant occurredAt) {

    public JobFailureItem {
        Objects.requireNonNull(itemKey, "itemKey 不能为 null");
        if (itemKey.isBlank()) {
            throw new IllegalArgumentException("itemKey 不能为空白");
        }
        Objects.requireNonNull(reasonCode, "reasonCode 不能为 null");
        if (reasonCode.isBlank()) {
            throw new IllegalArgumentException("reasonCode 不能为空白");
        }
        Objects.requireNonNull(occurredAt, "occurredAt 不能为 null");
    }

    public static JobFailureItem of(
            String itemKey,
            String reasonCode,
            String errorMessage,
            boolean retryable,
            String nextStep,
            Instant occurredAt) {
        return new JobFailureItem(itemKey, reasonCode, errorMessage, retryable, nextStep, occurredAt);
    }

    /**
     * 可重试失败（如临时网络、并发冲突、超时）。
     */
    public static JobFailureItem retryable(
            String itemKey, String reasonCode, String errorMessage, String nextStep, Instant occurredAt) {
        return new JobFailureItem(itemKey, reasonCode, errorMessage, true, nextStep, occurredAt);
    }

    /**
     * 不可重试失败（如数据校验失败、权限不足、业务规则违反），需人工补偿或修复。
     */
    public static JobFailureItem unrecoverable(
            String itemKey, String reasonCode, String errorMessage, String nextStep, Instant occurredAt) {
        return new JobFailureItem(itemKey, reasonCode, errorMessage, false, nextStep, occurredAt);
    }
}
