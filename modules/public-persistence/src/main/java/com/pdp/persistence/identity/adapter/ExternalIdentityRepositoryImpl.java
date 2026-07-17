package com.pdp.persistence.identity.adapter;

import com.pdp.identity.domain.ExternalIdentity;
import com.pdp.identity.port.ExternalIdentityRepository;
import com.pdp.persistence.identity.mapper.ExternalIdentityMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 外部身份绑定仓储适配器（MySQL 实现）。
 *
 * <p>实现 {@link ExternalIdentityRepository} 端口，委托 {@link ExternalIdentityMapper}；
 * 唯一约束 {@code uniq_ext_issuer_subject} 由 DDL 守护，重复绑定时数据库抛出唯一约束冲突，
 * 由上层统一异常处理转为 {@code CONFLICT} 业务异常。
 */
@Repository
public class ExternalIdentityRepositoryImpl implements ExternalIdentityRepository {

    private final ExternalIdentityMapper mapper;

    public ExternalIdentityRepositoryImpl(ExternalIdentityMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<ExternalIdentity> findByIssuerAndSubject(String issuer, String subject) {
        return Optional.ofNullable(mapper.selectByIssuerAndSubject(issuer, subject));
    }

    @Override
    public Optional<ExternalIdentity> findById(UUID id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    @Override
    public List<ExternalIdentity> findByUserId(UUID userId) {
        return mapper.selectByUserId(userId);
    }

    @Override
    public void save(ExternalIdentity identity) {
        int rows = mapper.insert(identity);
        if (rows != 1) {
            throw new IllegalStateException("外部身份绑定插入失败: " + identity.id());
        }
    }

    @Override
    public void unbind(UUID id, int expectedRevision) {
        int rows = mapper.unbind(id, expectedRevision);
        if (rows != 1) {
            throw new IllegalStateException("外部身份解绑失败（版本冲突或不存在）: " + id);
        }
    }
}
