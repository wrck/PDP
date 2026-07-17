package com.pdp.shared.operation;

import com.pdp.shared.context.ActorRef;
import com.pdp.shared.context.WorkspaceId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 高风险操作完整记录（FR-168 审计证据）。
 *
 * <p>贯穿操作全生命周期的不可变记录，包含预览版本链、确认记录、执行结果和补偿记录。
 * 持久化到审计摘要链，作为操作者知情同意和操作执行的可追溯证据（SC-039）。
 *
 * @param operationId      操作 ID（UUIDv7）
 * @param operationType    操作类型
 * @param workspaceId      工作空间 ID（权限范围）
 * @param scope            操作范围
 * @param state            当前状态
 * @param previewVersions  预览版本链（按版本号升序，最新版本在末尾）
 * @param confirmation     确认记录（CONFIRMED 后非空）
 * @param executionResult  执行结果（终态时非空）
 * @param compensationRecords 补偿记录列表（多次补偿时累积）
 * @param createdBy        创建者
 * @param createdAt        创建时间
 * @param updatedAt        最后更新时间
 * @param revision         记录 revision（乐观锁）
 */
public record HighRiskOperationRecord(
        UUID operationId,
        HighRiskOperationType operationType,
        WorkspaceId workspaceId,
        String scope,
        OperationState state,
        List<ImpactPreview> previewVersions,
        OperationConfirmation confirmation,
        ExecutionResult executionResult,
        List<CompensationRecord> compensationRecords,
        ActorRef createdBy,
        Instant createdAt,
        Instant updatedAt,
        int revision) {

    public HighRiskOperationRecord {
        Objects.requireNonNull(operationId, "operationId 不能为空");
        Objects.requireNonNull(operationType, "operationType 不能为空");
        Objects.requireNonNull(workspaceId, "workspaceId 不能为空");
        Objects.requireNonNull(scope, "scope 不能为空");
        if (scope.isBlank()) {
            throw new IllegalArgumentException("scope 不能为空白");
        }
        Objects.requireNonNull(state, "state 不能为空");
        previewVersions = previewVersions == null ? List.of() : List.copyOf(previewVersions);
        compensationRecords = compensationRecords == null
                ? List.of() : List.copyOf(compensationRecords);
        Objects.requireNonNull(createdBy, "createdBy 不能为空");
        Objects.requireNonNull(createdAt, "createdAt 不能为空");
        Objects.requireNonNull(updatedAt, "updatedAt 不能为空");
        // updatedAt 可以等于 createdAt（刚创建），或晚于 createdAt（已更新）
        // 不允许早于 createdAt（逻辑错误）
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt 不能早于 createdAt");
        }
        if (revision <= 0) {
            throw new IllegalArgumentException("revision 必须为正");
        }
    }

    /**
     * 获取最新预览版本（版本号最大）。
     *
     * @return 最新预览，无预览时返回 empty
     */
    public Optional<ImpactPreview> latestPreview() {
        if (previewVersions.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(previewVersions.get(previewVersions.size() - 1));
    }

    /**
     * 获取指定版本的预览。
     *
     * @param version 预览版本号
     * @return 对应版本预览，不存在返回 empty
     */
    public Optional<ImpactPreview> previewAtVersion(int version) {
        return previewVersions.stream()
                .filter(p -> p.version() == version)
                .findFirst();
    }

    /**
     * 操作是否已确认（CONFIRMED 或后续状态）。
     */
    public boolean isConfirmed() {
        return confirmation != null;
    }

    /**
     * 操作是否终态。
     */
    public boolean isTerminal() {
        return state.isTerminal();
    }

    /**
     * 累积补偿次数。
     */
    public int compensationCount() {
        return compensationRecords.size();
    }
}
