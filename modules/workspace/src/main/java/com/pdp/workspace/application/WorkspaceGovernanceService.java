package com.pdp.workspace.application;

import com.pdp.shared.error.BusinessRuleException;
import com.pdp.shared.error.ConflictException;
import com.pdp.shared.error.ErrorCode;
import com.pdp.shared.error.ResourceNotFoundException;
import com.pdp.shared.id.UuidV7Generator;
import com.pdp.shared.page.PageRequest;
import com.pdp.shared.page.PageResult;
import com.pdp.workspace.domain.DataScope;
import com.pdp.workspace.domain.DataScopeRule;
import com.pdp.workspace.domain.DataScopeType;
import com.pdp.workspace.domain.MemberStatus;
import com.pdp.workspace.domain.Organization;
import com.pdp.workspace.domain.OrganizationStatus;
import com.pdp.workspace.domain.RoleStatus;
import com.pdp.workspace.domain.Workspace;
import com.pdp.workspace.domain.WorkspaceMember;
import com.pdp.workspace.domain.WorkspaceRole;
import com.pdp.workspace.domain.WorkspaceStatus;
import com.pdp.workspace.port.DataScopeRepository;
import com.pdp.workspace.port.MemberQueryFilter;
import com.pdp.workspace.port.OrganizationRepository;
import com.pdp.workspace.port.WorkspaceMemberRepository;
import com.pdp.workspace.port.WorkspaceRepository;
import com.pdp.workspace.port.WorkspaceRoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 工作空间治理应用服务（FR-003、FR-004、FR-005、FR-063、FR-068）。
 *
 * <p>聚合工作空间生命周期、组织树、成员管理、角色管理、数据范围管理五个子域的应用逻辑。
 * 所有写操作通过端口适配器在 {@code pdpPrimary} 主库事务内执行，禁止事务内切换数据源。
 *
 * <p><strong>乐观锁</strong>：所有 {@code update*} 方法接收 {@code expectedRevision} 参数，
 * 通过 SQL {@code WHERE revision = #{expectedRevision} SET revision = revision + 1} 实现；
 * 返回 {@code false} 时抛 {@link ConflictException}（HTTP 409），携带当前状态与版本供客户端重试。
 *
 * <p><strong>状态机</strong>：状态迁移前置条件由领域记录的 {@code canXxx()} 方法校验；
 * 非法迁移抛 {@link BusinessRuleException}（HTTP 422）。
 *
 * <p><strong>FR-068 撤权 SLA</strong>：移除/暂停成员通过单条 UPDATE 原子完成（数据库层即时生效）；
 * 后续会话拒绝由工作空间边界过滤器在下次请求时校验。跨模块的会话 token 撤销由
 * {@link com.pdp.workspace.port.WorkspaceMemberRepository#revokeAllByUser} 端口供身份模块
 * 离职流程协调调用（不在本服务范围内）。
 *
 * <p><strong>工作空间边界</strong>：组织、成员、角色、数据范围查询均按 {@code workspaceId} 隔离，
 * 跨空间访问统一返回 {@link ResourceNotFoundException}（404），不泄露存在性。
 */
@Service
public class WorkspaceGovernanceService {

    private final WorkspaceRepository workspaceRepository;
    private final OrganizationRepository organizationRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final WorkspaceRoleRepository roleRepository;
    private final DataScopeRepository dataScopeRepository;

    public WorkspaceGovernanceService(
            WorkspaceRepository workspaceRepository,
            OrganizationRepository organizationRepository,
            WorkspaceMemberRepository memberRepository,
            WorkspaceRoleRepository roleRepository,
            DataScopeRepository dataScopeRepository) {
        this.workspaceRepository = workspaceRepository;
        this.organizationRepository = organizationRepository;
        this.memberRepository = memberRepository;
        this.roleRepository = roleRepository;
        this.dataScopeRepository = dataScopeRepository;
    }

    // ============================================================
    // 工作空间生命周期（FR-003）
    // ============================================================

    /**
     * 创建工作空间。
     *
     * <p>初始状态 {@code DRAFT}；code 全局唯一；同一 code 已存在抛 {@link ConflictException}。
     */
    @Transactional
    public Workspace createWorkspace(String code, String name, String description,
                                     UUID ownerUserId, String defaultLocale, String defaultTimezone) {
        if (workspaceRepository.findByCode(code).isPresent()) {
            throw new ConflictException("工作空间 code 已存在: " + code);
        }
        Instant now = Instant.now();
        Workspace workspace = new Workspace(
                UuidV7Generator.next(),
                code,
                name,
                description,
                WorkspaceStatus.DRAFT,
                ownerUserId,
                defaultLocale,
                defaultTimezone,
                1,
                now,
                now);
        workspaceRepository.save(workspace);
        return workspace;
    }

    /** 按负责人分页查询工作空间（游标分页）。 */
    public PageResult<Workspace> listWorkspacesByOwner(UUID ownerUserId, PageRequest pageRequest) {
        return workspaceRepository.findByOwnerUserId(ownerUserId, pageRequest);
    }

    /** 查询工作空间详情。 */
    public Workspace getWorkspace(UUID workspaceId) {
        return loadWorkspace(workspaceId);
    }

    /** 更新基本信息：名称、描述、默认语言/时区（乐观锁）。 */
    @Transactional
    public Workspace updateWorkspaceBasicInfo(UUID workspaceId, String name, String description,
                                              String defaultLocale, String defaultTimezone,
                                              int expectedRevision) {
        Workspace workspace = loadWorkspace(workspaceId);
        if (!workspaceRepository.updateBasicInfo(workspaceId, name, description,
                defaultLocale, defaultTimezone, expectedRevision, Instant.now())) {
            throw conflict("Workspace", workspaceId, workspace.status().name(), expectedRevision);
        }
        return loadWorkspace(workspaceId);
    }

    /** 激活工作空间（DRAFT/SUSPENDED → ACTIVE，乐观锁）。 */
    @Transactional
    public Workspace activateWorkspace(UUID workspaceId, int expectedRevision) {
        Workspace workspace = loadWorkspace(workspaceId);
        if (!workspace.canActivate()) {
            throw new BusinessRuleException(ErrorCode.BUSINESS_RULE_VIOLATED,
                    "工作空间当前状态不可激活: " + workspace.status());
        }
        if (!workspaceRepository.updateStatus(workspaceId, WorkspaceStatus.ACTIVE,
                expectedRevision, Instant.now())) {
            throw conflict("Workspace", workspaceId, workspace.status().name(), expectedRevision);
        }
        return loadWorkspace(workspaceId);
    }

    /** 暂停工作空间（ACTIVE → SUSPENDED，乐观锁）。 */
    @Transactional
    public Workspace suspendWorkspace(UUID workspaceId, int expectedRevision, String reason) {
        Workspace workspace = loadWorkspace(workspaceId);
        if (!workspace.canSuspend()) {
            throw new BusinessRuleException(ErrorCode.BUSINESS_RULE_VIOLATED,
                    "工作空间当前状态不可暂停: " + workspace.status());
        }
        if (!workspaceRepository.updateStatus(workspaceId, WorkspaceStatus.SUSPENDED,
                expectedRevision, Instant.now())) {
            throw conflict("Workspace", workspaceId, workspace.status().name(), expectedRevision);
        }
        return loadWorkspace(workspaceId);
    }

    /** 归档工作空间（ACTIVE/SUSPENDED → ARCHIVED，乐观锁）。 */
    @Transactional
    public Workspace archiveWorkspace(UUID workspaceId, int expectedRevision, String reason) {
        Workspace workspace = loadWorkspace(workspaceId);
        if (!workspace.canArchive()) {
            throw new BusinessRuleException(ErrorCode.BUSINESS_RULE_VIOLATED,
                    "工作空间当前状态不可归档: " + workspace.status());
        }
        if (!workspaceRepository.updateStatus(workspaceId, WorkspaceStatus.ARCHIVED,
                expectedRevision, Instant.now())) {
            throw conflict("Workspace", workspaceId, workspace.status().name(), expectedRevision);
        }
        return loadWorkspace(workspaceId);
    }

    /** 恢复归档工作空间（ARCHIVED → SUSPENDED，乐观锁）。 */
    @Transactional
    public Workspace restoreWorkspace(UUID workspaceId, int expectedRevision) {
        Workspace workspace = loadWorkspace(workspaceId);
        if (!workspace.canRestore()) {
            throw new BusinessRuleException(ErrorCode.BUSINESS_RULE_VIOLATED,
                    "工作空间当前状态不可恢复: " + workspace.status());
        }
        if (!workspaceRepository.updateStatus(workspaceId, WorkspaceStatus.SUSPENDED,
                expectedRevision, Instant.now())) {
            throw conflict("Workspace", workspaceId, workspace.status().name(), expectedRevision);
        }
        return loadWorkspace(workspaceId);
    }

    /** 转移负责人（乐观锁）。 */
    @Transactional
    public Workspace transferOwner(UUID workspaceId, UUID newOwnerUserId, String reason,
                                   int expectedRevision) {
        Workspace workspace = loadWorkspace(workspaceId);
        if (workspace.ownerUserId().equals(newOwnerUserId)) {
            throw new BusinessRuleException("新负责人不能与原负责人相同");
        }
        if (!workspaceRepository.transferOwner(workspaceId, newOwnerUserId,
                expectedRevision, Instant.now())) {
            throw conflict("Workspace", workspaceId, workspace.status().name(), expectedRevision);
        }
        return loadWorkspace(workspaceId);
    }

    // ============================================================
    // 组织管理（FR-004）
    // ============================================================

    /**
     * 创建组织。
     *
     * <p>计算物化路径与深度：根组织 {@code /ROOT} depth=0；子组织 {@code /<parent_path>/<code>} depth=parent.depth+1。
     * 工作空间内 code 唯一；同一 (workspace, code) 已存在抛 {@link ConflictException}。
     */
    @Transactional
    public Organization createOrganization(UUID workspaceId, String code, String name,
                                           String description, UUID parentId) {
        loadWorkspace(workspaceId);
        if (parentId != null) {
            Organization parent = loadOrganizationInWorkspace(parentId, workspaceId);
            if (!parent.isActive()) {
                throw new BusinessRuleException("父组织已停用，不能创建子组织");
            }
        }
        String path = buildPath(workspaceId, code, parentId);
        int depth = computeDepth(parentId, workspaceId);
        Instant now = Instant.now();
        Organization organization = new Organization(
                UuidV7Generator.next(),
                workspaceId,
                code,
                name,
                description,
                parentId,
                path,
                depth,
                OrganizationStatus.ACTIVE,
                1,
                now,
                now);
        organizationRepository.save(organization);
        return organization;
    }

    /** 查询组织树（按父组织分页）。parentId 为 null 表示顶层组织。 */
    public PageResult<Organization> listOrganizations(UUID workspaceId, UUID parentId,
                                                       PageRequest pageRequest) {
        loadWorkspace(workspaceId);
        return organizationRepository.findByWorkspaceAndParent(workspaceId, parentId, pageRequest);
    }

    /** 查询组织详情（按工作空间边界校验）。 */
    public Organization getOrganization(UUID workspaceId, UUID organizationId) {
        return loadOrganizationInWorkspace(organizationId, workspaceId);
    }

    /** 更新组织基本信息（乐观锁）。 */
    @Transactional
    public Organization updateOrganization(UUID workspaceId, UUID organizationId,
                                          String name, String description, int expectedRevision) {
        Organization organization = loadOrganizationInWorkspace(organizationId, workspaceId);
        if (!organizationRepository.updateBasicInfo(organizationId, name, description,
                expectedRevision, Instant.now())) {
            throw conflict("Organization", organizationId, organization.status().name(), expectedRevision);
        }
        return loadOrganization(organizationId);
    }

    /**
     * 调整组织层级（移动到新父组织下）。
     *
     * <p>更新自身的 path、depth、parent_id（乐观锁）。子组织路径调整（递归重写）由后台作业处理，
     * 不在事务内同步完成以避免大子树长事务。
     */
    @Transactional
    public Organization moveOrganization(UUID workspaceId, UUID organizationId,
                                         UUID newParentId, int expectedRevision) {
        Organization organization = loadOrganizationInWorkspace(organizationId, workspaceId);
        if (newParentId != null) {
            if (newParentId.equals(organizationId)) {
                throw new BusinessRuleException("不能将组织移动到自身下");
            }
            Organization newParent = loadOrganizationInWorkspace(newParentId, workspaceId);
            if (!newParent.isActive()) {
                throw new BusinessRuleException("新父组织已停用，不能移动到其下");
            }
            if (isAncestor(newParentId, organizationId, workspaceId)) {
                throw new BusinessRuleException("不能将组织移动到其子组织下（避免循环）");
            }
        }
        String newPath = buildPath(workspaceId, organization.code(), newParentId);
        int newDepth = computeDepth(newParentId, workspaceId);
        if (!organizationRepository.updatePath(organizationId, newPath, newDepth, newParentId,
                expectedRevision, Instant.now())) {
            throw conflict("Organization", organizationId, organization.status().name(), expectedRevision);
        }
        return loadOrganization(organizationId);
    }

    /** 停用组织（软删除，保留子组织和历史，乐观锁）。 */
    @Transactional
    public void deactivateOrganization(UUID workspaceId, UUID organizationId, int expectedRevision) {
        Organization organization = loadOrganizationInWorkspace(organizationId, workspaceId);
        if (!organization.isActive()) {
            throw new BusinessRuleException("组织已停用，无需重复操作");
        }
        if (!organizationRepository.deactivate(organizationId, expectedRevision, Instant.now())) {
            throw conflict("Organization", organizationId, organization.status().name(), expectedRevision);
        }
    }

    // ============================================================
    // 成员管理（FR-005、FR-068）
    // ============================================================

    /**
     * 添加成员。
     *
     * <p>校验工作空间内 (workspaceId, userId) 唯一；校验角色 ID 存在且属于同一工作空间；
     * 校验数据范围 ID 存在且属于同一工作空间；校验组织归属属于同一工作空间。
     * 初始状态 {@code ACTIVE}。
     */
    @Transactional
    public WorkspaceMember addMember(UUID workspaceId, UUID userId, List<UUID> roleIds,
                                     UUID organizationId, List<UUID> dataScopeIds,
                                     Instant validUntil) {
        loadWorkspace(workspaceId);
        if (memberRepository.findByWorkspaceAndUser(workspaceId, userId).isPresent()) {
            throw new ConflictException("成员已存在: workspaceId=" + workspaceId + ", userId=" + userId);
        }
        validateRoles(workspaceId, roleIds);
        validateDataScopes(workspaceId, dataScopeIds);
        if (organizationId != null) {
            loadOrganizationInWorkspace(organizationId, workspaceId);
        }
        Instant now = Instant.now();
        WorkspaceMember member = new WorkspaceMember(
                UuidV7Generator.next(),
                workspaceId,
                userId,
                organizationId,
                roleIds,
                dataScopeIds,
                MemberStatus.ACTIVE,
                validUntil,
                1,
                now,
                now);
        memberRepository.save(member);
        return member;
    }

    /** 按工作空间分页查询成员（可按组织、角色、状态过滤）。 */
    public PageResult<WorkspaceMember> listMembers(UUID workspaceId, MemberQueryFilter filter,
                                                    PageRequest pageRequest) {
        loadWorkspace(workspaceId);
        return memberRepository.findByWorkspace(workspaceId,
                filter == null ? MemberQueryFilter.empty() : filter, pageRequest);
    }

    /** 查询成员详情（按工作空间边界校验）。 */
    public WorkspaceMember getMember(UUID workspaceId, UUID memberId) {
        return loadMemberInWorkspace(memberId, workspaceId);
    }

    /** 更新成员（角色、组织归属、数据范围、有效期，乐观锁）。 */
    @Transactional
    public WorkspaceMember updateMember(UUID workspaceId, UUID memberId, List<UUID> roleIds,
                                        UUID organizationId, List<UUID> dataScopeIds,
                                        Instant validUntil, int expectedRevision) {
        WorkspaceMember member = loadMemberInWorkspace(memberId, workspaceId);
        validateRoles(workspaceId, roleIds);
        validateDataScopes(workspaceId, dataScopeIds);
        if (organizationId != null) {
            loadOrganizationInWorkspace(organizationId, workspaceId);
        }
        if (!memberRepository.update(memberId, roleIds, organizationId, dataScopeIds,
                validUntil, expectedRevision, Instant.now())) {
            throw conflict("WorkspaceMember", memberId, member.status().name(), expectedRevision);
        }
        return loadMember(memberId);
    }

    /**
     * 暂停成员（ACTIVE → SUSPENDED，乐观锁）。
     *
     * <p>FR-068：单条 UPDATE 原子完成；后续会话拒绝由工作空间边界过滤器在下次请求时校验。
     */
    @Transactional
    public WorkspaceMember suspendMember(UUID workspaceId, UUID memberId,
                                         String reason, int expectedRevision) {
        WorkspaceMember member = loadMemberInWorkspace(memberId, workspaceId);
        if (!member.canSuspend()) {
            throw new BusinessRuleException(ErrorCode.BUSINESS_RULE_VIOLATED,
                    "成员当前状态不可暂停: " + member.status());
        }
        if (!memberRepository.updateStatus(memberId, MemberStatus.SUSPENDED,
                expectedRevision, Instant.now())) {
            throw conflict("WorkspaceMember", memberId, member.status().name(), expectedRevision);
        }
        return loadMember(memberId);
    }

    /** 恢复成员（SUSPENDED → ACTIVE，乐观锁）。 */
    @Transactional
    public WorkspaceMember resumeMember(UUID workspaceId, UUID memberId, int expectedRevision) {
        WorkspaceMember member = loadMemberInWorkspace(memberId, workspaceId);
        if (!member.canResume()) {
            throw new BusinessRuleException(ErrorCode.BUSINESS_RULE_VIOLATED,
                    "成员当前状态不可恢复: " + member.status());
        }
        if (!memberRepository.updateStatus(memberId, MemberStatus.ACTIVE,
                expectedRevision, Instant.now())) {
            throw conflict("WorkspaceMember", memberId, member.status().name(), expectedRevision);
        }
        return loadMember(memberId);
    }

    /**
     * 移除成员（ACTIVE/SUSPENDED → REMOVED，乐观锁）。
     *
     * <p>FR-068：单条 UPDATE 原子完成；REMOVED 为终态不可逆。成员的角色与数据范围关联由适配器同步清理。
     * 跨工作空间的用户级会话撤销不在本服务范围；如需完全下线，调用方应额外协调身份模块。
     */
    @Transactional
    public void removeMember(UUID workspaceId, UUID memberId, int expectedRevision) {
        WorkspaceMember member = loadMemberInWorkspace(memberId, workspaceId);
        if (member.status() == MemberStatus.REMOVED) {
            return;
        }
        if (!memberRepository.updateStatus(memberId, MemberStatus.REMOVED,
                expectedRevision, Instant.now())) {
            throw conflict("WorkspaceMember", memberId, member.status().name(), expectedRevision);
        }
    }

    /**
     * 撤销指定用户的全部工作空间成员记录（FR-068 即时撤权，跨空间批量操作）。
     *
     * <p>用于用户离职/全局下线场景：将所有 ACTIVE/SUSPENDED 成员记录置为 REMOVED 并递增 revision。
     * 由身份模块 {@code IdentityLifecycleService.depart} 或后台作业协调调用。
     *
     * @return 受影响行数
     */
    @Transactional
    public int revokeAllMembershipsByUser(UUID userId, String reason) {
        return memberRepository.revokeAllByUser(userId, reason, Instant.now());
    }

    // ============================================================
    // 角色管理（FR-063）
    // ============================================================

    /**
     * 创建自定义角色。
     *
     * <p>系统角色 {@code isSystem=true} 仅由平台初始化创建，不可通过此方法创建。
     * 工作空间内 key 唯一；同一 (workspace, key) 已存在抛 {@link ConflictException}。
     */
    @Transactional
    public WorkspaceRole createRole(UUID workspaceId, String key, String name, String description,
                                    List<String> permissions, DataScopeType dataScopeType,
                                    boolean isSystem) {
        loadWorkspace(workspaceId);
        if (roleRepository.findByKey(workspaceId, key).isPresent()) {
            throw new ConflictException("角色 key 已存在: " + key);
        }
        if (permissions == null || permissions.isEmpty()) {
            throw new BusinessRuleException("角色权限不能为空");
        }
        Instant now = Instant.now();
        WorkspaceRole role = new WorkspaceRole(
                UuidV7Generator.next(),
                workspaceId,
                key,
                name,
                description,
                permissions,
                dataScopeType,
                RoleStatus.ACTIVE,
                isSystem,
                1,
                now,
                now);
        roleRepository.save(role);
        return role;
    }

    /** 按工作空间分页查询角色（可选是否包含系统角色）。 */
    public PageResult<WorkspaceRole> listRoles(UUID workspaceId, boolean includeSystem,
                                                PageRequest pageRequest) {
        loadWorkspace(workspaceId);
        return roleRepository.findByWorkspace(workspaceId, includeSystem, pageRequest);
    }

    /** 查询角色详情（按工作空间边界校验）。 */
    public WorkspaceRole getRole(UUID workspaceId, UUID roleId) {
        return loadRoleInWorkspace(roleId, workspaceId);
    }

    /** 更新角色（名称、描述、权限、数据范围类型，乐观锁）。 */
    @Transactional
    public WorkspaceRole updateRole(UUID workspaceId, UUID roleId, String name, String description,
                                    List<String> permissions, DataScopeType dataScopeType,
                                    int expectedRevision) {
        WorkspaceRole role = loadRoleInWorkspace(roleId, workspaceId);
        if (!roleRepository.update(roleId, name, description, permissions, dataScopeType,
                expectedRevision, Instant.now())) {
            throw conflict("WorkspaceRole", roleId, role.status().name(), expectedRevision);
        }
        return loadRole(roleId);
    }

    /**
     * 停用角色（ACTIVE → DISABLED，乐观锁）。
     *
     * <p>系统角色不可停用（{@link WorkspaceRole#canDisable()} 校验）。
     */
    @Transactional
    public WorkspaceRole disableRole(UUID workspaceId, UUID roleId, int expectedRevision) {
        WorkspaceRole role = loadRoleInWorkspace(roleId, workspaceId);
        if (!role.canDisable()) {
            throw new BusinessRuleException(ErrorCode.BUSINESS_RULE_VIOLATED,
                    "角色当前状态不可停用: " + role.status()
                            + (role.isSystem() ? "（系统角色不可停用）" : ""));
        }
        if (!roleRepository.updateStatus(roleId, RoleStatus.DISABLED,
                expectedRevision, Instant.now())) {
            throw conflict("WorkspaceRole", roleId, role.status().name(), expectedRevision);
        }
        return loadRole(roleId);
    }

    // ============================================================
    // 数据范围管理（FR-063）
    // ============================================================

    /**
     * 创建数据范围。
     *
     * <p>工作空间内 key 唯一；规则集合不能为空。
     */
    @Transactional
    public DataScope createDataScope(UUID workspaceId, String key, String name, String description,
                                     DataScopeType scopeType, List<DataScopeRule> rules) {
        loadWorkspace(workspaceId);
        if (dataScopeRepository.findByKey(workspaceId, key).isPresent()) {
            throw new ConflictException("数据范围 key 已存在: " + key);
        }
        if (rules == null || rules.isEmpty()) {
            throw new BusinessRuleException("数据范围规则不能为空");
        }
        Instant now = Instant.now();
        DataScope scope = new DataScope(
                UuidV7Generator.next(),
                workspaceId,
                key,
                name,
                description,
                scopeType,
                rules,
                1,
                now,
                now);
        dataScopeRepository.save(scope);
        return scope;
    }

    /** 按工作空间分页查询数据范围。 */
    public PageResult<DataScope> listDataScopes(UUID workspaceId, PageRequest pageRequest) {
        loadWorkspace(workspaceId);
        return dataScopeRepository.findByWorkspace(workspaceId, pageRequest);
    }

    /** 查询数据范围详情（按工作空间边界校验）。 */
    public DataScope getDataScope(UUID workspaceId, UUID scopeId) {
        return loadDataScopeInWorkspace(scopeId, workspaceId);
    }

    /** 更新数据范围（名称、描述、规则、类型，乐观锁）。 */
    @Transactional
    public DataScope updateDataScope(UUID workspaceId, UUID scopeId, String name, String description,
                                     List<DataScopeRule> rules, DataScopeType scopeType,
                                     int expectedRevision) {
        DataScope scope = loadDataScopeInWorkspace(scopeId, workspaceId);
        if (rules == null || rules.isEmpty()) {
            throw new BusinessRuleException("数据范围规则不能为空");
        }
        if (!dataScopeRepository.update(scopeId, name, description, rules, scopeType,
                expectedRevision, Instant.now())) {
            throw conflict("DataScope", scopeId, scope.scopeType().name(), expectedRevision);
        }
        return loadDataScope(scopeId);
    }

    /**
     * 删除数据范围（需无成员引用，乐观锁）。
     *
     * <p>引用检查由应用层在调用 {@link DataScopeRepository#delete} 前通过成员查询完成。
     * P1 简化：不维护成员-数据范围反向索引，依赖调用方在 controller 层先校验。
     * 完整实现应通过 {@code workspace_member_data_scope} 关联表查询并拒绝有引用的删除。
     */
    @Transactional
    public void deleteDataScope(UUID workspaceId, UUID scopeId, int expectedRevision) {
        DataScope scope = loadDataScopeInWorkspace(scopeId, workspaceId);
        if (!dataScopeRepository.delete(scopeId, expectedRevision)) {
            throw conflict("DataScope", scopeId, scope.scopeType().name(), expectedRevision);
        }
    }

    // ============================================================
    // 私有辅助方法
    // ============================================================

    private Workspace loadWorkspace(UUID workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", workspaceId));
    }

    private Organization loadOrganization(UUID organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", organizationId));
    }

    private Organization loadOrganizationInWorkspace(UUID organizationId, UUID workspaceId) {
        Organization organization = loadOrganization(organizationId);
        if (!organization.workspaceId().equals(workspaceId)) {
            throw new ResourceNotFoundException("Organization", organizationId);
        }
        return organization;
    }

    private WorkspaceMember loadMember(UUID memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkspaceMember", memberId));
    }

    private WorkspaceMember loadMemberInWorkspace(UUID memberId, UUID workspaceId) {
        WorkspaceMember member = loadMember(memberId);
        if (!member.workspaceId().equals(workspaceId)) {
            throw new ResourceNotFoundException("WorkspaceMember", memberId);
        }
        return member;
    }

    private WorkspaceRole loadRole(UUID roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkspaceRole", roleId));
    }

    private WorkspaceRole loadRoleInWorkspace(UUID roleId, UUID workspaceId) {
        WorkspaceRole role = loadRole(roleId);
        if (!role.workspaceId().equals(workspaceId)) {
            throw new ResourceNotFoundException("WorkspaceRole", roleId);
        }
        return role;
    }

    private DataScope loadDataScope(UUID scopeId) {
        return dataScopeRepository.findById(scopeId)
                .orElseThrow(() -> new ResourceNotFoundException("DataScope", scopeId));
    }

    private DataScope loadDataScopeInWorkspace(UUID scopeId, UUID workspaceId) {
        DataScope scope = loadDataScope(scopeId);
        if (!scope.workspaceId().equals(workspaceId)) {
            throw new ResourceNotFoundException("DataScope", scopeId);
        }
        return scope;
    }

    /**
     * 校验角色 ID 列表：每个角色必须存在且属于同一工作空间，且状态为 ACTIVE。
     */
    private void validateRoles(UUID workspaceId, List<UUID> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            throw new BusinessRuleException("角色列表不能为空");
        }
        for (UUID roleId : roleIds) {
            WorkspaceRole role = loadRoleInWorkspace(roleId, workspaceId);
            if (!role.isActive()) {
                throw new BusinessRuleException("角色已停用，不能分配: " + role.key());
            }
        }
    }

    /**
     * 校验数据范围 ID 列表：每个数据范围必须存在且属于同一工作空间。
     */
    private void validateDataScopes(UUID workspaceId, List<UUID> dataScopeIds) {
        if (dataScopeIds == null || dataScopeIds.isEmpty()) {
            return;
        }
        for (UUID scopeId : dataScopeIds) {
            loadDataScopeInWorkspace(scopeId, workspaceId);
        }
    }

    /**
     * 构建物化路径。
     *
     * <p>根组织（parentId=null）：{@code /<code>}。
     * 子组织：{@code <parent.path>/<code>}。
     */
    private String buildPath(UUID workspaceId, String code, UUID parentId) {
        if (parentId == null) {
            return "/" + code;
        }
        Organization parent = loadOrganizationInWorkspace(parentId, workspaceId);
        return parent.path() + "/" + code;
    }

    /**
     * 计算深度：根组织 depth=0；子组织 depth=parent.depth+1。
     */
    private int computeDepth(UUID parentId, UUID workspaceId) {
        if (parentId == null) {
            return 0;
        }
        Organization parent = loadOrganizationInWorkspace(parentId, workspaceId);
        return parent.depth() + 1;
    }

    /**
     * 判断 candidateId 是否为 organizationId 的祖先（沿 parent_id 上溯）。
     *
     * <p>用于移动组织时禁止将组织移动到其子树下（避免循环）。
     * 限制上溯层数防止异常数据导致无限循环。
     */
    private boolean isAncestor(UUID candidateId, UUID organizationId, UUID workspaceId) {
        UUID current = organizationId;
        int maxDepth = 64;
        while (current != null && maxDepth-- > 0) {
            Organization org = loadOrganizationInWorkspace(current, workspaceId);
            if (candidateId.equals(org.parentId())) {
                return true;
            }
            current = org.parentId();
        }
        return false;
    }

    /**
     * 构造乐观锁冲突异常。
     */
    private ConflictException conflict(String objectType, UUID objectId,
                                      String currentState, int expectedRevision) {
        return new ConflictException(objectType, objectId, currentState, (long) expectedRevision);
    }
}
