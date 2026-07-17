package com.pdp.persistence.workspace.adapter;

import com.pdp.persistence.workspace.mapper.WorkspaceMapper;
import com.pdp.shared.page.PageRequest;
import com.pdp.shared.page.PageResult;
import com.pdp.workspace.domain.Workspace;
import com.pdp.workspace.domain.WorkspaceStatus;
import com.pdp.workspace.port.WorkspaceRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 工作空间仓储适配器（MySQL 实现）。
 *
 * <p>实现 {@link WorkspaceRepository} 端口，委托 {@link WorkspaceMapper}；
 * 位于基础设施层 {@code com.pdp.persistence.workspace.adapter}，被领域/应用层通过端口消费。
 * 不使用 {@code @DS}，遵循默认 {@code pdpPrimary} 主库路由。
 *
 * <p>游标分页：游标为 {@link WorkspaceCursorCodec} 编码的 {@code UUIDv7 id}。
 * 查询时实际请求 size + 1 条记录；返回 size 条，多取的 1 条用于判断 {@code hasMore}，
 * 下一页游标基于当前页最后一条记录的 id 编码。
 *
 * <p>乐观锁：{@code update*} 方法通过 SQL {@code WHERE revision = #{expectedRevision}}
 * 与 {@code SET revision = revision + 1} 实现；返回 {@code false} 表示版本冲突或不存在，
 * 调用方抛出 {@code ConflictException}。
 */
@Repository
public class WorkspaceRepositoryImpl implements WorkspaceRepository {

    private final WorkspaceMapper mapper;

    public WorkspaceRepositoryImpl(WorkspaceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Workspace> findById(UUID id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    @Override
    public Optional<Workspace> findByCode(String code) {
        return Optional.ofNullable(mapper.selectByCode(code));
    }

    @Override
    public PageResult<Workspace> findByOwnerUserId(UUID ownerUserId, PageRequest pageRequest) {
        UUID lastId = WorkspaceCursorCodec.decode(pageRequest.cursor());
        int querySize = pageRequest.pageSize() + 1;
        List<Workspace> rows = mapper.selectByOwnerUserId(ownerUserId, lastId, querySize);
        boolean hasMore = rows.size() > pageRequest.pageSize();
        List<Workspace> page = hasMore ? rows.subList(0, pageRequest.pageSize()) : rows;
        String nextCursor = hasMore
                ? WorkspaceCursorCodec.encode(page.get(page.size() - 1).id())
                : null;
        return PageResult.of(page, nextCursor, hasMore);
    }

    @Override
    public void save(Workspace workspace) {
        int rows = mapper.insert(workspace);
        if (rows != 1) {
            throw new IllegalStateException("工作空间插入失败: " + workspace.id());
        }
    }

    @Override
    public boolean updateBasicInfo(UUID id, String name, String description,
                                    String defaultLocale, String defaultTimezone,
                                    int expectedRevision, Instant now) {
        return mapper.updateBasicInfo(id, name, description, defaultLocale, defaultTimezone,
                expectedRevision, now) == 1;
    }

    @Override
    public boolean updateStatus(UUID id, WorkspaceStatus newStatus, int expectedRevision, Instant now) {
        return mapper.updateStatus(id, newStatus, expectedRevision, now) == 1;
    }

    @Override
    public boolean transferOwner(UUID id, UUID newOwnerUserId, int expectedRevision, Instant now) {
        return mapper.transferOwner(id, newOwnerUserId, expectedRevision, now) == 1;
    }
}
