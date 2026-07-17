package com.pdp.persistence.workspace.mapper;

import com.pdp.persistence.workspace.adapter.WorkspaceCursorCodec;
import com.pdp.persistence.workspace.adapter.WorkspaceMemberRow;
import com.pdp.workspace.domain.MemberStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 工作空间成员 MyBatis Mapper。
 *
 * <p>纯 MyBatis 接口（不继承 MyBatis-Plus {@code BaseMapper}），所有 SQL 在
 * {@code resources/mapper/workspace/WorkspaceMemberMapper.xml} 中声明。
 *
 * <p>成员标量字段以 {@link WorkspaceMemberRow} 返回；角色与数据范围关联由独立 SQL
 * 加载/维护（{@link #selectRoleIds}/{@link #selectDataScopeIds} 等），避免在 record
 * 构造器上映射集合参数。
 *
 * <p>游标分页：游标为 {@link WorkspaceCursorCodec} 编码的 {@code UUIDv7 id}。
 * FR-068：{@link #revokeAllByUser} 单条 UPDATE 原子完成，立即撤销该用户在此空间的全部成员记录。
 */
@Mapper
public interface WorkspaceMemberMapper {

    WorkspaceMemberRow selectById(UUID id);

    WorkspaceMemberRow selectByWorkspaceAndUser(@Param("workspaceId") UUID workspaceId,
                                                 @Param("userId") UUID userId);

    /**
     * 按工作空间分页查询成员（可按组织、角色、状态过滤）。
     *
     * <p>角色过滤 {@code roleId} 通过 EXISTS 子查询命中 {@code workspace_member_role} 关联表。
     *
     * @param workspaceId 工作空间 ID
     * @param organizationId 组织归属过滤；{@code null} 不过滤
     * @param roleId         角色过滤；{@code null} 不过滤
     * @param status         状态过滤；{@code null} 不过滤
     * @param lastId         上一页最后一条记录的 id；首页传 null
     * @param size           每页大小（实际查询 size + 1 用于判断 hasMore）
     */
    List<WorkspaceMemberRow> selectByWorkspace(@Param("workspaceId") UUID workspaceId,
                                               @Param("organizationId") UUID organizationId,
                                               @Param("roleId") UUID roleId,
                                               @Param("status") MemberStatus status,
                                               @Param("lastId") UUID lastId,
                                               @Param("size") int size);

    int insert(WorkspaceMemberRow row);

    /**
     * 更新成员状态并递增 revision。
     *
     * @return 受影响行数：1=成功；0=版本冲突或不存在
     */
    int updateStatus(@Param("id") UUID id,
                     @Param("newStatus") MemberStatus newStatus,
                     @Param("expectedRevision") int expectedRevision,
                     @Param("now") Instant now);

    /**
     * 更新成员的角色、组织归属、数据范围与有效期，并递增 revision。
     * 关联表（{@code workspace_member_role}、{@code workspace_member_data_scope}）
     * 由适配器在调用本方法前后同步维护：先 DELETE 旧关联，再批量 INSERT 新关联。
     *
     * @return 受影响行数：1=成功；0=版本冲突或不存在
     */
    int updateAssociations(@Param("id") UUID id,
                           @Param("organizationId") UUID organizationId,
                           @Param("validUntil") Instant validUntil,
                           @Param("expectedRevision") int expectedRevision,
                           @Param("now") Instant now);

    /**
     * 删除成员的全部角色关联。
     */
    int deleteRoleAssociations(@Param("memberId") UUID memberId);

    /**
     * 删除成员的全部数据范围关联。
     */
    int deleteDataScopeAssociations(@Param("memberId") UUID memberId);

    /**
     * 批量插入成员-角色关联。
     */
    int insertRoleAssociations(@Param("memberId") UUID memberId,
                              @Param("roleIds") List<UUID> roleIds);

    /**
     * 批量插入成员-数据范围关联。
     */
    int insertDataScopeAssociations(@Param("memberId") UUID memberId,
                                   @Param("scopeIds") List<UUID> scopeIds);

    /** 加载成员的角色 ID 列表。 */
    List<UUID> selectRoleIds(@Param("memberId") UUID memberId);

    /** 加载成员的数据范围 ID 列表。 */
    List<UUID> selectDataScopeIds(@Param("memberId") UUID memberId);

    /**
     * 批量撤销指定用户的全部 ACTIVE/SUSPENDED 成员记录（FR-068 即时撤权）。
     * 单条 UPDATE 原子完成；REMOVED 为终态不可逆。
     *
     * @param userId 用户 ID
     * @param reason 撤权原因
     * @param now    当前时间
     * @return 受影响行数
     */
    int revokeAllByUser(@Param("userId") UUID userId,
                        @Param("reason") String reason,
                        @Param("now") Instant now);
}
