package com.pdp.workspace.port;

import com.pdp.shared.page.PageRequest;
import com.pdp.shared.page.PageResult;
import com.pdp.workspace.domain.DataScope;
import com.pdp.workspace.domain.DataScopeRule;
import com.pdp.workspace.domain.DataScopeType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 数据范围仓储端口。
 *
 * <p>规则集合以 JSON 文档存储；删除前需确认无成员引用（由应用层校验）。
 */
public interface DataScopeRepository {

    Optional<DataScope> findById(UUID id);

    Optional<DataScope> findByKey(UUID workspaceId, String key);

    PageResult<DataScope> findByWorkspace(UUID workspaceId, PageRequest pageRequest);

    void save(DataScope dataScope);

    /**
     * 更新数据范围名称、描述、规则与类型，并递增 revision。
     *
     * @return {@code true} 成功；{@code false} 版本冲突或不存在
     */
    boolean update(UUID id, String name, String description, List<DataScopeRule> rules,
                   DataScopeType scopeType, int expectedRevision, Instant now);

    /**
     * 删除数据范围（乐观锁）。
     *
     * @return {@code true} 成功；{@code false} 版本冲突或不存在
     */
    boolean delete(UUID id, int expectedRevision);
}
