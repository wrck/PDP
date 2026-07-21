package com.pdp.domainconfig.port;

import com.pdp.domainconfig.domain.packageversion.DomainPackageValidationResult;
import com.pdp.domainconfig.domain.packageversion.ValidationResultStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 领域包版本校验结果仓储端口（FR-167、SC-013）。
 *
 * <p>校验结果由 {@code DomainPackageValidationService}（T121）异步产出，作为版本发布与
 * 提交审核的强制前置条件。一个版本可有多次校验记录（每次草稿更新触发新校验），
 * {@link #findLatestByVersion} 返回最新一次。
 */
public interface DomainPackageValidationResultRepository {

    Optional<DomainPackageValidationResult> findById(UUID id);

    /** 按版本 ID 与任务 ID 精确查找。 */
    Optional<DomainPackageValidationResult> findByVersionAndJob(UUID versionId, UUID jobId);

    /** 查找版本最新一次校验结果（按 validatedAt 降序）。 */
    Optional<DomainPackageValidationResult> findLatestByVersion(UUID versionId);

    /** 插入校验结果（含 items JSON 列）。 */
    void save(DomainPackageValidationResult result);

    /**
     * 更新校验任务状态（PENDING → RUNNING → COMPLETED/FAILED）与 passed 标记。
     *
     * <p>仅在状态迁移时使用；items 列表通过 {@link #save} 一次性写入，不支持局部更新。
     *
     * @return {@code true} 成功；{@code false} 不存在
     */
    boolean updateStatus(UUID id, ValidationResultStatus newStatus, boolean passed, Instant now);
}
