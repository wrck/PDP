package com.pdp.persistence.domainconfig.mapper;

import com.pdp.persistence.domainconfig.adapter.CoreFieldReuseRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * 领域包核心字段复用声明 MyBatis Mapper（FR-134、SC-025）。
 *
 * <p>纯 MyBatis 接口，所有 SQL 在 {@code resources/mapper/domainconfig/DomainPackageCoreFieldReuseMapper.xml} 中声明。
 */
@Mapper
public interface DomainPackageCoreFieldReuseMapper {

    List<CoreFieldReuseRow> selectByVersion(@Param("versionId") UUID versionId);

    List<CoreFieldReuseRow> selectByCoreField(@Param("coreFieldKey") String coreFieldKey,
                                               @Param("coreObjectType") String coreObjectType);

    int insert(CoreFieldReuseRow row);

    int insertAll(@Param("rows") List<CoreFieldReuseRow> rows);

    int deleteByVersion(@Param("versionId") UUID versionId);
}
