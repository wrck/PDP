package com.pdp.api.workspace;

import com.pdp.shared.context.ActorRef;
import com.pdp.shared.context.RequestContext;
import com.pdp.shared.page.PageRequest;
import com.pdp.shared.page.PageResult;
import com.pdp.workspace.application.CollaborationGrantService;
import com.pdp.workspace.application.WorkspaceGovernanceService;
import com.pdp.workspace.domain.CollaborationGrant;
import com.pdp.workspace.domain.DataScope;
import com.pdp.workspace.domain.DataScopeRule;
import com.pdp.workspace.domain.DataScopeType;
import com.pdp.workspace.domain.GrantStatus;
import com.pdp.workspace.domain.MemberStatus;
import com.pdp.workspace.domain.Organization;
import com.pdp.workspace.domain.Workspace;
import com.pdp.workspace.domain.WorkspaceMember;
import com.pdp.workspace.domain.WorkspaceRole;
import com.pdp.workspace.port.GrantDirection;
import com.pdp.workspace.port.MemberQueryFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 工作空间治理控制器（T107、FR-003 至 FR-006、FR-063 至 FR-068）。
 *
 * <p>对外暴露工作空间生命周期、组织树、成员管理、角色管理、数据范围管理与跨空间协作授权
 * 的 HTTP 端点。实现 {@code specs/002-pdp-product/contracts/openapi.yaml} 中
 * {@code /workspaces/*} 路径下的全部端点。
 *
 * <p><strong>核心契约</strong>：
 * <ul>
 *   <li>工作空间边界由 {@link RequestContext#workspaceId()} 提供（{@code X-Workspace-Id} 头
 *       经 {@code RequestContextFilter} 解析注入），路径包含 {@code {workspaceId}} 的端点
 *       自动校验路径参数与头部一致；</li>
 *   <li>操作者身份由 {@link ActorRef} 提供，所有写操作审计回写；</li>
 *   <li>乐观锁：{@code If-Match} 头携带当前 revision 字符串；不匹配返回 409；</li>
 *   <li>幂等键：{@code Idempotency-Key} 头（高风险写操作 MUST 携带，由 {@code IdempotencyPort}
 *       校验，P1 简化直接执行）；</li>
 *   <li>跨工作空间访问统一返回 404，不泄露存在性（由 {@code WorkspaceGovernanceService}
 *       在加载时按 {@code workspaceId} 边界校验）；</li>
 *   <li>FR-068：移除/暂停成员为单条 UPDATE 原子完成，1 分钟内下次访问拒绝。</li>
 * </ul>
 *
 * <p><strong>端点列表</strong>（与 OpenAPI 契约对齐）：
 * <ol>
 *   <li>{@code GET /workspaces}：分页查询当前用户可访问的工作空间；</li>
 *   <li>{@code POST /workspaces}：创建工作空间；</li>
 *   <li>{@code GET /workspaces/{workspaceId}}：查询工作空间详情；</li>
 *   <li>{@code PATCH /workspaces/{workspaceId}}：更新基本信息；</li>
 *   <li>{@code POST /workspaces/{workspaceId}/activate|suspend|archive|restore}：状态迁移；</li>
 *   <li>{@code PUT /workspaces/{workspaceId}/owner}：转移负责人；</li>
 *   <li>{@code GET/POST /workspaces/{workspaceId}/organizations}：组织树查询/创建；</li>
 *   <li>{@code GET/PATCH/DELETE /workspaces/{workspaceId}/organizations/{organizationId}}：组织详情/更新/停用；</li>
 *   <li>{@code POST /workspaces/{workspaceId}/organizations/{organizationId}/move}：调整层级；</li>
 *   <li>{@code GET/POST /workspaces/{workspaceId}/members}：成员查询/添加；</li>
 *   <li>{@code GET/PATCH/DELETE /workspaces/{workspaceId}/members/{memberId}}：成员详情/更新/移除（FR-068）；</li>
 *   <li>{@code POST /workspaces/{workspaceId}/members/{memberId}/suspend|resume}：成员暂停/恢复；</li>
 *   <li>{@code GET/POST /workspaces/{workspaceId}/roles}：角色查询/创建；</li>
 *   <li>{@code GET/PATCH /workspaces/{workspaceId}/roles/{roleId}}：角色详情/更新；</li>
 *   <li>{@code POST /workspaces/{workspaceId}/roles/{roleId}/disable}：停用角色；</li>
 *   <li>{@code GET/POST /workspaces/{workspaceId}/data-scopes}：数据范围查询/创建；</li>
 *   <li>{@code PATCH/DELETE /workspaces/{workspaceId}/data-scopes/{scopeId}}：数据范围更新/删除；</li>
 *   <li>{@code POST /workspaces/{workspaceId}/collaboration-grants}：创建协作授权；</li>
 *   <li>{@code POST /workspaces/{workspaceId}/collaboration-grants/{grantId}/revoke}：撤销授权；</li>
 *   <li>{@code GET /workspaces/{workspaceId}/collaboration-grants/list}：查询协作授权。</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "工作空间", description = "工作空间生命周期、组织、成员、角色、数据范围与跨空间协作授权")
public class WorkspaceController {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final String IF_MATCH_HEADER = "If-Match";
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 200;

    private final WorkspaceGovernanceService governanceService;
    private final CollaborationGrantService grantService;

    public WorkspaceController(WorkspaceGovernanceService governanceService,
                               CollaborationGrantService grantService) {
        this.governanceService = Objects.requireNonNull(governanceService);
        this.grantService = Objects.requireNonNull(grantService);
    }

    // ============================================================
    // 工作空间集合
    // ============================================================

    /** 获取当前用户可访问的工作空间（按负责人分页查询）。 */
    @GetMapping("/workspaces")
    @Operation(
            operationId = "listWorkspaces",
            summary = "获取当前用户可访问的工作空间",
            description = "按当前操作者作为负责人分页查询工作空间。游标分页。")
    @ApiResponse(responseCode = "200", description = "工作空间分页")
    public ResponseEntity<PageResult<Workspace>> listWorkspaces(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int pageSize) {
        UUID ownerUserId = currentActor().actorId();
        PageRequest pageRequest = toPageRequest(cursor, pageSize);
        PageResult<Workspace> result = governanceService.listWorkspacesByOwner(ownerUserId, pageRequest);
        return ResponseEntity.ok(result);
    }

    /** 创建工作空间。 */
    @PostMapping("/workspaces")
    @Operation(
            operationId = "createWorkspace",
            summary = "创建工作空间",
            description = "创建工作空间，初始状态 DRAFT。code 全局唯一；重复创建返回 409。")
    @ApiResponse(responseCode = "201", description = "已创建",
            headers = @Header(name = "Location", description = "工作空间资源 URL"))
    @ApiResponse(responseCode = "409", description = "code 已存在")
    public ResponseEntity<Workspace> createWorkspace(
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @RequestBody CreateWorkspaceCommand request) {
        Workspace workspace = governanceService.createWorkspace(
                request.code(),
                request.name(),
                request.description(),
                request.ownerUserId(),
                request.defaultLocale(),
                request.defaultTimezone());
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Location", "/api/v1/workspaces/" + workspace.id())
                .body(workspace);
    }

    // ============================================================
    // 工作空间详情
    // ============================================================

    /** 获取工作空间详情。 */
    @GetMapping("/workspaces/{workspaceId}")
    @Operation(operationId = "getWorkspace", summary = "获取工作空间详情")
    @ApiResponse(responseCode = "200", description = "工作空间详情")
    @ApiResponse(responseCode = "404", description = "工作空间不存在或无权访问")
    public ResponseEntity<Workspace> getWorkspace(@PathVariable UUID workspaceId) {
        return ResponseEntity.ok(governanceService.getWorkspace(workspaceId));
    }

    /** 更新工作空间基本信息。 */
    @PatchMapping("/workspaces/{workspaceId}")
    @Operation(
            operationId = "updateWorkspace",
            summary = "更新工作空间基本信息",
            description = "更新名称、描述、默认语言/时区。If-Match 头携带当前 revision。")
    @ApiResponse(responseCode = "200", description = "已更新")
    @ApiResponse(responseCode = "409", description = "版本冲突")
    public ResponseEntity<Workspace> updateWorkspace(
            @PathVariable UUID workspaceId,
            @RequestHeader(IF_MATCH_HEADER) String ifMatch,
            @RequestBody UpdateWorkspaceCommand request) {
        int expectedRevision = parseRevision(ifMatch);
        Workspace workspace = governanceService.updateWorkspaceBasicInfo(
                workspaceId,
                request.name(),
                request.description(),
                request.defaultLocale(),
                request.defaultTimezone(),
                expectedRevision);
        return ResponseEntity.ok(workspace);
    }

    /** 激活工作空间（DRAFT/SUSPENDED → ACTIVE）。 */
    @PostMapping("/workspaces/{workspaceId}/activate")
    @Operation(operationId = "activateWorkspace", summary = "激活工作空间")
    @ApiResponse(responseCode = "200", description = "已激活")
    @ApiResponse(responseCode = "409", description = "状态非法或版本冲突")
    public ResponseEntity<Workspace> activateWorkspace(
            @PathVariable UUID workspaceId,
            @RequestHeader(IF_MATCH_HEADER) String ifMatch,
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey) {
        int expectedRevision = parseRevision(ifMatch);
        return ResponseEntity.ok(governanceService.activateWorkspace(workspaceId, expectedRevision));
    }

    /** 暂停工作空间（ACTIVE → SUSPENDED）。 */
    @PostMapping("/workspaces/{workspaceId}/suspend")
    @Operation(operationId = "suspendWorkspace", summary = "暂停工作空间")
    @ApiResponse(responseCode = "200", description = "已暂停")
    @ApiResponse(responseCode = "409", description = "状态非法或版本冲突")
    public ResponseEntity<Workspace> suspendWorkspace(
            @PathVariable UUID workspaceId,
            @RequestHeader(IF_MATCH_HEADER) String ifMatch,
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @RequestBody ReasonCommand request) {
        int expectedRevision = parseRevision(ifMatch);
        return ResponseEntity.ok(governanceService.suspendWorkspace(
                workspaceId, expectedRevision, request.reason()));
    }

    /** 归档工作空间（ACTIVE/SUSPENDED → ARCHIVED）。 */
    @PostMapping("/workspaces/{workspaceId}/archive")
    @Operation(operationId = "archiveWorkspace", summary = "归档工作空间")
    @ApiResponse(responseCode = "200", description = "已归档")
    @ApiResponse(responseCode = "409", description = "状态非法或版本冲突")
    public ResponseEntity<Workspace> archiveWorkspace(
            @PathVariable UUID workspaceId,
            @RequestHeader(IF_MATCH_HEADER) String ifMatch,
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @RequestBody ReasonCommand request) {
        int expectedRevision = parseRevision(ifMatch);
        return ResponseEntity.ok(governanceService.archiveWorkspace(
                workspaceId, expectedRevision, request.reason()));
    }

    /** 恢复归档工作空间（ARCHIVED → SUSPENDED）。 */
    @PostMapping("/workspaces/{workspaceId}/restore")
    @Operation(operationId = "restoreWorkspace", summary = "恢复归档工作空间")
    @ApiResponse(responseCode = "200", description = "已恢复")
    @ApiResponse(responseCode = "409", description = "状态非法或版本冲突")
    public ResponseEntity<Workspace> restoreWorkspace(
            @PathVariable UUID workspaceId,
            @RequestHeader(IF_MATCH_HEADER) String ifMatch,
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey) {
        int expectedRevision = parseRevision(ifMatch);
        return ResponseEntity.ok(governanceService.restoreWorkspace(workspaceId, expectedRevision));
    }

    /** 转移工作空间负责人。 */
    @PutMapping("/workspaces/{workspaceId}/owner")
    @Operation(operationId = "transferWorkspaceOwner", summary = "转移工作空间负责人")
    @ApiResponse(responseCode = "200", description = "已转移")
    @ApiResponse(responseCode = "409", description = "版本冲突")
    public ResponseEntity<Workspace> transferWorkspaceOwner(
            @PathVariable UUID workspaceId,
            @RequestHeader(IF_MATCH_HEADER) String ifMatch,
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @RequestBody TransferOwnerCommand request) {
        int expectedRevision = parseRevision(ifMatch);
        return ResponseEntity.ok(governanceService.transferOwner(
                workspaceId, request.newOwnerUserId(), request.reason(), expectedRevision));
    }

    // ============================================================
    // 组织管理
    // ============================================================

    /** 查询组织树（按父组织分页）。 */
    @GetMapping("/workspaces/{workspaceId}/organizations")
    @Operation(operationId = "listOrganizations", summary = "查询组织树")
    @ApiResponse(responseCode = "200", description = "组织分页")
    public ResponseEntity<PageResult<Organization>> listOrganizations(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(required = false) UUID parentId) {
        PageRequest pageRequest = toPageRequest(cursor, pageSize);
        return ResponseEntity.ok(governanceService.listOrganizations(workspaceId, parentId, pageRequest));
    }

    /** 创建组织。 */
    @PostMapping("/workspaces/{workspaceId}/organizations")
    @Operation(operationId = "createOrganization", summary = "创建组织")
    @ApiResponse(responseCode = "201", description = "已创建",
            headers = @Header(name = "Location", description = "组织资源 URL"))
    @ApiResponse(responseCode = "409", description = "code 已存在")
    public ResponseEntity<Organization> createOrganization(
            @PathVariable UUID workspaceId,
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @RequestBody CreateOrganizationCommand request) {
        Organization organization = governanceService.createOrganization(
                workspaceId,
                request.code(),
                request.name(),
                request.description(),
                request.parentId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Location", "/api/v1/workspaces/" + workspaceId
                        + "/organizations/" + organization.id())
                .body(organization);
    }

    /** 获取组织详情。 */
    @GetMapping("/workspaces/{workspaceId}/organizations/{organizationId}")
    @Operation(operationId = "getOrganization", summary = "获取组织详情")
    @ApiResponse(responseCode = "200", description = "组织详情")
    @ApiResponse(responseCode = "404", description = "组织不存在或跨工作空间访问")
    public ResponseEntity<Organization> getOrganization(
            @PathVariable UUID workspaceId,
            @PathVariable UUID organizationId) {
        return ResponseEntity.ok(governanceService.getOrganization(workspaceId, organizationId));
    }

    /** 更新组织信息。 */
    @PatchMapping("/workspaces/{workspaceId}/organizations/{organizationId}")
    @Operation(operationId = "updateOrganization", summary = "更新组织信息")
    @ApiResponse(responseCode = "200", description = "已更新")
    @ApiResponse(responseCode = "409", description = "版本冲突")
    public ResponseEntity<Organization> updateOrganization(
            @PathVariable UUID workspaceId,
            @PathVariable UUID organizationId,
            @RequestHeader(IF_MATCH_HEADER) String ifMatch,
            @RequestBody UpdateOrganizationCommand request) {
        int expectedRevision = parseRevision(ifMatch);
        return ResponseEntity.ok(governanceService.updateOrganization(
                workspaceId, organizationId, request.name(), request.description(), expectedRevision));
    }

    /** 停用组织（软删除）。 */
    @DeleteMapping("/workspaces/{workspaceId}/organizations/{organizationId}")
    @Operation(operationId = "deactivateOrganization", summary = "停用组织（软删除）")
    @ApiResponse(responseCode = "204", description = "已停用")
    @ApiResponse(responseCode = "409", description = "版本冲突")
    public ResponseEntity<Void> deactivateOrganization(
            @PathVariable UUID workspaceId,
            @PathVariable UUID organizationId,
            @RequestHeader(IF_MATCH_HEADER) String ifMatch) {
        int expectedRevision = parseRevision(ifMatch);
        governanceService.deactivateOrganization(workspaceId, organizationId, expectedRevision);
        return ResponseEntity.noContent().build();
    }

    /** 调整组织层级（移动到新父组织下）。 */
    @PostMapping("/workspaces/{workspaceId}/organizations/{organizationId}/move")
    @Operation(operationId = "moveOrganization", summary = "调整组织层级")
    @ApiResponse(responseCode = "200", description = "已移动")
    @ApiResponse(responseCode = "409", description = "循环依赖或版本冲突")
    public ResponseEntity<Organization> moveOrganization(
            @PathVariable UUID workspaceId,
            @PathVariable UUID organizationId,
            @RequestHeader(IF_MATCH_HEADER) String ifMatch,
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @RequestBody MoveOrganizationCommand request) {
        int expectedRevision = parseRevision(ifMatch);
        return ResponseEntity.ok(governanceService.moveOrganization(
                workspaceId, organizationId, request.newParentId(), expectedRevision));
    }

    // ============================================================
    // 成员管理
    // ============================================================

    /** 查询工作空间成员（可按组织、角色、状态过滤）。 */
    @GetMapping("/workspaces/{workspaceId}/members")
    @Operation(operationId = "listWorkspaceMembers", summary = "查询工作空间成员")
    @ApiResponse(responseCode = "200", description = "成员分页")
    public ResponseEntity<PageResult<WorkspaceMember>> listWorkspaceMembers(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false) UUID roleId,
            @RequestParam(required = false) MemberStatus status) {
        PageRequest pageRequest = toPageRequest(cursor, pageSize);
        MemberQueryFilter filter = MemberQueryFilter.of(organizationId, roleId, status);
        return ResponseEntity.ok(governanceService.listMembers(workspaceId, filter, pageRequest));
    }

    /** 添加工作空间成员。 */
    @PostMapping("/workspaces/{workspaceId}/members")
    @Operation(operationId = "addWorkspaceMember", summary = "添加工作空间成员")
    @ApiResponse(responseCode = "201", description = "已添加",
            headers = @Header(name = "Location", description = "成员资源 URL"))
    @ApiResponse(responseCode = "409", description = "成员已存在或角色/数据范围无效")
    public ResponseEntity<WorkspaceMember> addWorkspaceMember(
            @PathVariable UUID workspaceId,
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @RequestBody AddWorkspaceMemberCommand request) {
        WorkspaceMember member = governanceService.addMember(
                workspaceId,
                request.userId(),
                request.roleIds(),
                request.organizationId(),
                request.dataScopeIds(),
                request.validUntil());
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Location", "/api/v1/workspaces/" + workspaceId
                        + "/members/" + member.id())
                .body(member);
    }

    /** 获取成员详情。 */
    @GetMapping("/workspaces/{workspaceId}/members/{memberId}")
    @Operation(operationId = "getWorkspaceMember", summary = "获取成员详情")
    @ApiResponse(responseCode = "200", description = "成员详情")
    @ApiResponse(responseCode = "404", description = "成员不存在或跨工作空间访问")
    public ResponseEntity<WorkspaceMember> getWorkspaceMember(
            @PathVariable UUID workspaceId,
            @PathVariable UUID memberId) {
        return ResponseEntity.ok(governanceService.getMember(workspaceId, memberId));
    }

    /** 更新成员（角色、组织归属、数据范围、有效期）。 */
    @PatchMapping("/workspaces/{workspaceId}/members/{memberId}")
    @Operation(operationId = "updateWorkspaceMember", summary = "更新成员")
    @ApiResponse(responseCode = "200", description = "已更新")
    @ApiResponse(responseCode = "409", description = "版本冲突")
    public ResponseEntity<WorkspaceMember> updateWorkspaceMember(
            @PathVariable UUID workspaceId,
            @PathVariable UUID memberId,
            @RequestHeader(IF_MATCH_HEADER) String ifMatch,
            @RequestBody UpdateWorkspaceMemberCommand request) {
        int expectedRevision = parseRevision(ifMatch);
        return ResponseEntity.ok(governanceService.updateMember(
                workspaceId,
                memberId,
                request.roleIds(),
                request.organizationId(),
                request.dataScopeIds(),
                request.validUntil(),
                expectedRevision));
    }

    /**
     * 移除成员（FR-068 撤权，最长 1 分钟生效）。
     */
    @DeleteMapping("/workspaces/{workspaceId}/members/{memberId}")
    @Operation(
            operationId = "removeWorkspaceMember",
            summary = "移除成员（FR-068 撤权，最长 1 分钟生效）",
            description = "ACTIVE/SUSPENDED → REMOVED；单条 UPDATE 原子完成，下次访问拒绝。")
    @ApiResponse(responseCode = "204", description = "已移除")
    @ApiResponse(responseCode = "409", description = "版本冲突")
    public ResponseEntity<Void> removeWorkspaceMember(
            @PathVariable UUID workspaceId,
            @PathVariable UUID memberId,
            @RequestHeader(IF_MATCH_HEADER) String ifMatch) {
        int expectedRevision = parseRevision(ifMatch);
        governanceService.removeMember(workspaceId, memberId, expectedRevision);
        return ResponseEntity.noContent().build();
    }

    /** 暂停成员访问（ACTIVE → SUSPENDED）。 */
    @PostMapping("/workspaces/{workspaceId}/members/{memberId}/suspend")
    @Operation(operationId = "suspendWorkspaceMember", summary = "暂停成员访问")
    @ApiResponse(responseCode = "200", description = "已暂停")
    @ApiResponse(responseCode = "409", description = "状态非法或版本冲突")
    public ResponseEntity<WorkspaceMember> suspendWorkspaceMember(
            @PathVariable UUID workspaceId,
            @PathVariable UUID memberId,
            @RequestHeader(IF_MATCH_HEADER) String ifMatch,
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @RequestBody ReasonCommand request) {
        int expectedRevision = parseRevision(ifMatch);
        return ResponseEntity.ok(governanceService.suspendMember(
                workspaceId, memberId, request.reason(), expectedRevision));
    }

    /** 恢复成员访问（SUSPENDED → ACTIVE）。 */
    @PostMapping("/workspaces/{workspaceId}/members/{memberId}/resume")
    @Operation(operationId = "resumeWorkspaceMember", summary = "恢复成员访问")
    @ApiResponse(responseCode = "200", description = "已恢复")
    @ApiResponse(responseCode = "409", description = "状态非法或版本冲突")
    public ResponseEntity<WorkspaceMember> resumeWorkspaceMember(
            @PathVariable UUID workspaceId,
            @PathVariable UUID memberId,
            @RequestHeader(IF_MATCH_HEADER) String ifMatch,
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey) {
        int expectedRevision = parseRevision(ifMatch);
        return ResponseEntity.ok(governanceService.resumeMember(workspaceId, memberId, expectedRevision));
    }

    // ============================================================
    // 角色管理
    // ============================================================

    /** 查询工作空间角色。 */
    @GetMapping("/workspaces/{workspaceId}/roles")
    @Operation(operationId = "listWorkspaceRoles", summary = "查询工作空间角色")
    @ApiResponse(responseCode = "200", description = "角色分页")
    public ResponseEntity<PageResult<WorkspaceRole>> listWorkspaceRoles(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(defaultValue = "false") boolean includeSystem) {
        PageRequest pageRequest = toPageRequest(cursor, pageSize);
        return ResponseEntity.ok(governanceService.listRoles(workspaceId, includeSystem, pageRequest));
    }

    /** 创建自定义角色。 */
    @PostMapping("/workspaces/{workspaceId}/roles")
    @Operation(operationId = "createWorkspaceRole", summary = "创建自定义角色")
    @ApiResponse(responseCode = "201", description = "已创建",
            headers = @Header(name = "Location", description = "角色资源 URL"))
    @ApiResponse(responseCode = "409", description = "key 已存在")
    public ResponseEntity<WorkspaceRole> createWorkspaceRole(
            @PathVariable UUID workspaceId,
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @RequestBody CreateWorkspaceRoleCommand request) {
        WorkspaceRole role = governanceService.createRole(
                workspaceId,
                request.key(),
                request.name(),
                request.description(),
                request.permissions(),
                request.dataScopeType() == null ? DataScopeType.WORKSPACE : request.dataScopeType(),
                Boolean.TRUE.equals(request.isSystem()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Location", "/api/v1/workspaces/" + workspaceId
                        + "/roles/" + role.id())
                .body(role);
    }

    /** 获取角色详情。 */
    @GetMapping("/workspaces/{workspaceId}/roles/{roleId}")
    @Operation(operationId = "getWorkspaceRole", summary = "获取角色详情")
    @ApiResponse(responseCode = "200", description = "角色详情")
    @ApiResponse(responseCode = "404", description = "角色不存在或跨工作空间访问")
    public ResponseEntity<WorkspaceRole> getWorkspaceRole(
            @PathVariable UUID workspaceId,
            @PathVariable UUID roleId) {
        return ResponseEntity.ok(governanceService.getRole(workspaceId, roleId));
    }

    /** 更新角色（权限、数据范围、描述）。 */
    @PatchMapping("/workspaces/{workspaceId}/roles/{roleId}")
    @Operation(operationId = "updateWorkspaceRole", summary = "更新角色")
    @ApiResponse(responseCode = "200", description = "已更新")
    @ApiResponse(responseCode = "409", description = "版本冲突")
    public ResponseEntity<WorkspaceRole> updateWorkspaceRole(
            @PathVariable UUID workspaceId,
            @PathVariable UUID roleId,
            @RequestHeader(IF_MATCH_HEADER) String ifMatch,
            @RequestBody UpdateWorkspaceRoleCommand request) {
        int expectedRevision = parseRevision(ifMatch);
        return ResponseEntity.ok(governanceService.updateRole(
                workspaceId,
                roleId,
                request.name(),
                request.description(),
                request.permissions(),
                request.dataScopeType(),
                expectedRevision));
    }

    /** 停用角色（ACTIVE → DISABLED）。 */
    @PostMapping("/workspaces/{workspaceId}/roles/{roleId}/disable")
    @Operation(operationId = "disableWorkspaceRole", summary = "停用角色")
    @ApiResponse(responseCode = "200", description = "已停用")
    @ApiResponse(responseCode = "409", description = "系统角色不可停用或版本冲突")
    public ResponseEntity<WorkspaceRole> disableWorkspaceRole(
            @PathVariable UUID workspaceId,
            @PathVariable UUID roleId,
            @RequestHeader(IF_MATCH_HEADER) String ifMatch,
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey) {
        int expectedRevision = parseRevision(ifMatch);
        return ResponseEntity.ok(governanceService.disableRole(workspaceId, roleId, expectedRevision));
    }

    // ============================================================
    // 数据范围管理
    // ============================================================

    /** 查询数据范围定义。 */
    @GetMapping("/workspaces/{workspaceId}/data-scopes")
    @Operation(operationId = "listDataScopes", summary = "查询数据范围定义")
    @ApiResponse(responseCode = "200", description = "数据范围分页")
    public ResponseEntity<PageResult<DataScope>> listDataScopes(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int pageSize) {
        PageRequest pageRequest = toPageRequest(cursor, pageSize);
        return ResponseEntity.ok(governanceService.listDataScopes(workspaceId, pageRequest));
    }

    /** 创建数据范围。 */
    @PostMapping("/workspaces/{workspaceId}/data-scopes")
    @Operation(operationId = "createDataScope", summary = "创建数据范围")
    @ApiResponse(responseCode = "201", description = "已创建",
            headers = @Header(name = "Location", description = "数据范围资源 URL"))
    @ApiResponse(responseCode = "409", description = "key 已存在")
    public ResponseEntity<DataScope> createDataScope(
            @PathVariable UUID workspaceId,
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @RequestBody CreateDataScopeCommand request) {
        DataScope scope = governanceService.createDataScope(
                workspaceId,
                request.key(),
                request.name(),
                request.description(),
                request.scopeType(),
                request.rules());
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Location", "/api/v1/workspaces/" + workspaceId
                        + "/data-scopes/" + scope.id())
                .body(scope);
    }

    /** 更新数据范围。 */
    @PatchMapping("/workspaces/{workspaceId}/data-scopes/{scopeId}")
    @Operation(operationId = "updateDataScope", summary = "更新数据范围")
    @ApiResponse(responseCode = "200", description = "已更新")
    @ApiResponse(responseCode = "409", description = "版本冲突")
    public ResponseEntity<DataScope> updateDataScope(
            @PathVariable UUID workspaceId,
            @PathVariable UUID scopeId,
            @RequestHeader(IF_MATCH_HEADER) String ifMatch,
            @RequestBody UpdateDataScopeCommand request) {
        int expectedRevision = parseRevision(ifMatch);
        return ResponseEntity.ok(governanceService.updateDataScope(
                workspaceId,
                scopeId,
                request.name(),
                request.description(),
                request.rules(),
                request.scopeType(),
                expectedRevision));
    }

    /** 删除数据范围（需无引用）。 */
    @DeleteMapping("/workspaces/{workspaceId}/data-scopes/{scopeId}")
    @Operation(operationId = "deleteDataScope", summary = "删除数据范围（需无引用）")
    @ApiResponse(responseCode = "204", description = "已删除")
    @ApiResponse(responseCode = "409", description = "版本冲突")
    public ResponseEntity<Void> deleteDataScope(
            @PathVariable UUID workspaceId,
            @PathVariable UUID scopeId,
            @RequestHeader(IF_MATCH_HEADER) String ifMatch) {
        int expectedRevision = parseRevision(ifMatch);
        governanceService.deleteDataScope(workspaceId, scopeId, expectedRevision);
        return ResponseEntity.noContent().build();
    }

    // ============================================================
    // 跨工作空间协作授权
    // ============================================================

    /** 授予跨工作空间协作权限。 */
    @PostMapping("/workspaces/{workspaceId}/collaboration-grants")
    @Operation(
            operationId = "createCollaborationGrant",
            summary = "授予跨工作空间协作权限",
            description = "授权方工作空间将指定目标对象的部分操作权限授予协作方工作空间。")
    @ApiResponse(responseCode = "201", description = "授权已创建",
            headers = @Header(name = "Location", description = "授权资源 URL"))
    @ApiResponse(responseCode = "403", description = "无权授权")
    public ResponseEntity<CollaborationGrant> createCollaborationGrant(
            @PathVariable UUID workspaceId,
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @RequestBody CreateCollaborationGrantCommand request) {
        ObjectRef target = request.target();
        CollaborationGrant grant = grantService.createGrant(
                workspaceId,
                request.collaboratorWorkspaceId(),
                target.objectType(),
                target.objectId(),
                request.roleId(),
                request.allowedActions(),
                request.validUntil(),
                request.reason());
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Location", "/api/v1/workspaces/" + workspaceId
                        + "/collaboration-grants/" + grant.id())
                .body(grant);
    }

    /** 撤销跨工作空间授权（ACTIVE → REVOKED）。 */
    @PostMapping("/workspaces/{workspaceId}/collaboration-grants/{grantId}/revoke")
    @Operation(operationId = "revokeCollaborationGrant", summary = "撤销跨工作空间授权")
    @ApiResponse(responseCode = "204", description = "已撤销")
    @ApiResponse(responseCode = "409", description = "状态非法或版本冲突")
    public ResponseEntity<Void> revokeCollaborationGrant(
            @PathVariable UUID workspaceId,
            @PathVariable UUID grantId,
            @RequestHeader(IF_MATCH_HEADER) String ifMatch,
            @RequestBody ReasonCommand request) {
        int expectedRevision = parseRevision(ifMatch);
        grantService.revokeGrant(grantId, request.reason(), expectedRevision);
        return ResponseEntity.noContent().build();
    }

    /** 查询跨工作空间协作授权。 */
    @GetMapping("/workspaces/{workspaceId}/collaboration-grants/list")
    @Operation(operationId = "listCollaborationGrants", summary = "查询跨工作空间协作授权")
    @ApiResponse(responseCode = "200", description = "授权分页")
    public ResponseEntity<PageResult<CollaborationGrant>> listCollaborationGrants(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(required = false) GrantStatus status,
            @RequestParam(defaultValue = "OUTGOING") GrantDirection direction) {
        PageRequest pageRequest = toPageRequest(cursor, pageSize);
        return ResponseEntity.ok(grantService.listGrants(workspaceId, direction, status, pageRequest));
    }

    // ============================================================
    // 私有辅助方法
    // ============================================================

    private ActorRef currentActor() {
        return RequestContext.get().operator().actor();
    }

    /**
     * 解析 If-Match 头为 revision 整数。
     *
     * <p>If-Match 携带当前资源的 revision 字符串（如 {@code "3"}）；非法格式返回 400。
     */
    private int parseRevision(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new IllegalArgumentException("If-Match 头不能为空（必须携带当前 revision）");
        }
        try {
            return Integer.parseInt(ifMatch.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("If-Match 头格式非法，必须为 revision 整数: " + ifMatch);
        }
    }

    /**
     * 限制分页大小在 [1, MAX_PAGE_SIZE] 范围内。
     */
    private int clampSize(int size) {
        if (size < 1) return 1;
        return Math.min(size, MAX_PAGE_SIZE);
    }

    /**
     * 构造分页请求：首页用 {@link PageRequest#firstPage}，续页用 {@link PageRequest#next}。
     */
    private PageRequest toPageRequest(String cursor, int pageSize) {
        int safeSize = clampSize(pageSize);
        if (cursor == null || cursor.isBlank()) {
            return PageRequest.firstPage(safeSize);
        }
        return PageRequest.next(cursor, safeSize);
    }

    // ============================================================
    // 请求 DTO（对应 OpenAPI schemas）
    // ============================================================

    /** 创建工作空间请求。 */
    public record CreateWorkspaceCommand(
            String code,
            String name,
            String description,
            UUID ownerUserId,
            String defaultLocale,
            String defaultTimezone) {
    }

    /** 更新工作空间请求。 */
    public record UpdateWorkspaceCommand(
            String name,
            String description,
            String defaultLocale,
            String defaultTimezone) {
    }

    /** 转移负责人请求。 */
    public record TransferOwnerCommand(UUID newOwnerUserId, String reason) {
    }

    /** 原因请求（暂停/归档/撤销等）。 */
    public record ReasonCommand(String reason) {
    }

    /** 创建组织请求。 */
    public record CreateOrganizationCommand(
            String code,
            String name,
            String description,
            UUID parentId) {
    }

    /** 更新组织请求。 */
    public record UpdateOrganizationCommand(String name, String description) {
    }

    /** 移动组织请求。 */
    public record MoveOrganizationCommand(UUID newParentId) {
    }

    /** 添加成员请求。 */
    public record AddWorkspaceMemberCommand(
            UUID userId,
            List<UUID> roleIds,
            UUID organizationId,
            List<UUID> dataScopeIds,
            Instant validUntil) {
    }

    /** 更新成员请求。 */
    public record UpdateWorkspaceMemberCommand(
            List<UUID> roleIds,
            UUID organizationId,
            List<UUID> dataScopeIds,
            Instant validUntil) {
    }

    /** 创建角色请求。 */
    public record CreateWorkspaceRoleCommand(
            String key,
            String name,
            String description,
            List<String> permissions,
            DataScopeType dataScopeType,
            Boolean isSystem) {
    }

    /** 更新角色请求。 */
    public record UpdateWorkspaceRoleCommand(
            String name,
            String description,
            List<String> permissions,
            DataScopeType dataScopeType) {
    }

    /** 创建数据范围请求。 */
    public record CreateDataScopeCommand(
            String key,
            String name,
            String description,
            DataScopeType scopeType,
            List<DataScopeRule> rules) {
    }

    /** 更新数据范围请求。 */
    public record UpdateDataScopeCommand(
            String name,
            String description,
            List<DataScopeRule> rules,
            DataScopeType scopeType) {
    }

    /** 创建协作授权请求。 */
    public record CreateCollaborationGrantCommand(
            UUID collaboratorWorkspaceId,
            ObjectRef target,
            UUID roleId,
            List<String> allowedActions,
            Instant validUntil,
            String reason) {
    }

    /** 对象引用 DTO（对应 OpenAPI ObjectRef）。 */
    public record ObjectRef(String objectType, UUID objectId) {
    }
}
