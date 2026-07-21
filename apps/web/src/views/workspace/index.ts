/**
 * 工作空间治理视图 barrel 导出（T108）。
 *
 * 集中导出工作空间选择、详情、组织树、成员、角色、数据范围与协作授权页面，
 * 以及 API 客户端、Pinia store 与类型定义。路由在 `@/router` 中引用这些组件。
 */
export { default as WorkspaceSelectorView } from './WorkspaceSelectorView.vue'
export { default as WorkspaceDetailView } from './WorkspaceDetailView.vue'
export { default as OrganizationTreeView } from './OrganizationTreeView.vue'
export { default as WorkspaceMemberListView } from './WorkspaceMemberListView.vue'
export { default as WorkspaceRoleListView } from './WorkspaceRoleListView.vue'
export { default as DataScopeListView } from './DataScopeListView.vue'
export { default as CollaborationGrantListView } from './CollaborationGrantListView.vue'

export { workspaceApi } from './api'
export { useWorkspaceStore } from './store'
export type {
  Workspace,
  Organization,
  WorkspaceMember,
  WorkspaceRole,
  DataScope,
  DataScopeRule,
  CollaborationGrant,
  WorkspaceStatus,
  OrganizationStatus,
  MemberStatus,
  RoleStatus,
  DataScopeType,
  GrantStatus,
  GrantDirection,
  CreateWorkspaceCommand,
  UpdateWorkspaceCommand,
  TransferOwnerCommand,
  ReasonCommand,
  CreateOrganizationCommand,
  UpdateOrganizationCommand,
  MoveOrganizationCommand,
  AddWorkspaceMemberCommand,
  UpdateWorkspaceMemberCommand,
  CreateWorkspaceRoleCommand,
  UpdateWorkspaceRoleCommand,
  CreateDataScopeCommand,
  UpdateDataScopeCommand,
  CreateCollaborationGrantCommand,
  ObjectRef,
  PageResult,
  QueryParams,
} from './types'
