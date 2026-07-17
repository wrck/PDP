package com.pdp.workflow.domain;

import com.pdp.shared.context.ActorRef;
import com.pdp.shared.context.WorkspaceId;
import com.pdp.workflow.model.WorkflowDefinitionId;
import com.pdp.workflow.model.WorkflowInstanceId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 工作流迁移历史持久化聚合（对应 {@code workflow_migration_record} 表）。
 *
 * <p>记录流程实例受控迁移的历史（源定义、目标定义、触发者、批次大小、结果），
 * 供 {@link com.pdp.workflow.administration.WorkflowAdministrationPort#listMigrationHistory}
 * 审计回查。迁移 MUST 预览、分批、可暂停并保留证据（ADR-0005 第 6 节）。
 *
 * <p>本记录为只追加审计记录，不参与乐观锁并发控制。
 *
 * @param id                   记录 ID（PDP 自有 UUIDv7）
 * @param migrationId          业务迁移 ID（幂等键，同一迁移批次共享）
 * @param instanceRefId        实例引用 ID
 * @param workspaceId          工作空间边界
 * @param sourceDefinitionId   源流程定义 ID
 * @param targetDefinitionId   目标流程定义 ID
 * @param triggeredBy          触发者
 * @param migratedAt           迁移时间
 * @param batchSize            批次大小（0 表示不分批）
 * @param successful           是否成功
 * @param failureReason        失败原因（成功时为 null）
 * @param createdAt            创建时间
 */
public record WorkflowMigrationRecord(
        UUID id,
        String migrationId,
        WorkflowInstanceId instanceRefId,
        WorkspaceId workspaceId,
        WorkflowDefinitionId sourceDefinitionId,
        WorkflowDefinitionId targetDefinitionId,
        ActorRef triggeredBy,
        Instant migratedAt,
        int batchSize,
        boolean successful,
        String failureReason,
        Instant createdAt) {

    public WorkflowMigrationRecord {
        Objects.requireNonNull(id, "id 不能为 null");
        Objects.requireNonNull(migrationId, "migrationId 不能为 null");
        if (migrationId.isBlank()) {
            throw new IllegalArgumentException("migrationId 不能为空白");
        }
        Objects.requireNonNull(instanceRefId, "instanceRefId 不能为 null");
        Objects.requireNonNull(workspaceId, "workspaceId 不能为 null");
        Objects.requireNonNull(sourceDefinitionId, "sourceDefinitionId 不能为 null");
        Objects.requireNonNull(targetDefinitionId, "targetDefinitionId 不能为 null");
        Objects.requireNonNull(triggeredBy, "triggeredBy 不能为 null");
        Objects.requireNonNull(migratedAt, "migratedAt 不能为 null");
        if (batchSize < 0) {
            throw new IllegalArgumentException("batchSize 不能为负");
        }
        Objects.requireNonNull(createdAt, "createdAt 不能为 null");
    }

    public Optional<String> failureReasonOptional() {
        return Optional.ofNullable(failureReason);
    }
}
