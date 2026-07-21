package com.pdp.persistence.domainconfig.adapter;

import com.pdp.domainconfig.domain.metamodel.CoreFieldCatalogEntry;
import com.pdp.domainconfig.domain.metamodel.CoreFieldSource;
import com.pdp.domainconfig.domain.metamodel.DataType;
import com.pdp.domainconfig.port.CoreFieldCatalogRepository;
import com.pdp.persistence.domainconfig.mapper.CoreFieldCatalogMapper;
import com.pdp.shared.page.PageRequest;
import com.pdp.shared.page.PageResult;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 核心字段目录仓储适配器（MySQL 实现，FR-132、SC-025）。
 *
 * <p>游标分页：游标为 {@link DomainPackageCursorCodec} 编码的 {@code UUIDv7 id}。
 */
@Repository
public class CoreFieldCatalogRepositoryImpl implements CoreFieldCatalogRepository {

    private final CoreFieldCatalogMapper mapper;

    public CoreFieldCatalogRepositoryImpl(CoreFieldCatalogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<CoreFieldCatalogEntry> findById(UUID id) {
        CoreFieldCatalogRow row = mapper.selectById(id);
        return row == null ? Optional.empty() : Optional.of(row.toEntry());
    }

    @Override
    public Optional<CoreFieldCatalogEntry> findByStableKeyAndObjectType(String stableKey, String coreObjectType) {
        CoreFieldCatalogRow row = mapper.selectByStableKeyAndObjectType(stableKey, coreObjectType);
        return row == null ? Optional.empty() : Optional.of(row.toEntry());
    }

    @Override
    public PageResult<CoreFieldCatalogEntry> findByObjectType(String coreObjectType, PageRequest pageRequest) {
        UUID lastId = DomainPackageCursorCodec.decode(pageRequest.cursor());
        int querySize = pageRequest.pageSize() + 1;
        List<CoreFieldCatalogRow> rows = mapper.selectByObjectType(coreObjectType, lastId, querySize);
        return toPage(rows, pageRequest.pageSize());
    }

    @Override
    public PageResult<CoreFieldCatalogEntry> findBySource(CoreFieldSource source, PageRequest pageRequest) {
        UUID lastId = DomainPackageCursorCodec.decode(pageRequest.cursor());
        int querySize = pageRequest.pageSize() + 1;
        List<CoreFieldCatalogRow> rows = mapper.selectBySource(source, lastId, querySize);
        return toPage(rows, pageRequest.pageSize());
    }

    @Override
    public PageResult<CoreFieldCatalogEntry> findAll(PageRequest pageRequest) {
        UUID lastId = DomainPackageCursorCodec.decode(pageRequest.cursor());
        int querySize = pageRequest.pageSize() + 1;
        List<CoreFieldCatalogRow> rows = mapper.selectAll(lastId, querySize);
        return toPage(rows, pageRequest.pageSize());
    }

    @Override
    public void save(CoreFieldCatalogEntry entry) {
        int rows = mapper.insert(CoreFieldCatalogRow.fromEntry(entry));
        if (rows != 1) {
            throw new IllegalStateException("核心字段插入失败: " + entry.id());
        }
    }

    @Override
    public boolean update(UUID id, String label, String semantics, boolean allowedOverride,
                          Set<String> aliases, DataType dataType,
                          int expectedRevision, Instant now) {
        String aliasesJson = DomainPackageJsonCodec.writeStringSet(aliases);
        return mapper.update(id, label, semantics, allowedOverride, aliasesJson,
                dataType, expectedRevision, now) == 1;
    }

    private PageResult<CoreFieldCatalogEntry> toPage(List<CoreFieldCatalogRow> rows, int pageSize) {
        boolean hasMore = rows.size() > pageSize;
        List<CoreFieldCatalogRow> pageRows = hasMore ? rows.subList(0, pageSize) : rows;
        List<CoreFieldCatalogEntry> page = new ArrayList<>(pageRows.size());
        for (CoreFieldCatalogRow row : pageRows) {
            page.add(row.toEntry());
        }
        String nextCursor = hasMore
                ? DomainPackageCursorCodec.encode(page.get(page.size() - 1).id())
                : null;
        return PageResult.of(page, nextCursor, hasMore);
    }
}
