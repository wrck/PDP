package com.pdp.persistence.identity.mapper;

import com.pdp.identity.domain.ExternalIdentity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * 外部身份绑定 MyBatis Mapper。
 *
 * <p>纯 MyBatis 接口；所有 SQL 在 {@code resources/mapper/identity/ExternalIdentityMapper.xml} 中声明。
 * 唯一约束 {@code uniq_ext_issuer_subject} 由 DDL 守护，重复绑定抛出唯一约束冲突。
 */
@Mapper
public interface ExternalIdentityMapper {

    ExternalIdentity selectByIssuerAndSubject(@Param("issuer") String issuer, @Param("subject") String subject);

    ExternalIdentity selectById(UUID id);

    List<ExternalIdentity> selectByUserId(UUID userId);

    int insert(ExternalIdentity identity);

    /**
     * 解绑外部身份（乐观锁）。
     *
     * @return 受影响行数：1=成功；0=版本冲突或不存在
     */
    int unbind(@Param("id") UUID id, @Param("expectedRevision") int expectedRevision);
}
