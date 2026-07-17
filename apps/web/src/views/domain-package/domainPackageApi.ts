import { apiClient, type ApiClient } from '../../api/client'
import type {
  DomainPackage,
  DomainPackageDesignerApi,
  DomainPackagePage,
  DomainPackageVersion,
  MigrationJob,
  MigrationPreview,
  PackageLayer,
  ValidationReport,
} from './types'

function idempotencyKey(operation: string): string {
  return `${operation}-${globalThis.crypto?.randomUUID?.() ?? Date.now()}`
}

function path(packageId: string, versionId?: string): string {
  const base = `/domain-packages/${encodeURIComponent(packageId)}`
  return versionId
    ? `${base}/versions/${encodeURIComponent(versionId)}`
    : base
}

export function createDomainPackageDesignerApi(
  client: ApiClient = apiClient,
): DomainPackageDesignerApi {
  return {
    list(workspaceId, layer?: PackageLayer) {
      const query = new URLSearchParams({ pageSize: '200' })
      if (layer) query.set('layer', layer)
      return client.request<DomainPackagePage>(`/domain-packages?${query}`, {
        workspaceId,
      })
    },

    createPackage(workspaceId, command) {
      return client.request<DomainPackage>('/domain-packages', {
        method: 'POST',
        workspaceId,
        idempotencyKey: idempotencyKey('domain-package'),
        body: command,
      })
    },

    createVersion(workspaceId, packageId, command) {
      return client.request<DomainPackageVersion>(`${path(packageId)}/versions`, {
        method: 'POST',
        workspaceId,
        idempotencyKey: idempotencyKey('domain-package-version'),
        body: command,
      })
    },

    validate(workspaceId, packageId, versionId, revision) {
      return client.request<ValidationReport>(
        `${path(packageId, versionId)}/validate`,
        { method: 'POST', workspaceId, revision },
      )
    },

    submitReview(workspaceId, packageId, versionId, revision) {
      return client.request<DomainPackageVersion>(
        `${path(packageId, versionId)}/submit-review`,
        { method: 'POST', workspaceId, revision },
      )
    },

    review(workspaceId, packageId, versionId, revision, comment) {
      return client.request<DomainPackageVersion>(
        `${path(packageId, versionId)}/review`,
        {
          method: 'POST',
          workspaceId,
          revision,
          body: { decision: 'APPROVE', comment },
        },
      )
    },

    publish(workspaceId, packageId, versionId, revision, reviewComment) {
      return client.request<DomainPackageVersion>(
        `${path(packageId, versionId)}/publish`,
        { method: 'POST', workspaceId, revision, body: { reviewComment } },
      )
    },

    previewMigration(workspaceId, packageId, targetVersionId, command) {
      return client.request<MigrationPreview>(
        `${path(packageId, targetVersionId)}/migration-preview`,
        { method: 'POST', workspaceId, body: command },
      )
    },

    startMigration(workspaceId, packageId, targetVersionId, command) {
      return client.request<MigrationJob>(
        `${path(packageId, targetVersionId)}/migrations`,
        {
          method: 'POST',
          workspaceId,
          idempotencyKey: idempotencyKey('domain-package-migration'),
          body: command,
        },
      )
    },
  }
}

export const domainPackageDesignerApi = createDomainPackageDesignerApi()
