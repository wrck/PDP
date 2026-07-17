package com.pdp.persistence.workspace.mapper;

import com.pdp.persistence.workspace.adapter.WorkspaceCursorCodec;
import com.pdp.persistence.workspace.adapter.WorkspaceRoleRow;
import com.pdp.workspace.domain.DataScopeType;
import com.pdp.workspace.domain.RoleStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 工作空间角色 MyBatis Mapper。
 *
 * <p>纯 MyBatis 接口（不继承 MyBatis-Plus {@code BaseMapper}），所有 SQL 在
 * {@code resources/mapper/workspace/WorkspaceRoleMapper.xml} 中声明。
 *
 * <p>权限键集合以 JSON 文本列存储，通过 {@link WorkspaceRoleRow#permissionsJson()}
 * 返回字符串，由适配器装配 {@link com.pdp.workspace.domain.WorkspaceRole} 时反序列化。
 *
 * <p>游标分页：游标为 {@link WorkspaceCursorCodec} 编码的 {@code UUIDv7 id}。
 */
@Mapper
public interface WorkspaceRoleMapper {

    WorkspaceRoleRow selectById(UUID id);

    WorkspaceRoleRow selectByWorkspaceAndKey(@Param("workspaceId") UUID workspaceId,
                                              @Param("key") String key);

    /**
     * 按工作空间分页查询角色。
     *
     * @param includeSystem 是否包含系统角色（{@code is_system = true}）
     */
    List<WorkspaceRoleRow> selectByWorkspace(@Param("workspaceId") UUID workspaceId,
                                              @Param("includeSystem") boolean includeSystem,
                                              @Param("lastId") UUID lastId,
                                              @Param("size") int size);

    int insert(WorkspaceRoleRow row);

    /**
     * 更新角色名称、描述、权限与数据范围类型，并递增 revision。
     *
     * @return 受影响行数：1=成功；0=版本冲突或不存在
     */
    int update(@Param("id") UUID id,
               @Param("name") String name,
               @Param("description") String description,
               @Param("permissionsJson") String permissionsJson,
               @Param("dataScopeType") DataScopeType dataScopeType,
               @Param("expectedRevision") int expectedRevision,
               @Param("now") Instant now);

    /**
     * 更新角色状态并递增 revision。
     *
     * @return 受影响行数：1=成功；0=版本冲突或不存在
     */
    int updateStatus(@Param("id") UUID id,
                     @Param("newStatus") RoleStatus newStatus,
                     @Param("expectedRevision") int expectedRevision,
                     @Param("now") Instant now);
}
