package com.pdp.shared.operation;

import com.pdp.shared.context.ActorRef;
import com.pdp.shared.id.UuidV7Generator;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 操作确认记录（FR-168 明确确认）。
 *
 * <p>操作者基于 {@link ImpactPreview} 的具体版本显式确认操作。确认记录包含：
 * <ul>
 *   <li>引用的预览 ID 与版本（防止"预览 A → 修改 → 执行 B"）；</li>
 *   <li>确认者身份（{@link ActorRef}）；</li>
 *   <li>确认时间；</li>
 *   <li>操作者声明的预期结果（可选，用于审计对比实际结果）。</li>
 * </ul>
 *
 * <p>确认后操作进入 {@link OperationState#CONFIRMED}，可执行。
 * 确认记录持久化到审计链，作为操作者知情同意的证据。
 *
 * @param confirmationId      确认 ID（UUIDv7）
 * @param previewId           引用的预览 ID
 * @param previewVersion      引用的预览版本
 * @param confirmedBy         确认者
 * @param confirmedAt         确认时间
 * @param expectedOutcome     操作者声明的预期结果（可选）
 * @param acknowledgedIrreversible 操作者是否显式确认不可逆风险
 */
public record OperationConfirmation(
        UUID confirmationId,
        UUID previewId,
        int previewVersion,
        ActorRef confirmedBy,
        Instant confirmedAt,
        String expectedOutcome,
        boolean acknowledgedIrreversible) {

    public OperationConfirmation {
        Objects.requireNonNull(confirmationId, "confirmationId 不能为空");
        Objects.requireNonNull(previewId, "previewId 不能为空");
        if (previewVersion <= 0) {
            throw new IllegalArgumentException("previewVersion 必须为正");
        }
        Objects.requireNonNull(confirmedBy, "confirmedBy 不能为空");
        Objects.requireNonNull(confirmedAt, "confirmedAt 不能为空");
    }

    /**
     * 创建确认记录。
     *
     * @param preview              被确认的预览
     * @param confirmedBy          确认者
     * @param expectedOutcome      预期结果（可选）
     * @param acknowledgedIrreversible 是否确认不可逆风险（预览含不可逆变更时 MUST 为 true）
     * @return 确认记录
     */
    public static OperationConfirmation of(
            ImpactPreview preview,
            ActorRef confirmedBy,
            String expectedOutcome,
            boolean acknowledgedIrreversible) {
        if (preview.hasIrreversibleImpact() && !acknowledgedIrreversible) {
            throw new IllegalStateException(
                    "预览包含不可逆变更，操作者 MUST 显式确认不可逆风险");
        }
        return new OperationConfirmation(
                UUID.randomUUID(), // 确认 ID 不需时间序，使用随机 UUID
                preview.previewId(), preview.version(),
                confirmedBy, Instant.now(), expectedOutcome, acknowledgedIrreversible);
    }
}
