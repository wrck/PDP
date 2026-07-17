package com.pdp.persistence.workspace.mapper;

import com.pdp.persistence.workspace.adapter.WorkspaceCursorCodec;
import com.pdp.persistence.workspace.adapter.DataScopeRow;
import com.pdp.workspace.domain.DataScopeType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 数据范围 MyBatis Mapper。
 *
 * <p>纯 MyBatis 接口（不继承 MyBatis-Plus {@code BaseMapper}），所有 SQL 在
 * {@code resources/mapper/workspace/DataScopeMapper.xml} 中声明。
 *
 * <p>规则集合以 JSON 文本列存储，通过 {@link DataScopeRow#rulesJson()} 返回字符串，
 * 由适配器装配 {@link com.pdp.workspace.domain.DataScope} 时反序列化。
 *
 * <p>游标分页：游标为 {@link WorkspaceCursorCodec} 编码的 {@code UUIDv7 id}。
 */
@Mapper
public interface DataScopeMapper {

    DataScopeRow selectById(UUID id);

    DataScopeRow selectByWorkspaceAndKey(@Param("workspaceId") UUID workspaceId,
                                         @Param("key") String key);

    List<DataScopeRow> selectByWorkspace(@Param("workspaceId") UUID workspaceId,
                                         @Param("lastId") UUID lastId,
                                         @Param("size") int size);

    int insert(DataScopeRow row);

    /**
     * 更新数据范围名称、描述、规则与类型，并递增 revision。
     *
     * @return 受影响行数：1=成功；0=版本冲突或不存在
     */
    int update(@Param("id") UUID id,
               @Param("name") String name,
               @Param("description") String description,
               @Param("rulesJson") String rulesJson,
               @Param("scopeType") DataScopeType scopeType,
               @Param("expectedRevision") int expectedRevision,
               @Param("now") Instant now);

    /**
     * 删除数据范围（乐观锁）。
     *
     * @return 受影响行数：1=成功；0=版本冲突或不存在
     */
    int delete(@Param("id") UUID id,
               @Param("expectedRevision") int expectedRevision);
}
