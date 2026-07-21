/**
 * 工作空间治理前端类型定义（T108、FR-003 至 FR-006、FR-063 至 FR-068）。
 *
 * 与后端 `com.pdp.workspace.domain` 与 `WorkspaceController` 内嵌 DTO 对齐，
 * 字段命名与 OpenAPI schemas 保持一致。所有枚举值禁止前端硬编码，
 * 必须与后端 enum name() 完全一致。
 */

// ============================================================
// 枚举类型（与后端 enum name() 对齐）
// ============================================================

/** 工作空间状态（与 WorkspaceStatus 对齐）。 */
export type WorkspaceStatus = 'DRAFT' | 'ACTIVE' | 'SUSPENDED' | 'ARCHIVED'

/** 组织状态（与 OrganizationStatus 对齐）。 */
export type OrganizationStatus = 'ACTIVE' | 'INACTIVE'

/** 成员状态（与 MemberStatus 对齐）。 */
export type MemberStatus = 'ACTIVE' | 'SUSPENDED' | 'REMOVED'

/** 角色状态（与 RoleStatus 对齐）。 */
export type RoleStatus = 'ACTIVE' | 'DISABLED'

/** 数据范围类型（与 DataScopeType 对齐，FR-063）。 */
export type DataScopeType =
  | 'WORKSPACE'
  | 'ORGANIZATION'
  | 'REGION'
  | 'CUSTOMER'
  | 'PROJECT_OWNERSHIP'
  | 'PARTICIPATION'
  | 'OBJECT_ATTRIBUTE'

/** 协作授权状态（与 GrantStatus 对齐）。 */
export type GrantStatus = 'DRAFT' | 'ACTIVE' | 'EXPIRED' | 'REVOKED'

/** 协作授权方向。 */
export type GrantDirection = 'OUTGOING' | 'INCOMING'

// ============================================================
// 领域实体（与后端 record 字段顺序与命名对齐）
// ============================================================

/** 工作空间聚合根（对应 Workspace schema）。 */
export interface Workspace {
  id: string
  code: string
  name: string
  description?: string | null
  status: WorkspaceStatus
  ownerUserId: string
  defaultLocale?: string | null
  defaultTimezone?: string | null
  revision: number
  createdAt: string
  updatedAt: string
}

/** 组织（对应 Organization schema）。 */
export interface Organization {
  id: string
  workspaceId: string
  code: string
  name: string
  description?: string | null
  parentId?: string | null
  path: string
  depth: number
  status: OrganizationStatus
  revision: number
  createdAt: string
  updatedAt: string
}

/** 工作空间成员（对应 WorkspaceMember schema）。 */
export interface WorkspaceMember {
  id: string
  workspaceId: string
  userId: string
  organizationId?: string | null
  roleIds: string[]
  dataScopeIds: string[]
  status: MemberStatus
  validUntil?: string | null
  revision: number
  createdAt: string
  updatedAt: string
}

/** 工作空间角色（对应 WorkspaceRole schema）。 */
export interface WorkspaceRole {
  id: string
  workspaceId: string
  key: string
  name: string
  description?: string | null
  permissions: string[]
  dataScopeType: DataScopeType
  status: RoleStatus
  isSystem: boolean
  revision: number
  createdAt: string
  updatedAt: string
}

/** 数据范围规则（与 DataScopeRule 对齐）。 */
export interface DataScopeRule {
  field: string
  operator: string
  value?: unknown
}

/** 数据范围（对应 DataScope schema）。 */
export interface DataScope {
  id: string
  workspaceId: string
  key: string
  name: string
  description?: string | null
  scopeType: DataScopeType
  rules: DataScopeRule[]
  revision: number
  createdAt: string
  updatedAt: string
}

/** 跨工作空间协作授权（对应 CollaborationGrant schema）。 */
export interface CollaborationGrant {
  id: string
  workspaceId: string
  collaboratorWorkspaceId: string
  targetObjectType: string
  targetObjectId: string
  roleId: string
  allowedActions: string[]
  validUntil?: string | null
  status: GrantStatus
  reason?: string | null
  revokedAt?: string | null
  revokeReason?: string | null
  revision: number
  createdAt: string
  updatedAt: string
}

// ============================================================
// 请求 DTO（与 WorkspaceController 内嵌 record 对齐）
// ============================================================

/** 创建工作空间请求。 */
export interface CreateWorkspaceCommand {
  code: string
  name: string
  description?: string | null
  ownerUserId: string
  defaultLocale?: string | null
  defaultTimezone?: string | null
}

/** 更新工作空间请求。 */
export interface UpdateWorkspaceCommand {
  name: string
  description?: string | null
  defaultLocale?: string | null
  defaultTimezone?: string | null
}

/** 转移负责人请求。 */
export interface TransferOwnerCommand {
  newOwnerUserId: string
  reason: string
}

/** 原因请求（暂停/归档/撤销等）。 */
export interface ReasonCommand {
  reason: string
}

/** 创建组织请求。 */
export interface CreateOrganizationCommand {
  code: string
  name: string
  description?: string | null
  parentId?: string | null
}

/** 更新组织请求。 */
export interface UpdateOrganizationCommand {
  name: string
  description?: string | null
}

/** 移动组织请求。 */
export interface MoveOrganizationCommand {
  newParentId?: string | null
}

/** 添加成员请求。 */
export interface AddWorkspaceMemberCommand {
  userId: string
  roleIds: string[]
  organizationId?: string | null
  dataScopeIds?: string[]
  validUntil?: string | null
}

/** 更新成员请求。 */
export interface UpdateWorkspaceMemberCommand {
  roleIds: string[]
  organizationId?: string | null
  dataScopeIds?: string[]
  validUntil?: string | null
}

/** 创建角色请求。 */
export interface CreateWorkspaceRoleCommand {
  key: string
  name: string
  description?: string | null
  permissions: string[]
  dataScopeType: DataScopeType
  isSystem?: boolean
}

/** 更新角色请求。 */
export interface UpdateWorkspaceRoleCommand {
  name: string
  description?: string | null
  permissions: string[]
  dataScopeType: DataScopeType
}

/** 创建数据范围请求。 */
export interface CreateDataScopeCommand {
  key: string
  name: string
  description?: string | null
  scopeType: DataScopeType
  rules: DataScopeRule[]
}

/** 更新数据范围请求。 */
export interface UpdateDataScopeCommand {
  name: string
  description?: string | null
  rules: DataScopeRule[]
  scopeType: DataScopeType
}

/** 对象引用（与 WorkspaceController.ObjectRef 对齐）。 */
export interface ObjectRef {
  objectType: string
  objectId: string
}

/** 创建协作授权请求。 */
export interface CreateCollaborationGrantCommand {
  collaboratorWorkspaceId: string
  target: ObjectRef
  roleId: string
  allowedActions: string[]
  validUntil?: string | null
  reason?: string | null
}

// ============================================================
// 通用类型
// ============================================================

/** 游标分页结果（与 PageResult 对齐）。 */
export interface PageResult<T> {
  data: T[]
  nextCursor?: string | null
  hasMore: boolean
  total?: number | null
}

/** 查询参数类型。 */
export type QueryParams = Record<string, string | number | boolean | undefined | null>

// ============================================================
// 状态映射辅助函数
// ============================================================

/** 工作空间状态中文名。 */
export function workspaceStatusLabel(status: WorkspaceStatus): string {
  switch (status) {
    case 'DRAFT':
      return '草稿'
    case 'ACTIVE':
      return '已激活'
    case 'SUSPENDED':
      return '已暂停'
    case 'ARCHIVED':
      return '已归档'
    default:
      return status
  }
}

/** 工作空间状态对应的 Tag 颜色。 */
export function workspaceStatusColor(status: WorkspaceStatus): string {
  switch (status) {
    case 'DRAFT':
      return 'default'
    case 'ACTIVE':
      return 'green'
    case 'SUSPENDED':
      return 'orange'
    case 'ARCHIVED':
      return 'red'
    default:
      return 'default'
  }
}

/** 判断工作空间状态是否可激活。 */
export function canActivateWorkspace(status: WorkspaceStatus): boolean {
  return status === 'DRAFT' || status === 'SUSPENDED'
}

/** 判断工作空间状态是否可暂停。 */
export function canSuspendWorkspace(status: WorkspaceStatus): boolean {
  return status === 'ACTIVE'
}

/** 判断工作空间状态是否可归档。 */
export function canArchiveWorkspace(status: WorkspaceStatus): boolean {
  return status === 'ACTIVE' || status === 'SUSPENDED'
}

/** 判断工作空间状态是否可恢复。 */
export function canRestoreWorkspace(status: WorkspaceStatus): boolean {
  return status === 'ARCHIVED'
}

/** 组织状态中文名。 */
export function organizationStatusLabel(status: OrganizationStatus): string {
  switch (status) {
    case 'ACTIVE':
      return '正常'
    case 'INACTIVE':
      return '已停用'
    default:
      return status
  }
}

/** 组织状态对应的 Tag 颜色。 */
export function organizationStatusColor(status: OrganizationStatus): string {
  switch (status) {
    case 'ACTIVE':
      return 'green'
    case 'INACTIVE':
      return 'default'
    default:
      return 'default'
  }
}

/** 成员状态中文名。 */
export function memberStatusLabel(status: MemberStatus): string {
  switch (status) {
    case 'ACTIVE':
      return '正常'
    case 'SUSPENDED':
      return '已暂停'
    case 'REMOVED':
      return '已移除'
    default:
      return status
  }
}

/** 成员状态对应的 Tag 颜色。 */
export function memberStatusColor(status: MemberStatus): string {
  switch (status) {
    case 'ACTIVE':
      return 'green'
    case 'SUSPENDED':
      return 'orange'
    case 'REMOVED':
      return 'red'
    default:
      return 'default'
  }
}

/** 判断成员状态是否可暂停。 */
export function canSuspendMember(status: MemberStatus): boolean {
  return status === 'ACTIVE'
}

/** 判断成员状态是否可恢复。 */
export function canResumeMember(status: MemberStatus): boolean {
  return status === 'SUSPENDED'
}

/** 判断成员状态是否可移除。 */
export function canRemoveMember(status: MemberStatus): boolean {
  return status === 'ACTIVE' || status === 'SUSPENDED'
}

/** 角色状态中文名。 */
export function roleStatusLabel(status: RoleStatus): string {
  switch (status) {
    case 'ACTIVE':
      return '已启用'
    case 'DISABLED':
      return '已停用'
    default:
      return status
  }
}

/** 角色状态对应的 Tag 颜色。 */
export function roleStatusColor(status: RoleStatus): string {
  switch (status) {
    case 'ACTIVE':
      return 'green'
    case 'DISABLED':
      return 'default'
    default:
      return 'default'
  }
}

/** 数据范围类型中文名。 */
export function dataScopeTypeLabel(type: DataScopeType): string {
  switch (type) {
    case 'WORKSPACE':
      return '全工作空间'
    case 'ORGANIZATION':
      return '按组织'
    case 'REGION':
      return '按区域'
    case 'CUSTOMER':
      return '按客户'
    case 'PROJECT_OWNERSHIP':
      return '按项目归属'
    case 'PARTICIPATION':
      return '按参与身份'
    case 'OBJECT_ATTRIBUTE':
      return '按对象属性'
    default:
      return type
  }
}

/** 数据范围类型选项（供下拉选择使用）。 */
export const DATA_SCOPE_TYPE_OPTIONS: Array<{ value: DataScopeType; label: string }> = [
  { value: 'WORKSPACE', label: '全工作空间' },
  { value: 'ORGANIZATION', label: '按组织' },
  { value: 'REGION', label: '按区域' },
  { value: 'CUSTOMER', label: '按客户' },
  { value: 'PROJECT_OWNERSHIP', label: '按项目归属' },
  { value: 'PARTICIPATION', label: '按参与身份' },
  { value: 'OBJECT_ATTRIBUTE', label: '按对象属性' },
]

/** 数据范围规则操作符选项（与后端约定）。 */
export const DATA_SCOPE_OPERATOR_OPTIONS = [
  { value: 'EQ', label: '等于' },
  { value: 'NE', label: '不等于' },
  { value: 'IN', label: '属于' },
  { value: 'NOT_IN', label: '不属于' },
  { value: 'GT', label: '大于' },
  { value: 'GTE', label: '大于等于' },
  { value: 'LT', label: '小于' },
  { value: 'LTE', label: '小于等于' },
  { value: 'LIKE', label: '模糊匹配' },
  { value: 'IS_NULL', label: '为空' },
  { value: 'NOT_NULL', label: '非空' },
] as const

/** 协作授权状态中文名。 */
export function grantStatusLabel(status: GrantStatus): string {
  switch (status) {
    case 'DRAFT':
      return '草稿'
    case 'ACTIVE':
      return '生效中'
    case 'EXPIRED':
      return '已到期'
    case 'REVOKED':
      return '已撤销'
    default:
      return status
  }
}

/** 协作授权状态对应的 Tag 颜色。 */
export function grantStatusColor(status: GrantStatus): string {
  switch (status) {
    case 'DRAFT':
      return 'default'
    case 'ACTIVE':
      return 'green'
    case 'EXPIRED':
      return 'orange'
    case 'REVOKED':
      return 'red'
    default:
      return 'default'
  }
}

/** 判断授权状态是否可撤销。 */
export function canRevokeGrant(status: GrantStatus): boolean {
  return status === 'ACTIVE'
}

/** 协作授权方向中文名。 */
export function grantDirectionLabel(direction: GrantDirection): string {
  switch (direction) {
    case 'OUTGOING':
      return '授权给他人'
    case 'INCOMING':
      return '他人授权给我'
    default:
      return direction
  }
}
