package com.pdp.persistence.workspace.adapter;

import com.pdp.persistence.workspace.mapper.DataScopeMapper;
import com.pdp.shared.page.PageRequest;
import com.pdp.shared.page.PageResult;
import com.pdp.workspace.domain.DataScope;
import com.pdp.workspace.domain.DataScopeRule;
import com.pdp.workspace.domain.DataScopeType;
import com.pdp.workspace.port.DataScopeRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 数据范围仓储适配器（MySQL 实现）。
 *
 * <p>实现 {@link DataScopeRepository} 端口，委托 {@link DataScopeMapper}；
 * 位于基础设施层 {@code com.pdp.persistence.workspace.adapter}，被领域/应用层通过端口消费。
 * 不使用 {@code @DS}，遵循默认 {@code pdpPrimary} 主库路由。
 *
 * <p>规则集合以 JSON 文本列存储；适配器在装配 {@link DataScope} 时通过
 * {@link WorkspaceJsonCodec#readRuleList(String)} 反序列化，在持久化时通过
 * {@link WorkspaceJsonCodec#writeRuleList(java.util.List)} 序列化。
 *
 * <p>游标分页：游标为 {@link WorkspaceCursorCodec} 编码的 {@code UUIDv7 id}。
 *
 * <p>删除前需确认无成员引用（由应用层 {@code WorkspaceGovernanceService} 校验，
 * 通过查询 {@code workspace_member_data_scope} 关联表判断）。
 */
@Repository
public class DataScopeRepositoryImpl implements DataScopeRepository {

    private final DataScopeMapper mapper;

    public DataScopeRepositoryImpl(DataScopeMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<DataScope> findById(UUID id) {
        DataScopeRow row = mapper.selectById(id);
        return row == null ? Optional.empty() : Optional.of(assemble(row));
    }

    @Override
    public Optional<DataScope> findByKey(UUID workspaceId, String key) {
        DataScopeRow row = mapper.selectByWorkspaceAndKey(workspaceId, key);
        return row == null ? Optional.empty() : Optional.of(assemble(row));
    }

    @Override
    public PageResult<DataScope> findByWorkspace(UUID workspaceId, PageRequest pageRequest) {
        UUID lastId = WorkspaceCursorCodec.decode(pageRequest.cursor());
        int querySize = pageRequest.pageSize() + 1;
        List<DataScopeRow> rows = mapper.selectByWorkspace(workspaceId, lastId, querySize);
        boolean hasMore = rows.size() > pageRequest.pageSize();
        List<DataScopeRow> pageRows = hasMore ? rows.subList(0, pageRequest.pageSize()) : rows;
        List<DataScope> page = new ArrayList<>(pageRows.size());
        for (DataScopeRow row : pageRows) {
            page.add(assemble(row));
        }
        String nextCursor = hasMore
                ? WorkspaceCursorCodec.encode(page.get(page.size() - 1).id())
                : null;
        return PageResult.of(page, nextCursor, hasMore);
    }

    @Override
    public void save(DataScope dataScope) {
        DataScopeRow row = new DataScopeRow(
                dataScope.id(),
                dataScope.workspaceId(),
                dataScope.key(),
                dataScope.name(),
                dataScope.description(),
                dataScope.scopeType(),
                WorkspaceJsonCodec.writeRuleList(dataScope.rules()),
                dataScope.revision(),
                dataScope.createdAt(),
                dataScope.updatedAt());
        int rows = mapper.insert(row);
        if (rows != 1) {
            throw new IllegalStateException("数据范围插入失败: " + dataScope.id());
        }
    }

    @Override
    public boolean update(UUID id, String name, String description, List<DataScopeRule> rules,
                          DataScopeType scopeType, int expectedRevision, Instant now) {
        String rulesJson = WorkspaceJsonCodec.writeRuleList(rules);
        return mapper.update(id, name, description, rulesJson, scopeType, expectedRevision, now) == 1;
    }

    @Override
    public boolean delete(UUID id, int expectedRevision) {
        return mapper.delete(id, expectedRevision) == 1;
    }

    // ============================================================
    // 私有辅助方法
    // ============================================================

    /**
     * 装配 {@link DataScope}：从行加载标量字段 + 反序列化规则集合。
     */
    private DataScope assemble(DataScopeRow row) {
        return new DataScope(
                row.id(),
                row.workspaceId(),
                row.key(),
                row.name(),
                row.description(),
                row.scopeType(),
                WorkspaceJsonCodec.readRuleList(row.rulesJson()),
                row.revision(),
                row.createdAt(),
                row.updatedAt());
    }
}
