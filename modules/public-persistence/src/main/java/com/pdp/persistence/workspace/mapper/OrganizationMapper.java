package com.pdp.persistence.workspace.mapper;

import com.pdp.persistence.workspace.adapter.WorkspaceCursorCodec;
import com.pdp.workspace.domain.Organization;
import com.pdp.workspace.domain.OrganizationStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 组织 MyBatis Mapper。
 *
 * <p>纯 MyBatis 接口（不继承 MyBatis-Plus {@code BaseMapper}），所有 SQL 在
 * {@code resources/mapper/workspace/OrganizationMapper.xml} 中声明。
 *
 * <p>组织树通过 {@code (workspace_id, parent_id)} 索引支持父子查询，
 * 通过物化路径 {@code path} 与 {@code depth} 支持子树遍历。
 * 游标分页：游标为 {@link WorkspaceCursorCodec} 编码的 {@code UUIDv7 id}。
 */
@Mapper
public interface OrganizationMapper {

    Organization selectById(UUID id);

    /**
     * 按工作空间与父组织分页查询。
     *
     * @param workspaceId 工作空间 ID
     * @param parentId    父组织 ID；{@code null} 表示顶层组织（{@code parent_id IS NULL}）
     * @param lastId      上一页最后一条记录的 id；首页传 null
     * @param size        每页大小（实际查询 size + 1 用于判断 hasMore）
     */
    List<Organization> selectByWorkspaceAndParent(@Param("workspaceId") UUID workspaceId,
                                                   @Param("parentId") UUID parentId,
                                                   @Param("lastId") UUID lastId,
                                                   @Param("size") int size);

    int insert(Organization organization);

    /**
     * 更新基本信息并递增 revision。
     *
     * @return 受影响行数：1=成功；0=版本冲突或不存在
     */
    int updateBasicInfo(@Param("id") UUID id,
                        @Param("name") String name,
                        @Param("description") String description,
                        @Param("expectedRevision") int expectedRevision,
                        @Param("now") Instant now);

    /**
     * 更新层级路径、深度与父组织并递增 revision。
     *
     * @return 受影响行数：1=成功；0=版本冲突或不存在
     */
    int updatePath(@Param("id") UUID id,
                   @Param("newPath") String newPath,
                   @Param("newDepth") int newDepth,
                   @Param("newParentId") UUID newParentId,
                   @Param("expectedRevision") int expectedRevision,
                   @Param("now") Instant now);

    /**
     * 停用组织（{@link OrganizationStatus#ACTIVE} → {@link OrganizationStatus#INACTIVE}）并递增 revision。
     *
     * @return 受影响行数：1=成功；0=版本冲突或不存在
     */
    int deactivate(@Param("id") UUID id,
                  @Param("expectedRevision") int expectedRevision,
                  @Param("now") Instant now);
}
