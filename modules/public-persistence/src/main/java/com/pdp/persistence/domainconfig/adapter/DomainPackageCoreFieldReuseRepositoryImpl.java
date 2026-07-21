package com.pdp.persistence.domainconfig.adapter;

import com.pdp.domainconfig.domain.packageversion.DomainPackageCoreFieldReuse;
import com.pdp.domainconfig.port.DomainPackageCoreFieldReuseRepository;
import com.pdp.persistence.domainconfig.mapper.DomainPackageCoreFieldReuseMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 领域包版本核心字段复用声明仓储适配器（MySQL 实现，FR-134、SC-025）。
 */
@Repository
public class DomainPackageCoreFieldReuseRepositoryImpl implements DomainPackageCoreFieldReuseRepository {

    private final DomainPackageCoreFieldReuseMapper mapper;

    public DomainPackageCoreFieldReuseRepositoryImpl(DomainPackageCoreFieldReuseMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<DomainPackageCoreFieldReuse> findByVersion(UUID versionId) {
        List<CoreFieldReuseRow> rows = mapper.selectByVersion(versionId);
        List<DomainPackageCoreFieldReuse> result = new ArrayList<>(rows.size());
        for (CoreFieldReuseRow row : rows) {
            result.add(row.toReuse());
        }
        return result;
    }

    @Override
    public List<DomainPackageCoreFieldReuse> findByCoreField(String coreFieldKey, String coreObjectType) {
        List<CoreFieldReuseRow> rows = mapper.selectByCoreField(coreFieldKey, coreObjectType);
        List<DomainPackageCoreFieldReuse> result = new ArrayList<>(rows.size());
        for (CoreFieldReuseRow row : rows) {
            result.add(row.toReuse());
        }
        return result;
    }

    @Override
    public void save(DomainPackageCoreFieldReuse reuse) {
        int rows = mapper.insert(CoreFieldReuseRow.fromReuse(reuse));
        if (rows != 1) {
            throw new IllegalStateException("核心字段复用声明插入失败: " + reuse.id());
        }
    }

    @Override
    public void saveAll(List<DomainPackageCoreFieldReuse> reuses) {
        if (reuses == null || reuses.isEmpty()) {
            return;
        }
        List<CoreFieldReuseRow> rows = new ArrayList<>(reuses.size());
        for (DomainPackageCoreFieldReuse reuse : reuses) {
            rows.add(CoreFieldReuseRow.fromReuse(reuse));
        }
        int inserted = mapper.insertAll(rows);
        if (inserted != reuses.size()) {
            throw new IllegalStateException("核心字段复用声明批量插入失败，期望 "
                    + reuses.size() + " 实际 " + inserted);
        }
    }

    @Override
    public int deleteByVersion(UUID versionId) {
        return mapper.deleteByVersion(versionId);
    }
}
