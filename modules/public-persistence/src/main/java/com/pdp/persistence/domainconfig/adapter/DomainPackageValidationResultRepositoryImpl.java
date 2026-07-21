package com.pdp.persistence.domainconfig.adapter;

import com.pdp.domainconfig.domain.packageversion.DomainPackageValidationResult;
import com.pdp.domainconfig.domain.packageversion.ValidationResultStatus;
import com.pdp.domainconfig.port.DomainPackageValidationResultRepository;
import com.pdp.persistence.domainconfig.mapper.DomainPackageValidationResultMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 领域包版本校验结果仓储适配器（MySQL 实现，FR-167、SC-013）。
 */
@Repository
public class DomainPackageValidationResultRepositoryImpl implements DomainPackageValidationResultRepository {

    private final DomainPackageValidationResultMapper mapper;

    public DomainPackageValidationResultRepositoryImpl(DomainPackageValidationResultMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<DomainPackageValidationResult> findById(UUID id) {
        ValidationResultRow row = mapper.selectById(id);
        return row == null ? Optional.empty() : Optional.of(row.toResult());
    }

    @Override
    public Optional<DomainPackageValidationResult> findByVersionAndJob(UUID versionId, UUID jobId) {
        ValidationResultRow row = mapper.selectByVersionAndJob(versionId, jobId);
        return row == null ? Optional.empty() : Optional.of(row.toResult());
    }

    @Override
    public Optional<DomainPackageValidationResult> findLatestByVersion(UUID versionId) {
        ValidationResultRow row = mapper.selectLatestByVersion(versionId);
        return row == null ? Optional.empty() : Optional.of(row.toResult());
    }

    @Override
    public void save(DomainPackageValidationResult result) {
        int rows = mapper.insert(ValidationResultRow.fromResult(result));
        if (rows != 1) {
            throw new IllegalStateException("校验结果插入失败: " + result.id());
        }
    }

    @Override
    public boolean updateStatus(UUID id, ValidationResultStatus newStatus, boolean passed, Instant now) {
        return mapper.updateStatus(id, newStatus, passed, now) == 1;
    }
}
