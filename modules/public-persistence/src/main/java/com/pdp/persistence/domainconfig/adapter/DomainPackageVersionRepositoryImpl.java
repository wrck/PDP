package com.pdp.persistence.domainconfig.adapter;

import com.pdp.domainconfig.domain.packageversion.CompatibilityStatement;
import com.pdp.domainconfig.domain.packageversion.DomainPackageVersion;
import com.pdp.domainconfig.domain.packageversion.DomainPackageVersionStatus;
import com.pdp.domainconfig.port.DomainPackageVersionQueryFilter;
import com.pdp.domainconfig.port.DomainPackageVersionRepository;
import com.pdp.persistence.domainconfig.mapper.DomainPackageVersionMapper;
import com.pdp.shared.page.PageRequest;
import com.pdp.shared.page.PageResult;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 领域包版本仓储适配器（MySQL 实现）。
 *
 * <p>实现 {@link DomainPackageVersionRepository} 端口，委托 {@link DomainPackageVersionMapper}。
 * 装配时通过 {@link DomainPackageJsonCodec#readCompatibilityStatement(String)} 还原
 * {@link CompatibilityStatement}；持久化时通过
 * {@link DomainPackageJsonCodec#writeCompatibilityStatement(CompatibilityStatement)} 序列化。
 *
 * <p>所有状态迁移方法通过 SQL {@code WHERE revision = #{expectedRevision} AND status = ...}
 * 实现乐观锁与状态前置条件双重校验，由应用层（{@code DomainPackageLifecycleService}，T123）
 * 在调用前调用 {@link DomainPackageVersion#canSubmitForReview()} 等方法。
 */
@Repository
public class DomainPackageVersionRepositoryImpl implements DomainPackageVersionRepository {

    private final DomainPackageVersionMapper mapper;

    public DomainPackageVersionRepositoryImpl(DomainPackageVersionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<DomainPackageVersion> findById(UUID id) {
        DomainPackageVersionRow row = mapper.selectById(id);
        return row == null ? Optional.empty() : Optional.of(assemble(row));
    }

    @Override
    public Optional<DomainPackageVersion> findByPackageAndSemanticVersion(UUID packageId, String semanticVersion) {
        DomainPackageVersionRow row = mapper.selectByPackageAndSemanticVersion(packageId, semanticVersion);
        return row == null ? Optional.empty() : Optional.of(assemble(row));
    }

    @Override
    public PageResult<DomainPackageVersion> findByFilter(DomainPackageVersionQueryFilter filter, PageRequest pageRequest) {
        UUID lastId = DomainPackageCursorCodec.decode(pageRequest.cursor());
        int querySize = pageRequest.pageSize() + 1;
        List<DomainPackageVersionRow> rows = mapper.selectByFilter(filter, lastId, querySize);
        boolean hasMore = rows.size() > pageRequest.pageSize();
        List<DomainPackageVersionRow> pageRows = hasMore ? rows.subList(0, pageRequest.pageSize()) : rows;
        List<DomainPackageVersion> page = new ArrayList<>(pageRows.size());
        for (DomainPackageVersionRow row : pageRows) {
            page.add(assemble(row));
        }
        String nextCursor = hasMore
                ? DomainPackageCursorCodec.encode(page.get(page.size() - 1).id())
                : null;
        return PageResult.of(page, nextCursor, hasMore);
    }

    @Override
    public Optional<DomainPackageVersion> findCurrentPublished(UUID packageId) {
        DomainPackageVersionRow row = mapper.selectCurrentPublished(packageId);
        return row == null ? Optional.empty() : Optional.of(assemble(row));
    }

    @Override
    public Optional<DomainPackageVersion> findLatestDraft(UUID packageId) {
        DomainPackageVersionRow row = mapper.selectLatestDraft(packageId);
        return row == null ? Optional.empty() : Optional.of(assemble(row));
    }

    @Override
    public void save(DomainPackageVersion version) {
        DomainPackageVersionRow row = disassemble(version);
        int rows = mapper.insert(row);
        if (rows != 1) {
            throw new IllegalStateException("领域包版本插入失败: " + version.id());
        }
    }

    @Override
    public boolean updateDraft(UUID id, String manifestJson, String contentHash,
                                CompatibilityStatement compatibilityStatement,
                                String extendsVersionRange,
                                int expectedRevision, Instant now) {
        String compatibilityJson = DomainPackageJsonCodec.writeCompatibilityStatement(compatibilityStatement);
        return mapper.updateDraft(id, manifestJson, contentHash, compatibilityJson,
                extendsVersionRange, expectedRevision, now) == 1;
    }

    @Override
    public boolean updateStatus(UUID id, DomainPackageVersionStatus newStatus,
                                 int expectedRevision, Instant now) {
        return mapper.updateStatus(id, newStatus, expectedRevision, now) == 1;
    }

    @Override
    public boolean submitForReview(UUID id, String submittedBy, Instant submittedAt, int expectedRevision) {
        return mapper.submitForReview(id, submittedBy, submittedAt, expectedRevision) == 1;
    }

    @Override
    public boolean reject(UUID id, String rejectedBy, Instant rejectedAt, String rejectReason, int expectedRevision) {
        return mapper.reject(id, rejectedBy, rejectedAt, rejectReason, expectedRevision) == 1;
    }

    @Override
    public boolean publish(UUID id, String publishedBy, Instant publishedAt,
                            String runtimeSnapshotId, String parentSnapshotId, int expectedRevision) {
        return mapper.publish(id, publishedBy, publishedAt, runtimeSnapshotId, parentSnapshotId, expectedRevision) == 1;
    }

    @Override
    public boolean freeze(UUID id, int expectedRevision, Instant now) {
        return mapper.freeze(id, expectedRevision, now) == 1;
    }

    @Override
    public boolean deprecate(UUID id, int expectedRevision, Instant now) {
        return mapper.deprecate(id, expectedRevision, now) == 1;
    }

    @Override
    public boolean retire(UUID id, int expectedRevision, Instant now) {
        return mapper.retire(id, expectedRevision, now) == 1;
    }

    @Override
    public boolean rollback(UUID id, String rolledBackBy, Instant rolledBackAt,
                             String rollbackReason, int expectedRevision) {
        return mapper.rollback(id, rolledBackBy, rolledBackAt, rollbackReason, expectedRevision) == 1;
    }

    // ============================================================
    // 私有辅助方法
    // ============================================================

    private DomainPackageVersion assemble(DomainPackageVersionRow row) {
        return new DomainPackageVersion(
                row.id(),
                row.packageId(),
                row.semanticVersion(),
                row.contentHash(),
                row.status(),
                row.parentSnapshotId(),
                row.runtimeSnapshotId(),
                row.manifestJson(),
                row.compatibilityStatement(),
                row.extendsVersionRange(),
                row.submittedBy(),
                row.submittedAt(),
                row.publishedBy(),
                row.publishedAt(),
                row.rejectedBy(),
                row.rejectedAt(),
                row.rejectReason(),
                row.revision(),
                row.createdAt(),
                row.updatedAt());
    }

    private DomainPackageVersionRow disassemble(DomainPackageVersion version) {
        return new DomainPackageVersionRow(
                version.id(),
                version.packageId(),
                version.semanticVersion(),
                version.contentHash(),
                version.status(),
                version.parentSnapshotId(),
                version.runtimeSnapshotId(),
                version.manifestJson(),
                DomainPackageJsonCodec.writeCompatibilityStatement(version.compatibilityStatement()),
                version.extendsVersionRange(),
                version.submittedBy(),
                version.submittedAt(),
                version.publishedBy(),
                version.publishedAt(),
                version.rejectedBy(),
                version.rejectedAt(),
                version.rejectReason(),
                version.revision(),
                version.createdAt(),
                version.updatedAt());
    }
}
