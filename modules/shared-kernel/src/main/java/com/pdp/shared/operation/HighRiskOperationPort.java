package com.pdp.shared.operation;

import com.pdp.shared.context.ActorRef;
import com.pdp.shared.context.WorkspaceId;

import java.util.Optional;
import java.util.UUID;

/**
 * 高风险操作端口（FR-168、SC-039）。
 *
 * <p>统一高风险操作的影响预览、版本确认、执行、补偿和审计入口。业务模块（领域包发布、项目回退、
 * 基线替换、交付件发布、数据处置、历史迁移）通过实现此端口或委托给公共协调器提供能力；
 * {@link HighRiskOperationType#DATABASE_SWITCH} 在 P1 由公共实现返回稳定禁用原因。
 *
 * <p><strong>核心契约（FR-168）</strong>：
 * <ol>
 *   <li>影响预览：操作执行前 MUST 生成 {@link ImpactPreview}，包含受影响对象、不可逆变更和补偿计划；</li>
 *   <li>版本确认：操作者 MUST 基于具体预览版本显式确认（{@link OperationConfirmation}），防止
 *       "预览 A → 修改 → 执行 B"；执行时校验版本与源对象 revision 一致；</li>
 *   <li>审计：预览、确认、执行、补偿全过程 MUST 写入审计摘要链（before/after/previous digest）；</li>
 *   <li>补偿路径：操作失败或主动触发补偿时，按 {@link CompensationPlan} 执行恢复；
 *       不可逆点后只能前向修复或反向同步，不能简单回退（FR-149）。</li>
 * </ol>
 *
 * <p><strong>预览有效期与并发变化</strong>：
 * <ul>
 *   <li>预览过期后 MUST 重新生成（{@link ImpactPreview#isExpired}）；</li>
 *   <li>源对象 revision 变化后预览失效（{@link ImpactPreview#isStaleFor}），MUST 重新生成；</li>
 *   <li>重新生成递增版本号，旧版本确认记录作废。</li>
 * </ul>
 *
 * <p><strong>禁用操作契约</strong>：P1 期间 {@link HighRiskOperationType#DATABASE_SWITCH} 调用
 * {@link #preview} 或 {@link #execute} 时 MUST 返回包含 {@link DisabledReason#databaseSwitchP1Disabled()}
 * 的结果，<strong>不</strong>抛异常，保证前端展示稳定禁用提示（见 spec.md 末段）。
 *
 * <p>端口实现由业务模块提供（P1 启用类型）或由公共协调器提供（DATABASE_SWITCH 预注册）；
 * 仓储端口（持久化操作记录与预览版本）由 persistence 基础设施实现。
 */
public interface HighRiskOperationPort {

    /**
     * 生成影响预览。
     *
     * <p>操作执行前 MUST 调用此方法获取预览。预览包含受影响对象、不可逆变更和补偿计划。
     * 操作禁用时返回 {@link PreviewResult#disabled}，不抛异常。
     *
     * @param operationType 操作类型
     * @param workspaceId   工作空间 ID（权限范围）
     * @param scope         操作范围（如项目 ID、迁移批次键）
     * @param requestedBy   请求者
     * @return 预览结果（启用或禁用）
     */
    PreviewResult preview(
            HighRiskOperationType operationType,
            WorkspaceId workspaceId,
            String scope,
            ActorRef requestedBy);

    /**
     * 重新生成预览（源对象 revision 变化或预览过期时调用）。
     *
     * <p>递增版本号，保留 previewId 实现版本链追踪。旧版本确认记录作废。
     *
     * @param previewId 原 previewId
     * @return 新版本预览结果
     * @throws IllegalArgumentException previewId 不存在
     */
    PreviewResult regeneratePreview(UUID previewId);

    /**
     * 确认操作。
     *
     * <p>操作者基于具体预览版本显式确认。预览含不可逆变更时 MUST acknowledgedIrreversible=true。
     * 确认后操作进入 {@link OperationState#CONFIRMED}，可执行。
     *
     * @param previewId               引用的预览 ID
     * @param previewVersion          引用的预览版本
     * @param confirmedBy             确认者
     * @param expectedOutcome         预期结果（可选，用于审计对比实际结果）
     * @param acknowledgedIrreversible 是否确认不可逆风险（预览含不可逆变更时 MUST 为 true）
     * @return 确认记录
     * @throws IllegalStateException 预览已过期、源对象 revision 已变化或已确认过更高版本
     */
    OperationConfirmation confirm(
            UUID previewId,
            int previewVersion,
            ActorRef confirmedBy,
            String expectedOutcome,
            boolean acknowledgedIrreversible);

    /**
     * 执行操作。
     *
     * <p>仅 {@link OperationState#CONFIRMED} 状态可执行。执行时校验：
     * <ul>
     *   <li>确认记录引用的预览版本是最新版本（防止并发修改后执行旧预览）；</li>
     *   <li>源对象 revision 与预览时一致（防止并发修改）；</li>
     *   <li>确认者仍有执行权限（防止权限撤销后执行）。</li>
     * </ul>
     *
     * <p>操作禁用时返回 {@link ExecutionResult#disabled}，不抛异常。
     *
     * @param confirmationId 确认记录 ID
     * @return 执行结果（COMPLETED/COMPENSATED/FAILED/CANCELLED-禁用）
     */
    ExecutionResult execute(UUID confirmationId);

    /**
     * 触发补偿。
     *
     * <p>操作执行失败（{@link OperationState#FAILED}）或主动触发补偿时调用。
     * 按 {@link CompensationPlan} 执行恢复：未达不可逆点可 ROLLBACK，已过不可逆点只能
     * REVERSE_SYNC/MANUAL/NONE（FR-149）。
     *
     * @param operationId 操作 ID
     * @param triggeredBy 触发者
     * @return 执行结果（COMPENSATED 或 FAILED）
     * @throws IllegalStateException 操作未达可补偿状态
     */
    ExecutionResult compensate(UUID operationId, ActorRef triggeredBy);

    /**
     * 取消操作（仅未达不可逆点前）。
     *
     * <p>{@link OperationState#isCancellable} 返回 true 时可取消。
     * 不可逆点后取消 MUST 抛出 {@link IllegalStateException}。
     *
     * @param operationId 操作 ID
     * @param cancelledBy 取消者
     * @throws IllegalStateException 操作已达不可逆点，不可取消
     */
    void cancel(UUID operationId, ActorRef cancelledBy);

    /**
     * 查询操作当前状态。
     *
     * @param operationId 操作 ID
     * @return 操作状态（含预览、确认、执行、补偿全生命周期记录）
     * @throws IllegalArgumentException operationId 不存在
     */
    HighRiskOperationRecord getOperation(UUID operationId);

    /**
     * 查询预览当前版本（用于校验确认时引用的版本是否最新）。
     *
     * @param previewId 预览 ID
     * @return 预览（最新版本）
     * @throws IllegalArgumentException previewId 不存在
     */
    Optional<ImpactPreview> getPreview(UUID previewId);
}
