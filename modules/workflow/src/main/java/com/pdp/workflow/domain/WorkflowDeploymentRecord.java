package com.pdp.workflow.domain;

import com.pdp.shared.context.ActorRef;
import com.pdp.shared.context.WorkspaceId;
import com.pdp.workflow.model.WorkflowDefinitionId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 流程部署记录持久化聚合（对应 {@code workflow_deployment} 表）。
 *
 * <p>链接 PDP 流程定义到 Flowable 引擎的 {@code ProcessDefinition}/{@code Deployment} 标识，
 * 记录引擎类型、数据库类型与部署时间。同一引擎 definition ID 唯一，防止重复部署。
 *
 * <p>本记录不依赖 {@code org.flowable.*} 类型，仅保存引擎返回的字符串标识。
 *
 * @param id                       部署记录 ID（PDP 自有 UUIDv7）
 * @param definitionId             关联流程定义 ID
 * @param workspaceId              工作空间边界
 * @param engineType               引擎类型（如 "flowable"）
 * @param engineDefinitionId       引擎定义 ID（Flowable ProcessDefinition.id）
 * @param engineDefinitionVersion  引擎定义版本（Flowable 内部整数版本）
 * @param deploymentId             引擎部署 ID（Flowable Deployment.id）
 * @param databaseType             数据库类型（如 "mysql"）
 * @param deployedAt               部署时间
 * @param deployedBy               部署者
 * @param status                   部署状态机
 * @param createdAt                创建时间
 * @param revision                 乐观锁版本
 */
public record WorkflowDeploymentRecord(
        UUID id,
        WorkflowDefinitionId definitionId,
        WorkspaceId workspaceId,
        String engineType,
        String engineDefinitionId,
        int engineDefinitionVersion,
        String deploymentId,
        String databaseType,
        Instant deployedAt,
        ActorRef deployedBy,
        WorkflowDeploymentStatus status,
        Instant createdAt,
        int revision) {

    public WorkflowDeploymentRecord {
        Objects.requireNonNull(id, "id 不能为 null");
        Objects.requireNonNull(definitionId, "definitionId 不能为 null");
        Objects.requireNonNull(workspaceId, "workspaceId 不能为 null");
        Objects.requireNonNull(engineType, "engineType 不能为 null");
        if (engineType.isBlank()) {
            throw new IllegalArgumentException("engineType 不能为空白");
        }
        Objects.requireNonNull(engineDefinitionId, "engineDefinitionId 不能为 null");
        if (engineDefinitionId.isBlank()) {
            throw new IllegalArgumentException("engineDefinitionId 不能为空白");
        }
        if (engineDefinitionVersion < 0) {
            throw new IllegalArgumentException("engineDefinitionVersion 不能为负");
        }
        Objects.requireNonNull(deploymentId, "deploymentId 不能为 null");
        if (deploymentId.isBlank()) {
            throw new IllegalArgumentException("deploymentId 不能为空白");
        }
        Objects.requireNonNull(databaseType, "databaseType 不能为 null");
        if (databaseType.isBlank()) {
            throw new IllegalArgumentException("databaseType 不能为空白");
        }
        Objects.requireNonNull(deployedAt, "deployedAt 不能为 null");
        Objects.requireNonNull(deployedBy, "deployedBy 不能为 null");
        Objects.requireNonNull(status, "status 不能为 null");
        Objects.requireNonNull(createdAt, "createdAt 不能为 null");
        if (revision < 0) {
            throw new IllegalArgumentException("revision 不能为负");
        }
    }
}
