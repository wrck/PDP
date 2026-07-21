package com.pdp.persistence.domainconfig.adapter;

import com.pdp.domainconfig.domain.packageversion.DomainPackageMigrationPlan;
import com.pdp.domainconfig.domain.packageversion.MigrationPlanStatus;
import com.pdp.domainconfig.port.DomainPackageMigrationPlanRepository;
import com.pdp.domainconfig.port.MigrationPlanQueryFilter;
import com.pdp.persistence.domainconfig.mapper.DomainPackageMigrationPlanMapper;
import com.pdp.shared.page.PageRequest;
import com.pdp.shared.page.PageResult;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 领域包迁移计划仓储适配器（MySQL 实现，FR-168 高风险操作框架）。
 *
 * <p>游标分页：游标为 {@link DomainPackageCursorCodec} 编码的 {@code UUIDv7 id}。
 */
@Repository
public class DomainPackageMigrationPlanRepositoryImpl implements DomainPackageMigrationPlanRepository {

    private final DomainPackageMigrationPlanMapper mapper;

    public DomainPackageMigrationPlanRepositoryImpl(DomainPackageMigrationPlanMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<DomainPackageMigrationPlan> findById(UUID id) {
        MigrationPlanRow row = mapper.selectById(id);
        return row == null ? Optional.empty() : Optional.of(row.toPlan());
    }

    @Override
    public PageResult<DomainPackageMigrationPlan> findByFilter(MigrationPlanQueryFilter filter,
                                                                PageRequest pageRequest) {
        UUID lastId = DomainPackageCursorCodec.decode(pageRequest.cursor());
        int querySize = pageRequest.pageSize() + 1;
        List<MigrationPlanRow> rows = mapper.selectByFilter(filter, lastId, querySize);
        return toPage(rows, pageRequest.pageSize());
    }

    @Override
    public Optional<DomainPackageMigrationPlan> findActiveByPackage(UUID packageId) {
        MigrationPlanRow row = mapper.selectActiveByPackage(packageId);
        return row == null ? Optional.empty() : Optional.of(row.toPlan());
    }

    @Override
    public Optional<DomainPackageMigrationPlan> findActiveByVersionPair(UUID fromVersionId, UUID toVersionId) {
        MigrationPlanRow row = mapper.selectActiveByVersionPair(fromVersionId, toVersionId);
        return row == null ? Optional.empty() : Optional.of(row.toPlan());
    }

    @Override
    public void save(DomainPackageMigrationPlan plan) {
        int rows = mapper.insert(MigrationPlanRow.fromPlan(plan));
        if (rows != 1) {
            throw new IllegalStateException("迁移计划插入失败: " + plan.id());
        }
    }

    @Override
    public boolean updateStatus(UUID id, MigrationPlanStatus newStatus,
                                 int expectedRevision, Instant now) {
        return mapper.updateStatus(id, newStatus, expectedRevision, now) == 1;
    }

    @Override
    public boolean updateProgress(UUID id, int completedBatches, int failedInstances,
                                   int expectedRevision, Instant now) {
        return mapper.updateProgress(id, completedBatches, failedInstances,
                expectedRevision, now) == 1;
    }

    @Override
    public boolean markReady(UUID id, UUID impactPreviewId, UUID jobId,
                              int expectedRevision, Instant now) {
        return mapper.markReady(id, impactPreviewId, jobId, expectedRevision, now) == 1;
    }

    @Override
    public boolean updateRollbackWindow(UUID id, Instant rollbackWindowExpiresAt,
                                          int expectedRevision, Instant now) {
        return mapper.updateRollbackWindow(id, rollbackWindowExpiresAt,
                expectedRevision, now) == 1;
    }

    private PageResult<DomainPackageMigrationPlan> toPage(List<MigrationPlanRow> rows, int pageSize) {
        boolean hasMore = rows.size() > pageSize;
        List<MigrationPlanRow> pageRows = hasMore ? rows.subList(0, pageSize) : rows;
        List<DomainPackageMigrationPlan> page = new ArrayList<>(pageRows.size());
        for (MigrationPlanRow row : pageRows) {
            page.add(row.toPlan());
        }
        String nextCursor = hasMore
                ? DomainPackageCursorCodec.encode(page.get(page.size() - 1).id())
                : null;
        return PageResult.of(page, nextCursor, hasMore);
    }
}
