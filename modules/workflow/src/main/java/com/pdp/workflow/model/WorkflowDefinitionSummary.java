package com.pdp.workflow.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 流程定义摘要值对象（对应 OpenAPI {@code WorkflowDefinitionSummary}）。
 *
 * <p>不暴露 Flowable {@code ProcessDefinition} 对象，仅含 PDP 自有稳定契约字段。
 *
 * @param id                      流程定义 ID（PDP 自有 UUIDv7）
 * @param processDefinitionKey    流程定义稳定键
 * @param businessVersion         业务版本
 * @param contentHash             BPMN 内容哈希（SHA-256），用于校验部署一致性
 * @param status                  定义状态机
 * @param domainPackageVersionId  关联的领域包版本 ID（可选）
 * @param deployedAt              部署时间
 * @param findings                校验发现项（仅 VALIDATED 状态携带）
 */
public record WorkflowDefinitionSummary(
        WorkflowDefinitionId id,
        ProcessDefinitionKey processDefinitionKey,
        ProcessVersion businessVersion,
        String contentHash,
        WorkflowDefinitionStatus status,
        UUID domainPackageVersionId,
        Instant deployedAt,
        List<ValidationFinding> findings) {

    public WorkflowDefinitionSummary {
        Objects.requireNonNull(id, "id 不能为 null");
        Objects.requireNonNull(processDefinitionKey, "processDefinitionKey 不能为 null");
        Objects.requireNonNull(businessVersion, "businessVersion 不能为 null");
        Objects.requireNonNull(contentHash, "contentHash 不能为 null");
        if (contentHash.isBlank()) {
            throw new IllegalArgumentException("contentHash 不能为空白");
        }
        Objects.requireNonNull(status, "status 不能为 null");
        findings = findings == null ? List.of() : List.copyOf(findings);
    }

    public Optional<UUID> domainPackageVersionIdOptional() {
        return Optional.ofNullable(domainPackageVersionId);
    }

    public Optional<Instant> deployedAtOptional() {
        return Optional.ofNullable(deployedAt);
    }

    /**
     * 是否可启动新实例（仅 DEPLOYED 状态）。
     *
     * @return true 表示可启动
     */
    public boolean canStartInstance() {
        return status.canStartInstance();
    }

    /**
     * 是否含阻断性校验错误。
     *
     * @return true 表示存在 ERROR 级别发现项
     */
    public boolean hasBlockingFindings() {
        return findings.stream().anyMatch(ValidationFinding::isBlocking);
    }
}
