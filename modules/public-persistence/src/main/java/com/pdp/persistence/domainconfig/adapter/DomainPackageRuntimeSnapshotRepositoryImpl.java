package com.pdp.persistence.domainconfig.adapter;

import com.pdp.domainconfig.domain.packageversion.RuntimeSnapshot;
import com.pdp.domainconfig.port.DomainPackageRuntimeSnapshotRepository;
import com.pdp.persistence.domainconfig.mapper.DomainPackageRuntimeSnapshotMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * 领域包运行时版本快照仓储适配器（MySQL 实现，FR-018）。
 */
@Repository
public class DomainPackageRuntimeSnapshotRepositoryImpl implements DomainPackageRuntimeSnapshotRepository {

    private final DomainPackageRuntimeSnapshotMapper mapper;

    public DomainPackageRuntimeSnapshotRepositoryImpl(DomainPackageRuntimeSnapshotMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<RuntimeSnapshot> findById(String snapshotId) {
        RuntimeSnapshotRow row = mapper.selectById(snapshotId);
        return row == null ? Optional.empty() : Optional.of(row.toSnapshot());
    }

    @Override
    public Optional<RuntimeSnapshot> findByVersionId(UUID versionId) {
        RuntimeSnapshotRow row = mapper.selectByVersionId(versionId);
        return row == null ? Optional.empty() : Optional.of(row.toSnapshot());
    }

    @Override
    public Optional<RuntimeSnapshot> findLatestByPackageId(UUID packageId) {
        RuntimeSnapshotRow row = mapper.selectLatestByPackageId(packageId);
        return row == null ? Optional.empty() : Optional.of(row.toSnapshot());
    }

    @Override
    public void save(RuntimeSnapshot snapshot, UUID versionId, UUID packageId, String resolvedObjectsJson) {
        RuntimeSnapshotRow row = RuntimeSnapshotRow.fromSnapshot(
                snapshot, versionId, packageId, resolvedObjectsJson);
        int rows = mapper.insert(row);
        if (rows != 1) {
            throw new IllegalStateException("运行时快照插入失败: " + snapshot.snapshotId());
        }
    }

    @Override
    public Optional<String> findResolvedObjectsJson(String snapshotId) {
        return Optional.ofNullable(mapper.selectResolvedObjectsJson(snapshotId));
    }
}
