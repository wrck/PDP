package com.pdp.workflow.model;

import com.pdp.shared.context.ActorRef;
import com.pdp.shared.context.IdempotencyKey;
import com.pdp.shared.operation.OperationConfirmation;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 流程实例受控管理动作命令值对象（对应 OpenAPI {@code WorkflowAdminActionCommand}）。
 *
 * <p>管理动作 MUST 遵循 FR-168 高风险操作治理：
 * 影响预览、明确确认、审计以及撤销、回退或人工补偿路径。
 *
 * <p><strong>动作类型</strong>（对应 OpenAPI {@code action} 枚举）：
 * <ul>
 *   <li>{@link #PAUSE}：暂停实例（{@code ACTIVE → SUSPENDED}）；</li>
 *   <li>{@link #RESUME}：恢复实例（{@code SUSPENDED → ACTIVE}）；</li>
 *   <li>{@link #RETRY}：重试失败 incident（{@code INCIDENT → ACTIVE}）；</li>
 *   <li>{@link #MIGRATE}：迁移到新版本（需 {@link #migrationPlan} 与 {@link #confirmation}）；</li>
 *   <li>{@link #TERMINATE}：终止实例（终态）；</li>
 *   <li>{@link #MANUAL_COMPENSATE}：人工补偿（按 {@link com.pdp.shared.operation.CompensationPlan} 执行）。</li>
 * </ul>
 *
 * @param instanceId       实例 ID
 * @param action           动作类型
 * @param reason           原因（审计， minLength=5）
 * @param expectedRevision 期望版本（乐观并发控制）
 * @param migrationPlan    迁移计划（仅 MIGRATE 动作需要）
 * @param confirmation     高风险操作确认记录（MIGRATE/TERMINATE/MANUAL_COMPENSATE 需要，
 *                         由 {@link com.pdp.shared.operation.HighRiskOperationPort#confirm} 生成）
 * @param impactPreviewId  影响预览 ID（可选，用于审计追踪）
 * @param idempotencyKey   幂等键
 * @param triggeredBy      触发者
 */
public record WorkflowAdminAction(
        WorkflowInstanceId instanceId,
        Action action,
        String reason,
        int expectedRevision,
        MigrationPlan migrationPlan,
        OperationConfirmation confirmation,
        UUID impactPreviewId,
        IdempotencyKey idempotencyKey,
        ActorRef triggeredBy) {

    /** 管理动作类型。 */
    public enum Action {
        PAUSE,
        RESUME,
        RETRY,
        MIGRATE,
        TERMINATE,
        MANUAL_COMPENSATE;

        public String stableKey() {
            return name();
        }

        /** 是否为高风险动作（需确认记录）。 */
        public boolean isHighRisk() {
            return this == MIGRATE || this == TERMINATE || this == MANUAL_COMPENSATE;
        }
    }

    public WorkflowAdminAction {
        Objects.requireNonNull(instanceId, "instanceId 不能为 null");
        Objects.requireNonNull(action, "action 不能为 null");
        Objects.requireNonNull(reason, "reason 不能为 null");
        if (reason.length() < 5) {
            throw new IllegalArgumentException("reason 长度必须 >= 5");
        }
        if (reason.length() > 2000) {
            throw new IllegalArgumentException("reason 长度必须 <= 2000");
        }
        if (expectedRevision < 0) {
            throw new IllegalArgumentException("expectedRevision 不能为负");
        }
        Objects.requireNonNull(idempotencyKey, "idempotencyKey 不能为 null");
        Objects.requireNonNull(triggeredBy, "triggeredBy 不能为 null");
        // MIGRATE 动作 MUST 携带 migrationPlan
        if (action == Action.MIGRATE) {
            Objects.requireNonNull(migrationPlan, "MIGRATE 动作必须携带 migrationPlan");
        }
        // 高风险动作 MUST 携带 confirmation
        if (action.isHighRisk()) {
            Objects.requireNonNull(confirmation, action + " 为高风险动作，必须携带 confirmation");
        }
    }

    public Optional<MigrationPlan> migrationPlanOptional() {
        return Optional.ofNullable(migrationPlan);
    }

    public Optional<OperationConfirmation> confirmationOptional() {
        return Optional.ofNullable(confirmation);
    }

    public Optional<UUID> impactPreviewIdOptional() {
        return Optional.ofNullable(impactPreviewId);
    }
}
