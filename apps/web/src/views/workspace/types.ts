export type WorkspaceStatus = 'DRAFT' | 'ACTIVE' | 'SUSPENDED' | 'ARCHIVED'

export interface Workspace {
  id: string
  code: string
  name: string
  ownerUserId: string
  status: WorkspaceStatus
  revision: number
}

export interface OrganizationUnit {
  id: string
  workspaceId: string
  parentId?: string | null
  code: string
  name: string
  type: string
  path: string
  regionCode?: string | null
  status: string
  revision: number
}

export interface WorkspaceMembership {
  id: string
  workspaceId: string
  userId: string
  organizationUnitId?: string | null
  roleIds: string[]
  dataScopeIds: string[]
  membershipType: string
  validUntil?: string | null
  status: string
  revision: number
}

export interface WorkspaceRole {
  id: string
  workspaceId: string
  stableKey: string
  name: string
  allowedActions: string[]
  status: string
  revision: number
}

export interface CollaborationGrant {
  id: string
  collaboratorWorkspaceId: string
  target: {
    objectType: string
    objectId: string
  }
  roleId: string
  allowedActions: string[]
  validUntil: string
  reason?: string
  status: string
  revision: number
}

export interface WorkspacePage {
  items: Workspace[]
  nextCursor: string | null
}

export interface WorkspaceGovernanceSnapshot {
  organizations: OrganizationUnit[]
  members: WorkspaceMembership[]
  roles: WorkspaceRole[]
  grants: CollaborationGrant[]
}

export interface WorkspaceGovernanceApi {
  listWorkspaces(): Promise<Workspace[]>
  loadGovernance(workspaceId: string): Promise<WorkspaceGovernanceSnapshot>
  createOrganization(
    workspaceId: string,
    command: Record<string, unknown>,
  ): Promise<OrganizationUnit>
  addMember(
    workspaceId: string,
    command: Record<string, unknown>,
  ): Promise<WorkspaceMembership>
  createRole(
    workspaceId: string,
    command: Record<string, unknown>,
  ): Promise<WorkspaceRole>
  createGrant(
    workspaceId: string,
    command: Record<string, unknown>,
  ): Promise<CollaborationGrant>
  revokeGrant(
    workspaceId: string,
    grantId: string,
    revision: number,
    reason: string,
  ): Promise<void>
}
