/**
 * 权限客户端镜像 store（T092、FR-063、FR-067、FR-068）。
 *
 * 服务器事实由后端 {@code AuthorizationService} 实时复核；本 store 仅缓存当前操作者在
 * 当前工作空间下的权限集合与角色，供 UI 元素（v-permission 指令、按钮显隐）快速判断。
 *
 * <p><strong>权限撤销实时性（FR-068）</strong>：
 * <ul>
 *   <li>用户停用或权限撤销成功后，新的在线请求 MUST 立即使用最新授权；</li>
 *   <li>活动会话和刷新凭据最长 1 分钟撤销；</li>
 *   <li>本 store 提供 {@link refresh} 方法，由全局 401/403 拦截器或定时器触发刷新；</li>
 *   <li>权限相关的写操作 MUST 由后端二次复核，前端权限判断仅用于 UI 显隐，不作为安全边界。</li>
 * </ul>
 *
 * <p><strong>权限模型（FR-063、FR-064）</strong>：
 * <ul>
 *   <li>功能权限：{@code <domain>.<resource>.<action>}（如 {@code workflow.definition.deploy}）；</li>
 *   <li>数据范围：工作空间、组织、区域、客户、项目归属、参与关系、对象属性；</li>
 *   <li>字段权限：敏感字段、附件、签名、定位、设备凭据、成本独立授权（FR-066）；</li>
 *   <li>临时授权：有范围、期限、审批、撤销（FR-065）。</li>
 * </ul>
 */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { get } from '@/api'

/** 权限稳定键格式：`<domain>.<resource>.<action>`，如 `workflow.definition.deploy`。 */
export type PermissionKey = string

/** 角色稳定键，如 `WORKSPACE_ADMIN`、`PROJECT_OWNER`。 */
export type RoleKey = string

/** 数据范围类型（与后端 DataScopeType 对齐）。 */
export type DataScopeType =
  | 'WORKSPACE'
  | 'ORGANIZATION'
  | 'REGION'
  | 'CUSTOMER'
  | 'PROJECT_OWNERSHIP'
  | 'PARTICIPATION'
  | 'OBJECT_ATTRIBUTE'

/** 数据范围上下文（用于数据范围权限判断）。 */
export interface DataScopeContext {
  /** 数据范围类型。 */
  type: DataScopeType
  /** 范围内对象 ID 列表（如项目 ID、组织 ID）。 */
  objectIds?: string[]
  /** 范围属性（如客户 ID、区域代码）。 */
  attributes?: Record<string, string | string[]>
}

/** 当前操作者的权限快照（与后端 OperatorContext 镜像）。 */
export interface PermissionSnapshot {
  /** 操作者 ID。 */
  actorId: string
  /** 操作者类型。 */
  actorType: 'USER' | 'SYSTEM' | 'ORGANIZATION' | 'ROLE' | 'EXTERNAL'
  /** 当前工作空间 ID。 */
  workspaceId: string
  /** 已授予的功能权限集合。 */
  grantedPermissions: PermissionKey[]
  /** 已授予的角色集合。 */
  roles: RoleKey[]
  /** 数据范围上下文列表。 */
  dataScopes: DataScopeContext[]
  /** 临时授权（含到期时间）。 */
  temporaryGrants: Array<{
    permission: PermissionKey
    scope: DataScopeContext
    expiresAt: string
  }>
  /** 快照生成时间（ISO 8601）。 */
  snapshotAt: string
  /** 快照过期时间（FR-068：最长 1 分钟）。 */
  expiresAt: string
}

/** 后端权限快照端点（P1 简化：由 identity 模块提供，T063 OIDC 完成后对接）。 */
const PERMISSION_SNAPSHOT_ENDPOINT = '/auth/permissions/me'

export const usePermissionStore = defineStore('permission', () => {
  const snapshot = ref<PermissionSnapshot | null>(null)
  const loading = ref(false)
  const lastFetchAt = ref<number | null>(null)
  const fetchError = ref<string | null>(null)

  /** 当前是否已加载权限快照。 */
  const isLoaded = computed(() => snapshot.value !== null)

  /** 快照是否已过期（FR-068：超过 60 秒视为过期）。 */
  const isExpired = computed(() => {
    if (!snapshot.value) return true
    const expiresAt = new Date(snapshot.value.expiresAt).getTime()
    return Date.now() >= expiresAt
  })

  /** 已授予的权限集合（Set 形式，便于 O(1) 查询）。 */
  const permissionSet = computed<Set<PermissionKey>>(() => {
    if (!snapshot.value) return new Set()
    return new Set(snapshot.value.grantedPermissions)
  })

  /** 已授予的角色集合。 */
  const roleSet = computed<Set<RoleKey>>(() => {
    if (!snapshot.value) return new Set()
    return new Set(snapshot.value.roles)
  })

  /** 当前生效的临时授权（已过滤过期）。 */
  const activeTemporaryGrants = computed(() => {
    if (!snapshot.value) return []
    const now = Date.now()
    return snapshot.value.temporaryGrants.filter(
      (g) => new Date(g.expiresAt).getTime() > now,
    )
  })

  /**
   * 判断是否拥有指定权限。
   * <p>同时检查常规权限与未过期的临时授权。
   */
  function hasPermission(permission: PermissionKey): boolean {
    if (permissionSet.value.has(permission)) return true
    return activeTemporaryGrants.value.some((g) => g.permission === permission)
  }

  /**
   * 判断是否拥有任一指定权限（OR 语义）。
   */
  function hasAnyPermission(...permissions: PermissionKey[]): boolean {
    if (permissions.length === 0) return false
    return permissions.some(hasPermission)
  }

  /**
   * 判断是否拥有全部指定权限（AND 语义）。
   */
  function hasAllPermissions(...permissions: PermissionKey[]): boolean {
    if (permissions.length === 0) return true
    return permissions.every(hasPermission)
  }

  /**
   * 判断是否拥有指定角色。
   */
  function hasRole(role: RoleKey): boolean {
    return roleSet.value.has(role)
  }

  /**
   * 判断是否拥有任一指定角色。
   */
  function hasAnyRole(...roles: RoleKey[]): boolean {
    if (roles.length === 0) return false
    return roles.some(hasRole)
  }

  /**
   * 判断是否在工作空间管理员角色下。
   */
  function isWorkspaceAdmin(): boolean {
    return hasAnyRole('WORKSPACE_ADMIN', 'PLATFORM_ADMIN')
  }

  /**
   * 判断当前数据范围是否覆盖指定上下文（FR-064）。
   * <p>简化实现：仅校验数据范围类型与对象 ID 集合包含关系。
   */
  function isDataScopeCovered(ctx: DataScopeContext): boolean {
    if (!snapshot.value) return false
    // 工作空间范围默认覆盖所有
    if (snapshot.value.dataScopes.some((s) => s.type === 'WORKSPACE')) return true
    return snapshot.value.dataScopes.some((s) => {
      if (s.type !== ctx.type) return false
      if (!ctx.objectIds || ctx.objectIds.length === 0) return true
      if (!s.objectIds) return false
      return ctx.objectIds.every((id) => s.objectIds!.includes(id))
    })
  }

  /**
   * 从后端刷新权限快照（FR-068：每次刷新获取最新授权）。
   */
  async function refresh(): Promise<void> {
    if (loading.value) return
    loading.value = true
    fetchError.value = null
    try {
      const data = await get<PermissionSnapshot>(PERMISSION_SNAPSHOT_ENDPOINT)
      snapshot.value = data
      lastFetchAt.value = Date.now()
    } catch (err) {
      fetchError.value = err instanceof Error ? err.message : '加载权限失败'
      // 加载失败时不清除现有快照，避免短暂网络抖动导致 UI 抖动
      // eslint-disable-next-line no-console
      console.error('[permission] refresh failed', err)
    } finally {
      loading.value = false
    }
  }

  /**
   * 确保权限快照已加载且未过期，否则刷新。
   */
  async function ensureLoaded(): Promise<void> {
    if (!isLoaded.value || isExpired.value) {
      await refresh()
    }
  }

  /**
   * 清除权限快照（登出或工作空间切换时调用）。
   */
  function clear(): void {
    snapshot.value = null
    lastFetchAt.value = null
    fetchError.value = null
  }

  return {
    snapshot,
    loading,
    fetchError,
    lastFetchAt,
    isLoaded,
    isExpired,
    permissionSet,
    roleSet,
    hasPermission,
    hasAnyPermission,
    hasAllPermissions,
    hasRole,
    hasAnyRole,
    isWorkspaceAdmin,
    isDataScopeCovered,
    refresh,
    ensureLoaded,
    clear,
  }
})

/** 常见权限稳定键（与后端对齐，业务页面引用避免硬编码）。 */
export const PERMISSIONS = {
  // 工作空间治理
  WORKSPACE_CREATE: 'workspace.create',
  WORKSPACE_UPDATE: 'workspace.update',
  WORKSPACE_ARCHIVE: 'workspace.archive',
  WORKSPACE_MEMBER_MANAGE: 'workspace.member.manage',
  WORKSPACE_ROLE_MANAGE: 'workspace.role.manage',
  WORKSPACE_GRANT_CROSS: 'workspace.grant.cross',
  // 领域包
  DOMAIN_PACKAGE_DRAFT: 'domain.package.draft',
  DOMAIN_PACKAGE_REVIEW: 'domain.package.review',
  DOMAIN_PACKAGE_PUBLISH: 'domain.package.publish',
  DOMAIN_PACKAGE_MIGRATE: 'domain.package.migrate',
  DOMAIN_PACKAGE_RETIRE: 'domain.package.retire',
  // 项目模板
  PROJECT_TEMPLATE_EDIT: 'project.template.edit',
  PROJECT_TEMPLATE_PUBLISH: 'project.template.publish',
  PROJECT_INSTANTIATE: 'project.instantiate',
  // 项目生命周期
  PROJECT_CREATE: 'project.create',
  PROJECT_UPDATE: 'project.update',
  PROJECT_ROLLBACK: 'project.rollback',
  PROJECT_CLOSE: 'project.close',
  PROJECT_ARCHIVE: 'project.archive',
  // 任务
  TASK_CREATE: 'task.create',
  TASK_ASSIGN: 'task.assign',
  TASK_UPDATE: 'task.update',
  TASK_COMPLETE: 'task.complete',
  // 计划基线
  BASELINE_CREATE: 'baseline.create',
  BASELINE_REPLACE: 'baseline.replace',
  BASELINE_ADJUST: 'baseline.adjust',
  // 交付件
  DELIVERABLE_SUBMIT: 'deliverable.submit',
  DELIVERABLE_REVIEW: 'deliverable.review',
  DELIVERABLE_RELEASE: 'deliverable.release',
  // 审批
  APPROVAL_ACT: 'approval.act',
  APPROVAL_DELEGATE: 'approval.delegate',
  // 工作流
  WORKFLOW_DEFINITION_DEPLOY: 'workflow.definition.deploy',
  WORKFLOW_DEFINITION_TRANSITION: 'workflow.definition.transition',
  WORKFLOW_INSTANCE_ADMIN: 'workflow.instance.admin',
  WORKFLOW_INSTANCE_MIGRATE: 'workflow.instance.migrate',
  WORKFLOW_INSTANCE_TERMINATE: 'workflow.instance.terminate',
  WORKFLOW_INSTANCE_COMPENSATE: 'workflow.instance.compensate',
  // 高风险操作
  HIGH_RISK_OPERATION_CONFIRM: 'high.risk.operation.confirm',
  // 审计
  AUDIT_VIEW: 'audit.view',
  AUDIT_EXPORT: 'audit.export',
} as const

/** 常见角色稳定键。 */
export const ROLES = {
  PLATFORM_ADMIN: 'PLATFORM_ADMIN',
  WORKSPACE_ADMIN: 'WORKSPACE_ADMIN',
  PROJECT_OWNER: 'PROJECT_OWNER',
  PROJECT_MANAGER: 'PROJECT_MANAGER',
  TEAM_MEMBER: 'TEAM_MEMBER',
  APPROVER: 'APPROVER',
  REVIEWER: 'REVIEWER',
  VIEWER: 'VIEWER',
  EXTERNAL_PARTNER: 'EXTERNAL_PARTNER',
} as const
