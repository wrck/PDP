package com.pdp.persistence.domainconfig.mapper;

import com.pdp.persistence.domainconfig.adapter.DomainPackageRow;
import com.pdp.domainconfig.port.DomainPackageQueryFilter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 领域包 MyBatis Mapper。
 *
 * <p>纯 MyBatis 接口（不继承 MyBatis-Plus {@code BaseMapper}），所有 SQL 在
 * {@code resources/mapper/domainconfig/DomainPackageMapper.xml} 中声明。
 * 领域层与应用层不感知此接口（由 {@link com.pdp.domainconfig.port.DomainPackageRepository}
 * 适配器实现隔离，符合宪章原则 V）。
 *
 * <p>游标分页：游标为 {@link com.pdp.persistence.domainconfig.adapter.DomainPackageCursorCodec}
 * 编码的 {@code UUIDv7 id}。
 */
@Mapper
public interface DomainPackageMapper {

    DomainPackageRow selectById(UUID id);

    DomainPackageRow selectByWorkspaceAndKey(@Param("workspaceId") UUID workspaceId,
                                              @Param("stableKey") String stableKey);

    List<DomainPackageRow> selectByParentPackage(@Param("parentPackageId") UUID parentPackageId,
                                                  @Param("lastId") UUID lastId,
                                                  @Param("size") int size);

    /**
     * 按过滤条件分页查询。
     *
     * @param filter 过滤条件（layer/status/parentPackageId 等可为 null）
     * @param lastId 上一页最后一条记录的 id；首页传 null
     * @param size   实际查询 size + 1 用于判断 hasMore
     */
    List<DomainPackageRow> selectByFilter(@Param("filter") DomainPackageQueryFilter filter,
                                           @Param("lastId") UUID lastId,
                                           @Param("size") int size);

    int insert(DomainPackageRow row);

    int updateBasicInfo(@Param("id") UUID id,
                        @Param("name") String name,
                        @Param("description") String description,
                        @Param("expectedRevision") int expectedRevision,
                        @Param("now") Instant now);

    int updateStatus(@Param("id") UUID id,
                     @Param("newStatus") com.pdp.domainconfig.domain.packageversion.DomainPackageStatus newStatus,
                     @Param("expectedRevision") int expectedRevision,
                     @Param("now") Instant now);

    int updateCurrentPublishedVersion(@Param("id") UUID id,
                                       @Param("currentPublishedVersionId") UUID currentPublishedVersionId,
                                       @Param("expectedRevision") int expectedRevision,
                                       @Param("now") Instant now);
}
