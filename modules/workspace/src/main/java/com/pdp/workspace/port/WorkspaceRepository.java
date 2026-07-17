package com.pdp.workspace.port;

import com.pdp.shared.page.PageRequest;
import com.pdp.shared.page.PageResult;
import com.pdp.workspace.domain.Workspace;
import com.pdp.workspace.domain.WorkspaceStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 工作空间仓储端口。
 *
 * <p>领域/应用层依赖此端口，不依赖 MyBatis 或 MySQL 专有实现。
 * 乐观锁更新方法返回 {@code false} 表示版本冲突或不存在，调用方抛出 {@code ConflictException}。
 */
public interface WorkspaceRepository {

    Optional<Workspace> findById(UUID id);

    Optional<Workspace> findByCode(String code);

    /** 按负责人分页查询（游标分页）。 */
    PageResult<Workspace> findByOwnerUserId(UUID ownerUserId, PageRequest pageRequest);

    void save(Workspace workspace);

    /**
     * 更新基本信息（名称、描述、默认语言/时区）并递增 revision。
     *
     * @return {@code true} 成功；{@code false} 版本冲突或不存在
     */
    boolean updateBasicInfo(UUID id, String name, String description,
                            String defaultLocale, String defaultTimezone,
                            int expectedRevision, Instant now);

    /**
     * 更新状态并递增 revision。
     *
     * @return {@code true} 成功；{@code false} 版本冲突或不存在
     */
    boolean updateStatus(UUID id, WorkspaceStatus newStatus, int expectedRevision, Instant now);

    /**
     * 转移负责人并递增 revision。
     *
     * @return {@code true} 成功；{@code false} 版本冲突或不存在
     */
    boolean transferOwner(UUID id, UUID newOwnerUserId, int expectedRevision, Instant now);
}
