package com.pdp.persistence.domainconfig.adapter;

import com.pdp.domainconfig.domain.packageversion.DomainPackageImpactPreview;
import com.pdp.domainconfig.port.DomainPackageImpactPreviewRepository;
import com.pdp.persistence.domainconfig.mapper.DomainPackageImpactPreviewMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 领域包升级影响预览仓储适配器（MySQL 实现，FR-168 高风险操作框架）。
 */
@Repository
public class DomainPackageImpactPreviewRepositoryImpl implements DomainPackageImpactPreviewRepository {

    private final DomainPackageImpactPreviewMapper mapper;

    public DomainPackageImpactPreviewRepositoryImpl(DomainPackageImpactPreviewMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<DomainPackageImpactPreview> findById(UUID id) {
        ImpactPreviewRow row = mapper.selectById(id);
        return row == null ? Optional.empty() : Optional.of(row.toPreview());
    }

    @Override
    public Optional<DomainPackageImpactPreview> findLatestByCandidateVersion(UUID candidateVersionId) {
        ImpactPreviewRow row = mapper.selectLatestByCandidateVersion(candidateVersionId);
        return row == null ? Optional.empty() : Optional.of(row.toPreview());
    }

    @Override
    public Optional<DomainPackageImpactPreview> findLatestByPackageAndVersionPair(UUID packageId,
                                                                                   UUID currentVersionId,
                                                                                   UUID candidateVersionId) {
        ImpactPreviewRow row = mapper.selectLatestByPackageAndVersionPair(
                packageId, currentVersionId, candidateVersionId);
        return row == null ? Optional.empty() : Optional.of(row.toPreview());
    }

    @Override
    public void save(DomainPackageImpactPreview preview) {
        int rows = mapper.insert(ImpactPreviewRow.fromPreview(preview));
        if (rows != 1) {
            throw new IllegalStateException("影响预览插入失败: " + preview.id());
        }
    }

    @Override
    public boolean confirm(UUID id, String confirmedBy, Instant confirmedAt) {
        return mapper.confirm(id, confirmedBy, confirmedAt, confirmedAt) == 1;
    }

    @Override
    public int deleteExpired(Instant now) {
        return mapper.deleteExpired(now);
    }
}
