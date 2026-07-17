package com.pdp.persistence.workspace.mapper;

import com.pdp.persistence.workspace.adapter.CollaborationGrantRow;
import com.pdp.persistence.workspace.adapter.WorkspaceCursorCodec;
import com.pdp.workspace.domain.GrantStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 跨工作空间协作授权 MyBatis Mapper（FR-006）。
 *
 * <p>纯 MyBatis 接口（不继承 MyBatis-Plus {@code BaseMapper}），所有 SQL 在
 * {@code resources/mapper/workspace/CollaborationGrantMapper.xml} 中声明。
 *
 * <p>允许动作列表以 JSON 文本列存储，通过 {@link CollaborationGrantRow#allowedActionsJson()}
 * 返回字符串，由适配器装配 {@link com.pdp.workspace.domain.CollaborationGrant} 时反序列化。
 *
 * <p>授权方向由应用层 {@link com.pdp.workspace.port.GrantDirection} 决定查询列：
 * {@link com.pdp.workspace.port.GrantDirection#OUTGOING} 按 {@code workspace_id}（授权方）查询；
 * {@link com.pdp.workspace.port.GrantDirection#INCOMING} 按 {@code collaborator_workspace_id}（被授权方）查询。
 *
 * <p>游标分页：游标为 {@link WorkspaceCursorCodec} 编码的 {@code UUIDv7 id}。
 */
@Mapper
public interface CollaborationGrantMapper {

    CollaborationGrantRow selectById(UUID id);

    /**
     * 按工作空间分页查询授权。
     *
     * @param workspaceId 工作空间 ID
     * @param directionColumn 查询方向列名：{@code workspace_id} 或 {@code collaborator_workspace_id}
     * @param status      状态过滤；{@code null} 表示不过滤
     * @param lastId      上一页最后一条记录的 id；首页传 null
     * @param size        每页大小（实际查询 size + 1 用于判断 hasMore）
     */
    List<CollaborationGrantRow> selectByWorkspace(@Param("workspaceId") UUID workspaceId,
                                                  @Param("directionColumn") String directionColumn,
                                                  @Param("status") GrantStatus status,
                                                  @Param("lastId") UUID lastId,
                                                  @Param("size") int size);

    int insert(CollaborationGrantRow row);

    /**
     * 撤销授权（{@link GrantStatus#ACTIVE} → {@link GrantStatus#REVOKED}），
     * 记录撤销原因与时间，并递增 revision。
     *
     * @return 受影响行数：1=成功；0=版本冲突或不存在
     */
    int revoke(@Param("id") UUID id,
               @Param("reason") String reason,
               @Param("now") Instant now,
               @Param("expectedRevision") int expectedRevision);

    /**
     * 将所有已过有效期（{@code valid_until < now}）的 ACTIVE 授权迁移为 EXPIRED。
     *
     * @param now 当前时间
     * @return 受影响行数
     */
    int expireDue(@Param("now") Instant now);
}
