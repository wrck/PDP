package com.pdp.persistence.workspace.adapter;

import com.pdp.persistence.workspace.mapper.OrganizationMapper;
import com.pdp.shared.page.PageRequest;
import com.pdp.shared.page.PageResult;
import com.pdp.workspace.domain.Organization;
import com.pdp.workspace.port.OrganizationRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 组织仓储适配器（MySQL 实现）。
 *
 * <p>实现 {@link OrganizationRepository} 端口，委托 {@link OrganizationMapper}；
 * 位于基础设施层 {@code com.pdp.persistence.workspace.adapter}，被领域/应用层通过端口消费。
 * 不使用 {@code @DS}，遵循默认 {@code pdpPrimary} 主库路由。
 *
 * <p>游标分页：游标为 {@link WorkspaceCursorCodec} 编码的 {@code UUIDv7 id}。
 * 查询时实际请求 size + 1 条；返回 size 条，多取的 1 条用于判断 {@code hasMore}。
 */
@Repository
public class OrganizationRepositoryImpl implements OrganizationRepository {

    private final OrganizationMapper mapper;

    public OrganizationRepositoryImpl(OrganizationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Organization> findById(UUID id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    @Override
    public PageResult<Organization> findByWorkspaceAndParent(UUID workspaceId, UUID parentId,
                                                              PageRequest pageRequest) {
        UUID lastId = WorkspaceCursorCodec.decode(pageRequest.cursor());
        int querySize = pageRequest.pageSize() + 1;
        List<Organization> rows = mapper.selectByWorkspaceAndParent(
                workspaceId, parentId, lastId, querySize);
        boolean hasMore = rows.size() > pageRequest.pageSize();
        List<Organization> page = hasMore ? rows.subList(0, pageRequest.pageSize()) : rows;
        String nextCursor = hasMore
                ? WorkspaceCursorCodec.encode(page.get(page.size() - 1).id())
                : null;
        return PageResult.of(page, nextCursor, hasMore);
    }

    @Override
    public void save(Organization organization) {
        int rows = mapper.insert(organization);
        if (rows != 1) {
            throw new IllegalStateException("组织插入失败: " + organization.id());
        }
    }

    @Override
    public boolean updateBasicInfo(UUID id, String name, String description,
                                    int expectedRevision, Instant now) {
        return mapper.updateBasicInfo(id, name, description, expectedRevision, now) == 1;
    }

    @Override
    public boolean updatePath(UUID id, String newPath, int newDepth, UUID newParentId,
                              int expectedRevision, Instant now) {
        return mapper.updatePath(id, newPath, newDepth, newParentId, expectedRevision, now) == 1;
    }

    @Override
    public boolean deactivate(UUID id, int expectedRevision, Instant now) {
        return mapper.deactivate(id, expectedRevision, now) == 1;
    }
}
