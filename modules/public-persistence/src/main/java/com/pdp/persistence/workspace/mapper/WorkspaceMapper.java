package com.pdp.persistence.workspace.mapper;

import com.pdp.persistence.workspace.adapter.WorkspaceCursorCodec;
import com.pdp.workspace.domain.Workspace;
import com.pdp.workspace.domain.WorkspaceStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 工作空间 MyBatis Mapper。
 *
 * <p>纯 MyBatis 接口（不继承 MyBatis-Plus {@code BaseMapper}），所有 SQL 在
 * {@code resources/mapper/workspace/WorkspaceMapper.xml} 中声明。
 * 领域层与应用层不感知此接口（由 {@link com.pdp.workspace.port.WorkspaceRepository}
 * 适配器实现隔离，符合宪章原则 V）。
 *
 * <p>游标分页：游标为 {@link WorkspaceCursorCodec} 编码的 {@code UUIDv7 id}，
 * 续页查询使用 {@code WHERE id > #{lastId} ORDER BY id ASC LIMIT #{size} + 1}。
 * 多取一条用于判断是否还有下一页。
 */
@Mapper
public interface WorkspaceMapper {

    Workspace selectById(UUID id);

    Workspace selectByCode(String code);

    /**
     * 按负责人分页查询（游标分页）。
     *
     * @param ownerUserId 负责人 ID
     * @param lastId      上一页最后一条记录的 id；首页传 null
     * @param size        每页大小（实际查询 size + 1 用于判断 hasMore）
     */
    List<Workspace> selectByOwnerUserId(@Param("ownerUserId") UUID ownerUserId,
                                        @Param("lastId") UUID lastId,
                                        @Param("size") int size);

    int insert(Workspace workspace);

    /**
     * 更新基本信息并递增 revision。
     *
     * @return 受影响行数：1=成功；0=版本冲突或不存在
     */
    int updateBasicInfo(@Param("id") UUID id,
                        @Param("name") String name,
                        @Param("description") String description,
                        @Param("defaultLocale") String defaultLocale,
                        @Param("defaultTimezone") String defaultTimezone,
                        @Param("expectedRevision") int expectedRevision,
                        @Param("now") Instant now);

    /**
     * 更新状态并递增 revision。
     *
     * @return 受影响行数：1=成功；0=版本冲突或不存在
     */
    int updateStatus(@Param("id") UUID id,
                     @Param("newStatus") WorkspaceStatus newStatus,
                     @Param("expectedRevision") int expectedRevision,
                     @Param("now") Instant now);

    /**
     * 转移负责人并递增 revision。
     *
     * @return 受影响行数：1=成功；0=版本冲突或不存在
     */
    int transferOwner(@Param("id") UUID id,
                      @Param("newOwnerUserId") UUID newOwnerUserId,
                      @Param("expectedRevision") int expectedRevision,
                      @Param("now") Instant now);
}
