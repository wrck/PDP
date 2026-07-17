package com.pdp.workflow.domain;

import com.pdp.shared.context.ActorRef;
import com.pdp.shared.context.WorkspaceId;
import com.pdp.workflow.model.ProcessDefinitionKey;
import com.pdp.workflow.model.ProcessVersion;
import com.pdp.workflow.model.ValidationFinding;
import com.pdp.workflow.model.WorkflowDefinitionId;
import com.pdp.workflow.model.WorkflowDefinitionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 流程定义持久化聚合（对应 {@code workflow_definition} 表）。
 *
 * <p>承载 BPMN 2.0.2 内容、版本、内容哈希、领域包关联、状态机与审计字段，
 * 供 {@link WorkflowDefinitionRepository} 持久化使用。
 *
 * <p>与 {@link com.pdp.workflow.model.WorkflowDefinitionSummary}（公开读模型）的区别：
 * 本记录包含完整 BPMN XML 内容与审计字段，不直接暴露于业务模块端口契约。
 *
 * @param id                      流程定义 ID（PDP 自有 UUIDv7）
 * @param workspaceId             工作空间边界
 * @param stableKey               流程定义稳定键
 * @param name                    流程名称
 * @param bpmnVersion             BPMN 规范版本（如 "2.0.2"）
 * @param businessVersion         业务版本（语义化）
 * @param contentHash             BPMN 内容哈希（SHA-256 hex）
 * @param bpmnXml                 BPMN 2.0.2 XML 文本
 * @param domainPackageVersionId  关联领域包版本 ID（可选）
 * @param status                  定义状态机
 * @param findings                校验发现项（VALIDATED 状态携带）
 * @param createdBy               创建者
 * @param createdAt               创建时间
 * @param approvedBy              审批者（可选）
 * @param approvedAt              审批时间（可选）
 * @param updatedAt               更新时间
 * @param revision                乐观锁版本
 */
public record WorkflowDefinitionRecord(
        WorkflowDefinitionId id,
        WorkspaceId workspaceId,
        ProcessDefinitionKey stableKey,
        String name,
        String bpmnVersion,
        ProcessVersion businessVersion,
        String contentHash,
        String bpmnXml,
        UUID domainPackageVersionId,
        WorkflowDefinitionStatus status,
        List<ValidationFinding> findings,
        ActorRef createdBy,
        Instant createdAt,
        ActorRef approvedBy,
        Instant approvedAt,
        Instant updatedAt,
        int revision) {

    public WorkflowDefinitionRecord {
        Objects.requireNonNull(id, "id 不能为 null");
        Objects.requireNonNull(workspaceId, "workspaceId 不能为 null");
        Objects.requireNonNull(stableKey, "stableKey 不能为 null");
        Objects.requireNonNull(name, "name 不能为 null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name 不能为空白");
        }
        Objects.requireNonNull(bpmnVersion, "bpmnVersion 不能为 null");
        if (bpmnVersion.isBlank()) {
            throw new IllegalArgumentException("bpmnVersion 不能为空白");
        }
        Objects.requireNonNull(businessVersion, "businessVersion 不能为 null");
        Objects.requireNonNull(contentHash, "contentHash 不能为 null");
        if (contentHash.isBlank()) {
            throw new IllegalArgumentException("contentHash 不能为空白");
        }
        Objects.requireNonNull(bpmnXml, "bpmnXml 不能为 null");
        if (bpmnXml.isBlank()) {
            throw new IllegalArgumentException("bpmnXml 不能为空白");
        }
        Objects.requireNonNull(status, "status 不能为 null");
        findings = findings == null ? List.of() : List.copyOf(findings);
        Objects.requireNonNull(createdBy, "createdBy 不能为 null");
        Objects.requireNonNull(createdAt, "createdAt 不能为 null");
        Objects.requireNonNull(updatedAt, "updatedAt 不能为 null");
        if (revision < 0) {
            throw new IllegalArgumentException("revision 不能为负");
        }
    }

    public Optional<UUID> domainPackageVersionIdOptional() {
        return Optional.ofNullable(domainPackageVersionId);
    }

    public Optional<ActorRef> approvedByOptional() {
        return Optional.ofNullable(approvedBy);
    }

    public Optional<Instant> approvedAtOptional() {
        return Optional.ofNullable(approvedAt);
    }
}
