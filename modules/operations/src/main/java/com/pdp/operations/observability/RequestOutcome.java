package com.pdp.operations.observability;

import com.pdp.shared.context.WorkspaceId;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 请求结果观测样本（FR-165 SLI 采集的基本单元）。
 *
 * <p>对应 FR-165："月度可用性 MUST 按'成功完成且未超过适用交互时限的合格请求数 ÷ 合格请求总数'计算"。
 * 每个请求处理完成后采集一个样本，由 {@link SliCollector} 聚合计算月度可用性。
 *
 * <p><strong>合格请求判定</strong>（FR-165）：
 * <ul>
 *   <li><b>合格请求总数</b>：所有非排除请求（{@link ExclusionReason#isExcludable} 为 false）；</li>
 *   <li><b>成功完成且未超时</b>：{@link #succeeded} 为 true 且 {@link #withinTimeLimit} 为 true；</li>
 *   <li><b>可用性</b> = 成功完成且未超时数 ÷ 合格请求总数。</li>
 * </ul>
 *
 * <p><strong>排除项证据</strong>（SC-037）：排除请求 MUST 携带 {@link #exclusionEvidence}
 * （如客户端取消的请求 ID、客户网络故障的诊断信息），保证排除项证据完整率 100%。
 *
 * @param requestId          请求 ID（用于关联日志、追踪和审计）
 * @param correlationId      关联 ID（跨服务调用链）
 * @param workspaceId        工作空间（null 表示平台级请求）
 * @param serviceCategory    服务类别
 * @param operation          操作标识（如 {@code project.advance}、{@code task.update}）
 * @param actorId            操作者 ID（可为 null，系统调用）
 * @param startedAt          请求开始时间
 * @param completedAt        请求完成时间
 * @param succeeded          是否成功完成（业务语义成功，非 HTTP 2xx）
 * @param failureReason      失败原因稳定键（成功时为 null）
 * @param exclusionReason    排除原因（FR-165）
 * @param exclusionEvidence  排除证据（排除时必填，SC-037）
 */
public record RequestOutcome(
        UUID requestId,
        UUID correlationId,
        WorkspaceId workspaceId,
        ServiceCategory serviceCategory,
        String operation,
        UUID actorId,
        Instant startedAt,
        Instant completedAt,
        boolean succeeded,
        String failureReason,
        ExclusionReason exclusionReason,
        String exclusionEvidence) {

    public RequestOutcome {
        Objects.requireNonNull(requestId, "requestId 不能为 null");
        Objects.requireNonNull(serviceCategory, "serviceCategory 不能为 null");
        Objects.requireNonNull(operation, "operation 不能为 null");
        if (operation.isBlank()) {
            throw new IllegalArgumentException("operation 不能为空白");
        }
        Objects.requireNonNull(startedAt, "startedAt 不能为 null");
        Objects.requireNonNull(completedAt, "completedAt 不能为 null");
        if (completedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("completedAt 不能早于 startedAt");
        }
        if (exclusionReason == null) {
            exclusionReason = ExclusionReason.NONE;
        }
        if (exclusionReason != ExclusionReason.NONE && (exclusionEvidence == null
                || exclusionEvidence.isBlank())) {
            throw new IllegalArgumentException(
                    "排除原因 " + exclusionReason + " 必须携带证据（SC-037）");
        }
        if (succeeded && failureReason != null) {
            throw new IllegalArgumentException("成功请求不能携带 failureReason");
        }
    }

    /** 请求耗时。 */
    public Duration duration() {
        return Duration.between(startedAt, completedAt);
    }

    /** 是否在适用交互时限内完成（SC-018）。 */
    public boolean isWithinTimeLimit() {
        return duration().compareTo(serviceCategory.interactionTimeLimit()) <= 0;
    }

    /**
     * 是否为合格请求（计入可用性分母，FR-165）。
     *
     * <p>可排除请求（客户端取消、客户网络/身份故障）不计入合格请求。
     */
    public boolean isQualified() {
        return !exclusionReason.isExcludable();
    }

    /**
     * 是否为可用请求（FR-165 分子）。
     *
     * <p>合格请求中成功完成且未超过适用交互时限。
     */
    public boolean isAvailable() {
        return isQualified() && succeeded && isWithinTimeLimit();
    }

    /** 构造成功请求样本。 */
    public static RequestOutcome success(UUID requestId, UUID correlationId,
                                         WorkspaceId workspaceId, ServiceCategory category,
                                         String operation, UUID actorId,
                                         Instant startedAt, Instant completedAt) {
        return new RequestOutcome(requestId, correlationId, workspaceId, category,
                operation, actorId, startedAt, completedAt,
                true, null, ExclusionReason.NONE, null);
    }

    /** 构造失败请求样本。 */
    public static RequestOutcome failure(UUID requestId, UUID correlationId,
                                         WorkspaceId workspaceId, ServiceCategory category,
                                         String operation, UUID actorId,
                                         Instant startedAt, Instant completedAt,
                                         String failureReason) {
        return new RequestOutcome(requestId, correlationId, workspaceId, category,
                operation, actorId, startedAt, completedAt,
                false, failureReason, ExclusionReason.NONE, null);
    }

    /** 构造排除请求样本（客户端取消、客户网络/身份故障）。 */
    public static RequestOutcome excluded(UUID requestId, UUID correlationId,
                                          WorkspaceId workspaceId, ServiceCategory category,
                                          String operation, UUID actorId,
                                          Instant startedAt, Instant completedAt,
                                          boolean succeeded, String failureReason,
                                          ExclusionReason exclusionReason, String evidence) {
        return new RequestOutcome(requestId, correlationId, workspaceId, category,
                operation, actorId, startedAt, completedAt,
                succeeded, failureReason, exclusionReason, evidence);
    }
}
