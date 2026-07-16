import { apiClient, type ApiClient } from '../../api/client'
import type {
  CollaborationGrant,
  OrganizationUnit,
  WorkspaceGovernanceApi,
  WorkspaceMembership,
  WorkspacePage,
  WorkspaceRole,
} from './types'

function idempotencyKey(operation: string): string {
  return `${operation}-${globalThis.crypto?.randomUUID?.() ?? Date.now()}`
}

export function createWorkspaceGovernanceApi(
  client: ApiClient = apiClient,
): WorkspaceGovernanceApi {
  return {
    async listWorkspaces() {
      const page = await client.request<WorkspacePage | WorkspacePage['items']>(
        '/workspaces?pageSize=100',
      )
      return Array.isArray(page) ? page : page.items
    },

    async loadGovernance(workspaceId) {
      const base = `/workspaces/${encodeURIComponent(workspaceId)}`
      const request = <T>(path: string) =>
        client.request<T>(`${base}${path}`, { workspaceId })
      const [organizations, members, roles, grants] = await Promise.all([
        request<OrganizationUnit[]>('/organizations'),
        request<WorkspaceMembership[]>('/members'),
        request<WorkspaceRole[]>('/roles'),
        request<CollaborationGrant[]>('/collaboration-grants'),
      ])
      return { organizations, members, roles, grants }
    },

    createOrganization(workspaceId, command) {
      return client.request<OrganizationUnit>(
        `/workspaces/${encodeURIComponent(workspaceId)}/organizations`,
        {
          method: 'POST',
          workspaceId,
          idempotencyKey: idempotencyKey('organization'),
          body: command,
        },
      )
    },

    addMember(workspaceId, command) {
      return client.request<WorkspaceMembership>(
        `/workspaces/${encodeURIComponent(workspaceId)}/members`,
        {
          method: 'POST',
          workspaceId,
          idempotencyKey: idempotencyKey('member'),
          body: command,
        },
      )
    },

    createRole(workspaceId, command) {
      return client.request<WorkspaceRole>(
        `/workspaces/${encodeURIComponent(workspaceId)}/roles`,
        {
          method: 'POST',
          workspaceId,
          idempotencyKey: idempotencyKey('role'),
          body: command,
        },
      )
    },

    createGrant(workspaceId, command) {
      return client.request<CollaborationGrant>(
        `/workspaces/${encodeURIComponent(workspaceId)}/collaboration-grants`,
        {
          method: 'POST',
          workspaceId,
          idempotencyKey: idempotencyKey('grant'),
          body: command,
        },
      )
    },

    revokeGrant(workspaceId, grantId, revision, reason) {
      return client.request<void>(
        `/workspaces/${encodeURIComponent(workspaceId)}/collaboration-grants/${encodeURIComponent(grantId)}/revoke`,
        {
          method: 'POST',
          workspaceId,
          revision,
          body: { reason },
        },
      )
    },
  }
}

export const workspaceGovernanceApi = createWorkspaceGovernanceApi()
