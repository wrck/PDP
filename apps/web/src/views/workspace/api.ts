/**
 * 工作空间治理 API 客户端（T108、FR-003 至 FR-006、FR-063 至 FR-068）。
 *
 * 对接后端 `WorkspaceController` 暴露的 30 个端点（`/api/v1/workspaces/*`）。
 * 所有响应均为 PDP 自有稳定契约，遵循 RFC 7807 Problem Details 错误格式。
 *
 * <p><strong>核心约定</strong>：
 * <ul>
 *   <li>工作空间边界由 {@code X-Workspace-Id} 头携带，由全局 httpClient 拦截器自动注入；
 *   <li>乐观锁：{@code If-Match} 头携带当前 revision 字符串，由 {@link ifMatchHeader} 构造；
 *   <li>幂等键：{@code Idempotency-Key} 头（高风险写操作 MUST 携带，未显式提供时自动生成）；
 *   <li>跨工作空间访问统一返回 404，前端按"不存在"处理，不泄露存在性。
 * </ul>
 */
import {
  get,
  post,
  put,
  del,
  postRaw,
  ifMatchHeader,
  generateIdempotencyKey,
} from '@/api'
import type {
  AddWorkspaceMemberCommand,
  CollaborationGrant,
  CreateCollaborationGrantCommand,
  CreateDataScopeCommand,
  CreateOrganizationCommand,
  CreateWorkspaceCommand,
  CreateWorkspaceRoleCommand,
  DataScope,
  GrantDirection,
  GrantStatus,
  MoveOrganizationCommand,
  Organization,
  PageResult,
  QueryParams,
  ReasonCommand,
  TransferOwnerCommand,
  UpdateDataScopeCommand,
  UpdateOrganizationCommand,
  UpdateWorkspaceCommand,
  UpdateWorkspaceMemberCommand,
  UpdateWorkspaceRoleCommand,
  Workspace,
  WorkspaceMember,
  WorkspaceRole,
  MemberStatus,
} from './types'

/** 将对象转为查询参数（过滤 null/undefined）。 */
function toQuery(params: QueryParams): Record<string, string> {
  const result: Record<string, string> = {}
  for (const [key, value] of Object.entries(params)) {
    if (value !== null && value !== undefined && value !== '') {
      result[key] = String(value)
    }
  }
  return result
}

/** 构造幂等键头（未显式提供时自动生成）。 */
function withIdempotency(key?: string): { 'Idempotency-Key': string } {
  return { 'Idempotency-Key': key ?? generateIdempotencyKey() }
}

// ============================================================
// 工作空间集合
// ============================================================

/**
 * 分页查询当前用户作为负责人的工作空间。
 * 对应 `GET /workspaces`。
 */
export async function listWorkspaces(params: {
  cursor?: string | null
  pageSize?: number
} = {}): Promise<PageResult<Workspace>> {
  return get<PageResult<Workspace>>('/workspaces', {
    params: toQuery({
      cursor: params.cursor,
      pageSize: params.pageSize ?? 50,
    }),
  })
}

/**
 * 创建工作空间。
 * 对应 `POST /workspaces`，MUST 携带 Idempotency-Key。
 */
export async function createWorkspace(
  request: CreateWorkspaceCommand,
  idempotencyKey?: string,
): Promise<Workspace> {
  return post<Workspace>('/workspaces', request, {
    headers: withIdempotency(idempotencyKey),
  })
}

// ============================================================
// 工作空间详情
// ============================================================

/**
 * 获取工作空间详情。
 * 对应 `GET /workspaces/{workspaceId}`。
 */
export async function getWorkspace(workspaceId: string): Promise<Workspace> {
  return get<Workspace>(`/workspaces/${workspaceId}`)
}

/**
 * 更新工作空间基本信息。
 * 对应 `PATCH /workspaces/{workspaceId}`，需 If-Match 头。
 *
 * <p>httpClient 未暴露 patch，通过 httpClient 实例调用 axios.patch 方法。
 */
export async function patchWorkspace(
  workspaceId: string,
  request: UpdateWorkspaceCommand,
  expectedRevision: number,
): Promise<Workspace> {
  const { httpClient } = await import('@/api')
  const response = await httpClient.patch<Workspace>(
    `/workspaces/${workspaceId}`,
    request,
    { headers: ifMatchHeader(expectedRevision) },
  )
  return response.data
}

/**
 * 激活工作空间（DRAFT/SUSPENDED → ACTIVE）。
 * 对应 `POST /workspaces/{workspaceId}/activate`，需 If-Match 与 Idempotency-Key。
 */
export async function activateWorkspace(
  workspaceId: string,
  expectedRevision: number,
  idempotencyKey?: string,
): Promise<Workspace> {
  return post<Workspace>(
    `/workspaces/${workspaceId}/activate`,
    undefined,
    {
      headers: {
        ...ifMatchHeader(expectedRevision),
        ...withIdempotency(idempotencyKey),
      },
    },
  )
}

/**
 * 暂停工作空间（ACTIVE → SUSPENDED）。
 * 对应 `POST /workspaces/{workspaceId}/suspend`，需 If-Match 与 Idempotency-Key。
 */
export async function suspendWorkspace(
  workspaceId: string,
  expectedRevision: number,
  reason: string,
  idempotencyKey?: string,
): Promise<Workspace> {
  const body: ReasonCommand = { reason }
  return post<Workspace>(
    `/workspaces/${workspaceId}/suspend`,
    body,
    {
      headers: {
        ...ifMatchHeader(expectedRevision),
        ...withIdempotency(idempotencyKey),
      },
    },
  )
}

/**
 * 归档工作空间（ACTIVE/SUSPENDED → ARCHIVED）。
 * 对应 `POST /workspaces/{workspaceId}/archive`，需 If-Match 与 Idempotency-Key。
 */
export async function archiveWorkspace(
  workspaceId: string,
  expectedRevision: number,
  reason: string,
  idempotencyKey?: string,
): Promise<Workspace> {
  const body: ReasonCommand = { reason }
  return post<Workspace>(
    `/workspaces/${workspaceId}/archive`,
    body,
    {
      headers: {
        ...ifMatchHeader(expectedRevision),
        ...withIdempotency(idempotencyKey),
      },
    },
  )
}

/**
 * 恢复归档工作空间（ARCHIVED → SUSPENDED）。
 * 对应 `POST /workspaces/{workspaceId}/restore`，需 If-Match 与 Idempotency-Key。
 */
export async function restoreWorkspace(
  workspaceId: string,
  expectedRevision: number,
  idempotencyKey?: string,
): Promise<Workspace> {
  return post<Workspace>(
    `/workspaces/${workspaceId}/restore`,
    undefined,
    {
      headers: {
        ...ifMatchHeader(expectedRevision),
        ...withIdempotency(idempotencyKey),
      },
    },
  )
}

/**
 * 转移工作空间负责人。
 * 对应 `PUT /workspaces/{workspaceId}/owner`，需 If-Match 与 Idempotency-Key。
 */
export async function transferWorkspaceOwner(
  workspaceId: string,
  request: TransferOwnerCommand,
  expectedRevision: number,
  idempotencyKey?: string,
): Promise<Workspace> {
  return put<Workspace>(
    `/workspaces/${workspaceId}/owner`,
    request,
    {
      headers: {
        ...ifMatchHeader(expectedRevision),
        ...withIdempotency(idempotencyKey),
      },
    },
  )
}

// ============================================================
// 组织管理
// ============================================================

/**
 * 查询组织树（按父组织分页）。
 * 对应 `GET /workspaces/{workspaceId}/organizations`。
 */
export async function listOrganizations(
  workspaceId: string,
  params: {
    cursor?: string | null
    pageSize?: number
    parentId?: string | null
  } = {},
): Promise<PageResult<Organization>> {
  return get<PageResult<Organization>>(
    `/workspaces/${workspaceId}/organizations`,
    {
      params: toQuery({
        cursor: params.cursor,
        pageSize: params.pageSize ?? 50,
        parentId: params.parentId,
      }),
    },
  )
}

/**
 * 创建组织。
 * 对应 `POST /workspaces/{workspaceId}/organizations`，MUST 携带 Idempotency-Key。
 */
export async function createOrganization(
  workspaceId: string,
  request: CreateOrganizationCommand,
  idempotencyKey?: string,
): Promise<Organization> {
  return post<Organization>(
    `/workspaces/${workspaceId}/organizations`,
    request,
    { headers: withIdempotency(idempotencyKey) },
  )
}

/**
 * 获取组织详情。
 * 对应 `GET /workspaces/{workspaceId}/organizations/{organizationId}`。
 */
export async function getOrganization(
  workspaceId: string,
  organizationId: string,
): Promise<Organization> {
  return get<Organization>(
    `/workspaces/${workspaceId}/organizations/${organizationId}`,
  )
}

/**
 * 更新组织信息。
 * 对应 `PATCH /workspaces/{workspaceId}/organizations/{organizationId}`，需 If-Match。
 */
export async function updateOrganization(
  workspaceId: string,
  organizationId: string,
  request: UpdateOrganizationCommand,
  expectedRevision: number,
): Promise<Organization> {
  const { httpClient } = await import('@/api')
  const response = await httpClient.patch<Organization>(
    `/workspaces/${workspaceId}/organizations/${organizationId}`,
    request,
    { headers: ifMatchHeader(expectedRevision) },
  )
  return response.data
}

/**
 * 停用组织（软删除）。
 * 对应 `DELETE /workspaces/{workspaceId}/organizations/{organizationId}`，需 If-Match。
 */
export async function deactivateOrganization(
  workspaceId: string,
  organizationId: string,
  expectedRevision: number,
): Promise<void> {
  await del<void>(
    `/workspaces/${workspaceId}/organizations/${organizationId}`,
    { headers: ifMatchHeader(expectedRevision) },
  )
}

/**
 * 调整组织层级（移动到新父组织下）。
 * 对应 `POST /workspaces/{workspaceId}/organizations/{organizationId}/move`，
 * 需 If-Match 与 Idempotency-Key。
 */
export async function moveOrganization(
  workspaceId: string,
  organizationId: string,
  request: MoveOrganizationCommand,
  expectedRevision: number,
  idempotencyKey?: string,
): Promise<Organization> {
  return post<Organization>(
    `/workspaces/${workspaceId}/organizations/${organizationId}/move`,
    request,
    {
      headers: {
        ...ifMatchHeader(expectedRevision),
        ...withIdempotency(idempotencyKey),
      },
    },
  )
}

// ============================================================
// 成员管理
// ============================================================

/**
 * 查询工作空间成员（可按组织、角色、状态过滤）。
 * 对应 `GET /workspaces/{workspaceId}/members`。
 */
export async function listWorkspaceMembers(
  workspaceId: string,
  params: {
    cursor?: string | null
    pageSize?: number
    organizationId?: string | null
    roleId?: string | null
    status?: MemberStatus | null
  } = {},
): Promise<PageResult<WorkspaceMember>> {
  return get<PageResult<WorkspaceMember>>(
    `/workspaces/${workspaceId}/members`,
    {
      params: toQuery({
        cursor: params.cursor,
        pageSize: params.pageSize ?? 50,
        organizationId: params.organizationId,
        roleId: params.roleId,
        status: params.status,
      }),
    },
  )
}

/**
 * 添加工作空间成员。
 * 对应 `POST /workspaces/{workspaceId}/members`，MUST 携带 Idempotency-Key。
 */
export async function addWorkspaceMember(
  workspaceId: string,
  request: AddWorkspaceMemberCommand,
  idempotencyKey?: string,
): Promise<WorkspaceMember> {
  const response = await postRaw<WorkspaceMember>(
    `/workspaces/${workspaceId}/members`,
    request,
    { headers: withIdempotency(idempotencyKey) },
  )
  return response.data
}

/**
 * 获取成员详情。
 * 对应 `GET /workspaces/{workspaceId}/members/{memberId}`。
 */
export async function getWorkspaceMember(
  workspaceId: string,
  memberId: string,
): Promise<WorkspaceMember> {
  return get<WorkspaceMember>(
    `/workspaces/${workspaceId}/members/${memberId}`,
  )
}

/**
 * 更新成员（角色、组织归属、数据范围、有效期）。
 * 对应 `PATCH /workspaces/{workspaceId}/members/{memberId}`，需 If-Match。
 */
export async function updateWorkspaceMember(
  workspaceId: string,
  memberId: string,
  request: UpdateWorkspaceMemberCommand,
  expectedRevision: number,
): Promise<WorkspaceMember> {
  const { httpClient } = await import('@/api')
  const response = await httpClient.patch<WorkspaceMember>(
    `/workspaces/${workspaceId}/members/${memberId}`,
    request,
    { headers: ifMatchHeader(expectedRevision) },
  )
  return response.data
}

/**
 * 移除成员（FR-068 撤权，最长 1 分钟生效）。
 * 对应 `DELETE /workspaces/{workspaceId}/members/{memberId}`，需 If-Match。
 */
export async function removeWorkspaceMember(
  workspaceId: string,
  memberId: string,
  expectedRevision: number,
): Promise<void> {
  await del<void>(
    `/workspaces/${workspaceId}/members/${memberId}`,
    { headers: ifMatchHeader(expectedRevision) },
  )
}

/**
 * 暂停成员访问（ACTIVE → SUSPENDED）。
 * 对应 `POST /workspaces/{workspaceId}/members/{memberId}/suspend`，
 * 需 If-Match 与 Idempotency-Key。
 */
export async function suspendWorkspaceMember(
  workspaceId: string,
  memberId: string,
  reason: string,
  expectedRevision: number,
  idempotencyKey?: string,
): Promise<WorkspaceMember> {
  const body: ReasonCommand = { reason }
  return post<WorkspaceMember>(
    `/workspaces/${workspaceId}/members/${memberId}/suspend`,
    body,
    {
      headers: {
        ...ifMatchHeader(expectedRevision),
        ...withIdempotency(idempotencyKey),
      },
    },
  )
}

/**
 * 恢复成员访问（SUSPENDED → ACTIVE）。
 * 对应 `POST /workspaces/{workspaceId}/members/{memberId}/resume`，
 * 需 If-Match 与 Idempotency-Key。
 */
export async function resumeWorkspaceMember(
  workspaceId: string,
  memberId: string,
  expectedRevision: number,
  idempotencyKey?: string,
): Promise<WorkspaceMember> {
  return post<WorkspaceMember>(
    `/workspaces/${workspaceId}/members/${memberId}/resume`,
    undefined,
    {
      headers: {
        ...ifMatchHeader(expectedRevision),
        ...withIdempotency(idempotencyKey),
      },
    },
  )
}

// ============================================================
// 角色管理
// ============================================================

/**
 * 查询工作空间角色。
 * 对应 `GET /workspaces/{workspaceId}/roles`。
 */
export async function listWorkspaceRoles(
  workspaceId: string,
  params: {
    cursor?: string | null
    pageSize?: number
    includeSystem?: boolean
  } = {},
): Promise<PageResult<WorkspaceRole>> {
  return get<PageResult<WorkspaceRole>>(
    `/workspaces/${workspaceId}/roles`,
    {
      params: toQuery({
        cursor: params.cursor,
        pageSize: params.pageSize ?? 50,
        includeSystem: params.includeSystem ?? false,
      }),
    },
  )
}

/**
 * 创建自定义角色。
 * 对应 `POST /workspaces/{workspaceId}/roles`，MUST 携带 Idempotency-Key。
 */
export async function createWorkspaceRole(
  workspaceId: string,
  request: CreateWorkspaceRoleCommand,
  idempotencyKey?: string,
): Promise<WorkspaceRole> {
  return post<WorkspaceRole>(
    `/workspaces/${workspaceId}/roles`,
    request,
    { headers: withIdempotency(idempotencyKey) },
  )
}

/**
 * 获取角色详情。
 * 对应 `GET /workspaces/{workspaceId}/roles/{roleId}`。
 */
export async function getWorkspaceRole(
  workspaceId: string,
  roleId: string,
): Promise<WorkspaceRole> {
  return get<WorkspaceRole>(
    `/workspaces/${workspaceId}/roles/${roleId}`,
  )
}

/**
 * 更新角色（权限、数据范围、描述）。
 * 对应 `PATCH /workspaces/{workspaceId}/roles/{roleId}`，需 If-Match。
 */
export async function updateWorkspaceRole(
  workspaceId: string,
  roleId: string,
  request: UpdateWorkspaceRoleCommand,
  expectedRevision: number,
): Promise<WorkspaceRole> {
  const { httpClient } = await import('@/api')
  const response = await httpClient.patch<WorkspaceRole>(
    `/workspaces/${workspaceId}/roles/${roleId}`,
    request,
    { headers: ifMatchHeader(expectedRevision) },
  )
  return response.data
}

/**
 * 停用角色（ACTIVE → DISABLED）。
 * 对应 `POST /workspaces/{workspaceId}/roles/{roleId}/disable`，
 * 需 If-Match 与 Idempotency-Key。
 */
export async function disableWorkspaceRole(
  workspaceId: string,
  roleId: string,
  expectedRevision: number,
  idempotencyKey?: string,
): Promise<WorkspaceRole> {
  return post<WorkspaceRole>(
    `/workspaces/${workspaceId}/roles/${roleId}/disable`,
    undefined,
    {
      headers: {
        ...ifMatchHeader(expectedRevision),
        ...withIdempotency(idempotencyKey),
      },
    },
  )
}

// ============================================================
// 数据范围管理
// ============================================================

/**
 * 查询数据范围定义。
 * 对应 `GET /workspaces/{workspaceId}/data-scopes`。
 */
export async function listDataScopes(
  workspaceId: string,
  params: {
    cursor?: string | null
    pageSize?: number
  } = {},
): Promise<PageResult<DataScope>> {
  return get<PageResult<DataScope>>(
    `/workspaces/${workspaceId}/data-scopes`,
    {
      params: toQuery({
        cursor: params.cursor,
        pageSize: params.pageSize ?? 50,
      }),
    },
  )
}

/**
 * 创建数据范围。
 * 对应 `POST /workspaces/{workspaceId}/data-scopes`，MUST 携带 Idempotency-Key。
 */
export async function createDataScope(
  workspaceId: string,
  request: CreateDataScopeCommand,
  idempotencyKey?: string,
): Promise<DataScope> {
  return post<DataScope>(
    `/workspaces/${workspaceId}/data-scopes`,
    request,
    { headers: withIdempotency(idempotencyKey) },
  )
}

/**
 * 更新数据范围。
 * 对应 `PATCH /workspaces/{workspaceId}/data-scopes/{scopeId}`，需 If-Match。
 */
export async function updateDataScope(
  workspaceId: string,
  scopeId: string,
  request: UpdateDataScopeCommand,
  expectedRevision: number,
): Promise<DataScope> {
  const { httpClient } = await import('@/api')
  const response = await httpClient.patch<DataScope>(
    `/workspaces/${workspaceId}/data-scopes/${scopeId}`,
    request,
    { headers: ifMatchHeader(expectedRevision) },
  )
  return response.data
}

/**
 * 删除数据范围（需无引用）。
 * 对应 `DELETE /workspaces/{workspaceId}/data-scopes/{scopeId}`，需 If-Match。
 */
export async function deleteDataScope(
  workspaceId: string,
  scopeId: string,
  expectedRevision: number,
): Promise<void> {
  await del<void>(
    `/workspaces/${workspaceId}/data-scopes/${scopeId}`,
    { headers: ifMatchHeader(expectedRevision) },
  )
}

// ============================================================
// 跨工作空间协作授权
// ============================================================

/**
 * 授予跨工作空间协作权限。
 * 对应 `POST /workspaces/{workspaceId}/collaboration-grants`，MUST 携带 Idempotency-Key。
 */
export async function createCollaborationGrant(
  workspaceId: string,
  request: CreateCollaborationGrantCommand,
  idempotencyKey?: string,
): Promise<CollaborationGrant> {
  return post<CollaborationGrant>(
    `/workspaces/${workspaceId}/collaboration-grants`,
    request,
    { headers: withIdempotency(idempotencyKey) },
  )
}

/**
 * 撤销跨工作空间授权（ACTIVE → REVOKED）。
 * 对应 `POST /workspaces/{workspaceId}/collaboration-grants/{grantId}/revoke`，
 * 需 If-Match。
 */
export async function revokeCollaborationGrant(
  workspaceId: string,
  grantId: string,
  reason: string,
  expectedRevision: number,
): Promise<void> {
  const body: ReasonCommand = { reason }
  await post<void>(
    `/workspaces/${workspaceId}/collaboration-grants/${grantId}/revoke`,
    body,
    { headers: ifMatchHeader(expectedRevision) },
  )
}

/**
 * 查询跨工作空间协作授权。
 * 对应 `GET /workspaces/{workspaceId}/collaboration-grants/list`。
 */
export async function listCollaborationGrants(
  workspaceId: string,
  params: {
    cursor?: string | null
    pageSize?: number
    status?: GrantStatus | null
    direction?: GrantDirection
  } = {},
): Promise<PageResult<CollaborationGrant>> {
  return get<PageResult<CollaborationGrant>>(
    `/workspaces/${workspaceId}/collaboration-grants/list`,
    {
      params: toQuery({
        cursor: params.cursor,
        pageSize: params.pageSize ?? 50,
        status: params.status,
        direction: params.direction ?? 'OUTGOING',
      }),
    },
  )
}

/** 工作空间治理 API 客户端 barrel。 */
export const workspaceApi = {
  // 工作空间集合
  listWorkspaces,
  createWorkspace,
  // 工作空间详情
  getWorkspace,
  patchWorkspace,
  activateWorkspace,
  suspendWorkspace,
  archiveWorkspace,
  restoreWorkspace,
  transferWorkspaceOwner,
  // 组织管理
  listOrganizations,
  createOrganization,
  getOrganization,
  updateOrganization,
  deactivateOrganization,
  moveOrganization,
  // 成员管理
  listWorkspaceMembers,
  addWorkspaceMember,
  getWorkspaceMember,
  updateWorkspaceMember,
  removeWorkspaceMember,
  suspendWorkspaceMember,
  resumeWorkspaceMember,
  // 角色管理
  listWorkspaceRoles,
  createWorkspaceRole,
  getWorkspaceRole,
  updateWorkspaceRole,
  disableWorkspaceRole,
  // 数据范围管理
  listDataScopes,
  createDataScope,
  updateDataScope,
  deleteDataScope,
  // 跨工作空间协作授权
  createCollaborationGrant,
  revokeCollaborationGrant,
  listCollaborationGrants,
}
