package com.pdp.persistence.domainconfig.adapter;

import com.pdp.domainconfig.domain.packageversion.DomainPackage;
import com.pdp.domainconfig.domain.packageversion.DomainPackageStatus;
import com.pdp.domainconfig.port.DomainPackageQueryFilter;
import com.pdp.domainconfig.port.DomainPackageRepository;
import com.pdp.persistence.domainconfig.mapper.DomainPackageMapper;
import com.pdp.shared.page.PageRequest;
import com.pdp.shared.page.PageResult;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 领域包仓储适配器（MySQL 实现）。
 *
 * <p>实现 {@link DomainPackageRepository} 端口，委托 {@link DomainPackageMapper}；
 * 位于基础设施层 {@code com.pdp.persistence.domainconfig.adapter}，被领域/应用层通过端口消费。
 * 不使用 {@code @DS}，遵循默认 {@code pdpPrimary} 主库路由。
 *
 * <p>装配：从 {@link DomainPackageRow} 扁平化的 designer/publisher 三列还原为
 * {@link com.pdp.domainconfig.domain.packageversion.PrincipalRef} 值对象。
 *
 * <p>游标分页：游标为 {@link DomainPackageCursorCodec} 编码的 {@code UUIDv7 id}。
 */
@Repository
public class DomainPackageRepositoryImpl implements DomainPackageRepository {

    private final DomainPackageMapper mapper;

    public DomainPackageRepositoryImpl(DomainPackageMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<DomainPackage> findById(UUID id) {
        DomainPackageRow row = mapper.selectById(id);
        return row == null ? Optional.empty() : Optional.of(assemble(row));
    }

    @Override
    public Optional<DomainPackage> findByWorkspaceAndKey(UUID workspaceId, String stableKey) {
        DomainPackageRow row = mapper.selectByWorkspaceAndKey(workspaceId, stableKey);
        return row == null ? Optional.empty() : Optional.of(assemble(row));
    }

    @Override
    public PageResult<DomainPackage> findByParentPackage(UUID parentPackageId, PageRequest pageRequest) {
        UUID lastId = DomainPackageCursorCodec.decode(pageRequest.cursor());
        int querySize = pageRequest.pageSize() + 1;
        List<DomainPackageRow> rows = mapper.selectByParentPackage(parentPackageId, lastId, querySize);
        return toPage(rows, pageRequest.pageSize());
    }

    @Override
    public PageResult<DomainPackage> findByFilter(DomainPackageQueryFilter filter, PageRequest pageRequest) {
        UUID lastId = DomainPackageCursorCodec.decode(pageRequest.cursor());
        int querySize = pageRequest.pageSize() + 1;
        List<DomainPackageRow> rows = mapper.selectByFilter(filter, lastId, querySize);
        return toPage(rows, pageRequest.pageSize());
    }

    @Override
    public void save(DomainPackage domainPackage) {
        DomainPackageRow row = disassemble(domainPackage);
        int rows = mapper.insert(row);
        if (rows != 1) {
            throw new IllegalStateException("领域包插入失败: " + domainPackage.id());
        }
    }

    @Override
    public boolean updateBasicInfo(UUID id, String name, String description,
                                    int expectedRevision, Instant now) {
        return mapper.updateBasicInfo(id, name, description, expectedRevision, now) == 1;
    }

    @Override
    public boolean updateStatus(UUID id, DomainPackageStatus newStatus,
                                 int expectedRevision, Instant now) {
        return mapper.updateStatus(id, newStatus, expectedRevision, now) == 1;
    }

    @Override
    public boolean updateCurrentPublishedVersion(UUID id, UUID currentPublishedVersionId,
                                                  int expectedRevision, Instant now) {
        return mapper.updateCurrentPublishedVersion(id, currentPublishedVersionId, expectedRevision, now) == 1;
    }

    // ============================================================
    // 私有辅助方法
    // ============================================================

    private PageResult<DomainPackage> toPage(List<DomainPackageRow> rows, int pageSize) {
        boolean hasMore = rows.size() > pageSize;
        List<DomainPackageRow> pageRows = hasMore ? rows.subList(0, pageSize) : rows;
        List<DomainPackage> page = new ArrayList<>(pageRows.size());
        for (DomainPackageRow row : pageRows) {
            page.add(assemble(row));
        }
        String nextCursor = hasMore
                ? DomainPackageCursorCodec.encode(page.get(page.size() - 1).id())
                : null;
        return PageResult.of(page, nextCursor, hasMore);
    }

    private DomainPackage assemble(DomainPackageRow row) {
        return new DomainPackage(
                row.id(),
                row.workspaceId(),
                row.stableKey(),
                row.name(),
                row.description(),
                row.layer(),
                row.parentPackageId(),
                row.status(),
                row.designer(),
                row.publisher(),
                row.currentPublishedVersionId(),
                row.revision(),
                row.createdAt(),
                row.updatedAt());
    }

    private DomainPackageRow disassemble(DomainPackage pkg) {
        return new DomainPackageRow(
                pkg.id(),
                pkg.workspaceId(),
                pkg.stableKey(),
                pkg.name(),
                pkg.description(),
                pkg.layer(),
                pkg.parentPackageId(),
                pkg.status(),
                pkg.designer().principalType(),
                pkg.designer().principalId(),
                pkg.designer().displayLabel(),
                pkg.publisher().principalType(),
                pkg.publisher().principalId(),
                pkg.publisher().displayLabel(),
                pkg.currentPublishedVersionId(),
                pkg.revision(),
                pkg.createdAt(),
                pkg.updatedAt());
    }
}
