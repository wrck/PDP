package com.pdp.persistence.domainconfig.mapper;

import com.pdp.domainconfig.domain.metamodel.CoreFieldSource;
import com.pdp.domainconfig.domain.metamodel.DataType;
import com.pdp.persistence.domainconfig.adapter.CoreFieldCatalogRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 核心字段目录 MyBatis Mapper（FR-132、SC-025）。
 *
 * <p>纯 MyBatis 接口，所有 SQL 在 {@code resources/mapper/domainconfig/CoreFieldCatalogMapper.xml} 中声明。
 */
@Mapper
public interface CoreFieldCatalogMapper {

    CoreFieldCatalogRow selectById(@Param("id") UUID id);

    CoreFieldCatalogRow selectByStableKeyAndObjectType(@Param("stableKey") String stableKey,
                                                        @Param("coreObjectType") String coreObjectType);

    List<CoreFieldCatalogRow> selectByObjectType(@Param("coreObjectType") String coreObjectType,
                                                  @Param("lastId") UUID lastId,
                                                  @Param("size") int size);

    List<CoreFieldCatalogRow> selectBySource(@Param("source") CoreFieldSource source,
                                              @Param("lastId") UUID lastId,
                                              @Param("size") int size);

    List<CoreFieldCatalogRow> selectAll(@Param("lastId") UUID lastId,
                                         @Param("size") int size);

    int insert(CoreFieldCatalogRow row);

    int update(@Param("id") UUID id,
               @Param("label") String label,
               @Param("semantics") String semantics,
               @Param("allowedOverride") boolean allowedOverride,
               @Param("aliasesJson") String aliasesJson,
               @Param("dataType") DataType dataType,
               @Param("expectedRevision") int expectedRevision,
               @Param("now") Instant now);
}
